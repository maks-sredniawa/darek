package app.darek

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import android.service.quicksettings.TileService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.darek.listen.AfterBootReceiver
import app.darek.listen.ListeningTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Android reads off the installed manifest, which no source file shows whole: a dependency can
 * merge a permission, and a receiver the OS starts on its own is referenced by nothing in Kotlin. Add
 * a case here for every component the OS starts by itself.
 */
@RunWith(AndroidJUnit4::class)
class ManifestWiringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun internet_and_none_of_the_stripped_permissions() {
        assertTrue("android.permission.INTERNET" in requestedPermissions())
        assertEquals(emptySet<String>(), requestedPermissions() intersect NOT_REQUESTED)
    }

    @Test
    fun no_host_is_reached_over_plain_http() {
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("maksforge.com"))
    }

    @Test
    fun datatransport_has_no_backend_to_upload_through_and_keeps_its_scheduler() {
        val discovery =
            context.packageManager.getServiceInfo(
                ComponentName(context, TRANSPORT_BACKEND_DISCOVERY),
                PackageManager.GET_META_DATA,
            )
        assertEquals(
            emptySet<String>(),
            discovery.metaData
                ?.keySet()
                .orEmpty()
                .filter { it.startsWith("backend:") }
                .toSet(),
        )
        context.packageManager.getServiceInfo(ComponentName(context, TRANSPORT_JOB_SERVICE), 0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun boot_completed_reaches_the_after_boot_receiver() {
        val receivers =
            context.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
                0,
            )
        assertEquals(listOf(AfterBootReceiver::class.java.name), receivers.map { it.activityInfo.name })
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in requestedPermissions())
    }

    @Suppress("DEPRECATION")
    @Test
    fun the_tile_is_bound_only_by_the_system() {
        val tiles =
            context.packageManager.queryIntentServices(
                Intent(TileService.ACTION_QS_TILE).setPackage(context.packageName),
                0,
            )
        assertEquals(listOf(ListeningTile::class.java.name), tiles.map { it.serviceInfo.name })
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tiles.single().serviceInfo.permission)
    }

    private fun requestedPermissions(): Set<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()

    private companion object {
        // ACCESS_WIFI_STATE per ADR-0008; the rest is what the template's reminders and WorkManager
        // declared or merged. RECEIVE_BOOT_COMPLETED is declared on purpose (ADR-0010). This list
        // moves with aab-permissions.py.
        val NOT_REQUESTED =
            setOf(
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.WAKE_LOCK",
            )

        const val TRANSPORT_BACKEND_DISCOVERY =
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery"
        const val TRANSPORT_JOB_SERVICE =
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService"
    }
}
