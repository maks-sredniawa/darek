# Phase 1 — Say *"Hej Darek, powiedz coś"* with the app open and Darek answers out loud

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds [ADR-0009](adr/0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)'s
recogniser, [ADR-0011](adr/0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)'s Reply,
cancel window and call, and [ADR-0014](adr/0014-darek-speaks-with-his-own-bundled-voice.md)'s
bundled Polish voice. It adds [ADR-0013](adr/0013-a-command-may-only-reply.md): a Command that only
replies. It edits ADR-0009 (size, the grammar's encoding), ADR-0011 (which half of its HyperOS
question this phase can answer, when a Reply is finished, and what stopping Listening does to a
pending dial) and ADR-0012 (the Reply's voice is Darek's own) in place. `CONTEXT.md` is owed a change
to *Command*, which no longer always has a Number, to *Reply*, which is spoken in Darek's own voice,
and to *Listening*, which in this phase exists only while Home is on screen.

**`PLAN.md`'s phase 1 and phase 3 lines were wrong, and this PR corrects them.**

- Phase 1 promised the gate call as the phase's result. The owner moved the first milestone on
  2026-10-31: a Command that only answers, *"Hej Darek, powiedz coś"* → *„Co tam, mordo?"*. It can be
  read end to end on the test phone, which has no SIM. The call is still built in this phase, but
  **only in the debug build** (section 4).
- Phase 3 was called *the door*, where closed testing opens. **The door opened with phase 0:**
  Alpha 0.2.0 went to Google's review on 2026-10-31 with the tester-swap group as its testers. So a
  release cut by this phase can reach strangers. That moves the microphone's prominent disclosure out
  of phase 3 and into this phase, and keeps `CALL_PHONE` out of the release until phase 3's Number
  editor exists.

**What closing this phase means.** Today the app opens on a home screen that does nothing. After this
phase, Home has a *Słuchaj* button. While Home is on screen and the button is on, saying *"Hej Darek,
powiedz coś"* makes Darek answer *„Co tam, mordo?"* out loud, in Polish, with no network. In the debug
build, *"Darek, otwórz bramę"* also makes him answer *„Otwieram bramę"*, wait about 2 s for *"anuluj"*,
and then dial a Number set in the debug settings. The difference a device can be asked about: the
release AAB declares `RECORD_AUDIO` and not `CALL_PHONE`. Spoken to from 3 m, the test phone answers.

---

## 0. What this phase inherits

- **The model and the grammar have a host reading** from 2026-10-31 (ADR-0009): synthetic voices,
  40/40 Commands heard, 0/64 near-misses fired with Decoys in the grammar. Real voices on a phone's
  microphone have never been tried. That is this phase's gate.
- **Every word of the Commands is in the model's vocabulary.** Read on the host 2026-11-01 (vosk
  0.3.45, `vosk-model-small-pl-0.22`): *hej, darek, powiedz, coś, otwórz, bramę, garaż, furtkę, stop,
  anuluj, marek, jarek* are all accepted. That holds **only when the grammar's JSON carries Polish
  letters as UTF-8**. Written with `\u` escapes (Python's default `ensure_ascii`), Vosk takes
  `coś` as a literal word and drops it with a warning. *coś, otwórz, bramę, garaż* and *furtkę*
  vanish, and the Commands can never fire. The grammar is written with kotlinx.serialization,
  already in the app for the licences list, which leaves non-ASCII letters as they are;
  `GrammarTest` pins that on the JVM against the same encoder (*Tests*).
- **The model is 53 MB zipped (SHA-256 `c4cd1649…6fce15f0d`) and 92 MB unpacked.** ADR-0009 said
  ~50 MB unpacked, which was wrong; it is corrected. After the first run the phone holds both the copy
  inside the APK and the unpacked one, ~145 MB in all.
- **Darek's voice has a host reading** from 2026-11-01 (ADR-0014): Piper's mc_speech model, driven
  by our own Polish pronunciation rules. The rules agree with eSpeak on 92.4% of the 3,000 most
  frequent Polish words (1.4% phoneme error rate), and the model renders 4.6 s of speech in 0.09 s
  on the PC. It was read as an int8 quantisation at 19 MB; R3 then chose the full model, at 63 MB,
  for its sound, and on the test phone it is the faster of the two as well. The rules' Python
  prototype is committed as `scripts/voice/g2p_reference.py`, the reference the Kotlin port is
  tested against (*Tests*). ONNX Runtime is pinned at 1.28.0, the last
  version without a telemetry provider (ADR-0014).
- **The release is minified** (`isMinifyEnabled = true`). JNA finds Vosk's native methods by
  reflection. Without keep rules for `com.sun.jna.**` and `org.vosk.**`, JNA's native side aborts
  the process (`UnsatisfiedLinkError`, *Can't obtain peer field ID*). A adds those two keeps to
  `proguard-rules.pro`, with the probe's crash on `-PreleaseShapedDebug` as the evidence that file
  asks for. `aab-reflection.py` does not cover it: it checks classes named in the
  manifest, and JNA's are not. So the end-to-end reading is taken on `-PreleaseShapedDebug` (R4).
- **Closed testers exist, or will within days** (above). Anything a `feat:` merge releases may reach
  a stranger's phone.

## What is in, and what is deliberately not

**In:**
- The model, fetched at build time and unpacked on the phone.
- A recogniser over `AudioRecord` with ADR-0009's grammar and match rule.
- The Command catalogue with one Command that only replies (ADR-0013) and one that calls.
- Darek's own voice: ONNX Runtime, the mc_speech model and our Polish pronunciation rules
  (ADR-0014), with a debug-only field that speaks any text.
- The *Słuchaj* button, with the microphone disclosure and the `RECORD_AUDIO` ask on its first tap.
- In the debug build only: a Number for *brama*, `CALL_PHONE`, the cancel window and `placeCall`.
- A debug-only probe screen that shows the recogniser's raw results, which is the gate's apparatus.

**Not in, and each with a reason rather than a phase:**

- **Hearing with the screen off or the app closed.** That is the microphone service, its
  notification and its Play declaration (ADR-0010). It is a different cost from everything here, so
  it is a different phase (`PLAN.md` rule 2).
- **`CALL_PHONE` and the call in the release build.** A user would have no way to type a Number.
  A permission shipped before the feature that uses it is a Play question with no answer. The
  call's code lives in `main/`; the permission and the only way to set a Number live in
  `app/src/debug/`.
- **Garaż and furtka.** They are the same code path as brama with different words. Their Decoys are
  read once brama's are settled. Adding them now triples the reading for no new knowledge.
- **The user's own Reply text.** It needs the same editor as the Numbers, so it is phase 3's. The
  voice already reads any Polish text, so phase 3 adds a field, not a capability.
- **The Commands' stored shape** (the JSON list under one key). Phase 3's editor settles which
  fields a user changes. The debug Number is a debug-only store (section 4), so nothing in `main/`
  commits to a shape early.

**The tempting one: free decoding "just in case".** ADR-0009 keeps free decoding with matching in
code as the fallback. It is one flag away in Vosk, so it looks free to ship both. It costs ~5× the
CPU all day once phase 2 runs it in a pocket. A second mode also doubles every reading. It comes in
only through verdict 4 below, by a new ADR.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate**: the probe, debug-only, and the reading with a real voice on both phones | `chore:` | — |
| **V** | **Darek's voice**, debug-only: ONNX Runtime 1.28.0, the model, the Polish rules, a *say this* field | `chore:` | — |
| **B** | The reply-only Command in the release: *Słuchaj*, disclosure, voice, *powiedz coś* | `feat:` | A, V |
| **C** | The Gate call, debug-only: Number, `CALL_PHONE`, cancel window, `placeCall` | `chore:` | B |
| **D1** | Listing, privacy policy and App content for B's release | `docs:` | B |
| **D2** | Readings on both phones | `docs:` | B, C |

**Why A is a `chore:`.** Everything A adds is under `app/src/debug/` or unused by the release: the
Vosk dependency is `debugImplementation`, and the model is attached to the debug assets. The
exception is the pure matcher in `main/`, which nothing in the release calls yet. A `feat:` would
cut a release with no change a user can see. B moves the dependency and the model to `main`.

**Why V is separate from A, and also a `chore:`.** V touches nothing A does, so the two run in
either order. Like A it is debug-only until B moves it to `main`. It is its own checkpoint because
it has its own risk: the phone's speed and the model's sound (R3). R3 read the int8 quantisation V
first shipped against the full model and chose the full one, at 63 MB, before B builds on it. V's manifest removes
`ai.onnxruntime.TelemetryInitializer` with `tools:node="remove"` beside `INTERNET`'s removal, and
pins `onnxruntime-android` at 1.28.0 (ADR-0014); a bump past it re-reads the AAR's manifest.

**Licences are A's and V's first check, not B's.** Licensee runs per variant inside
`assembleDebug`, so adding Vosk and JNA, or ONNX Runtime, as `debugImplementation` is when an
allowlist failure shows. JNA's POM lists LGPL-2.1-or-later and Apache-2.0; A reads whether licensee
accepts it on the existing `allow("Apache-2.0")` before anything else is built on it. ONNX
Runtime's POM declares MIT, which the allowlist does not have, so V's first build fails until it
adds `allow("MIT")` and `src/main/assets/licences/MIT.txt`, as `app/build.gradle.kts` says.

**Why C is a `chore:` after B.** C's code in `main/` does nothing in a release, which has no Number
and no `CALL_PHONE`. B's release must not wait on it. C comes after B because it reuses B's Reply
and pause-while-speaking path instead of building a second one.

D1 rides with **the release that ships B**, as the `DOD.md` item already says, and does not wait for
C. It is written in the release's notes PR, before release-please's PR merges, not after the upload.
D2 is the readings, once B and C are on the phones.

---

## 1. The gate: does the phone's microphone hear a real voice?

### The question, in the form that can be measured

With the grammar of ADR-0009 (the Commands, the cancel words, the Decoys, `[unk]`), a real voice,
and each phone's own microphone with the app open:

- Does *"Hej Darek, powiedz coś"* fire at least **9 times in 10 at 0.5 m** and **8 in 10 at 3 m**?
- Does *"Darek, otwórz bramę"* do the same?
- Does **nothing** fire for the near-miss script and for 2 minutes of radio or conversation?

### The apparatus

`app/src/debug/`: a **probe** screen, opened from Settings' debug section.
- It starts a recogniser with the grammar and shows each partial and final result's raw tokens.
- It marks each final result with the Command the matcher would fire, if any.
- It logs every final result to logcat with the tag `DarekProbe`, so the reading is collected with
  `adb logcat -s DarekProbe` instead of being transcribed off the screen.
- `RECORD_AUDIO` is declared in the debug manifest and asked for on the probe's start.
- It keeps the screen on while it runs (`keepScreenOn`), so a screen timeout cannot end a reading
  partway. The test phone's `stayon` would hide that; the owner's phone has no such setting.
- The probe has a grammar/free toggle, used **only** under verdict 4.
- R1, R2 and R8 are read with **Reading mode** on: exact matching, the shipping grammar, and no
  Reply spoken.
- It writes no audio anywhere (ADR-0008 holds in the debug build too).

The owner speaks; the session runs adb and reads the log. The script, each line said once unless a
count is given:

| Group | Lines |
| --- | --- |
| Commands | *"Hej Darek, powiedz coś"* ×10 at 0.5 m, ×10 at 3 m; *"Darek, otwórz bramę"* ×10 at 0.5 m, ×10 at 3 m; both ×5 with the radio on |
| Embedded, must fire | *"Darek, otwórz bramę, proszę"*; *"No to Darek, powiedz coś"* |
| Near-misses, must not fire | *"Marek, powiedz coś"*; *"Jarek, otwórz bramę"*; *"Otwórz bramę"*; *"Darek, powiedz mi coś"*; *"Powiedz coś, Darek"*; *"Darek mówił, że brama jest zepsuta"*; *"Darek bramę otwórz"* |
| Noise, must not fire | 2 minutes of radio speech; 2 minutes of ordinary conversation that mentions Darek |

### The verdicts, decided now rather than argued about later

1. **Both Commands meet both thresholds on both phones, and nothing false fires.** → B as planned.
2. **The Commands meet the thresholds, but something false fires.** → Add Decoys from the logged
   tokens of what misfired, re-read only the failing lines and their Commands, then B.
3. **The thresholds hold at 0.5 m but not at 3 m.** → B goes ahead. The distance becomes a known
   gap, written into the listing's *what it cannot do*, and phase 2 re-reads it with the phone where
   it will actually sit.
4. **The 0.5 m threshold fails.** → Repeat the Command lines with the probe's free-decoding toggle.
   - If free decoding meets the thresholds, a new ADR supersedes ADR-0009's grammar for free
     decoding, at its CPU cost, before B.
   - If it does not, the phase stops and the owner hears it plainly: no offline Polish model that
     fits a phone hears this voice. The larger Vosk Polish model is a server model.

### The verdict, taken *(date)*

*(Filled in from R1 and R2.)*

## 2. The model, the recogniser and the match

**The model is fetched at build time, not committed.**
- A Gradle task downloads `vosk-model-small-pl-0.22.zip` from alphacephei.com into Gradle's user
  cache, checks its SHA-256 and fails the build on a mismatch.
- It unzips into a generated assets directory, registered through `androidComponents`. A clean
  build does not fetch again.
- CI caches the zip keyed on the hash. If the host ever disappears, the zip is mirrored as an asset
  of a GitHub release in this repo and the URL changes; the hash does not.
- Committing it would add 53 MB of binary to every clone for good.

**Unpacking.**
- On the first tap of *Słuchaj*, the model is copied out of the APK into
  `noBackupFilesDir/vosk-model-small-pl-0.22.tmp/`, then renamed to the final name.
- A directory with the final name is therefore complete. A half-finished copy is deleted and
  redone, never loaded.
- A new model is a new directory name; the old one is deleted on the next unpack.
- While it runs, the button shows *Przygotowuję…*, which also waits for the voice (section 3). R6
  reads how long the unpack takes on the test phone.

**The recogniser** is one class in `listen/`.
- It runs `AudioRecord` at 16 kHz mono, 16-bit, `VOICE_RECOGNITION` source, on its own thread.
- It feeds a Vosk `Recognizer` built with the grammar and emits partial and final results as a
  `Flow`.
- It has no idea of screens. Phase 2 moves it, unchanged, into the service.
- It stops reading audio while Darek speaks and resumes when the Reply is finished (section 3,
  ADR-0011), so he never hears himself.

**The grammar** is built from the catalogue (section 3) by `listen/Grammar.kt`, which writes it as a
JSON array with kotlinx.serialization: every word of every enabled Command, the cancel words, the
Decoys, and `[unk]`. A Command that cannot act is not enabled, and its phrase is not in the grammar:
in the release, *brama*, which has no Number. The cancel words are in the grammar only when a
Command that calls is enabled.

**The match** is ADR-0009's rule, as one pure function in `listen/CommandMatch.kt`:
- Input: a final result's raw tokens. Output: the Command whose words appear adjacent and in order,
  or none.
- Leading and trailing words are ignored, so *"Hej Darek, powiedz coś"* fires *Darek, powiedz coś*.
  *Hej* is not part of the Command; the owner's greeting works because it comes before it.
- Partial results never fire a Command. They are read only for the cancel words (section 4).

## 3. The catalogue, the Reply and Home

**The catalogue** is a Kotlin enum in `listen/Command.kt`. Words are lower-case, as the model emits
them:

| Command | Words | Reply | Acts |
| --- | --- | --- | --- |
| `SAY_SOMETHING` | *darek powiedz coś* | *„Co tam, mordo?"* | replies only (ADR-0013) |
| `GATE` | *darek otwórz bramę* | *„Otwieram bramę."* | calls its Number (ADR-0011) |

The Reply text is speech, not UI: it lives in the enum, outside `strings.xml` (ADR-0012).

**The voice** (ADR-0014) lives in `speak/`:
- **`PolishPhonemes.kt`**: text to the model's phonemes. It lower-cases, writes numbers as words,
  reads clock times the formal way (*21:30* → *dwudziesta pierwsza trzydzieści*, the owner's choice
  on 2026-11-01) and temperatures with their sign and the right form of *stopień* (*-0,5°C* → *minus
  pół stopnia Celsjusza*), then handles digraphs, soft *i*, nasal vowels by what follows them, voicing assimilation (with
  *w* and *rz* assimilating to what precedes them), final devoicing and penultimate stress. It is a
  port of `scripts/voice/g2p_reference.py`, pure Kotlin, and tested against it.
- **`Voice.kt`**: phonemes into the model through ONNX Runtime, 22.05 kHz 16-bit samples out.
  - The input is Piper's: `^`, then the id of each codepoint (NFD) followed by the pad `_` (id 0),
    then `$`; for mc_speech `[1, 0, p1, 0, p2, 0, …, 2]`. The nasal tilde is a codepoint of its own
    with its own id; a lookup of *ɔ̃* as one unit finds nothing. Without the pads the model speaks
    garbage.
  - Beside it go `input_lengths` and `scales`: `noise_scale`, `length_scale` and `noise_w` from the
    `.onnx.json` (0.667, 1, 0.8).
  - The output is float32, peaking at about 0.3. It is normalised to a peak of 0.95 (the gain capped
    at 8×), clipped to ±1 and scaled to 16-bit; at the model's own level a phone speaker was too
    quiet (owner's phone, 2026-11-01).
  - The model loads straight from the APK's assets into the session, with nothing unpacked. It
    loads when *Słuchaj* is turned on and it is not yet loaded, alongside the recogniser's unpack.
    *Przygotowuję…* covers both, and Listening starts only once the model is unpacked **and** the
    voice is loaded with the fixed Replies rendered. A Command never waits on a load in progress.
- **The model file** is `pl_PL-mc_speech-medium.onnx` (63 MB) and its `.onnx.json` as Piper
  publishes them on Hugging Face. The build fetches and SHA-256-checks them as it does the
  recogniser's model (section 2). V first shipped an int8 quantisation (19 MB, from a
  `voice-pl-mc_speech-int8-1` release of this repo), with a debug toggle to the full model for R3;
  R3 chose the full model, and the quantisation, its script and the toggle are gone.
- **Playback** through `AudioTrack` in static mode. The fixed Replies are rendered once and kept in
  memory, so a Command does not wait on synthesis.
  - The end marker is set at the clip's frame count **minus one**: on some devices a static track's
    head stops on the last frame and a marker at exactly the count never fires. The listener gets
    an explicit `Handler`, not the creating thread's looper.
  - The marker fires when the mixer has taken the last frame, not when the speaker falls silent.
    With output latency, more over Bluetooth, recognition resumes under the Reply's tail. That is
    harmless while no Reply ends in a Command's or a cancel word's sound.
  - Each Reply logs one `DarekVoice` line naming what finished it: the marker, the watchdog or a
    failure. R3 and R4 read it on both phones, because a marker that never fires looks like a pass
    behind the watchdog.

**The Reply is finished** (ADR-0011) on the first of:
- playback reaching its last frame (`AudioTrack`'s marker);
- synthesis or playback failing, or the model having failed to load, as soon as the Reply is shown;
- a watchdog at the clip's length plus 2 s after playback starts (`frames / 22,050 s + 2 s`), so a
  stalled `AudioTrack` cannot leave recognition paused while Home says Darek is listening, and
  phase 3's longer Replies are not cut off.

Recognition resumes then and, for a Command that calls, the cancel window starts then. The Reply is
on screen from the moment the Command fires, so a user with the sound off sees what is about to
happen. This is one pure function, `speak/ReplyEnd.kt`, over playback's events and a clock.

A failed voice dials **in this phase only**, because Listening exists only while Home is on screen
and the shown Reply is seen. With the phone in a pocket it is neither heard nor seen, so whether a
Command that calls may dial on a failed voice there is left open for phase 2 (ADR-0011).

**His name without a Command** (the owner's ask, 2026-11-01): a final result that holds *darek* and
no enabled Command gets *„Stary, nie mam żadnej fuchy."* while no Command the user sets
up is enabled, and one of eight *„nie rozumiem"* answers once one is. The built-in *powiedz coś* (and
the debug build's roast) answer but do not count as set up. A result without *darek* gets nothing, so
talk that never names him is never answered. While a partial result holds *darek*, Home shows *Darek
cię słyszy…* in place of *Darek słucha*. `CommandMatchTest` pins which results answer. In this
phase's release nothing can be set up yet, so his name alone always asks for it until phase 3. A room
that says *„Darek mówił, że…"* hears that answer; phase 2 re-reads it before he listens in a pocket.
In the debug build, `-PreleaseShapedDebug` included, Home's grammar also carries the roast's words,
so a Home reading that must match the release (R4, and phase 2's re-read of *„Darek mówił…"*) is
taken on a release build or read with that in mind; R1, R2 and R8 use the probe's Reading mode,
whose grammar is the shipping one.

**Home** loses its placeholder line and gains, in the middle:
- **The *Słuchaj* button.** Off, it says *Słuchaj*; on, *Darek słucha* with *Zatrzymaj*. It turns
  itself off when Home leaves the screen (`ON_STOP`), because in this phase the microphone belongs
  to the visible screen. The button is the switch phase 2 makes outlive the screen. While it is on,
  Home keeps the screen on (`keepScreenOn`); otherwise the screen timeout would stop Home and end
  Listening without a word.
- **The last Reply, as a line of text under the button** (*„Co tam, mordo?"*). This confirms the
  Command for a user with the sound off, and is what a reading reads off the screen.

**The microphone ask** (`PLAN.md` rule 4: the ask waits for the feature).
- On the first tap of *Słuchaj*, a sheet explains, before the system dialog, that Darek listens only
  while this button is on. It also says the sound is processed on the phone and never recorded or
  sent. *Dalej* opens the system's `RECORD_AUDIO` dialog.
- **On a denial**, the button stays off and Home shows a line that Darek cannot hear without the
  microphone, with *Zezwól*.
- **On a permanent denial**, *Zezwól* becomes *Otwórz ustawienia* and opens the app's details
  screen. A denial is permanent only when `shouldShowRequestPermissionRationale` was true before the
  request and false after it. False after a *first* request also means the dialog was dismissed with
  Back, and the system would still ask. A later launch cannot see the before-value, so a permanent
  denial is kept in a preference (*Adding a preference*, `data/AppPreferences.kt`) and cleared once
  the grant is found. The app re-reads the grant on every `ON_START`.

The copy, in English, translated once at the merge:

- `home_listen` *Listen* · `home_listening` *Darek is listening* · `home_hearing` *Darek hears you…* · `home_stop` *Stop* ·
  `home_preparing` *Getting ready…*
- `home_listen_failed` *Darek couldn't start listening. Try again.*, under the button after a start
  that failed, until the next one
- `mic_disclosure_title` *Darek needs the microphone*
- `mic_disclosure_body` *Darek listens only while the Listen button is on. What he hears is processed
  on this phone and is never recorded or sent anywhere.*
- `mic_disclosure_continue` *Continue*
- `mic_denied` *Darek can't hear you without the microphone.* · `mic_allow` *Allow* ·
  `mic_open_settings` *Open settings*

## 4. The Gate call, in the debug build

- **The Number** comes through a seam, `listen/GateNumber.kt`, one file per variant (house rules).
  - `src/debug/` reads a debug-only `SharedPreferences` file, written by a field in Settings' debug
    section.
  - `src/release/` returns `null`.
  - With `null`, `GATE` is not enabled and not in the grammar.
- **`CALL_PHONE`** is declared in `src/debug/AndroidManifest.xml` only. It is asked for when the
  debug Number is saved, because that is when the feature is switched on.
- **The sequence** (ADR-0011):
  1. Final result matches `GATE`.
  2. Recognition pauses and Darek speaks *„Otwieram bramę."*, shown on Home at the same time.
  3. When the Reply is finished (section 3), recognition resumes and the **cancel window** of 2 s
     runs. Cancel words are heard only from here: an *"anuluj"* said over the Reply is not heard
     and has to be said again (ADR-0011). The 2 s run until a partial *arrives*, so Vosk's partial
     latency comes out of them; R7 reads whether a prompt *"anuluj"* still lands in time.
  4. If a partial result containing *stop* or *anuluj* arrives, Darek cancels and says *„Anulowane."*
  5. If Listening stops before the dial, the dial is cancelled: *Zatrzymaj*, or Home leaving the
     screen (`ON_STOP`: the Home key, the screen locking, an incoming call). Nobody could cancel it
     by voice any more.
  6. If not, `TelecomManager.placeCall(tel:<number>)`. Its in-call screen stops Home, so in this
     phase Listening switches off with every dial and *Słuchaj* is off when the user comes back.
     ADR-0011's wait for the audio mode to return is phase 2's, where Listening outlives the screen.
  7. If the call fails, Darek says *„Nie udało się zadzwonić."*.
- **The cancel window is a pure state machine**, `listen/CancelWindow.kt`, driven by events (Reply
  finished, partial result, Listening stopped, tick) and a clock. It answers one question: dial or
  not. It is the safety value of this phase: getting it wrong opens a gate the user said no to.

---

## Tests

`PLAN.md` rule 3's second clause. Each fails in the direction the app exists to prevent:

- **`CommandMatchTest`**: a table written by hand from ADR-0009's rule, because the host reading's
  token strings were not kept. Section 1's script for both Commands, as the model would emit it:
  each Command, the embedded lines, every near-miss (*darek powiedz mi coś*, *powiedz coś darek*,
  *marek powiedz coś*, *jarek otwórz bramę*, …), an `[unk]` between words, and leading and trailing
  words. R1's logged token strings join it once taken. It fails toward a **false fire**.
- **`CancelWindowTest`**:
  - A partial result with *anuluj* 1.9 s after the Reply is finished cancels.
  - One at 2.1 s is too late and the dial stands.
  - No dial happens before the Reply is finished, however much time passes.
  - Listening stopping before the Reply is finished, or in the window, means no dial, whatever
    follows.

  It fails toward a **dial the user cancelled**.
- **`ReplyEndTest`**: playback's last frame, a synthesis or playback error, a model that failed to
  load, and the clip's length plus 2 s with no marker each finish the Reply. A 12 s clip is not
  finished at 10 s. It fails toward **a Darek that shows he is listening and hears nothing**, and
  toward one that listens while he is still talking.
- **`GrammarTest`**, on the JVM against `listen/Grammar.kt` and the kotlinx.serialization encoder it
  uses:
  - With the call off, `coś` is written as UTF-8, never `\u015b`, and there is no `GATE` phrase, no
    `bramę` and no cancel word.
  - With the call on, *darek otwórz bramę* is a phrase, `bramę` is UTF-8, and the cancel words are
    in.

  It fails toward **Commands that can never fire**, silently.
- **`PolishPhonemesTest`**: the 3,000 most frequent Polish words and section 3's Replies, each with
  the phonemes of `scripts/voice/g2p_reference.py`, and the Replies also with their model ids
  (`g2p_reference.py --ids`), as a fixture in `app/src/test/resources/`. The ids catch a missing pad
  or a dropped tilde, which a phoneme check passes. eSpeak's output is not in it.
  - The fixture is generated once from the reference, and from then on the Kotlin rules are the
    source and the fixture is edited with them. V commits it.
  - The words are the first 3,000 of hermitdave/FrequencyWords' `content/2018/pl/pl_50k.txt`
    (OpenSubtitles 2018, commit `f8a65e6`), whose content is **CC BY-SA 4.0**. The fixture derives
    from it, so it carries that licence, with the attribution beside it and in `LICENSE`'s paragraph
    for data under its own licence. It is a test resource and never ships in the app.

  Not a safety value, but the only check that a port of ~200 lines of rules says what was heard on
  the host. It fails toward **a Darek who mispronounces his own Replies**.

## Documents this phase amends

- **New [ADR-0013](adr/0013-a-command-may-only-reply.md)**, and **ADR-0009** (92 MB unpacked;
  UTF-8 grammar; Commands that only reply), **ADR-0011** (it applies to Commands that call; the
  HyperOS background question moves to phase 2; when a Reply is finished, and a shown Reply without
  a voice; stopping Listening cancels a pending dial; cancel words only after the Reply) and
  **ADR-0012** (the Reply's voice is Darek's own) edited in place. In this PR.
- **New [ADR-0014](adr/0014-darek-speaks-with-his-own-bundled-voice.md)**, and **ADR-0008** (the
  Reply never leaves the app; ONNX Runtime's telemetry) and **ADR-0011** (the watchdog follows the
  clip's length) edited in place. In the own-voice PR, with `scripts/voice/`.
- **`CONTEXT.md`**: *Command*, *Reply* and *Listening*. In this PR.
- **`PLAN.md`**: phase 1's and phase 3's lines, and the asks table rule 4 asks for. In this PR.
- **In the release that ships B** (`DOD.md`, *Before the next upload*):
  - The listing's test-build paragraph, in both languages: *"This test build answers one Command
    with the app open, „Hej Darek, powiedz coś". Listening with the screen off and the call arrive in
    the next test builds."* The screenshots are re-shot with the *Słuchaj* button.
  - The privacy policy's microphone bullet. *"While it is on, a notification says that Darek is
    listening"* becomes *"the app's screen, or later its notification, says…"*. Its
    *Text-to-speech* paragraph is replaced: Darek speaks with his own voice inside the app, and no
    other app is given the Reply.
  - The listing's *What it cannot do* paragraph on the phone's text-to-speech engine, in both
    languages (`store-listing.md`): he speaks with his own voice, on every phone.
  - `play-app-content.md`'s point 4, *The call and the voice are other apps' work*: the voice is now
    Darek's own, and only the call is another app's.
  - `play-app-content.md`: `RECORD_AUDIO` gets its row and leaves the *not in the artifact yet*
    list, and the Data safety answer is re-read (still *no data collected*: audio is processed on
    the phone). `CALL_PHONE` stays on that list.
  - `aab-permissions.py`: `RECORD_AUDIO` into EXPECTED. The `android.hardware.microphone` feature it
    implies, `required`, is accepted: a phone without a microphone cannot use Darek.
  - Licences: the Vosk API and JNA reach the Licences screen through licensee (Apache 2.0, JNA
    dual-licensed and taken as Apache), already checked in A. The model is a zip fetched by a Gradle
    task, not a Maven artifact, so licensee never sees it: B gives it its own entry in `Licences.kt`
    (source URL, Apache-2.0, SHA-256), a small code change. ONNX Runtime (MIT) arrives through
    licensee once V has allowed MIT and added its text. The voice gets its own entry the same way:
    Piper `pl_PL-mc_speech`, MIT with its notice (*Copyright (c) 2022 Michael Hansen*), MC Speech
    recordings CC0, and the *lessac* base's research-only licence named as ADR-0014 states it.

## Known gaps, deliberately

- **Darek hears only while Home is on screen.** Phase 2's service is the fix, and costs a
  notification, a Play declaration and a battery reading.
- **The disclosure covers only listening with Home on screen.** Phase 2's service listens with the
  screen off, and a user who granted `RECORD_AUDIO` here skips the ask there. So phase 2 shows an
  updated disclosure once before the first service start, whether or not the permission is granted
  (`PLAN.md`).
- **The call exists only in the debug build** until phase 3 lets a user type a Number.
- ***Słuchaj* is off after every dial.** The in-call screen stops Home. Phase 2's service outlives
  it.
- **An *"anuluj"* said over the Reply is not heard.** Recognition is paused while Darek speaks, so
  the user cancels after it (ADR-0011).
- **~145 MB on the phone after the first *Słuchaj*.** It is the APK's copy plus the unpacked model.
  Removing the APK's copy means a download (ADR-0008 forbids it) or an install-time asset pack,
  which is still unpacked by us. Not worth it for one model.
- **A Command that calls, on a failed voice, in phase 2** is not decided here: this phase dials on
  a shown Reply because Home is on screen. ADR-0011 leaves the pocket case to phase 2.
- **The voice's licence has a grey area** (ADR-0014): the *lessac* base. It is accepted and named
  on the Licences screen. A voice trained from scratch on the CC0 recordings would swap a file.
- **HyperOS and `placeCall` from the background** is unanswerable here: phase 1 dials only with the
  app open. ADR-0011 now gives it to phase 2.
- **Dual-SIM phones set to *ask every time*** may show a SIM chooser on `placeCall`. R7 reads whether
  the owner's phone does; choosing a SIM per Command waits for a user who needs it (ADR-0011).

## Readings

`PLAN.md` rule 3: filled in as the phase runs, from the device, with the command that read it.

**Device state:**
- **Test phone** `mnbm7hw8ivdqzx9l`: woken and unlocked before each reading
  (`svc power stayon usb`, `input keyevent KEYCODE_WAKEUP`, `wm dismiss-keyguard`).
  `persist.sys.miui_optimization` must read `false` before any install.
- **Owner's phone** `132bc856`: announced in chat before a reading takes its screen. R7 needs the
  owner's go-ahead in chat each time, because it opens the real gate.
- **Voices:** R1, R2 and R8 need the owner's voice. The session runs adb and reads the log; it
  cannot speak.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate script (section 1) on the test phone, debug build, probe | `adb logcat -s DarekProbe` while the owner reads the script | |
| R2 | The same on the owner's phone | as R1, `-s 132bc856` | |
| R3 | Darek's voice on each phone: model load time, synthesis time for each fixed Reply, what finished each Reply, and the owner's ear on the voice | the `DarekVoice` log lines from V's *say this* field | **Test phone, int8, 2026-11-01:** load 3.5–3.7 s cold; *„Co tam, mordo?"* 1.3 s of speech in 0.6 s, longer sentences at ~0.45× real time (5.5 s in 2.5 s); every Reply ended on the marker. The owner heard it, and asked for formal clock times, now in. **Owner's phone, int8, 2026-11-01:** load 1.9 s; ~0.25× real time (8.5 s of speech in 2.0 s); all four samples ended on the marker. **Owner's phone, int8 against full, 2026-11-01:** the owner heard "a big difference" and chose the full model. **Test phone, full model, 2026-11-01:** load 2.4 s cold; *„Co tam, mordo?"* 1.2 s of speech in 0.27 s, longer sentences at ~0.18–0.2× real time (8.6 s in 1.6 s); all seven samples ended on the marker. The full model is faster than int8 on this phone as well as better. **Done:** the int8-against-full comparison is settled, and the full model ships (ADR-0014) |
| R4 | End to end on `-PreleaseShapedDebug`: *Słuchaj* → *"Hej Darek, powiedz coś"* → *„Co tam, mordo?"* heard and shown, and finished by the marker, on both phones | `./gradlew installDebug -PreleaseShapedDebug`, then the screen, `screencap` and `adb logcat -s DarekVoice` | |
| R5 | Denial: deny once → *Zezwól*; deny again → *Otwórz ustawienia* opens the app's details screen; a first dialog dismissed with Back → still *Zezwól* | *Deny* tapped in the real system dialog, then the screen; `adb uninstall` and reinstall between runs (`pm revoke` sets neither the user-set nor the user-fixed flag, so it reproduces no denial) | |
| R6 | First unpack time on the test phone, and that a killed half-unpack is redone | `DarekModel` log line; `adb shell am force-stop` mid-unpack, relaunch, `run-as … ls no_backup` | **Probe, test phone, `-PreleaseShapedDebug`, 2026-11-01:** unpack 755 ms, model load 893 ms (`DarekProbe`'s `model` line, after `pm clear`). Owed: the same from *Słuchaj*, and the killed half-unpack |
| R7 | The Gate call on the owner's phone, with the owner's go-ahead: Reply, ~2 s, dial; then once with *"anuluj"* said as soon as the Reply ends, no dial; then once with the Home key in the window, no dial | the screen, `adb shell dumpsys telecom` call list | |
| R8 | The owner's phone in a parked car, engine running, Commands at arm's length | `adb logcat -s DarekProbe` | |
| R9 | The release AAB declares `RECORD_AUDIO` and not `CALL_PHONE`; the debug APK declares both | `python3 scripts/aab-permissions.py` on `bundleRelease`; `aapt2 dump permissions` on the debug APK | |
| R10 | Download and installed size for arm64-v8a before B and with it: the voice's model and ONNX Runtime 1.28.0's 28.6 MB `libonnxruntime.so`, stored uncompressed (ADR-0014) | `bundletool build-apks` on `bundleRelease`, then `bundletool get-size total --dimensions=ABI` | |
