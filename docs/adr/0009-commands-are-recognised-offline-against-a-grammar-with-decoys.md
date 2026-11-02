# Commands are recognised offline by Vosk, against a grammar of the Commands plus Decoys

## Context

Darek has to hear one of a handful of fixed Polish phrases — *"Darek, otwórz bramę"* — in a room or a
car, for hours, without the network, and must **not** act on anything else. A false match is worse
than a miss: it opens a gate. Free speech around the phone is noise to be ignored, not input.

Offline Polish recognisers that run continuously on a phone come down to one: **Vosk** (Kaldi), with
its `vosk-model-small-pl-0.22` model (Apache 2.0, 53 MB zipped, 92 MB unpacked). Small Vosk
models accept a **runtime grammar**: a list of phrases that restricts the search.

A host reading on 2026-10-31 (vosk 0.3.75, the model above, 104 clips of synthetic Polish speech from
two voices at two speeds, half with pink noise: 40 Commands, 64 non-Commands including *"Marek, otwórz
bramę"*, *"Jarek, otwórz bramę"*, *"Otwórz bramę"* and *"Darek mówił, że brama jest zepsuta"*):

| Mode | Commands heard | False matches | CPU (real-time factor) |
| --- | --- | --- | --- |
| Grammar = the Commands only | 40/40 | **16/64** | 0.018 |
| Grammar = the Commands + Decoys | 40/40 | 0/64 | 0.017 |
| Free decoding, matched in code | 40/40 | 0/64 | 0.082 |

A grammar of only the Commands has nowhere else to put *"Marek"*, so it returns the Darek Command at
confidence 1.00. That is the failure this ADR exists to prevent.

## Decision

- **Vosk** (`com.alphacephei:vosk-android`, with JNA) recognises 16 kHz mono audio from `AudioRecord`.
  Both libraries' 64-bit `.so` files are 16 KB page-aligned, which Play requires.
- **The model ships inside the app**, fetched at build time and checked against its SHA-256 rather
  than committed, and is unpacked to `noBackupFilesDir` on first use, never into
  Auto Backup: it is re-derivable from the APK and alone exceeds Auto Backup's 25 MB quota. No
  download step, no network ([ADR-0008](0008-darek-connects-only-where-the-user-can-see.md)).
- **The Commands come in fuchy** ([ADR-0015](0015-darek-takes-on-fuchy.md)): Darek's bundled ones
  (*brama*, *garaż*, *furtka*, and *powiedz coś*, which only replies:
  [ADR-0013](0013-a-command-may-only-reply.md)), each with Decoys tuned for it, and the ones the user
  installs, which may bring Decoys of their own. The user never types a phrase into Darek: a phrase
  arrives in a fucha, every word of it is checked against the model's vocabulary when it is
  installed, because Vosk drops grammar words its model does not know, and the name Decoys below
  apply to every Command whichever fucha it came from.
- **The grammar is the Commands, the cancel words (*"stop"*, *"anuluj"*, which carry no name,
  [ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)) and Decoys**: names and
  words close to the Commands' (*marek, jarek, janek, darka, darku, dawid, otwórz, zamknij, brama*)
  and `[unk]`, so near-misses have somewhere to land. The name is the chosen Persona's, and the
  name Decoys, the same under either, include the other Persona's name
  ([ADR-0016](0016-daria-is-a-persona-the-user-switches-to.md) lists them). A Command that cannot
  act (one that calls, with no Number) is left out, and the cancel words are in only while a Command
  that calls is enabled. **Its JSON carries Polish letters as UTF-8**: Vosk does not decode `\u`
  escapes, takes `co\u015b` as an unknown word and drops it, so the Command could never fire (read
  on the host 2026-11-01).
- **A Command fires when its words are adjacent in a single final result's raw tokens**, with
  nothing between them, not even `[unk]`. Anything before or after, Decoys or `[unk]`, is ignored,
  so *"darek otwórz bramę proszę"* and a Command joined to surrounding noise fire. A split Command
  (*"Darek powiedział: otwórz bramę"* → `darek [unk] otwórz bramę`), a partial or reordered one
  (*"darek bramę otwórz"*) and partial results never fire.
- **While a partial result ends on the name, the utterance waits longer before it ends** (the owner's
  ask, 2026-11-02, on Home only, not the probe), so a user can say the name, wait for *„No?"*
  ([ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)) and still say the
  Command in one final result. Rule 2's trailing silence goes from `model.conf`'s 0.5 s to 3.5 s, the
  owner's number. Vosk derives rules 3 and 4 from it, so the hold is 4.0 s or 4.5 s, against the
  model's 0.75 s and 1.0 s, when the decoder is not at a likely end. A partial that stops ending on
  the name, and a final result, put `model.conf`'s rules back. The rule above for what fires is
  unchanged; what it protects is not (*Consequences*).

## Alternatives

**Free decoding with matching in code.** Equally accurate in the reading, and it would understand any
phrasing. It costs ~5× the CPU of a grammar, all day, on a phone in a pocket. Kept as the fallback if
real voices on a real microphone show the grammar is too narrow.

**A separate wake-word engine** (Porcupine, openWakeWord) followed by recognition. Porcupine's custom
words need a vendor account and key; openWakeWord has no Polish models. And "Darek" alone is a common
name: the Command, not the name, has to be what fires.

**Android's on-device `SpeechRecognizer`** (API 31+). Not continuous, no grammar, and whether a Polish
on-device model is present depends on the phone.

**Downloading the model on first run.** A smaller install, but a network permission and a broken first
launch without signal. Rejected with ADR-0008.

## Consequences

- The download is ~53 MB larger, and the unpacked model takes 92 MB more in `noBackupFilesDir`:
  ~145 MB on the phone in all.
- The Apache 2.0 licences of Vosk, its model and JNA's join `assets/licences/`.
- The reading is synthetic speech of isolated phrases. **Phase 1 owes the real one**: real voices,
  the phone's own microphone, at a distance and in a car, and Commands embedded in speech (*"…,
  proszę"*) to check the match rule. Decoys are added from what that reading mishears.
- A new bundled Command is an app release, with its Decoys read like the first one's. A fucha's
  Command is not: its Decoys are its author's. Only an approved fucha's are checked by the owner's
  review; an unapproved one's Decoys and vocabulary meet nothing but Darek's install checks, the
  vocabulary check and ADR-0015's clash rules, which also catch a Decoy or a Command word that
  would take another fucha's Command.
- Recognition pauses while Darek speaks, so he does not answer himself; only *„No?"*, the answer to the name, plays into
  the open microphone (ADR-0011).
- **The hold weakens what adjacency protects.** Without it, the name and 0.5 s of silence ended the
  utterance, so a name and a Command said apart could not share a final result. With it, *"Darek"*, up
  to 3.5 s of quiet and someone else's *"otwórz bramę"* are one final result with adjacent tokens, and
  it fires, with only the Reply and the cancel window (ADR-0011) left in the way. A false match is
  still worse than a miss, and the reading above has no such clip. It stands for now because no
  Command that calls is in the release; the debug build's Gate call has the hold already. **A negative
  reading is owed before a Command that calls can fire in a release, under either Persona**
  ([`DOD.md`](../DOD.md)): the name, a 1 to 3 s gap, the Command in a second voice. Whether a Command
  that calls gets the hold at all is the owner's call on that reading.
- A partial result that passes through the name now gets *„No?"* and can start the hold, though
  it still fires nothing, so talk that only sounds like the name gets a spoken answer. The 0/64 above counted
  final results and does not measure this; phase 2 owes it, before Darek listens in a pocket.
