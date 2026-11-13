package app.darek.listen

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.darek.MainApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** See phase-2.md, *The start paths*. */
class ListeningTile : TileService() {
    private var scope: CoroutineScope? = null
    private var running = false

    private val container get() = (application as MainApplication).container

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope =
            MainScope().also {
                it.launch {
                    container.listening.running.collect { now ->
                        running = now
                        show()
                    }
                }
            }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        if (running) {
            container.stopListening()
        } else if (isLocked) {
            unlockAndRun(::openStart)
        } else {
            openStart()
        }
    }

    private fun show() {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openStart() {
        val intent = Intent(this, StartListeningActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The Intent overload throws for targetSdk 34+ on Android 14+ (ADR-0010).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
