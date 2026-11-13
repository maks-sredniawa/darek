# Darek takes on fuchy: Commands come as data files, reviewed and signed by MaksForge

## Context

Darek cannot ship a Command for everything people want from him: a second gate, a garage, the
weather, a roast for a friend. Until now every Command was an app release
([ADR-0009](0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)), and the user
could not add a phrase, because nothing would choose its Decoys and Vosk silently drops words its
model does not know.

The owner decided on 2026-11-01 that Darek grows through **fuchy** (`CONTEXT.md`, *Fucha*): files of
Commands made by MaksForge and by the community, installed from a page in the app, the way a
handyman takes on side jobs. Darek is a little meme, and the word is chosen to say so.

Three constraints shape it:
- **Play forbids an app from downloading executable code** (`.dex`, `.jar`, `.so`) from outside
  Play. A fucha that ran code would be a policy breach and an app Play never reviewed.
- **A false match on a Command that calls rings a gate.** Whatever a fucha adds must not loosen
  that.
- **A fucha can never add an Android permission.** Permissions come from the manifest, which Android
  reads at install time. Darek can only ever do what its release declares.

## Decision

**A fucha is data, never code.** It is a JSON file (`.darek`) of Commands, each with its phrase,
its Reply and the action it uses. The phrase is written without the name: Darek puts the chosen
Persona's in front ([ADR-0016](0016-daria-is-a-persona-the-user-switches-to.md)), and a Reply may
carry a feminine form for Daria. From outside the fuchy page it arrives through Darek's own file
picker (`ACTION_OPEN_DOCUMENT`), the dependable way in: file managers and messengers hand over a
`content://` URI with no name to match and a generic MIME type, so an intent filter for `.darek` is
a bonus, not the path. The actions are code inside Darek, each one written for its task (speak,
call a Number, weather, later more): one general "fetch" cannot make sense of every service's answer
without AI, and Darek is meant to grow into tasks harder than reading one field. A new kind of
action is a Darek release, and **so is every permission: each lands in the release that adds the
feature using it**, an action or Darek's own (Słuchaj, the fuchy page), never ahead of it.
`EXPECTED` in `scripts/aab-permissions.py` names each one's user. It comes with what Play asks of
it: a Data safety answer, the listing naming the feature, and a Play Console declaration where Play
has one. Darek is meant to do everything on the phone in the end, so he may come to hold most of the
permissions Play lets an assistant like him hold, one feature at a time; Play allows only
permissions a shipped feature promoted in the listing uses.

**Darek's own Commands are fuchy too**, bundled in the app in the same format, so there is one loader
and one path into the grammar. A bundled fucha can be switched off, never removed. One that needs a
permission (a weather fucha needs the internet and the location) is off on a fresh install.

**What a fucha declares, the user sees before installing:**
- **its permissions**, from a fixed list Darek defines, mostly Android's own (location, calling)
  plus Darek's (internet). A fucha that uses an action whose permission it did not declare is
  refused. Location needs its own decision before a fucha can use it: read from the `microphone`
  service with the screen off, Android 10+ counts it as background access, so it needs either the
  `location` service type (another Play declaration, and on 14+ the grant before the service starts)
  or `ACCESS_BACKGROUND_LOCATION` and Play's background-location declaration. A weather fucha that
  takes the city as a slot needs neither;
- **the exact addresses it contacts**: HTTPS only, each an exact host, plus a path prefix if one is
  declared. Darek refuses any other, including one found in an answer, and follows no redirect;
- **each value it sends**, by name, from a fixed list Darek defines in its code, like the
  permissions: *your approximate location*, the value said in one of its slots (*the city you
  say*), or a value the user sets for the fucha when switching it on (*the city you chose*). A fucha declaring a value not on the list is refused. **Audio and the rest of what Darek
  heard are never sent**, whatever a fucha declares
  ([ADR-0008](0008-darek-connects-only-where-the-user-can-see.md));
- **its language.** Only `pl` is accepted today ([ADR-0012](0012-darek-speaks-polish-and-opens-in-polish.md));
  the field exists so a later Darek can refuse what it cannot speak.

**A Command that calls never carries a number.** The user picks one of their own Numbers for it.

**Slots are allowed** (*„obraź {imię}"*, *„pogoda w {mieście}"*), in Commands that call too: the
fucha lists every value with its forms, and the cancel window
([ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)) stands before every
dial. The builder and the install screen warn that slots are less reliable, Polish inflection most
of all.

**Approved** fuchy are the ones the owner reviewed: community authors open a PR in
`maks-sredniawa/darek-fuchy` under published rules, and merging is the decision that a fucha goes live.
CI signs it with a key held in that repo's secrets, in a workflow that runs only on a push to
`main`, after the merge, never on `pull_request_target` or in any job that runs a PR's code. Darek
ships the public half and checks the signature offline. The signature is ECDSA P-256 over SHA-256,
which the platform verifies from API 26; Ed25519 reaches the platform only at API 33. An approved fucha carries the MaksForge
badge. Any other `.darek` file still installs, after a strong warning that MaksForge has not
reviewed it.

**The install screen** shows what the fucha does, its permissions, addresses and values sent, the
badge or the warning, and that even an approved fucha may contain a mistake and is installed at the
user's own risk. Every fucha has *„Zgłoś fuchę"*, an email to the developer through the support
hand-off with its ID and version.

**Permissions can be granted per fucha or for all**, per permission: ask for each fucha (the
default), allow for every fucha, or never, each with exceptions. "Allow for every fucha" covers
approved ones only; extending it to unapproved ones is a separate option behind a dialog whose
confirm button unlocks after ten seconds.

**Phrases that clash** are compared after normalising (lower case, punctuation dropped), against
ADR-0009's match rule, which ignores anything said before or after a Command:
- **A phrase holding a cancel word** (*stop*, *anuluj*,
  [ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)) is refused: cancel
  words act on partial results, so saying it would cancel any call in its window.
- **Identical phrases, and two where one contains the other** (*„otwórz bramę dwa"* holds
  *„otwórz bramę"*, which would fire and call the other Number), are never both on:
  switching one on switches the other off.
- **A phrase that sounds like an enabled one**, within two letters of it in all (edit distance over
  the normalised phrase), installs switched off, behind a warning the user can override to keep both
  on. So does a fucha with a Decoy or a Command word within two letters of *darek* or *daria*,
  whichever Persona is chosen, since the user can switch (the name Decoys are Darek's own), or of a
  word in another fucha's enabled Command: the grammar holds every
  Command word as a token of its own too, so either would take that Command's utterances. A word
  equal to such a word is the same token and changes nothing.

**The fuchy page is a native screen** drawn from the site's `fuchy.json`, not a web view. A fucha
needing an action this Darek lacks is shown greyed out, *„Wymaga nowszej wersji Darka"*. Fuchy with
profanity are marked *„Wulgaryzmy"* and hidden until the user turns them on in Settings.

**Updates:** Darek checks maksforge.com for them while an approved fucha other than his own is
installed; his own update with the app. They update automatically (a setting, on by default) or,
with it off, on a tap; *„Darek, zaktualizuj fuchy"* does it by voice. An unapproved fucha has
nowhere to update from: a newer file installs over it. A file without a valid MaksForge signature
over an approved fucha with the same ID is a fresh install, not an update: the strong warning is
shown, every grant is asked again, and its Commands' Numbers are cleared, so each one that calls
stays off until the user picks a Number again. Every update passes the checks an install does
(signature, vocabulary, clashes), and one that adds a permission, an address or a value sent waits
for the user.

**Personas stay in the app.** A Persona (Daria) changes the name every Command starts with, the name
Decoys and the grammatical gender of every line spoken, so it is not one fucha among others.

## Alternatives

**Commands only as app releases** (ADR-0009 as it stood). Safe, and every Command tuned in house,
but Darek would only ever do what one developer had time to ship.

**Fuchy that run code** (scripts in an interpreter). Play allows sandboxed interpreters, but a
review of every fucha would then be a code review, and a fucha could do whatever the interpreter
reaches. Data choosing from fixed actions keeps the review to reading a file.

**One general fetch action**, where a fucha writes its own request and picks a field from the
answer. Any JSON API would work with no release, but it cannot handle services whose answers need
real work to turn into a sentence, and every fucha would be a small program in disguise.

**Reply-only fuchy, calling kept to the bundled ones.** Calling a Number the user chooses is most of
why people want fuchy (a second gate, a garage), and a fucha never carrying a number already keeps a
malicious one from dialling a premium line.

**Only approved fuchy install.** It would stop people building their own on their own phones. The
badge and the warning keep the review meaningful without that.

**A badge for the owner's own fuchy only.** Three levels (own, reviewed, unknown) would make every
permission rule ask which; the review is what makes a fucha safe, not who wrote it.

**Signing on the owner's PC.** No one could sign if GitHub were broken into, but every fucha release
would wait for the owner at the computer. The merge is already the decision; CI signing adds no
trust, only removes the step.

**The page as a web view.** Simpler to build, but it runs the site's JavaScript inside Darek, which
is the code-from-the-internet question this ADR closes.

## Consequences

- Darek gets `INTERNET` ([ADR-0008](0008-darek-connects-only-where-the-user-can-see.md), rewritten).
- Fuchy are user-generated content in Play's sense: published content rules for authors, the
  owner's review as moderation, and *„Zgłoś fuchę"* as the in-app report. The content rating
  questionnaire is answered for the profanity a fucha can carry.
- ADR-0009's catalogue becomes the bundled fuchy, and a phrase can come from outside the app. The
  vocabulary check moves to install time and to `darek-fuchy`'s CI, where it also helps the owner's
  review.
- *Configured* (`reactionTo` in `listen/CommandMatch.kt`) means an enabled Command that calls, from
  whichever fucha. That test and the answer it picks are re-decided in phase F, the owner's call:
  for anyone whose enabled fuchy only reply, Darek's own *powiedz coś* on a fresh install included,
  *„Stary, nie mam żadnej fuchy."* would be false.
- A leaked signing key lets anyone badge anything; the fix is a Darek release with a new public key.
- The phase that builds fuchy comes after phase 2 (`docs/PLAN.md`). The `darek-fuchy` repo, its
  rules and the builder on the site are a project of their own.
