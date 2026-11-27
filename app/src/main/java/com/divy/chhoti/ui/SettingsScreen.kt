package com.divy.chhoti.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.divy.chhoti.BuildConfig
import com.divy.chhoti.Push
import com.divy.chhoti.ChatViewModel
import com.divy.chhoti.MessageListener
import com.divy.chhoti.Reminders
import com.divy.chhoti.Store
import com.divy.chhoti.Updater

@Composable
fun SettingsScreen(vm: ChatViewModel, back: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }

    var apiKey by remember { mutableStateOf(p.apiKey) }
    var name by remember { mutableStateOf(p.userName) }
    var address by remember { mutableStateOf(p.address) }
    var assistant by remember { mutableStateOf(p.assistantName) }
    var about by remember { mutableStateOf(p.about) }
    var model by remember { mutableStateOf(p.model) }
    var speak by remember { mutableStateOf(p.speakReplies) }
    var web by remember { mutableStateOf(p.webSearch) }
    var voice by remember { mutableStateOf(p.ttsVoice) }
    val memories = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(Store.memories()) } }

    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
            Text("Settings", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Section("Server (Chhoti Link)")
            var serverUrl by remember { mutableStateOf(p.serverUrl) }
            var code by remember { mutableStateOf("") }
            LaunchedEffect(Unit) { vm.refreshLink() }
            if (p.paired) {
                Text(vm.linkStatus.ifBlank { "Paired." }, color = Ink, fontSize = 14.sp)
                Text("Channel tools and calls come from the server next to Kyuniverse Studio. The Anthropic key lives there, not on this phone.",
                    color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = { vm.unpair(); apiKey = p.apiKey }) { Text("Unpair this phone") }
            } else {
                OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Server address") }, placeholder = { Text("https://…ts.net:8443") }, singleLine = true)
                OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(),
                    label = { Text("Pairing code (from Telegram)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = { Text("On the server: node ~/Chhoti-Link/server.mjs pair. Pairing moves your API key to the server.") })
                Button(onClick = { vm.pair(serverUrl, code) }, enabled = !vm.pairing && code.length == 8) { Text("Pair") }
                if (vm.linkStatus.isNotBlank()) Text(vm.linkStatus, color = Ink, fontSize = 13.sp)
            }

            Section("Brain")
            if (!p.paired) OutlinedTextField(apiKey, { apiKey = it; p.apiKey = it }, Modifier.fillMaxWidth(),
                label = { Text("Anthropic API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("From console.anthropic.com → API Keys. Stored only on this phone (not needed once paired).") })
            OutlinedTextField(model, { model = it; p.model = it }, Modifier.fillMaxWidth(),
                label = { Text("Model") }, singleLine = true,
                supportingText = { Text("claude-sonnet-5-5 (smart) or claude-haiku-5-5 (cheaper, faster)") })
            Toggle("Web search", web) { web = it; p.webSearch = it }

            Section("Personality")
            OutlinedTextField(assistant, { assistant = it; p.assistantName = it }, Modifier.fillMaxWidth(),
                label = { Text("Assistant name") }, singleLine = true)
            OutlinedTextField(name, { name = it; p.userName = it }, Modifier.fillMaxWidth(),
                label = { Text("Your name") }, singleLine = true)
            OutlinedTextField(address, { address = it; p.address = it }, Modifier.fillMaxWidth(),
                label = { Text("Address me as") }, singleLine = true)
            OutlinedTextField(about, { about = it; p.about = it }, Modifier.fillMaxWidth(),
                label = { Text("About you") }, minLines = 3)

            Section("Voice")
            Toggle("Speak every reply aloud", speak) { speak = it; p.speakReplies = it }
            Text("Replies to the mic button are always spoken.", color = Muted, fontSize = 12.sp)
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }) { Text(if (voice.isBlank()) "Voice: phone default" else "Voice: $voice", maxLines = 1) }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem({ Text("Phone default") }, { voice = ""; p.ttsVoice = ""; open = false })
                    for (v in vm.speaker.voiceNames()) {
                        DropdownMenuItem({ Text(v) }, {
                            voice = v; p.ttsVoice = v; vm.speaker.applyVoice(); open = false
                            vm.speaker.speak("Hello ${p.address}. This is how I sound.")
                        })
                    }
                }
            }

            Section("Permissions")
            key(refresh) {
                if (BuildConfig.HAS_MESSAGES) {
                    Status("Read message notifications (WhatsApp, Instagram, Telegram, SMS…)",
                        MessageListener.hasAccess(ctx)) { MessageListener.openAccessSettings(ctx) }
                }
                Status("Exact reminder times", Reminders.canBeExact(ctx)) {
                    if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                }
                Status("Install its own updates", Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx) }
                if (p.paired) {
                    Status("Microphone (for calls)", ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                    Status("Full-screen incoming calls", Push.canFullScreen(ctx)) { Push.openFullScreenSettings(ctx) }
                }
            }

            Section("Memories")
            if (memories.isEmpty()) Text("Nothing saved yet. Tell me things like \"remember I prefer chai\".", color = Muted, fontSize = 13.sp)
            for (m in memories.toList()) {
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(m.second, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { Store.deleteMemory(m.first); memories.remove(m) }) {
                        Icon(Icons.Filled.Close, "Delete", tint = Muted)
                    }
                }
            }

            Section("Updates")
            Text("Installed version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}, ${BuildConfig.FLAVOR} edition)", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.checkUpdate(manual = true) }) { Text("Check for updates") }
                if (vm.update != null) {
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = vm::installUpdate, enabled = !vm.installing) { Text("Update to v${vm.update!!.versionCode}") }
                }
            }
            if (vm.updateStatus.isNotBlank()) Text(vm.updateStatus, color = Ink, fontSize = 13.sp)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun key(k: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Cyan, fontSize = 12.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Status(label: String, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontSize = 14.sp)
            Text(if (ok) "Allowed" else "Not allowed", color = if (ok) Cyan else Danger, fontSize = 12.sp)
        }
        if (!ok) OutlinedButton(onClick = fix) { Text("Allow") }
    }
}
