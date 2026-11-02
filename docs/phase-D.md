# Phase D — Daria

The Persona switch of [ADR-0016](adr/0016-daria-is-a-persona-the-user-switches-to.md), built after
0.4.0 and before phase 2. What is in is the ADR's *Decision*; this file holds the readings.

## Readings

| | Reading | How | Result |
| --- | --- | --- | --- |
| D1 | Daria's voice on the test phone: load and synthesis time for each sample | the debug *say this* field with Daria chosen, `adb logcat -s DarekVoice` | **2026-11-01:** load 2.2–2.3 s cold; *„Co tam, mordo?"* 1.2 s of speech in 0.28 s, longer sentences at ~0.18–0.2× real time (9.8 s in 1.8 s); all seven samples ended on the marker |
| D2 | The owner's ear on her voice, and her noise settings | the debug sliders for `noise_scale`, `length_scale` and `noise_w` | **2026-11-01:** at her config's 0.667 / 1.0 / 0.8 she lisps, and each rendering differs. 0.4 / 1.0 / 0.5 sounded better; 0.25 / 1.1 / 0.35 worse. 0.4 / 1.0 / 0.5 ships. *„Co tam, mordo?"* and *„Dziś 22°C…"* still sound odd at every setting tried: shipped as a known gap, fixable by a better voice or a line of her own |
| D3 | Her name and Decoys against real voices: *„Hej Daria, powiedz coś"* fires, *„Hej Darek…"* gets the correction, *Maria*, *Dario* and *Darii* do not fire | Home with Daria chosen, on the test phone, debug 0.6.0, the owner's voice at the phone, `adb logcat -s DarekListen DarekVoice`: the probe logs no correction, Home does | **2026-11-02:** the owner reports it works. The log holds `SAY_SOMETHING`, then `CORRECTED` five seconds later, each ended by the marker, then `NOT_CONFIGURED` 26 s later for her name alone, with no reaction in those 26 s where *Maria*, *Dario* and *Darii* were said. An earlier `NOT_CONFIGURED`, about a minute before these, was not part of the script. One voice, one take of each: `reaction=` names no tokens, so the Decoys' silence is read from the gap |
