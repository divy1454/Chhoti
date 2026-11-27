package com.divy.chhoti.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.divy.chhoti.BuildConfig
import com.divy.chhoti.ChatViewModel
import com.divy.chhoti.MessageListener
import com.divy.chhoti.Push
import com.divy.chhoti.Reminders
import com.divy.chhoti.Store
import com.divy.chhoti.Updater

@Composable
fun SettingsScreen(vm: ChatViewModel, back: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    LaunchedEffect(Unit) { vm.refreshLink() }

    var apiKey by remember { mutableStateOf(p.apiKey) }
    var name by remember { mutableStateOf(p.userName) }
    var address by remember { mutableStateOf(p.address) }
    var assistant by remember { mutableStateOf(p.assistantName) }
    var about by remember { mutableStateOf(p.about) }
    var model by remember { mutableStateOf(p.model) }
    var speak by remember { mutableStateOf(p.speakReplies) }
    var web by remember { mutableStateOf(p.webSearch) }
    var voice by remember { mutableStateOf(p.ttsVoice) }
    var serverUrl by remember { mutableStateOf(p.serverUrl) }
    var code by remember { mutableStateOf("") }
    val memories = remember { mutableStateListOf<Pair<Int, String>>().apply { addAll(Store.memories()) } }

    Column(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.ink) }
            Text("Settings", color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = C.line)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Profile card
            Row(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(18.dp)).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.assistantName, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Version ${BuildConfig.VERSION_NAME} · ${if (BuildConfig.FLAVOR == "full") "Full" else "Lite"} edition", color = C.muted, fontSize = 13.sp)
                }
            }

            Header("Server")
            Card {
                if (p.paired) {
                    Text(vm.linkStatus.ifBlank { "Connected." }, color = C.ink, fontSize = 15.sp, lineHeight = 21.sp)
                    Text("Channel info and calls come from the server next to Kyuniverse Studio. Your API key is kept there, not on this phone.",
                        color = C.muted, fontSize = 13.sp, lineHeight = 18.sp)
                    TextButton(onClick = { vm.unpair(); apiKey = p.apiKey }) { Text("Disconnect this phone", color = C.danger) }
                } else {
                    Field(serverUrl, { serverUrl = it }, "Server address", placeholder = "https://…ts.net:8443")
                    Field(code, { code = it.filter(Char::isDigit).take(8) }, "Pairing code", keyboard = KeyboardType.NumberPassword,
                        hint = "Ask for a code on the server; it arrives on Telegram. Pairing moves your API key to the server.")
                    Button(onClick = { vm.pair(serverUrl, code) }, enabled = !vm.pairing && code.length == 8 && serverUrl.isNotBlank()) { Text("Pair") }
                    if (vm.linkStatus.isNotBlank()) Text(vm.linkStatus, color = C.ink, fontSize = 14.sp)
                }
            }

            Header("Brain")
            Card {
                if (!p.paired) Field(apiKey, { apiKey = it; p.apiKey = it }, "Anthropic API key", secret = true,
                    hint = "From console.anthropic.com. Stays on this phone.")
                Field(model, { model = it; p.model = it }, "Model", hint = "claude-sonnet-5-5, or claude-haiku-5-5 for quicker, cheaper replies")
                Toggle("Search the web when needed", web) { web = it; p.webSearch = it }
            }

            Header("About you")
            Card {
                Field(name, { name = it; p.userName = it }, "Your name")
                Field(address, { address = it; p.address = it }, "What should I call you?")
                Field(assistant, { assistant = it; p.assistantName = it }, "My name")
                Field(about, { about = it; p.about = it }, "A few lines about you", lines = 3)
            }

            Header("Voice")
            Card {
                Toggle("Read every reply out loud", speak) { speak = it; p.speakReplies = it }
                Text("Replies to the mic are always spoken.", color = C.muted, fontSize = 13.sp)
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Voice", color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(if (voice.isBlank()) "Phone default" else voice, color = C.muted, fontSize = 14.sp, maxLines = 1)
                    }
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem({ Text("Phone default") }, { voice = ""; p.ttsVoice = ""; open = false })
                        for (v in vm.speaker.voiceNames()) {
                            DropdownMenuItem({ Text(v) }, {
                                voice = v; p.ttsVoice = v; vm.speaker.applyVoice(); open = false
                                vm.speaker.speak("Hi ${p.address}, this is how I sound.")
                            })
                        }
                    }
                }
            }

            Header("Permissions")
            androidx.compose.runtime.key(refresh) {
                Card {
                    if (BuildConfig.HAS_MESSAGES) Permission("Read message notifications", "WhatsApp, Instagram, Telegram, SMS…",
                        MessageListener.hasAccess(ctx)) { MessageListener.openAccessSettings(ctx) }
                    Permission("Exact reminder times", null, Reminders.canBeExact(ctx)) {
                        if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                    Permission("Install updates", null, Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx) }
                    if (p.paired) {
                        Permission("Microphone", "For calls",
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                        }
                        Permission("Full-screen incoming calls", "So my calls show up like a normal call", Push.canFullScreen(ctx)) {
                            Push.openFullScreenSettings(ctx)
                        }
                    }
                }
            }

            Header("Things I remember")
            Card {
                if (memories.isEmpty()) Text("Nothing yet. Tell me things like \"remember I prefer chai\".", color = C.muted, fontSize = 14.sp)
                for ((i, m) in memories.toList().withIndex()) {
                    if (i > 0) HorizontalDivider(color = C.line)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.second, color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        IconButton(onClick = { Store.deleteMemory(m.first); memories.remove(m) }) { Icon(Icons.Filled.Close, "Forget", tint = C.muted) }
                    }
                }
            }

            Header("Updates")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { vm.checkUpdate(manual = true) }) { Text("Check for updates") }
                    if (vm.update != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = vm::installUpdate, enabled = !vm.installing) { Text("Update to 1.0.${vm.update!!.versionCode}") }
                    }
                }
                if (vm.updateStatus.isNotBlank()) Text(vm.updateStatus, color = C.ink, fontSize = 14.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(title, color = C.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.surface, RoundedCornerShape(18.dp)).border(1.dp, C.line, RoundedCornerShape(18.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    placeholder: String? = null, hint: String? = null, secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1,
) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        supportingText = if (hint != null) { { Text(hint) } } else null,
        singleLine = lines == 1, minLines = lines,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = C.line, focusedLabelColor = C.accent))
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Permission(label: String, detail: String?, ok: Boolean, fix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = C.ink, fontSize = 15.sp)
            if (detail != null) Text(detail, color = C.muted, fontSize = 13.sp)
        }
        if (ok) Icon(Icons.Filled.Check, "Allowed", tint = C.good, modifier = Modifier.size(22.dp))
        else TextButton(onClick = fix) { Text("Allow") }
    }
}
