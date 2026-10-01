package com.g1lg1l.stash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.g1lg1l.stash.data.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.autofill.ContentType as Autofill

/** Email and password, switching between signing in and creating the account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(create: Boolean) {
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(create) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val valid = email.isNotBlank() && password.length >= 6

    fun submit() {
        if (!valid || busy) return
        busy = true
        error = null
        note = null
        scope.launch {
            try {
                // No session yet means email confirmation is on: sign in once it's confirmed.
                note = Account.signIn(email.trim(), password, creating)
                if (note == null) backStack.removeLastOrNull() else creating = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = Account.describe(e)
            }
            busy = false
        }
    }

    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(if (creating) "Create account" else "Sign in") }, navigationIcon = { BackButton() }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                "Your saves sync across your devices. They stay on this phone either way.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                email, { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().semantics { contentType = Autofill.EmailAddress },
            )
            OutlinedTextField(
                password, { password = it },
                label = { Text("Password") },
                supportingText = if (creating) ({ Text("At least 6 characters") }) else null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = if (creating) Autofill.NewPassword else Autofill.Password },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            note?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            Button(::submit, Modifier.fillMaxWidth().padding(top = Spacing.xs).height(56.dp), enabled = valid && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text(if (creating) "Create account" else "Sign in", style = MaterialTheme.typography.titleMedium)
            }
            TextButton({ creating = !creating; error = null; note = null }, Modifier.fillMaxWidth()) {
                Text(if (creating) "Already have an account? Sign in" else "New to Stash? Create an account")
            }
        }
    }
}
