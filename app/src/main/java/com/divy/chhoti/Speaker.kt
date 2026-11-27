package com.divy.chhoti

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Text-to-speech using the phone's own engine (Google / Samsung voices, offline-capable). */
class Speaker(context: Context, private val prefs: Prefs, private val forCall: Boolean = false) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var queued: Pair<String, (() -> Unit)?>? = null
    private val done = HashMap<String, () -> Unit>()
    private var counter = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.language = Locale("en", "IN")
        applyVoice()
        // On a call, speak on the voice-call stream so it comes out of the earpiece like a phone call.
        if (forCall) tts.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finish(utteranceId) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finish(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { finish(utteranceId) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { synchronized(done) { done.remove(utteranceId) } }
        })
        queued?.let { (t, cb) -> queued = null; speak(t, cb) }
    }

    private fun finish(id: String?) {
        val cb = synchronized(done) { done.remove(id) } ?: return
        main.post(cb)
    }

    fun applyVoice() {
        if (!ready) return
        val wanted = prefs.ttsVoice
        if (wanted.isNotBlank()) tts.voices?.firstOrNull { it.name == wanted }?.let { tts.voice = it }
    }

    /** English and Hindi voices installed on this phone, by name. */
    fun voiceNames(): List<String> = if (!ready) emptyList() else
        (tts.voices ?: emptySet())
            .filter { it.locale.language in setOf("en", "hi") && !it.isNetworkConnectionRequired }
            .map { it.name }.sorted()

    /** Speak; [onDone] runs on the main thread when it has finished (or failed). */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ready) { queued = text to onDone; return }
        val clean = text.replace(Regex("https?://\\S+"), "the link").replace(Regex("[*_#`>|]"), "")
        val id = "chhoti-${counter++}"
        if (onDone != null) synchronized(done) { done[id] = onDone }
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        if (ready) tts.stop()
        synchronized(done) { done.clear() }
    }
    fun shutdown() = tts.shutdown()
}
