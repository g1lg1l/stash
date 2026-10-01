package com.g1lg1l.stash

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.Sync
import com.g1lg1l.stash.ui.AuthScreen
import com.g1lg1l.stash.ui.CategoryScreen
import com.g1lg1l.stash.ui.DetailScreen
import com.g1lg1l.stash.ui.ExploreScreen
import com.g1lg1l.stash.ui.HomeScreen
import com.g1lg1l.stash.ui.LocalBackStack
import com.g1lg1l.stash.ui.LocalSharedTransition
import com.g1lg1l.stash.ui.LocalSnackbar
import com.g1lg1l.stash.ui.Prefs
import com.g1lg1l.stash.ui.SearchScreen
import com.g1lg1l.stash.ui.SettingsScreen
import com.g1lg1l.stash.ui.StashTheme
import com.g1lg1l.stash.ui.TipsScreen
import com.g1lg1l.stash.ui.WelcomeScreen
import com.g1lg1l.stash.ui.open
import com.g1lg1l.stash.widget.StashWidget
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object Tabs : NavKey
@Serializable data class Detail(val id: String) : NavKey
@Serializable data class CategoryFeed(val category: Category) : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Tips : NavKey
@Serializable data object Welcome : NavKey
@Serializable data class Auth(val create: Boolean) : NavKey

class MainActivity : ComponentActivity() {
    /** A save to open, from the widget (`stash://save/<id>`). */
    private val openSave = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug builds: `adb shell am start -n com.g1lg1l.stash/.MainActivity --ez sampleData true`
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent.getBooleanExtra("sampleData", false) && !Stash.isSample) Stash.open(this, sample = true)
        if (savedInstanceState == null) openSave.value = saveId(intent)
        setContent { StashTheme { StashRoot(openSave) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSave.value = saveId(intent)
    }

    override fun onStart() {
        super.onStart()
        // Fill in anything shared while Stash was in the background, and sync.
        Stash.scope.launch { Stash.refresh() }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is when saves have changed: refresh the Home Screen widget here, and push them.
        Stash.scope.launch {
            StashWidget().updateAll(applicationContext)
            Sync.sync()
        }
    }

    private fun saveId(intent: Intent) = intent.data?.takeIf { it.scheme == "stash" && it.host == "save" }?.lastPathSegment
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StashRoot(openSave: MutableState<String?>) {
    val backStack = rememberNavBackStack(if (Prefs.seenWelcome) Tabs else Welcome)
    val snackbar = remember { SnackbarHostState() }
    // Blank for the first few milliseconds rather than flashing an empty stash.
    val saves = Stash.saves.collectAsStateWithLifecycle().value ?: return

    LaunchedEffect(openSave.value) {
        openSave.value?.let { backStack.open(Detail(it)) }
        openSave.value = null
    }

    CompositionLocalProvider(LocalBackStack provides backStack, LocalSnackbar provides snackbar) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransition provides this) {
                NavDisplay(
                    backStack = backStack,
                    sharedTransitionScope = this,
                    entryProvider = entryProvider {
                        entry<Tabs> { TabsScreen(saves) }
                        entry<Detail> { route -> DetailScreen(saves.find { it.id == route.id }) }
                        entry<CategoryFeed> { route -> CategoryScreen(route.category, saves) }
                        entry<Settings> { SettingsScreen(saves) }
                        entry<Tips> { TipsScreen() }
                        entry<Welcome> { WelcomeScreen() }
                        entry<Auth> { route -> AuthScreen(route.create) }
                    },
                )
            }
        }
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    HOME("Home", R.drawable.ic_home, R.drawable.ic_home_filled),
    EXPLORE("Explore", R.drawable.ic_grid_view, R.drawable.ic_grid_view_filled),
    SEARCH("Search", R.drawable.ic_search, R.drawable.ic_search),
}

@Composable
private fun TabsScreen(saves: List<Save>) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Each tab keeps its scroll position while another one is showing.
    val tabStates = rememberSaveableStateHolder()
    // Back from another tab goes Home first, as in most Android apps.
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(painterResource(if (tab == it) it.selectedIcon else it.icon), contentDescription = null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            tabStates.SaveableStateProvider(tab) {
                when (tab) {
                    Tab.HOME -> HomeScreen(saves)
                    Tab.EXPLORE -> ExploreScreen(saves)
                    Tab.SEARCH -> SearchScreen(saves)
                }
            }
        }
    }
}
