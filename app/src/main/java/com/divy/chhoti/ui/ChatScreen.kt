package com.divy.chhoti.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divy.chhoti.ChatItem
import com.divy.chhoti.ChatViewModel
import com.divy.chhoti.R
import java.util.Calendar

@Composable
fun ChatScreen(vm: ChatViewModel, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) vm.send(heard, voice = true)
    }

    LaunchedEffect(vm.items.size, vm.busy) {
        val count = vm.items.size + if (vm.busy) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(
        Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Reactor(Modifier.size(28.dp), active = vm.busy)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.prefs.assistantName.uppercase(), color = Ink, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(if (vm.busy) "processing…" else "online", color = if (vm.busy) Cyan else Muted,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            if (vm.items.isNotEmpty()) {
                IconButton(onClick = { vm.speaker.stop(); vm.clearChat() }) {
                    Icon(Icons.Filled.Delete, "Clear chat", tint = Muted)
                }
            }
            IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Muted) }
        }

        // Update banner
        vm.update?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(PanelHi, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Update available: v${r.versionCode}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (vm.updateStatus.isNotBlank()) Text(vm.updateStatus, color = Muted, fontSize = 12.sp)
                }
                Button(onClick = vm::installUpdate, enabled = !vm.installing) { Text("Update") }
            }
        }

        // Messages
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.items.isEmpty() && !vm.busy) {
                Greeting(vm, Modifier.align(Alignment.Center)) { vm.send(it) }
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Spacer(Modifier.size(4.dp)) }
                items(vm.items) { item -> Bubble(item, vm) }
                if (vm.busy) item { Thinking() }
                item { Spacer(Modifier.size(4.dp)) }
            }
        }

        // Input
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Talk to ${vm.prefs.assistantName}…", color = Muted) },
                maxLines = 5, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = PanelHi,
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                ),
            )
            Spacer(Modifier.width(8.dp))
            if (input.isBlank()) {
                FilledIconButton(
                    onClick = {
                        vm.speaker.stop()
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening, ${vm.prefs.address}…")
                        try { speech.launch(i) } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, "No speech recognizer found. Install the Google app.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.size(54.dp), enabled = !vm.busy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Bg),
                ) { Icon(painterResource(R.drawable.ic_mic), "Speak") }
            } else {
                FilledIconButton(
                    onClick = { vm.send(input); input = "" },
                    modifier = Modifier.size(54.dp), enabled = !vm.busy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Bg),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}

@Composable
private fun Greeting(vm: ChatViewModel, modifier: Modifier, onPick: (String) -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val part = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; else -> "evening" }
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Reactor(Modifier.size(150.dp), active = true)
        Spacer(Modifier.size(24.dp))
        Text("Good $part, ${vm.prefs.address}.", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text("How can I help?", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.size(20.dp))
        for (s in listOf("Any new messages for me?", "Remind me in 10 minutes to stretch", "What's the latest on GTA 6?")) {
            OutlinedButton(onClick = { onPick(s) }, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(s, color = Cyan, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Bubble(item: ChatItem, vm: ChatViewModel) {
    val user = item.role == "user"
    val error = item.role == "error"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(
                    when { user -> CyanDim.copy(alpha = 0.35f); error -> Danger.copy(alpha = 0.15f); else -> Panel },
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (user) 16.dp else 4.dp, bottomEnd = if (user) 4.dp else 16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(item.text, color = if (error) Danger else Ink, fontSize = 15.sp, lineHeight = 21.sp)
            }
            for (card in item.actions) {
                Spacer(Modifier.size(10.dp))
                Column(Modifier.fillMaxWidth().border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("APPROVAL NEEDED · #${card.id}", color = Cyan, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(Modifier.size(4.dp))
                    Text(card.summary, color = Ink, fontSize = 14.sp)
                    Spacer(Modifier.size(8.dp))
                    val result = card.result
                    if (result == null) {
                        Row {
                            Button(onClick = { vm.decide(card, true) }) { Text("Approve") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { vm.decide(card, false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("Reject") }
                        }
                    } else {
                        Text(result, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Thinking() {
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Text("● ● ●", color = Cyan, modifier = Modifier.padding(start = 8.dp).alpha(a), fontSize = 12.sp)
}

/** Animated arc-reactor mark. */
@Composable
fun Reactor(modifier: Modifier, active: Boolean) {
    val t = rememberInfiniteTransition(label = "reactor")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (active) 2400 else 9000, easing = LinearEasing)), label = "spin")
    val glow by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Cyan.copy(alpha = 0.10f * glow), r, c)
        drawCircle(Cyan.copy(alpha = 0.9f), r * 0.94f, c, style = Stroke(width = r * 0.06f))
        val inner = r * 0.66f
        for (k in 0 until 4) {
            drawArc(Cyan.copy(alpha = 0.75f), spin + k * 90f, 60f, false,
                topLeft = Offset(c.x - inner, c.y - inner), size = Size(inner * 2, inner * 2),
                style = Stroke(width = r * 0.14f))
        }
        drawCircle(Color(0xFFE8FBFF).copy(alpha = glow), r * 0.28f, c)
        drawCircle(Cyan.copy(alpha = 0.35f * glow), r * 0.42f, c)
    }
}

@Suppress("unused")
private val pillShape = CircleShape
