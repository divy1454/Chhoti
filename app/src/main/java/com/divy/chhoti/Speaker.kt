package com.divy.chhoti

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text-to-speech using the phone's own engine (Google / Samsung voices, offline-capable). */
class Speaker(context: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.language = Locale("en", "IN")
        applyVoice()
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

    fun speak(text: String) {
        if (!ready) return
        val clean = text.replace(Regex("https?://\\S+"), "the link").replace(Regex("[*_#`>|]"), "")
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "chhoti")
    }

    fun stop() { if (ready) tts.stop() }
    fun shutdown() = tts.shutdown()
}
