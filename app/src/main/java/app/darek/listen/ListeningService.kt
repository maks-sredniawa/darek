package app.darek.listen

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
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
import app.darek.ui.settings.inAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Listening as a foreground service of type `microphone` (ADR-0010): it keeps the container's
 * [Listening] alive with the screen off and the app swiped away, and ends itself when Listening ends.
 */
class ListeningService : Service() {
    private val scope = MainScope()
    private var run: Job? = null
    private var strings: Context = this
    private var shown: Shown? = null

    private data class Shown(
        val persona: Persona,
        val enabled: Set<Command>,
        val hearing: Hearing,
    )

    private val listening get() = (application as MainApplication).container.listening
    private val container get() = (application as MainApplication).container
    private val preferences get() = container.preferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            container.stopListening()
            return START_NOT_STICKY
        }
        // startForeground must come before this call returns; both reads are small files.
        val persona =
            runBlocking(Dispatchers.IO) {
                strings = inAppLanguage()
                preferences.persona.first()
            }
        val enabled = enabledCommands(container.fuchyOn, GateNumber.get(this))
        if (!goForeground(persona, enabled)) {
            stopSelf()
            return START_NOT_STICKY
        }
        AfterBootReceiver.cancel(this)
        run?.cancel()
        run =
            scope.launch {
                listening.start(enabled, commandDecoys(container.fuchyOn), persona)
                launch(Dispatchers.IO) { preferences.setListeningWanted(container.installedAt) }
                launch { preferences.persona.distinctUntilChanged().collect { listening.switchTo(it) } }
                launch {
                    combine(preferences.persona, listening.state.map { it.hearing }, ::Pair)
                        .distinctUntilChanged()
                        .collect { (persona, hearing) -> renotify(persona, enabled, hearing) }
                }
                listening.running.first { !it }
                stopSelf()
            }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scope.launch {
            strings = withContext(Dispatchers.IO) { inAppLanguage() }
            createChannel()
            shown?.let { renotify(it.persona, it.enabled, it.hearing) }
        }
    }

    override fun onDestroy() {
        listening.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(
        persona: Persona,
        enabled: Set<Command>,
    ): Boolean {
        createChannel()
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(persona, enabled, Hearing.HEARS),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            shown = Shown(persona, enabled, Hearing.HEARS)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "foreground start refused", e)
            false
        }
    }

    private fun renotify(
        persona: Persona,
        enabled: Set<Command>,
        hearing: Hearing,
    ) {
        // A hidden notification leaves no Stop outside the app, so Listening ends (phase-2.md).
        if (listeningNotificationBlock() != null) {
            listening.stop()
            return
        }
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(persona, enabled, hearing))
            shown = Shown(persona, enabled, hearing)
        } catch (e: SecurityException) {
            Log.e(TAG, "notification refused", e)
            listening.stop()
        }
    }

    private fun createChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(strings.getString(R.string.listening_channel))
                .setShowBadge(false)
                .build(),
        )
    }

    private fun notification(
        persona: Persona,
        enabled: Set<Command>,
        hearing: Hearing,
    ): Notification {
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
            .setContentTitle(strings.getString(persona.listeningRes()))
            .setContentText(stateText(persona, enabled, hearing))
            .setContentIntent(open)
            .addAction(0, strings.getString(R.string.home_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun stateText(
        persona: Persona,
        enabled: Set<Command>,
        hearing: Hearing,
    ): String? =
        when (hearing) {
            Hearing.HEARS ->
                (enabled.firstOrNull { it.calls } ?: enabled.firstOrNull())
                    ?.let { strings.getString(R.string.listening_say, it.spoken(persona)) }
            Hearing.PAUSED_FOR_CALL -> strings.getString(R.string.listening_paused_call)
            Hearing.CANNOT_HEAR -> strings.getString(R.string.listening_cannot_hear)
        }

    companion object {
        private const val TAG = "DarekListen"
        private const val CHANNEL_ID = LISTENING_CHANNEL_ID
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.darek.listen.STOP"

        /** False when nothing was started; whoever called says why. */
        fun start(context: Context): Boolean {
            // Android 14+ refuses startForeground for a `microphone` service without RECORD_AUDIO,
            // and a started service that never reaches it crashes the process.
            if (!context.hasMicrophone() || context.listeningNotificationBlock() != null) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java))
                true
            } catch (e: IllegalStateException) {
                Log.e(TAG, "foreground start refused", e)
                false
            }
        }
    }
}

fun Persona.listeningRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.home_listening
        Persona.DARIA -> R.string.home_listening_daria
    }
