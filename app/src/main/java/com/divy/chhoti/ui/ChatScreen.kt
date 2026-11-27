package com.divy.chhoti.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divy.chhoti.CallActivity
import com.divy.chhoti.ChatItem
import com.divy.chhoti.ChatViewModel
import com.divy.chhoti.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(vm: ChatViewModel, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) vm.send(heard, voice = true)
    }
    fun listen() {
        vm.speaker.stop()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Go ahead")
        try { speech.launch(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No speech recognizer found. Install the Google app.", Toast.LENGTH_LONG).show()
        }
    }
    fun sendTyped() {
        if (input.isNotBlank() && !vm.busy) { vm.send(input); input = "" }
    }

    LaunchedEffect(vm.items.size, vm.busy) {
        val count = vm.items.size + if (vm.busy) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopBar(vm, openSettings) {
            vm.speaker.stop()
            ctx.startActivity(Intent(ctx, CallActivity::class.java).putExtra(CallActivity.EXTRA_MANUAL, true))
        }

        vm.task?.let { (title, step) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .background(C.surface, RoundedCornerShape(14.dp)).border(1.dp, C.line, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), color = C.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Working on: $title", color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(step, color = C.muted, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = vm::stopTask) { Text("Stop", color = C.danger) }
            }
        }

        vm.update?.let { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .background(C.sunSoft, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A new version is ready (1.0.${r.versionCode})", color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (vm.updateStatus.isNotBlank() && vm.installing) Text(vm.updateStatus, color = C.muted, fontSize = 12.sp)
                }
                TextButton(onClick = vm::installUpdate, enabled = !vm.installing) { Text("Update", fontWeight = FontWeight.SemiBold) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.items.isEmpty() && !vm.busy) Welcome(vm, Modifier.align(Alignment.Center)) { vm.send(it) }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                item { Spacer(Modifier.height(8.dp)) }
                itemsIndexed(vm.items) { i, item ->
                    val prev = vm.items.getOrNull(i - 1)
                    val firstOfGroup = prev == null || prev.role != item.role
                    Column(Modifier.fillMaxWidth()) {
                        if (prev != null && !sameDay(prev.time, item.time) && item.time > 0) DayLabel(item.time)
                        Spacer(Modifier.height(if (firstOfGroup) 10.dp else 3.dp))
                        when (item.role) {
                            "user" -> Mine(item)
                            "assistant" -> Hers(item, vm, showFace = firstOfGroup)
                            "note" -> Note(item.text)
                            else -> ErrorLine(item) { vm.retry(item) }
                        }
                    }
                }
                if (vm.busy) item { Column { Spacer(Modifier.height(10.dp)); Typing() } }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }

        // Typing bar
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", color = C.muted) },
                maxLines = 6, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendTyped() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.surface, unfocusedContainerColor = C.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    cursorColor = C.accent, focusedTextColor = C.ink, unfocusedTextColor = C.ink,
                ),
            )
            Spacer(Modifier.width(8.dp))
            val typing = input.isNotBlank()
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (vm.busy) C.line else C.accent)
                .clickable(enabled = !vm.busy) { if (typing) sendTyped() else listen() },
                contentAlignment = Alignment.Center) {
                if (typing) Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = C.onAccent)
                else Icon(painterResource(R.drawable.ic_mic), "Speak", tint = C.onAccent)
            }
        }
    }
}

@Composable
private fun TopBar(vm: ChatViewModel, openSettings: () -> Unit, call: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.prefs.assistantName, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(if (vm.busy) "typing…" else if (vm.prefs.canThink) "online" else "not connected",
                    color = if (vm.busy) C.accent else C.muted, fontSize = 13.sp)
            }
            if (vm.prefs.paired) IconButton(onClick = call) { Icon(Icons.Filled.Call, "Call Chhoti", tint = C.accent) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = C.muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Settings") }, { menu = false; openSettings() })
                    if (vm.items.isNotEmpty()) DropdownMenuItem({ Text("Clear chat") }, { menu = false; vm.speaker.stop(); vm.clearChat() })
                }
            }
        }
        HorizontalDivider(color = C.line)
    }
}

@Composable
private fun Welcome(vm: ChatViewModel, modifier: Modifier, onPick: (String) -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val hello = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    Column(modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(88.dp)
        Spacer(Modifier.height(16.dp))
        Text("$hello, ${vm.prefs.userName}", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Ask me anything, or start with one of these.", color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        val ideas = if (vm.prefs.paired) listOf("What needs my OK on the channel?", "How did yesterday's reels do?", "Any new messages for me?")
                    else listOf("Any new messages for me?", "Remind me in 10 minutes to stretch", "What's new in tech today?")
        for (s in ideas) {
            Text(s, color = C.ink, fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp))
                    .border(1.dp, C.line, RoundedCornerShape(20.dp)).background(C.surface)
                    .clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
private fun clock(t: Long) = if (t > 0) timeFmt.format(Date(t)).lowercase() else ""
private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayLabel(t: Long) {
    val label = if (sameDay(t, System.currentTimeMillis())) "Today" else SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(t))
    Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = C.muted, fontSize = 12.sp,
            modifier = Modifier.background(C.surfaceAlt, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun Mine(item: ChatItem) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        SelectionContainer {
            Text(item.text, color = C.onMine, fontSize = 16.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 300.dp)
                    .background(C.mine, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp))
        }
        Text(clock(item.time), color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, end = 4.dp))
    }
}

@Composable
private fun Hers(item: ChatItem, vm: ChatViewModel, showFace: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (showFace) Avatar(28.dp) else Spacer(Modifier.width(28.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 300.dp)) {
            Column(Modifier.border(1.dp, C.line, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
                .background(C.surface, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)) {
                SelectionContainer { Text(item.text, color = C.ink, fontSize = 16.sp, lineHeight = 22.sp) }
                for (card in item.actions) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = C.line)
                    Spacer(Modifier.height(10.dp))
                    Text("Needs your OK", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(card.summary, color = C.ink, fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(8.dp))
                    val result = card.result
                    if (result == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { vm.decide(card, true) }) { Text("Approve") }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { vm.decide(card, false) }) { Text("Not now", color = C.muted) }
                        }
                    } else {
                        Text(if (result == "…") "Working on it…" else result, color = C.muted, fontSize = 14.sp)
                    }
                }
            }
            Text(clock(item.time), color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        }
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.muted, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorLine(item: ChatItem, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(item.text, color = C.danger, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        if (item.retry != null) TextButton(onClick = retry) { Text("Try again") }
    }
}

@Composable
private fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.Bottom) {
        Avatar(28.dp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.border(1.dp, C.line, RoundedCornerShape(18.dp)).background(C.surface, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (k in 0 until 3) {
                val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = k * 160), RepeatMode.Reverse), label = "d$k")
                Box(Modifier.size(7.dp).alpha(a).background(C.muted, CircleShape))
            }
        }
    }
}
