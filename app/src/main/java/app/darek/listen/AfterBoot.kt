package app.darek.listen

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.darek.MainApplication
import app.darek.R
import app.darek.ui.settings.inAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** Only someone who left Listening on, on this install, is asked after a reboot; *Stop* clears it. */
fun offersListenAfterBoot(
    wantedUnder: Instant?,
    installedAt: Instant,
): Boolean = wantedUnder == installedAt

/**
 * After a reboot Darek may not start listening by himself (ADR-0010), so this posts a notification
 * whose *Słuchaj* starts him with one tap.
 */
class AfterBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as MainApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (offersListenAfterBoot(app.preferences.listeningWantedUnder.first(), app.installedAt)) {
                    postListenAfterBoot(app, app.preferences.persona.first())
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "after-boot notification failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun postListenAfterBoot(
        context: Context,
        persona: Persona,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val strings = context.inAppLanguage()
        manager.createNotificationChannel(
            NotificationChannelCompat
                .Builder(AFTER_BOOT_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(strings.getString(R.string.after_boot_channel))
                .setShowBadge(false)
                .build(),
        )
        val start =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, StartListeningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, AFTER_BOOT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(strings.getString(persona.notListeningRes()))
                .setContentText(strings.getString(R.string.after_boot_text))
                .setContentIntent(start)
                .addAction(0, strings.getString(R.string.home_listen), start)
                .setAutoCancel(true)
                .build()
        try {
            manager.notify(AFTER_BOOT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "after-boot notification refused", e)
        }
    }

    companion object {
        private const val TAG = "DarekListen"
        private const val AFTER_BOOT_CHANNEL_ID = "after_boot"
        private const val AFTER_BOOT_NOTIFICATION_ID = 2

        fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(AFTER_BOOT_NOTIFICATION_ID)
    }
}

private fun Persona.notListeningRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.after_boot_title
        Persona.DARIA -> R.string.after_boot_title_daria
    }
