package app.darek.fuchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md section 5: fails towards a fucha connecting with no grant the user gave or chose to give. */
class GrantsTest {
    private val maksForge = keyPair()
    private val approved =
        (
            checked(
                signedFileOf(weatherAction, maksForge),
                knowsAll,
                key = maksForge.public,
            ) as Checked.Accepted
        ).fucha
    private val unapproved = accepted(weatherAction)
    private val krakow = City("Kraków", "Małopolskie", 50.06, 19.94)
    private val ask = emptyMap<Permission, GrantDefault>()

    private fun defaults(default: GrantDefault) = mapOf(Permission.INTERNET to default)

    @Test
    fun with_no_default_stored_a_fucha_is_asked() {
        assertEquals(listOf(Permission.INTERNET), missingGrants(approved, null, ask))
        assertEquals(GrantDefault.ASK, ask.defaultFor(Permission.INTERNET))
    }

    @Test
    fun a_stored_on_without_its_grant_is_not_trusted() {
        assertFalse(isOn(approved, FuchaState(on = true, city = krakow), ask))
        assertTrue(isOn(approved, FuchaState(on = true, granted = setOf(Permission.INTERNET), city = krakow), ask))
    }

    @Test
    fun a_fucha_that_sends_the_city_is_off_until_one_is_chosen() {
        assertFalse(isOn(approved, FuchaState(on = true, granted = setOf(Permission.INTERNET)), ask))
    }

    @Test
    fun allowing_approved_fuchy_covers_only_approved_ones() {
        val state = FuchaState(on = true, city = krakow)
        assertTrue(isOn(approved, state, defaults(GrantDefault.APPROVED)))
        assertFalse(isOn(unapproved, state, defaults(GrantDefault.APPROVED)))
        assertTrue(isOn(unapproved, state, defaults(GrantDefault.ALL)))
    }

    @Test
    fun a_grant_given_by_hand_wins_over_never_and_never_grants_nothing_else() {
        val byHand = FuchaState(on = true, granted = setOf(Permission.INTERNET), city = krakow)
        assertTrue(isOn(approved, byHand, defaults(GrantDefault.NEVER)))
        assertFalse(isOn(approved, byHand.copy(granted = emptySet()), defaults(GrantDefault.NEVER)))
    }

    @Test
    fun darek_s_own_needs_no_grant_and_is_on_until_switched_off() {
        assertTrue(isOn(ownFucha, null, defaults(GrantDefault.NEVER)))
        assertFalse(isOn(ownFucha, FuchaState(on = false), ask))
    }
}
