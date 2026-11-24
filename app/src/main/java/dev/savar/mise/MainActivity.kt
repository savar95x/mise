@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.savar.mise

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.load(this)
        Sync.load(this)
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    DIARY("Diary", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    WATCHLIST("Watchlist", Icons.Outlined.BookmarkBorder, Icons.Rounded.Bookmark),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Full-screen pages that open over the tabs. */
sealed interface Route {
    data class TitlePage(val id: String) : Route
    data class Log(val titleId: String, val watchId: String? = null) : Route
    data object SyncPage : Route
}

/** Navigation and messages for every screen. */
class Nav(
    private val stack: MutableList<Route>,
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Direction of the last move, for the page transition. */
    var forward = true
        private set

    fun open(r: Route) { forward = true; stack += r }
    fun back() { forward = false; if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Replaces the top page (Log → its title page after saving). */
    fun replace(r: Route) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); forward = true; stack += r }
    /** After saving a log: back to that title's page, opening it if the log was started from search. */
    fun afterSave(titleId: String) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        forward = false
        if (stack.lastOrNull() != Route.TitlePage(titleId)) { forward = true; stack += Route.TitlePage(titleId) }
    }

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("no Nav") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.load(this)
        Sync.load(this)
        setContent { MiseTheme { MiseRoot() } }
    }

    override fun onStop() {
        super.onStop()
        Player.stop()
    }
}

@Composable
fun MiseRoot(
    preview: Boolean = false,
    startTab: Tab = Tab.DIARY,
    startRoute: Route? = null,
    startSearch: SearchFor? = null,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(startTab) }
    val stack = remember { mutableStateListOf<Route>().apply { startRoute?.let { add(it) } } }
    var search by remember { mutableStateOf(startSearch) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val nav = remember { Nav(stack, snackbar, scope) }

    if (!preview) LifecycleResumeEffect(Unit) {
        Updater.checkIfDue(ctx)
        Updater.resumeAfterPermission(ctx)
        Sync.syncIfStale(ctx)
        onPauseOrDispose { }
    }

    BackHandler(enabled = stack.isEmpty() && tab != Tab.DIARY) { tab = Tab.DIARY }

    CompositionLocalProvider(LocalNav provides nav) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { if (stack.isEmpty()) SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                        when (t) {
                            Tab.DIARY -> DiaryScreen(onLog = { search = SearchFor.LOG })
                            Tab.WATCHLIST -> WatchlistScreen(onAdd = { search = SearchFor.WATCHLIST })
                            Tab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }

            // Pages over the tabs, sliding in from the right like Android's own settings pages.
            val top = stack.lastOrNull()
            AnimatedContent(
                top,
                transitionSpec = {
                    if (nav.forward) {
                        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
                    } else {
                        fadeIn(tween(160)) togetherWith (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(180)))
                    }
                },
                label = "page",
            ) { r ->
                if (r != null) {
                    BackHandler { nav.back() }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.surface,
                            snackbarHost = { SnackbarHost(snackbar) },
                            // The title page draws its backdrop under the status bar and pads itself.
                            contentWindowInsets = if (r is Route.TitlePage) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                        ) { pad ->
                            Box(Modifier.fillMaxSize().padding(pad)) {
                                when (r) {
                                    is Route.TitlePage -> TitleScreen(r.id)
                                    is Route.Log -> LogScreen(r.titleId, r.watchId)
                                    Route.SyncPage -> SyncScreen()
                                }
                            }
                        }
                    }
                }
            }
        }

        search?.let { purpose ->
            SearchSheet(purpose, onDismiss = { search = null }) { title ->
                search = null
                when (purpose) {
                    SearchFor.LOG -> nav.open(Route.Log(title.id))
                    SearchFor.WATCHLIST -> {
                        Store.setWatchlist(title.id, true)
                        nav.toast("Added ${title.name} to your watchlist")
                    }
                }
            }
        }
    }
}
