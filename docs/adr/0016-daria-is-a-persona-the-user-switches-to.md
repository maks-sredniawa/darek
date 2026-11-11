# Daria is a Persona the user switches to: her name, her voice, his lines in the feminine

## Context

The owner wants a choice of a man's or a woman's voice. On 2026-11-01 he chose **Daria**: switched
on in Settings, she answers to *„Hej Daria"* and speaks as a woman. Darek stays the default on a
fresh install, and the app, its icon and its listing keep the name Darek.

A Persona is more than a voice. The name is the first word of every Command, the word the name
Decoys guard ([ADR-0009](0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)),
and the word whose presence decides between „nie rozumiem" and silence. Polish verbs agree with the
speaker: *nie załapałem* is Darek, *nie załapałam* is Daria. They agree with the name in the UI
too, and the name declines: *„Darek nie zaczął słuchać"*, *„Daria nie zaczęła słuchać"*.

Read on the host on 2026-11-01:
- Vosk's `vosk-model-small-pl-0.22` knows *daria*, *darii*, *darię* and *dario*, and the near-misses
  *maria* and *marii* (read from the symbol table in `graph/Gr.fst`, where *darek*, *marek* and
  *powiedz* were found and a nonsense word was not).
- Piper's `pl_PL-gosia-medium` is 63 MB, 22.05 kHz, with the same inference settings as Darek's
  mc_speech. Its phoneme map is mc_speech's less five symbols our rules never produce, so
  [ADR-0014](0014-darek-speaks-with-his-own-bundled-voice.md)'s pronunciation rules drive it as they
  are.

## Decision

- **A Persona is Darek or Daria**, chosen in Settings, Darek by default. It sets the name, the voice
  and the grammatical gender. Personas are built into the app, never fuchy
  ([ADR-0015](0015-darek-takes-on-fuchy.md)).
- **Only the chosen Persona's name works.** Every Command is the Persona's name followed by its
  phrase, so a Command is written once, without the name, and works for both. The cancel words
  carry no name ([ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)): a
  bare *stop* or *anuluj* cancels whoever answers, and no hint quotes a name with them.
- **The name Decoys are the same under either Persona**, beside ADR-0009's Command-word Decoys
  (*otwórz, zamknij, brama*): *marek, jarek, janek, dawid, maria, marii*, both names' other forms
  *darka, darku, darii, darię, dario*, and the other Persona's name, *daria* with Darek chosen and
  *darek* with Daria chosen. The vocatives *darku* and *dario* are Decoys like any other form: a
  Command starts with the name as it is written, and *„Dario, otwórz bramę"* gets silence, as
  *„Darku, otwórz bramę"* does today.
- **The other Persona's name gets a correction.** A final result with no Command in it is answered
  in this order: the chosen Persona hailed (*„hej"* right before the name, ADR-0009) gets „nie
  rozumiem", or the not-configured line while no Command that calls is enabled; failing that, the
  other Persona hailed gets the correction in their place, whatever is enabled; failing both,
  silence. Daria says *„Nie jestem Darkiem,
  mówisz z Darią."*, Darek says *„Daria? Tu Darek, stary."*.
- **Daria's voice is `pl_PL-gosia-medium` as Piper publishes it**, bundled in the app at full
  quality and fetched at build time from the same pinned Hugging Face commit as Darek's, SHA-256
  checked. Only the chosen Persona's voice is loaded. At her config's settings (noise 0.667,
  timing noise 0.8) she lisps; she speaks at 0.4 and 0.5, the best of the settings the owner
  compared by ear on 2026-11-01 (`docs/phase-D.md`).
- **Switching the Persona while Listening restarts it.** Stopping cancels a pending call, as any
  stop does (ADR-0011); the old voice is released, and the new start builds the grammar for the new
  name and loads the new voice. Lines rendered ahead are kept per Persona, so a line spelled the
  same in both genders is never played in the other voice.
- **Her lines are his in the feminine.** Every line a Persona speaks has both forms: the Replies,
  the not-understood and not-configured lines and the corrections, and the text shown when the voice
  fails is the chosen Persona's form. A line with no gendered word, like *„Otwieram bramę."* or
  *„Anulowane."*, is written once. A fucha's Reply may carry a feminine form; without one, Daria
  speaks the masculine one.
- **The UI names the chosen Persona** wherever it says who is listening, who hears or what to say:
  *„Daria słucha"*, the hints, the notification, with verbs agreeing with her. The app's name, icon,
  listing and About screen stay Darek, and so do the lines about the app rather than who answers:
  the microphone disclosure, which is Play's prominent disclosure for the app, and *„Wymaga nowszej
  wersji Darka"*.
- **Debug-only extras follow the Persona too.** The debug roast puts the chosen Persona's name in
  front of its phrases and its lines follow the rule above, and the hears-you state (`hearingName`)
  looks for the chosen name, so no `darek` is left behind in either build.

## Alternatives

**Both names answer at once, each in its own voice.** No switch, but twice the phrases that can fire
a call, and Darek's phrases would still place a call for a user who chose Daria.

**Daria with her own character and lines.** The owner may grow her some later. Writing every line
twice, in the app and in every fucha, is the cost of starting there.

**A Persona as a fucha.** A Persona reaches every Command, every Decoy and every line's gender; it
is who answers, not one job among others.

**Daria's voice quantised to int8, or downloaded on demand through a Play asset pack.** The owner
heard int8 as clearly worse for Darek (ADR-0014) and finds a bigger download no problem. An asset
pack adds a download state, an offline failure and a `bundletool` test path for one file.

**Leaving the UI as Darek.** It would tell a Daria user the wrong name to say.

**One UI string with the name as a parameter.** Polish declines the name and agrees the verb with
it, so a `%s` cannot cover both Personas.

## Consequences

- Daria is built after 0.4.0 and before phase 2, as its own step (`docs/PLAN.md`), so ahead of
  fuchy: the built-in `Command` catalogue drops the name from its phrases and carries each Reply's
  feminine form beside it, and the fuchy format later takes the same shape.
- The download grows by another ~63 MB, and the installed size by about the same.
- *gosia* carries mc_speech's licence grey area: CC0 recordings, fine-tuned from Piper's English
  *lessac* voice, whose recordings are research-only (ADR-0014). The Licences screen credits it the
  same way.
- Every UI string that names the Persona is two resources, one per Persona, with the name written
  in, in both languages; the translation gate and `TranslationTest` hold each like any other string.
- Daria is read on the phone like Darek's voice was (load time, synthesis time, the owner's ear), and
  her name and Decoys are read against real voices before they are trusted to call.
