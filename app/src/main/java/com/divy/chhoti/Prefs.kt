package com.divy.chhoti

import android.content.Context

/**
 * Settings, stored privately on the phone. Once paired with Chhoti Link (the server), the Anthropic key lives on the
 * server only and this phone keeps just its device token.
 */
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

    // Chhoti Link (the server)
    var serverUrl: String get() = str("server_url", ""); set(v) = put("server_url", v.trim().trimEnd('/'))
    var deviceToken: String get() = str("device_token", ""); set(v) = put("device_token", v.trim())
    /** Firebase app settings from the server (public values), as JSON. */
    var firebaseConfig: String get() = str("firebase_config", ""); set(v) = put("firebase_config", v)
    val paired: Boolean get() = deviceToken.isNotBlank()
    /** Paired, or has its own key: either way it can think. */
    val canThink: Boolean get() = paired || apiKey.isNotBlank()

    var speakReplies: Boolean
        get() = p.getBoolean("speak_replies", false)
        set(v) = p.edit().putBoolean("speak_replies", v).apply()
    var webSearch: Boolean
        get() = p.getBoolean("web_search", true)
        set(v) = p.edit().putBoolean("web_search", v).apply()
}
