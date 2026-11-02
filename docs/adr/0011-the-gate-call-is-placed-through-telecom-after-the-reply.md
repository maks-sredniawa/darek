# The Gate call is placed through Telecom, after Darek has finished his Reply

## Context

A GSM gate opener is driven by calling its SIM: it recognises the caller's number, rejects the call
and opens the gate, so the call costs nothing. Darek's action is therefore a phone call to a number,
placed while no Darek screen is visible — only the listening service is running.

`startActivity(ACTION_CALL)` from there is a background activity launch, which Android 10+ blocks and
a foreground service does not exempt. `TelecomManager.placeCall()` with `CALL_PHONE` goes through the
system's Telecom service, which checks the permission and nothing about visibility.

Play's malware policy forbids making calls without the user's consent; the rule is aimed at
premium-rate fraud, and `CALL_PHONE` is otherwise unrestricted.

## Decision

- **Calls are placed with `TelecomManager.placeCall(tel:<number>)`**, never with an activity intent.
- **The number is one the user typed in** for that Command. Darek ships with no number and never
  reads contacts.
- **This ADR covers Commands that call.** One that only replies
  ([ADR-0013](0013-a-command-may-only-reply.md)) speaks and stops: no cancel window, no call. The
  pause while Darek speaks applies to both.
- **A Command is acknowledged before it acts**: Darek speaks the Reply (*"Otwieram bramę"*) in his
  own voice ([ADR-0014](0014-darek-speaks-with-his-own-bundled-voice.md)) and waits until it is
  **finished**: playback ends, synthesis or playback fails, or a watchdog of the clip's length plus
  2 s fires, so a voice that never finishes cannot leave him deaf. The Reply is also shown, on screen in phase 1 and
  in the notification from phase 2.
- **If his voice fails** (the model does not load, or playback errors), the Reply is only shown and
  is finished as soon as it is shown. **In phase 1** a Command that calls still dials after its
  cancel window: Listening exists only while Darek's screen is visible, so the shown Reply is seen.
  **Phase 2's case is open**: with the phone in a pocket a shown Reply is neither heard nor seen,
  which the consent line below does not cover. Whether a Command that calls may dial there with a
  Reply nobody hears is decided in phase 2, not here.
- A **cancel window** of about 2 s follows, in which *"stop"* or *"anuluj"*, with no name needed, or
  the notification's *Anuluj* action cancels; only then does he dial. Cancel words act on
  **partial** results: one containing *"stop"* or *"anuluj"* cancels at once, because a final result comes only
  after a pause and could land after the dial. They are heard only from the end of the Reply:
  recognition is paused while he speaks, so an *"anuluj"* said over the Reply is not heard.
- **If Listening stops before the dial**, by the user or, in phase 1, by Darek's screen leaving the
  foreground, the dial is cancelled: nobody could cancel it by voice any more.
- **Consent** is the Number the user typed for that Command and the user's own start of Listening:
  that is what Play's malware policy asks for. A false match therefore costs a Reply the user hears
  or sees and can cancel, not a silent dial.
- **Recognition is paused while he speaks and from the dial until the call ends**, so he cannot hear
  a Reply or the call; it runs through the cancel window. The end of the call is read from
  `AudioManager`'s mode (`OnModeChangedListener` on API 31+, `getMode()` below). Because `placeCall`
  is asynchronous, phase 2 resumes recognition only once the mode has left `MODE_NORMAL` and come
  back, or after a timeout. No `READ_PHONE_STATE`.
- **The answer to the name, *„No?"*, is the one line played with the microphone open.** The Persona says it when a partial result
  first holds the name ([ADR-0009](0009-commands-are-recognised-offline-against-a-grammar-with-decoys.md)),
  while the Command is still being said, so pausing recognition for it would cost the Command's first
  words. It replaced a hum and two notes, by the owner's ear (2026-11-02), and unlike them it is
  speech. It is a hint that the name was heard, not a Reply: it acknowledges no Command and is no part
  of the consent above. What it costs recognition is phase 1's R11 to read.
- If dialling fails, he says so (*"Nie udało się zadzwonić"*) rather than staying silent.

## Alternatives

**Dial first, then speak.** Faster by a second, but the user learns nothing until the gate moves, and
the call takes the audio path the Reply needs.

**Call state from `TelephonyCallback` or `TelecomManager.isInCall()`.** Both need `READ_PHONE_STATE`
on API 31+, another runtime permission for something the audio mode already shows.

**SMS-driven openers.** Some openers accept an SMS. SMS permissions are restricted on Play to default
messaging apps; calling covers the openers people actually have.

**Hanging up after a few seconds** with `TelecomManager.endCall()` for openers that answer instead of
rejecting. It needs `ANSWER_PHONE_CALLS` and is deprecated; deferred until an opener that needs it
appears.

## Consequences

- The test phone has no SIM, so the dial is read on the owner's phone, with the owner's go-ahead each
  time (`CLAUDE.md`, environment notes). A dial is a real gate opening.
- Whether MIUI/HyperOS add a check of their own to `placeCall` from a background service is phase 2's
  reading on the owner's phone. Phase 1 dials only with the app open, and only in the debug build:
  `CALL_PHONE` reaches the release with phase 3's Number editor.
- The cancel window adds about 2 s between the Reply and the gate.
- In phase 1 the in-call screen takes the foreground from Darek's, so Listening ends with every
  dial; the wait for the audio mode to return starts mattering in phase 2.
- Dual-SIM phones dial on the default SIM; choosing a SIM per Command waits for someone who needs it.
