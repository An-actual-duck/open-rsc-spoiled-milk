# Release validation repairs — F1–F4 and client F3

Reviewed November 1, 2026. Worker: `Core-Framework-ai-2`, branch
`fix/release-validation-gaps`, based on
`8c939950ecac4dbee1a54c1e38b9ef0814303481`.

This closes the assigned verification failures from
[`next-update-code-review-findings.md`](next-update-code-review-findings.md).
The auction repair and `Player.scheduleCombatCast` publication fix belong to the
separate manager-coordinated worker. This branch does not change gameplay
values, item identities, approved artwork, sprite archives, saved maps or server
configuration, and does not activate any server or client.

## Changes and individual dispositions

**F1: current contracts, with compatibility coverage retained.** NPC profile
checks now obtain the current paired client version from the Cleric contract
(10055), while testing the separate expanded-NPC capability floor (10053).
Older incompatible clients, capacity/headroom, synchronized generated contracts
and runtime limits remain checked. The gem fixtures now verify the exact egg and
antidote identities at 3400–3407 instead of requiring retired holes. Their
subsequent renderer, equipment, stats and loot assertions now execute. Cleric
foundation checks compare complete resource vectors, reject duplicate entries,
and cover Stone, Stone+Silver and Silver+Moonsilver costs as well as both neutral
spells. The native metal-sigil transaction fixture independently retains
mixed-vector admission/consumption and insufficient-resource atomicity coverage.

**F2: preserve approved bytes; compose independent content receipts.** The
coating generator copies the approved Aseprite-exported potion PNG bytes directly
instead of re-encoding them with Pillow. No art was regenerated. Once that gate
could proceed, it exposed a second stale layer: its historical archive receipt
predated approved Cleric well textures. The historical receipt remains unchanged;
all its original entries must still exist with identical serialized payloads.
The independent asset check permits only the later entries declared by
`dev/myworld/cleric-wells/installation.json`, and invokes the well installer in
read-only `--check` mode to verify their actual approved payloads. These are
custom texture entries 67–72 and native records 4100–4117 (12 earlier custom
textures made available in classic mode and six new well textures). Unexpected
additions or mutations still fail. Original classic compressed bytes, local
headers, payloads and per-entry ZIP metadata remain checked. This does not refresh
the entire preservation baseline or exempt unrelated content.

**F3: both assigned client warnings fixed individually.** The Slayer coin icon
loader makes one attempt, catches only `IOException`, and emits a single fallback
diagnostic for an unavailable/undecodable image. It does not silently retry every
draw or swallow unrelated runtime errors. Party rendering removes both the
adjacent redundant membership expression and its nested repeated membership
test. The first limited analyzer rerun exposed the latter once the original
expression was fixed. No analyzer configuration or baseline was changed.

The separate Player warning concerns paired lifetime/deadline publication, so
making only its `long` volatile would be insufficient. The manager accepted one
immutable holder published through a volatile reference and assigned that change
to the worker owning `Player.java`. This branch leaves that file untouched.

**F4: test-owned drop logging with awaited teardown.** `CurrentCombatHarness`
offers an explicit, opt-in `recordDropLogs()` boundary. It records calls to the
real production drop-logging path, rejects JDBC access before opening a
connection, and remembers asynchronous violations even if their Future is
ignored. `awaitRecordedDropLogs()` waits on a FIFO logging barrier. Opted-in
teardown gracefully finishes logging and checks the recorded failures before
normal harness cleanup. Other harness users keep their original database and
logging behavior; this is a drop-only fixture boundary, not a replacement for
real auction-persistence tests.

Both gem-loot fixtures opt in and require nonzero recorded production drop logs.
Their success markers were already outside their try-with-resources blocks; the
fix is the completed asynchronous work and guarded database boundary, not moving
those markers. A negative fixture proves that a forbidden JDBC operation fails,
an ignored asynchronous failure remains visible, and cleanup still terminates
the logging executor. Ant now creates a fresh synthetic terrain/configuration
folder for these fixtures, rather than relying on installed worker map state.
Every Java process is checked with `failonerror`; a printed marker alone cannot
make the target pass.

## Verification actually completed

All commands ran offline in the assigned worker. Java used
`JAVA_HOME=/home/justin/jdk8u482-b08`, its `bin` first on `PATH`,
`ANT_OPTS='-Xmx512m -XX:+UseSerialGC'`, and
`JAVA_TOOL_OPTIONS='-Xmx512m -XX:+UseSerialGC'`. Heavy batches were serialized with
`flock /home/justin/.local/share/core-heavy-build.lock`.

| Command | Actual result |
|---|---|
| `bash scripts/build-client.sh` | PASS; repeated after the final Party change |
| `bash scripts/build-server.sh` | PASS |
| `python3 tests/myworld/test-npc-capacity-profile-configs.py` | PASS; both profiles version 10055, capacity 16384, reserve 512 |
| `python3 tests/myworld/test-cleric-spellbook-foundation.py` | PASS, 237 assertions |
| `python3 tests/myworld/test-battleaxe-coatings.py` | PASS through generator, approved wells, Aseprite, archive preservation and both packaged client modes; 192180 client checks per mode |
| `python3 tests/myworld/test-gem-dragons.py` | PASS; both real client modes and all 18 native frames |
| `sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml test_gem_dragons` | PASS with exit 0: JDBC negative guard/teardown, Gem Dragons 232 assertions, guaranteed loot 7380, normal loot 86162 |
| `python3 tests/myworld/test-cleric-metal-sigils.py` | PASS, 206 assertions |
| `python3 tests/myworld/test-party-social-ui.py` | PASS; repeated after final Party change |
| `bash scripts/test-npc-capacity.sh` | PASS entire gate, including builds, production/wire/lifecycle/client fixtures, Slow/Slayer and combat 145/145 |
| `git diff --check` | PASS |

The client-only SpotBugs rerun uses the existing `scripts/lint.py`
`run_spotbugs` function, pinned offline toolchain, analysis Java and unchanged
include/exclude/baseline files, with its in-memory `PRODUCT_ARTIFACTS` restricted
to `client`. This is a focused disposition check, not a claim that the combined
whole-program lint gate has passed. It exited 0: 68 existing findings observed,
no new client findings. No baseline entries were added or removed.

Evidence is preserved under ignored `output/release-validation-gaps/`: command
and exit-status ledgers, full logs, focused analyzer reports and
`unchanged-inputs.json`. Initial failures are retained alongside successful reruns.
The earlier `output/next-update-code-review/` evidence is untouched.

The two native archives match the manager's independent pre-work SHA-256
baseline. `library.orsc`, the historical coating receipt and the installed potion
also match the starting commit byte-for-byte. The full asset gate validates the
approved source potion as well. No account database or network listener was
opened; the opt-in drop paths reject any JDBC attempt before connection.

## Remaining manager gates

Review and integrate the exact READY commit with the auction/Player changes,
then run the unchanged combined lint gate and the release acceptance already
planned for the paired artifacts and final map. This worker did not run public
or private gameplay, real-account database migration/restore, final packaging or
live activation. Those are not prerequisites for this focused validation handoff
and remain explicit release gates.
