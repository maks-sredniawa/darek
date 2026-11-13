package app.darek.listen

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.darek.MainActivity
import app.darek.MainApplication
import kotlinx.coroutines.launch

/** Windowless start for the tile and the boot notification (ADR-0010). */
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
