package app.darek.listen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

const val LISTENING_CHANNEL_ID = "listening"

/** What hides Listening's notification, so Darek may not listen (phase-2.md, *The start paths*). */
enum class NotificationBlock {
    /** The app's notifications are off, or `POST_NOTIFICATIONS` was never granted (Android 13+). */
    APP,

    /** Listening's own channel is off while the rest of the app's notifications are on. */
    CHANNEL,
}

/** A live read on every start and every return to Home: the user changes it in system settings. */
fun Context.listeningNotificationBlock(): NotificationBlock? {
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return NotificationBlock.APP
    val channel = manager.getNotificationChannelCompat(LISTENING_CHANNEL_ID)
    return if (channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE) NotificationBlock.CHANNEL else null
}

fun Context.hasMicrophone(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
