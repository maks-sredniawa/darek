package app.darek.fuchy

/**
 * What a permission's fuchy get without being asked, set on *Uprawnienia fuch* (phase-F.md section 5).
 * [ALL] covers unapproved fuchy too, which is why the screen puts it behind the ten-second dialog.
 */
enum class GrantDefault { ASK, APPROVED, ALL, NEVER }

fun Map<Permission, GrantDefault>.defaultFor(permission: Permission): GrantDefault =
    this[permission] ?: GrantDefault.ASK

/** A grant the user gave this fucha is an exception to the default and wins over it; Darek's own needs none. */
fun granted(
    fucha: Fucha,
    permission: Permission,
    state: FuchaState?,
    defaults: Map<Permission, GrantDefault>,
): Boolean =
    fucha.bundled ||
        permission in state?.granted.orEmpty() ||
        when (defaults.defaultFor(permission)) {
            GrantDefault.APPROVED -> fucha.approved
            GrantDefault.ALL -> true
            GrantDefault.ASK, GrantDefault.NEVER -> false
        }

fun missingGrants(
    fucha: Fucha,
    state: FuchaState?,
    defaults: Map<Permission, GrantDefault>,
): List<Permission> = fucha.permissions.filterNot { granted(fucha, it, state, defaults) }

/**
 * A stored *on* counts only with every grant and, for a fucha that sends a city, the city. So an *on*
 * stored before grants existed, or one whose grant was taken back, is not trusted.
 */
fun isOn(
    fucha: Fucha,
    state: FuchaState?,
    defaults: Map<Permission, GrantDefault>,
): Boolean =
    (state?.on ?: fucha.bundled) &&
        missingGrants(fucha, state, defaults).isEmpty() &&
        (!fucha.needsCity || state?.city != null)
