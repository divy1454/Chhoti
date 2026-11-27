package com.divy.chhoti

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Chhoti Link: the server next to Kyuniverse Studio. It holds the Anthropic key, gives Chhoti the channel tools and
 * rings this phone for calls. Every call here is blocking: use it from a background thread.
 */
object Link {
    private val JSON = "application/json".toMediaType()

    class LinkException(message: String, val code: Int) : IOException(message)

    private fun request(prefs: Prefs, method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val b = Request.Builder().url(prefs.serverUrl + path)
        if (auth) b.header("Authorization", "Bearer ${prefs.deviceToken}")
        if (method == "POST") b.post((body ?: JSONObject()).toString().toRequestBody(JSON)) else b.get()
        Claude.http.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optJSONObject("error")?.optString("message") ?: json.optString("error").ifBlank { "HTTP ${resp.code}" }
                throw LinkException(
                    when (resp.code) {
                        401 -> "This phone isn't paired with the server any more. Pair again in Settings."
                        else -> msg
                    }, resp.code
                )
            }
            return json
        }
    }

    /** One-time pairing with the code from Telegram. Hands the phone's Anthropic key to the server. */
    fun pair(prefs: Prefs, url: String, code: String, name: String): JSONObject {
        prefs.serverUrl = url
        val r = request(prefs, "POST", "/pair",
            JSONObject().put("code", code.trim()).put("name", name).put("anthropicKey", prefs.apiKey), auth = false)
        prefs.deviceToken = r.getString("token")
        r.optJSONObject("firebase")?.let { prefs.firebaseConfig = it.toString() }
        if (r.optBoolean("keyOnServer")) prefs.apiKey = "" // the key now lives on the server only
        return r
    }

    fun me(prefs: Prefs): JSONObject = request(prefs, "GET", "/me").also { r ->
        r.optJSONObject("firebase")?.let { prefs.firebaseConfig = it.toString() }
    }

    fun registerPush(prefs: Prefs, token: String) { request(prefs, "POST", "/fcm", JSONObject().put("token", token)) }

    fun messages(prefs: Prefs, body: JSONObject): JSONObject {
        val req = Request.Builder().url(prefs.serverUrl + "/v1/messages")
            .header("Authorization", "Bearer ${prefs.deviceToken}")
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON)).build()
        return Claude.execute(req)
    }

    fun tools(prefs: Prefs): JSONArray = request(prefs, "GET", "/tools").getJSONArray("tools")

    fun runTool(prefs: Prefs, name: String, input: JSONObject): JSONObject =
        request(prefs, "POST", "/tool", JSONObject().put("name", name).put("input", input))

    /** via = "tap" or "voice" (then utterance must be a clear yes; the server checks it too). */
    fun confirm(prefs: Prefs, id: String, via: String, utterance: String = ""): String =
        request(prefs, "POST", "/actions/$id/confirm", JSONObject().put("via", via).put("utterance", utterance)).optString("result", "Done.")

    fun cancel(prefs: Prefs, id: String) { request(prefs, "POST", "/actions/$id/cancel") }

    fun tasks(prefs: Prefs): JSONObject = request(prefs, "GET", "/tasks")

    fun stopTask(prefs: Prefs, id: String) { request(prefs, "POST", "/tasks/$id/stop") }

    fun startCall(prefs: Prefs): String = request(prefs, "POST", "/calls").getString("id")

    fun call(prefs: Prefs, id: String): JSONObject = request(prefs, "GET", "/calls/$id")

    fun callState(prefs: Prefs, id: String, state: String) { request(prefs, "POST", "/calls/$id/state", JSONObject().put("state", state)) }

    fun endCall(prefs: Prefs, id: String, transcript: JSONArray, actions: JSONArray) {
        request(prefs, "POST", "/calls/$id/end", JSONObject().put("transcript", transcript).put("actions", actions))
    }
}
