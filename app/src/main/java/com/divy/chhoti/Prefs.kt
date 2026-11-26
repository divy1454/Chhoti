package com.divy.chhoti

import android.content.Context

/** Settings, stored privately on the phone. The API key never leaves the device except to Anthropic. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("chhoti_prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = p.getString(key, def) ?: def
    private fun put(key: String, value: String) = p.edit().putString(key, value).apply()

    var apiKey: String get() = str("api_key", "").trim(); set(v) = put("api_key", v.trim())
    var model: String get() = str("model", "claude-sonnet-5-5"); set(v) = put("model", v.trim())
    var userName: String get() = str("user_name", "Divy"); set(v) = put("user_name", v)
    var address: String get() = str("address", "sir"); set(v) = put("address", v)
    var assistantName: String get() = str("assistant_name", "Chhoti"); set(v) = put("assistant_name", v)
    var about: String
        get() = str("about", "Developer from Mehsana, Gujarat. Runs creator channels Kyuniverse " +
            "(explainer reels) and ChaoScope (gaming Shorts). Speaks English, Hindi and Gujarati.")
        set(v) = put("about", v)
    var ttsVoice: String get() = str("tts_voice", ""); set(v) = put("tts_voice", v)

    var speakReplies: Boolean
        get() = p.getBoolean("speak_replies", false)
        set(v) = p.edit().putBoolean("speak_replies", v).apply()
    var webSearch: Boolean
        get() = p.getBoolean("web_search", true)
        set(v) = p.edit().putBoolean("web_search", v).apply()
}
