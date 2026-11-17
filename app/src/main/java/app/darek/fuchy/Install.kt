package app.darek.fuchy

data class Installed(
    val fucha: Fucha,
    val on: Boolean,
)

/** What *Zainstaluj* does, shown before the tap (phase-F.md section 3). */
data class InstallPlan(
    val candidate: Fucha,
    val replaces: Installed?,
    val clashes: List<Clash>,
) {
    /** An unsigned file over an approved fucha with the same ID is a fresh install, not an update (ADR-0015, *Updates*). */
    val fresh: Boolean get() = replaces == null || (replaces.fucha.approved && !candidate.approved)

    /**
     * Any clash installs it off, behind its warning (ADR-0015); so does a permission, whose grant comes
     * with the switch-on. An update keeps the switch it had.
     */
    val startsOn: Boolean
        get() = clashes.isEmpty() && candidate.permissions.isEmpty() && (fresh || replaces?.on == true)
}

fun planInstall(
    candidate: Fucha,
    installed: List<Installed>,
): InstallPlan =
    InstallPlan(
        candidate = candidate,
        replaces = installed.firstOrNull { it.fucha.id == candidate.id },
        clashes = clashes(candidate, commandsOn(installed, except = candidate.id)),
    )

/** [turnsOff] holds or is held by [target] and can never be on beside it; [warnings] the user may override. */
data class SwitchOn(
    val turnsOff: List<Fucha>,
    val warnings: List<Clash>,
)

fun switchingOn(
    target: Fucha,
    installed: List<Installed>,
): SwitchOn {
    val clashes = clashes(target, commandsOn(installed, except = target.id))
    val off = clashes.filterIsInstance<Clash.Holds>().map { it.enabled.fucha }.toSet()
    return SwitchOn(
        turnsOff = installed.map { it.fucha }.filter { it.id in off },
        warnings = clashes.filterNot { it is Clash.Holds || it.with() in off },
    )
}

private fun commandsOn(
    installed: List<Installed>,
    except: String,
) = installed.filter { it.on && it.fucha.id != except }.flatMap { it.fucha.commands }

private fun Clash.with(): String? =
    when (this) {
        is Clash.Holds -> enabled.fucha
        is Clash.SoundsLike -> enabled.fucha
        is Clash.NearWord -> enabled.fucha
        is Clash.NearName -> null
    }
