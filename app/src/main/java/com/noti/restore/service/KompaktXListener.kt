package com.noti.restore.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "InboxListener"

/**
 * Minimal notification listener: keeps an in-memory snapshot of the system's
 * active notifications so the Inbox activity can render it. No overlay, no
 * popups, no media sessions, no background loop beyond the system callbacks.
 */
class KompaktXListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener connected")
        instance = this
        updateNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Listener disconnected")
        instance = null
        _notifications.value = emptyList()
        // Android can restart the listener automatically; without this the
        // service can linger disconnected until the next event.
        try { requestRebind(ComponentName(this, KompaktXListener::class.java)) } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateNotifications()
    }

    private fun updateNotifications() {
        try {
            val active = activeNotifications?.toList() ?: emptyList()
            _notifications.value = active.sortedByDescending { it.postTime }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notifications", e)
        }
    }

    companion object {
        private var instance: KompaktXListener? = null

        private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

        fun getInstance(): KompaktXListener? = instance

        fun isEnabled(context: Context): Boolean {
            val listeners = try {
                Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            } catch (_: Exception) { null }
            val me = ComponentName(context, KompaktXListener::class.java).flattenToString()
            return listeners?.split(":")?.contains(me) == true
        }

        /** Pull the freshest list (call on resume / after mutations). */
        fun refresh() { instance?.updateNotifications() }

        fun dismiss(key: String) {
            try { instance?.cancelNotification(key) } catch (e: Exception) {
                Log.e(TAG, "Error dismissing notification", e)
            }
        }

        fun clearAll() {
            try { instance?.cancelAllNotifications() } catch (e: Exception) {
                Log.e(TAG, "Error clearing all notifications", e)
            }
        }

        /** Deep-link into the source app at the notification; fall back to launching the app. */
        fun open(context: Context, item: StatusBarNotification): Boolean {
            try {
                item.notification.contentIntent?.let { pi ->
                    try { pi.send(); return true } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            return try {
                context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    true
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch app: ${item.packageName}", e); false
            }
        }

        /** Newest sender name for MessagingStyle notifications, else null. */
        fun latestMessageSender(notification: Notification): String? {
            return try {
                val messages = NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(notification)?.messages
                    ?: return null
                (messages.maxByOrNull { it.timestamp } ?: messages.lastOrNull())?.person?.name?.toString()
            } catch (_: Exception) { null }
        }

        /**
         * Newest human-readable line of a notification. Messaging apps keep the thread history
         * in MessagingStyle/InboxStyle and may leave EXTRA_TEXT stale (the oldest line), so
         * prefer the newest structured entry and only fall back to EXTRA_TEXT.
         */
        fun latestMessageText(notification: Notification): String {
            val extras = notification.extras
            try {
                val messages = NotificationCompat.MessagingStyle
                    .extractMessagingStyleFromNotification(notification)?.messages
                if (!messages.isNullOrEmpty()) {
                    val t = (messages.maxByOrNull { it.timestamp } ?: messages.last()).text?.toString()
                    if (!t.isNullOrBlank()) return t
                }
            } catch (_: Exception) {}
            try {
                val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                if (lines != null && lines.isNotEmpty()) {
                    val t = lines.last()?.toString()
                    if (!t.isNullOrBlank()) return t
                }
            } catch (_: Exception) {}
            return try {
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
            } catch (_: Exception) { "" }
        }
    }
}
