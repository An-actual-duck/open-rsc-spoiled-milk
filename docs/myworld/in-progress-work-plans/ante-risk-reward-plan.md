# Ante Risk-and-Reward Plan

## Status and Purpose

Status: **CONFIRMED FEATURE DIRECTION; IMPLEMENTATION NOT STARTED.**

Ante replaces unavoidable PvM inventory loss with a player-authored risk
contract. A player chooses which eligible unstackable inventory items they are
willing to lose on death. Those items remain in the inventory rather than
moving into escrow. The value exposed through Ante determines the player's
rare-reward rate:

- low risk means a longer rare-drop grind;
- reaching the combat-level-scaled normal threshold restores ordinary rates;
- exceeding that threshold improves rare-reward chances; and
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
  lowest rare-reward rate.
- Every combat level has a normal-value threshold. The threshold rises
  monotonically as the player's combat level rises.
- Reaching the threshold gives the current normal rare-reward rate. Ante value
  below it reduces that rate; value above it increases that rate.
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
deaths, Ultimate Ironman rules, staff exceptions, untradeable-ground-item
behavior, and PvP ownership all require explicit compatibility decisions.

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

Guaranteed bones, fixed quest drops, always-drops, scripted awards, currencies,
and ordinary common drops should not silently inherit Ante scaling.

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
- the exact item instance must be in inventory, not merely equipped or banked;
- server placeholders, administrator/debug items, and invalid definitions are
  ineligible;
- an item that normal death rules cannot genuinely remove must not provide
  reward value without an explicit destruction contract; and
- irreplaceable quest, account-bound, or recovery-sensitive items require an
  explicit allow, reject, or safe-reclaim decision before launch.

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

## Provisional Sliding Reward Curve

Ante uses a bounded multiplier `A(R)` centered on the ordinary rate:

| Ante ratio `R` | Provisional rare-reward multiplier |
| ---: | ---: |
| `0` | `0.25x` |
| `0.25` | `0.50x` |
| `0.50` | `0.75x` |
| `1.00` | `1.00x` |
| `2.00` | `1.25x` |
| `4.00+` | `1.50x` cap |

Interpolate continuously between breakpoints. This is a recommended tuning
starting point, not a settled number table. It gives an empty Ante a real but
long grind, restores today's odds at the combat-scaled threshold, and applies
diminishing returns above normal so extreme wealth cannot trivialize rare
rewards.

Before final approval, a simulator must report expected kills per reward and
expected item loss exposure at representative combat levels, thresholds,
contribution shares, NPC tables, and existing equipment/potion modifiers.

## Drop Calculation and LootShare Composition

For each personal reward recipient, construct one immutable
`RareRewardContext` containing:

- recipient identity and lifecycle generation;
- NPC and drop-family identity;
- the recipient's contribution scale;
- the recipient's valid Ante snapshot and multiplier;
- existing Luck, Wealth, Cosmic, and bad-luck facts; and
- stable RNG/event identity for diagnostics and deterministic tests.

The conceptual chance is:

`effective rare chance = base chance × contribution factor × Ante factor × existing applicable modifiers`

This is an ownership model, not permission to multiply every current modifier
blindly. Existing modifiers use different mechanics—weight adjustment, extra
rolls, or gates—and must retain those mechanics. Ante is integrated exactly
once at each audited rare seam and must not add an independent second RNG gate
when its multiplier is `1.00x`. At the normal threshold, the same RNG stream
must reproduce current results.

Rules:

- contribution and Ante are personal; never average them across a party;
- no player receives another player's Ante multiplier;
- chance is capped at `100%` after applicable composition;
- ordinary guaranteed/common rewards remain unchanged unless separately
  approved;
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

The selected items follow normal recoverability unless final design explicitly
chooses destruction. Public ground ownership, private reclaim windows,
untradeable behavior, and world cleanup must be shown in the interface rather
than described vaguely as “lost.”

### Simultaneous player and NPC death

If a player's death and an NPC's death settle in the same combat resolution,
the NPC reward uses the same valid pre-death Ante snapshot that governed the
player's loss. Processing player inventory removal first must not reduce the
reward to empty Ante, and processing the NPC first must not spare selected
items. Tests must cover both callback orders and exactly-once settlement.

## Recommended Initial Death Scope

The safest first release is **ordinary PvM deaths on the maintained Spoiled
Milk world**:

- PvM death: Ante policy applies;
- PvP/Wilderness death: retain current PvP loss and ownership rules;
- active duel: retain stake/death rules;
- Ultimate Ironman: retain its existing full-loss identity until explicitly
  reviewed;
- Hardcore Ironman: Ante may govern items, but death still removes Hardcore
  status;
- safe minigame, tutorial, scripted, and staff/test deaths: retain their
  explicit existing contracts.

This boundary is recommended, not yet owner-confirmed. Extending Ante to PvP
would change player-kill incentives and cannot be inferred from a PvM rare-drop
system.

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
- whether selected losses are ground-recoverable or destroyed;
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
- `AnteRewardCurve`: bounded ratio-to-multiplier calculation;
- `AnteRiskSnapshot`: immutable selection/value/lifecycle fact;
- `RareRewardContext`: composes contribution, Ante, and existing modifiers;
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
- Confirm whether recoverable ground drops provide enough risk. If not, use a
  separate reviewed destruction or reclaim contract rather than quietly
  changing ordinary ground-item ownership.
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
- selected item loss and ground/reclaim outcome; and
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
- Propose the final threshold ladder, curve, cap, and initial death scope.
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

### ANTE-4 — Rare-reward and LootShare integration

- Integrate Ante exactly once into every approved rare family.
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
- Publish the exact death scope, normal-threshold meaning, and capped reward
  curve to players.
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
- below/above-threshold scaling for marked tables, rare normal drops, hidden
  uniques, and custom rare paths;
- guaranteed/common/quest drops remaining unchanged;
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

1. Confirm ordinary PvM as the initial death scope and retain current PvP,
   duel, and Ultimate Ironman rules.
2. Decide whether lost Ante items use current recoverable ground-drop behavior,
   a private reclaim window, or permanent destruction.
3. Approve the item safety exclusions, especially untradeable and irreplaceable
   quest items.
4. Approve the combat-level threshold ladder after the economy report.
5. Approve the low-risk floor, high-risk cap, and interpolation curve after
   simulation.
6. Confirm that guaranteed/common drops remain unchanged and Ante affects only
   reviewed rare-reward opportunities.
7. Choose the authentic/old-client fallback contract.

## Acceptance Criteria

Ante is complete only when:

- the player can plainly see which exact inventory items are at risk;
- those items remain in ordinary inventory with no duplicate escrow copy;
- death loses every displayed-risk item and no other covered item;
- zero risk produces the approved low rare rate;
- the combat-scaled threshold reproduces ordinary rates;
- additional real risk improves rare rates only up to the approved cap;
- personal contribution/LootShare and Ante compose once and per recipient;
- all rare families and existing drop modifiers have explicit tests;
- simultaneous death cannot erase either the promised reward factor or item
  loss;
- old clients cannot unknowingly operate under a misrepresented death policy;
  and
- private testing validates both the economy model and the player-facing death
  experience before public rollout.
