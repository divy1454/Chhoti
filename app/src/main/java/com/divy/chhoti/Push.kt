package com.divy.chhoti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Lets the server ring this phone. Firebase is set up by hand from the settings the server hands over (public app
 * values, no google-services.json in the repo), so nothing happens until the phone is paired and Firebase exists.
 */
object Push {
    const val CHANNEL_CALLS = "calls_v1"
    const val CALL_NOTIFICATION = 7001

    /** Start Firebase (if the server has given its settings) and send this phone's push token to the server. */
    fun init(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.paired || prefs.firebaseConfig.isBlank()) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val c = JSONObject(prefs.firebaseConfig)
                val opts = FirebaseOptions.Builder()
                    .setApiKey(c.getString("apiKey"))
                    .setApplicationId(c.getString("appId"))
                    .setProjectId(c.getString("projectId"))
                    .setGcmSenderId(c.optString("senderId"))
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, opts)
            }
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> register(context, token) }
        } catch (e: Exception) {
            // bad or missing settings: calls stay off, everything else works
        }
    }

    fun register(context: Context, token: String) {
        val prefs = Prefs(context)
        if (!prefs.paired) return
        thread { try { Link.registerPush(prefs, token) } catch (_: Exception) {} }
    }

    fun ready(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    fun createChannel(context: Context) {
        val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val ch = NotificationChannel(CHANNEL_CALLS, "Calls from Chhoti", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Chhoti calling about the channel"
            setSound(ring, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Android 14+ asks the user before an app may show full-screen call screens. */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 34) context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The incoming-call screen: rings like a phone call, Answer / Decline, full screen on the lock screen. */
    fun showIncoming(context: Context, callId: String, reason: String, ringSeconds: Long = 60) {
        val prefs = Prefs(context)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val answer = PendingIntent.getActivity(context, 1,
            Intent(context, CallActivity::class.java).putExtra(CallActivity.EXTRA_CALL, callId).putExtra(CallActivity.EXTRA_ANSWERED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
        val screen = PendingIntent.getActivity(context, 2,
            Intent(context, CallActivity::class.java).putExtra(CallActivity.EXTRA_CALL, callId).putExtra(CallActivity.EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags)
        val decline = PendingIntent.getBroadcast(context, 3,
            Intent(context, CallActionReceiver::class.java).putExtra(CallActivity.EXTRA_CALL, callId), flags)
        val caller = Person.Builder().setName(prefs.assistantName).setImportant(true).build()
        val n = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${prefs.assistantName} is calling")
            .setContentText(reason)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(ringSeconds * 1000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until answered, declined or timed out
        try { NotificationManagerCompat.from(context).notify(CALL_NOTIFICATION, n) } catch (_: SecurityException) {}
    }

    fun stopRinging(context: Context) = NotificationManagerCompat.from(context).cancel(CALL_NOTIFICATION)
}

/** Messages from the server through Firebase. */
class ChhotiMessaging : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.register(applicationContext, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] == "call") {
            val id = d["call_id"] ?: return
            Push.showIncoming(applicationContext, id, d["reason"] ?: "About the channel")
        }
    }
}

/** Decline from the notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Push.stopRinging(context)
        val id = intent.getStringExtra(CallActivity.EXTRA_CALL) ?: return
        val prefs = Prefs(context)
        val done = goAsync()
        thread {
            try { Link.callState(prefs, id, "declined") } catch (_: Exception) {}
            done.finish()
        }
    }
}
