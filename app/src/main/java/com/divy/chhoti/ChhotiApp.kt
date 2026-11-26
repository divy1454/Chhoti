package com.divy.chhoti

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ChhotiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_UPDATES = "updates"

        fun notify(context: Context, id: Int, channel: String, title: String, text: String) {
            val open = android.app.PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (_: SecurityException) {
                // notification permission not granted
            }
        }
    }
}
