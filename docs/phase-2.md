# Phase 2 — Darek hears a Command with the screen off and the app closed

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds ADR-0010 and leans on ADR-0009 (the grammar), ADR-0011 (the Gate call and its cancel window),
ADR-0014 (the bundled voice) and ADR-0016 (the Persona). [`CONTEXT.md`](../CONTEXT.md) is owed
*Listening* rewritten for the service and the word *Hej* (section 4).

**What closing this phase means.** Today Darek listens only while Home is on screen, and every dial
ends Listening. After this phase *Słuchaj* starts a `microphone` foreground service: Darek hears a
Command with the screen off, the app swiped away and the phone idle, pauses during the Gate call and
comes back by himself when it ends, and says so in his notification. A device is asked: with the
screen off and the app gone from recents, does *„Darek, otwórz bramę"* still fire?

**Where `PLAN.md`'s paragraph is already wrong.** Two decisions taken while planning (2026-11-10)
change it: the name heard alone no longer gets an answer — *„No?"* and every other answer that is not
a Command firing need *„Hej"* in front of the name (section 4), so the negative reading counts those,
not bare `name heard` lines; and a dial no longer ends Listening (section 3).

---

## 0. What this phase inherits

- **`Listening` already lives in `AppContainer`**, one per process, so the service wraps it rather
  than replacing it. What changes is who holds it alive: Home today, the service from now.
- **Phase 1's cancel window and its guard.** `Recogniser` refuses to dial when recording does not
  start (PR #46); nothing is said when it refuses, which this phase fixes (section 3).
- **The asks are written but unused.** `work/NotificationPermission.kt`, `BatteryExemption.kt` and
  `Autostart.kt` came from the template for this phase (ADR-0010).
- **The asymmetry that shapes the phase:** stock Android's half of ADR-0010 can be read on the
  emulators; Xiaomi's half (MIUI and HyperOS killing the service, autostart, a night's battery)
  cannot, and both phones are out of reach (`CLAUDE.md`, *Environment notes*). The owner's call,
  2026-11-10: build on the emulators' verdict, list the Xiaomi readings as owed, and leave the phase
  unticked until a phone takes them.

## What is in, and what is deliberately not

**In:** the `microphone` service and its notification; *Słuchaj* starting it from Home, the
notification after a reboot and a Quick Settings tile; pausing during the Reply and the call;
*„Hej"* in front of every answer that is not a Command; the updated microphone disclosure; the
`POST_NOTIFICATIONS` ask and the Xiaomi setup hand-offs; the negative reading and the battery reading.

**Not in, and each with a reason rather than a phase:**

- **A Home-only Listening beside the service.** One Listening, one state for the user (the owner,
  2026-11-10). Home keeping the screen on goes with it: nothing needs the screen once the service
  holds the microphone.
- **Starting by itself after boot.** Refused by the platform on Android 14+ (ADR-0010).
- **`READ_PHONE_STATE`** to see the call. The microphone's own silenced signal and the audio mode
  answer the same question with no new permission (section 3).
- **`CALL_PHONE` in the release.** Still phase 3; the Gate call stays debug-only, and so does every
  path in this phase that depends on a dial.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — the service itself: *Słuchaj* starts it, *Stop* from Home or the notification ends it, a plain notification; then the gate reading on four emulators | `feat:` | — |
| **B** | *„Hej"* in front of every answer that is not a Command, and the false-answer reading | `feat:` | A |
| **C** | Pausing during the Reply and the call, the notification's three states, the refused call spoken | `feat:` | A |
| **D** | The start paths and the asks: `POST_NOTIFICATIONS` and refusing to listen without it, the notification after a reboot, the Quick Settings tile, the updated microphone disclosure, the Xiaomi sheet | `feat:` | C |

One PR per checkpoint, each leaving the app working and translated (the owner, 2026-11-10). Each PR
amends its own ADRs and `CONTEXT.md`; the listing, the privacy policy and the Play documents go with
D, which is what a stranger would see. **Nothing after A is built before A's verdict**: the gate is
the service's own first reading, so A is the service and no more. B comes next because it is
independent of the rest and cheap, and in the background every answer it removes is one a TV could
have triggered.

---

## 1. The gate: does the service hear with the screen off and the app gone?

### The question, in the form that can be measured

On `pixel-api36` (and API 26, 30, 34): Listening started from Home, the app swiped from recents, the
screen off, Doze forced with `adb shell dumpsys deviceidle force-idle`. Does *„Darek, otwórz bramę"*,
injected through the emulator's gRPC `injectAudio`, produce `reaction=GATE` in `DarekListen` within
the same second it would with Home on screen?

### The apparatus

The emulator voice driver (`darek-emulator-voice-testing` recipe): one stream per boot, no silence
padding. Readings logged by the existing `DarekListen` lines; no new debug surface.

### The verdicts, decided now rather than argued about later

Pass is `reaction=GATE` logged, 3 of 3 tries, on each of API 26, 30, 34 and 36 (the owner,
2026-11-10).

1. **Passes on all four** → the rest of the phase is built on it.
2. **Fails on some levels only** → diagnose. A bug of ours is fixed and re-read; a platform refusal
   on a level is written into ADR-0010 and that level gets a warning on Home, not a workaround.
3. **Fails on 34 and 36** → stop; ADR-0010 is reopened with the owner before more code.

### The verdict, taken 2026-11-10 and 2026-11-11

**Verdict 1 on API 26, 30 and 37; API 36 cannot be read on its emulator.** No API 34 image is
installed, so `pixel-api37` stands in for it: Android 14's background-start rules hold on every level
from 34. On `urgent-api26`, `small-api30` and `pixel-api37`, each try was a fresh boot, with the task
gone from `dumpsys activity recents` and the process alive, `mWakefulness=Asleep` and
`deviceidle get deep` = `IDLE`. Every try logged `reaction=GATE` and a `cancel window=DIAL`, the dial
showed as an outgoing call in `dumpsys telecom`, and the service stopped after it, as every dial ends
Listening until checkpoint C.

`pixel-api36` passed 1 of 3, and every later try with the app swiped away heard nothing, even with
the screen on and no Doze. With the app on screen it passed. What the failing tries showed is below
Darek and below Android's audio framework. AudioService reports the recording active and
`silenced:false`. AudioFlinger shows Darek's track active, with `Sil n`, and it is the only capture
client. Yet the emulator's sound card logged *„pcm_readi was late delivering frames, inserting
silence"* about 1,800 times in each failing try, against 2 with the app on screen. So the
injected audio never reached the guest. Opening the injection stream before the swipe did not change
it. The same Android rules pass on `pixel-api37`, whose audio HAL is newer. The reading on Android 16
is owed to the owner's phone (API 36, HyperOS), with the Xiaomi readings. The owner took this as
verdict 1 on 2026-11-11, and B is built on it.

**The release waits for D** (the owner, 2026-11-11). Checkpoints A to D merge to `main` one by one,
but release-please's 0.9.0 PR stays unmerged until D is in: A puts `FOREGROUND_SERVICE_MICROPHONE`
in the build, and Play's foreground-service declaration with its video is filed once, for the
finished flow. On Android 13+ the notification is hidden until D asks for `POST_NOTIFICATIONS`. The
0.9.0 notes are rewritten for A to D before that PR merges.

## 2. The service

- **One Listening, through the service** (the owner, 2026-11-10). A foreground service of type
  `microphone` holds `AppContainer.listening` alive; *Słuchaj* on Home starts the service, *Stop* on
  Home, in the notification or on the tile stops it. Leaving Home, locking the phone or swiping the
  app from recents no longer stops Listening. Home only reads the state and stops keeping the screen
  on.
- **The hold after the name** (ADR-0009) was Home-only because Listening was; it now runs wherever
  Listening does. Whether a Command that calls keeps it is still `DOD.md`'s reading, owed before
  phase 3.
- The manifest gains `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`, and
  `aab-permissions.py`'s EXPECTED with them.

## 3. The Reply, the call, and a microphone that cannot hear

- **During the Reply** Darek is not listening to himself: recognition pauses while a line plays.
- **During the Gate call** Listening pauses and resumes by itself when the call ends (the owner,
  2026-11-10). The audio mode is polled every 0.5 s on every API (`MODE_IN_CALL`,
  `MODE_IN_COMMUNICATION`, ringing and the call-screening modes): while it says a call is on, the
  recording is closed, so the microphone is free for the call and nothing in it is recognised, and a
  new one opens when the call ends. The platform's own silencing of capture during a call (API 29+,
  `isClientSilenced`) is not needed for the pause: it reads as zeros, below. A run of exact zeros
  longer than a second, outside a call, reads as *cannot hear* on every API, which also catches
  another app taking the microphone (ADR-0010's last bullet). Ringing pauses only while a ringtone
  plays (`MODE_RINGTONE`): a silent ring leaves `MODE_NORMAL`, so Listening goes on until the call is
  answered (read on the API 36 emulator, 2026-11-12). Sources: Android's *Sharing audio
  input* and AOSP `AudioPolicyService` (`updateUidStates_l`, `setAppState_l`); not yet measured on
  Xiaomi.
- **When the microphone fails after the Reply** the call is refused (phase 1) and Darek now says
  *„Nie udało się zadzwonić."* (`CALL_FAILED`), and the notification switches to *cannot hear* (the
  owner, 2026-11-10). A recording that cannot start or stops giving audio, there or anywhere else,
  is *cannot hear* too, and Listening tries again every 2 s instead of ending; only a recording that
  has started clears it.

## 4. „Hej" in front of every answer that is not a Command

In the background a TV or a friend saying *„Darek"* would make a phone on a shelf answer. So every
spoken answer that is not a Command firing needs *„Hej"* before the Persona's name: *„No?"*, *„nie
rozumiem"*, *„nie mam żadnej fuchy"* and the correction. Commands keep their phrase, *„Darek, otwórz
bramę"*, with no *„Hej"* (the owner, 2026-11-10). *„hej"* joins the grammar; the bundled model knows
it (checked on the host with Vosk 2026-11-10: no *missing in vocabulary* warning, which a made-up
word does raise).

## 5. The notification

One ongoing notification, silent, at the lowest importance that still shows the icon. Title *„Darek
słucha"* / *„Daria słucha"*; the text follows the state; one action, *Stop*; a tap opens Home (the
owner, 2026-11-10).

| State | Text |
| --- | --- |
| Listening | *„Powiedz: Darek, otwórz bramę"* — the enabled Command, under the chosen Persona |
| Paused during a call | *„Pauza, trwa rozmowa"* |
| Cannot hear | *„Nie słyszę, mikrofon zajęty"* |

## 6. The start paths

- **After a reboot** a boot receiver posts a notification whose *Słuchaj* starts Listening, one tap
  and an exempt start (ADR-0010), **only if Listening was on when the phone went down**: a preference
  remembers it, *Stop* clears it, so a user who stopped Darek on purpose is not asked at every boot
  (the owner, 2026-11-10). The receiver declares `RECEIVE_BOOT_COMPLETED`.

- **No notifications, no Listening** (Android 13+). The first start asks `POST_NOTIFICATIONS`;
  refused, or the app's notifications or Listening's channel off later, Darek does not listen and
  Home says why, with a button to the notification settings (the owner, 2026-11-10). A hidden
  notification would leave no *Stop* outside the app and no way back after a reboot. It is checked
  at every start, from any path; turned off while Listening runs, it is found the next time Home
  opens or Listening restarts, and Listening stops.

- **Xiaomi's setup, once.** Right after the first service start, on a phone that has Xiaomi's
  autostart screen, Home shows a sheet with two buttons, autostart and the battery saver's *„Bez
  ograniczeń"*, and *„Gotowe"*; shown once, then a permanent row in Settings that makes no claim
  about the state, since autostart cannot be read back (`PLAN.md` rule 4). On other ROMs only the
  battery button, and only while the exemption reads as missing (the owner, 2026-11-10).

- **The Quick Settings tile** shows Listening and toggles it. Starting goes through a translucent
  activity opened with `startActivityAndCollapse(PendingIntent)`, since a tile is not an exempt start
  on Android 14+; the `Intent` overload only below 34 (ADR-0010). Stopping needs no activity.
- **The microphone disclosure** is updated for the background — Darek now hears with the screen off
  and the app closed, and still keeps nothing — and shown once before the first service start,
  whether or not `RECORD_AUDIO` is already granted: phase 1's users agreed to listening with Home on
  screen only (`PLAN.md`).

---

## Tests

`PLAN.md` rule 3's second clause: each pure function that bounds a safety value gets its test here.

- **Silence detection.** A run of exact zeros longer than its bound reads as *cannot hear*; the
  cancel window treats it as no Listening and refuses the dial. Fails towards dialling on a
  microphone that hears nothing.
- **Pause around the call.** The audio modes that read as a call. Fails towards a recogniser that
  never resumes, or one that resumes mid-call.
- **„Hej" in `reactionTo`.** The name without *„hej"* in front gets no answer; a Command fires with or
  without it. Fails towards a phone on a shelf answering the TV.
- **The after-reboot decision.** Posted only when the remembered state says Listening was on, cleared
  by *Stop*. Fails towards nagging a user who stopped Darek on purpose.

## Documents this phase amends

- **ADR-0010**: the notification's three states, pausing during the call with no new permission,
  the *„Hej"* rule's effect on what the background answers.
- **ADR-0009**: *„hej"* in the grammar, and which answers need it.
- **ADR-0011**: a dial no longer ends Listening; the refused call is spoken.
- **`CONTEXT.md`**: *Listening* for the service; *Hej*.
- **`PLAN.md`**: phase 2's paragraph, the negative reading recounted for *„Hej"*; rule 4's table
  gains the Xiaomi sheet's once-only ask.
- **`DOD.md`**: the hold is no longer Home-only; *Before the next upload* gains the
  foreground-service declaration for `microphone` and its video, and the permissions.
- **With D**: the privacy policy (listening in the background), the listing in both languages,
  `play-app-content.md` and the Data safety re-read, and the screenshots with the notification.

## Known gaps, deliberately

- **Xiaomi's half of ADR-0010 is unread** until a phone is attached: whether MIUI and HyperOS kill
  the service, autostart, a night's battery, and whether their audio policy silences and un-silences
  capture around a call as AOSP does. The phase stays unticked until then.

## Readings

*(Filled in as the phase runs.)*

**False answers** (on the emulators). With *„Hej"* required, talk that names the Persona without it
gets no answer, and near-misses fire nothing. Injected audio: the near-miss script's lines (*Marek…*,
*Jarek…*, *Otwórz bramę*, *„Darek, idziesz?"*, Daria's equivalents) synthesised on the host with the
bundled voices, so nothing leaves the machine, and 2 minutes of Polish speech from a public-domain
recording (LibriVox, *Lalka* vol. 2 ch. 1). Bar, fixed before the reading (the owner, 2026-11-10): zero spoken answers and zero
Commands fired on the script; at most one spoken answer in the 2 minutes. A failure makes the
failing word a Decoy and the reading is taken again. Real voices at a distance stay phase 3's.

**A night's battery** (owed: the test phone). Charged to 100%, Listening on, screen off, 8 hours,
read with `dumpsys batterystats`; a night without Listening is the baseline. Verdicts on the
difference, fixed before the reading (the owner, 2026-11-10):

1. **Up to 5 points** → pass.
2. **5 to 10 points** → pass, and the listing and Help say what a night costs.
3. **Over 10 points** → the phase stays open until the recogniser skips silence (a voice-activity
   gate in front of Vosk), then it is read again.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| 1 | False answers, Darek, before the fixes | `pixel-api37`, one gRPC `injectAudio` stream: the script in both voices, the radio, then *„Hej, Darek!"* twice as a control | **Fail.** *„Hej, Marek!"* in Daria's voice got *„No?"*: a partial flickered through *„hej darek"* on its way to *„hej marek"*, though *marek* is a Decoy. Radio 0; the control answered twice |
| 2 | False answers, both Personas, with *„No?"* after 400 ms of a still hail | the same, Daria's script under Daria | **Fail.** *„Darku, otwórz bramę"* fired the gate under Darek and *„Dario, otwórz bramę"* under Daria; each dial ended Listening, so the rest was unread. On the host *„Dario…"* in Darek's voice decoded as *„daria otwórz bramę"* at 9 of 9 alignments: a lone Decoy word lost to the whole Command phrase |
| 3 | False answers, both Personas, with phrase Decoys too (ADR-0009) | the same | **Pass.** Script: 0 spoken answers, 0 Commands under either Persona. Radio: 0. Control: 2 of 2 under each, *„No?"* 1.6 s (Darek) and 1.9 s (Daria) after the hail began. One run each. *Darku* fired in reading 2 but never on the host, likely the emulator's audio varying (not proven); real voices at a distance stay phase 3's |
| 4 | Checkpoint D's start paths and asks | `pixel-api36`, `urgent-api26`, `pixel-api37`, the debug build: disclosure, microphone and notification asks, the setup sheet once, Settings' *W tle*, *Stop* then reboot, left on then reboot and the notification's *Słuchaj*, the tile both ways, notifications off from the tile, from Home and while listening | **Pass** on all three, 2026-11-12 and 2026-11-13. No crash and no background-start refusal; the tile and the boot notification started the service with Home never on screen. The first build showed Xiaomi's Autostart on every phone (`Intent.resolveActivity` returns an explicit component unchecked) and opened the setup sheet half-expanded with *Gotowe* under the navigation bar at 1080×1920; both fixed and re-read on `urgent-api26` and `pixel-api37`. On `pixel-api36` the notification permission was revoked with `pm revoke`, which kills the process, so Home's own stop was read on the other two |
