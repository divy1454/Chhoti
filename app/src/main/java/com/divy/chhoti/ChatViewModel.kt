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

class ActionCard(val id: Int, val summary: String) {
    var result by mutableStateOf<String?>(null)
}

class ChatItem(val role: String, val text: String, val actions: List<ActionCard> = emptyList())

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val agent = Agent(app, prefs)
    val speaker = Speaker(app, prefs)

    val items = mutableStateListOf<ChatItem>()
    var busy by mutableStateOf(false); private set

    var update by mutableStateOf<Updater.Release?>(null); private set
    var updateStatus by mutableStateOf(""); private set
    var installing by mutableStateOf(false); private set

    init {
        // Announce a successful self-update once.
        val sp = app.getSharedPreferences("chhoti_prefs", android.content.Context.MODE_PRIVATE)
        val last = sp.getInt("last_version", 0)
        if (last in 1 until BuildConfig.VERSION_CODE) {
            items += ChatItem("assistant", "Updated to version ${BuildConfig.VERSION_NAME}, ${prefs.address}. All systems nominal.")
        }
        sp.edit().putInt("last_version", BuildConfig.VERSION_CODE).apply()
    }

    fun send(text: String, voice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        items += ChatItem("user", t)
        if (prefs.apiKey.isBlank()) {
            items += ChatItem("error", "Add your Anthropic API key in Settings first, ${prefs.address}.")
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val r = agent.chat(t, voice)
                items += ChatItem("assistant", r.text, r.pending.map { ActionCard(it.id, it.summary) })
                if (voice || prefs.speakReplies) speaker.speak(r.text)
            } catch (e: Exception) {
                items += ChatItem("error", e.message ?: "Something went wrong.")
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
        }
    }

    fun clearChat() {
        agent.reset()
        items.clear()
    }

    fun checkUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            try {
                if (manual) updateStatus = "Checking…"
                val r = Updater.latest()
                update = r.takeIf { Updater.isNewer(it) }
                updateStatus = when {
                    update != null -> "Version ${update!!.versionCode} is available."
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
