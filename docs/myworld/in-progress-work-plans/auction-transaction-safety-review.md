# Auction transaction safety — Core review

Status: **verification in progress; prior READY superseded**. Tested code checkpoint:
`b8c2e96289d466fca123bb33f1d49573874ed189`. The final handoff adds only this
report and its evidence manifest. No server operations, account databases,
credentials, owner maps, imports, integration or deployment were performed.

## Result

Purchases, collection, listing creation/cancellation, moderator removal and expiry
now share an auction-owned persistence boundary. The transaction commits listing
or claim changes, seller proceeds/returns, the player's current inventory, bank
and equipment ownership/statuses, and a durable request receipt together. Live
inventory/bank publication and success notifications follow COMMIT. Failure does
not depend on reconstructing a refund or granting first and saving afterward.

Each queued player request receives one server-generated UUID retained by its
task; retries check its actor/type/request identity. Listing and claim updates
use guarded affected-row checks. The global listing cache remains advisory and
refreshes partial quantity/price changes even when row count stays constant.
Expiry returns only the remaining unsold quantity. Buyer history, automatic UI
refresh and postcommit seller/Discord notifications remain supported.

The transaction owns a separate physical JDBC connection, excluding the inherited
shared logging/save connection from this economic commit. An exception after
COMMIT is resolved by a fresh locking receipt read: MySQL uses a current
`FOR UPDATE` read, SQLite obtains `BEGIN IMMEDIATE`. This waits for the original
transaction rather than trusting an ordinary SELECT that can miss an unfinished
remote COMMIT. Unavailable/ambiguous resolution, or failed live publication,
revokes and detaches the old session; queued old saves cannot overwrite the
committed state or a replacement session. Ordinary provider loading recovers
current equipment, inventory and bank. Login's online check uses the live World
registry, which revocation removes, rather than a stale database online flag.

## Ownership, containers and independent review corrections

Player auction mutations run synchronously as player game events. Experimental
concurrent-player-event mode (`WANT_THREADING__BREAK_PID_PRIORITY`) refuses auction
mutations. Ordinary saves and settlement share an economic boundary. Saves retain
their existing per-container phases, without holding a new bank/inventory monitor
across equipment saving. Settlement's brief synchronous ownership boundary covers
current equipment plus detached inventory/bank staging and commit/publication.

Independent review found two issues in an earlier checkpoint: a new asynchronous
save monitor inversion against bank equip/unequip, and stale equipment ownership
when only inventory/bank were committed. Both were corrected and re-reviewed in
the tested checkpoint. Tests cover actual bank equip, unequip and deposit-all
against an ordinary save; equip/unequip since the last save, rollback, reload and
process crash. Equipment keeps its existing `(playerID,itemID)` storage and
existing definition-derived slots. Legacy worlds without the optional equipment
table also work.

Staging retains real instance IDs, noted/wielded/durability values, opaque item
attributes and bank pin reservations. Surviving live Item identities are retained
on publication. Inventory admission tests the exact delivered representation
(one non-stackable item is unnoted); rejected/full/overflow staging cannot spill
ground items. Container/status rows are written atomically, with removed statuses
purged only after confirming they have no remaining ownership links. Prices and
amounts use checked arithmetic. This preserves the existing catalog-based auction
representation; it does not redesign auctions to trade arbitrary per-instance
custom metadata.

F3's paired combat snapshot/deadline fields now use one immutable holder published
through a volatile reference. Readers capture that holder once. This addresses
the paired-state warning without claiming all combat state is thread safe.

## Schema, backup and rollback requirements

The additive `<configured-prefix>market_operations` table is created on a separate
autocommit connection before any economic transaction; MySQL DDL cannot implicitly
commit an active auction mutation. Identifiers use a validated configured prefix.
Existing MySQL participating tables must be **InnoDB**: players, auctions,
expired_auctions, invitems, bank, itemstatuses, the receipt table and equipped when
enabled. Nontransactional/missing tables fail closed; nothing is auto-converted.
The database user needs permission to create the receipt table, or an operator
must provision the equivalent schema before enabling auctions.

Back up and restore the complete economic state together: player ownership/status
rows, listings, claims and receipts. A downgrade must first stop auction
processing and retain the receipt table. Restoring only listings or only player
containers is unsafe. There is no automatic receipt pruning; future retention
work must preserve retry and backup consistency.

New MySQL-owned connections disable automatic reconnect and use 2-second connect,
3-second socket and 2-second InnoDB lock waits. SQLite uses a 2-second busy wait.
These bound individual failure waits; they are not a whole-operation latency
promise. A database outage can briefly delay the synchronous game event. Other
historical database transaction behavior is unchanged.

## Verification

All heavy commands used `/home/justin/.local/share/core-heavy-build.lock`, Java 8,
`-Xmx512m -XX:+UseSerialGC`. Final commands at the tested checkpoint:

```bash
# Environment: JAVA_HOME=/home/justin/jdk8u482-b08
# JAVA_TOOL_OPTIONS and ANT_OPTS: -Xmx512m -XX:+UseSerialGC
sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml \
  test_combat_strict test_slow test_inventory_capacity_packets
python3 tests/myworld/test-market-atomicity.py
python3 tests/myworld/test-market-atomicity.py --mysql-manifest \
  /home/justin/.local/share/release-review-fixes-20261101/mysql-test-container.json
```

- Full source compilation and combat: **145/145 scenarios**.
- Shared Slow: **115 boundary assertions**, adapters, legacy ranged cadence,
  retained damage/RNG and committed magic deadlines passed.
- Inventory-capacity packet compatibility passed.
- Actual SQLite: **194 assertions**, plus **3** precommit-crash recovery and **3**
  postcommit-crash recovery assertions.
- Actual MySQL JDBC driver against the specifically assigned disposable MariaDB
  11.4.13: the same **194 + 3 + 3** assertions passed.
- Real-engine cases cover all settlement paths, eight precommit failure points,
  replay, seller/buyer conservation, competing buyers, lost COMMIT acknowledgement,
  a resolver blocked by the original uncommitted receipt, resolver timeout,
  publication failure, exact-session rejection, a queued old PlayerSaveRequest,
  notes/full containers/overflow, zero ground spills, pins, current equipment,
  non-InnoDB refusal, classic optional-table compatibility, default lazy market
  construction/game-event task, and configured provider connection isolation.
  Missing SQLite files are rejected without creating substitute databases.
- Child JVMs halt without cleanup at both sides of COMMIT; a separate fresh JVM
  loads the ordinary production equipment/inventory/bank providers afterward.
- The runner requires the completion marker as well as exit status, preventing
  application logging/uncaught-handler behavior from producing a false pass.
- Server-only unchanged SpotBugs check passed at predecessor `cddcab99c` (314
  existing findings; no new findings). Core owns final combined builds/full lint
  on integration with the separately reviewed validation/UI repairs.

Retained final local evidence:

- `/tmp/auction-regressions-final.log`
- `output/market-atomicity-2af6bd6c57/` (SQLite)
- `output/market-atomicity-bf8ae2cec8/` (MySQL/MariaDB)
- `output/auction-server-spotbugs/` (predecessor analysis)

`auction-transaction-safety-evidence.json` records the final commands, result
manifests, log hashes and artifacts. Synthetic fixture data remains ignored local
review evidence, not game content or account input.

Limits: no socket/password login or private/public gameplay was run. Recovery
exercises actual ordinary provider container loading and exact registry/session
cleanup. JDBC failure checkpoints and transaction-lock tests are not a claim of
exhaustive network/storage fault testing or general database correctness. External
notifications were source-reviewed and are emitted only after commit; no Discord
service was contacted. No lint baseline or production content values changed.

## Successor-login correction in progress

Final manager review identified that a timed-out receipt resolver cannot prove
the original remote COMMIT has finished. AccountLoadBarrier now waits on a fresh
current actor-row lock before Player construction or an ordinary load snapshot.
It applies after restart and with auctions disabled, uses the same canonical
username as Player loading, and never creates receipt tables. Timeout/unavailable
settlement denies login. LoginRequest now sends success only after a complete
load, removes its validation reservation on failure and sends the existing retry
response; a false atomically result never returns a partial Player. Updated
real-engine proofs and final evidence are being completed.
