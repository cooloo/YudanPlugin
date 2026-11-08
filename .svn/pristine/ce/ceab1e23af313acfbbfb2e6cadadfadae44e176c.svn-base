package com.cloudmusic.car.data

import com.paopao.music.nowplaying.LyricWord
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.egogame.plugin.api.PluginStorage
import com.egogame.plugin.api.PluginTransfers
import com.egogame.music.plugin.common.HostImageFetcher
import com.egogame.music.plugin.common.downloadAudio
import com.egogame.music.plugin.common.AudioDownloadState
import com.egogame.music.plugin.common.MusicTransferException
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.cloudmusic.car.api.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class MusicCacheStats(
    val audioBytes: Long = 0,
    val imageBytes: Long = 0,
    val lyricBytes: Long = 0,
) {
    val totalBytes: Long get() = audioBytes + imageBytes + lyricBytes
}

/** 播放、封面与歌词共用插件独立缓存目录。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@kotlin.OptIn(ExperimentalCoilApi::class)
object MusicCache {
    private const val LOW_SPACE_BYTES = 1L * 1024 * 1024 * 1024
    private const val TARGET_FREE_BYTES = 2L * 1024 * 1024 * 1024

    private lateinit var root: File
    private lateinit var lyricDir: File
    private lateinit var artworkLoader: ImageLoader
    private var initialized = false
    private var statsScope: CoroutineScope? = null
    private var statsJob: Job? = null
    private var statsGeneration = 0L
    private val statsMutex = Mutex()
    private data class StatsSource(val generation: Long, val cache: SimpleCache,
        val fullAudio: File, val images: File, val lyrics: File)
    private lateinit var storage: PluginStorage
    private lateinit var transfers: PluginTransfers
    @Volatile private var playingTrackId: String? = null
    private lateinit var fullAudioDir: File
    private val activeDownloads = ConcurrentHashMap.newKeySet<String>()
    private val downloadMutex = Mutex()
    private val _audioDownloads = MutableStateFlow<Map<String, AudioDownloadState>>(emptyMap())
    val audioDownloads: StateFlow<Map<String, AudioDownloadState>> = _audioDownloads.asStateFlow()

    lateinit var audio: SimpleCache
        private set

    private val _stats = MutableStateFlow(MusicCacheStats())
    val stats: StateFlow<MusicCacheStats> = _stats.asStateFlow()

    @Synchronized
    fun init(context: Context, storage: PluginStorage, transfers: PluginTransfers) {
        if (initialized) return
        this.storage = storage
        this.transfers = transfers
        val appContext = context.applicationContext
        root = File(storage.cacheDirectory, "music").apply { mkdirs() }
        lyricDir = File(root, "lyrics").apply { mkdirs() }
        fullAudioDir = File(root, "complete-audio").apply { mkdirs() }
        audio = SimpleCache(
            File(root, "audio").apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(storage.audioCacheBudgetBytes / 2),
        )
        try {
            artworkLoader = ImageLoader.Builder(appContext)
            .components { add(HostImageFetcher.Factory(transfers)) }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(root, "images"))
                    .maxSizeBytes(storage.imageCacheBudgetBytes)
                    .build()
            }
            .crossfade(true)
            .build()
        } catch (failure: Throwable) {
            try { audio.release() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
        initialized = true
        statsGeneration++
        statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        _stats.value = MusicCacheStats()
        requestStatsRefresh()
    }

    /**
     * 车机存储快满时的保护，每首歌开始加载前在加载线程调用：剩余空间不到 1GB 就从最久没听的整首删，
     * 删到剩 2GB 为止（正要放的这首不删）。
     */
    @Synchronized
    fun ensureDiskRoom(keepKey: String?, reserveBytes: Long = 0) {
        if (!initialized) return
        val dir = File(root, "audio")
        val budget = storage.audioCacheBudgetBytes / 2
        fun needsRoom(): Boolean = dir.usableSpace < LOW_SPACE_BYTES || fullAudioDir.sizeOnDisk() + reserveBytes > budget
        if (!needsRoom()) return
        fullAudioDir.listFiles().orEmpty().sortedBy(File::lastModified).forEach { file ->
            if (needsRoom() && file.name != keepKey && !protectedAudioFile(file)) file.delete()
        }
        val oldestFirst = runCatching {
            audio.keys.toList()
                .map { key -> key to (audio.getCachedSpans(key).maxOfOrNull { it.lastTouchTimestamp } ?: 0L) }
                .sortedBy { it.second }
        }.getOrDefault(emptyList())
        for ((key, _) in oldestFirst) {
            if (dir.usableSpace >= TARGET_FREE_BYTES) break
            if (key != keepKey && !key.startsWith("song-${playingTrackId}-")) runCatching { audio.removeResource(key) }
        }
        requestStatsRefresh()
    }

    /**
     * 功能包卸载/升级时由运行时调用。SimpleCache 的目录锁记在桌面进程共享的类上，
     * 不释放的话同一进程里新装的包再开这个目录会直接失败。
     */
    @Synchronized
    fun close() {
        if (!initialized) return
        initialized = false
        statsGeneration++
        statsScope?.cancel()
        statsScope = null
        statsJob = null
        try { audio.release() } finally {
            artworkLoader.shutdown()
            playingTrackId = null
            initialized = false
        }
    }

    fun imageLoader(context: Context): ImageLoader {
        check(initialized) { "音乐缓存尚未初始化" }
        return artworkLoader
    }

    fun protectTrack(id: String?) { playingTrackId = id }

    fun completeAudio(key: String): File? =
        File(fullAudioDir, key).takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }

    suspend fun downloadCompleteAudio(key: String, url: String): Boolean = downloadMutex.withLock {
        if (completeAudio(key) != null) return@withLock true
        // 分段与整首各占一半；完整文件先预留下载上限，不能挤占正在播放的资源。
        val fullBudget = storage.audioCacheBudgetBytes / 2
        val downloadLimit = minOf(512L * 1024 * 1024, fullBudget)
        ensureDiskRoom(key, downloadLimit)
        if (fullAudioDir.usableSpace < LOW_SPACE_BYTES || fullAudioDir.sizeOnDisk() + downloadLimit > fullBudget) return@withLock false
        activeDownloads.add(key)
        _audioDownloads.update { it + (key to AudioDownloadState()) }
        try {
            transfers.downloadAudio(url, File(fullAudioDir, key), downloadLimit) { received, total ->
                _audioDownloads.update { it + (key to AudioDownloadState(received, total)) }
            }
            ensureDiskRoom(key)
            completeAudio(key) != null
        } catch (cancelled: CancellationException) {
            _audioDownloads.update { it - key }
            throw cancelled
        } catch (failure: Exception) {
            _audioDownloads.update { states ->
                val reason = if (failure is MusicTransferException) failure.message else failure.javaClass.simpleName
                states + (key to (states[key] ?: AudioDownloadState()).copy(error = reason))
            }
            throw failure
        } finally {
            activeDownloads.remove(key)
        }
    }

    private fun protectedAudioFile(file: File): Boolean =
        file.name.startsWith("song-${playingTrackId}-") || activeDownloads.any { file.name.contains(it) }

    fun readLyrics(trackId: Long): List<LyricLine>? = runCatching {
        val array = JSONArray(File(lyricDir, "$trackId.v3.json").readText())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    LyricLine(
                        timeMs = item.getLong("time"),
                        text = item.getString("text"),
                        translation = item.optString("translation").takeIf { it.isNotEmpty() },
                        words = item.optJSONArray("words").toLyricWords(),
                    ),
                )
            }
        }
    }.getOrNull()

    fun writeLyrics(trackId: Long, lines: List<LyricLine>) {
        if (lines.isEmpty()) return
        val target = File(lyricDir, "$trackId.v3.json")
        val temp = File(lyricDir, "$trackId.tmp")
        val array = JSONArray().apply {
            lines.forEach { line ->
                put(
                    JSONObject()
                        .put("time", line.timeMs)
                        .put("text", line.text)
                        .put("translation", line.translation.orEmpty())
                        .apply { if (line.words.isNotEmpty()) put("words", line.words.toJson()) },
                )
            }
        }
        runCatching {
            temp.writeText(array.toString())
            check(temp.renameTo(target)) { "歌词缓存发布失败" }
        }.onFailure { temp.delete() }
    }

    suspend fun refreshStats(): Unit = withContext(Dispatchers.IO) {
        statsMutex.withLock {
            val source = synchronized(this@MusicCache) {
                if (!initialized) return@withContext
                StatsSource(statsGeneration, audio, fullAudioDir, File(root, "images"), lyricDir)
            }
            try {
                val result = MusicCacheStats(
                    audioBytes = source.cache.cacheSpace + source.fullAudio.scanSizeOnDisk(),
                    imageBytes = source.images.scanSizeOnDisk(),
                    lyricBytes = source.lyrics.scanSizeOnDisk(),
                )
                currentCoroutineContext().ensureActive()
                synchronized(this@MusicCache) {
                    if (initialized && source.generation == statsGeneration) _stats.value = result
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("MusicCache", "缓存统计失败：${failure.javaClass.simpleName}")
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        if (!initialized) return@withContext
        audio.keys.toList().filterNot { it.startsWith("song-${playingTrackId}-") }
            .forEach { key -> runCatching { audio.removeResource(key) } }
        fullAudioDir.listFiles().orEmpty().filterNot(::protectedAudioFile).forEach(File::delete)
        artworkLoader.memoryCache?.clear()
        artworkLoader.diskCache?.clear()
        lyricDir.listFiles().orEmpty().forEach(File::delete)
        refreshStats()
    }

    @Synchronized
    private fun requestStatsRefresh() {
        val scope = statsScope ?: return
        statsJob?.cancel()
        statsJob = scope.launch { refreshStats() }
    }
}

fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun File.sizeOnDisk(): Long = when {
    isFile -> length()
    isDirectory -> listFiles().orEmpty().sumOf(File::sizeOnDisk)
    else -> 0L
}

/** 后台统计逐目录响应取消，关闭后的任务不会继续遍历整棵缓存树。 */
private suspend fun File.scanSizeOnDisk(): Long {
    currentCoroutineContext().ensureActive()
    return when {
        isFile -> length()
        isDirectory -> {
            var total = 0L
            for (child in listFiles().orEmpty()) total += child.scanSizeOnDisk()
            total
        }
        else -> 0L
    }
}

/** 逐字片段存成 [文本, 起点, 终点] 三元组，省空间。 */
private fun List<LyricWord>.toJson(): JSONArray = JSONArray().apply {
    forEach { put(JSONArray().put(it.text).put(it.startMs).put(it.endMs)) }
}

private fun JSONArray?.toLyricWords(): List<LyricWord> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val w = optJSONArray(i) ?: return@mapNotNull null
        LyricWord(w.optString(0), w.optLong(1), w.optLong(2)).takeIf { it.text.isNotEmpty() }
    }
}
