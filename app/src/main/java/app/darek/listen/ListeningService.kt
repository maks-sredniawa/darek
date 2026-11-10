package app.darek.listen

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.darek.MainActivity
import app.darek.MainApplication
import app.darek.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Listening as a foreground service of type `microphone` (ADR-0010): it keeps the container's
 * [Listening] alive with the screen off and the app swiped away, and ends itself when Listening ends.
 * Started only from a user action, since Android 14+ refuses a background start.
 */
class ListeningService : Service() {
    private val scope = MainScope()
    private var run: Job? = null

    private val listening get() = (application as MainApplication).container.listening
    private val preferences get() = (application as MainApplication).container.preferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            listening.stop()
            return START_NOT_STICKY
        }
        // startForeground must come before this call returns; the store is already in memory.
        val persona = runBlocking { preferences.persona.first() }
        if (!goForeground(persona)) {
            stopSelf()
            return START_NOT_STICKY
        }
        run?.cancel()
        run =
            scope.launch {
                listening.start(enabledCommands(GateNumber.get(this@ListeningService)), persona)
                launch {
                    preferences.persona.distinctUntilChanged().collect {
                        listening.switchTo(it)
                        renotify(it)
                    }
                }
                listening.running.first { !it }
                stopSelf()
            }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        listening.stop()
        scope.cancel()
        super.onDestroy()
    }

    /** False when the system refuses: on Android 14+ a `microphone` service needs `RECORD_AUDIO` held. */
    private fun goForeground(persona: Persona): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        createChannel()
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(persona),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "foreground start refused", e)
            false
        }
    }

    private fun renotify(persona: Persona) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(persona))
    }

    private fun createChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.listening_channel))
                .setShowBadge(false)
                .build(),
        )
    }

    private fun notification(persona: Persona): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                0,
                Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(persona.listeningRes()))
            .setContentIntent(open)
            .addAction(0, getString(R.string.home_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "DarekListen"
        private const val CHANNEL_ID = "listening"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.darek.listen.STOP"

        /** Called only from a user action on a visible screen, the start Android 14+ allows. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java))
        }
    }
}

fun Persona.listeningRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.home_listening
        Persona.DARIA -> R.string.home_listening_daria
    }
