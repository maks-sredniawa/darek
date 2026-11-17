package app.darek.ui.fuchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.data.AppPreferences
import app.darek.fuchy.Fucha
import app.darek.fuchy.Fuchy
import app.darek.fuchy.GrantDefault
import app.darek.fuchy.Permission
import app.darek.fuchy.defaultFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [exceptions]: the fuchy the user granted [permission] to one by one, which win over [default]. */
data class PermissionGrants(
    val permission: Permission,
    val default: GrantDefault,
    val exceptions: List<Fucha>,
)

data class GrantsUiState(
    val loaded: Boolean = false,
    val permissions: List<PermissionGrants> = emptyList(),
    /** *Allow for every fucha* waits behind the ten-second dialog, since it covers unapproved ones too. */
    val confirmingAll: Permission? = null,
)

/** *Uprawnienia fuch*: per permission, the default and its exceptions (phase-F.md section 5). */
class GrantsViewModel(
    private val fuchy: Fuchy,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val confirming = MutableStateFlow<Permission?>(null)

    val state: StateFlow<GrantsUiState> =
        combine(fuchy.installed, fuchy.grantDefaults, confirming) { installed, defaults, confirming ->
            GrantsUiState(
                loaded = true,
                permissions =
                    Permission.entries.filter { it.forFuchy }.map { permission ->
                        PermissionGrants(
                            permission,
                            defaults.defaultFor(permission),
                            installed
                                .filter { !it.fucha.bundled && permission in it.state?.granted.orEmpty() }
                                .map { it.fucha }
                                .sortedBy { it.name },
                        )
                    },
                confirmingAll = confirming,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrantsUiState())

    fun choose(
        permission: Permission,
        default: GrantDefault,
    ) {
        if (default == GrantDefault.ALL) {
            confirming.value = permission
        } else {
            viewModelScope.launch { preferences.setGrantDefault(permission, default) }
        }
    }

    fun confirmAll() {
        val permission = confirming.value ?: return
        confirming.value = null
        viewModelScope.launch { preferences.setGrantDefault(permission, GrantDefault.ALL) }
    }

    fun dismissAll() {
        confirming.value = null
    }

    fun revoke(
        fucha: Fucha,
        permission: Permission,
    ) {
        viewModelScope.launch { fuchy.revoke(fucha.id, permission) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    GrantsViewModel(app.container.fuchy, app.preferences)
                }
            }
    }
}
