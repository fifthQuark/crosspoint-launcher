package com.crosspoint.launcher.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A notification reduced to what the Notifications page shows. */
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean
)

/**
 * Mirrors the active notifications for the Notifications swipe page. Android binds this service
 * only after the user allows notification access in system settings.
 */
class NotificationsService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        publish()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _notifications.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        _notifications.value = active
            // Group summaries repeat their children; keep summaries only when they stand alone.
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 || active.none { other -> other !== it && other.groupKey == it.groupKey } }
            .map { it.toEntry() }
            .sortedByDescending { it.postTime }
    }

    private fun StatusBarNotification.toEntry(): NotificationEntry {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        return NotificationEntry(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime,
            clearable = isClearable,
            contentIntent = notification.contentIntent,
            autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0
        )
    }

    companion object {
        // Cleared when Android unbinds the service, so it never outlives the connection.
        @android.annotation.SuppressLint("StaticFieldLeak")
        private var instance: NotificationsService? = null
        private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
        val notifications: StateFlow<List<NotificationEntry>> = _notifications.asStateFlow()

        fun hasAccess(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')
                ?.any { ComponentName.unflattenFromString(it)?.packageName == context.packageName } == true

        fun openAccessSettings(context: Context) {
            val component = ComponentName(context, NotificationsService::class.java)
            val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            } else null
            val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            runCatching { context.startActivity((detail ?: fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }

        /** Opens what the notification points to, and removes it when it asks to be auto-cancelled. */
        fun open(context: Context, entry: NotificationEntry): Boolean {
            val intent = entry.contentIntent ?: return false
            val sent = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    intent.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    intent.send()
                }
            }.isSuccess
            if (sent && entry.autoCancel) dismiss(entry)
            return sent
        }

        fun dismiss(entry: NotificationEntry) {
            instance?.cancelNotification(entry.key)
        }

        fun dismissAll() {
            instance?.cancelAllNotifications()
        }
    }
}
