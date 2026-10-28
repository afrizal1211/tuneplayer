package com.tuneplayer.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.tuneplayer.app.R

/**
 * Type-safe navigation destinations matching ArchiveTune's Screens pattern.
 * Bottom nav maps to these; detail screens use argument routes.
 *
 * ponytail: artist/album detail routes added in Phase 3 when browse pages exist.
 */
sealed class Screens(
    val route: String,
    @StringRes val titleId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Library : Screens(
        route = "library",
        titleId = R.string.tab_library,
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
    )

    data object Home : Screens(
        route = "home",
        titleId = R.string.tab_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    )

    data object Search : Screens(
        route = "search",
        titleId = R.string.tab_youtube,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    )

    data object Queue : Screens(
        route = "queue",
        titleId = R.string.tab_queue,
        selectedIcon = Icons.Filled.QueueMusic,
        unselectedIcon = Icons.Outlined.QueueMusic,
    )

    data object Liked : Screens(
        route = "liked",
        titleId = R.string.tab_liked,
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Filled.Favorite,
    )

    data object History : Screens(
        route = "history",
        titleId = R.string.tab_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Filled.History,
    )

    data object Playlists : Screens(
        route = "playlists",
        titleId = R.string.tab_playlists,
        selectedIcon = Icons.Filled.PlaylistPlay,
        unselectedIcon = Icons.Filled.PlaylistPlay,
    )

    data object Stats : Screens(
        route = "stats",
        titleId = R.string.tab_stats,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Filled.BarChart,
    )

    data object Downloads : Screens(
        route = "downloads",
        titleId = R.string.tab_downloads,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Filled.Download,
    )

    data object Settings : Screens(
        route = "settings",
        titleId = R.string.tab_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    )

    companion object {
        /** Bottom bar shows these; the rest open through the More sheet. */
        val mainItems = listOf(Library, Home, Search, Queue, Liked)

        /** Everything reachable from the More sheet. */
        val overflowItems = listOf(History, Playlists, Downloads, Stats, Settings)

        val all by lazy {
            listOf(Library, Home, Search, Queue, Liked, History, Playlists, Stats, Downloads, Settings)
        }

        fun byRoute(route: String): Screens? = all.firstOrNull { it.route == route }
    }
}
