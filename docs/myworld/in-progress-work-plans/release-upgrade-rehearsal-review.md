# Offline release upgrade and rollback rehearsal

2026-11-01. Core review evidence; no public activation, listeners, account
authentication, or gameplay acceptance is claimed by this rehearsal.

## Result

The v0.2.89 server's consistent SQLite snapshot successfully loaded and saved
through both the actual old runtime and the prepared next-update runtime. All
60 existing characters were exercised, together with one isolated synthetic
character holding the four legacy Neutral/Grace sigil IDs. The candidate then
passed fresh-JVM reload/save idempotence, controlled Slayer progression and
restart, and a complete old-server backup restoration. No production migration
defect was found.

The sanitized machine-readable receipt is in the adjacent
[`release-upgrade-rehearsal-evidence/summary.json`](release-upgrade-rehearsal-evidence/summary.json).
The Java phases performed 1,198 assertions; the Python driver additionally
compared the actual persisted economic/skill/authentication rows and verified
the immutable inputs and complete restored file inventory. Private database
copies, per-character receipts, and raw logs remain outside the repository in
the manager-supplied private release-preparation directory.

## Exact tested inputs

- Old source: `a5043328f99995f1e77681b5b06f84dac72a9f9c` (`v0.2.89`).
- Candidate source: `0eea4c88a4cb23fec4a048f8d357196565a4c196`.
- Old paired map: `f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75`.
- Candidate paired repair map: `e343c03b026920ab0f6dd6e9fdd8d9601d1992177f24510f1d21a266e22e58cf`.
- Old and candidate `core.jar`, `core-gameplay-overlay.jar`, and `plugins.jar`
  were checked against the manager's recorded SHA-256 receipts before use.
  Each JVM used its matching definitions, SQL queries, capability/configuration
  files and selected map. The archive hashes are in the sanitized receipt.
- The SQLite snapshot was read-only and integrity checked, and its hash and all
  frozen old-runtime file hashes were unchanged after the run. Only disposable
  clones were opened through production persistence.

## What was checked

The fixture constructs `Server`, loads real definitions and native terrain and
placements, and installs the actual validated Slayer catalog. It never calls
`Server.init`, `Server.start`, `World.load`, or `LoginRequest.process`.
`World.load` is deliberately avoided because it starts the market timer. The
profile reads and writes themselves use the unmodified production
`PlayerService` and SQLite implementation, including account settlement checks,
atomic save transactions, equipment/inventory/bank/preset decoding, skills,
cache, Slayer migration and layered-location restoration. A proxy channel
supplies a local address without opening a socket.

The following comparisons passed for all existing characters:

- Actual item-instance ownership, catalog IDs, quantities, notes, wielded state
  and durability, including every persisted `itemstatuses` row. Coins are covered
  by the same per-owner, per-instance comparison.
- Typed inventory, bank, equipment and bank-preset receipts, including slot
  positions, against the old build and after a candidate restart.
- Existing auction/claim rows and authentication-related fields, without
  displaying or exporting them. The actual new `MarketJdbcStore` additive
  receipt-table DDL ran successfully; it created no market operation receipts
  and performed no trade.
- Slayer rank, balances, introduction state, completed-task count, inventory
  entitlements and legacy migration metadata. Roster version migrated from 1 to
  3; no existing active task required retirement in this snapshot. All Slayer
  and layered-location cache entries were stable after the candidate restart.
- Skills against the old build's semantics. The old runtime itself normalizes
  the retired hidden Firemaking skill to level 99 for eight existing profiles.
  The candidate produces the same result. No other existing skill columns
  changed in this normalization.
- Positions: 58 existing logical locations were identical. Two characters had
  packed underground coordinates and no layer cache; the accepted bootstrap
  correction now retains level -1 instead of the old erroneous level 0, with
  original X/Y unchanged. These are explicitly checked against the original
  stored packed band, rather than allowing arbitrary location differences.

The old runtime also canonically rewrites bank-preset blobs and supplies missing
default preset rows. Both builds produced identical persisted blobs and typed
presets. These inherited normalizations are distinguished from candidate
regressions; exact whole-database byte equality after a production save is not
claimed.

The real snapshot had no legacy Neutral/Grace stocks. The separate synthetic
probe supplied IDs `3299`, `3300`, `3307`, and `3308` in known quantities. The
candidate resolves those same IDs to the current Grace definitions, preserves
the instances and quantities, and retains them after restart. On that probe,
the real Slayer introduction/mandatory-assignment/eligible-kill transitions also
survived another fresh-JVM load/save. No owner's progression was manufactured.

## Rollback boundary

An old-source-only downgrade is unsafe. The rehearsal loaded a copy of the
upgraded database with the frozen old server and deliberately **did not save
it**: all 61 profiles had unsupported forward Slayer state. A successful basic
profile load is insufficient because Slayer initialization can quarantine the
newer state instead of rejecting the whole login.

The supported recovery demonstrated here restores the complete matching
**database, server jars/libraries, configuration/definitions/queries, capability
files, and both old map halves**. The restored file inventory matched the
backup before execution, and the old runtime reproduced the old typed profile
and persisted economic receipts afterward. Restoring only the source, jars,
selected tables, or the map is not this rollback.

**A full backup rollback discards every change made after the snapshot:** XP,
items/coins, trades, Slayer progress, account changes and other saved state.
The synthetic post-upgrade Slayer progression was intentionally absent after
the verified restore. Reconciliation of later progress is a separate operation,
not an automatic merge into the old database.

This fixture did not contain or launch an old client archive and does not prove
client connectivity after rollback. Preserve the verified old Java/Windows
release packages separately and use the matching old client for an old-server
restore. The updater's downgrade guard intentionally does not automatically
replace a newer client with an older one.

## Reproduction and remaining gates

Run `tests/myworld/test-release-upgrade-rehearsal.py --help` for the explicitly
supplied snapshot/runtime/candidate/map inputs. The runner checks the snapshot's
read-only mode and hash, creates its work under the private input parent with
restricted permissions, and acquires
`/home/justin/.local/share/core-heavy-build.lock`. JVMs use Java 8, a 512 MiB heap
and Serial GC. A SecurityManager denies listener creation, outgoing connections
and writes outside that phase's disposable runtime. Errors point to private
logs rather than emitting account data. No live-server directories, credentials
or databases are discovered or opened by the runner.

The rehearsal is an offline persistence check, not a gameplay/network/load test.
It bypasses password authentication and world registration and does not advance
combat, market expiry, concurrent events or normal game ticks. Existing focused
auction/Slayer/combat fixtures cover their separate behavior. Final private
paired-client gameplay, the owner's icon choices and final release checks remain
manager gates. A fresh complete backup and separately authorized public
maintenance are required for live activation; this task did not authorize or
perform a public shutdown, deployment or migration.
