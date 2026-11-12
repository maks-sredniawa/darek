# Listening is a microphone foreground service, and only the user starts it

## Context

Darek is only useful hands-free, so he must hear a Command with the screen off and the app closed.
Android allows that in one way: a foreground service of type `microphone`, with its ongoing
notification.

Android limits **when** that service may start. Since Android 14 (targetSdk 34) a microphone service
cannot be started from the background at all — not from `BOOT_COMPLETED`, not from an alarm, not by a
service the app already runs. The exemptions are: an activity of the app is visible, the user tapped
the app's widget or one of its notifications, a system component or a visible app's `PendingIntent`
started it, or the app is the phone's assistant (`VoiceInteractionService`), a device owner or
privileged. A Quick Settings tile is not documented as an exemption; AOSP binds the tile without the
privileges the check needs, so a tile must open an activity that starts the service. On API 30–33 the
same background start is allowed but the microphone records silence (Android 11's while-in-use rule);
on 26–29 there is no such rule. Sources:
[background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[Android 15's boot restrictions](https://developer.android.com/about/versions/15/behavior-changes-15),
and for the tile, AOSP's `TileLifecycleManager` and `ActiveServices` — read, not yet measured.

Once running, a foreground service keeps its process out of Doze's wakelock cull, and the audio server
holds its own wakelock while capturing, so listening continues with the screen off.

## Decision

- **Listening** is one foreground service, type `microphone`, with an ongoing notification that says
  Darek is listening and offers *Stop*.
- **It starts only from a user action**: the switch in the app, the notification's *Słuchaj* action,
  or the Quick Settings tile through a translucent activity, opened with
  `startActivityAndCollapse(PendingIntent)`. The `Intent` overload throws for targetSdk 34+ on API
  34+, so it is used only below 34.
- **After a reboot it does not start by itself.** A boot receiver posts a notification whose action
  starts it — one tap, an exempt start. The receiver declares `RECEIVE_BOOT_COMPLETED` itself
  (phase 2), since WorkManager's merged one leaves with the reminders in phase 0.
- **MIUI and other vendor ROMs** get a setup step for autostart and the battery saver's "No
  restrictions", because they kill foreground services the platform would keep.
- **What the microphone is doing is shown, not hidden**: the notification says Darek hears, is
  paused for a call, or cannot hear. *Cannot hear* is more than a second of exact zeros outside a
  call (another app took the microphone) or a recording that cannot start, which is tried again
  rather than ending Listening ([phase-2.md](../phase-2.md), section 3).

## Alternatives

**Listen only while the app is open.** No permanent notification, little battery, and no special Play
declaration — but it is not the app the owner asked for. Rejected on 2026-10-31.

**Be the phone's assistant** (`VoiceInteractionService`), which is exempt and could start after boot.
It replaces the user's Google assistant, needs the user to change the default assistant in system
settings, and is a much larger API for one action.

**Start on boot anyway** through a loophole (an alarm that opens an activity, an accessibility service).
Each is either refused on current Android or a Play policy violation.

## Consequences

- Play Console needs the **foreground service declaration** for `microphone` (use case: voice commands
  for an assistant, nothing saved) with a **video** of the user starting it, and a **prominent
  disclosure** in the app right before the `RECORD_AUDIO` request.
- The notification stays while listening: the platform's price and the honest signal that the
  microphone is open. On Android 14+ the user can swipe it away while the service keeps running;
  Darek does not repost it, and Listening stays visible in the app and on the tile.
- In the background as on screen, only a Command or a hail with *„hej"* gets an answer (ADR-0009):
  the name alone, from a TV or a pocket, gets silence.
- Battery cost is real and has to be measured over a night on the test phone before the door.
