package com.egogame.music.plugin.common

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import coil.ImageLoader
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.egogame.plugin.api.PluginTransfers
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicTransferException(val code: String, message: String) : IOException(
    "$code：${message.replace(Regex("https?://[^\\s]+"), "下载地址")}",
)

suspend fun PluginTransfers.previewResponse(request: PluginTransfers.Request): PluginTransfers.PreviewResult =
    suspendCancellableCoroutine { continuation ->
        val handle = previewWithMetadata(request, object : PluginTransfers.Callback<PluginTransfers.PreviewResult> {
            override fun onSuccess(result: PluginTransfers.PreviewResult) {
                if (continuation.isActive) continuation.resume(result)
            }
            override fun onFailure(code: String, message: String) {
                if (continuation.isActive) continuation.resumeWithException(MusicTransferException(code, message))
            }
        })
        continuation.invokeOnCancellation { handle.cancel() }
    }

/** 图片只经宿主公共预览后端读取，不复用账号客户端的身份头。 */
class HostImageFetcher(private val uri: Uri, private val options: Options, private val transfers: PluginTransfers) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val bytes = suspendCancellableCoroutine<ByteArray> { continuation ->
            val handle = transfers.preview(
                PluginTransfers.Request(uri.toString(), emptyMap(), 20L * 1024 * 1024, 30_000),
                object : PluginTransfers.Callback<ByteArray> {
                    override fun onSuccess(result: ByteArray) { if (continuation.isActive) continuation.resume(result) }
                    override fun onFailure(code: String, message: String) {
                        if (continuation.isActive) continuation.resumeWithException(MusicTransferException(code, message))
                    }
                },
            )
            continuation.invokeOnCancellation { handle.cancel() }
        }
        return SourceResult(ImageSource(Buffer().write(bytes), options.context), null, coil.decode.DataSource.NETWORK)
    }

    class Factory(private val transfers: PluginTransfers) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "http" || data.scheme == "https") HostImageFetcher(data, options, transfers) else null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class HostMediaDataSource(private val transfers: PluginTransfers) : BaseDataSource(true) {
    private var stream: PluginTransfers.MediaStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var signal = StreamCancellation()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        signal = StreamCancellation()
        val opened = transfers.openMedia(
            PluginTransfers.Request(dataSpec.uri.toString(), dataSpec.httpRequestHeaders, Long.MAX_VALUE, 35_000),
            dataSpec.position, dataSpec.length, signal,
        )
        stream = opened
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else opened.length()
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val amount = if (remaining < 0) length else minOf(length.toLong(), remaining).toInt()
        val count = stream?.body()?.read(buffer, offset, amount) ?: throw IOException("音频流未打开")
        if (count < 0) {
            if (remaining > 0) throw IOException("音频流提前结束")
            return C.RESULT_END_OF_INPUT
        }
        if (remaining > 0) remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val opened = stream
        stream = null
        uri = null
        try { signal.cancel() } finally {
            try { opened?.close() } finally { if (opened != null) transferEnded() }
        }
    }

    class Factory(private val transfers: PluginTransfers) : DataSource.Factory {
        override fun createDataSource(): DataSource = HostMediaDataSource(transfers)
    }
}

private class StreamCancellation : PluginTransfers.CancellationSignal {
    @Volatile private var cancelled = false
    private val listener = AtomicReference<Runnable?>()
    override fun isCancelled(): Boolean = cancelled
    override fun onCancel(value: Runnable) {
        listener.set(value)
        if (cancelled) listener.getAndSet(null)?.run()
    }
    fun cancel() {
        cancelled = true
        listener.getAndSet(null)?.run()
    }
}

data class AudioDownloadState(val received: Long = 0, val total: Long = -1, val error: String? = null)

suspend fun PluginTransfers.downloadAudio(
    url: String,
    destination: File,
    maxBytes: Long = 512L * 1024 * 1024,
    progress: (Long, Long) -> Unit,
): File =
    suspendCancellableCoroutine { continuation ->
        val handle = download(
            PluginTransfers.Request(url, emptyMap(), maxBytes, 120_000), destination, null,
            PluginTransfers.Progress { received, total -> progress(received, total) },
            object : PluginTransfers.Callback<File> {
                override fun onSuccess(result: File) { if (continuation.isActive) continuation.resume(result) }
                override fun onFailure(code: String, message: String) {
                    if (continuation.isActive) continuation.resumeWithException(MusicTransferException(code, message))
                }
            },
        )
        continuation.invokeOnCancellation { handle.cancel() }
    }
