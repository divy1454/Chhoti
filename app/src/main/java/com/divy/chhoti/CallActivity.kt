package com.divy.chhoti

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divy.chhoti.ui.Bg
import com.divy.chhoti.ui.ChhotiTheme
import com.divy.chhoti.ui.Cyan
import com.divy.chhoti.ui.Danger
import com.divy.chhoti.ui.Ink
import com.divy.chhoti.ui.Muted
import com.divy.chhoti.ui.Panel
import com.divy.chhoti.ui.Reactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A voice call with Chhoti about the channel. Opened by the incoming-call notification (the server rang), or by
 * "Call about the channel" in the app. Hands-free: Chhoti speaks, then listens, until someone hangs up.
 */
class CallActivity : ComponentActivity() {
    private val vm: CallViewModel by viewModels()

    companion object {
        const val EXTRA_CALL = "call_id"
        const val EXTRA_ANSWERED = "answered"
        const val EXTRA_REASON = "reason"
        const val EXTRA_MANUAL = "manual"
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.micAllowed = ok
        if (ok) vm.onMicReady()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vm.micAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (savedInstanceState == null) handle(intent)
        if (!vm.micAllowed) askMic.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            ChhotiTheme {
                BackHandler { if (vm.phase == Phase.Ringing) vm.decline() else vm.hangUp() }
                CallScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        val id = i.getStringExtra(EXTRA_CALL)
        when {
            i.getBooleanExtra(EXTRA_MANUAL, false) -> vm.answer(null)
            id != null && i.getBooleanExtra(EXTRA_ANSWERED, false) -> vm.answer(id)
            id != null -> vm.ring(id, i.getStringExtra(EXTRA_REASON).orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onFinish = { finish() }
    }
}

enum class Phase { Ringing, Connecting, Thinking, Speaking, Listening, Idle, Ended }

class Caption(val role: String, val text: String)

class CallViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val speaker = Speaker(app, prefs)
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var agent: Agent? = null
    private var callId: String? = null
    private var incoming = false
    private var silentTries = 0
    private var started = false
    private val notes = mutableListOf<String>() // things that happened by tap, told to Chhoti on the next turn

    var phase by mutableStateOf(Phase.Connecting); private set
    var reason by mutableStateOf("")
    var partial by mutableStateOf("")
    var error by mutableStateOf("")
    var micAllowed = false
    val ringingId: String? get() = callId
    var onFinish: () -> Unit = {}
    val captions = mutableStateListOf<Caption>()
    val cards = mutableStateListOf<ActionCard>()

    fun ring(id: String, why: String) {
        if (started) return
        callId = id
        incoming = true
        reason = why
        phase = Phase.Ringing
        viewModelScope.launch {
            delay(60_000)
            if (phase == Phase.Ringing) { Push.stopRinging(getApplication()); onFinish() }
        }
    }

    fun decline() {
        Push.stopRinging(getApplication())
        val id = callId
        phase = Phase.Ended
        viewModelScope.launch(Dispatchers.IO) { if (id != null) try { Link.callState(prefs, id, "declined") } catch (_: Exception) {} }
        onFinish()
    }

    /** Answer an incoming call (id), or start one yourself (null). */
    fun answer(id: String?) {
        if (started) return
        started = true
        Push.stopRinging(getApplication())
        incoming = id != null
        phase = Phase.Connecting
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val cid = id ?: Link.startCall(prefs)
                    callId = cid
                    if (id != null) Link.callState(prefs, cid, "answered")
                    Link.call(prefs, cid)
                }
                reason = info.optString("reason")
                agent = Agent(getApplication(), prefs, CallInfo(info.optString("kind"), info.optString("agenda"), info.optString("context")))
                if (micAllowed) turn("(The call has just connected. Start talking now, following the call's agenda.)", heard = false)
                else phase = Phase.Idle
            } catch (e: Exception) {
                error = e.message ?: "Couldn't reach the server."
                phase = Phase.Ended
            }
        }
    }

    fun onMicReady() {
        if (agent != null && phase == Phase.Idle && captions.isEmpty()) turn("(The call has just connected. Start talking now, following the call's agenda.)", heard = false)
    }

    private fun turn(text: String, heard: Boolean) {
        val a = agent ?: return
        if (heard) captions += Caption("user", text)
        val msg = if (notes.isEmpty()) text else (notes.joinToString(" ") + " " + text).also { notes.clear() }
        phase = Phase.Thinking
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { a.chat(msg, voice = true) }
                captions += Caption("assistant", r.text)
                for (p in r.pending) cards += ActionCard(p.id, p.summary)
                if (phase == Phase.Ended) return@launch
                phase = Phase.Speaking
                speaker.speak(r.text) { if (r.endCall) hangUp() else if (phase == Phase.Speaking) listen() }
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
                phase = Phase.Speaking
                speaker.speak("Sorry, I lost the connection for a moment. Tap the mic when you want to go on.") { if (phase == Phase.Speaking) phase = Phase.Idle }
            }
        }
    }

    private fun recognizer(): SpeechRecognizer? {
        if (recognizer == null && SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply { setRecognitionListener(listener) }
        }
        return recognizer
    }

    private fun listen() {
        if (phase == Phase.Ended) return
        val r = recognizer() ?: run { error = "No speech recognizer on this phone. Install the Google app."; phase = Phase.Idle; return }
        partial = ""
        phase = Phase.Listening
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        try { r.startListening(i) } catch (e: Exception) { phase = Phase.Idle }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial = it }
        }
        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
            partial = ""
            if (phase != Phase.Listening) return
            if (heard.isEmpty()) return retry()
            silentTries = 0
            turn(heard, heard = true)
        }
        override fun onError(code: Int) {
            partial = ""
            if (phase != Phase.Listening) return
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> retry()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Chhoti needs the microphone."; phase = Phase.Idle }
                else -> main.postDelayed({ if (phase == Phase.Listening) listen() }, 600)
            }
        }
    }

    private fun retry() {
        silentTries++
        if (silentTries < 3) listen() else { silentTries = 0; phase = Phase.Idle }
    }

    /** The big button: interrupt Chhoti, or start listening again. */
    fun mic() {
        when (phase) {
            Phase.Speaking -> { speaker.stop(); listen() }
            Phase.Idle -> { silentTries = 0; if (captions.isEmpty() && agent != null) turn("(The call has just connected. Start talking now, following the call's agenda.)", heard = false) else listen() }
            Phase.Listening -> recognizer?.stopListening()
            else -> {}
        }
    }

    fun decide(card: ActionCard, approve: Boolean) {
        val a = agent ?: return
        if (card.result != null) return
        card.result = "…"
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { if (approve) a.approve(card.id) else a.reject(card.id) }
            card.result = r
            notes += if (approve) "(He tapped Approve on action #${card.id}: $r)" else "(He tapped Reject on action #${card.id}.)"
        }
    }

    fun hangUp() {
        if (phase == Phase.Ended) { onFinish(); return }
        phase = Phase.Ended
        speaker.stop()
        recognizer?.cancel()
        val id = callId
        val transcript = JSONArray().apply { captions.forEach { put(JSONObject().put("role", it.role).put("text", it.text)) } }
        val actions = JSONArray().apply { agent?.confirmed?.forEach { put(it) } }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { if (id != null) try { Link.endCall(prefs, id, transcript, actions) } catch (_: Exception) {} }
            onFinish()
        }
    }

    override fun onCleared() {
        recognizer?.destroy()
        speaker.shutdown()
    }
}

@Composable
private fun CallScreen(vm: CallViewModel) {
    val list = rememberLazyListState()
    LaunchedEffect(vm.captions.size, vm.cards.size) {
        val n = vm.captions.size + vm.cards.size
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(12.dp))
        Reactor(Modifier.size(if (vm.phase == Phase.Ringing) 170.dp else 110.dp),
            active = vm.phase in setOf(Phase.Ringing, Phase.Thinking, Phase.Speaking, Phase.Connecting))
        Spacer(Modifier.size(12.dp))
        Text(vm.prefs.assistantName.uppercase(), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
        val status = when (vm.phase) {
            Phase.Ringing -> "is calling…"
            Phase.Connecting -> "connecting…"
            Phase.Thinking -> "thinking…"
            Phase.Speaking -> "speaking · tap the mic to interrupt"
            Phase.Listening -> "listening…"
            Phase.Idle -> "tap the mic to talk"
            Phase.Ended -> "call ended"
        }
        Text(status, color = if (vm.phase == Phase.Listening) Cyan else Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        if (vm.reason.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(vm.reason, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        if (vm.error.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(vm.error, color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp)) {
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(vm.captions) { c ->
                    val me = c.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
                        Text(c.text, color = Ink, fontSize = 15.sp, lineHeight = 21.sp,
                            modifier = Modifier.background(if (me) Cyan.copy(alpha = 0.18f) else Panel, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                items(vm.cards) { card ->
                    Column(Modifier.fillMaxWidth().border(1.dp, Cyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("SAY YES, OR TAP · #${card.id}", color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        Spacer(Modifier.size(4.dp))
                        Text(card.summary, color = Ink, fontSize = 14.sp)
                        Spacer(Modifier.size(8.dp))
                        val result = card.result
                        if (result == null) Row {
                            Button(onClick = { vm.decide(card, true) }) { Text("Approve") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { vm.decide(card, false) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("Reject") }
                        } else Text(result, color = Muted, fontSize = 13.sp)
                    }
                }
                if (vm.partial.isNotBlank()) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(vm.partial, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }

        val green = Color(0xFF2ECC71)
        if (vm.phase == Phase.Ringing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton("Decline", Danger) { vm.decline() }
                RoundButton("Answer", green) { vm.answer(vm.ringingId) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = vm::mic, modifier = Modifier.size(72.dp), enabled = vm.phase in setOf(Phase.Speaking, Phase.Idle, Phase.Listening),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (vm.phase == Phase.Listening) Cyan else Panel, contentColor = if (vm.phase == Phase.Listening) Bg else Cyan)) {
                    Icon(painterResource(R.drawable.ic_mic), "Talk", modifier = Modifier.size(32.dp))
                }
                RoundButton(if (vm.phase == Phase.Ended) "Close" else "Hang up", Danger) { vm.hangUp() }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onClick, shape = CircleShape, modifier = Modifier.size(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)) {}
        Spacer(Modifier.size(6.dp))
        Text(label, color = Ink, fontSize = 13.sp)
    }
}
