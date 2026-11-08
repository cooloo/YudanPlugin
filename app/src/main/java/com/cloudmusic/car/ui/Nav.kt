package com.cloudmusic.car.ui

import android.os.Bundle
import com.egogame.music.plugin.common.MusicPage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed interface Route {
    // 一级页面（侧边栏 / 底部标签）
    object Home : Route
    object Toplists : Route
    object Liked : Route
    object Recent : Route
    object Cloud : Route
    object MyPlaylists : Route
    object Search : Route
    object Settings : Route

    // 二级页面
    object Daily : Route
    data class PlaylistPage(val id: Long, val name: String, val cover: String?) : Route
}

/** 极简导航：一个一级页面 + 二级页面栈。 */
class Nav(savedState: Bundle? = null) : MusicPage {
    var root by mutableStateOf<Route>(Route.Home)
        private set
    val stack = mutableStateListOf<Route>()
    var nowPlayingOpen by mutableStateOf(false)
    var nowPlayingQueueOpen by mutableStateOf(false)

    var searchInput = savedState?.getString("searchInput").orEmpty()
    var searchSubmitted = savedState?.getString("searchSubmitted").orEmpty()
    var searchTab = savedState?.getString("searchTab") ?: "SONGS"

    init {
        savedState?.getBundle("root")?.let { root = decode(it) }
        savedState?.getParcelableArrayList<Bundle>("stack")?.forEach { stack.add(decode(it)) }
        nowPlayingOpen = savedState?.getBoolean("nowPlayingOpen") ?: false
        nowPlayingQueueOpen = savedState?.getBoolean("nowPlayingQueueOpen") ?: false
    }

    override fun save(): Bundle = Bundle().apply {
        putInt("schemaVersion", 1)
        putBundle("root", encode(root))
        putParcelableArrayList("stack", ArrayList(stack.map(::encode)))
        putBoolean("nowPlayingOpen", nowPlayingOpen)
        putBoolean("nowPlayingQueueOpen", nowPlayingQueueOpen)
        putString("searchInput", searchInput)
        putString("searchSubmitted", searchSubmitted)
        putString("searchTab", searchTab)
    }

    private fun encode(route: Route): Bundle = Bundle().apply {
        putString("route", when (route) {
            Route.Home -> "home"
            Route.Toplists -> "toplists"
            Route.Liked -> "liked"
            Route.Recent -> "recent"
            Route.Cloud -> "cloud"
            Route.MyPlaylists -> "playlists"
            Route.Search -> "search"
            Route.Settings -> "settings"
            Route.Daily -> "daily"
            is Route.PlaylistPage -> "playlist"
        })
        if (route is Route.PlaylistPage) {
            putLong("id", route.id)
            putString("name", route.name)
            putString("cover", route.cover)
        }
    }

    private fun decode(state: Bundle): Route = when (state.getString("route")) {
        "toplists" -> Route.Toplists
        "liked" -> Route.Liked
        "recent" -> Route.Recent
        "cloud" -> Route.Cloud
        "playlists" -> Route.MyPlaylists
        "search" -> Route.Search
        "settings" -> Route.Settings
        "daily" -> Route.Daily
        "playlist" -> Route.PlaylistPage(state.getLong("id"), state.getString("name").orEmpty(), state.getString("cover"))
        else -> Route.Home
    }

    val current: Route get() = stack.lastOrNull() ?: root

    fun select(route: Route) {
        root = route
        stack.clear()
    }

    fun open(route: Route) {
        stack.add(route)
    }

    /** @return 是否处理了返回。 */
    override fun back(): Boolean = when {
        nowPlayingQueueOpen -> { nowPlayingQueueOpen = false; true }
        nowPlayingOpen -> {
            nowPlayingOpen = false
            true
        }
        stack.isNotEmpty() -> {
            stack.removeAt(stack.lastIndex)
            true
        }
        root != Route.Home -> {
            root = Route.Home
            true
        }
        else -> false
    }
}
