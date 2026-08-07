# A06.4 projectile resource and progression ledger

Status: implemented and executable on the focused A06.4 branch; manager review
and integration remain the publication boundary.

## Outcome and boundary

Every maintained projectile producer now has an explicit resource-coverage
classification. Player bow, thrown, shuriken, combat-magic, cannon, and
Legends holy-water launches attach a sealed, per-event receipt describing work
that their existing producer already completed. The receipt records facts; it
does not remove or grant items, roll recovery, award experience, refund a
launch, or decide whether an impact may execute.

This boundary deliberately preserves the current economy:

- ammunition and runes are removed before the delayed projectile impact;
- Ranged hit XP and Magic base-cast XP remain launch-time awards;
- arrow and thrown-item recovery remains a launch-time roll at the target's
  launch-time location;
- an invalid, failed, duplicated, or canceled impact does not refund a cost or
  revoke XP/recovery already settled at launch; and
- stopping the scheduler or server after launch does not replay or refund the
  receipt.

Moving any of those policies to impact time, using applied impact damage for
XP, or moving recovery to the eventual impact tile remains a separate gameplay
and economy decision.

## Typed receipt

`ProjectileResourceLedger` binds once to the projectile event UUID and stable
`ProjectileLaunchSpecification.Producer`. A tracked receipt can contain:

- item identity, requested and actually removed quantity, inventory/equipment
  source, and a preservation reason;
- recovery identity, quantity, destination, and decision `WorldLocation`; and
- skill identity, base and actually applied XP, and the XP basis.

Tracked receipts are populated by the launch producer, sealed, then exposed as
immutable snapshots. Default receipts make incomplete migration visible:

- `UNRECORDED_TRACKED_PRODUCER` means a producer is expected to supply a
  receipt but used a compatibility constructor;
- `NO_PROJECTILE_RESOURCES` means the producer has no launch-owned item or XP
  economy;
- `CALLER_OWNED` preserves an economy outside the projectile event; and
- `UNCLASSIFIED_COMPATIBILITY` labels a positional legacy constructor whose
  resource policy cannot be inferred safely.

## Current settlement inventory

| Producer family | Current launch settlement | Receipt policy |
| --- | --- | --- |
| Player bow | One compatible arrow/bolt from the equipment slot or wielded legacy inventory; optional Ranged hit XP; one recovery roll | Tracked item cost, XP decision, and recovery decision |
| Ordinary thrown | One equipped/wielded thrown item; optional Ranged hit XP; one recovery roll | One tracked receipt per launched item |
| Shuriken volley | Up to three items removed together, then one damage/recovery/XP decision per selected target | One independently bound tracked receipt per child; the child totals equal the bulk removal |
| Combat Magic | Required runes after the existing second cape/staff/robe preservation check; base cast XP during `finalizeSpell` | Tracked requested/removed rune vector, preservation reason, and actual XP |
| Iban blast | Same rune and cast-XP rules as combat Magic | Separate `PLAYER_IBAN_MAGIC` tracked identity |
| Scripted Magic debuffs | Same rune and cast-XP rules; delayed callback owns only the effect | Separate `MAGIC_SCRIPTED_EFFECT` tracked identity |
| Cannon | One cannonball from inventory; no XP and no recovery | Tracked item cost only |
| Legends holy water | One equipped vial, including the retained removal-failure compatibility result | Tracked item cost with explicit failure reason when removal returns `-1` |
| Ordinary NPC, summon, and admin projectiles | No player item, recovery, or XP settlement | `NO_PROJECTILE_RESOURCES` |
| Gnome Ball | Ball transfer/removal remains in the minigame callback/caller | `CALLER_OWNED`; no transfer moved into the projectile |
| Positional damaging constructor | Caller purpose is not stable enough to infer an economy | `UNCLASSIFIED_COMPATIBILITY` |
| Positional benign constructor | No projectile-owned economy is established | `NO_PROJECTILE_RESOURCES` |

### Bow and thrown recovery order

`RangeUtils.settleProjectileRecovery` exposes the result of the established
single recovery operation without changing its order:

1. the server combat RNG decides whether the projectile survives;
2. Ring of Avarice gets the first collection opportunity;
3. Loot Goblin gets the second opportunity;
4. an eligible existing owned stack at the target location is increased; or
5. a new player-owned ground stack is created at that location.

The shared helper preserves the difference between stackable arrows and
non-stackable thrown items. `handleArrowLossAndDrop` remains as a compatibility
facade for callers that do not need the typed result.

### Magic preservation

The combat-cast path intentionally performs its established two checks. The
first validates the walk-to cast and rolls Magic Cape/staff/robe preservation;
the second occurs immediately before removal. The ledger observes the second,
authoritative removal result. It distinguishes:

- full Magic Cape preservation;
- per-rune equipment-effect preservation;
- ordinary full removal; and
- the retained case where a planned removal reports less than requested.

No rune chance, iteration order, cast message, sound, timer, or XP call was
moved.

## Compatibility and stop conditions

`RangeEventNpc` is retained by the `npcshootnpc` and `npcrangedplayer` admin
commands. Its existing callback asks the NPC-owned event for a player owner and
therefore reaches a null attacker before its generated bronze-arrow recovery
or projectile launch. A06.4 neither repairs nor hides that behavior: doing so
would reactivate/change an admin combat helper and belongs on a separately
approved compatibility branch. Its post-check receipt code is present for the
existing recovery policy, while default construction continues to label an
unrecorded tracked launch if another caller bypasses it.

No A06.4 code reads a receipt during impact, death, logout, or shutdown. Stop
and seek explicit approval if a follow-up requires a receipt to become refund,
mutation, XP, recovery, or impact authority.

## Executable verification

`CurrentCombatProjectileResourceCharacterization` covers:

- all producer coverage defaults, stable binding, sealing, immutable list
  views, and rejected duplicate binding/mutation;
- real equipment-mode and legacy inventory-mode bow removal;
- real ordinary thrown and three-child shuriken settlement;
- per-child cost, XP, recovery, and event identity conservation;
- normal rune removal, deterministic Magic Cape preservation, deterministic
  staff preservation, and base/actual cast XP;
- recovery loss, new ground stack, existing stack, ownership and exact world
  location, Ring of Avarice, Loot Goblin, and inventory-full ground fallback;
- cannon cost with no invented XP or recovery;
- logout, source death, invalid impact, duplicate callback, deliberate callback
  failure, sibling callbacks, and scheduler shutdown; and
- the rule that none of those terminal paths replays or refunds launch work.

The authoritative combat gate contains 83 executable scenarios after this
slice. A06.4 changes no visible timing, range, movement, message, sound,
animation, or packet behavior, so a private visual checkpoint is not required
by the A06 stop condition.
