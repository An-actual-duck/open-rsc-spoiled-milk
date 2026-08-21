# MyWorld Change History

This is the consolidated record of major MyWorld changes made so far. Detailed legacy planning notes live in `docs/myworld/completed-work-plans/archive/`.

## Repository Migration

- Added root MyWorld scripts for build, run, reset, check, test, and benchmarks.
- Added root `Makefile` targets for common MyWorld workflows.
- Promoted generators to `tools/generators/`.
- Promoted benchmarks to `tools/benchmarks/`.
- Promoted tests to `tests/myworld/`.
- Kept command wrappers under `dev/myworld/` for compatibility while retaining
  `dev/myworld/assets/` as the active client visual-asset source.
- Added `server/inc/sqlite/myworld_seed.db` as the canonical local seed.
- Made `server/myworld.conf` the direct Ant default and Java no-argument fallback.
- Archived inherited OpenRSC/Cabbage docs, launchers, server configs, SQLite seeds, and Make recipes under `legacy/docs/inherited-openrsc/`.
- Reduced active server configs to `server/myworld.conf` and `server/connections.conf`.
- Reduced active SQLite databases to `myworld_seed.db` and local `myworld_dev.db`.
- Added `test-standalone-layout.py` to guard the standalone shape.
- Migration recovery is now considered complete for the current pass. Remaining
  work should be treated as normal content cleanup, polish, or feature work
  instead of migration triage.

## Core Rules And Skills

- Exalted Rune weapon poison covers the eligible Dagger and Spear while preserving each original profile; all other Exalted Rune equipment remains excluded.
- MyWorld is PvM-only.
- Old fatigue/sleep behavior is disabled for MyWorld.
- Combat now uses `Melee`, `Ranged`, and `Magic` as the active combat model instead of exposing the legacy Attack/Strength/Defense model directly to players.
- Fletching has been folded into Crafting where current production flows require it.
- Enchanting owns altar-based staff, jewelry, robe, and related magical equipment direction.
- Skillcape cleanup now follows the current skill model: the old Attack cape is
  the player-facing Melee cape, Strength and Defense cape acquisition is
  retired, Fletching cape remains a compatibility item with no normal
  acquisition path, and the Firemaking cape keeps its effects but no longer has
  the old NPC purchase path.
- Worship has a god-line allocation model with current-book UI behavior and
  skilling XP bonuses. `Prayer` remains only in compatibility identifiers and
  in ordinary references to prayers as actions/effects.
- Runecrafting was simplified around rune essence and altar use, with talisman/tiara-style progression retired from normal flow.
- Overworld rune altars now use direct altar interaction and themed altar
  presentation instead of the old mysterious-ruins entry model.
- Gathering uses tool-tier and resource-level yield logic, with stone mining and gem visibility behavior explicitly guarded.

## Combat And NPCs

- PvM combat interaction supports multi-contributor damage, damage-share XP, personal loot, and target-priority guardrails.
- NPC style profiles support melee, ranged, magic, mixed, and strength/boss override families.
- Representative combat scenario tests cover group rewards, target priority, projectile roles, and style viability.
- Combat exception tests guard reviewed direct kill/drop bypasses and PvM-only config assumptions.
- Auto-retaliate now resumes after self-heal while already attacking, can still
  start after self-heal while only being attacked, and re-engages after manual
  walk-away when an enemy pursues and attacks again.
- Combat sprites now stay anchored to map position instead of the old
  camera-relative locked-combat placement, and combat direction selection was
  adjusted after live testing.
- Dual-element spell effects now include Wood `Splinter`, which can strike one
  random additional nearby attackable NPC for half of dealt damage.
- White and Grey Knights now directly source their matching god-aligned
  equipment lines, while standard Black Knights retain ordinary drops and
  Dark Warriors/altar conversion source black god equipment.
- A MyWorld-only Wilderness overlay adds `74` hostile placements across all
  depth bands, including new Hellhound, Zamorak monk, Chaos Druid Warrior,
  Necromancer, elite Hobgoblin, and Shadow Warrior presence.
- Remaining combat work is tuning, player-facing clarity, special-item review, and future spell/effect polish rather than basic runtime implementation.

## Equipment And Items

- Standard item overrides are authored through split generator sources and merged into `ItemDefsMyWorld.json`.
- NPC override data is authored through split generator sources and merged into `NpcDefsMyWorld.json`.
- Several retired item families were removed from normal inflows or kept only as compatibility data.
- Battlestaff/orb crafting is retired as an active progression path in favor of staff altar attunement.
- The plain `Staff` remains the tier-1 base of the normal staff ladder and now uses the same all-altars attunement path as every other staff tier, producing rune-first outputs across every rune altar such as `Air Staff`, `Law Staff`, `Cosmic Magic Staff`, and `Soul Yew Staff`.
- Legacy enchanted jewelry paths are partly retired/remapped; altar-enchanted jewelry now uses benefit-forward names such as `Sapphire Amulet of Teleportation`, rune-preserving necklaces use rune-first names such as `Air Sapphire Necklace`, and material staffs use rune-first names such as `Fire Oak Staff`.
- Retired orb/battlestaff IDs are kept as inert compatibility records instead of being removed from the item index.
- Ground-item spawns now replace old generic leather armour/gloves with cow-hide equivalents and are covered by a retired-item guardrail.
- Tanning is now rack-driven Crafting work; legacy Tanner NPC processing and the old hammer/fat/fire leather action path are retired and guarded by tests.
- Leather full-set bonuses now have a live first slice: full `Cow` grants `+5 Hits`, full `Unicorn` grants `+10 Prayer`, and full `Bear` applies a non-stacking `Intimidate` debuff that stacks with spell debuff categories instead of replacing them.
- The generic `Dragon` leather line was renamed to `Earth Dragon` in live item text and related crafting/drop labels to match the intended creature family naming.
- Elemental debuffs and the first leather-set debuff now use attack-count duration instead of tick timers, and poison application now follows a shared higher-strength-wins refresh path across melee, ranged, thrown, NPC, and side-effect poison sources.
- Metal, leather, cloth, robe, jewelry, and special-item plans were audited in detail; only the active work items remain in `work-items.md`.

## Production And UI

- Production-window infrastructure now supports hardened production session behavior and packet assembly for smithing and Crafting flows.
- Production behavior tests cover default selection, disabled-state truth, batching, resource exhaustion, and former fletchery routing.
- The custom bank supports `Ctrl` plus left-click to withdraw the available
  bank quantity or deposit all inventory copies of the selected item.
- Production windows can remember the last successfully started recipe and can
  remain open between completed batches through separate opt-in preferences.
- Pinned inventory, skills, spellbook, social, and options panels remain visible
  without blocking game-world input outside their rendered bounds.
- Remaining UI work is mostly player-facing text, clipped labels, production polish, and magic/combat explanation surfaces. Skill guides passed the current field-test pass.

## Layered World And Runtime

- The signed layered-world loader is the production map path. Player/entity
  location, scene context, collision, interactions, persistence, and client
  residency use explicit world space and signed level identity.
- The custom client keeps a wider visual residency field around the smaller
  authoritative gameplay window and renders the complete loaded area on the
  minimap.
- Later regression work corrected restricted passages, guild-area checks,
  arena placement, scenery reachability, and projectile cover that still
  depended on legacy packed coordinates.
- RSC World Editor and its embedded runtime are now independent repositories.
  Their former Core plans remain archived only as migration history.

## Cleric, Blessing, And Devotion

- Blessing is a level 1-99 production skill with stone and silver sigils,
  altar blessing, every-ten-level duplicate production, and diminishing XP
  returns.
- The Spells interface contains separate Mage and Cleric tabs. The launch
  Cleric book has twelve party-support spells with Holy Power, atomic sigil
  spending, typed timed effects, status HUD presentation, and PvP exclusion.
- Devotion blessing/destruction transactions, costs, hourly limits, alignment,
  cleanup, and Bonecrusher behavior use the accepted audited rules.
- Unholy sigils and enemy-facing Cleric debuffs remain future concept work, not
  missing launch implementation.

## Monster Slayer's Guild

- Six progressive guild tiers provide mandatory/repeatable tasks, promotions,
  typed point currencies, graphical reward shops, and rank-themed contacts.
- Six ordered satchel purchases permanently expand inventory capacity from 30
  to 40 slots.
- When multiple eligible players share a task and target, only the highest
  damage contributor receives Slayer task credit, with deterministic tie
  handling.
- Unique Slayer rewards remain deliberately deferred until the shipped task
  and point economy has stronger field evidence.

## Combat Modernization

- The A01-A11 behavior-preserving program established deterministic seams,
  reasoned eligibility, engagement/event ownership, damage facts, exact death
  lifecycle boundaries, projectile resource/impact ledgers, typed poison and
  burn lifecycles, contribution profiles, NPC combat profiles, and bounded
  tracing without importing Classic-Scape balance or formulas.
- Projectile cover now distinguishes allegiance: solid walls and closed doors
  block both sides, ordinary scenery and unwalkable terrain can be attacked
  across, and fences block hostile NPC projectiles while allowing player and
  player-allied projectiles.

## Agility Rewards

- The three live Agility courses now each award a stackable, tradeable pouch on lap completion: Gnome -> Tier 1, Barbarian -> Tier 2, Wilderness -> Tier 3.
- Agility pouches open into multiple non-overlapping reward categories instead of a single flat reward pull.
- Pouch rewards prefer noted form where the item data supports it and fall back cleanly to normal items otherwise.
- Tier reward pools now focus on consumables and grindy resources such as ores, logs, runes, arrows, herbs, potion ingredients, food, and potions.
- Each pouch also rolls an additional independent chance at the shared rare drop table.

## Quests

- The global login-time quest bootstrap was removed.
- Per-quest player-facing shortcuts are implemented across the current free,
  members, and MyWorld quest matrix instead of blanket login completion.
- Quest audit work records start flows, branch-sensitive rewards, utility
  item backfills, and the implemented shortcut rollout.
- Reward skill remaps are explicitly tracked: Attack/Defense/Strength to Melee, Fletching to Crafting.
- Standard quest reward registration now uses `Melee` directly for former Attack, Strength, and Defense rewards instead of relying on legacy skill aliases.
- Quest reward guardrail tests now block central quest rewards and direct quest XP grants from reintroducing retired combat-skill reward targets.

## Optimization

- Benchmark scripts and matrix runs live under `tools/benchmarks/`.
- Benchmark output now goes under `output/benchmarks/optimization/` and logs under `output/logs/`.
- Foundation optimization work has focused on measurable hot paths, avoiding gameplay-rule changes.
- Remaining optimization work should continue only from benchmark evidence and one phase at a time.

## Documentation Consolidation

- `docs/myworld/README.md` is the authoritative category index.
- Active implementation direction lives in `in-progress-work-plans/`; completed
  records move to `completed-work-plans/`; stable audits and references live in
  `info/`; early ideas remain in `rough-drafts/`.
- Detailed historical plans and audits were archived under `docs/myworld/completed-work-plans/archive/`.
- The 2026-08-21 documentation audit reconciled the index and roadmap through
  release `v0.2.75`, moved closed implementation records out of the active
  queue, and archived former Core World Editor plans after project separation.

## Combat Follow-Up

- Poison weapons now use the MyWorld max/applied poison-power model instead of immediate flat poison application.
- Poison from melee, arrows/bolts, and thrown weapons now only rolls on successful hits and resolves at impact.
- PvP poison in MyWorld no longer goes through the old legacy side-effect script, preventing duplicate poison application paths.
- Scorpion, spider, and magic-spider full sets now feed into that same shared poison runtime with one combined cap per hit instead of separate stacking applications.
- Poison now uses target-local proc ramps for player poison sources: weapon poison opens at `100%`, drops to `50%`, then steps down to a `20%` floor; armor poison opens at `50%` and steps down to a `10%` floor, with both recharging after `5` failed proc attempts.
- Poison ticks now pulse every `8` server ticks, and poison reapplications update the existing poison event without clearing the max cap or restarting the tick timer.
- Baby-dragon smoke and the demon-line infernal fire procs are now active as source-specific leather-set effects instead of placeholder notes.
- Blue dragon, Earth dragon, red dragon, black dragon, and elder green dragon full sets now use source-specific dragon-breath effects with shared poison/runtime integration rather than legacy spell-source debuffs.
- Unicorn and black-unicorn prayer bonuses now key off the active prayer book, bear `Intimidate` now behaves as a `20%` attack-speed proc instead of a constant accuracy penalty, goblin `Enraged` is live as a temporary attack-speed buff, giant-line `Brute Force` buffs are live as `5`-attack proc windows, and ogre `Staggering Blow` is live with the intended `5 attacks or 10 seconds` cooldown behavior.
- Leather and carapace armor pieces now describe their live full-set traits directly in item examine text. Wolf and hellhound sets now summon armor-bound spirit companions backed by the summon runtime.
