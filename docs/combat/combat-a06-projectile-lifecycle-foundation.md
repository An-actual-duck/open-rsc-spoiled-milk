# A06 projectile lifecycle foundation

Status: A06.1 implementation complete on the focused foundation branch; the
producer-policy and resource-settlement slices below remain pending.

## Outcome and boundary

This slice gives every current `ProjectileEvent`, `CustomProjectileEvent`, and
`BallProjectileEvent` an immutable launch snapshot and a bounded, per-event
impact ledger. The ledger owns exactly one delayed callback. It does not own
damage, death, ammunition, runes, recovery, experience, collision, or combat
eligibility.

The one intentional behavior correction is duplicate callback suppression. A
second or concurrent callback is recorded as `DUPLICATE_CALLBACK` and cannot
repeat damage, effects, scripted work, contribution, death, or packets. A
callback that throws after taking ownership becomes `FAILED`; it is not
replayed after potentially partial work. The scheduler already stops a failed
single-tick event, so this makes manual, restored, or re-entrant invocation
obey the same one-use rule.

Everything else stays on the characterized current policy:

- the launch visual is emitted in the constructor before delayed impact;
- ordinary damaging projectiles settle one tick later;
- base `ProjectileEvent` checks only the existing cancellation flag and the
  existing `caster.withinRange(opponent, 15)` gate at impact;
- that range call retains its current same-world-space and signed-level
  behavior when layered spatial authority is enabled;
- `CustomProjectileEvent` checks cancellation but does not gain a range gate;
- base benign cleanup ignores its dormant cancellation flag, while
  `BallProjectileEvent` continues to honor it;
- participant lifecycle changes alone do not invalidate an impact;
- a launched hit currently persists after source death and after target
  removal when the actors remain inside the spatial gate; and
- no impact collision/path check is added.

The last three rules are recorded compatibility facts, not endorsements. They
must not be changed without the A06.3 characterization and explicit policy
decision described below.

## Foundation records

`ProjectileLaunchSnapshot` freezes:

- scheduler event ID, launch tick, and expected impact tick;
- source and target UUID/lifecycle/session/world snapshots through the existing
  `CombatParticipantSnapshot`;
- source and target launch `WorldLocation`, including world space and signed
  level;
- damaging, scripted-effect, or benign-effect classification;
- a stable current family key;
- attack type, projectile visual, impact effect, proposed launch damage, and
  whether a visual was requested.

It intentionally retains no mutable `Mob` handle and grants no mutation
authority. The participant snapshots can prove later lifecycle drift, but this
slice does not use that evidence to reject an impact.

`ProjectileImpactLedger` is event-local and has no server-global retention.
Its state machine is:

`LAUNCHED -> VALIDATING -> SETTLING -> SETTLED`

Validation may instead end at `INVALIDATED`, and a failure after callback
ownership ends at `FAILED`. Any callback after the first receives a typed
duplicate decision without changing the initial decision or terminal state.
This is deliberately separate from the future economy ledger.

## Current producer and family inventory

| Current producer/path | Stable launch family | Current launch ownership | Foundation treatment |
| --- | --- | --- | --- |
| `RangeEvent` bow/crossbow | `player-bow-projectile` | Ammo removal, damage roll, hit XP, recovery/drop decision, sound, cadence, and visual enqueue occur before impact | Snapshot and one-use delayed callback only |
| `ThrowingEvent` knives/darts | `player-thrown-projectile` | Item removal, damage/XP, recovery/drop, sound, and cadence occur before impact | Snapshot and one-use delayed callback only |
| `ThrowingEvent` shuriken child | `player-shuriken-projectile` | One removed item and one launch-time damage/XP/recovery decision per selected child | Each child gets an independent snapshot/ledger |
| `SpellHandler` ordinary/god magic | `player-magic-projectile` | Rune preservation/removal, base Magic XP, cast feedback, damage roll, and visual enqueue occur before impact | Snapshot and one-use delayed callback only |
| `SpellHandler` Iban primary | `iban-magic-projectile` | Same spell launch authority; the separate delayed area child remains outside this ledger | Primary projectile only |
| `NpcBehavior` ranged/magic | `npc-ranged-projectile` / `npc-magic-projectile` | Profile range/path/prayer checks and damage roll occur at launch | Snapshot and one-use delayed callback only |
| `Summoning` ranged/magic | `summon-ranged-projectile` / `summon-magic-projectile` | Summon range/path/cadence and damage roll occur at launch | Snapshot and one-use delayed callback only |
| `FireCannonEvent` | `cannon-projectile` | Cannonball removal, target choice, facing, and damage roll occur before impact | Snapshot and one-use delayed callback only |
| `RangeEventNpc` compatibility path | NPC ranged family | Legacy launch can manufacture/drop bronze arrows before impact | Preserved compatibility path; no economy change |
| admin/signed/unclassified `ProjectileEvent` | NPC family when NPC-owned, otherwise `compatibility-projectile` | Active signed-damage compatibility permits negative input | Snapshot accepts current signed input; A05 compatibility authority is unchanged |
| `CustomProjectileEvent` spell/quest effect | `custom-projectile` | Producer owns runes/items and `doSpell`; no current impact range check | Typed scripted snapshot and one-use callback |
| gnome-ball `BallProjectileEvent` | `ball-projectile` | Producer/script owns pass, goal, and score behavior | Typed benign snapshot and one-use callback |

The family key is current event-level classification, not yet a typed producer
specification. In particular, the retained admin NPC projectile and ordinary
NPC projectile are indistinguishable once they reach the positional
constructor. A06.2 must let producers declare that distinction without
removing compatibility constructors prematurely.

Chaos chain lightning is not an independent delayed projectile in the current
server. Its visual and child damage execute immediately inside the primary
projectile's authorized callback. It therefore inherits the parent's one-use
ledger in this slice. Giving each hop a separate launch/impact identity would
change child order and belongs with the A07 effect-policy work, not this
foundation.

## Verified current impact policy

The executable fixture freezes the following before and after this extraction:

| Condition after launch | Current result retained by A06.1 |
| --- | --- |
| Explicit `setCanceled(true)` | Visual remains; damage/effects are suppressed |
| Target moves beyond 15 tiles | Impact invalidates with `OUTSIDE_CURRENT_SPATIAL_GATE` |
| Target changes signed level | The same spatial gate invalidates the impact |
| Source and target advance combat lifecycle but remain live/in range | Impact settles; immutable snapshots report the generation mismatch |
| Source reaches zero Hits and advances lifecycle | Primary impact still settles, then existing post-impact work stops |
| Target is removed but remains at its tile | Impact still settles through the existing removed-target compatibility path |
| Same callback is invoked twice | First settles; second is typed duplicate and has no gameplay effect |
| Scripted or ball callback is invoked twice | `doSpell` executes once |
| Scripted callback throws | Failure escapes once and cannot replay partial work |

The fixture also verifies launch family parity for player bow, thrown,
shuriken, magic, NPC ranged/magic, summon ranged/magic, Iban, cannon, and the
unclassified/debug compatibility family. Existing A05 scenarios continue to
verify damage request identities, mitigation, contribution, reflection,
secondary-effect order, hitsplats, death, and packets downstream of an
authorized impact.

## Preserved compatibility surfaces

- All positional `ProjectileEvent` constructors remain available to core and
  plugin code.
- `setCanceled(boolean)` remains the compatibility facade. Repository search
  still finds death/walk readers for the historical `"projectile"` player
  attribute but no active writer; this branch does not delete or reinterpret
  that dormant bridge.
- `RangeEventNpc`, signed admin projectile values, custom spell subclasses,
  Legends holy water, and gnome-ball subclasses remain active or maintained
  compatibility boundaries.
- No RNG call, resource mutation, formula, range, projectile speed, prayer
  check, animation, hitsplat, sound, or authentic-client packet was moved.
- Snapshot capture occurs before the existing visual call but performs no RNG,
  packet, resource, or world mutation.

## Ordered A06 follow-up branches

### A06.2 — typed producer launch specification

Status: implemented on the focused A06.2 branch. All 22 tracked production
construction sites now use immutable named specifications; positional
constructors remain compatibility facades. See
[`combat-a06-projectile-launch-specifications.md`](combat-a06-projectile-launch-specifications.md).

Inventory every core and plugin constructor call, then introduce named launch
specifications at producers while retaining positional compatibility facades.
Freeze effect parameters currently written by constructor tails, including
magic element, dual-element procs, poison weapon, dragon breath, blood spell,
chase, and visibility. Give admin debug, legacy NPC, holy-water, and gnome-ball
paths explicit producer identities.

Stop if construction changes damage-roll timing, visual/packet order, plugin
source compatibility, or the number/order of child events. Required tests are
one real launch per family, constructor-facade parity, immutable parameter
mutation checks, authentic-client packet order, and artifact checks proving
test classes remain excluded.

### A06.3 — impact eligibility policy

Use the captured participant and `WorldLocation` evidence to decide, per
family, target/source death, logout, removal/respawn, teleport, world-space or
level change, retarget, movement, and semantic projectile collision. Do not
copy the upstream source-death persistence, launch-origin range, or collision
rules as defaults.

This branch requires owner decisions for the currently visible deltas:
whether removed/dead targets retain launched hits; which families persist
after source death/logout; whether distance is measured from current source or
launch origin; and whether a door/wall appearing during flight blocks impact.
Stop if one rule cannot be isolated from damage, effect, or packet ordering.
Tests must cover death, logout/reconnect, removal/respawn/reused identity,
teleport, signed layer/world-space change, long movement, collision changes,
retarget, protected launch behavior, siblings, and every invalidation reason.

### A06.4 — resource and progression settlement ledger

Characterize and then type the existing launch-time economy separately for
bow, thrown, shuriken volleys, magic, cannon, and retained compatibility
paths. Required facts include equipment versus inventory removal, cape/staff
preservation, requested versus removed quantities, hit/base XP, recovery RNG,
Ring of Avarice, Loot Goblin, ground stacking/ownership/location, inventory
full, invalid impact, source logout/death, and shutdown.

No launch-time XP or recovery behavior may be moved merely to match the
upstream project. Any proposal to make XP use actual impact damage or move
recovery to the impact tile is a gameplay/economy change and needs explicit
approval. Conservation and duplicate-callback tests must prove each cost,
award, collection, and drop happens exactly once across normal, invalid,
failed, sibling, and shutdown paths.

## Verification gates

For this foundation and every follow-up:

1. run `./server/test_combat` and retain a nonzero scenario receipt;
2. run authoritative server core and plugin builds;
3. verify core/plugin artifacts and plugin discovery;
4. run changed-code compiler warnings and the repository's focused static
   analyzers without broad baseline churn;
5. confirm test classes are absent from production artifacts; and
6. stop for private gameplay inspection if a later slice changes any visible
   timing, range, movement, effect, message, sound, or animation.
