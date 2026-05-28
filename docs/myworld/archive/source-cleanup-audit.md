# MyWorld Source Cleanup Audit

Authoritative prerequisite:

- Check
  [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
  before treating any family as still standard.
- Record family retirement/reuse decisions in
  [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md).

This note tracks the `MyWorld` cleanup work that removed or redirected obsolete
item inflow from active gameplay.

It is intentionally narrower than the general tasklists: this file is for
documenting what changed in shops, crafting outputs, quest rewards, spell
outputs, and other acquisition paths so the refactor does not lose track of
what was already retired.

## Confirmed Removed Or Redirected

### Standard enchanted-jewelry spell outputs

The old direct `Magic`-enchant path no longer produces the legacy enchanted
jewelry outputs during active play.

Confirmed redirects:

- [`SpellHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java)
  now tells players to use altar-based enchanting instead of enchanting:
  - sapphire amulets and rings
  - emerald amulets and rings
  - ruby amulets and rings
  - diamond amulets and rings
  - dragonstone amulets, rings, and necklaces
- The old `opal ring` enchant path is also blocked there and now reports that
  it no longer has a separate enchantment path.

Impact:

- old spell-output jewelry no longer re-enters live play through the original
  enchant spells
- the altar-based `MyWorld` jewelry system is now the active route

### Old dragonstone amulet fountain path

The legacy charged-dragonstone route has now been removed as a live code path.

Current state:

- the old fountain-based dragonstone progression no longer exists as an active
  plugin path
- the legacy `Dragonstone Amulet` and `Charged Dragonstone Amulet` items are
  retained only as inert compatibility baggage
- altar-based enchanting remains the only supported dragonstone jewelry upgrade
  route

### Opal ring and dwarven-ring path

The old partial-tier opal path has already been retired from active gameplay.

Confirmed state:

- [`equipment-tasklist.md`](/home/justin/Core-Framework/docs/myworld/equipment-tasklist.md)
  records:
  - `Opal ring` retired as active equipment content
  - old opal enchant path removed from active gameplay
  - dwarven-ring cannonball bonus folded into standard gameplay
- [`equipment-transition-audit.md`](/home/justin/Core-Framework/docs/myworld/equipment-transition-audit.md)
  records:
  - `Opal ring` crafting removed from active gameplay
  - old `opal ring -> Dwarven ring` enchant path removed
  - `Opal ring` no longer forced equipable
- [`Smelting.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java)
  now grants the old dwarven-ring cannonball bonus as standard cannonball
  production behavior when configured

Impact:

- the old opal-specific jewelry branch no longer acts as a live progression path
- cannonball utility was preserved without keeping the ring line active
- the old `Dwarven ring` item is now inert compatibility baggage rather than a
  live wearable

### Crowns removed from live crafting menu

Crowns are still a separate subsystem internally, but they are no longer meant
to compete with the live `MyWorld` jewelry direction.

Confirmed redirects and removals:

- [`SpellHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java)
  now reports `Crowns are hidden for now.` when players try to use the old
  crown-enchant path
- [`Crafting.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java)
  no longer offers `Crown` in the live gold-jewelry shape menu
- [`test-enchanting-data.py`](/home/justin/Core-Framework/tests/myworld/test-enchanting-data.py)
  now checks that crown production stays hidden from the gold jewelry menu

Impact:

- crowns no longer enter normal play through the main gold-jewelry crafting UI
- the spell side and crafting side now agree that crowns are hidden pending a
  later redesign

### Reachable battlestaff/orb upgrade paths

The old battlestaff/orb flow has already been retired as a live acquisition
path.

Confirmed state:

- [`magic-equipment-tasklist.md`](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md)
  records that reachable battlestaff/orb upgrade paths were retired from:
  - shops
  - quest dialogue
  - spell handling

Impact:

- players no longer progress through the old orb-combine and battlestaff
  acquisition path in normal play
- the live direction is the new staff ladder plus direct altar attunement

## Confirmed Remaining Live Sources

These are still reachable in the current codebase and should remain documented
until they are intentionally remapped, replaced, or retired.

## Phase-1 Invalid Source Ledger

This section records the first concrete invalid-source buckets identified for
the broader content cleanup pass. These are not all removed yet; this is the
working ledger for the first retirement sweep.

### Legacy potion-family sources that no longer fit MyWorld

These items still exist as active sources, but they are old stock/reward
surfaces that no longer match the new potion line cleanly.

Confirmed live sources:

- [`Apothecary.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Apothecary.java)
  still tracks and cert-supports a wide legacy potion surface including:
  - old attack/strength/defense/stat-restore/prayer/super/ranging families
  - the reused MyWorld potion IDs
  - `Weapon poison`
- [`PointsStore.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/npcs/PointsStore.java)
  still sells old pre-MyWorld potion lines:
  - `Super attack potion`
  - `Super strength potion`
  - `Super defense potion`
  - `Restore prayer potion`
  - `Ranging potion`
  - `Cure poison potion`
- [`Certer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/Certer.java)
  still exposes certificate support for the old potion families, including
  `Super attack`, `Super strength`, `Super defense`, `Prayer`, `Cure poison`,
  and `Poison antidote`
- [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)
  still includes potion-family drops such as:
  - `FULL_CURE_POISON_POTION`
  - `FULL_POISON_ANTIDOTE`
  - `WEAPON_POISON`

Current interpretation:

- the item IDs are now reused for MyWorld potions, so these sources are not
  always invalid just because they mention old constants
- the invalid part is that the source balance and source identity still reflect
  the pre-rework potion ecosystem
- these need a replacement pass, not a blind delete pass

`2026-04-14` first cleanup pass:

- removed old potion-bundle stock from
  [`PointsStore.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/npcs/PointsStore.java)
- removed the apothecary's direct legacy strength-potion creation path from
  [`Apothecary.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Apothecary.java)
- removed potion certification support from
  [`Certer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/Certer.java)
  and
  [`SidneySmith.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/yanille/SidneySmith.java)
- removed the identified generic potion drops from
  [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)

What remains:

- quest/special rewards that still intentionally hand out those reused item IDs
- utility apothecary services like spot cream and vial handling, which are not
  part of the retired combat-potion inflow
- source replacement design for where the new potion line should enter play

### Legacy magic-gear sources

These references are now split between actual live utility content and inert
compatibility records for retired magic-gear families.

Current status:

- [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)
  no longer uses the generic `Magic staff` as a standard NPC drop
- [`MonkOfEntrana.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/portsarim/MonkOfEntrana.java)
  still references battlestaff and enchanted battlestaff IDs as compatibility
  restrictions, not as evidence that those items remain live progression gear
- [`BattlestaffCrafting.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/BattlestaffCrafting.java)
  is reduced to a retirement blocker and no longer exposes the old battlestaff-
  orb output ladder as live crafting content
- [`MageArena.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java)
  still references `Magic staff` as specialty/minigame utility content

Current interpretation:

- `Dramen staff` and the renamed `Wizard staff` are intentional utility
  holdovers
- old vanilla elemental staff names are retired, but the standard staff
  elemental products remain active as rune-first items such as `Air Staff` and
  `Fire Staff`
- battlestaves, enchanted battlestaves, and orbs are retired as active
  progression and kept only as inert compatibility records
- `Magic staff` / `Wizard staff` source balance needs a separate placement
  review because it is now a utility item, not a standard caster progression
  step

`2026-04-14` first cleanup pass:

- removed the generic `Magic staff` NPC drop from
  [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)
- reduced
  [`BattlestaffCrafting.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/BattlestaffCrafting.java)
  to a minimal retirement blocker so it no longer carries the old output / XP /
  product ladder as if battlestaff crafting were still part of live progression

`2026-04-19` follow-up cleanup pass:

- replaced the old vanilla elemental staff names with rune-first standard-staff
  products in server and client item definitions
- retired the standard battlestaff IDs, enchanted battlestaff IDs, and orb IDs
  as inert compatibility records instead of removing them from the item index
- removed remaining battlestaff crafting and skill-guide presentation as live
  player-facing progression

What remains:

- `MageArena` and similar specialty content still reference `Magic staff`
- battlestaff/orb compatibility references still exist in shared rule surfaces
  like `Entrana`, but the direct combine path is now explicitly a retired
  compatibility handler rather than a hidden production route
- audit specialty utility wording around `Magic staff` / `Wizard staff` so those
  items are clearly treated as utility exceptions, not the standard staff ladder

### Legacy clothing and duplicate shop stock

Clothing and clothier cleanup is now constrained by stronger rules than this
audit used earlier.

Confirmed live sources:

- [`ThessaliasClothes.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/ThessaliasClothes.java)
  still acts as the quest-safe source for:
  - `Pink skirt`
  - `Priest robe`
  - `Priest gown`
- [`Tailor.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Tailor.java)
  previously duplicated that same legacy clothing stock
- [`PrinceAliRescue.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/PrinceAliRescue.java)
  still explicitly requires `Pink skirt`
- [`BioHazard.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java)
  and
  [`DoorAction.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java)
  still rely on `Priest robe` plus `Priest gown`

`2026-04-14` authored direction update:

- `Tailor` is now the authored members-side starter leather shop:
  - `silk`
  - base `leather`
  - `needle`
  - `thread`
  - `cow`, `goblin`, `unicorn`, and `bear` hide starter sets
- `Tailor` should not drift back into generic clothing or quest-clothing stock
- `Thessalia` remains the quest-safe compatibility shop until those quest item
  dependencies are replaced intentionally
- future authored direction is governed by the authoritative rules doc:
  only magic shops should sell clothing-type magic gear, while generic clothing
  is expected to be removed or repurposed later

What remains:

- `Thessalia` still needs a deliberate long-term replacement plan for
  quest-critical clothing compatibility before generic clothing is fully removed
- further leather-shop expansion should stay authored and limited, rather than
  turning clothiers into broad full-tier armor outlets

### Early ranged / leather shop cleanup

The full ranged-shop retiering pass is still later work, but some generic shop
stock was clearly leaking leather body armor in ways that no longer fit the
active material direction.

Confirmed live sources before cleanup:

- [`Jiminua.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Jiminua.java)
  sold `Leather armour` as general-store stock
- [`Obli.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Obli.java)
  sold `Leather armour` as general-store stock

Current interpretation:

- leather armor is now a `Crafting` / material-family problem, not generic
  ranged-shop filler or miscellaneous general-store gear
- removing obvious leather-body shortcuts from broad stores is safe even before
  the larger leather-shop redistribution is authored

`2026-04-14` first cleanup pass:

- removed `Leather armour` from
  [`Jiminua.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Jiminua.java)
- removed `Leather armour` from
  [`Obli.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/shilo/Obli.java)

What remains:

- dedicated archery-shop stock still needs a separate retier/rewrite pass
- `black` and other non-standard side tiers should not be treated as part of
  the normal shop ladder
- clothiers should not be repurposed ad hoc; their future direction now depends
  on the authoritative clothing rules

### Obsolete clothing and skirt-shop inflow

These are active shops or sources whose stock still reflects the old clothing
and skirt assumptions rather than the newer robe/leather direction.

Confirmed live sources:

- [`ThessaliasClothes.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/ThessaliasClothes.java)
  still sells:
  - `Pink skirt`
  - apron/basic clothing stock
  - priest robes mixed into a generic clothes shop
- [`Tailor.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Tailor.java)
  still sells:
  - `Pink skirt`
  - apron/basic costume stock
  - priest robes mixed into a garment shop
- [`RanaelSkirt.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/alkharid/RanaelSkirt.java)
  is still an explicit dedicated plated-skirt shop
- [`Scavvo.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Scavvo.java)
  still sells `Rune skirt` directly alongside rune weapons
- [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)
  still includes old plated-skirt drops such as `IRON_PLATED_SKIRT`

Current interpretation:

- plated skirts themselves are not invalid; they remain the female/mirrored
  plate path
- what is invalid is the old dedicated “skirt shop” identity and the generic
  old-fashion clothing stock that no longer reflects the active robe/leather
  direction
- `Pink skirt` must remain until its quest dependencies are intentionally
  remapped, because `Prince Ali Rescue` still depends on it

### High-tier shop stock that now needs re-tier review

These are not invalid by existence, but they are strong candidates for the next
 phase because they still reflect the old ladder assumptions.

Confirmed live sources:

- [`Scavvo.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Scavvo.java)
  sells rune-tier gear directly
- [`PointsStore.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/npcs/PointsStore.java)
  still sells a pre-MyWorld progression bundle
- [`NpcDrops.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java)
  still carries old rune drop tables and ultra-rare assumptions that need
  review now that rune sits at tier 10 rather than an old tier-6 endpoint

Current interpretation:

- these belong in the re-tier/distribution phases after the invalid-source
  retirements
- they are recorded here early because they are likely to be the next immediate
  source of progression mismatch after the obvious legacy removals

### Quest reward: Amulet of accuracy

- [`ImpCatcher.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ImpCatcher.java)
  still awards `Amulet of accuracy`

Current interpretation:

- this is a legacy specialty reward, not part of the standard altar-jewelry
  ladder
- it still needs the remap described in
  [`equipment-transition-audit.md`](/home/justin/Core-Framework/docs/myworld/equipment-transition-audit.md)

### Quest reward: Emerald amulet

- [`Observatory.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java)
  can still award an `Emerald amulet`

Current interpretation:

- this is a base jewelry reward, not an obsolete enchanted output
- it remains a real source of pre-enchanted jewelry input material

### Crown subsystem internals

The main crown acquisition menu has now been hidden, but crown behavior still
exists in code and still needs a later audit.

Known remaining crown surfaces:

- [`EnchantedCrowns.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/content/EnchantedCrowns.java)
- crown effect hooks referenced throughout
  [`equipment-transition-audit.md`](/home/justin/Core-Framework/docs/myworld/equipment-transition-audit.md)

Current interpretation:

- crowns are hidden from the primary live acquisition/enchant flow
- recharge/effect logic still exists and remains redesign work, not finished
  cleanup

## Current Audit Boundaries

This note only records source-cleanup facts that are confirmed by the current
code or existing MyWorld task docs.

As of this pass:

- I have confirmed removals or redirects in crafting, spell handling, and
  legacy upgrade interactions
- I have confirmed two remaining quest-reward holdovers: `Amulet of accuracy`
  and `Emerald amulet`
- I have started the first concrete invalid-source ledger for:
  - potion-family sources
  - legacy magic-gear sources
  - obsolete clothing / skirt-shop inflow
  - obvious high-tier shop/drop candidates that need the next re-tier pass
- I have not yet produced a full per-shop, per-drop-table, or per-ground-spawn
  manifest in this file

That broader source manifest is still a reasonable follow-up if the project
wants a single checklist covering:

- shops
- NPC drops
- world spawns
- quest rewards
- admin/dev kits
- replacement dialogs

## Change Log

- `2026-04-03` Added this audit note to document which old jewelry and
  enchanting acquisition paths were already removed or redirected during the
  MyWorld refactor, and which specialty holdovers still remain live.
- `2026-04-14` Added the first concrete Phase-1 invalid-source ledger covering
  potion-family sources, legacy magic-gear inflow, obsolete clothing/skirt
  shop stock, and the first obvious high-tier re-tier candidates.
