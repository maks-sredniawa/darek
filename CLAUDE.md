# Darek

A Polish voice assistant for Android. It listens in the background for a short, fixed set of
Commands — *"Darek, otwórz bramę"* — answers out loud, and calls the number the user gave it, which is
how a GSM gate opener is driven. Polish is the language users speak to it and hear from it. The
constraint that shapes everything: **recognition, speech and the call all happen on the phone, and
audio never leaves it; of what Darek hears, only a slot's value a fucha showed before install is
ever sent (ADR-0008).**

- **Vocabulary:** [`CONTEXT.md`](CONTEXT.md) — use these terms in code and UI.
- **Decisions and why:** [`docs/adr/`](docs/adr/) — read before changing anything they cover.
- **What's still open:** [`docs/DOD.md`](docs/DOD.md) — the live checklist. **Read this one first.**
- **The arc, in phases:** [`docs/PLAN.md`](docs/PLAN.md) — sequence only; a phase being built gets
  its own `docs/phase-N.md`. Read the phase you're in, not the file.
- **How to work here:** invoke the `lean-loop` skill at the start of every session and keep it
  active. This repo is written before it is built — plans, ADRs and phase documents are long on
  purpose — so the waste is re-reading what is already in the transcript and testing code whose
  shape has not settled.
- **Commits & releasing:** [`docs/RELEASING.md`](docs/RELEASING.md). Commit subjects **must** be
  [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `feat!:`, `docs:`, …)
  — a `commit-msg` hook rejects anything else, and release-please derives the version and
  `CHANGELOG.md` from them. `versionName` and `versionCode` are automated; never hand-edit either.

Don't restate ADR reasoning here; link to it. This file is loaded every session and must stay short.

## Working with the person who owns this repo

<Their background, and what to explain rather than assume. For example: "Fluent in
JavaScript/TypeScript, new to Kotlin. Comment code where a Kotlin/Compose idiom has no direct JS
analogue — `Flow` vs promises/observables, `suspend` vs `async`, `remember`/`derivedStateOf` vs
`useMemo`, data class `copy()` vs object spread, sealed classes vs discriminated unions. Explain the
*why*, don't restate the line. Prefer explicit and readable over clever.

The Android **platform** is the other half of the gap and has no JS analogue at all: the manifest as
a declaration the OS reads before any code runs, permission models that differ per API level, process
death, and vendor ROMs overriding documented behaviour. Say when a constraint comes from the platform
rather than from this codebase — that is the difference between "we chose this" and "Android will not
let us do otherwise".">

## Stack

| Choice | Note |
| --- | --- |
| Kotlin, Jetpack Compose, Material 3 | Android only, `minSdk` 26 |
| **Navigation 3** (`androidx.navigation3`) | Replaces Navigation Compose 2.x — don't reintroduce the old one |
| Manual DI via `AppContainer`, **not Hilt** | Constructor injection is clearer at this size and easy to migrate later |
| Play Billing (`tip/`), for the tip only | Unlocks nothing; adds `BILLING`. Its `datatransport` has its upload backend removed — ADR-0007, ADR-0008 |
| `INTERNET`, every request through `net/HttpClient` | HTTPS only, no redirects, maksforge.com's fuchy page and a fucha's declared addresses only — ADR-0008 |
| On-device storage only | No backend. Platform Auto Backup, no custom agent — ADR-0005 |

## House rules

- **A branch merges with every shipped language complete.** Adding an English string does *not*
  redden your build — completeness is a merge gate (`scripts/translation-gate.py`), not a test, so
  copy is translated **once** rather than against draft wording and again after review. Everything
  else about a translation — format arguments, plural categories per CLDR, orphans,
  `translatable="false"` — is `TranslationTest` and holds continuously. Translate from **English
  only**, per [`docs/translator-brief.md`](docs/translator-brief.md).
- **Enums, not loose strings**, for closed vocabularies. Store enums by **name, never ordinal**, so
  adding a value can't rewrite history.
- **A new preference follows *Adding a preference* in `data/AppPreferences.kt`.** A list the user
  makes is JSON under one key, not a table — [`docs/optional-modules.md`](docs/optional-modules.md),
  *How to decide*.
- One `ViewModel` per screen, UI state as a single immutable data class.
- **Developer-only surface lives in `app/src/debug/`, never in `main/` behind `BuildConfig.DEBUG`.**
  With `isMinifyEnabled = false` a statically-false branch is still compiled into the release AAB —
  a hide, not a strip — and its strings are still inside the translation gate. Each seam is one
  file per variant with a no-op in `src/release/`: `ui/settings/DebugSettings.kt`.
- **Colours come from `MaterialTheme`, never literals.** The palette is generated from four seeds by
  `scripts/gen_scheme.py`; edit the seeds and regenerate, never a single role.
- **Spacing comes from `theme/Spacing.kt`.** Six steps on a 4dp grid; no screen invents a seventh.

## Commands

```bash
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on the connected phone
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests — needs a device
./gradlew lint
./gradlew spotlessApply          # format (the CI gate is spotlessCheck)
./gradlew assembleDebug -PreleaseShapedDebug   # debug id, R8 on — find minification bugs early

python3 scripts/project.py                      # what the toolchain thinks this app is called
python3 scripts/translation-gate.py --report    # what translations does this branch still owe?
python3 scripts/translation-gate.py origin/main # the gate itself — would this merge?
python3 scripts/schema-gate.py origin/main
python3 scripts/notes-gate.py
adb devices                                     # confirm the phone is attached
```

## Layout

```
app/src/main/java/<namespace>/
  MainActivity.kt, MainApplication.kt, AppContainer.kt, Navigation.kt, NavigationKeys.kt
               the app shell and Nav3 wiring stay at the package root, not under ui/ — they describe
               how the app hangs together rather than any one screen
  data/        the preferences (DataStore)
  theme/       generated palette, type scale, spacing, the night-mode window half
  net/         the one HTTP client and its address allowlist (ADR-0008)
  tip/         the tip through Play Billing, held by AppContainer so a purchase finishes after its
               screen is gone, and the rule for its one ask on the home screen (ADR-0007)
  ui/          Compose screens + ViewModels, one package per area
  work/        the notification ask and the OEM background hand-offs (autostart, battery), kept
               for phase 2's Listening service (ADR-0010)

app/src/debug/    the developer-only build: Settings' debug section and the debug app name
app/src/release/  only the no-op half of each seam, so main/ can call it unconditionally
scripts/          the Python toolchain. project.py is the one place the app's identity lives
art/             mark.py is the identity; both generators derive from it
```

## Versions

Pinned in `gradle/libs.versions.toml`: **AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, Compose BOM
2026.03.01**. `compileSdk`/`targetSdk` 36, `minSdk` 26, JDK 21 toolchain.

`compileSdk` stays at **36**: it is the combination AGP 9.0.1 validates against. Bump deliberately,
with a build to prove it.

## Environment notes

- **For now, test on the emulators** (the `emulator-test` skill). The physical phones below are not
  easy to reach, so expect `adb devices` to list neither and don't wait on one. Only what an emulator
  cannot show (a real microphone, a real call, a Xiaomi ROM (MIUI, HyperOS)) needs a phone, and that
  waits for the owner.
- **Two phones, so name one.** Gradle and adb both reach every attached device; set
  `ANDROID_SERIAL` (both honour it) or pass `adb -s <serial>`.
  - **The test phone**, `mnbm7hw8ivdqzx9l`: a wiped, debloated Redmi Note 8 Pro, Android 11 (API
    30), MIUI 12.5, Polish, in airplane mode with no SIM. Location stays on: turning
    it off raises a Play services popup. It has **no screen lock** since 2026-10-22,
    but MIUI still shows a swipe keyguard, and `screencap` captures that instead of the app;
    `svc power stayon usb` keeps it awake on USB, `adb shell input keyevent KEYCODE_WAKEUP` wakes it
    and `adb shell wm dismiss-keyguard` clears the keyguard. Drive it at will.
    `connectedAndroidTest` runs on it with no prompt.
    **It has no SIM, so it cannot place the gate call**: the voice-to-reply half of Darek can be read
    on it, the dial needs the owner's phone.
    **While it is attached, this PC's internet runs through it**: its USB tether outranks the PC's Ethernet (route
    metric 100 against 101), so its Wi-Fi stays on; switching it off takes the PC offline.
  - **The owner's phone**, `132bc856`: a Redmi Note 14 Pro 5G, API 36, HyperOS. Drive it without
    asking whenever adb reaches it (the owner's standing permission since 2026-10-20), but it is in
    use: say so in chat before a reading takes its screen, and restore any toggle afterwards.
    **That permission does not cover dialling**: the gate call really opens the gate on the owner's
    SIM, so any reading that can reach it needs the owner's go-ahead in chat, every time.
- **MIUI optimisation is off on the test phone**, and that is what makes it tap-free. With it on,
  every adb install fails `INSTALL_FAILED_USER_RESTRICTED`, and the "Install via USB" fix wants a Mi
  account and a SIM. An update can turn it back on, so check before installing:
  `adb shell getprop persist.sys.miui_optimization` reads `false`. The toggle is at the bottom of
  Developer options, revealed by tapping "Reset to default values" a few times.
- **On the owner's phone (HyperOS)**, split-APK installs (`connectedAndroidTest`) prompt on the
  phone and a missed prompt fails the run with `INSTALL_FAILED_USER_RESTRICTED`; the workaround is
  to install both APKs plain and run the instrumentation directly:
  ```bash
  adb install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w <applicationId>.debug.test/androidx.test.runner.AndroidJUnitRunner
  ```
  **A brand-new `applicationId` is a different failure with the same error string**: HyperOS refuses
  the *first* install of a package outright, with no dialog to miss. Either enable USB installation
  in developer options, or `adb push` the APK to `/sdcard/Download/` and tap it in the file manager.
- **Xiaomi kills background work aggressively.** Anything that must run without the app on screen
  may need battery-optimisation exemption and **autostart**, and autostart is the load-bearing one:
  without it the ROM does not start the process for a broadcast at all. It has no appop, it is a
  Settings toggle, and the grant **lapses on its own** — re-read it before any run that depends on
  it. `scripts/device-gate.py` reads and sets it.
- **A device reading can look identical to a pass when it is nothing at all.** A database pulled
  without its `-wal` sidecar is **stale** — the app's most recent writes sit in the WAL, so a deleted
  row still reads as present. Pull both and `PRAGMA wal_checkpoint(TRUNCATE)` on the host.
