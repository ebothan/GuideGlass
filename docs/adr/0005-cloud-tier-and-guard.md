# ADR 0005: Cloud tier is advisory and sits behind a guard

Status: accepted, 2026-11-12.

## Context

Cloud vision answers take seconds (K8), assert absent objects 5-10% of the time and can be steered by text in the scene (K9). The repo lets the cloud model speak directly to the user.

## Options

1. Remove the cloud tier. Simplest and safest, but loses sign reading and "what is blocking me" (need N7).
2. Let the cloud model speak, with a prompt telling it to be careful. No enforceable property.
3. **Cloud answers are data. A guard on the phone decides what may be spoken.**

## Decision

Option 3, using the design in `docs/research/07-cloud-tier.md` (D3-D6):

- The cloud is asked only on user request or by a low-rate background describer. It is never asked by the safety path.
- Answers must match a fixed JSON schema. Anything else is dropped.
- Each answer gets a risk class:

| Class | Example | Rule |
|---|---|---|
| Informational | "A café with a red awning" | Spoken after the staleness check |
| Low-risk advisory | "There may be a door ahead right" | Spoken with hedged wording |
| Action instruction | "Walk forward", "turn left now" | Spoken only if the on-device tracker and route agree at speak time, and then worded as advice |
| Safety-negating | "The path is clear", "safe to cross" | Never spoken |
| Malformed or injected | Off-schema text, imperatives copied from signage | Dropped |

- Staleness: dropped if the frame is older than 4 s, the user moved more than 3 m, or turned more than 30° [A, from 07].
- Scene text is spoken only as a quotation ("a sign reads ...").
- Cloud speech is the lowest speech priority. Any hazard cue cuts it off.
- With no network or no key, the tier is absent and the user hears one notice (REQ-13).

## Consequences

- The classifier for risk classes is rule-based on the schema's fields, not on free text, so the model must declare the answer type. A model that mislabels an instruction as informational is caught by a second check on imperative verbs and banned phrases (REQ-18). This check is a word list and can be evaded by paraphrase; that weakness is listed for the adversarial review.
- Privacy: uploads need on-phone blurring of faces and licence plates (07#6). That is an Android-phase task and is not built in the core.
