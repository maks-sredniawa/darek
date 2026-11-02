import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.licensee)
}

// Whether *this invocation* is building a release. Two things below refuse to guess when it is:
// the version code and the signing config. Both are silent, plausible failures otherwise — a
// release signed with the debug key installs fine and can never be uploaded, and a versionCode
// that fell back to 1 can never climb again on a track that has seen a higher one.
val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

// `-PreleaseShapedDebug` builds the debug variant through R8 with the release rules, keeping the
// debug applicationId so it installs beside a Play copy. It is how you find out that minification
// broke something *before* uploading, on a build you can still attach a debugger to.
val releaseShapedDebug = providers.gradleProperty("releaseShapedDebug").isPresent

// versionCode = the number of commits. Monotonic, needs no file to be hand-edited, and cannot be
// forgotten — which is the failure mode of every scheme that stores it. versionName is release-
// please's to write (see the marker comment below); this half is git's.
val gitVersionCode: Int =
    runCatching {
        providers
            .exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
            .toInt()
    }.getOrElse { cause ->
        if (buildingRelease) {
            error(
                "Release build requested but the versionCode could not be derived from " +
                    "`git rev-list --count HEAD` ($cause).\n" +
                    "Falling back to 1 here would ship a version code that cannot climb. Build " +
                    "releases from a full clone — not a shallow checkout or a source archive.",
            )
        }
        1
    }

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

// The upload keystore lives **outside the repository** and is referenced from local.properties,
// which is gitignored. Losing it means never being able to update the app on Play again, so back
// it up somewhere that is not this machine before the first upload.
val uploadKeyProperties =
    listOf(
        "upload.storeFile",
        "upload.storePassword",
        "upload.keyAlias",
        "upload.keyPassword",
    )
val hasUploadKey = uploadKeyProperties.all { localProperties.getProperty(it)?.isNotBlank() == true }

// Fail loudly rather than fall back to the debug key. An unsigned-for-upload release build is
// indistinguishable from a good one until Play rejects it.
if (buildingRelease && !hasUploadKey) {
    error(
        "Release build requested but the upload key is missing. Add to local.properties:\n" +
            uploadKeyProperties.joinToString("\n") { "  $it=..." } +
            "\nThe keystore belongs outside the repo and is never committed. See docs/RELEASING.md.",
    )
}

android {
    // The Kotlin package root. Refactorable at any time — imports follow it.
    namespace = "app.darek"
    compileSdk = 36

    defaultConfig {
        // The one string here that can never change: a Play Console package name is fixed the
        // moment the app entry is created, so it is deliberately allowed to differ from `namespace`.
        applicationId = "com.maksforge.darek"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = "0.8.0" // x-release-please-version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("release") {
                storeFile = file(localProperties.getProperty("upload.storeFile"))
                storePassword = localProperties.getProperty("upload.storePassword")
                keyAlias = localProperties.getProperty("upload.keyAlias")
                keyPassword = localProperties.getProperty("upload.keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // So the debug build installs *alongside* a Play copy instead of replacing it. Without
            // this, testing a local change means uninstalling the real app — and on a signature
            // mismatch the install is simply refused.
            applicationIdSuffix = ".debug"
            if (releaseShapedDebug) {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                isDebuggable = false
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Darek's voice is memory-mapped from the APK (ADR-0014), which only an uncompressed asset allows.
    androidResources {
        noCompress += "onnx"
    }

    lint {
        // "A newer version exists" is news, not a defect. Left informational so a red lint run
        // always means something is actually wrong with the code.
        //
        // MissingTranslation is here for a sharper reason: it contradicts this project's stated
        // rule. A branch merges with every shipped language complete, but adding an English string
        // is explicitly *not* supposed to redden the build — copy gets translated once, against
        // reviewed wording, rather than twice. `scripts/translation-gate.py` enforces completeness
        // at the merge boundary, where the decision belongs, and it understands staged drafts and
        // `translatable="false"` where lint does not. Left fatal here, every English-first commit
        // is red for the entirely normal reason that its translations are not written yet — which
        // the template's own strings hid, because they all arrived translated.
        informational +=
            setOf(
                "AndroidGradlePluginVersion",
                "GradleDependency",
                "MissingTranslation",
                "NewerVersionAvailable",
                "OldTargetApi",
            )
        // Resource shrinking strips an unused string from the release bundle, which fails
        // `aab-locale.py` at publish time; this fails the PR instead.
        error += "UnusedResources"
    }
}

kotlin {
    jvmToolchain(21)
}

// TranslationTest reads `res/` off disk as plain files, because an XML resource is not readable
// from a JVM unit test without Robolectric — and the file itself is the artifact whose contents are
// in question. Gradle cannot see that: a `File("src/main/res/…")` opened inside a test body is
// invisible to up-to-date checking, so **editing a translation and re-running `test` reports the
// previous run's verdict** — `:app:test UP-TO-DATE`, green, having checked nothing. That is the
// worst shape a gate can fail in: it passes because it did not run.
//
// Declaring the directory as an input is the whole fix.
tasks.withType<Test>().configureEach {
    inputs
        .dir(layout.projectDirectory.dir("src/main/res"))
        .withPropertyName("resourcesReadByUnitTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // `translations/` holds drafts staged outside `res/` so their existence does not ship them, and
    // TranslationTest holds a draft to the same rules as a shipped language. Registered as a **file
    // tree** rather than a directory because it is legitimately absent whenever nothing is staged —
    // a missing directory is a hard validation failure for `inputs.dir()`, and `.optional()` does
    // not rescue it (that makes the *property* optional, not the *path* absent).
    inputs
        .files(fileTree(rootProject.layout.projectDirectory.dir("translations")))
        .withPropertyName("stagedTranslationsReadByUnitTests")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Attribution, generated rather than remembered.
//
// The obligation is over the **resolved runtime classpath**, not the entries in
// `libs.versions.toml`: Compose alone pulls dozens of transitive artifacts and Apache-2.0 §4
// travels with each. Licensee resolves that classpath, so the list cannot go stale behind a
// dependency bump. An artifact whose licence is not allowed here **fails the build** — the failure
// a hand-typed list has no way to produce. When it fires, the fix is two things: allow the licence
// here, and put its text in `src/main/assets/licences/<spdx-id>.txt` so it travels with the binary.
licensee {
    allow("Apache-2.0")
    // Exactly one artifact: androidx's repackaging of protobuf-javalite, pulled in behind DataStore.
    // Nobody knows this is in the build until the plugin is run — which is the argument for it.
    allow("BSD-3-Clause")
    // Google Play services, behind the Play Billing Library (the tip, ADR-0007). Vendor terms rather
    // than an open licence, so the Licences screen links them instead of shipping their text.
    allowUrl("https://developer.android.com/studio/terms.html")
    // ONNX Runtime, which runs Darek's voice (ADR-0014).
    allow("MIT")
}

/**
 * Licensee's report, copied into the variant's assets as the one file the app reads.
 *
 * A copy rather than pointing the asset source directory at the report folder, because that folder
 * also holds `validation.txt` — a build artifact with no business in an APK.
 *
 * Gradle note: `abstract class` with `@get:`-annotated abstract properties is Gradle's lazy-property
 * idiom — it generates the implementation, and the `Property` types are what let a value be wired
 * before the task runs (roughly a promise Gradle resolves at execution time).
 */
abstract class BundleLicences : DefaultTask() {
    @get:InputFile
    abstract val artifacts: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun bundle() {
        val destination = outputDirectory.get().asFile.resolve(ASSET_NAME)
        destination.parentFile.mkdirs()
        artifacts.get().asFile.copyTo(destination, overwrite = true)
    }

    companion object {
        const val ASSET_NAME = "licences.json"
    }
}

/**
 * A large file the build needs but the repo does not hold: fetched once into Gradle's user cache,
 * checked against its SHA-256, and copied into the variant's generated assets, or unzipped into the
 * directory `assetPath` names. A changed file fails the build rather than shipping.
 */
abstract class FetchVerifiedAsset : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val assetPath: Property<String>

    @get:Input
    abstract val unzip: Property<Boolean>

    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun fetch() {
        val cached = cacheDirectory.get().asFile.resolve("${sha256.get()}/${url.get().substringAfterLast('/')}")
        if (!cached.exists() || cached.sha256() != sha256.get()) {
            cached.parentFile.mkdirs()
            val partial = Files.createTempFile(cached.parentFile.toPath(), cached.name, ".part").toFile()
            try {
                val connection = URI(url.get()).toURL().openConnection()
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                val actual = partial.sha256()
                if (actual != sha256.get()) {
                    throw GradleException("${url.get()}: SHA-256 $actual, expected ${sha256.get()}")
                }
                Files.move(
                    partial.toPath(),
                    cached.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                partial.delete()
            }
        }
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val destination = output.resolve(assetPath.get())
        if (unzip.get()) {
            cached.unzipInto(destination)
        } else {
            destination.parentFile.mkdirs()
            cached.copyTo(destination, overwrite = true)
        }
    }

    private fun File.unzipInto(destination: File) {
        val root = destination.canonicalFile
        ZipFile(this).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = root.resolve(entry.name).canonicalFile
                if (!target.startsWith(root)) throw GradleException("$name: ${entry.name} escapes the archive")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class VerifiedAsset(
    val url: String,
    val sha256: String,
    val path: String,
    val unzip: Boolean = false,
)

// Each Persona's voice (ADR-0014, ADR-0016) is a Piper model as published, named after the
// Persona; the recogniser's model is ADR-0009's, unzipped to `vosk/vosk-model-small-pl-0.22/`.
private val piperCommit = "b4e358ffcb232591bf73a540e70f7de528e4174a"
private val piperVoices = "https://huggingface.co/rhasspy/piper-voices/resolve/$piperCommit/pl/pl_PL"
private val shippedAssets =
    listOf(
        VerifiedAsset(
            "$piperVoices/mc_speech/medium/pl_PL-mc_speech-medium.onnx",
            "a6b043358bc81e6c111a5140606a21959ce7f34969b8b7207f62869787cc3907",
            "voice/darek.onnx",
        ),
        VerifiedAsset(
            "$piperVoices/mc_speech/medium/pl_PL-mc_speech-medium.onnx.json",
            "b8bb11228e15c505219846a88fdc129e93f57e774ed7f9bac263156d1aa3d324",
            "voice/darek.onnx.json",
        ),
        VerifiedAsset(
            "$piperVoices/gosia/medium/pl_PL-gosia-medium.onnx",
            "38f66464240ed74f186e6b7dc13c6e3b22e023426299f25c2b3cc9dfa9373fbc",
            "voice/daria.onnx",
        ),
        VerifiedAsset(
            "$piperVoices/gosia/medium/pl_PL-gosia-medium.onnx.json",
            "1aefb31a9d53ffe44a8163ff73ec833acb7a6253848f6bb0403d8a66f9c7510d",
            "voice/daria.onnx.json",
        ),
        VerifiedAsset(
            "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip",
            "c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d",
            "vosk",
            unzip = true,
        ),
    )

androidComponents {
    onVariants { variant ->
        val suffix = variant.name.replaceFirstChar(Char::uppercase)
        shippedAssets.forEach { asset ->
            val name = asset.url.substringAfterLast('/').replace(Regex("[^A-Za-z0-9]"), "")
            val fetch =
                tasks.register<FetchVerifiedAsset>("fetch${suffix}Asset$name") {
                    url.set(asset.url)
                    sha256.set(asset.sha256)
                    assetPath.set(asset.path)
                    unzip.set(asset.unzip)
                    cacheDirectory.set(File(gradle.gradleUserHomeDir, "caches/darek-assets"))
                }
            variant.sources.assets?.addGeneratedSourceDirectory(fetch, FetchVerifiedAsset::outputDirectory)
        }
    }
    onVariants { variant ->
        // Per variant on purpose: the debug build ships `ui-tooling` and the release build does
        // not, so one shared list would be wrong for whichever variant it was not generated from.
        // The screen names what *this* binary contains, which is what the obligation is about.
        val suffix = variant.name.replaceFirstChar(Char::uppercase)
        val licenseeTask = "licenseeAndroid$suffix"
        val bundleTask =
            tasks.register<BundleLicences>("bundle${suffix}Licences") {
                artifacts.set(layout.buildDirectory.file("reports/licensee/android$suffix/artifacts.json"))
                // The report path is Licensee's convention rather than something it exposes, so the
                // dependency is declared by name. Wrong here and the build fails on a missing input
                // file — loudly, not silently with a stale list.
                dependsOn(licenseeTask)
            }
        variant.sources.assets?.addGeneratedSourceDirectory(bundleTask, BundleLicences::outputDirectory)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // AppCompat is here for exactly one thing: the per-app language backport below Android 13.
    // None of its widgets are used — Compose M3 draws every pixel — but the backport is applied
    // through AppCompatDelegate, which only exists inside an AppCompatActivity, which in turn only
    // starts under an AppCompat-descended theme. That chain is why an in-app language switcher
    // costs a dependency, the activity's base class and the root theme rather than one Settings row.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.datastore.preferences)

    // The tip (ADR-0007). Its transitive telemetry merges INTERNET, which the manifest removes again.
    implementation(libs.billing.ktx)
    // Consumes a cleared tip whenever the app comes to the foreground — see MainApplication.
    implementation(libs.androidx.lifecycle.process)

    // Reads licensee's report for the Licences screen.
    implementation(libs.kotlinx.serialization.json)

    // Darek's voice (ADR-0014) and his ears (ADR-0009).
    implementation(libs.onnxruntime.android)
    implementation(libs.vosk.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
