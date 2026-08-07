# Animation Asset Migration Plan

This plan tracks the gradual replacement of the current spell and combat
animation layout with a reusable, spritesheet-first visual-effect library. The
work is intentionally incremental: animations will be assigned in small waves,
field-tested, and corrected before the temporary legacy folder is removed.

## Goals

- Organize animations by how they move and where they are placed, rather than
  by the player, NPC, or spell that first used them.
- Prefer generic source VFX names so the same animation can be reused by
  spells, NPC attacks, summons, equipment effects, and future content.
- Use PNG spritesheets as the normal runtime source. Individual frame exports
  may remain useful working material, but they are not required for an effect
  to load.
- Reassign active animations deliberately instead of automatically carrying
  every old spell mapping forward.
- Support separate travel and impact visuals where the supplied assets provide
  a better complete effect.
- Preserve hand-tuned specialty effects when recreating their timing would add
  risk without adding value.

## Target Categories

The replacement runtime folder will have three top-level categories:

- `projectile-moving`: the visual moves from its caster to its target.
- `projectile-static`: the visual spans or connects its caster and target but
  does not travel between them.
- `on-entity`: the visual remains anchored on an entity. The entity may be a
  player, NPC, summon, caster, or target.

The folder category describes rendering behavior. It does not describe which
kind of entity originally used the effect.

## Naming Policy

- Keep animation names generic and reusable. Avoid runtime names such as
  `dark-wizard-magic` when a source-oriented VFX name can describe the asset.
- Preserve the useful identity of source names such as `Acid VFX 10`, even
  when the source library is not uniformly named.
- Normalize runtime lookup keys to stable lowercase kebab-case, such as
  `acid-vfx-10`, without changing the effect into a use-specific name.
- Store the runtime key separately from the human/source label. This allows
  folder cleanup without changing spell or network identifiers.
- Correct harmless formatting and spelling inconsistencies when an asset is
  promoted into runtime use, but do not bulk rename unassigned library assets
  merely for cosmetic uniformity.

## Basic Moving Projectile Fallbacks

Introductory fallback visuals use the `[type]-basic` naming convention. These
keys describe reusable baseline art, not a particular spell or NPC.

| Runtime key | Source visual |
| --- | --- |
| `acid-basic` | Acid VFX 1 |
| `earth-basic` | Earth projectile |
| `fire-basic` | Firebolt |
| `ice-basic` | Ice VFX 1 |
| `thunder-basic` | Thunder Ball |
| `water-basic` | Water Ball |
| `wind-basic` | Wind projectile |
| `wood-basic` | Wood VFX 01 |
| `holy-basic` | Holy VFX 01 |
| `arrow-basic` | Arrow |
| `bolt-basic` | Crossbow bolt |
| `dart-basic` | Throwing dart |
| `throwing-knife-basic` | Eight-angle legacy throwing knife |
| `shuriken-basic` | Eight-angle legacy shuriken |

Projectile selection follows this order:

1. Use an explicit assignment when one exists.
2. Otherwise, when a moving projectile is intended and an element is known,
   use that element's `-basic` visual.
3. Use `holy-basic` for a non-elemental god-themed projectile.
4. Use the matching physical ranged fallback for arrows, bolts, darts,
   throwing knives, and shuriken.
5. Use legacy fallback only for an explicitly recorded specialty or currently
   unclassified effect.

Unassigned assets under `projectile-moving` remain library candidates. Their
presence in that folder does not automatically make them fallbacks.

## Folder Transition

At the start of implementation:

1. Rename the existing `dev/myworld/assets/animations` directory to
   `dev/myworld/assets/legacy animation folder`.
2. Rename `dev/myworld/assets/replacement animations folder` to
   `dev/myworld/assets/animations`.
3. Make the new `animations` directory the preferred runtime lookup path.
4. Fall back to `legacy animation folder` only for an explicitly documented
   specialty effect that has not yet been migrated.
5. Do not add new effects to the legacy folder.
6. Delete the legacy folder only after all live mappings have migrated and the
   fallback audit is empty.

Current state: the folder swap was completed in the first fallback wave. The
new three-category directory is the preferred runtime source, while the old
folder remains available only through explicit legacy lookup.

The current client build already packages PNG files beneath `animations`.
Editable sources such as `.aseprite` files should remain repository working
material and must not become runtime inputs.

## Spritesheet-First Runtime

Each promoted animation will have a catalog entry that identifies its exact
spritesheet and how to divide it into frames. The loader must not recursively
load every PNG beneath an animation folder because many source folders contain
both sheets and separated-frame exports.

A catalog entry should be able to describe:

- stable runtime key and source label;
- category and exact spritesheet path;
- sheet columns, rows, first frame, and frame count;
- playback speed and optional opening, loop, and ending ranges;
- display size, anchor, X/Y offset, and mirroring behavior;
- caster/target alignment for `projectile-static` effects;
- optional travel and impact components for a combined spell visual;
- whether a temporary legacy fallback is intentional.

Frame capacity must be validated rather than silently truncating long
sequences. The catalog or loader should fail clearly when sheet geometry,
declared frame ranges, or required assets do not match.

Resolved source audit note: the empty Earth Impale source sheet was rebuilt
losslessly from its 17 supplied 64x64 frames as
`on-entity/earth-4/Earth Impale 64x64.png` before `earth-4` entered the runtime
catalog.

Deferred Water Burst projection note: the current `water-2` sheet animates and
aligns correctly as a screen-space caster-to-target visual, but its flat beam
is not convincing from every camera angle in the 3D world. Revisit it in a
later wave and compare three options: replace the asset, reclassify the effect,
or add perspective-aware warping for `projectile-static` visuals. Do not expand
the current polish wave into that renderer experiment.

## Specialty Legacy Effects

Some existing effects were tuned through repeated visual adjustment. Rebuilding
those timings from scratch is unnecessary when the result is already good.

The summoning charge and arrival visuals are the first known examples:

- copy their proven runtime assets into the appropriate new category;
- preserve their existing opening, looping, ending, reveal, and offset rules;
- give them generic reusable catalog entries where practical;
- remove their legacy fallback once the copied version matches in field tests.

Apply the same rule to another specialty effect only when its current behavior
is genuinely hand-tuned and worth preserving. Record every exception in the
migration ledger so the fallback does not become permanent by accident.

## Assignment Waves

Animation assignment will proceed in user-selected waves of no more than five
live effects. A wave begins with a list such as:

> Next five things that need animations assigned are ...

For each effect in the wave:

1. Identify the live spell, NPC attack, summon, or equipment effect and its
   current server/client identifiers.
2. Choose a generic source animation from the new library.
3. Decide whether it needs a moving projectile, a static caster-to-target
   visual, an on-entity impact, or a combination.
4. Define spritesheet geometry, timing, placement, scale, and mirroring in the
   catalog.
5. Add or update focused automated coverage for mapping and asset integrity.
6. Build the client and field-test the complete visual from multiple camera
   directions and caster/target distances.
7. Adjust visual data until approved, then mark the assignment complete.

Do not migrate unrelated old mappings merely because their files are nearby.
The deliberate wave process is the source of truth for what moves next.

## Migration Ledger

Add one row for every live effect as it enters a wave.

| Wave | Live effect | Render component(s) | New asset key(s) | Legacy fallback | Automated check | Field test | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Basic elemental, holy, and physical ranged projectiles | Moving projectile | `acid-basic`, `earth-basic`, `fire-basic`, `ice-basic`, `thunder-basic`, `water-basic`, `wind-basic`, `wood-basic`, `holy-basic`, `arrow-basic`, `bolt-basic`, `dart-basic`, `throwing-knife-basic`, `shuriken-basic` | Only unclassified specialty projectiles | Added | Accepted | Complete |
| Foundation | Summoning charge and arrivals | On entity | To be assigned from copied legacy assets | Temporary | Existing summoning guardrail | Required | Pending |
| 2 | Tornado | On-entity impact | `wind-3` | No | Added | Accepted | Complete |
| 2 | Water Eruption | On-entity impact | `water-3` | No | Added | Accepted | Complete |
| 2 | Earth Burst | On-entity impact | `earth-3` | No | Added | Accepted | Complete |
| 2 | Explosion | On-entity impact | `explosion-vfx-3` | No | Added | Accepted | Complete |
| 2 | Thunder Strike | On-entity impact | `thunder-3` | No | Added | Accepted | Complete |
| 2 | Ice Crystal | On-entity impact | `ice-3` | No | Added | Accepted | Complete |
| 2 | Acid Gush | On-entity impact | `acid-3` | No | Added | Accepted | Complete |
| 2 | Battering Ram | On-entity impact | `wood-3` | No | Added | Accepted | Complete |
| 2 | Iban Blast | Moving projectile, on-entity impact | `skull`, `thunder-explosion-start`, `thunder-explosion` | No | Added | Accepted | Complete |
| 2 | Wind Beam | On-entity impact | `wind-4` | No | Added | Accepted | Complete |
| 2 | Water Vortex | On-entity impact | `water-4-start`, `water-4-end` | No | Added | Accepted | Complete |
| 2 | Earth Impale | On-entity impact | `earth-4` | No | Added | Accepted | Complete |
| 2 | Fire Pillar | On-entity impact | `fire-4` | No | Added | Accepted | Complete |
| 2 | Eye of Guthix | Moving projectile, on-entity impact | `holy-basic`, `dark-10` | No | Added | Accepted | Complete |
| 2 | Saradomin Strike | Moving projectile, on-entity impact | `holy-basic`, `dark-11` | No | Added | Accepted | Complete |
| 2 | Void of Zamorak | Moving projectile, on-entity impact | `holy-basic`, `dark-7-repeatable`, `dark-7-ending` | No | Added | Accepted | Complete |
| 2 | Zamorak's Apocolypse | Moving projectile, on-entity impact | `holy-basic`, `dark-4` | No | Added | Accepted | Complete |
| 2 | Saradomin Soul Slash | Moving projectile, on-entity impact | `holy-basic`, `dark-12` | No | Added | Accepted | Complete |
| 2 | Claw of Guthix | Moving projectile, on-entity impact | `holy-basic`, `dark-6-diagonal` | No | Added | Accepted | Complete |
| 3 | Lesser Demon, Greater Demon, Chronozon | `fire-basic`, on-entity impact | `explosion-vfx-15` | No | Added | Required | Field test |
| 3 | Black Demon | `fire-basic`, on-entity impact | `explosion-vfx-17` | No | Added | Required | Field test |
| 3 | Elder Green Dragon burn | `fire-basic`, on-entity impact | `burn` | No | Added | Required | Field test |
| 3 | Green dragon magic | `earth-basic`, on-entity impact | `earth-3` | No | Added | Required | Field test |
| 3 | Blue dragon magic | `water-basic`, on-entity impact | `water-3` | No | Added | Required | Field test |
| 3 | Red and other fire dragon magic | `fire-basic`, on-entity impact | `explosion-vfx-3` | No | Added | Required | Field test |
| 3 | Ordinary elemental enemy magic | Moving projectile | Elemental `-basic` fallback | No | Added | Required | Field test |
| 4 | Dragon weapon breath proc | On-entity impact | Equal roll between `fire-slash-1` and `fire-slash-2` | No | Added | Required | Field test |
| 4 | Fire Sword proc | On-entity impact | `fire-hit-3` | No | Added | Required | Field test |
| 4 | Ice Sword proc | Static caster-to-target | `ice-stab` | No | Added | Required | Field test |
| 4 | Earth Sword proc | On-entity impact | `wood-6` | No | Added | Required | Field test |
| 4 | True Defense proc | On entity (defender) | `bubble-shield` | No | Added | Required | Field test |
| 4 | Tail Sting, Spitting Venom, Mystic Venom | Moving projectile | `acid-basic-2` | No | Added | Required | Field test |
| 4 | Hell's Fire | On-entity impact | `explosion-vfx-15` | No | Added | Required | Field test |
| 4 | Hell's Blaze | On-entity impact | `explosion-vfx-17` | No | Added | Required | Field test |
| 4 | Hell's Inferno | On-entity impact | `explosion-vfx-1` | No | Added | Required | Field test |

Wave 2 was deliberately approved as one player-spellbook completion batch
rather than five smaller assignment groups. It also widens the Teleport sheet
rendering by 25 percent after the corrected 48x64 slicing proved slightly too
narrow in field testing.

Player spellbook status: complete and accepted after field testing. Wave 3
finishes ordinary enemy magic by removing legacy impact overlays from fallback
casts. The requested `fire-3` dragon visual is the already accepted tier-three
fire spell effect cataloged as `explosion-vfx-3`; no duplicate asset folder is
required.

Wave 4 preserves the stable Dragon weapon effect ID for the first slash and
adds one alternate ID so the server can make an actual equal per-proc choice.
The poison armor animation is emitted only when the armor poison roll succeeds;
weapon poison does not borrow that visual.

Status values should be `Pending`, `In progress`, `Field test`, or `Complete`.
An effect is not complete merely because its asset loads; placement, timing,
and the full cast-to-impact sequence must also be visually accepted.

## Implementation Stages

### Stage 1: Folder And Loader Foundation

- Perform the two-folder rename without discarding either asset set.
- Add the spritesheet catalog and exact-path loader.
- Add the three render categories, including a true caster-to-target static
  placement mode.
- Add strict catalog, spritesheet, mapping, and frame-capacity validation.
- Keep explicit legacy fallback support during migration.

### Stage 2: Specialty Preservation

- Copy the summoning assets and preserve their existing tuned behavior.
- Inventory other genuine specialty cases as they are encountered.
- Avoid copying ordinary spell mappings that should instead be reassigned.

### Stage 3: Five-Effect Waves

- Accept the next user-selected group of up to five effects.
- Assign reusable animations and impact components.
- Run focused tests and build checks.
- Pause for field-test feedback before completing the wave.
- Create a durable checkpoint at useful accepted milestones while keeping this
  worker ACTIVE.

### Stage 4: Legacy Removal

- Confirm every live combat-effect and projectile identifier has an approved
  new mapping or an intentionally authentic non-custom visual.
- Confirm no runtime lookup reads from `legacy animation folder`.
- Run the full animation guardrails, client build, and relevant release checks.
- Delete the temporary legacy folder and fallback code.
- Update this plan and the migration ledger with the final state.

## Validation

Automated validation should cover:

- every catalog key is unique and resolves to exactly one spritesheet;
- declared sheet geometry divides the image without remainder;
- frame ranges are valid and fit renderer capacity;
- every mapped live effect uses the declared render category;
- no unapproved player/enemy-specific asset aliases return;
- legacy fallbacks are enumerated and decrease over time;
- packaged client assets contain each mapped runtime sheet;
- existing summoning timing and reveal guardrails remain intact;
- `./scripts/build-client.sh` succeeds.

Field testing should cover:

- caster and target facing each cardinal and diagonal direction;
- short, medium, and maximum casting distances;
- player-to-NPC, NPC-to-player, and applicable player-to-player casts;
- camera rotation, zoom, and both supported aspect modes;
- correct travel, impact, mirroring, scale, centering, and layer order;
- effects ending cleanly when an entity moves, dies, despawns, or leaves view.

## Completion Criteria

The migration is complete when:

- the new `animations` folder is the only custom animation runtime source;
- active custom visuals use the three-category catalog;
- approved effects use spritesheets rather than requiring separated exports;
- all specialty exceptions have migrated with their tuned behavior preserved;
- every ledger entry has passed automated checks and field testing;
- the legacy folder and fallback code have been removed;
- client build and release asset validation pass.
