package app.darek.ui.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCreditsTest {
    @Test
    fun `the bundled models join their licences, each voice under each of its three`() {
        val report =
            """
            [{"groupId":"a","artifactId":"one","version":"1.0",
              "spdxLicenses":[{"identifier":"MIT","name":"MIT License"}]}]
            """.trimIndent()
        val groups = groupLicences(report, SupportConfig.BUNDLED_CREDITS)
        listOf("pl_PL-mc_speech-medium", "pl_PL-gosia-medium").forEach { voice ->
            val voiceIn = groups.filter { group -> group.artifacts.any { it.artifactId == voice } }
            assertEquals(voice, listOf("CC0-1.0", "MIT", null), voiceIn.map { it.spdxId })
        }
        assertEquals(3, groups.single { it.spdxId == "MIT" }.artifacts.size)
        val apache = groups.single { it.spdxId == "Apache-2.0" }
        assertTrue(apache.artifacts.any { it.artifactId == "vosk-model-small-pl" })
    }

    @Test
    fun `each bundled model lists its source and SHA-256 under its coordinates`() {
        SupportConfig.BUNDLED_CREDITS.forEach { model ->
            assertTrue(model.listing.startsWith("${model.coordinates}\nhttps://"))
            assertTrue(Regex("SHA-256 [0-9a-f]{64}$").containsMatchIn(model.listing))
        }
    }
}
