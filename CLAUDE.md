# Standing rules for this repo (branch `av-prototype`)

- Resume: `pwd`; read this file, `docs/PROGRESS.md`, `acceptance.json`, `git log --oneline -15`; run `./init.sh`; continue from the "Next" list in PROGRESS.
- All work goes on `av-prototype` in the fork `ebothan/GuideGlass`. Never push to `main` or upstream. Commit small, push often.
- No secrets in git. Keys come from `local.properties` or the environment. Everything must run with no keys (mocks, recorded responses).
- Android SDK and emulator are NOT installed: the user declined on 2026-11-11. Do not install them without asking again. The safety core is a pure Kotlin/JVM module that builds with JDK 17 (`/opt/homebrew/opt/openjdk@17`).
- Python tooling uses `.venv/` (gitignored). Never regenerate the paper figures in place; `init.sh` runs the baseline simulation in a temp copy.
- Evidence rungs on every number: L0 assumed/read, L1 simulated, L2 replayed real footage, L3 emulator/mock device, L4 real device + glasses, L5 supervised BLV users. L4/L5 are `needs_hardware`, never "working".
- Never call this safe or a mobility aid. It is a research prototype that supplements a cane or guide dog.
- Do not edit or delete a test to make it pass; do not hard-code values to pass. Fix requirements openly with an ADR.
- Never guess an SDK signature: quote it from the docs or repo.
- Disk is tight (about 8 GB free). Keep downloads small and delete temporary files.
