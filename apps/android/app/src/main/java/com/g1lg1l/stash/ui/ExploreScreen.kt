package com.g1lg1l.stash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.g1lg1l.stash.CategoryFeed
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Category
import com.g1lg1l.stash.data.FeedSection
import com.g1lg1l.stash.data.Rediscovery
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.displayName
import com.g1lg1l.stash.data.displayTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(saves: List<Save>) {
    val backStack = LocalBackStack.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val picks = if (Prefs.showRediscovery) Rediscovery.picks(saves) else emptyList()
    // Only categories the user actually has, biggest first. "Other" is a leftover bucket, so it goes last.
    val categories = saves.groupBy { it.category }.toList()
        .sortedWith(compareBy({ it.first == Category.OTHER }, { -it.second.size }, { it.first.displayName }))
    // One column at the largest text sizes, like iOS at accessibility sizes.
    val tileWidth = 160.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Explore") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (saves.isEmpty()) {
            EmptyState(R.drawable.ic_grid_view, "Nothing to explore yet", "Categories appear here as your stash grows.", Modifier.padding(padding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(tileWidth),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (picks.isNotEmpty()) {
                    item(key = "rediscovery", span = { GridItemSpan(maxLineSpan) }) {
                        RediscoveryRow(picks, Modifier.bleed(Spacing.m).padding(bottom = Spacing.s))
                    }
                    item(key = "categories", span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Categories") }
                }
                items(categories, key = { it.first }) { (category, group) ->
                    CategoryTile(category, group) { backStack.open(CategoryFeed(category)) }
                }
            }
        }
    }
}

@Composable
private fun RediscoveryRow(saves: List<Save>, modifier: Modifier) {
    val state = rememberLazyListState()
    val cardWidth = (240.dp * LocalDensity.current.fontScale).coerceAtMost(320.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            SectionTitle("Worth another look")
            Text("You saved these a while ago.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            contentPadding = PaddingValues(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(saves, key = { it.id }) { save ->
                SaveItem(save, Modifier.width(cardWidth)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SaveThumbnail(
                            save,
                            Modifier.fillMaxWidth().aspectRatio(4f / 3).sharedMedia(save).clip(RoundedCornerShape(Radius.card)),
                        )
                        Text(save.displayTitle, style = MaterialTheme.typography.titleMedium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Saved ${relativeTime(save.lastSavedAt)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Cover image from the newest save that has one; tinted placeholder otherwise. */
@Composable
private fun CategoryTile(category: Category, saves: List<Save>, onClick: () -> Unit) {
    val count = if (saves.size == 1) "1 save" else "${saves.size} saves"
    Box(
        Modifier
            .aspectRatio(4f / 5)
            .pressable(onClickLabel = "Open", onClick = onClick)
            .clip(RoundedCornerShape(Radius.card))
            .clearAndSetSemantics { contentDescription = "${category.displayName}, $count" },
    ) {
        SaveThumbnail(saves.firstOrNull { it.thumbnailUrl != null } ?: saves.first(), Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .padding(Spacing.m),
        ) {
            Text(category.displayName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(count, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(category: Category, saves: List<Save>) {
    val shown = saves.filter { it.category == category }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(category.displayName) }, navigationIcon = { BackButton() }, scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SaveFeed(
                sections = listOf(FeedSection(null, shown)),
                header = { modifier ->
                    Text(
                        if (shown.size == 1) "1 save" else "${shown.size} saves",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = modifier.padding(horizontal = Spacing.m),
                    )
                },
            )
        }
    }
}
