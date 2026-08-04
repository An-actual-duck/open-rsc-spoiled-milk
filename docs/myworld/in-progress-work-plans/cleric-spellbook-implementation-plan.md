# Cleric Spellbook Implementation Plan

## Status

- Branch: `main` (C06 integrated)
- Governing design: [`cleric-spellbook-concept.md`](cleric-spellbook-concept.md)
- Completed milestones: **C01 — definition catalog foundation; C02 — sigil item and asset identities; C03 — Holy Power equipment foundation; C04 — Blessing skill platform; C05 — sigil carving and altar blessing; C06 — Cleric spellbook transport and presentation**
- Current milestone: **C06 integrated, automated-tested, and privately accepted**
- Next planned milestone: **C07 — shared support targeting and cast transaction; its cast-level resource, Unify movement, and Respite clock rules are settled**
- Runtime exposure: **Holy Power equipment, Blessing skill state, stone/silver sigil production, and maintained-client Cleric catalog presentation; support effects and sigil consumption remain disabled**
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

The following remain later design work and may not be guessed:

1. Mixed potion/Cleric status priority and charge/pulse representation within
   the expanded transport bound (C08).
2. Future offensive god-spell expansion beyond the existing Mage entries;
   existing god spells remain under Mage for the initial rollout.
3. Additional Devotion sources for later economy tuning; the initial confirmed
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

Implement transient effect identity, snapshotted rank, magnitude, expiry, and
optional charges/pulses. Enforce replacement/exclusivity centrally and clear
effects on death, logout, or party separation. Extend C06's expanded status
packet compatibly with per-effect charges/pulses only after mixed-effect
priority is settled. Preserve its visible overflow count. Authentic clients
receive no custom dependency.

### C09 — Low-Risk Support Effects

Implement in narrow branches, beginning with mechanics that do not alter the
shared combat damage pipeline:

1. Purify;
2. Restore;
3. Mend and Greater Mend;
4. Respite after its regeneration-clock rule is resolved.

Each branch owns its pure calculations, lifecycle tests, area behavior,
resource transaction, client text/HUD behavior where applicable, builds, and
private verification.

### C10 — Direct-Combat Support Effects

Introduce one shared direct-damage eligibility model, then implement Fervor,
Zeal, Ward/Aegis, Thorns, and Rally in focused branches. Preserve existing
blood-item, god-spell, recoil, critical-hit, prayer, poison, summon, and kill
attribution behavior. Test melee, ranged, and Magic paths together so no effect
is accidentally implemented only in one handler.

### C11 — Unify Movement

Implement last among the launch spells because it crosses movement queues,
pathfinding, collision, layered maps, combat retention, and client movement
presentation. Move only up to two validated steps; never teleport. Stop until
the remaining queue/presentation decision is approved.

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
