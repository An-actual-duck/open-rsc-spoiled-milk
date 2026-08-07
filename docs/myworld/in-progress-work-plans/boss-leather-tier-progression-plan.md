# Boss Leather Tier Progression Plan

Status: approved implementation complete; private visual verification pending

Scope: King Black Dragon, Balrog, and Elder Green Dragon hide armor

## Accepted Tier Direction

The three boss sources will use two equipment tiers:

| Source | Equipment tier | Direction |
| --- | ---: | --- |
| King Black Dragon | 10 | Reintroduce a purpose-specific KBD hide, leather, and five-piece set. |
| Balrog | 11 | Raise the existing set from tier 10 and strengthen Hell's Inferno. |
| Elder Green Dragon | 11 | Leave the existing armor statistics, effect, acquisition, and encounter unchanged. |

This treats Balrog and Elder Green Dragon as different tier-11 sidegrades.
Balrog remains the fire-Magic/AOE set; Elder Green Dragon remains the
multi-element dragon-breath/poison set.

## Encounter Comparison

The displayed combat levels do not describe the encounters accurately because
Balrog's legacy definition contains `attack=999`, which becomes its Magic
offense through the modern attack-style fallback.

| Boss | HP | Effective defense M/R/M | Melee maximum | Magic maximum | Projectile reach | Distinct threat |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| King Black Dragon (477) | 240 | 240 / 180 / 240 | 33 | 56 | 5 | Dragonfire opener, severe Worship drain, Ranged reduction |
| Balrog (809) | 500 | 60 / 40 / 80 | 36 | 226 | 5 | Primary Magic damage splashes 50% within radius 2 |
| Elder Green Dragon (844) | 280 | 265 / 210 / 265 | 36 | 61 | 7 | Dragonfire, melee sweep, fireshot AOE, and burn AOE |

The damage maxima are pre-mitigation ceilings rather than average hits.
Balrog is much easier to hit than either dragon, but its 500 HP and exceptional
burst justify sharing the highest leather tier. Its fixed one-tile roam bounds
and five-tile projectile reach remain an encounter-balance concern because
long-range players can potentially reduce its practical farming difficulty.

## Existing Progression Inconsistencies

- Balrog's armor recipe calls it tier 10, but tanning and style-defense tests
  still place it on the prior tier-9 budget.
- Elder Green Dragon's armor recipe calls it tier 11, while its current tanning
  and style-defense values use the next-lower internal budget.
- The former special KBD item IDs and identity were migrated to Elder Green
  Dragon to preserve player holdings. Those IDs must remain Elder items.
- KBD currently drops ordinary Black dragon hide rather than a unique hide.

The implementation should preserve Elder Green Dragon exactly as it behaves
today. KBD takes over Balrog's current progression rung, while Balrog moves to
the existing Elder rung. This avoids silently increasing Elder equipment.

## Proposed Stat and Production Changes

### King Black Dragon: new tier 10 family

- Add unique KBD hide, leather, coif, gloves, boots, chaps, and cuirass IDs.
- Keep the guaranteed ordinary Black dragon hide unless the new unique hide is
  explicitly intended to replace it; add one guaranteed KBD hide as the set
  material source.
- Use tier-10 Crafting requirements: 70/71/71/72/73.
- Use the production rung currently occupied by Balrog for tanning and XP.
- Use the current Balrog total style-defense budget, redistributed according to
  KBD's 240/180/240 defense profile:

| Piece | Melee | Ranged | Magic | Total |
| --- | ---: | ---: | ---: | ---: |
| Coif | 3 | 3 | 3 | 9 |
| Gloves | 6 | 5 | 6 | 17 |
| Boots | 6 | 5 | 6 | 17 |
| Chaps | 9 | 7 | 9 | 25 |
| Cuirass | 12 | 9 | 12 | 33 |
| Full set | 36 | 29 | 36 | 101 |

KBD's set effect and visual identity need a separate design decision before
implementation. It does not reuse Elder Green Dragon's migrated IDs. Until a
purpose-specific asset direction is selected, its five wearable definitions
reuse the established Black dragon armor appearance IDs; its item identity,
stats, recipes, and drop source remain separate.

### Balrog: tier 11 sidegrade

- Move its armor recipe to tier 11: Crafting 80/81/81/82/83.
- Move tanning to the same current production requirement and XP as Elder Green
  Dragon without changing Elder Green Dragon.
- Match Elder's current total style-defense budget while retaining Balrog's
  fire/Magic-biased 60/40/80 defense distribution:

| Piece | Melee | Ranged | Magic | Total |
| --- | ---: | ---: | ---: | ---: |
| Coif | 3 | 2 | 4 | 9 |
| Gloves | 6 | 4 | 8 | 18 |
| Boots | 6 | 4 | 8 | 18 |
| Chaps | 9 | 6 | 12 | 27 |
| Cuirass | 12 | 8 | 16 | 36 |
| Full set | 36 | 24 | 48 | 108 |

- Raise the base/legacy armor values to the same slot budget as Elder where
  those values remain authoritative outside My World.
- Preserve the exact five-piece requirement and the 40-point full-leather
  Magic Power penalty.

### Elder Green Dragon

No gameplay changes. Preserve its current:

- five armor items and IDs;
- Crafting requirements and defensive values;
- True Dragon's Breath proc chance, damage, poison, and elements;
- hide acquisition and tanning behavior;
- boss stats, reach, attacks, and Mischief Imp interaction.

## Hell's Inferno AOE Proposal

Hell's Inferno becomes a 40% full-set proc and retains its name, full primary
maximum of 18 fire-Magic damage, and 12% fire-defense debuff.

To echo Balrog's own Magic attack at a safer player-owned scale:

1. Resolve the existing primary proc normally.
2. Search radius 2 around the primary target for other valid hostile targets.
3. Apply 50% of the primary proc's actual damage to each secondary target,
   rounded up, after each secondary target's own Magic mitigation.
4. Show Hell's Inferno on every affected target. Increase the final effect draw
   from 64 to 96 pixels so it reads as the tier-11 AOE, while keeping each
   decoded frame inside the loader's fixed 64-pixel canvas.
5. Keep the 12% debuff on the primary target only. Secondary targets receive
   splash damage but no additional debuff, matching Balrog's boss splash.

The AOE must use the established player-owned secondary-damage transaction and
target rules. It must:

- work from existing melee and projectile proc entry points;
- never damage the player, allies, summons, non-attackable NPCs, or otherwise
  invalid PvP targets;
- be suppressed by Guard Dog like every player-originated AOE;
- award contribution/XP only through the established secondary-damage policy;
- avoid recursive jewelry, prayer, proc, reflection, or lifesteal activation;
- settle primary and secondary deaths exactly once.

Focused deterministic tests should cover proc/no-proc, primary damage,
secondary half-damage and rounding, per-target mitigation, radius boundaries,
target filtering, Guard Dog suppression, melee/projectile parity, death
settlement, and absence of recursive effects.

## Balrog NPC Animation Finding

Balrog currently launches the shared moving fire projectile, but its impact
effect resolves to `CombatEffect.NONE`. The client still declares and loads
combat-effect ID 46 (`balrog-magic`) from an 864x48, 18-frame legacy sheet, but
the server's enemy-effect mapping deliberately omits Balrog. As a result, the
normal Magic attack has no Balrog-specific impact animation.

Implementation should:

- copy the intact 18-frame, 48x48 sheet from the legacy folder into the
  preferred on-entity animation tree under a reusable fire-impact name;
- register it explicitly in `CombatEffectAnimationCatalog` rather than relying
  on legacy folder fallback;
- map Balrog's normal Magic attack to effect 46 again;
- retain the moving fire projectile before the impact;
- apply the impact to both the primary target and existing boss-splash targets;
- update the regression test that currently asserts the Balrog effect remains
  retired;
- visually verify complete 18-frame playback in both directions and on primary
  and splash targets.

Hell's Inferno remains a separate Explosion VFX 11 animation and should not be
silently substituted for the boss's normal attack impact.

## Implementation Waves

1. Add constants, authoritative/generated definitions, assets, Crafting and
   tanning coverage for the KBD family; do not yet assign a set effect.
2. Raise Balrog production and defensive stats, leaving Elder unchanged.
3. Centralize Hell's Inferno selection and half-damage rounding and add its
   radius-2 secondary damage across legacy melee, modern PvM melee, PvP melee,
   and projectile attacks.
4. Restore Balrog NPC impact animation through the modern animation catalog.
5. Update guides, examines, acquisition documentation, data-integrity tests,
   combat tests, and animation tests.
6. Build server and desktop client, then visually test KBD crafting/equipment,
   Balrog primary/AOE procs, Guard Dog suppression, and Balrog boss projectiles
   on a private server/client.

## Decision Still Needed

The KBD tier-10 set needs its own five-piece effect. That decision can be made
after its item family and visual direction are reviewed; it does not need to
reuse either Black Dragon's poison effect or Elder Green Dragon's True Dragon's
Breath.

## Implemented Result

- Added custom IDs 3311-3317 for KBD hide, leather, and the five basic armor
  pieces. KBD retains its ordinary Black dragon hide drop and additionally
  drops one purpose-specific KBD hide.
- KBD uses tier-10 Crafting levels 70-73, the former Balrog tanning rung, the
  101-point style-defense budget above, and the former Balrog economy rung.
- Balrog now uses tier-11 Crafting levels 80-83, the Elder tanning/XP rung, the
  108-point style-defense budget above, and Elder's current economy rung.
- Elder definitions, acquisition, defensive values, and proc behavior were not
  changed.
- Hell's Inferno now has a Balrog-specific 40% primary proc, maximum hit 18,
  and primary-only
  12% fire-defense debuff. It now splashes half of actual primary proc damage,
  rounded up, through each secondary target's Magic mitigation within radius
  two. NPC secondaries exclude summons, dead/removed/unattackable targets, and
  the primary. PvP secondaries pass the side-effect-free combat eligibility
  policy, exclude party members and invalid Wilderness targets, and are
  disabled during duels. Guard Dog suppresses all secondary damage.
- Explosion VFX 11 contains 16 sheet cells but only 14 visible frames. It now
  plays those 14 frames and renders at 96 pixels in the final screen/scene draw
  rather than attempting to place a 96-pixel frame into the loader's fixed
  64-pixel canvas, which clipped both sides. Balrog's normal Magic attack once
  again resolves combat effect 46,
  backed by the intact 18-frame sheet copied into the preferred on-entity
  animation tree.
- The KBD set deliberately has no full-set runtime detector or proc yet. That
  remains the next design discussion rather than an inferred effect.
