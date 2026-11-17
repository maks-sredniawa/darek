package app.darek.ui.fuchy

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.fuchy.Checked
import app.darek.fuchy.Fuchy
import app.darek.fuchy.InstallPlan
import app.darek.fuchy.Parsed
import app.darek.fuchy.Refusal
import app.darek.fuchy.checked
import app.darek.fuchy.parse
import app.darek.fuchy.planInstall
import app.darek.listen.LoadedModel
import app.darek.listen.Persona
import app.darek.listen.Vocabulary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

sealed interface InstallStep {
    data object Checking : InstallStep

    data object Unreadable : InstallStep

    /** The recogniser's model would not load, so the words cannot be checked and nothing installs. */
    data object CannotCheck : InstallStep

    /** [phrases] maps each Command's ID to its phrase, which is what the user knows it by. */
    data class Refused(
        val refusals: List<Refusal>,
        val phrases: Map<String, String> = emptyMap(),
    ) : InstallStep

    /** [names] maps every installed fucha's ID to its name, for the clashes. */
    data class Ready(
        val plan: InstallPlan,
        val names: Map<String, String>,
    ) : InstallStep

    data object Installed : InstallStep
}

data class InstallUiState(
    val step: InstallStep = InstallStep.Checking,
    val persona: Persona = Persona.DAREK,
)

/** Checks a picked file as an install does (phase-F.md section 2) and installs it only on *Zainstaluj*. */
class InstallViewModel(
    private val uri: Uri,
    private val resolver: ContentResolver,
    private val model: LoadedModel,
    private val fuchy: Fuchy,
    persona: Flow<Persona>,
) : ViewModel() {
    private val step = MutableStateFlow<InstallStep>(InstallStep.Checking)
    private var file: ByteArray? = null

    val state: StateFlow<InstallUiState> =
        combine(step, persona, ::InstallUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InstallUiState())

    init {
        viewModelScope.launch { step.value = check() }
    }

    fun install() {
        val ready = step.value as? InstallStep.Ready ?: return
        val bytes = file ?: return
        step.value = InstallStep.Checking
        viewModelScope.launch {
            fuchy.install(bytes, ready.plan)
            step.value = InstallStep.Installed
        }
    }

    private suspend fun check(): InstallStep {
        val bytes = read() ?: return InstallStep.Unreadable
        file = bytes
        val loaded =
            try {
                model.get()
            } catch (e: IOException) {
                return InstallStep.CannotCheck.also { Log.e(TAG, "model failed to load, words cannot be checked", e) }
            }
        val result = withContext(Dispatchers.Default) { checked(bytes, knows = { Vocabulary.knows(loaded, it) }) }
        return when (result) {
            is Checked.Refused ->
                InstallStep.Refused(
                    result.refusals,
                    (parse(bytes) as? Parsed.Read)
                        ?.source
                        ?.commands
                        .orEmpty()
                        .associate { it.id to it.phrase },
                )
            is Checked.Accepted -> {
                val installed = fuchy.installed.first()
                InstallStep.Ready(
                    planInstall(result.fucha, installed),
                    installed.associate {
                        it.fucha.id to
                            it.fucha.name
                    },
                )
            }
        }
    }

    // Capped, so a huge file picked by mistake cannot exhaust memory; every real fucha is a few kilobytes.
    private suspend fun read(): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readAtMost(MAX_BYTES + 1)
                    bytes.takeIf { it.size <= MAX_BYTES }
                }
            } catch (e: IOException) {
                null.also { Log.w(TAG, "could not read $uri", e) }
            } catch (e: SecurityException) {
                null.also { Log.w(TAG, "lost the grant to $uri", e) }
            }
        }

    // InputStream.readNBytes arrives only at API 33.
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "DarekFuchy"
        private const val MAX_BYTES = 256 * 1024

        fun factory(uri: Uri): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    InstallViewModel(
                        uri,
                        app.contentResolver,
                        app.container.model,
                        app.container.fuchy,
                        app.preferences.persona,
                    )
                }
            }
    }
}
