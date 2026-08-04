# Cleric Spellbook Implementation Plan

## Status

- Branch: `feat/cleric-status-hud-extension` (C08B implementation)
- Governing design: [`cleric-spellbook-concept.md`](cleric-spellbook-concept.md)
- Completed milestones: **C01 — definition catalog foundation; C02 — sigil item and asset identities; C03 — Holy Power equipment foundation; C04 — Blessing skill platform; C05 — sigil carving and altar blessing; C06 — Cleric spellbook transport and presentation; C07 — shared support targeting, atomic cast transaction, and Unify; C08A — typed transient effect state and lifecycle foundation**
- Current milestone: **C08B implemented from published `main` at `ca9ac4576`; automated verification and private presentation acceptance remain before READY handoff**
- Next planned milestone: **C09 — low-risk support effects, only after C08B is accepted and integrated**
- Runtime exposure: **Holy Power equipment, Blessing skill state, stone/silver sigil production, maintained-client Cleric catalog presentation, and party-only Unify with its blessed-neutral-stone cost; C08A's registry remains empty, while C08B presents only already-authoritative status snapshots and does not enable a new spell effect**
- Public-server work: **forbidden**

This plan orders implementation of the confirmed Cleric concept without
silently deciding its open design questions. The concept remains authoritative
for player-facing behavior and balance. This document owns implementation
sequencing, dependencies, verification, and stop conditions.

## Global Boundaries

- Preserve stable `Prayer`/skill-ID compatibility while presenting `Worship`
  to players.
- Do not add Cleric spells to the legacy `Spells` enum by ordinal. Cleric
  identities require their own explicit stable keys and numeric codes so future
  Magic additions cannot renumber them.
- Do not expose a spell, production action, skill, packet, or interface until
  every decision required by that surface is settled.
- Cleric casting never awards Worship XP. Worship remains offering-trained.
- Sigil carving belongs to Crafting; altar conversion and blessed-equipment
  production belong to Blessing.
- Casting never drains Devotion directly. Blessed sigils embody the Devotion
  paid during production.
- Do not introduce Holy Defense or move god-spell damage away from the existing
  Magic Power/Magic Defense authority.
- Keep authentic-client behavior safe. Custom presentation must be optional;
  server gameplay state must never depend on a custom HUD packet.
- Do not alter, restart, or deploy the public server from an implementation
  branch.

## Remaining Decisions by Later Milestone

C07 is no longer design-blocked. Its cast transaction spends one complete
sigil vector only when at least one eligible recipient receives a useful
application; an entirely ineffective cast spends nothing. Partial success is
valid, an equal-strength refresh is useful, and resource removal plus every
successful application commit atomically. Unify clears each affected
recipient's queued walking before applying up to two ordinary,
collision-checked steps. Respite joins the natural regeneration clock without
resetting it or granting a free immediate tick.

No C08 design question remains open. Its mixed HUD ordering, state ownership,
typed magnitude/rank model, replacement, origin transfer, lifecycle, wire
extension, fallback icons, labels, overflow behavior, and compatibility
boundaries are settled below. This planning completion does **not** authorize
runtime implementation; wait for an explicit owner instruction.

The following remain later design work and may not be guessed:

1. Future offensive god-spell expansion beyond the existing Mage entries;
   existing god spells remain under Mage for the initial rollout.
2. Additional Devotion sources for later economy tuning; the initial confirmed
   sigil-production economy may proceed without inventing them.

## Ordered Implementation Sequence

### C01 — Definition Catalog Foundation

Create a server-side, content-neutral metadata source for the confirmed launch
roster:

- explicit stable code and key for all twelve Cleric spells;
- display name, alignment, Worship requirement, spell/resource tier, standard
  radius, caster-exclusion flag, and primary sigil vector;
- confirmed Holy Power threshold ladder and pure rank resolution;
- immutable collections, defensive copies, unique-key/code validation, and
  bounded launch-tier cost arithmetic;
- compiled fixture covering the complete roster, lookups, cost totals, radius
  rules, threshold boundaries, and rejection cases.

This milestone must not:

- register startup data;
- add packet handlers or modify `Spells`;
- expose a Cleric tab or casting action;
- add effects, items, production, Blessing, or persistence;
- decide any open casting/resource rule.

Acceptance gate:

- catalog tests and full server build pass;
- changed-code static analysis passes;
- a repository guard proves no gameplay handler or startup path references the
  new catalog;
- the exact pushed commit is independently useful to later spell work.

### C02 — Sigil Item and Asset Identities

Allocate stable item IDs and definitions for the confirmed four alignments,
two materials, and unblessed/blessed states. Preserve the eight supplied source
sprites from `/home/justin/Core-Framework/output/sigils` under a maintained
`dev/myworld/assets/sprites/items/inventory-ground/resources/sigils/` source
folder. Generate silver variants from the same symbols with a silver treatment
and smaller inventory footprint, without redrawing their religious marks.

This slice may make inert items visible to development clients, but it must not
add carving, altar conversion, spawning, shops, drops, spell consumption, or
other acquisition. Server/client item definitions, client asset lookup,
packaging, item-count bounds, and fallback behavior must agree. Confirm exact
names and derived silver dimensions during asset review; do not infer silver
production inputs or quantities.

Verification:

- item-ID integrity and client/server definition parity;
- PNG alpha/dimension checks and packaged-resource presence;
- classic fallback behavior when an external PNG is unavailable;
- client and server builds;
- private inventory/ground-icon inspection if inert development spawning is
  authorized.

Compatibility note: the legacy monolithic `ItemId` enum is already at the JVM
64-KiB generated-method limit and cannot safely accept the sixteen new enum
entries. `ClericSigilItemId` therefore owns their explicit stable numeric
identities, material, alignment, and blessing state. `ItemId.maxCustom` remains
the authoritative exclusive item-count boundary. Later Cleric code must use
the focused identity catalog rather than relying on enum ordinals or assuming
that every custom definition can be added to the legacy enum.

### C03 — Holy Power Equipment Foundation

Add Holy Power as an equipment-derived server stat and a client-displayed
equipment value. Map blessed staff tiers to the established full staff-power
ladder (`8/12/16/24/28/32/40/44/48/56`) and god staves to the tier-eleven
value `64`.
Ordinary Magic staves and blessed armor contribute zero. Any aligned blessed
or god staff empowers ordinary Cleric support regardless of spell alignment.

Synchronize blessed-staff Magic Power to exactly half its comparable ordinary
staff (`4/6/8/12/14/16/20/22/24/28`) and all god staves to `28`. Keep existing
offensive god spells on Magic Power and Magic Defense.

Verification:

- all three blessed lines and three god staves have parity;
- no-staff and ordinary-staff Holy Power remain zero;
- Magic Power comparisons and client display match the authoritative server;
- equipment mode compatibility, equip/unequip, relog, and definition tests;
- private equipment-panel inspection.

### C04 — Blessing Skill Platform

Add Blessing as a real original-curve level `1-99` skill. Append it after all
existing internal skill identities, while sorting its player-facing stats-tab
placement alphabetically. Existing and new records begin at level `1`, zero
XP. Persist current level, maximum level, XP, and cap date; include Blessing in
skill totals, overall/per-skill highscores, dynamic command lookup, the empty
skill-guide platform, general non-combat XP modifiers, and the production-skill
Mind-necklace XP family. Do not add a cape, potion, guild, production handler,
or high-tier content, and do not change combat level.

The migration contract is append-only. Add defaulted columns through both
database dialects and templates. Extend only the maintained custom stat packet
after Summoning and advance its enforced client version. Quest Points remains
a separate byte after all skill fields. Legacy/authentic packet generators are
unchanged. Do not use Enchanting/RuneCraft aliases as an implementation
shortcut.

Verification must cover the original curve, identity/order, existing/new
record defaults, save/load round trips, Quest Points packet separation,
legacy-packet non-change, totals, highscores, commands, skill selectors,
applicable XP modifiers, and the absence of C05 production or new cape/potion/
guild content. Build both client and server, run changed-code analysis, and
privately inspect the stats panel, Quest Points, skill total, guide/hiscores,
and a relogged Blessing value before handoff.

### C05 — Sigil Carving and Altar Blessing

Implement chisel-driven Crafting selection and full-inventory altar conversion
using the now-complete C05 production contract. Reuse the existing Crafting
selection/quantity flow.
Each base carving action consumes one Rune stone or one Silver nugget and
produces one corresponding unblessed sigil; alignment does not alter that
quantity. Unblessed sigils are non-stackable so inventory pressure preserves
the intended resource-to-altar production loop; blessed outputs are stackable.
Stone requires Crafting/Blessing `1/1`; silver requires `20/16`.
Use centralized altar god identity and the existing transaction pattern.
Aligned sigils require their matching altar; neutral sigils accept any god
altar and charge that altar's god while producing one neutral item identity.
The player's selected Worship alignment is irrelevant. The exact starting
balance must be above `-1000`, and the full cost must fit without crossing
below it. Ending exactly at `-1000` is valid; clamping an underfunded batch is
not. Success feedback identifies the charged god and its exact remaining
balance.

Base `1x` XP per successful input is `5` Crafting plus `5` Blessing for stone,
and `10` Crafting plus `10` Blessing for silver, awarded at their respective
carving and conversion steps. Only conversion applies diminishing bonus-output
XP; carving XP remains fixed per input.

Carving is an interruptible per-item batch: each completed step consumes and
adds atomically, completed steps survive interruption, and an incomplete next
step changes nothing. `All` is server-clamped to eligible source materials;
the required chisel leaves room for at most 29 non-stackable inputs in a
standard inventory. Altar conversion is one immediate atomic transaction over
all carried unblessed sigils of the exact selected material/alignment identity.
Precompute every requirement, the total Devotion charge, duplicated output,
XP, and capacity before mutation. Failure changes nothing and must not drop
overflow. A 30-input conversion is supported after the chisel is banked.

Keep `devotion_<god>_offerings` as the compatible whole-offering cache value
and add a signed per-god half-offering remainder (`-1`, `0`, or `1`). Missing
remainders mean zero. Perform all Devotion adjustments through centralized
exact half-unit arithmetic, normalize both fields after each mutation, clamp
the combined exact value, and clear the remainder on an explicit absolute
level-set. Never use floating point or rescale an existing cache value. One
sigil costs one half-offering unit: `1/2/29/30` inputs cost exactly
`0.05/0.10/1.45/1.50` displayed Devotion.

Change the maintained custom Devotion packet's value semantics from whole
displayed levels to signed half-offering units while preserving its opcode and
two-byte width. Advance the enforced maintained-client version and format the
client value precisely (for example, `9.95`). Do not change authentic or legacy
protocol generators.

Verification must cover 1, 2, 29, and 30-item batches, exact cumulative cost,
insufficient Devotion, inventory replacement failure, neutral altars, all
three aligned altars, wrong-alignment rejection, a one-sigil success from
`-999.95`, a multi-sigil atomic failure at that balance, rollback, exact
client display, relog, and no Worship XP.

### C06 — Cleric Spellbook Transport and Presentation

Add the independently identified Cleric catalog to server/client transport and
a maintained-client spellbook view without changing Magic ordinals. Rename the
existing Magic top-level tab to Spells, add Mage and Cleric subtabs, default a
fresh session to Mage, and remember the selected inner subtab afterward. Keep
Prayer and Summon as top-level peers. Show Worship gates, primary sigil vectors,
alignment, and effect descriptions from server-authoritative metadata rather
than a second hand-maintained table.

A Cleric icon click sends one immediate stable-code request. It must never set
the legacy target-selection or autocast state. The server validates maintained
MyWorld protocol, stable identity, Worship level, and the initial no-PvP rule.
C06 deliberately stops with a bounded unavailable message after validation:
C07, not this presentation branch, owns recipient selection, sigil
consumption, cooldowns, and effects. This makes the confirmed interface
inspectable without guessing the unresolved partial/empty cast transaction.

Advance the coordinated maintained-client version and add a custom-only
catalog packet. Authentic generators and the legacy Magic enum remain
unchanged. Spell definitions may carry an optional caster icon and animation;
both default to absent and dispatch only through opt-in hooks, so no final
asset is required.

Expand the maintained-client status snapshot from `16` to `32` entries. Keep
server collection bounded at `64`, append an omitted-entry count to the custom
packet, and render a visible `+N more effects` notice. Preserve the existing
entry prefix and server authority. Do not choose mixed-effect priority or add
charge/pulse fields yet; those remain C08 work.

Verification must cover stable server/client identity parity, all twelve
levels/costs/descriptions, Mage initial and remembered-subtab behavior,
immediate request semantics, Worship and PvP rejection, classic-client safety,
no Magic spell drift, optional visual-hook no-op/dispatch behavior, status
capacity/overflow, server/client builds, changed-code analysis, and private
icon/text-layout inspection. Stop before READY handoff until the owner accepts
the private presentation.

### C07 — Shared Support Targeting and Cast Transaction

Implement caster-centered square/Chebyshev party resolution, self exclusion,
world-space and signed-layer equality, per-recipient spell line-of-effect, and
one server-authoritative cast transaction. Standard radius is `tier + 1`;
Unify is radius four. Spend one full cast vector exactly once when at least one
recipient receives a useful application, including an equal-strength refresh.
Allow partial success and skip ineffective recipients; if every recipient is
ineffective, spend nothing. Preflight and commit sigil removal and all
successful applications as one atomic transaction. Preserve the C06 no-PvP
boundary. For Unify, clear an affected recipient's queued walking before
applying up to two ordinary, collision-valid server-authoritative steps.

### C08 — Shared Cleric Effect State and HUD Extension

#### Objective and hard boundary

C08 establishes the transient authority later support spells need and extends
the shared maintained-client status presentation. It does **not** implement a
spell effect. At the end of C08, Unify remains the only reachable Cleric
support action; Mend, Fervor, Purify, Restore, Ward, Greater Mend, Zeal,
Thorns, Aegis, Rally, and Respite retain their C07 unavailable response.

C08 must not:

- change a Cleric spell's magnitude, rank threshold, duration, recipient, sigil
  cost, or PvP boundary;
- consume sigils for an unavailable effect or introduce a test-only live cast;
- add persistence/cache keys, database fields, login restoration, or party
  persistence for transient effects;
- implement healing, accuracy, damage, reflection, lifesteal, protection, or
  regeneration behavior in combat paths;
- rename opcode `152` or remove its legacy potion-prefix compatibility;
- add caster bubbles or animations before their separate artwork review, or
  map any spell icon beyond the approved Atelier Pixerelia set (Respite retains
  its safe sigil fallback);
- send custom status state to authentic clients; or
- touch, restart, or deploy the public server.

#### Authoritative effect-state model

Each recipient owns one bounded, transient `ClericEffectRegistry`. It is keyed
by exclusive family rather than display text, item ID, cache key, or mutable
party object:

| Family slot | Launch identities | Dominance within slot | Counter kind |
| --- | --- | --- | --- |
| Healing pulses | Mend, Greater Mend | spell tier first, then rank | `PULSES` |
| Accuracy | Fervor | rank | `NONE` |
| Protection | Ward, Aegis | spell tier first, then rank | `CHARGES` |
| Damage | Zeal | rank | `NONE` |
| Reflection | Thorns | rank | `NONE` |
| Lifesteal | Rally | rank | `NONE` |
| Passive regeneration | Respite | rank | `NONE` |

Different family slots coexist. This table preserves the already confirmed
Mend/Greater Mend and Ward/Aegis exclusivity while preventing later handlers
from inventing local replacement flags.

Create one authoritative typed rank definition per timed Cleric spell/rank.
The common portion owns stable spell identity, family, one-based rank,
duration, counter kind, and initial counter where applicable. Spell-specific
immutable magnitude types own meaningful fields rather than an unlabeled
integer array or string map: heal per pulse, upward-roll chance, damage bonus,
reflection rate, fixed reduction and protected-hit count, lifesteal and ending
Hits threshold, or passive-regeneration speed. The typed definitions generate
both the catalog presentation metadata and the immutable magnitude snapshot
installed in a registry. C09/C10 handlers must later consume these same
definitions; they may not create a second gameplay table.

An active registry entry combines that immutable definition snapshot with:

- monotonic application and expiry deadlines;
- optional remaining counter state with no underflow;
- the originating caster's opaque session token; and
- both caster and recipient opaque party-membership-generation tokens for the
  exact membership tenure in which the cast succeeded.

Use a testable monotonic clock abstraction because this state is never
persisted. Do not base expiry behavior on player-cache timestamps. Registry
snapshots are immutable and bounded to the seven family slots. Mutations and
snapshots use one private registry synchronization boundary; cleanup must not
hold two recipients' registry locks at once.

Replacement produces an explicit result suitable for C07's useful-application
contract. A higher spell tier wins before numerical rank in the two shared
families; within one spell, higher rank replaces lower rank. Equal identity and
rank refreshes the full duration and normal full counter without adding any
remainder. Every accepted install, replacement, or refresh atomically adopts
the newest caster session and membership origin. A rejected weaker application
changes no timer, magnitude, counter, or origin and remains ineffective for
resource spending.

#### Membership and lifecycle integration

Add an opaque membership-generation token beside current party membership. It
is renewed on every join or login reattachment, including rejoining the same
`Party` object, and cleared when membership ends. It is not the database party
ID or Java `Party` object identity.

Integrate cleanup at the existing convergent boundaries:

- a genuine recipient death clears only that recipient's registry;
- recipient logout clears that registry and restores nothing on relog;
- before leave, kick, logout, or another membership transition commits, clear
  the departing recipient's registry and remove entries originating from that
  caster membership from each remaining online recipient;
- caster death alone does not clear bounded blessings from living recipients;
  later logout or party departure does; and
- every pulse, counter use, combat/regeneration lookup, and HUD snapshot first
  validates expiry, caster session, and both live membership-generation
  tokens, removes invalid entries, and only then exposes behavior or display.

`Party.removePlayer` is the current shared leave/kick/logout path and
`Player.killedBy` is the guarded real-death path. The implementation may add a
small lifecycle collaborator, but it must keep those established authorities
and must not spread cleanup calls across individual commands or coordinate-
specific handlers. Defensive validation covers abnormal disconnect ordering;
do not add a periodic world-wide cleanup sweep.

#### Mixed status inventory and stable presentation order

Replace the potion-only internal snapshot record with a bounded generic status
snapshot while retaining compatibility facades where names are externally or
protocol sensitive. Presentation priority is metadata, not gameplay state.
The exact launch ordering is:

| Priority | Stable authored identities, in order |
| ---: | --- |
| 1 — finite tactical | `cleric:healing_pulses`, `cleric:protection` |
| 2 — short combat | `potion:brawn`, `potion:deftness`, `cleric:fervor`, `cleric:rally`, `potion:stat_reduction_protection`, `cleric:thorns`, `cleric:zeal` |
| 3 — longer combat support | `potion:magic_resistance`, `potion:melee_resistance`, `potion:poison_protection`, `potion:ranged_resistance`, `potion:regeneration`, `cleric:respite` |
| 4 — utility/skilling | `potion:insight`, `potion:insight_skills`, `potion:luck`, `potion:notation`, `potion:skiller`, `potion:speed`, `potion:warrior` |

Only one identity can occupy each Cleric family slot, so the concrete Mend or
Greater Mend and Ward or Aegis identity inherits its family's position. The
server never sorts by remaining time, application time, origin, or source type.
A new future status defaults to the bottom group in development and must gain
an explicit stable identity/order before release. Do not allow an unknown
status to silently obtain tactical priority.

Collect the known bounded potion and Cleric sources, select the first `32`
entries, and report the exact number omitted from that bounded inventory. The
existing `64`-entry server collection ceiling remains. Normal launch state is
well below it, but tests must fill the bound so presentation behavior is not
accidentally coupled to that expectation.

#### Catalog and status wire contracts

Advance the server-fed Cleric catalog schema in coordination with the client.
For each timed spell it carries typed per-rank magnitude, duration, initial
counter, and presentation-kind metadata. Instant Purify/Restore and movement-
only Unify must not pretend to create timed statuses. The client formats these
received values; it does not own an independent magnitude table or receive a
free-form status string on every update.

Keep opcode `152` and its existing length-compatible prefix exactly:

1. unsigned visible count;
2. for each visible entry, unsigned icon item ID and signed remaining seconds;
3. unsigned overflow count.

Append status-extension version `1`, an entry count that must equal the prefix
count, and one fixed record per visible entry:

- identity kind: `ITEM` or `CLERIC`;
- unsigned stable identity (`itemId` for `ITEM`, stable spell code for
  `CLERIC`);
- rank (`0` for item-backed potion entries, one-based for Cleric entries);
- explicit counter kind: `NONE`, `CHARGES`, or `PULSES`; and
- unsigned remaining counter, necessarily zero for `NONE`.

The coordinated client validates version, exact record count, identity/rank
bounds, counter-kind/count agreement, and complete packet length before using
any enrichment. An absent, unsupported, truncated, or malformed trailer is
ignored as a whole while the already validated icon/timer prefix remains a
safe presentation fallback. Do not partially apply an invalid trailer.
Older maintained parsing can ignore trailing bytes. Advance the enforced
maintained-client protocol version for the coordinated catalog/wire change;
authentic generators continue to omit opcode `152` entirely.

Caster session and party-membership tokens never enter either packet. If a
Cleric catalog is unavailable or does not contain a received stable code, the
client shows only the safe prefix icon/timer rather than guessing a label or
crashing.

#### HUD presentation

Extract the bounded timer/counter/identity state from `mudclient` into a small
compiled-testable active-status HUD model; leave drawing behavior at the
existing HUD boundary. Preserve the current anchor, eight rows per column,
countdown rounding, local timer compaction, and `+N more effects` disclosure.
The row shows its icon, countdown, and an optional compact `3H` or `2P` badge.
The client never decrements a charge or pulse itself; an authoritative snapshot
is sent immediately when either changes.

Potion rows retain the exact consumed item icon and item-name hover behavior.
Cleric rows use the spell definition's approved Atelier Pixerelia icon when
mapped. Respite deliberately retains its aligned blessed-stone-sigil fallback;
a supplied but not yet approved Respite PNG does not change that mapping.
Their hover begins with exact spell name and Roman rank, followed by the
catalog-derived active magnitude and any remaining counter, for example:

- `Ward III — 25% reduction — 6 protected hits remaining`;
- `Fervor III — 15% chance to raise offense roll by 1`;
- `Rally II — 20% lifesteal until 60% Hits`;
- `Respite IV — 25% faster passive regeneration`; and
- `Mend II — 2 Hits per pulse — 2 healing pulses remaining`.

Do not display caster name, session identity, or party identity. Unique status
art later replaces only authoritative catalog icon fields and does not change
effect or packet identity. Caster bubbles and animations remain unset.

#### Focused implementation branches

Implement C08 as two sequential reviewable branches from the then-current
published `main`:

1. **C08A — `feat/cleric-effect-state-foundation`**
   - Add typed rank/magnitude definitions, families, counter kinds, origins,
     immutable entries, the bounded recipient registry, and deterministic
     replacement results.
   - Add session and party-membership-generation identities and centralized
     death/logout/membership cleanup with defensive validation.
   - Wire a content-neutral empty transient-effect slot and opaque lifecycle
     tokens into `Player`. Keep the concrete Cleric registry content-owned and
     unattached until a later approved effect handler needs it, without
     changing packets, the client, sigil spending, or reachable spells.
   - Stop and hand off after the compiled state/lifecycle fixture, server build,
     and changed-code analysis pass. The production registry must remain empty
     because no effect handler exists yet.
2. **C08B — `feat/cleric-status-hud-extension`**
   - Begin only after C08A is integrated.
   - Extend the authoritative catalog with typed rank presentation, add the
     unified status inventory/priority metadata, append and validate the
     versioned opcode-`152` trailer, and extract the client HUD model.
   - Retain the legacy prefix and authentic-client exclusion. Use the approved
     Atelier Pixerelia spell icons and labels, with the sigil fallback for
     Respite; assign no animation sheets.
   - Use a temporary private-only status injection fixture for visual review
     if required, and prove it is absent from the committed diff.
   - Stop and hand off after automated verification and owner-confirmed private
     presentation. Do not continue into C09 on this branch.

Do not combine either branch with `Player` decomposition, party-system
modernization, general potion refactoring, packet renaming, or combat-path
changes. If a required hook cannot remain this narrow, stop and return the
dependency to planning.

#### Required verification

C08A adds a compiled fixture covering:

- all seven family slots and every confirmed timed spell/rank definition;
- exact magnitude and duration tables, valid typed counters, immutable
  snapshots, invalid rank/magnitude/counter rejection, and collection bounds;
- different-family coexistence;
- Greater Mend over Mend and Aegis over Ward regardless of numerical rank;
- higher-rank replacement, lower-rank rejection, equal refresh without counter
  accumulation, full snapshot replacement, and accepted-cast origin transfer;
- monotonic expiry boundaries, counter decrement without underflow, pulse and
  charge exhaustion, repeated clear/expiry idempotence, and no stale revival;
- recipient death/logout, caster logout/departure, recipient leave/kick,
  same-party leave/rejoin, caster relog, and defensive stale-origin rejection;
- caster death preserving effects on living recipients until another lifecycle
  condition occurs;
- registry snapshot/mutation concurrency without exposing mutable state or
  acquiring nested recipient locks; and
- source guards proving no new cache/database key, login restoration, reachable
  spell effect, or gameplay magnitude table exists outside the authority.

C08B adds compiled packet/model and repository fixtures covering:

- the exact current potion-family inventory and authored mixed priority order;
- deterministic selection at 31, 32, 33, and 64 entries, exact overflow, and
  no gameplay removal for an omitted status;
- stable ordering across timer changes and refreshes;
- byte-compatible legacy prefix decoding with no trailer;
- valid mixed `ITEM`/`CLERIC` trailer decoding, all three counter kinds,
  unsigned counter bounds, catalog lookup, rank labels, and fallback icons;
- unsupported version, count mismatch, truncation, trailing garbage, unknown
  identity, invalid rank, and kind/count mismatch falling back without partial
  enrichment or a crash;
- client-local timer countdown but no local counter decrement;
- immediate authoritative refresh after counter/pulse changes;
- potion-only icon/name/countdown behavior, Cleric exact magnitude labels,
  Roman ranks, compact badges, `+N more effects`, and logout/reconnect clearing;
- maintained-client version/catalog parity and authentic-client absence of the
  packet; and
- packaged approved icon assets, explicit Respite fallback, and no required
  caster-bubble or animation asset.

Run at minimum on the applicable branch:

- new `tests/myworld/test-cleric-effect-state.py` and
  `tests/myworld/test-cleric-status-hud.py` compiled fixtures;
- `python3 tests/myworld/test-cleric-support-cast-transaction.py`;
- `python3 tests/myworld/test-cleric-spellbook-foundation.py`;
- `python3 tests/myworld/test-cleric-spellbook-presentation.py`;
- `python3 tests/myworld/test-potion-hud.py`;
- `python3 tests/myworld/test-potion-runtime.py`;
- `python3 tests/myworld/test-potion-brawn-healing-cap.py`;
- relevant party, death, logout, prayer, god-spell, protocol-version, and
  client-definition regression suites discovered at implementation time;
- `./scripts/build-server.sh` and, for C08B, `./scripts/build-client.sh`;
- changed-code compiler/static analysis against that branch's published-main
  base, without broad baseline cleanup; and
- `git diff --check`.

Before C08B READY handoff, launch only a loopback/private client and server and
ask the owner to inspect a potion-only row, representative `NONE`, `CHARGES`,
and `PULSES` Cleric rows, exact hover magnitudes, rank labels, mixed ordering,
32-entry overflow, expiry, counter refresh, and logout clearing. Screenshot
capture is not required. An intentional owner close is not a failure. Remove
all temporary injection code before checkpointing the reviewed commit.

#### C08 acceptance and stop gates

C08 is complete only when:

- registry state remains transient, bounded, typed, and unreachable from
  incomplete spell handlers;
- replacement, origin, membership generation, cleanup, and defensive
  validation match the confirmed contract;
- one authoritative rank definition supplies both future mechanics and current
  presentation;
- mixed priority and overflow are deterministic without affecting gameplay;
- the legacy packet prefix, maintained fallback behavior, and authentic-client
  boundary pass regression coverage;
- no new asset, cache key, database field, PvP support, Worship XP, sigil cost,
  or spell effect has slipped into scope;
- builds and changed-code analysis pass; and
- the owner privately accepts C08B presentation before its exact pushed commit
  is handed off.

### C09 — Low-Risk Support Effects

Implement in narrow branches, beginning with mechanics that do not alter the
shared combat damage pipeline:

1. Purify;
2. Restore;
3. Mend and Greater Mend;
4. Respite using C07's natural-regeneration interval boundary after C08 owns
   its transient state.

Each branch owns its pure calculations, lifecycle tests, area behavior,
resource transaction, client text/HUD behavior where applicable, builds, and
private verification.

### C10 — Direct-Combat Support Effects

Introduce one shared direct-damage eligibility model, then implement Fervor,
Zeal, Ward/Aegis, Thorns, and Rally in focused branches. Preserve existing
blood-item, god-spell, recoil, critical-hit, prayer, poison, summon, and kill
attribution behavior. Test melee, ranged, and Magic paths together so no effect
is accidentally implemented only in one handler.

### C11 — Unify Movement (retired into C07)

The settled cast-transaction decision moved the bounded Unify implementation
into C07. C07 clears affected recipients' queued walking and applies at most two
ordinary collision-validated steps without teleporting, changing combat
targets, or creating a persistent tether. There is no remaining independent
C11 implementation branch; later changes to Unify require a new focused scope.

### C12 — Devotion Economy and Release Gate

Add approved Devotion sources, measure acquisition against expected sigil
consumption, and validate that ordinary offerings remain relevant. Complete
private party-combat and production testing, package all client assets, run the
full server/client/release suites, and retain feature gating until all twelve
spells and compatibility checks pass. Live activation is a separate manager
operation subject to the public-server shutdown gate.

## Supplied Sigil Asset Inventory

The owner supplied these source files on 2026-08-03:

- `unblessed-sara-sigil.png`
- `blessed-sara-sigil.png`
- `unblessed-guth-sigil.png`
- `blessed-guth-sigil.png`
- `unblessed-zam-sigil.png`
- `blessed-zam-sigil.png`
- `unblessed-neutral-sigil.png`
- `blessed-neutral-sigil.png`

They visibly distinguish all four primary alignments and the unblessed/blessed
state. C02 will preserve these as the stone-family source art. Silver versions
must retain the same symbols, use a silver palette treatment, and render
slightly smaller as requested.

## C01 Handoff Contract

The first handoff is complete only when:

- the implementation plan is checkpointed;
- the twelve-definition catalog compiles in isolation and in the full server;
- explicit keys/codes do not depend on `Spells.ordinal()`;
- all confirmed launch metadata in C01 is covered by executable assertions;
- cost and rank helpers reject invalid input and return immutable data;
- no production code outside the new foundation package references the catalog;
- no unresolved behavior has been implemented;
- the branch is clean, pushed, and marked READY with the exact commit.

## C01 Completion Record

C01 was completed on 2026-08-03 as an intentionally unreachable server-side
foundation. It adds explicit Cleric spell identities, immutable definition and
cost records, the twelve-entry launch catalog, and pure Holy Power effect-rank
resolution. Nothing registers the catalog with server startup, packet
handling, the legacy Magic spell list, an item, or a player-facing action.

The compiled fixture validates every confirmed launch name, alignment,
Worship gate, tier, radius, caster-exclusion rule, primary-sigil vector, stable
identity, and Holy Power threshold. It also covers collection immutability,
defensive copying, rank boundaries, invalid identities, invalid costs, and the
deliberate rejection of unsettled tier-three costs. A source guard fails if
production code outside the foundation package begins referencing it early.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `./scripts/build-server.sh`
- `./scripts/lint.sh compiler --base 40d2407cb047742e52070e41fc5ef9d865bf77f5 --offline`
- `./scripts/lint.sh analyze --base 40d2407cb047742e52070e41fc5ef9d865bf77f5 --offline`
- `git diff --check`

No client or visual verification is required for C01 because it deliberately
has no presentation or runtime path. The supplied sigil PNGs remain untouched
at their source location for C02, when their stable item identities, maintained
asset locations, generated silver variants, and classic fallbacks can be added
and verified together. All unresolved runtime-exposure blockers above remain
open and continue to stop later dependent phases.

## C02 Completion Record

C02 established sixteen inert item definitions at stable IDs
`3293-3308`: four alignments, stone and silver materials, and unblessed and
blessed states. It does not provide any acquisition or use path. The server
definitions and direct client registry agree on names, descriptions, flags,
IDs, and the exclusive item count of `3309`.

C05 subsequently refines the provisional C02 stackability flag: the eight
unblessed identities are non-stackable and the eight blessed identities remain
stackable, preserving inventory pressure before altar conversion. This is an
intentional production-economy decision, not identity drift.

The owner's eight supplied sigil images and plain `stone.png` reference are
preserved byte-for-byte in the maintained sigil asset folder. Eight derived
silver variants recolor only pixels belonging to the supplied stone substrate
palette; every religious and neutral symbol pixel is unchanged. Maintained
images remain `28x25` RGBA files. Client metadata renders stone at `28x25` and
silver at `24x21`, using nearest-neighbor scaling for the requested smaller
silver footprint.

Enhanced clients search the focused maintained folder and package all PNGs in
the client JAR. Missing external art remains safe: stone definitions fall back
to authentic stone sprite `443`, while silver definitions fall back to
authentic silver sprite `134`. Classic presentation therefore never depends on
the new external artwork.

The focused compiled fixtures validate stable identity lookups and rejection,
all sixteen server/client records, source-art hashes, exact substrate-only
silver transformation, alpha and dimensions, development and packaged loading,
render bounds, packaged-resource presence, and the missing-PNG fallback path.
Source guards keep the identities unreachable from production gameplay until a
later approved phase. Verification for this milestone includes:

- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `python3 tests/myworld/test-cleric-sigil-item-assets.py`
- `python3 tests/myworld/test-client-definition-registry-extraction.py`
- `python3 tests/myworld/audit-item-id-integrity.py`
- `python3 tests/myworld/audit_client_item_coverage.py`
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- changed-code compiler and static analysis
- `git diff --check`

C02 deliberately adds no carving, altar conversion, Devotion accounting,
Blessing behavior, spawn/drop/shop source, spell consumption, dialogue, packet,
or player-visible spell functionality. All global runtime blockers remain open.

## C03 Completion Record

C03 adds Holy Power as an equipment-derived server statistic and an eighth
extended equipment value in maintained clients. It is calculated from current
equipment rather than persisted independently, so equip, unequip, both
equipment modes, and relog all share the existing equipment authority. The
authentic revision-38 six-byte equipment packet remains unchanged; maintained
packet families append Holy Power after Magic Power and the client parses the
optional extension by packet length.

All three blessed-staff lines use Holy Power
`8/12/16/24/28/32/40/44/48/56`; the three god staves use `64`. Ordinary Magic
staves and blessed armor contribute zero. The launch Cleric-effect thresholds
were translated to the same staff breakpoints, so the larger display scale
does not change which staff tier reaches a given effect rank. Blessed-staff
Magic Power is exactly half the comparable ordinary-staff ladder, and god
staves retain Magic Power `28`. Existing offensive god spells continue to use
the established Magic offense/defense calculation and never consult Holy
Power.

The client labels the stat `Holy Pow:` in both equipment-stat presentations.
The blessed-staff inventory symbols now consistently communicate alignment:
Zamorak is fiery red, Saradomin is pale yellow, and Guthix is nature green
across every material tier. The owner privately confirmed no staff, ordinary
staff, multiple blessed-staff tiers, god staff, equip/unequip, relog, both stat
presentations, and the corrected alignment colors on the isolated loopback
test world.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-holy-power-equipment.py`
- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `python3 tests/myworld/test-combat-data.py`
- `python3 tests/myworld/test-blessed-staff-god-variants.py`
- `python3 tests/myworld/test-god-special-prayers-and-spells.py`
- `python3 tests/myworld/test-devotion-equipment-scaling.py`
- `python3 tests/myworld/test-server-equipment-calculation-extraction.py`
- `python3 tests/myworld/test-wood-crafting-client-definitions.py`
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- changed-code compiler and static analysis
- `git diff --check`

C03 adds no Cleric casting, effect application, sigil production or
consumption, Blessing skill, persistence field, dialogue, shop, drop, or other
player-visible Cleric gameplay. At its handoff, C04 remained blocked on the
then-unresolved Blessing skill decisions recorded above.

## C04 Completion Record

C04 appends Blessing after Summoning as a stable original-curve level `1-99`
skill without renumbering an existing identity. The maintained client retains
that transport order while sorting Blessing alphabetically in the stats panel.
The skill starts at current/base level `1` with zero XP, counts toward skill
totals and overall/per-skill highscores, resolves through dynamic commands,
and has an intentionally informational guide with no production action.

MySQL and SQLite schema templates and dated migration patches persist current
level, base level, XP, and cap date. The executable SQLite migration fixture
upgrades an account from the canonical pre-Blessing seed, preserves its Quest
Points, checks the `1/1/0/null` defaults, writes non-default state, closes and
reopens the database, and verifies both existing- and new-row behavior. A real
isolated server startup applied the same SQLite patch once to the development
database; after disconnect, `devduck` retained Blessing `12/12`, raw XP `6336`,
an unset cap, and unchanged Quest Points, with a successful SQLite integrity
check.

Only the maintained custom stat packet gains Blessing fields, after Summoning
in each of the current/base/XP arrays. Quest Points remains the following
independent byte, and C04's coordinated enforced client version was `10048`.
Authentic/legacy generators remain byte-for-byte outside this change. Blessing
automatically receives the existing general non-combat XP handling and is
explicitly included in the production-skill Mind-necklace XP family; the
Insight level potion and combat-level calculation remain unchanged.

The owner privately confirmed the alphabetical stats presentation, separate
Quest Points, expected skill-total adjustment, informational guide/hiscores,
and an administrator-set Blessing level on an isolated loopback world. The
server was bound only to `127.0.0.1:43625`; neither the public server nor the
other worker's private server was touched.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-blessing-skill-platform.py`
- focused skill-selector, stats-layout, hiscore, protocol-version, Worship,
  Cleric-foundation, sigil-asset, Holy-Power, guide, potion, jewelry,
  RuneCraft-XP, processing, and player-data guards
- World Builder release, runtime-preparation, discovery, import, and export
  suites
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- `python3 scripts/lint.py all --base spoiled-milk/main --offline`
- `git diff --check`

The player-release asset-inventory guard remains red on the C02 sigil artwork
already present on `main`; its whitelist has not yet been reconciled with those
merged assets. The layered World Builder import suite also requires a
separately generated accepted package that was not available in this
workspace. Neither limitation was changed in C04.

C04 adds no sigil crafting, altar conversion, Devotion spending, Cleric spell,
shop, drop, dialogue, cape, dedicated potion, guild, or high-tier content.
C05 owns the now-confirmed recipe requirements, base Crafting/Blessing XP,
batching, transaction-failure behavior, and compatible half-offering
accounting and Devotion-floor semantics. No C05 production-design blocker
remained at the C04 handoff; the following record documents its implementation.

## C05 Completion Record

C05 makes the eight launch carving recipes and their altar conversions
reachable without exposing Cleric spells. A chisel used on Rune stone (`1299`)
or Silver nugget (`383`) opens the existing Crafting quantity interface with
Saradomin, Guthix, Zamorak, and neutral choices. Stone uses Crafting/Blessing
requirements `1/1` and base XP `5/5`; silver uses `20/16` and base XP `10/10`.
Carving is an interruptible per-input batch and awards Crafting XP only after
an exact in-slot conversion succeeds.

The eight carved identities are deliberately non-stackable, while the eight
blessed identities remain stackable. This C05 refinement of C02 preserves the
intended inventory pressure and repeated resource/bank-to-altar loop. One
sigil-on-altar action converts every carried unblessed sigil of the exact used
material and alignment. Aligned inputs require their matching god altar;
neutral inputs accept any recognized god altar and charge that altar's god.
The selected Worship book is irrelevant. Output follows the existing
one-extra-per-ten-level production ladder, and Blessing XP uses RuneCraft's
exact diminishing `1x`, `1.5x`, `1.75x`, and later series.

The compatible `devotion_<god>_offerings` cache value retains whole-offering
meaning and a signed `-1/0/+1` remainder stores half-offering precision. Each
input costs one half-offering unit, or `0.05` displayed Devotion, with bounded
integer arithmetic and no eager migration. The transaction verifies the full
cost and inventory conversion first, then commits both under player ownership;
a rejected inventory state change never performs a transient deduction or
prayer cleanup. Starting must be above `-1000`, ending exactly there is valid,
and crossing it is rejected. The maintained Devotion packet keeps its opcode
and signed-short width but now carries exact half-offering units, advances the
enforced client version to `10049`, and displays fractional balances precisely.
Authentic/legacy packet layouts remain unchanged.

Executable coverage validates all eight recipes, stable item resolution,
server/client stackability parity, level thresholds, `1/2/29/30` cost cases,
ten-level output duplication, diminishing XP, overflow and corrupt-remainder
rejection, exact positive/negative display, the `-999.95` floor boundary,
atomic inventory preflight, and transaction-failure ordering. Relevant C01-C04,
Devotion, ordinary prayer blessing, production UI/flow, item-integrity, client
coverage, World Builder protocol, and release fixtures also pass.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-sigil-production.py`
- `python3 tests/myworld/test-cleric-sigil-item-assets.py`
- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `python3 tests/myworld/test-cleric-blessing-skill-platform.py`
- `python3 tests/myworld/test-cleric-holy-power-equipment.py`
- focused Devotion, prayer-blessing, production, item-integrity, client-coverage,
  bank-protocol, and World Builder discovery/release suites
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- `./scripts/lint.sh compiler --base cda8a0ab8566eb5d7551ce409ef0a4c3fb5a3112 --offline`
- `./scripts/lint.sh analyze --base cda8a0ab8566eb5d7551ce409ef0a4c3fb5a3112 --offline`
- `git diff --check`

The owner privately accepted the Crafting UI, five separate unblessed
inventory slots, artwork, whole-batch altar conversion, `2x` stone output at
Blessing `16`, and exact Zamorak Devotion change from `10` to `9.75`. The
matching client and server ran only on loopback ports `43625/43525`, because
another worker already owned the ordinary private ports. That worker's process
and the public server were untouched. The expected connection-reset diagnostic
was limited to intentionally closing the private client after acceptance; no
sigil-production exception occurred.

C05 added no Cleric spell transport, spellbook UI, casting, support effect,
combat change, shop, drop, dialogue, secondary sigil, blessed equipment recipe,
or additional Devotion source. The later C06 branch begins only the confirmed
catalog transport, presentation, immediate-request boundary, and status
capacity work described above; it does not reinterpret C05 production.

## C06 Completion Record

C06 adds a maintained-client-only, server-authoritative Cleric catalog packet
using stable spell codes and schema version `1`. Its twelve records carry the
confirmed Worship requirement, alignment, area, caster exclusion, primary
stone/silver cost vector, concise effect description, spellbook icon item, and
optional caster icon/animation identifiers. The client validates ordering,
keys, schema, and bounds before replacing an immutable session snapshot. The
coordinated maintained-client protocol version is `10050`; authentic protocol
generators and the compatibility-sensitive legacy `Spells` identities remain
unchanged.

The custom interface now presents Spells as the top-level tab with nested Mage
and Cleric tabs. Mage is the fresh-session default, while subsequent tab
navigation remembers the player's last Mage/Cleric selection. Both icon and
text layouts render Cleric metadata without a client-side name table. Clicking
an unlocked Cleric entry submits one stable-code request immediately and
clears item/spell targeting; it never creates a target cursor or autocast
selection. The server validates maintained MyWorld use, identity, trained
Worship level, and the initial no-PvP boundary, then intentionally reports that
the effect is unavailable. C07 still owns party recipients, resource spending,
cooldowns, and actual support effects.

The existing custom status snapshot now transmits up to `32` entries from a
server-side collection bounded to `64`, appending the number omitted. The HUD
shows `+N more effects` when necessary. This increases capacity and makes
overflow explicit without making client presentation authoritative or choosing
the future mixed potion/Cleric priority and charge/pulse representation.

Optional player-centered icon and animation hooks are present but all launch
definitions deliberately leave them unset. The owner confirmed that final
icon and animation visuals should be postponed until all major Cleric work is
complete; later milestones must not invent or require those assets in the
meantime.

Verification completed from the milestone branch:

- `python3 tests/myworld/test-cleric-spellbook-foundation.py`
- `python3 tests/myworld/test-cleric-spellbook-presentation.py`
- `python3 tests/myworld/test-potion-hud.py`
- `python3 tests/myworld/test-cleric-sigil-production.py`
- `python3 tests/myworld/test-cleric-sigil-item-assets.py`
- `python3 tests/myworld/test-cleric-blessing-skill-platform.py`
- `python3 tests/myworld/test-cleric-holy-power-equipment.py`
- `python3 tests/myworld/test-spellbook-text-layouts.py`
- `python3 tests/myworld/test-prayer-ui.py`
- `python3 tests/myworld/test-god-special-prayers-and-spells.py`
- `python3 tests/myworld/test-player-spell-animation-migration.py`
- `python3 tests/myworld/test-bank-wide-slot-updates.py`
- World Builder discovery and release suites
- `./scripts/build-client.sh`
- `./scripts/build-server.sh`
- `python3 scripts/lint.py all --base spoiled-milk/main --offline`
- `git diff --check`

The owner privately accepted the Mage default, Mage/Cleric subtab navigation
and memory, server-fed Cleric icons and tooltips, immediate click behavior,
text layout, and unchanged Mage/god-spell presentation. The client and server
ran only on loopback ports `43615/43515`; the public server was untouched. The
client's intentional window close produced the existing connection-reset
diagnostic during logout, with no Cleric presentation or protocol exception.

C06 adds no support effect, party targeting, sigil consumption, cooldown,
Worship XP, combat change, PvP support, caster visual asset, dialogue, shop,
drop, or Devotion source. C07 may now proceed under the confirmed
partial/ineffective cast transaction, Unify movement-queue, and Respite
natural-regeneration-clock rules.

## C07 Completion Record

C07 adds one shared, immutable party-target snapshot and resolves launch
support recipients by identity rather than by nearby-player scans. The caster,
offline or unregistering members, stale party references, PvP recipients,
duplicate references, other world spaces, other signed levels, out-of-square
range members, and recipients without spell line-of-effect are excluded
independently. Walls, closed boundaries, and missing terrain therefore fail
closed through the same layered-aware path authority used by Magic. A blocked
or otherwise ineligible member does not invalidate an unobstructed one.

The shared cast transaction receives only side-effect-free prepared
applications. It skips ineffective recipients, treats an equal-strength
refresh as useful when a later effect planner supplies one, and never enters
the resource boundary for a wholly ineffective cast. One complete aligned
stone/silver cost vector is preflighted once. Its prepared applications and
deterministic item removal then commit under the same serialized player and
container boundary, avoiding partial vectors and observable deduct/refund
behavior. Casting still awards no Worship XP.

Unify is the only support effect made reachable by C07. Eligible party members
within two tiles remain unchanged and do not make a cast useful. Members three
or four Chebyshev tiles away move one or two ordinary steps toward the caster.
Every step is collision checked, diagonal movement may fall back to safe
cardinal progress, and one safe step remains a useful partial application.
Affected recipients have queued walking cleared first; their combat targets
and statuses are not reset. Unify never teleports, crosses world/level
boundaries, or creates a tether. One blessed neutral stone sigil is consumed
once only when at least one recipient actually moves.

C07 also extracts pure natural-Hits-regeneration interval math. Existing rapid
heal, soul-robe, regeneration-potion, and body-amulet factors retain their
order and independent multiplicative ownership. The later Respite application
will supply only its snapshotted speed factor to this existing clock; C07
passes zero, creates no status, does not reschedule the event, and cannot grant
an immediate healing tick. All remaining spell effects and C08 status/HUD
representation stay unreachable.

## C08A Completion Record

C08A adds the server-only transient authority required by later timed Cleric
effects. Seven explicit stable family identities bound each recipient registry
to one healing-pulse, accuracy, protection, damage, reflection, lifesteal, and
passive-regeneration slot. Thirty-five immutable rank definitions cover all
nine confirmed timed launch spells. Spell-specific magnitude types prevent an
unlabeled value array from becoming a second balance table, and construction
rejects mismatched spell, family, counter, and magnitude shapes. Purify,
Restore, and Unify cannot create timed definitions.

Every active entry owns an immutable definition snapshot, a monotonic
application/expiry deadline, its typed remaining charge or pulse count, and
opaque caster-session plus caster/recipient membership-generation origins.
Higher-tier Greater Mend and Aegis replace lower-tier Mend and Ward before rank
comparison; same-spell higher ranks replace, lower ranks are ineffective, and
equal ranks refresh the complete duration and normal counter while transferring
origin. Registry operations are synchronized within one recipient, snapshots
are immutable, counters cannot underflow, and expiry/origin validation fails
closed before state is exposed.

`Player` now owns a content-neutral empty transient-effect slot and opaque
session identity, while each party join or login reattachment issues a fresh
membership generation. The concrete Cleric registry implements that narrow
foundation-facing lifecycle contract but remains content-owned and unattached
until a later approved effect handler creates state. This preserves the Server
R2 dependency direction: foundation lifecycle code never imports Cleric
content. Real death and logout clear received effects. The shared
`Party.removePlayer` boundary now also ends membership for unregistering player
references, not only members still considered online, so leave, kick, logout,
and membership transition all clear the departing recipient and effects
sourced from that exact membership before party-list removal. Caster death
deliberately clears only the dead recipient's own state; living recipients
retain bounded effects until expiry, logout, or party separation. Cleanup is
idempotent, deduplicates recipient states, and never holds two registry locks
at once.

The compiled fixture covers all definitions and values, invalid construction,
seven-family coexistence, tier/rank replacement and refresh, origin transfer,
charge/pulse exhaustion, exact expiry, stale-session and same-party rejoin
rejection, all lifecycle rules, immutable snapshots, bounded concurrency, and
source guards for the convergent Player/Party boundaries. It also proves no
handler can populate the registry and no cache, database, packet, login restore,
client, sigil-spending, or non-Unify spell path references the new authority.

Verification completed from the C08A branch:

- `python3 tests/myworld/test-cleric-effect-state.py`
- the C01 and C03-C07 Cleric regression fixtures
- potion HUD, runtime, Brawn healing-cap, combat-invariant, ordinary-death,
  player-data, god-spell, and prayer regression fixtures
- `./scripts/build-server.sh`
- `./scripts/lint.sh compiler --base 2a0ff5ddb1e77d72fbea354ac0a2e76a86f242de --offline`
- `./scripts/lint.sh analyze --base 2a0ff5ddb1e77d72fbea354ac0a2e76a86f242de --offline`
- `git diff --check`

C08A changes no packet, client, status HUD, spell magnitude, sigil cost,
Worship XP, persistence, PvP rule, or combat/regeneration behavior. Unify
remains the only reachable Cleric support action. C08B must begin from a
published main that contains C08A; it may consume this authority for
presentation but must not expose C09/C10 mechanics.
