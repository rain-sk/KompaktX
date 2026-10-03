package com.noti.restore.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.noti.restore.R
import com.noti.restore.service.KompaktXListener

/**
 * Optional persistent "N unread" notification so the phone tells you *something*
 * arrived without opening the Inbox. Kept by the app itself (lowest importance,
 * silent) — the app never rings on its own; it just mirrors the count.
 */
object InboxPersistent {

    private const val CHANNEL_ID = "inbox_count"
    private const val NOTIF_ID = 1

    fun update(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!getPrefs(context).getBoolean("persistent", true)) {
            nm.cancel(NOTIF_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.persistent_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(channel)
        }

        val count = KompaktXListener.notifications.value.size
        if (count == 0) { nm.cancel(NOTIF_ID); return }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.unread, count, count))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("inbox", Context.MODE_PRIVATE)
}
