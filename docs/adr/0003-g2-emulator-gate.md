# ADR 0003: Gate G2 stays blocked; a desktop end-to-end gate is added beside it

Status: accepted, 2026-11-12.

## Context

G2 requires the app to run end to end in the Android emulator. The user declined installing the Android SDK and emulator on this machine (2026-11-11). The task rules forbid weakening a gate without an ADR.

## Decision

- **G2 is not changed and not passed.** Its status is `blocked`, with the reason recorded in `acceptance.json`.
- **A new gate G2a is added:** the full pipeline runs end to end on the desktop JVM. It uses a mock frame source, on synthetic scenes and replayed logs. Cues are produced, rendered by a logging audio sink, and written to a log.
- G2a is evidence for the decision core only. It says nothing about:
  - the Android lifecycle or the foreground service;
  - the DAT SDK;
  - real audio output;
  - on-device model inference.
- Evidence from G2a is labelled "L3-substitute (JVM, mock device)". It is never reported as L3.

## To unblock G2

Either allow the SDK install on this machine, or run the steps in the hardware-validation checklist (written in P6) on a machine that has Android Studio.
