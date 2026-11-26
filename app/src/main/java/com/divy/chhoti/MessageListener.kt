package com.divy.chhoti

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads incoming chat notifications (WhatsApp, Instagram, Telegram, SMS...) once you grant
 * "Notification access". This is how Chhoti sees DMs from apps that have no public API.
 * Replies go through the notification's own quick-reply button, only after you approve.
 */
class MessageListener : NotificationListenerService() {

    companion object {
        val APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "org.thunderdog.challegram" to "Telegram X",
            "com.google.android.apps.messaging" to "Messages",
            "com.samsung.android.messaging" to "Samsung Messages",
            "com.facebook.orca" to "Messenger",
            "com.discord" to "Discord",
            "com.twitter.android" to "X",
            "com.snapchat.android" to "Snapchat",
            "com.linkedin.android" to "LinkedIn",
            "com.google.android.gm" to "Gmail",
        )

        /** Latest quick-reply action per conversation; valid while its notification exists. */
        private val replyActions = ConcurrentHashMap<String, Notification.Action>()

        @Volatile var connected = false

        fun hasAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, MessageListener::class.java).flattenToString()
            return flat.split(":").any { it == me }
        }

        fun openAccessSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun canReply(convoKey: String) = replyActions.containsKey(convoKey)

        fun reply(context: Context, convoKey: String, text: String): String {
            val action = replyActions[convoKey]
                ?: return "Can't reply there: the notification is gone or that app has no quick reply. Open the app instead."
            val inputs = action.remoteInputs ?: return "That notification has no reply box."
            val intent = Intent()
            val results = Bundle()
            for (ri in inputs) results.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            replyActions.remove(convoKey)
            return "Reply sent."
        }
    }

    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = APPS[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var convo = style?.conversationTitle?.toString() ?: title
        val sender: String
        val text: String
        val last = style?.messages?.lastOrNull()
        if (last != null) {
            sender = last.person?.name?.toString() ?: style.user.name?.toString() ?: title
            text = last.text?.toString().orEmpty()
        } else {
            sender = title
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        }
        if (convo.isBlank()) convo = sender
        if (text.isBlank() || Regex("^\\d+ new messages?").containsMatchIn(text)) return

        val convoKey = "${sbn.packageName}|$convo"
        n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
            ?.let { replyActions[convoKey] = it }

        Store.addMessage(app, convoKey, convo, sender, Safety.redact(text), sbn.postTime)
    }
}
