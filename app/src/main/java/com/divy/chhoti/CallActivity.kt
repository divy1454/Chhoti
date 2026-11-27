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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.widthIn
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
import com.divy.chhoti.ui.Avatar
import com.divy.chhoti.ui.C
import com.divy.chhoti.ui.ChhotiTheme
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
        volumeControlStream = android.media.AudioManager.STREAM_VOICE_CALL

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
    private val speaker = Speaker(app, prefs, forCall = true)
    private val audio = CallAudio(app)
    var speakerOn by mutableStateOf(false); private set
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
    /** Muted: Chhoti still talks but doesn't listen until unmuted. */
    var muted by mutableStateOf(false); private set
    /** When the call connected (for the timer); 0 = not yet. */
    var connectedAt by mutableLongStateOf(0L); private set
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
        audio.start()
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val cid = id ?: Link.startCall(prefs)
                    callId = cid
                    if (id != null) Link.callState(prefs, cid, "answered")
                    Link.call(prefs, cid)
                }
                reason = info.optString("reason")
                connectedAt = System.currentTimeMillis()
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
                audio.callMode()
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
        if (muted) { phase = Phase.Idle; return }
        val r = recognizer() ?: run { error = "No speech recognizer on this phone. Install the Google app."; phase = Phase.Idle; return }
        partial = ""
        phase = Phase.Listening
        audio.beforeListening()
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
                SpeechRecognizer.ERROR_AUDIO -> {
                    // this phone's recognizer can't record in call mode: pause call mode while listening
                    if (!audio.normalWhileListening) { audio.normalWhileListening = true; main.postDelayed({ if (phase == Phase.Listening) listen() }, 300) }
                    else { error = "The microphone isn't available."; phase = Phase.Idle }
                }
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

    fun toggleMute() {
        muted = !muted
        if (muted) {
            if (phase == Phase.Listening) { recognizer?.cancel(); partial = ""; phase = Phase.Idle }
        } else if (phase == Phase.Idle && agent != null) {
            silentTries = 0
            listen()
        }
    }

    fun toggleSpeaker() {
        speakerOn = !speakerOn
        audio.setSpeaker(speakerOn)
    }

    fun hangUp() {
        if (phase == Phase.Ended) { onFinish(); return }
        phase = Phase.Ended
        speaker.stop()
        recognizer?.cancel()
        audio.stop()
        val id = callId
        val transcript = JSONArray().apply { captions.forEach { put(JSONObject().put("role", it.role).put("text", it.text)) } }
        val actions = JSONArray().apply { agent?.confirmed?.forEach { put(it) } }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { if (id != null) try { Link.endCall(prefs, id, transcript, actions) } catch (_: Exception) {} }
            onFinish()
        }
    }

    override fun onCleared() {
        audio.stop()
        recognizer?.destroy()
        speaker.shutdown()
    }
}

@Composable
private fun CallScreen(vm: CallViewModel) {
    // the call timer
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.connectedAt, vm.phase) {
        while (vm.connectedAt > 0 && vm.phase != Phase.Ended) { now = System.currentTimeMillis(); delay(1000) }
    }
    val list = rememberLazyListState()
    LaunchedEffect(vm.captions.size, vm.cards.size) {
        val n = vm.captions.size + vm.cards.size
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    Column(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(if (vm.phase == Phase.Ringing) 64.dp else 28.dp))
        RingingFace(ringing = vm.phase == Phase.Ringing, size = if (vm.phase == Phase.Ringing) 140.dp else 104.dp)
        Spacer(Modifier.size(16.dp))
        Text(vm.prefs.assistantName, color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        val secs = if (vm.connectedAt > 0) ((now - vm.connectedAt) / 1000).coerceAtLeast(0) else 0
        val status = when (vm.phase) {
            Phase.Ringing -> "Incoming call"
            Phase.Connecting -> "Connecting…"
            Phase.Ended -> if (vm.connectedAt > 0) "Call ended · ${"%d:%02d".format(secs / 60, secs % 60)}" else "Call ended"
            else -> "%d:%02d".format(secs / 60, secs % 60) + when (vm.phase) {
                Phase.Listening -> "  ·  listening"
                Phase.Thinking -> "  ·  thinking"
                Phase.Speaking -> "  ·  talking"
                else -> if (vm.muted) "  ·  muted" else ""
            }
        }
        Text(status, color = C.muted, fontSize = 15.sp)
        if (vm.reason.isNotBlank()) {
            Spacer(Modifier.size(10.dp))
            Text(vm.reason, color = C.ink, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (vm.error.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(vm.error, color = C.danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        }

        // What was said, quietly, plus anything that needs an OK
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp)) {
            if (vm.phase != Phase.Ringing) LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(vm.captions) { c ->
                    val me = c.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
                        Text(c.text, color = if (me) C.onMine else C.ink, fontSize = 15.sp, lineHeight = 21.sp,
                            modifier = Modifier.widthIn(max = 300.dp)
                                .background(if (me) C.mine else C.surface, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                items(vm.cards) { card ->
                    Column(Modifier.fillMaxWidth().background(C.sunSoft, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text("Needs your OK · say yes, or tap", color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(4.dp))
                        Text(card.summary, color = C.ink, fontSize = 15.sp, lineHeight = 21.sp)
                        Spacer(Modifier.size(8.dp))
                        val result = card.result
                        if (result == null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { vm.decide(card, true) }) { Text("Approve") }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { vm.decide(card, false) }) { Text("Not now", color = C.muted) }
                        } else Text(if (result == "…") "Working on it…" else result, color = C.muted, fontSize = 14.sp)
                    }
                }
                if (vm.partial.isNotBlank()) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(vm.partial, color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }

        val green = Color(0xFF2E9E5B)
        val red = Color(0xFFD93B30)
        if (vm.phase == Phase.Ringing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallButton("Decline", red, hangUp = true) { vm.decline() }
                CallButton("Answer", green) { vm.answer(vm.ringingId) }
            }
        } else {
            val live = vm.phase !in setOf(Phase.Ended, Phase.Connecting)
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Toggle(if (vm.muted) "Unmute" else "Mute", vm.muted, live, painterResource(R.drawable.ic_mic_off)) { vm.toggleMute() }
                Toggle(when (vm.phase) { Phase.Speaking -> "Interrupt"; Phase.Listening -> "Done"; else -> "Talk" },
                    vm.phase == Phase.Listening, live && !vm.muted && vm.phase != Phase.Thinking, painterResource(R.drawable.ic_mic)) { vm.mic() }
                Toggle("Speaker", vm.speakerOn, vm.phase != Phase.Ended, painterResource(R.drawable.ic_speaker)) { vm.toggleSpeaker() }
            }
            Box(Modifier.padding(bottom = 32.dp)) {
                CallButton(if (vm.phase == Phase.Ended) "Close" else "End", red, hangUp = true) { vm.hangUp() }
            }
        }
    }
}

/** Chhoti's face; while ringing, a soft ring pulses around it. */
@Composable
private fun RingingFace(ringing: Boolean, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "ring")
    val pulse by t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        if (ringing) Box(Modifier.size(size).scale(pulse).background(C.sun.copy(alpha = 0.35f), CircleShape))
        Avatar(size)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, enabled: Boolean, icon: androidx.compose.ui.graphics.painter.Painter, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (on) C.ink else C.surfaceAlt)
            .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = (if (on) C.bg else C.ink).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(6.dp))
        Text(label, color = C.muted, fontSize = 13.sp)
    }
}

@Composable
private fun CallButton(label: String, color: Color, hangUp: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, label, tint = Color.White, modifier = Modifier.size(32.dp).rotate(if (hangUp) 135f else 0f))
        }
        Spacer(Modifier.size(6.dp))
        Text(label, color = C.muted, fontSize = 13.sp)
    }
}
