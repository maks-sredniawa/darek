package app.darek.listen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AfterBootTest {
    private val installedAt = Instant.ofEpochMilli(1_000)

    @Test
    fun `offers Listen when Listening was left on under this install`() {
        assertTrue(offersListenAfterBoot(wantedUnder = installedAt, installedAt = installedAt))
    }

    @Test
    fun `stays quiet once Stop cleared it`() {
        assertFalse(offersListenAfterBoot(wantedUnder = null, installedAt = installedAt))
    }

    @Test
    fun `stays quiet on a phone the setting was restored onto`() {
        assertFalse(offersListenAfterBoot(wantedUnder = Instant.ofEpochMilli(500), installedAt = installedAt))
    }
}
