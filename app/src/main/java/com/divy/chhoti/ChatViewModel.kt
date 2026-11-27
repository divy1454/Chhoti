package com.divy.chhoti

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ActionCard(val id: Int, val summary: String, result: String? = null) {
    var result by mutableStateOf(result)
}

/** One line on the chat screen. [retry] is set on errors: the text to send again. */
class ChatItem(
    val role: String,
    val text: String,
    val actions: List<ActionCard> = emptyList(),
    val time: Long = System.currentTimeMillis(),
    val retry: String? = null,
    val voice: Boolean = false,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val agent = Agent(app, prefs, persist = true)
    val speaker = Speaker(app, prefs)

    val items = mutableStateListOf<ChatItem>()
    var busy by mutableStateOf(false); private set

    var update by mutableStateOf<Updater.Release?>(null); private set
    var updateStatus by mutableStateOf(""); private set
    var installing by mutableStateOf(false); private set

    init {
        load()
        // Say once that an update went in.
        val sp = app.getSharedPreferences("chhoti_prefs", android.content.Context.MODE_PRIVATE)
        val last = sp.getInt("last_version", 0)
        if (last in 1 until BuildConfig.VERSION_CODE) {
            add(ChatItem("note", "Updated to ${BuildConfig.VERSION_NAME}"))
        }
        sp.edit().putInt("last_version", BuildConfig.VERSION_CODE).apply()
    }

    // ------------------------------------------------------------------ saved chat
    private fun load() {
        val arr = Store.chat()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val acts = o.optJSONArray("actions") ?: JSONArray()
            items += ChatItem(
                o.getString("role"), o.getString("text"),
                (0 until acts.length()).map { acts.getJSONObject(it) }
                    .map { ActionCard(it.getInt("id"), it.getString("summary"), it.optString("result").ifBlank { null }) },
                o.optLong("time", 0L), null,
            )
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (it in items) {
            if (it.role == "error") continue
            val acts = JSONArray()
            it.actions.forEach { a -> acts.put(JSONObject().put("id", a.id).put("summary", a.summary).put("result", a.result?.takeIf { r -> r != "…" } ?: "")) }
            arr.put(JSONObject().put("role", it.role).put("text", it.text).put("time", it.time).put("actions", acts))
        }
        Store.saveChat(arr)
    }

    private fun add(item: ChatItem) {
        items += item
        save()
    }

    // ------------------------------------------------------------------ talking
    fun send(text: String, voice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        add(ChatItem("user", t, voice = voice))
        ask(t, voice)
    }

    /** Send again after an error (the failed turn was already taken back). */
    fun retry(item: ChatItem) {
        val t = item.retry ?: return
        if (busy) return
        items.remove(item)
        ask(t, item.voice)
    }

    private fun ask(t: String, voice: Boolean) {
        if (!prefs.canThink) {
            items += ChatItem("error", "I'm not connected yet. Pair me with the server in Settings.")
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val r = agent.chat(t, voice)
                add(ChatItem("assistant", r.text, r.pending.map { ActionCard(it.id, it.summary) }))
                if (voice || prefs.speakReplies) speaker.speak(r.text)
            } catch (e: Exception) {
                items += ChatItem("error", e.message ?: "Something went wrong.", retry = t, voice = voice)
            } finally {
                busy = false
            }
        }
    }

    fun decide(card: ActionCard, approve: Boolean) {
        if (card.result != null) return
        card.result = "…"
        viewModelScope.launch {
            card.result = withContext(Dispatchers.IO) { if (approve) agent.approve(card.id) else agent.reject(card.id) }
            save()
        }
    }

    fun clearChat() {
        agent.reset()
        items.clear()
        save()
    }

    // ------------------------------------------------------------------ Chhoti Link
    var linkStatus by mutableStateOf(""); private set
    var pairing by mutableStateOf(false); private set
    var canRing by mutableStateOf(false); private set

    fun pair(url: String, code: String) {
        if (pairing) return
        pairing = true
        linkStatus = "Pairing…"
        viewModelScope.launch {
            linkStatus = try {
                withContext(Dispatchers.IO) { Link.pair(prefs, url.trim().trimEnd('/'), code, android.os.Build.MODEL) }
                Push.init(getApplication())
                refreshLink()
                "Paired. Your API key now lives on the server."
            } catch (e: Exception) {
                "Couldn't pair: ${e.message}"
            } finally {
                pairing = false
            }
        }
    }

    fun unpair() {
        prefs.deviceToken = ""
        canRing = false
        linkStatus = "Unpaired. Add an API key or pair again."
    }

    /** Ask the server what it knows about this phone (and pick up Firebase settings once they exist). */
    fun refreshLink() {
        if (!prefs.paired) return
        viewModelScope.launch {
            try {
                val me = withContext(Dispatchers.IO) { Link.me(prefs) }
                if (!Push.ready(getApplication())) Push.init(getApplication())
                canRing = me.optBoolean("canRing")
                val c = me.optJSONObject("config")
                linkStatus = when {
                    canRing -> "Connected. I'll call you at ${c?.optString("dailyCallAt") ?: "?"} every day, never between ${c?.optString("quietStart")} and ${c?.optString("quietEnd")}."
                    me.optJSONObject("firebase") == null -> "Connected. Calls are off until Firebase is set up on the server."
                    else -> "Connected. Setting up calls on this phone…"
                }
            } catch (e: Exception) {
                linkStatus = "Can't reach the server: ${e.message}"
            }
        }
    }

    // ------------------------------------------------------------------ updates
    fun checkUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            try {
                if (manual) updateStatus = "Checking…"
                val r = Updater.latest()
                update = r.takeIf { Updater.isNewer(it) }
                updateStatus = when {
                    update != null -> "Version 1.0.${update!!.versionCode} is ready."
                    manual -> "You're on the latest version."
                    else -> ""
                }
            } catch (e: Exception) {
                if (manual) updateStatus = "Couldn't check: ${e.message}"
            }
        }
    }

    fun installUpdate() {
        val r = update ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            updateStatus = "Allow Chhoti to install updates, then tap Update again."
            Updater.openInstallPermission(ctx)
            return
        }
        installing = true
        viewModelScope.launch {
            try {
                val apk = Updater.download(ctx, r) { pct -> updateStatus = "Downloading… $pct%" }
                updateStatus = "Tap Update on the next screen."
                Updater.openInstaller(ctx, apk)
            } catch (e: Exception) {
                updateStatus = "Update failed: ${e.message}"
            } finally {
                installing = false
            }
        }
    }

    override fun onCleared() {
        speaker.shutdown()
    }
}
