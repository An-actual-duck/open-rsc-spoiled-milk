# Auction transaction safety — implementation checkpoint

Status: implementation checkpoint, **not READY**. No live operations, accounts,
owner maps, imports or deployment were performed.

The inherited buy/collect defect is being replaced with an auction-owned JDBC
transaction containing escrow/listing changes, seller claims, player inventory
and bank snapshots, and an idempotency receipt. Each queued request has one
server-generated UUID. Live containers publish only after durable commit.

The transaction owns a physical connection separate from ordinary logging/save
connections. Receipt schema setup occurs before economic transactions. Existing
MySQL participant tables must be InnoDB; no automatic table conversion occurs.
The new additive `<prefix>market_operations` table must be retained with economic
backups. A downgrade must stop auction processing first and preserve this table;
restoring an old backup of only listings or only player containers is unsafe.

Player mutations run as player game events in tick-ordered mode. The experimental
concurrent-player-event mode refuses auction mutations. Both live container list
monitors and the ordinary-save economic boundary span staging/commit/publication.
Unknown commits use a fresh locking receipt read; unavailable resolution or live
publication failure closes and detaches the session, preventing queued stale saves.
Ordinary player-container loading reads the committed state on re-entry.

The implementation also covers create/cancel/moderation/expiry, rejects unsafe
container admission before any ground spill, and returns only the unsold amount
on expiry. Advisory listing snapshots refresh partial purchases regardless of
row count. F3's combat deadline pair is published through one immutable volatile
holder; this does not claim all combat state is thread safe.

Validation in progress: source build passed; real SQLite and MariaDB fixture code
is implemented and being exercised. The fixture uses only newly generated map
runtime/config copies and synthetic database rows, with a network/listener guard.
Initial test corrections concern Java compilation and noted-item assertions; no
passing engine or crash/reload claim is made at this checkpoint. Final evidence,
commands, coverage and limits will replace this status before READY.

Independent review corrections (verification continuing): ordinary saves retain
only the economic boundary, avoiding a new bank/inventory-to-equipment lock
inversion with existing bank equip/unequip/deposit-all paths. Auction transactions
now include current equipment ownership/statuses alongside inventory and bank,
so a recent equip/unequip cannot be lost or rejected because its previous saved
container is stale. Equipment uses its existing `(playerID,itemID)` links and
existing definition-derived slots; no invented slot column is introduced.
Legacy worlds without the optional equipment table remain supported.
