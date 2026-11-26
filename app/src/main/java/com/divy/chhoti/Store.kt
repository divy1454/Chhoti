package com.divy.chhoti

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Chhoti's long-term memory, kept in one private JSON file on the phone:
 * facts about you, reminders, actions waiting for approval, and recent incoming messages.
 */
object Store {
    private lateinit var file: File
    private var root = JSONObject()
    private const val MAX_MESSAGES = 300

    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "store.json")
        root = try {
            if (file.exists()) JSONObject(file.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        for (k in listOf("memories", "reminders", "actions", "messages")) {
            if (!root.has(k)) root.put(k, JSONArray())
        }
        if (!root.has("next_id")) root.put("next_id", 1)
    }

    @Synchronized
    private fun save() {
        val tmp = File(file.parentFile, "store.json.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    private fun arr(name: String): JSONArray = root.getJSONArray(name)

    @Synchronized
    private fun nextId(): Int {
        val id = root.getInt("next_id")
        root.put("next_id", id + 1)
        return id
    }

    private fun objects(name: String): List<JSONObject> =
        (0 until arr(name).length()).map { arr(name).getJSONObject(it) }

    private fun removeWhere(name: String, pred: (JSONObject) -> Boolean): Boolean {
        val a = arr(name)
        for (i in a.length() - 1 downTo 0) {
            if (pred(a.getJSONObject(i))) { a.remove(i); return true }
        }
        return false
    }

    // --- memories --------------------------------------------------------
    @Synchronized
    fun addMemory(text: String): Int {
        val id = nextId()
        arr("memories").put(JSONObject().put("id", id).put("text", text.trim()))
        save(); return id
    }

    @Synchronized
    fun deleteMemory(id: Int): Boolean = removeWhere("memories") { it.getInt("id") == id }.also { if (it) save() }

    @Synchronized
    fun memories(): List<Pair<Int, String>> = objects("memories").map { it.getInt("id") to it.getString("text") }

    // --- reminders -------------------------------------------------------
    @Synchronized
    fun addReminder(atMillis: Long, text: String): Int {
        val id = nextId()
        arr("reminders").put(JSONObject().put("id", id).put("at", atMillis).put("text", text).put("done", false))
        save(); return id
    }

    @Synchronized
    fun pendingReminders(): List<JSONObject> = objects("reminders").filter { !it.getBoolean("done") }.sortedBy { it.getLong("at") }

    @Synchronized
    fun finishReminder(id: Int) {
        objects("reminders").firstOrNull { it.getInt("id") == id }?.put("done", true)
        // keep the file small: drop finished reminders beyond the latest 50
        val done = objects("reminders").filter { it.getBoolean("done") }
        if (done.size > 50) removeWhere("reminders") { it.getBoolean("done") && it.getInt("id") == done.first().getInt("id") }
        save()
    }

    // --- actions awaiting approval --------------------------------------
    @Synchronized
    fun addAction(tool: String, args: JSONObject, summary: String): Int {
        val id = nextId()
        arr("actions").put(JSONObject().put("id", id).put("tool", tool).put("args", args)
            .put("summary", summary).put("status", "pending"))
        while (arr("actions").length() > 100) arr("actions").remove(0)
        save(); return id
    }

    @Synchronized
    fun action(id: Int): JSONObject? = objects("actions").firstOrNull { it.getInt("id") == id }

    @Synchronized
    fun setActionStatus(id: Int, status: String) {
        action(id)?.put("status", status); save()
    }

    // --- incoming messages (from notifications) --------------------------
    /** Returns the new message id, or -1 if this exact message was already stored. */
    @Synchronized
    fun addMessage(app: String, convoKey: String, convo: String, sender: String, text: String, time: Long): Int {
        val recent = objects("messages").takeLast(40)
        if (recent.any { it.getString("convo_key") == convoKey && it.getString("sender") == sender && it.getString("text") == text }) return -1
        val id = nextId()
        arr("messages").put(JSONObject().put("id", id).put("app", app).put("convo_key", convoKey)
            .put("convo", convo).put("sender", sender).put("text", text).put("time", time))
        while (arr("messages").length() > MAX_MESSAGES) arr("messages").remove(0)
        save(); return id
    }

    @Synchronized
    fun messages(app: String?, limit: Int): List<JSONObject> =
        objects("messages").filter { app.isNullOrBlank() || it.getString("app").contains(app, ignoreCase = true) }
            .takeLast(limit)

    @Synchronized
    fun message(id: Int): JSONObject? = objects("messages").firstOrNull { it.getInt("id") == id }
}
