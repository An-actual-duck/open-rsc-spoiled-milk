# Ante Risk-and-Reward Plan

## Status and Purpose

Status: **CONFIRMED FEATURE DIRECTION; IMPLEMENTATION NOT STARTED.**

Ante replaces unavoidable PvM inventory loss with a player-authored risk
contract. A player chooses which eligible unstackable inventory items they are
willing to lose on death. Those items remain in the inventory rather than
moving into escrow. The value exposed through Ante determines the player's
ordinary and rare drop rates:

- low risk means a longer rare-drop grind;
- reaching the combat-level-scaled normal threshold restores today's ordinary
  and rare rates;
- exceeding that threshold improves both common-drop frequency and rare-reward
  chances; and
- death loses the selected Ante items while protecting non-Ante inventory.

The governing product principle is **low risk, high grind; high risk, high
reward**. Ante is definitely intended for implementation. Exact economy values
and a few scope boundaries remain balance decisions, not reasons to weaken the
confirmed feature.

## Confirmed Product Direction

- Ante has a dedicated maintained-client interface.
- The player may select eligible unstackable items that are currently in their
  inventory.
- Selecting an item does not remove, bank, duplicate, or escrow it. It remains
  an ordinary inventory item until consumed, transferred, transformed, or lost
  on death.
- Within an Ante-covered death, selected items are the items at risk and
  unselected inventory is protected.
- An empty Ante selection is valid. It provides maximum item safety and the
  lowest common and rare reward rates.
- Every combat level has a normal-value threshold. The threshold rises
  monotonically as the player's combat level rises.
- Reaching the threshold gives the current normal common and rare drop rates.
  Ante value below it reduces both rates; value above it increases both rates.
- Common-drop scaling changes how often ordinary common rewards occur, not the
  quantity inside an awarded stack. Guaranteed bones, fixed quest rewards, and
  true always-drops remain guaranteed rather than being reclassified as common
  rolls.
- The relationship is a sliding scale rather than a small set of equipment
  presets.
- Loot sharing and personal contribution scaling must compose with Ante. No
  participant may inherit another player's higher Ante value.
- The system is server authoritative. Client totals, highlights, and projected
  rates are explanatory views, never trusted reward or death inputs.

## Repository Reality to Preserve

### Current death authority

`Inventory.dropOnDeath` currently combines equipment and inventory, sorts
unstackable items by server definition price, normally keeps the three most
valuable unstackable items, then applies Soul-necklace and compatibility
exceptions. Stackable and noted items are normally lost. `Player.killedBy`
keeps duel handling separate and invokes this inventory authority for ordinary
deaths.

Ante must become an explicit death policy rather than adding ad-hoc checks to
that price-sorted iterator. Duel stakes, Hardcore status loss, scripted safe
deaths, Ultimate Ironman rules, staff exceptions, and PvP ownership retain
their explicit compatibility boundaries. Untradeable-ground-item behavior is
outside Ante because non-tradeable items cannot be selected.

### Current personal loot and “LootShare” reality

NPC death currently creates a personal reward roll for every live contributor
with positive damage. Each recipient receives a contribution scale based on
their damage divided by the NPC's Hits, clamped to the current `5%` minimum and
`100%` maximum. That scale gates marked rare tables, selected rare normal
drops, hidden uniques, and custom rare paths.

The party `shareLoot` field is presently transported for party presentation
but is not the authority that distributes NPC rewards. For this plan,
“LootShare” therefore means both:

1. the maintained personal-loot contribution scaling that already affects
   rare chances; and
2. compatibility with any later party redistribution feature that begins
   using the existing party flag.

Ante must first compose with the real contribution scale. A future shared-loot
transport must retain the originating recipient's own Ante and contribution
facts rather than rerolling with the leader's or party's best value.

### Current rare-reward families

The initial audit must cover every rare path rather than changing only the
named `Rare Drop Table`:

- nested `DropTable` instances marked rare;
- rare normal-table items governed by the current rare-item policy;
- hidden unique rolls in `NpcDrops`;
- custom NPC rare paths such as KBD and other boss-specific tables;
- extra rare-table access from Wealth equipment;
- extra standard rolls from Cosmic equipment;
- Luck-potion rare-table weight changes;
- bad-luck mitigation; and
- any plugin-owned drop path intentionally classified as rare.

Guaranteed bones, fixed quest drops, true always-drops, and scripted awards do
not inherit Ante scaling. Ordinary common table rewards do scale with Ante, but
through an explicit common-frequency policy rather than the rare-drop gates.

## Ante Item Identity and Eligibility

### Instance ownership

Selections bind to the server's persistent `Item.itemId`, not an inventory
slot or catalog ID. This is required because inventory sorting can move an
item, two visually identical unstackable items may have different durability
or state, and a replacement item must not inherit an older item's risk merely
because it has the same definition.

Every selected record carries enough server-owned information to validate:

- character ownership;
- unique item-instance ID;
- current catalog ID and item state;
- current inventory presence;
- current eligibility;
- authoritative Ante value; and
- the selection revision used by the client snapshot.

Inventory movement within the inventory preserves selection. Banking, trading,
dropping, destroying, consuming, noting, transforming, or otherwise removing
the exact instance invalidates it immediately. A newly acquired instance of
the same item is not selected automatically.

### Eligible inputs

The confirmed broad rule is **unstackable inventory items**. The implementation
audit must define the narrow safety exclusions needed to make “loss” real and
prevent account damage:

- stackable and noted items are ineligible;
- non-tradeable/untradeable items are ineligible, even when unstackable;
- the exact item instance must be in inventory, not merely equipped or banked;
- server placeholders, administrator/debug items, and invalid definitions are
  ineligible;
- an item that normal death rules cannot genuinely remove must not provide
  reward value; and
- any tradeable but recovery-sensitive exception requires an explicit reviewed
  exclusion before launch.

This audit must not casually redefine “any unstackable item” into “equipment
only.” Food, potions, tools, jewelry, and other unstackable items remain
eligible candidates when normal item semantics make their loss safe. If a
selected consumable is used, it simply ceases to exist, leaves the active
selection, and contributes no later value.

### Equipped items

The first implementation should require the item to remain in inventory.
Moving a selected item into an equipment slot invalidates that selection and
updates the total. This follows the stated inventory contract and avoids
secretly risking worn gear. Supporting equipped Ante items later would require
a separate interface, death preview, and item-instance transport decision.

## Authoritative Valuation

Ante must never use a client-supplied price or a live player-to-player market
quote. The server computes value with a dedicated `AnteValuationCatalog`:

1. begin with the effective server item definition's default price;
2. apply reviewed catalog-ID overrides for items whose definition price does
   not represent acquisition or replacement difficulty;
3. reject items with missing, zero, negative, placeholder, or unsafe values
   unless explicitly approved; and
4. sum with checked `long` arithmetic.

Durability, charges, enchantment tier, poison state, and other instance state
must be audited. If that state changes real replacement value, either provide
a deterministic state-aware valuation or conservatively use a reviewed fixed
value. Do not infer prices from display names.

The valuation catalog is also an economy-review surface. Tests must list every
override and fail when an approved high-tier item falls back to an accidental
placeholder price.

## Combat-Level Normal Threshold

Let:

- `V` be the current authoritative value of valid selected Ante items;
- `T(level)` be the normal-value threshold for the player's current combat
  level; and
- `R = V / T(level)` be the player's Ante ratio.

`T(level)` must be server-owned, monotonic, data-driven, and defined for every
combat level the Spoiled Milk formula can produce. Combat-level changes update
the displayed threshold and ratio immediately. They never rewrite the player's
selection.

The first balance branch should derive a threshold ladder from real equipment
and acquisition values at representative combat bands. A table or validated
piecewise curve is preferable to an unexplained quadratic constant. The
result must satisfy:

- `T(level) > 0` for every playable combat level;
- no lower combat level requires more value than a higher level;
- small level changes do not create extreme value cliffs;
- upper levels require meaningfully greater risk; and
- existing custom combat-level calculation remains the authority.

Exact threshold values remain open until the economy simulator and item-value
audit are complete.

## Sliding Reward Curves — Balance TBD

Ante has two bounded multipliers centered on the ordinary rate:

- `C(R)` controls how often ordinary common rewards occur; and
- `A(R)` controls reviewed rare and unique reward chances.

Both equal exactly `1.00x` at `R = 1.00`, fall below `1.00x` when the player
antes less than the normal threshold, and rise above `1.00x` when the player
antes more. Empty Ante remains a valid nonzero low-rate profile rather than
making ordinary or rare drops impossible. The exact low-risk floors,
high-risk caps, breakpoints, interpolation, and whether the common and rare
curves share values are all **TBD** pending simulation and owner approval.

Common scaling means frequency rather than stack inflation. Below normal, an
ordinary common reward opportunity occurs less often. Above normal, it occurs
more often through a bounded common-only opportunity; awarded item quantities
and relative common-table weights remain unchanged. The implementation method
must preserve today's RNG/result stream at `1.00x` and must suppress rare
tables from any common-only bonus opportunity.

Before final approval, a simulator must report expected kills per reward and
expected item loss exposure at representative combat levels, thresholds,
contribution shares, common and rare NPC tables, and existing equipment/potion
modifiers.

## Drop Calculation and LootShare Composition

For each personal reward recipient, construct one immutable
`DropRewardContext` containing:

- recipient identity and lifecycle generation;
- NPC and drop-family identity;
- the recipient's contribution scale;
- the recipient's valid Ante snapshot and common/rare multipliers;
- existing Luck, Wealth, Cosmic, and bad-luck facts; and
- stable RNG/event identity for diagnostics and deterministic tests.

The conceptual rare chance is:

`effective rare chance = base chance × contribution factor × Ante factor × existing applicable modifiers`

The conceptual common occurrence is:

`effective common frequency = current common opportunity × Ante common factor`

This is an ownership model, not permission to multiply every current modifier
blindly. Existing modifiers use different mechanics—weight adjustment, extra
rolls, or gates—and must retain those mechanics. Ante is integrated exactly
once at each audited common or rare seam and must not add an independent second
RNG gate when its multiplier is `1.00x`. At the normal threshold, the same RNG
stream must reproduce current results.

Rules:

- contribution and Ante are personal; never average them across a party;
- no player receives another player's Ante multiplier;
- common and rare chances are capped safely after applicable composition;
- ordinary common frequency scales, while item quantities and relative common
  weights remain unchanged;
- guaranteed bones, fixed quest drops, true always-drops, and scripted awards
  remain unchanged;
- bad-luck state must not be reset, double-incremented, or made easier to farm
  by toggling Ante;
- Wealth and Cosmic extra-roll cardinality remains unchanged;
- a shared or reassigned item retains the reward context of the player whose
  personal roll created it; and
- diagnostics must identify base, contribution, Ante, and other modifier facts
  without exposing future RNG outcomes to the client.

The rare-family inventory must explicitly decide whether “rare normal” remains
a maintained ID/weight policy or becomes authored metadata. Ante must not use
sale price alone to decide rarity.

## Selection Lifecycle and Combat Lock

### Out of combat

Players may open Ante, select or unselect eligible inventory instances, clear
the list, and inspect the projected threshold and multiplier. Every edit is a
server-validated revision. The client refreshes from the authoritative
response after success or rejection.

### During hostile combat

Ante edits are locked while the player has a live hostile engagement and for a
short reviewed post-damage grace window. This prevents unselecting risk moments
before a lethal hit or selecting high value only after an NPC is effectively
dead. The UI remains readable and explains why editing is locked.

Normal item use still applies. If a selected item is consumed, transformed, or
leaves inventory through an otherwise legal action, it stops contributing.
The system must not create a phantom-value snapshot for an item the player no
longer risks.

### Login and persistence

The selection persists by character and item-instance ID. Login performs a
full fail-closed reconciliation against loaded inventory. Missing, duplicated,
ineligible, or owner-mismatched instances are removed from the selection and
the client receives the corrected snapshot.

No selected item is stored outside the ordinary inventory tables. Ante
persistence stores only intent and revisions, never a second copy of an item.

## Death Settlement

For an Ante-covered death:

1. capture one immutable pre-death Ante snapshot before inventory mutation;
2. identify every selected, still-present, eligible item instance;
3. preserve all unselected inventory and equipment covered by the Ante policy;
4. remove/drop every selected instance using the approved death-loss
   ownership and ground-item lifecycle;
5. record exact selected, present, lost, exempted, and invalidated instances;
6. clear selections for items no longer owned; and
7. send refreshed inventory, equipment, death preview, and Ante state.

Ante items must not then be rescued by the ordinary three-item keep rule,
Protect Item compatibility, or Soul-necklace extra-kept slots. Otherwise the
displayed risk and reward would disagree. Conversely, non-Ante items must not
fall through into the legacy iterator during an Ante-covered death.

Selected Ante items use the existing recoverable PvM ground-drop lifecycle.
They are not permanently destroyed and do not enter a new reclaim service.
Ground ownership, visibility, and cleanup timing remain the current PvM death
contract and must be shown plainly in the interface. Non-tradeable items never
enter this path because they cannot be selected for Ante.

### Simultaneous player and NPC death

If a player's death and an NPC's death settle in the same combat resolution,
the NPC reward uses the same valid pre-death Ante snapshot that governed the
player's loss. Processing player inventory removal first must not reduce the
reward to empty Ante, and processing the NPC first must not spare selected
items. Tests must cover both callback orders and exactly-once settlement.

## Confirmed Death Scope

Ante is **PvM-only** on the maintained Spoiled Milk world:

- NPC-caused PvM death: Ante policy applies, including in the Wilderness unless
  another explicit safe/scripted activity contract takes authority;
- player-caused PvP death: retain current PvP loss and ownership rules;
- active duel: retain stake/death rules;
- Ultimate Ironman: retain its existing full-loss identity until explicitly
  reviewed;
- Hardcore Ironman: Ante may govern items, but death still removes Hardcore
  status;
- safe minigame, tutorial, scripted, and staff/test deaths: retain their
  explicit existing contracts.

Ante never changes PvP rewards, player-kill ownership, player-caused Wilderness
item loss, or duel stakes. Location alone does not decide the policy: an
NPC-caused death remains PvM, while a player-caused death remains PvP. Any
future PvP risk system requires a separate owner-approved design rather than an
extension of this plan.

## Maintained-Client Interface

### Entry point

Add an **Ante** entry beside or within the maintained client's existing Items
on Death surface. The legacy preview is the natural discovery point, but Ante
gets its own protocol-backed panel rather than deriving server state locally.

Authentic clients cannot safely select instance-bound Ante items. The rollout
must choose and enforce one explicit fallback: retain legacy death rules for
those clients, or block Ante-enabled world entry with a clear version message.
Never silently grant full protection while also granting normal rates.

### Required layout

The interface should show:

- the player's current unstackable inventory grid;
- a clear selected/unselected state on each eligible item;
- item name and authoritative Ante value on hover;
- why an item is ineligible;
- selected item count and total value;
- current combat level and normal-value threshold;
- current ratio and projected rare-reward multiplier;
- a sliding `Low risk` → `Normal` → `High risk` meter;
- a plain-language death result: “Selected items are at risk; other covered
  items are protected”;
- that selected losses use the recoverable PvM ground-drop lifecycle;
- combat-lock status;
- `Clear`, `Revert`, and explicit `Apply` controls; and
- a high-value confirmation when a revision materially raises exposed value.

The display must update when inventory, combat level, valuation, eligibility,
or selection revision changes. Client-side preview math may animate the meter,
but the final total and multiplier come from the server snapshot.

## Server Architecture

Keep feature ownership narrow:

- `AnteSelectionService`: validates edits and owns persistence/reconciliation;
- `AnteValuationCatalog`: item eligibility and authoritative value;
- `AnteThresholdProfile`: combat-level threshold ladder;
- `AnteRewardCurve`: bounded common- and rare-ratio calculations;
- `AnteRiskSnapshot`: immutable selection/value/lifecycle fact;
- `DropRewardContext`: composes contribution, Ante, and existing modifiers;
- `AnteDeathPolicy`: selects and settles at-risk instances; and
- versioned custom protocol structs/handlers for snapshots and revisions.

Do not place UI state in `Player` as scattered cache keys or call client code
from drop tables. Do not make `DropTable` responsible for inventory selection
or death policy. Feature configuration belongs in a validated Spoiled Milk
profile with safe startup defaults.

## Persistence and Migration

Prefer a small normalized character-selection table keyed by character and
item-instance ID, with a monotonic revision. The exact schema must support both
MySQL and SQLite and obey existing patch ordering.

Rollout rules:

- existing characters begin with no selected items;
- no item or bank row is rewritten merely to enable Ante;
- stale selection rows fail closed and are pruned after inventory load;
- rollback can disable reward/death integration without deleting player items;
- disabling the feature must explicitly restore legacy death and ordinary
  reward behavior; and
- release compatibility must prevent an old client from misrepresenting the
  active death contract.

## Economy and Abuse Safeguards

- Audit free, shop-cheap, renewable, quest, and artificially high-default-price
  items before approving valuation.
- Cap the high-risk multiplier and use diminishing returns.
- Do not allow a selected item to count after banking, trading, dropping,
  transforming, or consuming it.
- Do not allow edits during hostile combat or the reviewed grace window.
- Prevent duplicate item-instance IDs and cross-character selection rows.
- Log and reject client totals, values, ratios, or item identities that differ
  from server state.
- Ensure safe-spotted or trivial NPC farming does not receive an unintended
  additional multiplier beyond the approved player-combat-level model.
- Preserve current recoverable PvM ground-drop ownership and cleanup; do not
  replace it with destruction or a reclaim service inside an implementation
  branch.
- Rate-limit selection revisions and protocol requests.
- Never expose future rolls, seeds, bad-luck counters, or hidden table contents
  through the interface.

## Diagnostics and Auditability

Add bounded reason-coded diagnostics for:

- selection accepted/rejected and reason;
- reconciliation removals;
- combat-lock rejection;
- threshold, value, ratio, and multiplier at reward settlement;
- contribution and Ante factors applied to each rare family;
- selected item loss and recoverable ground-drop outcome; and
- simultaneous death/kill snapshot reuse.

Production logs must avoid unnecessary full-inventory dumps. Administrator
inspection may show a player's current selected item IDs, total, threshold,
and multiplier but must not reveal pending RNG outcomes.

## Ordered Implementation Milestones

### ANTE-0 — Economy inventory and deterministic simulator

- Enumerate item prices, stateful-value exceptions, death exclusions, combat
  levels, rare families, and current modifier composition.
- Build a deterministic offline simulator for thresholds and expected rare
  rates.
- Propose the final threshold ladder plus common and rare curves and caps.
- Change no live death or drop behavior.

Stop if normal-threshold simulations do not reproduce current reward odds.

### ANTE-1 — Server domain foundation

- Add valuation, thresholds, reward curve, immutable snapshots, selection
  validation, MySQL/SQLite persistence, reconciliation, and unit/runtime tests.
- Keep death and rewards in observation-only mode.

Stop if item identity cannot survive inventory reorder/login without catalog-
ID inheritance or duplicated ownership.

### ANTE-2 — Versioned protocol and interface

- Add server snapshot/revision packets and the maintained-client Ante panel.
- Integrate Items on Death preview and combat-lock messaging.
- Keep reward/death mutation disabled during interface acceptance.

Stop on stale-client acceptance, locally authoritative values, hidden selected
items, or an unclear death warning.

### ANTE-3 — Death policy integration

- Enable ordinary PvM Ante loss/protection behind a server flag.
- Preserve excluded death families and add simultaneous-settlement coverage.
- Audit every legacy keep/loss modifier explicitly.

Stop on duplication, failure to lose a displayed-risk item, or loss of an
unselected covered item.

### ANTE-4 — Common/rare drop-rate and LootShare integration

- Integrate Ante exactly once into ordinary common frequency and every approved
  rare family.
- Compose per-recipient contribution, current equipment/potions, and bad-luck
  behavior.
- Preserve the originating recipient context through any shared distribution.

Stop if `1.00x` changes current deterministic outcomes or if party members can
borrow another player's multiplier.

### ANTE-5 — Private economy and failure testing

- Test empty, below-normal, normal, and above-normal Ante profiles.
- Exercise death, logout, reconnect, inventory mutation, simultaneous death,
  parties, bosses, exclusions, and client-version failure.
- Run extended deterministic simulations and review telemetry volume.

Do not deploy publicly until death-loss UI and server logs agree on every
tested item instance.

### ANTE-6 — Public rollout and tuning

- Release behind a reversible validated configuration flag.
- Publish the PvM-only death scope, recoverable-loss behavior,
  normal-threshold meaning, and approved common/rare curves to players.
- Monitor value bands, death losses, expected-vs-observed rare outcomes, party
  composition, and item inflation.
- Tune data tables rather than patching formulas throughout drop code.

## Verification Matrix

Server/runtime coverage must include:

- empty selection and full protection in an Ante-covered death;
- selected item loss and unselected item preservation;
- duplicate catalog items distinguished by item-instance ID;
- inventory reorder preserving selection;
- bank/trade/drop/use/transform invalidation;
- reconnect persistence and stale-row cleanup;
- checked-value overflow and invalid/placeholder prices;
- monotonic thresholds across every combat level;
- exact curve breakpoints and interpolation;
- edits accepted out of combat and rejected during lock;
- combat-level changes updating threshold without changing selection;
- normal threshold reproducing current RNG outcomes;
- below/above-threshold common-frequency scaling without quantity inflation;
- below/above-threshold scaling for marked tables, rare normal drops, hidden
  uniques, and custom rare paths;
- guaranteed bones, fixed quest drops, true always-drops, and scripted awards
  remaining unchanged;
- contribution and Ante applied once per recipient;
- Wealth, Cosmic, Luck, and bad-luck behavior preserved;
- no party-member multiplier borrowing;
- simultaneous NPC/player death using one pre-death snapshot;
- PvP, duel, Ironman, tutorial, safe-death, and staff/test boundaries;
- exactly-once death removal, ground registration, logging, and reward roll;
- malformed/stale/replayed client revisions rejected; and
- authentic/old-client fallback behaving exactly as approved.

Client coverage must include resizing, borderless/windowed modes, icon/text
clarity, inventory mutation while open, combat lock, high-value confirmation,
empty selection, inaccessible items, hover details, and protocol-version
failure.

Run authoritative server/plugin/client builds, the combat characterization
gate, personal-loot/drop tests, death lifecycle tests, item-integrity and bank
tests, party tests, database patch tests, deterministic RNG tests, artifact
guards, and changed-code static analysis for every implementation milestone.

## Owner Decisions Required Before ANTE-3/ANTE-4

The foundational and simulator work can begin without these final answers, but
death/reward activation cannot:

1. Approve the combat-level threshold ladder after the economy report.
2. Approve the low-risk floors, high-risk caps, breakpoints, and interpolation
   for common and rare rewards after simulation.
3. Decide whether the common and rare curves use the same values or separate
   tuning while retaining the same normal threshold.
4. Choose the authentic/old-client fallback contract.

## Acceptance Criteria

Ante is complete only when:

- the player can plainly see which exact inventory items are at risk;
- those items remain in ordinary inventory with no duplicate escrow copy;
- death loses every displayed-risk item and no other covered item;
- zero risk produces the approved low common and rare rates;
- the combat-scaled threshold reproduces ordinary common and rare rates;
- additional real risk improves both rates only up to their approved caps;
- personal contribution/LootShare and Ante compose once and per recipient;
- common frequency, all rare families, and existing drop modifiers have
  explicit tests;
- simultaneous death cannot erase either the promised reward factor or item
  loss;
- old clients cannot unknowingly operate under a misrepresented death policy;
  and
- private testing validates both the economy model and the player-facing death
  experience before public rollout.
