@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.mise

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SyncScreen() {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val c = Sync.config
    var url by remember { mutableStateOf(c.url) }
    var key by remember { mutableStateOf(c.key) }
    var email by remember { mutableStateOf(c.email) }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun auth(create: Boolean) {
        Sync.setProject(ctx, url, key)
        working = true
        message = null
        scope.launch {
            val r = Sync.signIn(ctx, email.trim(), password, create)
            working = false
            message = r.getOrElse { it.message }
            if (r.isSuccess) password = ""
        }
    }

    Page("Supabase sync", onBack = { nav.back() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                "Keeps your journal in your own free Supabase project, so it's there on a new phone: titles, watchlist, " +
                    "thoughts, scores. Voice memos stay on this phone (they'd fill the free 1 GB); Back up everything saves them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Group("1 · Project") {
                item {
                    Pad {
                        Text(
                            "Create a free project at supabase.com. In its SQL editor, paste the setup SQL and run it once.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Mise setup SQL", Sync.SETUP_SQL))
                                nav.toast("Setup SQL copied")
                            }) {
                                androidx.compose.material3.Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Copy setup SQL")
                            }
                            FilledTonalButton(onClick = { openUrl(ctx, "https://supabase.com/dashboard/projects") }) {
                                Text("Dashboard")
                                Spacer(Modifier.size(6.dp))
                                androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
                item {
                    Pad {
                        Text("Then from Project Settings → API (or Connect):", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            url, { url = it.trim() }, label = { Text("Project URL") }, placeholder = { Text("https://xyz.supabase.co") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            key, { key = it.trim() }, label = { Text("anon / publishable key") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Group("2 · Account") {
                if (c.signedIn && c.url == url.trimEnd('/')) {
                    item {
                        NavRow(
                            Icons.Rounded.Sync,
                            if (Sync.busy) "Syncing…" else "Sync now",
                            "${c.email} · last synced ${Fmt.ago(Sync.lastSync)}" + (Sync.error?.let { "\n$it" } ?: ""),
                        ) { Sync.syncNow(ctx) }
                    }
                    item {
                        NavRow(Icons.AutoMirrored.Rounded.Logout, "Sign out", "Your journal stays on this phone") { Sync.signOut(ctx) }
                    }
                } else {
                    item {
                        Pad {
                            OutlinedTextField(
                                email, { email = it }, label = { Text("Email") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                password, { password = it }, label = { Text("Password") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(16.dp))
                            val ready = url.isNotBlank() && key.isNotBlank() && email.contains('@') && password.length >= 6 && !working
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { auth(create = false) }, enabled = ready) { Text("Sign in") }
                                FilledTonalButton(onClick = { auth(create = true) }, enabled = ready) { Text("Create account") }
                                if (working) LoadingIndicator(Modifier.size(36.dp))
                            }
                        }
                    }
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}
