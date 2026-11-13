# Phase F — Darek installs his first fucha from his own page and tells the weather

Sequence lives in [`PLAN.md`](PLAN.md); the live worklist lives in [`DOD.md`](DOD.md). This phase
builds ADR-0015 and the network half of ADR-0008, and leans on ADR-0009 (the grammar and its match
rule), ADR-0011 (the cancel words), ADR-0014 (the voice) and ADR-0016 (the Persona in front of every
phrase). [`CONTEXT.md`](../CONTEXT.md) already has *Fucha* and *Approved*; it is owed *Command*
rewritten so a Command comes from a fucha rather than the catalogue, and nothing else.

**What closing this phase means.** Today every Command is an entry in an enum and Darek never
touches the network. After this phase Darek's own Commands are a bundled fucha, Darek has a native
*Fuchy* page drawn from `maksforge.com`, and the first fucha on it is the weather: the user installs
it from the page, sees what it sends and where before saying yes, sets the city once, and *„Darek,
jaka pogoda?"* gets the forecast in the Persona's voice, with the screen off while Listening. A
device is asked: on a fresh install, with the page opened, the weather installed and a city set,
does that phrase answer with the temperature, and did nothing connect anywhere before the page was
opened?

**Where `PLAN.md`'s paragraph is already wrong.** Three decisions taken while planning (2026-11-13)
narrow it:

- **The first fucha is the weather, installed from the page** (the owner). The page is not a later
  nicety on top of file installs: it is the path the first fucha takes.
- **The weather's place is a city the user sets once** when switching it on (the owner, answer A).
  So no fucha reads location in this phase, and ADR-0015's open point (the `location` service type
  against `ACCESS_BACKGROUND_LOCATION`) stays open for the first fucha that needs it.
- **No fucha calls a Number in this phase.** Calling stays where `PLAN.md` put it, phase 3, with
  `CALL_PHONE` and the three readings `DOD.md` owes before a Command that calls can fire. A fucha
  declaring the `call` action is shown greyed out, *„Wymaga nowszej wersji Darka"*, like any other
  action this Darek lacks.

---

## 0. What this phase inherits

- **The catalogue is an enum.** `listen/Command.kt` holds `SAY_SOMETHING` and `GATE` with their
  phrases and Replies; `Grammar`, `commandIn` and `reactionTo` take a `Set<Command>`. The enabled set
  is computed from the debug Number (`enabledCommands`). Everything downstream of that set already
  works per enabled Command, so the change is where the set comes from, not what is done with it.
- **The grammar is rebuilt per enabled set and Persona.** A fucha switched on or off is the same
  event as a Persona switch: a new grammar for the recogniser.
- **`INTERNET` is forbidden.** `scripts/aab-permissions.py` lists it in FORBIDDEN, and the manifest
  removes Play Billing's merged copy. ADR-0008 says what has to land with it: the network security
  config, `datatransport`'s backend removed, and a traffic reading.
- **Vosk can check a word but the binding cannot ask.** The bundled `libvosk.so` (0.3.75) exports
  `vosk_model_find_word`; the Java binding (`org.vosk.LibVosk`) does not declare it, and the model
  ships no `words.txt`. Read 2026-11-13 from the AAR and the model's `graph/`. Binding it through
  JNA is checkpoint A's question.
- **The support hand-off exists.** `ui/support/SupportHandoff.kt` writes a mail with a tag;
  *„Zgłoś fuchę"* is one more caller with the fucha's ID and version.
- **The asymmetry that shapes the phase:** what a fucha may do is enforced by Darek's code alone
  (ADR-0008: the manifest cannot follow addresses learned at install). So the allowlist, the
  signature check and the clash rules are safety values with tests (rule 3), and the traffic is a
  reading, not an assumption.

## What is in, and what is deliberately not

**In:** the fucha format and its loader; Darek's own Commands as a bundled fucha; the clash rules;
the vocabulary check; the signature check and the badge or the warning; the install screen; the
*Fuchy* page from `fuchy.json`; install from a file through Darek's picker; switching on and off,
and removing; the grants per fucha and for all; `INTERNET` with ADR-0008's network rules; the
weather action and its city; updates, by hand, automatic and by voice; *„Zgłoś fuchę"*; the
profanity flag and its setting; the *Configured* test re-decided.

**Not in, and each with a reason rather than a phase:**

- **A fucha that calls a Number.** It brings `CALL_PHONE` into the release, and `DOD.md` owes three
  readings before any Command that calls can fire, two of them on the owner's phone. Phase 3 owes
  them already; doing them twice is waste.
- **Location.** The weather takes a city the user sets, so nothing here needs it, and the decision
  ADR-0015 leaves open is cheaper to take with the fucha that needs it in front of it.
- **Slots.** The weather has none. The first fucha with a slot (the roast, *„obraź {imię}"*) brings
  the per-value forms, Decoys per value and its own false-fire reading; bundling that with the
  network would hide which half is blocking (`PLAN.md` rule 2).
- **The `darek-fuchy` repo's builder on the site.** ADR-0015 makes it its own project. This phase
  needs only the repo, its signing workflow and one signed fucha (section 5).
- **A background job for updates.** A check when the app process starts, at most once a day, and
  when the page opens, covers ADR-0015's rule without a scheduled job that a Xiaomi ROM would kill
  anyway.
- **An intent filter for `.darek`.** ADR-0015 calls it a bonus; file managers hand over a
  `content://` URI with a generic type, so the picker is the path and the filter adds a second one
  to test for little.

**The tempting one: a web view for the page.** It would be an afternoon instead of a checkpoint.
ADR-0015 refused it because it runs the site's JavaScript inside an app that holds the microphone
all day; a native list from a JSON file is the whole point.

---

## Checkpoints

| | Checkpoint | Merges | Depends on |
| --- | --- | --- | --- |
| **A** | **The gate** — the vocabulary check: `vosk_model_find_word` through JNA, in a release-shaped build | no commit if verdict 1; the binding lands with B | — |
| **B** | **Fuchy as data**: the format, the loader, Darek's own bundled fucha, the clash rules, the vocabulary check; the enum goes | `refactor:` | A |
| **C** | **Install from a file**: the picker, the signature check, the install screen, the badge or the warning, the installed list with on/off and remove | `feat:` | B |
| **D** | **The network**: `INTERNET`, the network security config, the client that holds ADR-0008's rules, `datatransport` without a backend, the traffic reading | `feat:` | — |
| **E** | **The weather**: the action, the city, the grants, *„jaka pogoda"* with the screen off | `feat:` | C, D |
| **F** | **The page**: `fuchy.json`, install from it, *„Wymaga nowszej wersji Darka"*, profanity, updates, *„Zgłoś fuchę"* | `feat:` | E, and section 5 outside this repo |

B is a `refactor:` on purpose: nothing a user sees changes, and it should not cut a release. D does
not depend on B or C and can be built beside them; it is split from E because its traffic reading
(nothing connects on a fresh install) has to be taken before any fucha can connect, or the reading
cannot tell Darek's traffic from the fucha's. Every `feat:` merge goes to the internal track, which
is only the owner, so C and E ship before the page exists; the listing and Data safety change once,
with F (*Documents this phase amends*).

---

## 1. The gate: can Darek tell, at install, that the model knows every word?

### The question, in the form that can be measured

Vosk drops a grammar word its model does not know, silently, and the Command can then never fire
(ADR-0015's *Context*). In a release-shaped build on the API 26 and API 37 emulators, does a JNA
binding of `vosk_model_find_word(model, word)` return a non-negative id for *„pogoda"*, *„jaka"* and
*„darek"*, and `-1` for a made-up word (*„pogodzisko"*), against the bundled model?

### The apparatus

A debug-only row in `app/src/debug/.../ui/settings/DebugSettings.kt` with a text field and the
result, calling the binding the loader will use (`listen/Vocabulary.kt`, an object declaring the
one native function with `Native.register("vosk")`). `-PreleaseShapedDebug` for R8: JNA resolves
by name, so a missing keep rule is the likely failure.

### The verdicts, decided now rather than argued about later

1. **Known words return ids and the made-up one returns `-1`, in both builds** → the check runs at
   install and on every update, as ADR-0015 says; `darek-fuchy`'s CI runs the same check through
   the Python `vosk` package against the same model version.
2. **The symbol does not resolve, or crashes, on either API** → the build extracts the word list from
   `Gr.fst`'s output symbols into an asset at build time (`fstprint` on the host), and the check reads
   that list. Same rule, a different source.
3. **Ids for everything, the made-up word included** → the model maps unknowns to `[unk]` here; the
   check is unusable on device. Only `darek-fuchy`'s CI checks words, and an unapproved fucha gets a
   line in its warning that Darek could not check its words.

### The verdict, taken *(date)*

*(Filled in when read.)*

## 2. Fuchy as data

**The file.** A `.darek` file is a JSON envelope: `{"fucha": "<base64>", "signature": "<base64>"}`,
`signature` absent on an unsigned file. The signature is over the exact bytes inside `fucha`, so no
JSON has to be canonicalised to verify it. Inside:

```json
{
  "format": 1,
  "id": "pogoda",
  "version": 1,
  "language": "pl",
  "name": "Pogoda",
  "description": "Darek mówi, jaka jest pogoda w twoim mieście.",
  "author": "MaksForge",
  "profanity": false,
  "permissions": ["internet"],
  "addresses": ["https://api.open-meteo.com/v1/forecast", "https://geocoding-api.open-meteo.com/v1/search"],
  "sends": ["chosen_city"],
  "commands": [
    { "id": "jaka-pogoda", "phrase": "jaka pogoda", "action": "weather", "decoys": ["jaka przygoda"] }
  ]
}
```

A Command's `reply` (with `reply_feminine` for Daria) is for the `speak` action; the `weather`
action builds its own. `format` is the file format, not Darek's version: a Darek that reads format 1
refuses format 2 with *„Wymaga nowszej wersji Darka"*, and so for an `action`, `permission` or
`sends` value it does not know.

**The fixed lists Darek defines** (ADR-0015), each an enum stored by name:

| List | Values in this phase |
| --- | --- |
| Actions | `speak`, `weather`, `update_fuchy` (Darek's own only) |
| Permissions | `internet` |
| Values sent | `chosen_city` (*the city you chose*) |

**The checks at install**, every one a refusal with the reason on screen, and the same on every
update: the format and every list value known; `language` is `pl`; every action's permission
declared (`weather` needs `internet`); every address HTTPS, an exact host, no query, no fragment;
every Command word known to the model (section 1); no phrase holding a cancel word; the signature,
when there is one (section 3).

**The clash rules** (ADR-0015), over the normalised phrase (lower case, punctuation dropped), against
every enabled Command, the bundled fucha's included:

- **identical, or one containing the other** as adjacent words → never both on: switching one on
  switches the other off, and the screen says which;
- **within edit distance 2 over the whole phrase**, or a Decoy or Command word within 2 of the
  Persona names or another enabled Command's word (equal is the same token and fine) → installs
  switched off, behind a warning the user can override.

Section 6 has the owner's word on whether 2 is the number.

**Darek's own fucha** is `assets/fuchy/darek.darek`, signed like any approved fucha, with
*powiedz coś*, *otwórz bramę* (still calling only in the debug build, until phase 3) and
*zaktualizuj fuchy*. It can be switched off, never removed. The name Decoys stay in `Grammar`, not in
the fucha: they belong to the Persona (ADR-0016).

**Storage.** An installed fucha's original file goes to `filesDir/fuchy/<id>.darek`, so its
signature can be checked again on every start; the state (on or off, grants, the city, the update
setting) is JSON under one DataStore key, per *Adding a preference* in `data/AppPreferences.kt`. IDs,
never positions. Both are inside platform Auto Backup (ADR-0005), so a restored phone keeps its fuchy.

## 3. Signature, badge and the install screen

**Algorithm: ECDSA P-256 with SHA-256.** It verifies with the platform's own `Signature` on API 26;
Ed25519 arrives in the platform only at API 33 and would mean bundling a crypto library. CI signs
with `openssl` in `darek-fuchy`'s workflow; Darek ships the public key as a constant.

**Approved** means: the signature verifies against that key. Anything else installs only after the
strong warning, and an unsigned file over an approved fucha with the same ID is a fresh install
(ADR-0015, *Updates*): every grant asked again.

**The install screen**, top to bottom: the name, the author and the badge or the warning; what it
does, one line per Command, as the user says it (*„Darek, jaka pogoda?"*); its permissions; the
exact addresses; each value it sends, by name; *Wulgaryzmy* if flagged; a clash or vocabulary refusal
if any; and the line that even an approved fucha may contain a mistake and is installed at the
user's own risk. Then *Zainstaluj* or *Anuluj*.

## 4. The network

Built to ADR-0008, which this section does not restate: `INTERNET` moves from FORBIDDEN to EXPECTED
in `aab-permissions.py` with its user named; `network_security_config.xml` with
`cleartextTrafficPermitted="false"`; `datatransport`'s `CctBackendFactory` meta-data removed.

**One client for every request**, `HttpURLConnection` (no new dependency): HTTPS only,
`instanceFollowRedirects = false`, and a request goes out only when its URL's host equals a declared
address's host and its path starts with that address's path. Darek's own requests go only to
`maksforge.com`. The allowlist check is the function with the test; the client cannot be called
without it.

**A connection with the screen off.** A foreground service's process is exempt from Doze's network
block on stock Android; Xiaomi's ROMs are not stock. Reading R3 asks the emulator; the Xiaomi half is
owed to a phone, like the rest of phase 2's.

## 5. The weather

**The action** asks Open-Meteo, which needs no key: the forecast for the stored coordinates
(current temperature and weather code, today's minimum and maximum). It turns the WMO weather code
into Polish words from a table in Darek's code, and the Persona says it, for example *„Teraz 12°C,
pochmurno. Dziś od 8 do 15 stopni."* The Reply names no city: the city is typed in its nominative,
and *„w Krakowie"* would need its locative.

**The city** is asked once, when the fucha is switched on: a text field, then Open-Meteo's geocoding
search, then the user picks one of its answers (*Kraków, małopolskie*). The coordinates are stored;
the city's name is sent once to the geocoding search and the coordinates on every forecast, both
declared as *the city you chose*. Settings for the fucha change it.

**When the network fails**, the Persona says *„Nie mogę sprawdzić pogody, nie mam internetu."*
within the Reply's usual time: a 5-second timeout, then that line. Nothing is retried in the
background.

**The grant.** On the first switch-on, unless *allow for every fucha* already covers it, a sheet asks
for the `internet` grant for this fucha (ADR-0015, *Permissions can be granted per fucha or for
all*). Refused, the fucha stays off. The per-permission default (ask, allow for all approved, never)
and its exceptions live on a *Uprawnienia fuch* screen under Settings; allowing unapproved fuchy for
all is behind the ten-second dialog.

**Outside this repo, before F's readings:** `maks-sredniawa/darek-fuchy` (public, so
`maksforge.com/darek-fuchy/` serves it), with the published rules for authors, the vocabulary check
in CI, a signing workflow on push to `main` only (ADR-0015), the weather fucha, and `fuchy.json`.
The signing key's public half goes into Darek at checkpoint C, so the repo and its key come first.

**Open-Meteo's terms** (read 2026-11-13, `open-meteo.com/en/terms`): free for apps without
subscriptions or advertising, and *„voluntary donations, including contributions through a tip
jar, are permitted"*, so Darek qualifies; under 10,000 calls a day, 5,000 an hour and 600 a minute,
limited *„by IP addresses and application"*; the data under CC BY 4.0. One forecast per spoken
Command stays far inside that. The attribution goes on the fucha's description and in Credits.

## 6. Decided with the owner

- **The first fucha:** the weather, installed from the page (2026-11-13).
- **Where the weather gets the place:** a city set once (2026-11-13, answer A).
- **Calling from a fucha:** phase 3 (2026-11-13, no objection to the plan).
- **What counts as configured** once a fucha is installed: *(asked 2026-11-13, awaiting the
  owner.)*
- **The clash thresholds** (edit distance 2; containment never both on): *(to ask.)*

## 7. The page

**`fuchy.json`** at `https://maksforge.com/darek-fuchy/fuchy.json`: each approved fucha's ID,
version, name, description, author, profanity flag, the actions it uses and the URL of its file.
The page is a native list: installed fuchy first, then the rest; each row with the badge, a short
description and *Zainstaluj*, *Zaktualizuj* or *Wymaga nowszej wersji Darka*. Fuchy flagged
*Wulgaryzmy* are hidden until *Pokazuj fuchy z wulgaryzmami* is on in Settings, off by default.
The page opens from Home and from Settings. Opening it is the first time a fresh install connects
anywhere.

**Updates.** With an approved fucha other than Darek's own installed, Darek reads `fuchy.json` when
the page opens, and once a day at process start. *Aktualizuj fuchy automatycznie* is on by default;
off, the page shows *Zaktualizuj*. *„Darek, zaktualizuj fuchy"* runs the same check and says what
it did. An update adding a permission, an address or a value sent waits for the user on the page.

**„Zgłoś fuchę"** on every installed fucha: the support hand-off with the fucha's ID and version in
the mail's tag.

---

## Tests

`PLAN.md` rule 3's second clause. Each is a pure function that bounds a safety value.

- **The signature check.** A valid file verifies; one changed byte in `fucha`, a signature from
  another key, and a missing signature do not count as approved. Fails towards badging what
  MaksForge never reviewed.
- **The address allowlist.** HTTP refused; a host that only ends with the declared one
  (`evilapi.open-meteo.com.example`) refused; a path outside the prefix refused; a redirect never
  followed. Fails towards sending the city somewhere the user never saw.
- **The install checks.** An action whose permission is not declared, an unknown value sent, an
  unknown format or action, a non-`pl` language: each refused. Fails towards a fucha doing what its
  screen did not show.
- **The clash rules.** A phrase with *stop* or *anuluj* refused; containment never both on; within
  two letters installs off; a Decoy within two of *darek* or *daria*. Fails towards a fucha
  stealing another's utterances or cancelling a call.
- **The update decision.** A new permission, address or value waits; an unsigned file over an
  approved ID is a fresh install. Fails towards a silent widening of what a fucha may do.

## Documents this phase amends

- **ADR-0015**, amended with this plan: the repo is `maks-sredniawa/darek-fuchy` (the repos moved,
  2026-11-06); a value the user sets for a fucha (*the city you chose*) joins the fixed list beside
  slots; ECDSA P-256 as the signature. ADR-0008 needs nothing: a typed city is not something Darek
  heard, and it already lets a fucha send only the values it declares.
- **ADR-0009**: the catalogue is the bundled fucha.
- **`CONTEXT.md`**: *Command* from a fucha.
- **`PLAN.md`**: phase F's paragraph and rule 4's table (location and calling leave F).
- **With F**: the privacy policy (the network, Open-Meteo, the city), the listing in both languages
  naming the fuchy page and the weather, `play-app-content.md` and the Data safety re-answered (the
  city is approximate location, sent to Open-Meteo, optional, for app functionality: **assumed**
  category, read it in the Console sitting), the content rating re-answered for user-generated
  content and the profanity a fucha can carry, and the screenshots with the page.

## Known gaps, deliberately

- **Xiaomi's network with the screen off** is unread until a phone is attached, like phase 2's
  Xiaomi half.
- **A leaked signing key** badges anything until a Darek release ships a new public key
  (ADR-0015).
- **The weather's Reply names no city**, because the city is typed in its nominative.

## Readings

`PLAN.md` rule 3: from the device, with the command that read it. Every reading is on the emulators
unless it says otherwise; `docs/phase-2.md` and the `emulator-test` skill have the injected-audio
setup.

| # | Reading | Command | Result |
| --- | --- | --- | --- |
| R1 | The gate, section 1 | the debug row, release-shaped, API 26 and 37 | *(to read)* |
| R2 | A fresh install connects nowhere until the page opens; `datatransport` uploads nothing | emulator started with `-tcpdump`, the app opened, Listening started, a Command fired, 10 minutes; then the page opened | *(to read)* |
| R3 | The weather answers with the screen off, the app swiped away and the device forced idle | `adb shell dumpsys deviceidle force-idle`, then *„Darek, jaka pogoda?"* injected | *(to read)* |
| R4 | Installing from the page: the install screen shows the addresses and the value sent, the badge is there, the grant is asked once | the page on a fresh install | *(to read)* |
| R5 | An unsigned copy of the weather fucha from a file: the strong warning, every grant asked | the picker with a hand-edited file | *(to read)* |
| R6 | An update adding an address waits for the user | a version 2 in a test `fuchy.json` | *(to read)* |
| R7 | Owed to a Xiaomi phone: R3 on MIUI and HyperOS | as R3 | *(owed)* |
