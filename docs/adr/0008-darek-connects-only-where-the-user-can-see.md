# Darek connects only to MaksForge and to the addresses a fucha declares, and never sends audio

## Context

A voice assistant is the app people most expect to be listening for someone else. Darek holds the
microphone open all day ([ADR-0010](0010-listening-is-a-microphone-service-the-user-starts.md)), so
the privacy promise has to be one a user can check: **your voice never leaves the phone, and of
what you say, only a value a fucha showed you it sends before you installed it.**

Darek's own work needs no network. Recognition runs on a bundled model
([ADR-0009](0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)), the Reply is
his own bundled voice ([ADR-0014](0014-darek-speaks-with-his-own-bundled-voice.md)), and the Gate
call goes through the phone's dialler
([ADR-0011](0011-the-gate-call-is-placed-through-telecom-after-the-reply.md)). Fuchy are what need
it: installing them from the fuchy page, updating them, and the ones whose task is on the internet,
such as the weather ([ADR-0015](0015-darek-takes-on-fuchy.md)).

Android grants `INTERNET` at install time, to every user, with no prompt. The network security
config can hold an app's TLS to a fixed list of hosts (a `<base-config>` with no trust anchors, a
`<domain-config>` per host with the system's), which would cover maksforge.com. It cannot follow the
addresses a fucha declares, which Darek learns only when it is installed, and it does not govern raw
sockets. So once Darek declares `INTERNET`, where he connects is a rule of this code, checked by
reading the source and watching the traffic, not by reading the manifest.

## Decision

**Darek declares `INTERNET` from the phase that builds fuchy** (`docs/PLAN.md`), at its checkpoint D
(`docs/phase-F.md`), the network rules below landing in the same merge. Internal builds carry it
before the privacy policy, the listing and Data safety say so: those change with checkpoint F,
before any build reaches a tester.

**Darek connects to two kinds of address, over HTTPS only, follows no redirect, and connects to
no other:**
- **maksforge.com**, for the fuchy page's `fuchy.json`, an approved fucha's file and its updates,
  and nothing else from it: no model, voice or code download. The request carries nothing about the
  user: no account, no device ID, no analytics. A fresh install connects nowhere until the user
  opens the fuchy page or a fucha that declares an address runs.
- **the addresses a fucha declares** ([ADR-0015](0015-darek-takes-on-fuchy.md)), only while it
  runs, only with the permission the user granted it, and carrying only the values it declares, each
  one shown by name before install.

The network security config sets `cleartextTrafficPermitted="false"` for every host: Android's own
default allows plain HTTP on Android 8, which `minSdk` 26 still reaches.

**Audio never leaves the phone, and neither does anything else Darek heard except a slot's value.**
Audio is consumed frame by frame and never written to storage, and no fucha can declare it as a
value to send. A fucha may declare the value said in one of its slots (*„pogoda w {mieście}"*) as a
value it sends, shown by name before install like any other (*Sends: the city you say*). The rest of
what Darek heard, the Command's other words and anything said around it, is never sent.

**No dependency connects anywhere of its own accord.** ONNX Runtime from 1.29.0 merges a telemetry
provider that posts to Microsoft: it is pinned at 1.28.0, which has none, and the manifest removes
the provider anyway ([ADR-0014](0014-darek-speaks-with-his-own-bundled-voice.md)). Play Billing
talks to the Play Store app, not the network, but merges `datatransport`, a log uploader that today
fails only because `INTERNET` is missing
([ADR-0007](0007-the-tip-is-a-play-billing-consumable-that-unlocks-nothing.md)). The phase that adds
`INTERNET` removes the `backend:com.google.android.datatransport.cct.CctBackendFactory` meta-data
from `TransportBackendDiscovery` with `tools:node="remove"`, which leaves `datatransport` no backend
to upload through, and reads the traffic to prove it. `JobInfoSchedulerService` and
`AlarmManagerSchedulerBroadcastReceiver` stay: `datatransport` still schedules them, and
`JobScheduler` throws for a job service missing from the manifest.
`ACCESS_WIFI_STATE` stays forbidden; `ACCESS_NETWORK_STATE` is allowed as Play Billing's merge.

## Alternatives

**Never connect** (this ADR until 2026-11-01). The strongest promise, checkable from the manifest
alone, but fuchy could then only arrive by hand and no fucha could fetch anything. The owner chose
fuchy installed from the app.

**A companion app holding `INTERNET`**, with Darek handing it requests. Darek's manifest would stay
clean, but every user wanting the weather would install a second app, and the fuchy page would live
in it rather than in Darek.

**Cloud speech recognition.** Better on free speech, but it sends audio off the phone, fails with no
signal at the gate, and cannot run continuously in the background. Rejected for good: it breaks the
one promise this ADR keeps.

**Crash reporting of our own.** Android vitals covers opted-in devices; the mail hand-off in
`ui/support/` covers the rest.

## Consequences

- When the fuchy phase ships, everything that says "no network" today is rewritten to this ADR on
  the same day: `docs/privacy-policy.md`; `docs/store-listing.md`'s short and full descriptions in
  both languages (release notes already shipped stay as they were); `docs/play-app-content.md` and
  Data safety, re-read for the values fuchy send; `README.md`; `docs/index.md` and
  `docs/_config.yml`'s description (the maksforge.com/darek page); `CLAUDE.md`'s Stack row for Play
  Billing; and `SupportHandoff.kt`'s KDoc. ADR-0007's *`INTERNET` stays out* ends then, by its
  amendment. Until then they stay as they are.
- `aab-permissions.py` moves `INTERNET` from FORBIDDEN to EXPECTED in that phase, and
  `ManifestWiringTest` with it.
- A dependency that connects on its own is still not used.
