package com.divy.chhoti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object Reminders {
    private fun pending(context: Context, id: Int, text: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun canBeExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, id: Int, atMillis: Long, text: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, id, text)
        if (canBeExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) // may be a few minutes late
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id, ""))
        Store.finishReminder(id)
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        for (r in Store.pendingReminders()) {
            schedule(context, r.getInt("id"), maxOf(r.getLong("at"), now + 5_000), r.getString("text"))
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getIntExtra("id", 0)
        val text = intent.getStringExtra("text").orEmpty()
        Store.finishReminder(id)
        val name = Prefs(context).assistantName
        ChhotiApp.notify(context, 10_000 + id, ChhotiApp.CHANNEL_REMINDERS, "$name: reminder", text)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Store.init(context)
            Reminders.rescheduleAll(context)
        }
    }
}
