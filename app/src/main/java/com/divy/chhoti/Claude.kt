package com.divy.chhoti

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal client for Anthropic's Messages API. Call from a background thread. */
object Claude {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    fun messages(apiKey: String, body: JSONObject): JSONObject {
        if (apiKey.isBlank()) throw IOException("No API key yet. Add it in Settings.")
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (!resp.isSuccessful) {
                val msg = json.optJSONObject("error")?.optString("message") ?: text.take(200)
                throw IOException(
                    when (resp.code) {
                        401 -> "The API key was rejected. Check it in Settings."
                        429 -> "Rate limited by Anthropic. Try again in a moment."
                        529 -> "Anthropic is overloaded right now. Try again shortly."
                        else -> "API error ${resp.code}: $msg"
                    }
                )
            }
            return json
        }
    }
}
