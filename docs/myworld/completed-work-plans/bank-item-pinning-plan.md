# Persistent Bank Item Pinning

Status: implemented, visually verified, and merged into `main`

## Goal

Let a player reserve an item type in a particular custom-bank slot. Withdrawing
the last real item leaves a visibly pinned, zero-quantity icon in that slot,
and a later deposit of the same item type refills it. Pin state must survive
logout and restart without making a zero-quantity item part of the player's
owned bank contents.

## Traced architecture

- `Bank` stores real, positive-quantity `Item` instances in one compact list.
  Its count, ownership, withdrawal, presets, trade-related checks, and database
  save path all consume that list.
- Player bank rows persist only real items. The existing player cache persists
  typed per-player metadata and is loaded in the same atomic login operation.
- Custom bank open/update packets currently send slot, catalog ID, and amount.
  Authentic clients use their existing compact bank formats and cannot express
  a persistent empty slot.
- The custom bank UI owns search, item-tag filters, right-click actions, drag
  swap/insert, wealth display, and bank rendering. Filter results retain the
  original bank slot ID.
- Withdraw/deposit packets identify an item type and quantity. Equip-from-bank
  and drag rearrangement are the important slot-indexed paths.

## Chosen design

### Server ownership and layout

Keep `Bank.list` as the sole ownership source. Add separate pin metadata mapping
logical display slot to catalog ID. A generated display layout combines:

- each pin at its reserved logical slot, backed by a real matching stack when
  one exists;
- a metadata-only placeholder when no real matching stack exists; and
- all unpinned real stacks compacted through the remaining slots.

No zero-quantity `Item` is created or saved server-side. Counts, item lookups,
withdrawals, presets, trades, drops, and database bank rows therefore continue
to see only owned items.

A single catalog ID may have one pinned slot. This avoids ambiguous refill
behavior when an integer-overflow stack creates a second real stack.

### Persistence

Store a bounded, versioned serialization of `slot:catalogId` pairs in the
existing persistent player cache. Load and validate it after both bank items
and cache data are available. Invalid, duplicate, out-of-range, or sparse
entries are discarded or normalized before use. Ordinary player saves persist
the metadata in the same transaction as bank rows.

### Capacity

An empty pin consumes one bank slot because it is an intentional reservation.
An occupied pin consumes the same one slot as its real item. Depositing the
matching type into an empty pin consumes no additional display slot. Other new
item types cannot use reserved capacity.

### Rearrangement

Swap and insert operate on the generated display layout. After a successful
operation, real items are rebuilt in visible order and pins are rebuilt at
their new logical indexes. This permits moving both occupied pins and empty
placeholders while preserving item ownership invariants.

### Client protocol and compatibility

Increment the custom client protocol version and add a pin flag to each custom
bank-open entry and custom bank update. Gate these fields on the corresponding
custom-client capability. Authentic and older client payload generators retain
their original compact format and never receive placeholders.

The feature is exposed only by the custom bank UI. Persistent pins remain
server metadata if a player uses an authentic client, whose compact view cannot
display or rearrange them.

### Client behavior

- Right-click an occupied item for `Pin`; right-click a pinned item or empty
  placeholder for `Unpin`.
- On desktop, Shift-left-click toggles `Pin`/`Unpin`. Ctrl-left-click retains
  its existing withdraw-all priority when both modifiers are held.
- Send slot, catalog ID, and requested action so stale menus cannot affect a
  different item.
- Empty placeholders cannot withdraw, quick-withdraw, or equip.
- Pinned entries remain searchable and filterable by their item definition.
- Empty pins render with a faded icon, zero quantity, and pin marker; occupied
  pins retain a pin marker.
- Drag swap/insert is disabled while filters are active, as before, and works
  on pinned entries when the unfiltered bank is shown.

## Tradeoffs

- Full custom-bank refreshes are preferred after pin-aware mutations. They are
  slightly larger than incremental updates but eliminate index drift after
  compaction, refill, unpin, and rearrangement.
- Cache metadata avoids a database schema migration in both MySQL and SQLite
  and keeps item rows semantically pure. Pin durability follows the existing
  player-save lifecycle.
- A protocol bump is required because an older custom client cannot distinguish
  an occupied pin from an ordinary stack. Authentic protocol behavior remains
  unchanged.

## Verification

- Deterministic layout tests: pin, final removal, refill, unpin compaction,
  swap, insert, duplicate/invalid metadata, and capacity accounting.
- Ownership tests: placeholders never affect count/has/withdraw semantics.
- Persistence tests: serialize/load round trip and restart-style reconstruction.
- Client contract tests: packet width/version agreement, Pin/Unpin menu,
  placeholder rendering, search/filter inclusion, and blocked transfers.
- Server and client authoritative builds plus focused My World regression tests.
- Private-server visual testing confirmed occupied pinning, zero-quantity
  placeholders, refill, rearrangement, filtering, empty-placeholder unpinning,
  persistence across restart, and the desktop Shift-left-click toggle. Holding
  Ctrl+Shift retained Ctrl-click's withdraw-all priority.
