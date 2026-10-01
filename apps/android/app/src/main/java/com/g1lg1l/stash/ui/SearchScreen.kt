package com.g1lg1l.stash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.g1lg1l.stash.R
import com.g1lg1l.stash.data.Save
import com.g1lg1l.stash.data.Search

@Composable
fun SearchScreen(saves: List<Save>) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val results = Search.results(query, saves)
    val list = rememberLazyListState()
    // Scrolling the results puts the keyboard away.
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focus.clearFocus() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
            placeholder = { Text("Titles, tags, sources") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear") }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        when {
            saves.isEmpty() -> EmptyState(R.drawable.ic_search, "Nothing to search yet", "Everything you share to Stash becomes searchable here.")
            query.isNotBlank() && results.isEmpty() -> EmptyState(R.drawable.ic_search, "No results for “${query.trim()}”", "Check the spelling or try a new search.")
            else -> LazyColumn(state = list, contentPadding = PaddingValues(bottom = Spacing.xl)) {
                if (query.isBlank()) {
                    item(key = "recent") {
                        Text(
                            "Recently saved",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        )
                    }
                }
                items(if (query.isBlank()) saves.take(8) else results, key = { it.id }) { save ->
                    SwipeableSaveRow(save, Modifier.animateItem())
                }
            }
        }
    }
}
