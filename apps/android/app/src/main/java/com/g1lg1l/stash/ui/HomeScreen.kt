package com.g1lg1l.stash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.g1lg1l.stash.R
import com.g1lg1l.stash.Settings
import com.g1lg1l.stash.data.ContentType
import com.g1lg1l.stash.data.FeedSection
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.displayName
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(saves: List<Save>) {
    val backStack = LocalBackStack.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var unseenOnly by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf<ContentType?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val shown = saves.filter { (!unseenOnly || it.openedAt == null) && (type == null || it.contentType == type) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(greeting()) },
                actions = {
                    if (saves.isNotEmpty()) {
                        val compact = Prefs.feedLayout == FeedLayout.COMPACT
                        IconButton({
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            Prefs.feedLayout = if (compact) FeedLayout.CARDS else FeedLayout.COMPACT
                        }) {
                            Icon(
                                painterResource(if (compact) R.drawable.ic_view_agenda else R.drawable.ic_view_list),
                                contentDescription = if (compact) "Show as cards" else "Show as list",
                            )
                        }
                    }
                    IconButton({ backStack.open(Settings) }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    Stash.refresh(retryFailed = true)
                    refreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
            if (saves.isEmpty()) {
                // Scrollable, so pull to refresh still works on an empty stash.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyState(R.drawable.ic_inbox, "Your stash is empty.", "When you find something worth keeping,\nshare it to Stash.")
                }
            } else {
                SaveFeed(
                    sections = FeedSection.byDay(shown),
                    header = { modifier ->
                        HomeHeader(saves, unseenOnly, type, modifier, onUnseenOnly = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            unseenOnly = it
                        }, onType = { type = it })
                    },
                    empty = if (shown.isEmpty()) {
                        {
                            EmptyState(
                                R.drawable.ic_search, "Nothing matches", "No saves match these filters.",
                                action = "Show all" to { unseenOnly = false; type = null },
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

/** "12 saved · 3 unseen", then the filters: Unseen, and only the content types the user has. */
@Composable
private fun HomeHeader(
    saves: List<Save>, unseenOnly: Boolean, type: ContentType?, modifier: Modifier,
    onUnseenOnly: (Boolean) -> Unit, onType: (ContentType?) -> Unit,
) {
    val unseen = saves.count { it.openedAt == null }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            if (unseen == 0) "${saves.size} saved" else "${saves.size} saved · $unseen unseen",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            item {
                FilterChip(
                    selected = unseenOnly,
                    onClick = { onUnseenOnly(!unseenOnly) },
                    label = { Text("Unseen") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            items(ContentType.entries.filter { t -> saves.any { it.contentType == t } }) { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { onType(if (type == option) null else option) },
                    label = { Text(option.displayName) },
                    leadingIcon = { Icon(painterResource(option.icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

/**
 * Saves as large cards or compact rows, following the layout chosen on Home.
 * Cards flow into more columns on wide screens.
 */
@Composable
fun SaveFeed(
    sections: List<FeedSection>,
    header: (@Composable (Modifier) -> Unit)? = null,
    empty: (@Composable () -> Unit)? = null,
) {
    when (Prefs.feedLayout) {
        FeedLayout.CARDS -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalItemSpacing = Spacing.l,
        ) {
            header?.let { item(key = "header", span = StaggeredGridItemSpan.FullLine) { it(Modifier.bleed(Spacing.m)) } }
            empty?.let { item(key = "empty", span = StaggeredGridItemSpan.FullLine) { it() } }
            for (section in sections) {
                section.title?.let { title ->
                    item(key = "section-$title", span = StaggeredGridItemSpan.FullLine) {
                        SectionTitle(title, Modifier.padding(top = Spacing.xs).animateItem())
                    }
                }
                items(section.saves, key = { it.id }) { save ->
                    SaveItem(save, Modifier.animateItem()) { SaveCard(save) }
                }
            }
        }
        FeedLayout.COMPACT -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xl)) {
            header?.let { item(key = "header") { it(Modifier.padding(bottom = Spacing.xs)) } }
            empty?.let { item(key = "empty") { it() } }
            for (section in sections) {
                section.title?.let { title ->
                    stickyHeader(key = "section-$title") {
                        Surface(Modifier.fillMaxWidth()) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).semantics { heading() },
                            )
                        }
                    }
                }
                items(section.saves, key = { it.id }) { save -> SwipeableSaveRow(save, Modifier.animateItem()) }
            }
        }
    }
}

private fun greeting() = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
