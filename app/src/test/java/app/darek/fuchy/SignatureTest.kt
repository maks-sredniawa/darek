package app.darek.fuchy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** phase-F.md, *Tests*: fails towards badging what MaksForge never reviewed. */
class SignatureTest {
    private val maksForge = keyPair()
    private val stranger = keyPair()

    private fun approved(file: ByteArray) =
        (checked(file, knowsAll, key = maksForge.public) as Checked.Accepted).fucha.approved

    @Test
    fun a_file_signed_by_the_key_is_approved() {
        assertTrue(approved(signedFileOf(weather, maksForge)))
    }

    @Test
    fun a_missing_signature_is_not_approved() {
        assertFalse(approved(fileOf(weather)))
    }

    @Test
    fun a_signature_from_another_key_is_not_approved() {
        assertFalse(approved(signedFileOf(weather, stranger)))
    }

    @Test
    fun one_changed_byte_in_the_fucha_is_not_approved() {
        val inner = Json.encodeToString(weather).toByteArray()
        val signed = signature(inner, maksForge)
        val changed = Json.encodeToString(weather.copy(version = 2)).toByteArray()
        assertTrue(approved(envelope(inner, signed)))
        assertFalse(approved(envelope(changed, signed)))
    }

    @Test
    fun a_signature_that_is_not_one_is_not_approved_and_not_a_crash() {
        listOf("", "bm90IGEgc2lnbmF0dXJl", "%%% not base64 %%%").forEach {
            assertFalse(it, approved(fileOf(weather, signature = it)))
        }
    }

    @Test
    fun darek_s_own_is_approved_unsigned_and_only_by_being_bundled() {
        assertTrue(ownFucha.approved)
        val unsigned = (checked(fileOf(weather), knowsAll, bundled = true, key = maksForge.public) as Checked.Accepted)
        assertTrue(unsigned.fucha.approved)
    }

    @Test
    fun the_shipped_key_is_the_one_darek_fuchy_signs_with() {
        val pogoda = parse(File("src/test/fuchy/pogoda.darek").readBytes()) as Parsed.Read
        assertTrue(signedBy(pogoda.bytes, pogoda.signature, FuchyKey.maksForge))
        assertFalse(signedBy(pogoda.bytes + 0, pogoda.signature, FuchyKey.maksForge))
    }
}
