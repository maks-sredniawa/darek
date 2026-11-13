package app.darek.ui.support

/**
 * Everything about Help and feedback that differs from one app to the next — **the one file in
 * `ui/support/` that `scripts/sync-support.py` never overwrites.** Every other file in this package,
 * and the `shared:support` block of each `strings.xml`, comes from android-starter.
 *
 * `bootstrap.py` writes the four constants. Each is compiled into every build and frozen there for as
 * long as a sideloaded copy survives, so the address and the site sit on a domain we own, whose
 * routing can move without a release. The Play listing's contact field carries the same address —
 * `docs/DOD.md`, *Before the first upload*.
 */
object SupportConfig {
    const val SUPPORT_ADDRESS = "darek.support@maksforge.com"

    /**
     * The id Play knows this app by, as a literal rather than `BuildConfig.APPLICATION_ID`: debug
     * builds carry `applicationIdSuffix = ".debug"`, and Play has never heard of the suffixed id.
     */
    const val PLAY_PACKAGE = "com.maksforge.darek"

    /** The mail subject's prefix — "Starter #bug" — which a mail filter sorts on. Never translated. */
    const val MAIL_TAG = "Darek"

    /** The app's page on maksforge.com; the privacy policy is `privacy-policy` beneath it. */
    const val SITE_URL = "https://maksforge.com/darek/"

    /**
     * What the build ships that Licensee never sees — a model or a data set fetched as a file — shown
     * on the Credits screen beside the dependencies. Here the recogniser's model (ADR-0009) and both
     * voices, whose base voice's terms are named as ADR-0014 states them.
     */
    val BUNDLED_CREDITS: List<LicensedArtifact> =
        listOf(
            LicensedArtifact(
                groupId = "com.alphacephei",
                artifactId = "vosk-model-small-pl",
                version = "0.22",
                name = "Vosk Polish speech model",
                source =
                    "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip · " +
                        "SHA-256 c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d",
                spdxLicenses =
                    listOf(
                        SpdxLicence("Apache-2.0", "Apache License 2.0", "https://alphacephei.com/vosk/models"),
                    ),
            ),
            LicensedArtifact(
                groupId = "rhasspy.piper-voices",
                artifactId = "pl_PL-mc_speech-medium",
                version = "medium",
                name = "Darek's voice: Piper pl_PL mc_speech",
                source =
                    "https://huggingface.co/rhasspy/piper-voices/resolve/b4e358ffcb232591bf73a540e70f7de528e4174a/" +
                        "pl/pl_PL/mc_speech/medium/" +
                        "pl_PL-mc_speech-medium.onnx · " +
                        "SHA-256 a6b043358bc81e6c111a5140606a21959ce7f34969b8b7207f62869787cc3907",
                spdxLicenses =
                    listOf(
                        SpdxLicence(
                            "MIT",
                            "MIT License",
                            "https://huggingface.co/rhasspy/piper-voices/blob/" +
                                "b4e358ffcb232591bf73a540e70f7de528e4174a/" +
                                "pl/pl_PL/mc_speech/medium/MODEL_CARD",
                        ),
                        SpdxLicence("CC0-1.0", "Creative Commons Zero v1.0 Universal (MC Speech recordings)"),
                    ),
                unknownLicenses =
                    listOf(
                        UnknownLicence(
                            "Blizzard 2013 lessac recordings, the base voice it was fine-tuned from: research use only",
                            "https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html",
                        ),
                    ),
            ),
            LicensedArtifact(
                groupId = "rhasspy.piper-voices",
                artifactId = "pl_PL-gosia-medium",
                version = "medium",
                name = "Daria's voice: Piper pl_PL gosia",
                source =
                    "https://huggingface.co/rhasspy/piper-voices/resolve/b4e358ffcb232591bf73a540e70f7de528e4174a/" +
                        "pl/pl_PL/gosia/medium/" +
                        "pl_PL-gosia-medium.onnx · " +
                        "SHA-256 38f66464240ed74f186e6b7dc13c6e3b22e023426299f25c2b3cc9dfa9373fbc",
                spdxLicenses =
                    listOf(
                        SpdxLicence(
                            "MIT",
                            "MIT License",
                            "https://huggingface.co/rhasspy/piper-voices/blob/" +
                                "b4e358ffcb232591bf73a540e70f7de528e4174a/" +
                                "pl/pl_PL/gosia/medium/MODEL_CARD",
                        ),
                        SpdxLicence("CC0-1.0", "Creative Commons Zero v1.0 Universal (OHF-Voice gosia recordings)"),
                    ),
                unknownLicenses =
                    listOf(
                        UnknownLicence(
                            "Blizzard 2013 lessac recordings, the base voice it was fine-tuned from: research use only",
                            "https://www.cstr.ed.ac.uk/projects/blizzard/2013/lessac_blizzard2013/license.html",
                        ),
                    ),
            ),
        )
}
