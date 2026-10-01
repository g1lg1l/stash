package com.g1lg1l.stash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.g1lg1l.stash.data.Source
import com.g1lg1l.stash.data.Stash
import com.g1lg1l.stash.data.UrlSourceDetector
import com.g1lg1l.stash.data.displayName
import com.g1lg1l.stash.ui.Spacing
import com.g1lg1l.stash.ui.StashTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Share → Stash → saved → gone. No form, no choices; organizing happens later in the app.
 * Handles the share sheet and "Save to Stash" in the text selection menu.
 */
class ShareActivity : ComponentActivity() {
    private sealed interface State {
        data object Saving : State
        data class Saved(val source: Source) : State
        data object NoLink : State
        data object Failed : State
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val state = mutableStateOf<State>(State.Saving)
        val analysis = UrlSourceDetector.sharedLink(sharedTexts(intent))?.let(UrlSourceDetector::analyze)
        if (analysis == null) {
            state.value = State.NoLink
        } else {
            // The app's scope, not the activity's: the save lands even if the sheet is gone first.
            Stash.scope.launch {
                state.value = try {
                    Stash.dao.add(analysis, System.currentTimeMillis())
                    State.Saved(analysis.source)
                } catch (e: Exception) {
                    State.Failed
                }
                Stash.refresh()
            }
        }
        setContent { StashTheme { ShareSheet(state.value, onDone = ::finish) } }
    }

    /** Apps share links in different shapes: the link as text, text containing it, a selection, or the data URI. */
    private fun sharedTexts(intent: Intent): List<String> = listOfNotNull(
        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
        intent.dataString,
    ).map { it.toString() } + (0 until (intent.clipData?.itemCount ?: 0)).mapNotNull { index ->
        intent.clipData?.getItemAt(index)?.let { it.text?.toString() ?: it.uri?.toString() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ShareSheet(state: State, onDone: () -> Unit) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        fun dismiss() {
            scope.launch { sheet.hide() }.invokeOnCompletion { onDone() }
        }
        LaunchedEffect(state) {
            // Save and disappear: leave on our own shortly after success.
            if (state !is State.Saved) return@LaunchedEffect
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(1200)
            dismiss()
        }

        ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(bottom = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                when (state) {
                    State.Saving -> CircularProgressIndicator(Modifier.padding(Spacing.xl))
                    is State.Saved -> Message(
                        R.drawable.ic_check_circle_filled, "Saved to Stash",
                        if (state.source == Source.WEB) "Stash will organize it for you."
                        else "From ${state.source.displayName}. Stash will organize it for you.",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    State.NoLink -> Message(R.drawable.ic_add_link, "No link to save", "Stash saves links. Try sharing the page or post itself.")
                    State.Failed -> Message(R.drawable.ic_add_link, "Couldn't save", "Something went wrong. Try sharing it again.")
                }
                Button(::dismiss, Modifier.fillMaxWidth().padding(top = Spacing.s).height(56.dp)) {
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    @Composable
    private fun Message(icon: Int, title: String, detail: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
