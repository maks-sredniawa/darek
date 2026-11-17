package app.darek.fuchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md, *Tests*: fails towards a fucha taking another's utterances, or an unsigned file passing as an update. */
class InstallTest {
    private val maksForge = keyPair()

    private fun fucha(
        id: String = "zarty",
        phrase: String = "jaki kawał",
        permissions: List<String> = emptyList(),
        version: Int = 1,
        signed: Boolean = false,
    ): Fucha {
        val source =
            weather
                .copy(
                    id = id,
                    version = version,
                    permissions = permissions,
                    addresses = emptyList(),
                    sends = emptyList(),
                ).withCommand(
                    CommandSource(id = "c", phrase = phrase, action = "speak", reply = "Okej."),
                )
        val file = if (signed) signedFileOf(source, maksForge) else fileOf(source)
        return (checked(file, knowsAll, key = maksForge.public) as Checked.Accepted).fucha
    }

    @Test
    fun a_fucha_that_clashes_with_nothing_installs_on() {
        val plan = planInstall(fucha(), installed(ownFucha to true))
        assertTrue(plan.fresh)
        assertEquals(emptyList<Clash>(), plan.clashes)
        assertTrue(plan.startsOn)
    }

    @Test
    fun any_clash_installs_it_off() {
        listOf("otwórz bramę", "otwórz bramy", "powiedz coś więcej").forEach { phrase ->
            val plan = planInstall(fucha(phrase = phrase), installed(ownFucha to true))
            assertTrue(phrase, plan.clashes.isNotEmpty())
            assertFalse(phrase, plan.startsOn)
        }
    }

    @Test
    fun a_fucha_needing_a_permission_installs_off_until_it_is_granted() {
        assertFalse(planInstall(fucha(permissions = listOf("internet")), installed(ownFucha to true)).startsOn)
    }

    @Test
    fun only_fuchy_that_are_on_are_clashed_against() {
        val plan = planInstall(fucha(phrase = "otwórz bramę"), installed(ownFucha to false))
        assertEquals(emptyList<Clash>(), plan.clashes)
    }

    @Test
    fun an_update_keeps_its_switch_and_is_not_its_own_clash() {
        val old = fucha(signed = true)
        val newer = fucha(signed = true, version = 2)
        val wasOn = planInstall(newer, installed(ownFucha to true, old to true))
        assertFalse(wasOn.fresh)
        assertEquals(emptyList<Clash>(), wasOn.clashes)
        assertTrue(wasOn.startsOn)
        assertFalse(planInstall(newer, installed(ownFucha to true, old to false)).startsOn)
    }

    @Test
    fun an_update_keeps_what_the_user_switched_even_while_a_missing_grant_holds_it_off() {
        val old = fucha(signed = true, permissions = listOf("internet"))
        val newer = fucha(signed = true, version = 2, permissions = listOf("internet"))
        val switchedButUngranted = Installed(old, on = false, state = FuchaState(on = true))
        assertTrue(planInstall(newer, listOf(Installed(ownFucha, true), switchedButUngranted)).startsOn)
        val switchedOff = Installed(old, on = false, state = FuchaState(on = false))
        assertFalse(planInstall(newer, listOf(Installed(ownFucha, true), switchedOff)).startsOn)
    }

    @Test
    fun an_unsigned_file_over_an_approved_fucha_is_a_fresh_install() {
        val approved = fucha(signed = true)
        val unsigned = fucha(version = 2)
        assertTrue(approved.approved)
        assertFalse(unsigned.approved)
        assertTrue(planInstall(unsigned, installed(ownFucha to true, approved to false)).fresh)
        assertFalse(planInstall(unsigned, installed(ownFucha to true, fucha() to false)).fresh)
    }

    @Test
    fun switching_on_a_fucha_that_holds_another_switches_that_one_off() {
        val second = fucha(phrase = "otwórz bramę dwa")
        val plan = switchingOn(second, installed(ownFucha to true, second to false))
        assertEquals(listOf(ownFucha), plan.turnsOff)
        assertEquals(emptyList<Clash>(), plan.warnings)
    }

    @Test
    fun switching_on_a_fucha_that_sounds_like_another_asks_and_switches_nothing_off() {
        val near = fucha(phrase = "otwórz bramy")
        val plan = switchingOn(near, installed(ownFucha to true, near to false))
        assertEquals(emptyList<Fucha>(), plan.turnsOff)
        assertTrue(plan.warnings.any { it is Clash.SoundsLike })
    }

    @Test
    fun switching_on_darek_s_own_checks_it_against_what_is_on() {
        val holder = fucha(phrase = "powiedz coś")
        val plan = switchingOn(ownFucha, installed(ownFucha to false, holder to true))
        assertEquals(listOf(holder), plan.turnsOff)
    }
}
