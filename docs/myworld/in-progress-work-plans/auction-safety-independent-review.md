# Independent auction safety review

Reviewed November 1, 2026 in the manager-assigned documentation-only worker
`Core-Framework-ai-2`, branch `docs/auction-safety-independent-review`, based on
published main `9f787172beeaa50f2d066bccc628b81ab382084a`.

Initial source reviewed: `713bc69eb8e3e8161ffbf8fd6cc6a4a8b7c8388f`, then its
superseding checkpoint `cddcab99c458c591e99fde62526c972426b611e4`. Source was
read with `git show` and exact-commit diffs. No production source, other worker,
database, server, map, account or installed toolkit was changed by this review.

Primary corrections were reviewed at
`b8c2e96289d466fca123bb33f1d49573874ed189`; its first READY
`a37668ee77a27baaa178d2be92b525160b0699ae` added only review/evidence documents.
Core then raised the unresolved-COMMIT login question below, and independent
source review confirmed a third blocker. Its complete correction is reviewed at
`200e804bdbe8a467977afaf682402493368073b8`, superseding that first acceptance.
All three findings are resolved. Fixture completion checks were reviewed at
`c708855e8672df162cabdfef30570546b14b9538`; final auction READY
`5d2e81973df03a98a5b70ea7bc319c7329454ae5` adds only review/evidence documents.
That exact READY is acceptable for manager integration and final combined gates.
Earlier checkpoints should not be integrated by themselves.

## Findings in the reviewed checkpoints

**P1 — ordinary save introduces a bank/equipment monitor cycle.**
`PlayerService.savePlayer` holds the bank and inventory list monitors while
calling the existing full save. Its equipment serialization calls
`GameDatabase.querySavePlayerEquipped` and then `Equipment.get`, acquiring the
equipment list monitor. Existing `Equipment.equipItemFromBank` and the FROM_BANK
unequip branch acquire equipment first and then bank. An ordinary asynchronous
save can therefore hold bank and wait for equipment while the default game event
holds equipment and waits for bank. Experimental concurrent event execution is
not needed for this deadlock. The default bank deposit/withdraw paths already
use bank then inventory; a bank/inventory-only analysis misses equipment.

Requested correction: choose a consistent boundary compatible with existing
equipment, bank and inventory paths, and demonstrate bounded concurrent
autosave versus bank equip/unequip completion using deterministic coordination.
The review did not prescribe adding more nested monitors without checking their
existing acquisition order. In particular, deposit-all-equipment uses bank then
equipment, so reversing the new save locks would not have been sufficient.

**Resolved at b8c2e962:** ordinary saves retain only the economic exclusion
boundary and their original individual container access, rather than holding
bank/inventory monitors through equipment serialization. The auction mutation
is a default tick-owned game event and refuses experimental concurrent-player
events. A bounded fixture coordinates ordinary save against actual bank equip,
unequip and deposit-all-equipment calls; all complete. This removes the new
autosave/game-thread cycle without claiming a general rewrite of legacy monitor
ordering.

**P1 — auction persistence mixes current inventory/bank with stale equipment.**
`MarketTransactions.execute` persists staged inventory and bank using
`MarketJdbcStore.saveContainers`, but does not persist current equipment. If the
last ordinary save has item X in inventory, the player equips X, then an
unrelated auction commits, the new inventory snapshot removes X's old durable
inventory link without writing its live equipped link. A subsequent crash
recovers X in neither container. The later unlinked-status cleanup also removes
its status row; retaining the row alone would not restore the missing ownership
link. In the reverse direction, a recently unequipped item remains linked from
the old durable equipment table and the transaction rejects it as a conflicting
item. This is a snapshot coherence problem even though each SQL operation is
inside a transaction.

Requested correction: include equipment in the same coherent economic snapshot,
ownership validation and durable commit; exercise real equip and unequip since
the previous save, auction settlement, and ordinary fresh container reload.
Respect the production equipment table shape, which does not have an inventory
`slot` column. This correction must be considered together with the monitor
order finding above.

**Resolved at b8c2e962:** the auction commits a coherent union of current
inventory, bank and equipment statuses/ownership. Own stale durable links may
move between those containers, duplicate current identities and foreign
ownership are rejected, and removed-status cleanup checks every active
container. Equipment uses its real `(playerID,itemID)` links and ordinary
definition-derived slots. Legacy inventory-only worlds need no optional equipped
table. Real equip and unequip since the previous save are followed by auction
commit and ordinary reload; rollback preserves the previous coherent durable
state. Precommit and postcommit process-halt/reload checks also include equipment.

**P1 — unresolved remote settlement can outlive the detached session.**
Core's final recovery question exposed a remaining gap in the first READY.
After a COMMIT timeout, JDBC rollback/socket close does not prove that the remote
database has finished committing or aborting. The resolver deliberately uses a
bounded locking receipt read and can time out while the original transaction
still owns its actor-row lock. Detaching the old Player only revokes that object.
Ordinary login then performed plain consistent reads, which can return the old
balances while an InnoDB transaction is pending. Activating a successor from
that snapshot could make stale balances spendable or save them over the eventual
commit. Earlier crash/reload tests used already-settled transactions, so their
success did not cover this window.

**Resolved at 200e804b:** `AccountLoadBarrier` waits on an owned fresh current
actor-row read before constructing a Player or starting its ordinary snapshot.
MySQL uses `FOR UPDATE`; SQLite uses bounded `BEGIN IMMEDIATE`. A timeout or
unavailable database rejects entry; no auction-enabled switch or receipt-table
DDL is involved. The barrier releases its read-only transaction before the new
profile snapshot. `PlayerService` rejects incomplete `atomically` results and
changed account IDs. `LoginRequest` acknowledges success only after the complete
load, and releases validation's reservation on failure.

Compatibility review also caught binary SQLite `username=?` versus existing
case-insensitive `LIKE` loads and use of an initial request hash after accepted
former-name redirection. The correction uses prepared, escaped `LIKE` and the
final validated name normalized exactly like Player construction. Tests seed
noncanonical stored case and a redirect whose initial hash differs. A strict
modern validation-success whitelist now controls load admission before legacy
wire translation, so modern failure 0 or generic failure 100 cannot be mistaken
for old-client success. Denials on both classic/current clients and a positive
authenticated classic load are covered.

## Other paths inspected

The new transaction design stages detached item/container state, commits listing
or claim changes together with durable player state and a request receipt, then
publishes live containers. Listing and claim updates check previous state;
receipt identities distinguish operation kind, actor and payload. Replayed
requests avoid re-executing their mutation. Unknown commit resolution uses a
fresh locking receipt read rather than assuming an exception means rollback.
The schema setup is outside economic transactions, and MySQL participants are
required to use InnoDB. These choices address the original grant-without-settle
failure mechanism.

Reviewed create, purchase, cancellation, collection, moderation and expiry;
quantity/price/ownership checks; exact noted/unnoted delivery admission;
inventory/bank overflow; postcommit cache/notification behavior; removed status
cleanup; instance identity, durability and attributes; stale operation and
queued-save session checks; and receipt retry behavior. Identity-owned
`EntityList.remove(entity)` and conditional username-index removal protect a
successor even though `Player.equals` uses the username. No additional
substantive blocker was found in those paths during this pass.

Production isolated-connection methods were reviewed separately from injected
fixture providers. They retain the configured database/provider and table
prefix. MySQL isolated connections disable implicit automatic reconnect and
bound connection/socket waits; SQLite uses the configured existing database
file and a bounded busy wait. The fixture separately exercises the transaction
store through injected connections and the actual configured factory methods.
The latter checks physical connection/transaction isolation, plus
missing-SQLite-file refusal without creating a replacement file.

## Verification and limits

This reviewer ran preflight/status, exact source/diff inspection and focused
cross-reference searches. A minimal in-memory SQLite check using the tracked
production username-column shape confirmed that a mixed-case stored name matched
the old `LIKE` query but not the first barrier's binary equality query. The
repository-owned seed schema was inspected read-only; no account rows were read.
Existing worker evidence under ignored
`Core-Framework-ai-1/output/tests/market-atomicity/*` was inspected as implementation
evidence and is not relabeled as independently executed tests. The manager owns
the synthetic MariaDB service receipt. No duplicate database service or heavy
test batch was started by this reviewer.

Final worker evidence inspected for production source 200e804b and fixtures
c708855e:

| Engine / evidence directory under ai1 `output/` | Result |
|---|---|
| SQLite, `market-atomicity-42efba9e72` | Test exit 0, 252 assertions; precommit halt 77/recovery 0 (4 assertions); postcommit halt 77/recovery 0 (4 assertions) |
| MariaDB, `market-atomicity-19b0b1d960` | Test exit 0, 252 assertions; precommit halt 77/recovery 0 (4 assertions); postcommit halt 77/recovery 0 (4 assertions) |

The fixtures also exercise failed and replayed operations, uncertain commits,
bounded locking resolution, seller conservation, competing buyers, ordinary
game-event dispatch, queued stale saves, overflow/admission, moderation, expiry,
InnoDB refusal and legacy storage. They use synthetic rows in assigned disposable
databases and deny listeners/unassigned connections. These are meaningful
focused integration tests, not exhaustive crash/fault injection of the whole
economy. Real-account migration, real network sessions, long-running load and
all legacy gameplay save races remain outside this review.

The new login cases hold an actual uncommitted actor transaction across a login
attempt and demonstrate rejection before a profile snapshot or success response.
Both later COMMIT and later ROLLBACK permit a fresh login with their exact
balances, even with auctions disabled. Known-committed publication failure,
incomplete profile snapshots, stored-case/alias compatibility and classic/current
admission are covered. Authentication and non-economic profile fields are
synthetic fixture scaffolding; actual LoginRequest/PlayerService control flow
and production economic reads run against the real engines. Final runner checks
reject unexpected scheduler exceptions as well as bad exits/missing markers;
none appeared in the inspected final logs.

The final worker report also records source build, combat 145/145, Slow 115 plus
adapters and capacity packet checks. Its adjacent
`auction-transaction-safety-evidence.json` records command/result/log/artifact
hashes. This reviewer inspected the exact final document-only delta and both
engine result ledgers/log markers. No production source changed after the
accepted 200e804b review.

Combined lint, paired release acceptance, real-account upgrade/restore and
deployment remain manager gates. This report approves the scoped corrected
implementation for those gates, not live activation.
