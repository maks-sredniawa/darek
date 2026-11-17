package app.darek.fuchy

import android.util.Log
import app.darek.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.security.PublicKey

/** [on] is what the user switched; [isOn] decides whether it counts. [granted] holds the per-fucha grants. */
@Serializable
data class FuchaState(
    val on: Boolean,
    val granted: Set<Permission> = emptySet(),
    val city: City? = null,
)

/** *The city you chose* (phase-F.md section 5): picked from the geocoding search, sent as its coordinates. */
@Serializable
data class City(
    val name: String,
    val region: String? = null,
    val latitude: Double,
    val longitude: Double,
)

/**
 * The fuchy on this phone: Darek's own, then each installed file in [dir], kept as it came so its
 * signature is checked again on every start (phase-F.md section 2). A fucha with no stored state is
 * on only if it is Darek's own. Its words were checked against the model at install, so a start does
 * not wait for the model to load.
 */
class Fuchy(
    private val dir: File,
    private val own: Fucha,
    private val preferences: AppPreferences,
    private val key: PublicKey = FuchyKey.maksForge,
) {
    private val files = MutableStateFlow<List<Fucha>?>(null)
    private val writes = Mutex()

    val installed: Flow<List<Installed>> =
        combine(files.filterNotNull(), preferences.fuchy, preferences.grantDefaults) { files, states, defaults ->
            (listOf(own) + files).map { Installed(it, isOn(it, states[it.id], defaults), states[it.id]) }
        }

    val on: Flow<List<Fucha>> = installed.map { all -> all.filter { it.on }.map { it.fucha } }.distinctUntilChanged()

    suspend fun load() = writes.withLock { if (files.value == null) files.value = read() }

    /** Throws [IOException] when the file cannot be saved, leaving no partial file and the state untouched. */
    suspend fun install(
        file: ByteArray,
        plan: InstallPlan,
    ) = writes.withLock {
        val id = plan.candidate.id
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val partial = File(dir, "$id.tmp")
            try {
                partial.writeBytes(file)
                if (!partial.renameTo(fileOf(id))) throw IOException("could not rename $partial")
            } catch (e: IOException) {
                partial.delete()
                throw e
            }
        }
        preferences.editFuchy { states -> states + (id to stateAfter(plan, states[id])) }
        files.value = files.value.orEmpty().filter { it.id != id } + plan.candidate
    }

    suspend fun remove(id: String) =
        writes.withLock {
            withContext(Dispatchers.IO) { fileOf(id).delete() }
            preferences.editFuchy { it - id }
            files.value = files.value.orEmpty().filter { it.id != id }
        }

    /** Switches [fucha] on and off what can never be on beside it, which it returns (ADR-0015). */
    suspend fun switchOn(fucha: Fucha): List<Fucha> {
        val turnsOff = switchingOn(fucha, installed.first()).turnsOff
        switch(mapOf(fucha.id to true) + turnsOff.associate { it.id to false })
        return turnsOff
    }

    val grantDefaults: Flow<Map<Permission, GrantDefault>> get() = preferences.grantDefaults

    suspend fun switch(changes: Map<String, Boolean>) =
        preferences.editFuchy { states ->
            states + changes.map { (id, on) -> id to (states[id]?.copy(on = on) ?: FuchaState(on)) }
        }

    suspend fun find(id: String): Installed? = installed.first().firstOrNull { it.fucha.id == id }

    suspend fun grantedTo(
        id: String,
        permission: Permission,
    ): Boolean {
        val found = find(id) ?: return false
        return granted(found.fucha, permission, found.state, preferences.grantDefaults.first())
    }

    /** Kept on the fucha whether or not it is on, so a later switch-on asks nothing it was already told. */
    suspend fun grant(
        id: String,
        permissions: Collection<Permission>,
    ) = edit(id) { it.copy(granted = it.granted + permissions) }

    suspend fun revoke(
        id: String,
        permission: Permission,
    ) = edit(id) { it.copy(granted = it.granted - permission) }

    suspend fun setCity(
        id: String,
        city: City,
    ) = edit(id) { it.copy(city = city) }

    private suspend fun edit(
        id: String,
        change: (FuchaState) -> FuchaState,
    ) = preferences.editFuchy { states -> states + (id to change(states[id] ?: FuchaState(on = false))) }

    private suspend fun read(): List<Fucha> =
        withContext(Dispatchers.IO) {
            dir
                .listFiles { file -> file.name.endsWith(SUFFIX) }
                .orEmpty()
                .sortedBy { it.name }
                .mapNotNull { file ->
                    when (val checked = checked(file.readBytes(), knows = { true }, key = key)) {
                        is Checked.Accepted -> checked.fucha.takeIf { fileOf(it.id) == file }
                        is Checked.Refused -> null.also { Log.w(TAG, "${file.name} refused: ${checked.refusals}") }
                    }
                }
        }

    private fun fileOf(id: String) = File(dir, "$id$SUFFIX")

    /** A fresh install starts from nothing; an update keeps the city and only the grants for permissions it still declares. */
    private fun stateAfter(
        plan: InstallPlan,
        old: FuchaState?,
    ): FuchaState =
        if (plan.fresh || old == null) {
            FuchaState(plan.startsOn)
        } else {
            old.copy(on = plan.startsOn, granted = old.granted intersect plan.candidate.permissions.toSet())
        }

    private companion object {
        const val TAG = "DarekFuchy"
        const val SUFFIX = ".darek"
    }
}
