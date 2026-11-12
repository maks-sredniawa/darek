package app.darek.listen

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.darek.MainActivity
import app.darek.MainApplication
import kotlinx.coroutines.launch

/**
 * Starts Listening from the Quick Settings tile and the notification after a reboot. Neither may
 * start a `microphone` service itself on Android 14+, a visible activity may (ADR-0010), so this one
 * has no window, starts it and closes. Anything still owed before Listening may start (the
 * disclosure, the microphone, the notifications) is Home's to ask, so then it opens Home instead.
 */
class StartListeningActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val container = (application as MainApplication).container
        lifecycleScope.launch {
            AfterBootReceiver.cancel(this@StartListeningActivity)
            if (!container.listeningStartable() || !ListeningService.start(this@StartListeningActivity)) {
                startActivity(
                    Intent(this@StartListeningActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            finish()
        }
    }
}
