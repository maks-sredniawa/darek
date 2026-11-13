# Play Console — App content answers

The answers Darek gave in the Console's **App content** pages, what each one rests on, and **the
fact that would change it**. The Console shows what was answered but never why, and Play asks again:
when the app changes, when the questionnaire changes, and once a year. A re-declaration made from
memory a year from now is how a true answer turns false.

**Status:** answered 2026-10-31 with managed publishing on. The content rating was submitted the same
evening and IARC's ratings are in (below). **Sent for review 2026-10-31**, together with the listing
and the first closed release, Alpha 0.2.0 (versionCode 31). Still owed: the re-read of *Target
audience and content* (below).

Two rules keep this file honest:

- **The privacy policy and the Data safety form describe the same facts to two audiences**, and Play
  cross-checks them. [`privacy-policy.md`](privacy-policy.md) and this file move in the same commit.
  A change to one without the other is the drift this file exists to stop.
- **A claim about permissions is read off the built artifact, never off the manifest.** A dependency
  can merge a permission the source never declared, and that is invisible from `AndroidManifest.xml`.
  `scripts/aab-permissions.py` is the reader, it runs before every upload, and its `EXPECTED` and
  `FORBIDDEN` tables are this section's twin — edit them together.

## What the artifact carries

Read at **versionCode 152** from a local release bundle (`./gradlew bundleRelease`, commit
`c85e72b`, phase 2's checkpoint D, the code 0.9.0 ships) on 2026-11-12. `publish-play.yml`'s verify
step reads the uploaded bundle again:

```text
8 permissions, all accounted for; none of the 6 forbidden ones present; 0 <uses-feature> declared; 0 of the 5 activities lock their orientation, every one of them expected
```

`RECORD_AUDIO` implies `android.hardware.microphone`, `required`, though nothing declares it: a phone
without a microphone cannot use Darek, so Play filtering it out is correct.

| Permission | Whose | What it is for | Console consequence |
| --- | --- | --- | --- |
| `RECORD_AUDIO` | Darek | *Słuchaj*: the bundled recogniser hears the Commands, only while Listening is on ([ADR-0009](adr/0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)) | a runtime permission, asked after the in-app disclosure; Data safety still *no data collected* (point 2). Not on Play's declaration list |
| `ACCESS_NETWORK_STATE` | Play Billing's `datatransport` | read before its (blocked) log upload; opens nothing ([ADR-0008](adr/0008-darek-connects-only-where-the-user-can-see.md) allows it as this merge only) | none, but a reviewer may ask why an app with no network reads it — this row is the answer |
| `com.android.vending.BILLING` | Play Billing Library | the tip ([ADR-0007](adr/0007-the-tip-is-a-play-billing-consumable-that-unlocks-nothing.md)): IPC to the Play app's billing service | none: not on Play's declaration list. The listing shows *Contains in-app purchases* once products exist |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | its own non-exported receivers, signature-level | none: not user-visible |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Darek | the Listening service, type `microphone` ([ADR-0010](adr/0010-listening-is-a-microphone-service-the-user-starts.md)) | the foreground-service declaration with its video (*Foreground service permissions*, below) |
| `POST_NOTIFICATIONS` | Darek | Listening's notification, and the one after a reboot; without it Darek does not listen (ADR-0010) | a runtime permission on Android 13+, asked at the first start of Listening; not on Play's declaration list |
| `RECEIVE_BOOT_COMPLETED` | Darek | after a reboot, a notification whose *Słuchaj* starts Listening, only if it was on (ADR-0010) | none: not on Play's declaration list. The listing names it, *Run at startup* |

The billing library's `datatransport` dependency merges `INTERNET`; the manifest removes it with
`tools:node="remove"`, and the gate is what proves it stayed out. Its usage log sits in a database,
which `backup_rules.xml` and `data_extraction_rules.xml` keep out of Auto Backup
([ADR-0005](adr/0005-backup-is-automatic-for-the-database-and-manual-for-media.md)'s amendment).

**Forbidden, and asserted absent**: `INTERNET`, `AD_ID`, `QUERY_ALL_PACKAGES`,
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `CAMERA`, `USE_EXACT_ALARM`. `INTERNET` and `AD_ID` are what
the Data safety and advertising-ID answers rest on. `ACCESS_WIFI_STATE` is not listed but fails all
the same, as any permission missing from `EXPECTED` does (ADR-0008).

**Not in the artifact yet, and owed a row here the day they arrive.** The privacy policy already
describes them, because it covers the app as it will ship; the Console is answered for the artifact
in front of it.

- **Phase 3:** `CALL_PHONE` (the Gate call, [ADR-0011](adr/0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)).
  Phase 1's checkpoint C dials from the debug build only; the release gets it with the Commands.

**Two asks that are not permissions**, from phase 2: the battery-optimisation exemption and Xiaomi's
autostart, both hand-offs to Settings (`work/`). They appear on no Play list and need no
declaration; the privacy policy names them, because a user sent to two Settings screens should find
both explained.

---

## Ads

**No, my app does not contain ads.** Answered 2026-10-31.

Rests on: no ad SDK; `AD_ID` is forbidden by `aab-permissions.py`. Would change: any ad SDK, ever.
`AD_ID` absent from the artifact is the check that would catch one arriving through a dependency.

## Sign-in details

The Console's former *App access* page. **No: no part of the app is restricted.** Answered
2026-10-31.

Rests on: there is no account and no sign-in. The Console's *Yes* list mentions one-time products,
but the tip unlocks nothing, so no content requires payment (ADR-0007). Would change: a feature
behind a login, or a purchase that unlocks something.

## Content rating

**Submitted 2026-10-31 21:43; IARC status *Completed*.** The owner accepted IARC's Terms of Use. The
answers given:

- **Email:** `darek.support@maksforge.com`, the listing's address, so there is one address to keep
  alive, not two. (Kozica used the developer account's Gmail.)
- **Category:** *All other app types*. Not *Social or communication*: that category means people
  talking to each other through the app, and calling the user's own gate opener is not that.
- **Every question No**, except *Does the app allow users to purchase digital goods?* **Yes**, for
  the tip (ADR-0007), with its follow-up, *can these purchases include random items* (loot boxes),
  **No**.

The ratings IARC returned, as the siblings that re-rated with the tip predicted: **PEGI 3, ESRB
Everyone, USK 0**, the *3* level in the other rating systems, and **Brazil's ClassInd at 14+**, which
it gives any app selling digital goods. The interactive element shown is *In-app purchases*. Deleting
the tip is the only answer that brings Brazil back to L.

In the Console the questionnaire shows one section at a time, and *Next* stays disabled until *Save*
has been pressed.

Would change: user-generated content, communication between users, or a link out to anything that
has either. A support `mailto:` hand-off is not user-to-user communication — it opens the user's own
mail app, addressed to the developer — and the rate row opens Play's own listing, whose reviews are
Play's surface rather than the app's. A call to a Number the user typed in is the user's own call, not
communication the app hosts.

## Target audience and content

**18 and over** only. Answered 2026-10-31. The optional *Restrict users that Google has determined to
be minors* is left unticked: nothing asks for it.

Rests on: Darek is for adults who own a gate opener, and any age band under 18 brings in the Families
policy and its design and SDK requirements. The Console showed only *Target age* and the summary for
this answer; *App details*, *Ads* and *Store presence* asked nothing. Would change: lowering it.

⚠ The page said the under-13 bands were blocked because *"your app's ESRB rating is set to Teen or
higher"*, before any rating existed. Re-read this page once the content rating is submitted.

## News app

**Not asked.** On 2026-10-31 the Console listed no News declaration for Darek, on either the *Needs
attention* or the *Actioned* tab, nor on the dashboard. If it appears: **No**, Darek publishes no
news.

## Government, financial and health features

**No to all three.** Answered 2026-10-31.

- **Government:** not developed by or for a government.
- **Financial features:** *My app doesn't provide any financial features*; the *Documentation* step
  asked nothing. A tip is an in-app purchase, not a financial feature.
- **Health:** *My app does not have any health features*; the *Regional requirements* step asked
  nothing. Darek is a voice gate opener.

Would change: copy or a tag that sells the app as something it is not. **Keep health and safety claims
out of the listing, the site and the tags** — Play's enforcement has treated a linked page as part of
the listing.

## Data safety

**No data collected. No data shared.** Answered 2026-10-31 against the artifact above. The Console's
preview reads *"No data collection declared"* and *"No data shared with third parties"*, with the
privacy policy URL.

Play's definitions, from its Data safety help page (support.google.com, answer 10787469) — re-read
them at each re-declaration rather than trusting this quote:

> "'Collect' means transmitting data from your app off a user's device."
>
> "User data accessed by your app that is only processed locally on the user's device and not sent
> off device does **not** need to be disclosed."
>
> Not sharing: "Transferring user data to a third party based on a specific user-initiated action
> where the user reasonably expects the data to be shared."

The answer rests on one fact per route data could take:

1. **The app opens no network connection.** `INTERNET` is absent from the artifact (ADR-0008), and
   `aab-permissions.py` fails the upload if it ever appears. This one fact is most of the answer. From
   phase 3, when `CALL_PHONE` reaches the release, one thing does leave the phone at the app's
   request: the Gate call of point 4.
2. **Audio is processed on the device, and that is not collection.** The bundled recogniser consumes
   the microphone frame by frame; only the text of a matched Command is kept, in memory, and audio is
   never stored or sent (ADR-0008, ADR-0009). `RECORD_AUDIO` is in the artifact from 0.3.0; re-read
   2026-11-01 against Play's definitions above: still no data collected, so the Console answer stands
   unchanged. From 2026-11-02 the policy also says that *„No?"*, the answer to the name, plays with the
   microphone open (ADR-0011). It is processed like any other sound, so no answer here moves.
   Re-read 2026-11-12 for phase 2 (0.9.0): Listening now runs with the screen off and the app closed,
   in a foreground service, and processes the same audio the same way; the boot notification and the
   tile read nothing but the app's own setting. Still no data collected and none shared.
3. **The Numbers stay on the device.** The user types each one in; it is kept in DataStore and used
   only to place that Command's call. **Auto Backup is still "no data collected"**: the platform moves
   the user's backup to the user's own Google account, and the developer never sees it. That is
   Android transmitting the user's backup, not the app transmitting to the developer. ⚠ Every setting
   the backup carries lands on the user's *next* phone too — ADR-0005's amendment says what that is.
4. **The call is another app's work; the voice is Darek's own.** The Reply is rendered inside the
   app by its bundled synthesiser and played by the app itself; no other app is handed it
   ([ADR-0014](adr/0014-darek-speaks-with-his-own-bundled-voice.md)). The Gate call is placed by the
   phone's Telecom service on the user's SIM, to the Number the user typed for that Command
   (ADR-0011). The app opens no network connection, but the call does leave the phone at its request,
   with no tap at that moment: the user's operator carries the Number dialled and the user's own caller
   ID to the user's own opener. **Still "No data shared"**, for Play's own reason quoted above: a
   transfer from a specific user-initiated action the user reasonably expects, here the Command they
   set up for that Number. ⚠ Give that reason in a re-declaration, not "the app sends nothing".
5. **A support mail is still "no data collected", and the reason is the definition rather than an
   exemption.** A `mailto:` hands a draft to **another app**; the user reads it, can edit every line,
   and sends it from their own mail client, or does not. ⚠ **Do not write "Play exempts support
   flows" in a re-declaration.** The help page has no such exemption; the claim is true because of
   what "collect" means, and that is the reason to give. What the developer then holds is a mail in a
   mailbox, kept until the sender asks for it to be deleted, as the policy's *Deleting your data*
   states.
6. **A tip is still "no data collected", by the same help page's FAQ on payment services.** It names
   Google Play's billing system as a service whose payment data you don't need to declare when the
   app never accesses that information and the service collects it directly from the user under its
   own terms. The tip meets both: Play's own sheet takes the payment details, under Google's terms,
   and the app never sees them. What the app receives back is a purchase token and a product id,
   which it hands straight back to Play to consume, never stores and never sends anywhere else. The
   developer's order report comes from Google, not from the app.

Would change any of it:

- **`INTERNET` on the artifact**, from our code or a dependency. Every answer here becomes a question,
  and ADR-0008 with them.
- **Anything the app sends by itself**, such as an analytics or crash-reporting SDK. There is none.
- **Saving audio or transcripts**, even only on the device: the policy says never stored.
- **The app keeping a purchase**, such as a tip history or a supporter badge: that is *Purchase
  history* the app collects, and the form has no way to declare it as Google Play's.
- **Reading contacts, the call log or phone state.** Darek reads none of them today (ADR-0011).

## Advertising ID

**No.** Answered 2026-10-31. Rests on: the artifact carries no `com.google.android.gms.permission.AD_ID`,
and `aab-permissions.py` forbids it. Would change: an SDK that merges it.

## SMS and call log permissions

**Not asked, and not expected to be.** `CALL_PHONE` is not in Play's SMS and Call Log group, and
Darek declares nothing that is: no `READ_CALL_LOG`, no `PROCESS_OUTGOING_CALLS`, no SMS permission,
and no `READ_PHONE_STATE` either, because the end of the call is read from the audio mode (ADR-0011).
Play's malware policy still asks that calls are made with the user's consent: that is the Number the
user typed for the Command, the user's own start of Listening, and the spoken Reply with its cancel
window before the dial. Would change: any permission from that group, or an SMS-driven opener.

## Foreground service permissions

**Filed 2026-11-13 with 0.9.0 (versionCode 158), in review.** The artifact declares
`FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE` for the Listening service (ADR-0010). The
Console lists the type only once a release holds a bundle that declares it: CI's upload of 158 was
refused at commit (*"You must let us know whether your app uses any Foreground Service
permissions"*), so the bundle went up by hand into the internal release and the form appeared then.
Answered: `FOREGROUND_SERVICE_MICROPHONE` → task **Background audio input**, with a 39 s video of a
first start on the API 37 emulator from a release build (Słuchaj, the disclosure, both permission
asks, the notification with *Zatrzymaj*, stopped), linked from the owner's Google Drive and also
attached to the `v0.9.0` GitHub release. The answers behind it:

- **Use case:** voice commands for an assistant, started by the user; nothing is saved.
- **What the user sees:** after turning Listening on, a notification that Darek is listening, with
  *Stop*, for as long as it runs.
- **Why it cannot wait:** a Command is spoken with the screen off and the app closed, and a missed one
  is a gate that does not open.
- **A video** of the user starting Listening and the notification, linked here and viewable by anyone
  with the link for as long as the declaration is on file. `adb screenrecord` captures no audio, so
  say so if the reviewer needs to hear a Command.

The **prominent disclosure** right before the `RECORD_AUDIO` request shipped with *Słuchaj* in 0.3.0
(`mic_disclosure_body`). Phase 2's checkpoint D rewrote it for the background (Darek hears with the
screen off and the app closed, a notification shows it, nothing is kept) and shows it once before
the first service start, whether or not the microphone was already granted, since 0.3.0's users
agreed to listening with Home on screen only.

## Privacy policy

**`https://maksforge.com/darek/privacy-policy`**, saved 2026-10-31 — the extensionless form; the
`.html` form redirects to it, and the siblings used it. Served from `docs/` by maksforge.com, so it
updates within about 15 minutes of a merge to `main` with no release, which is why it has to be right
before the app it describes ships rather than after. Until this commit, the live page was the unfilled
template; nothing was sent for review in between.

## Store settings (not App content, recorded here because it is asked for the same reason)

Grow → Store presence → Store settings, saved 2026-10-31.

- **App or game:** App. **Category:** Tools — a utility that turns a spoken command into a call to the
  user's gate opener. Tools was offered, so Productivity was not needed.
- **Tags:** none.
- **Contact email:** `darek.support@maksforge.com`, public on the listing and the same address
  `ui/support/SupportConfig.kt` compiles in. The Console field changes without a release; the app's copy needs
  one. Replying from it needs Gmail's *Send mail as* entry (`DOD.md`).
- **Phone:** empty. **Website:** `https://maksforge.com/darek/`. **External marketing:** left at the
  default, ticked.

## Production access — the application, and the answers it was given

**Not yet submitted.** Asked once, from the Console's Dashboard, after the closed test's 14 days.
Record the answers **verbatim**, not summarised: the Console asks the same set again for the next app
on this account, and an answer re-read a year later has to be the one that was given. Draft them here
first, under a dated *Drafted, not submitted* line; every claim must trace to a dated record in
[`DOD.md`](DOD.md) or a phase file.

**About your closed test**

- *How did you recruit users for your closed test?* — not yet asked
- *How easy was it to recruit testers?* — not yet asked
- *Describe the engagement you received from testers* — not yet asked
- *Provide a summary of the feedback that you received* — not yet asked

**About your app**

- *Who is the intended audience?* — not yet asked
- *Describe how your app provides value to users* — not yet asked
- *How many installs do you expect in your first year?* — not yet asked

**Your production readiness**

- *What changes did you make based on what you learned during your closed test?* — not yet asked
- *How did you decide that your app is ready for production?* — not yet asked

**Three things the apps built from this template kept deliberate, so a later answer does not undo
them:**

1. **The audience answer agrees with *Target audience* above.** A reviewer reads the two side by side.
2. **Engagement is answered from what the app can know.** With no `INTERNET` permission there is no
   telemetry, so *"I cannot see how often testers used it"* is the true answer. A guessed figure would
   contradict *Data safety* in this same file.
3. **The changes answer says where the changes came from.** If the testers reported nothing, say so
   and name what came from your own use instead.

## The tip (ADR-0007)

**A Play Billing consumable that unlocks nothing, not a payment link** — ADR-0007 says why. What a
reviewer can ask about, and the answer:

- **The *Contains in-app purchases* label** on a free app: the tip unlocks nothing, and the app says
  so where it offers it.
- **The content rating**: *digital purchases* Yes, Brazil at 14+ (above).
- **Data safety**: point 6 above.
- **Financial features**: none. A tip is an in-app purchase, not a financial feature.

**No payment link on any surface the release controls**, and that reaches one level out, to the pages
the listing links to. ⚠ The listing's *Website*, `maksforge.com/darek/`, is drawn by
`maks-sredniawa/pages-theme`, whose layout carries a *Support* link and a *Buy me a coffee* link to
`buymeacoffee.com/maksforge` on every page, the privacy policy's included. The siblings went live with the
same theme; if a reviewer cites it, the theme is where the fix goes.

---

## When Play asks again

1. Run `python3 scripts/aab-permissions.py` against the newest bundle, and compare its table with the
   one above. A new row is a new question.
2. Read `docs/privacy-policy.md` against the answers here. If one has moved, both move, in one commit.
3. Copy any value this file does not hold while the Console is open — a category, a date, a
   declaration's exact wording — so the next re-declaration can compare against it.
4. Re-date the sections you re-answered.
