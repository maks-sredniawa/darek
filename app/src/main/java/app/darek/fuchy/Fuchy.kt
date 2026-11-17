package app.darek.fuchy

import android.util.Log
import app.darek.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.PublicKey

@Serializable
data class FuchaState(
    val on: Boolean,
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
        combine(files.filterNotNull(), preferences.fuchy) { files, states ->
            (listOf(own) + files).map { Installed(it, states[it.id]?.on ?: it.bundled) }
        }

    val on: Flow<List<Fucha>> = installed.map { all -> all.filter { it.on }.map { it.fucha } }.distinctUntilChanged()

    suspend fun load() = writes.withLock { if (files.value == null) files.value = read() }

    suspend fun install(
        file: ByteArray,
        plan: InstallPlan,
    ) = writes.withLock {
        val id = plan.candidate.id
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val partial = File(dir, "$id.tmp")
            partial.writeBytes(file)
            check(partial.renameTo(fileOf(id))) { "could not rename $partial" }
        }
        preferences.editFuchy { it + (id to FuchaState(plan.startsOn)) }
        files.value = files.value.orEmpty().filter { it.id != id } + plan.candidate
    }

    suspend fun remove(id: String) =
        writes.withLock {
            withContext(Dispatchers.IO) { fileOf(id).delete() }
            preferences.editFuchy { it - id }
            files.value = files.value.orEmpty().filter { it.id != id }
        }

    suspend fun switch(changes: Map<String, Boolean>) =
        preferences.editFuchy { states ->
            states + changes.map { (id, on) -> id to (states[id]?.copy(on = on) ?: FuchaState(on)) }
        }

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

    private companion object {
        const val TAG = "DarekFuchy"
        const val SUFFIX = ".darek"
    }
}
