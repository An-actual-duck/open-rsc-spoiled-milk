# Existing custom equipment sprite assignment audit

Recorded 2026-09-23; audited and implemented 2026-09-24 in the content worker.
No server restart or public deployment. This is held-family normalization,
not new artwork or a general recoloring pass.

## Implemented coverage

Effective server definitions were traced after custom conditions and generated
MyWorld overrides, then compared to the actual client animation catalog and
`Custom_Sprites.osar` equipment entries—not just static item JSON or inventory
icons. The supplied `custom-sprites/equipment` export remains untouched.

| Items | Correction |
| --- | --- |
| Tin 1995/1997/1999/2000 | Dagger/shortsword/scimitar/2hander instead of sword; established tin mask retained |
| Copper 2006/2008/2010/2011 | Same four correct families; copper mask retained |
| Titan Steel 2017/2019/2021/2022 | Same four correct families; titan mask retained |
| Orichalcum 2028/2030/2032/2033 | Same four correct families; orichalcum mask retained |
| Exalted Rune 3262/3263/3265/3309/3267 | Dagger/shortsword/scimitar/poisoneddagger/hatchet; Exalted mask retained |
| White 2151/2152/2154/2155 and Grey 3113/3114/3116/3117 | Reuse existing black-palette dagger/shortsword/2hander/scimitar; existing masks deliberately preserved |
| Quest daggers 1205/1230/1236/1255/1256 | Existing iron-palette dagger family instead of sword; no poison art/effect added |
| Fire sword 3235, Ice sword 3236 | Correct one-based appearance IDs 1036/1037; previously resolved to hood/firesword |

Thirty-six items corrected. Twenty-one palette/family definitions are appended
at appearances **1130–1150**, after the approved Abyssal Whip (1129). No existing
animation IDs move. All seven bespoke Slayer equipment appearances, leather
palettes, inventory icons, stats, IDs and effects remain unchanged.

The source of truth is `tools/generators/held-equipment-families.json`.
`generate-held-equipment-families.py` generates matching client/server catalogs.
Server assignment runs after all item-data overrides. MyWorld-only IDs are
gated by `WANT_MYWORLD`; authentic quest daggers also work outside that profile.
With custom sprites disabled, corrected items use valid authentic sword/axe
fallback appearances in the nearest existing metal palette; custom-only new
metal tints cannot be represented by the authentic catalog. Poison art remains
specific to already-poisoned items; this patch changes no poison eligibility.

## Verified unchanged / explicit deferrals

- Authentic metal daggers, poisoned daggers, short/long swords, 2h swords,
  scimitars and woodcutting hatchets already receive the original custom
  family overrides. Maces and battle axes use their own existing families.
- New-metal long swords, battle axes, maces and spears already use the proper
  family. MyWorld staff, pickaxe, shears, fishing rod and god-mace runtime
  overrides were inspected rather than treating their raw JSON as effective.
- Tin/Copper/Titan/Orichalcum hatchets already use `hatchet` but borrow older
  metal masks. **Their palette inconsistency is deferred**, as are the existing
  White/Grey black masks; neither is silently recolored in this family pass.
- Throwing knives (authentic/new tiers and poisoned variants) retain existing
  assignments. There is no dedicated throwing-knife held family in the supplied
  custom archive. Selecting a substitute or creating art requires a separate
  visual decision; no dagger conversion or attack gameplay change was invented.
- Poisoned spears share the existing spear family; the archive has no separate
  poisoned-spear family. Special elemental/quest designs without an exact
  replacement, including Earth sword, retain existing visuals.
- New wood-tier short/long bows share their existing longbow-family mappings.
  A broader silhouette review for shortbows is separate; no new bow art or
  reassignment was inferred here. Slayer's bespoke Leaching Bow is preserved.

## Avatar export limitation

The legacy server AvatarGenerator has only about 508 catalog entries. A narrow
sparse resolver now supports the 21 appended variants using existing family
frames and the correct masks, without padding/renumbering its older table.
Resolver unit tests pass, but full avatar export could not be verified: the
preexisting `server/conf/server/data/Custom_Sprites.osar` fails its GZIP trailer
check during AvatarGenerator initialization. The client archive loads and
verifies correctly. No archive was replaced or repaired here.

Fire/Ice and other previously added appearances beyond the legacy avatar
catalog remain a separate avatar-export coverage gap. This does not affect the
in-game client family mappings; no claim of full avatar-export completion is
made.

## Verification and reproduction

```sh
bash scripts/build-client.sh
node tests/myworld/test-held-equipment-families.cjs
# Optional exact pixel comparison against the supplied read-only export:
node tests/myworld/test-held-equipment-families.cjs /home/justin/Core-Framework/output/sprite-png-export-20260702-154351/custom-sprites/equipment
cd server
bash ../tools/vendor/apache-ant-1.10.5/bin/ant test_held_equipment_appearances test_combat_strict
```

Client checks cover append-only IDs, family, tint/blue masks, equipment layer,
all 18 directional/walk/attack frames (including intentional occlusion blanks),
exact reference pixels, Fire/Ice packaged frames and authentic fallback
resolution. Server checks cover all 36 effective mappings, custom on/off,
MyWorld profile isolation, unchanged non-appearance fields, preserved related
and Slayer mappings, and the sparse avatar resolver. Client/server builds and
the full **144/144 combat gate pass**, as do existing Slayer equipment/material
asset regressions. An in-game visual spot-check remains appropriate after a
private-session restart; none was initiated by this task.

## Original reported problem and audit method

## Reported problem

Custom worn/held spritework was made for the original RSC equipment families
so daggers, for example, have their own held appearance rather than sharing
sword visuals. Later content appears to have inherited older generic sprite
assignments instead of using the matching custom family artwork.

Owner-reported example: Tin dagger (item 1995) appears to use sword held
sprites rather than the custom dagger. Bronze dagger (item 62) is an original
family comparison to inspect, not a verified correct mapping yet. Inventory
icons and held animation assignments are separate; checking the icon alone
does not establish correct equipment rendering.

## Audit scope and approach

1. Inventory existing custom equipment sprite families and identify original
   items that correctly demonstrate each family in game.
2. Trace item appearance IDs through animation definitions, archive subspace/
   entry references, overrides and fallback resolution. Confirm which artwork
   actually renders; names or matching item icons are not sufficient evidence.
3. Compare all related newer items, including metal tiers, poisoned variants,
   unique rewards and other variants, against the correct family. Cover other
   affected equipment categories, not just daggers.
4. Record item ID/name, current resolved artwork, intended existing family,
   relevant palette, evidence and proposed correction. Preserve intentional
   unique designs rather than indiscriminately replacing them.
5. Repair confirmed stale assignments by reusing existing custom spritework.
   Preserve gameplay stats, requirements, item IDs, palettes and inventory
   artwork unless a separately identified mapping correction requires otherwise.
6. Verify idle, walking and combat frames across supported directions and
   applicable equipment layers. Check poison/tier variants, tint behavior and
   fallback paths. Add regression checks for corrected family assignments.

## Rule for new equipment work

Inspect the current custom family artwork and a verified working family item
before selecting a worn/held source. Do not copy a legacy sword assignment for
a dagger merely because it occurs in an older definition. If an existing family
is unsuitable, document that explicitly before creating a new visual.

For the current Slayer pass, apply this rule to selecting the Dagger of Terror
and the other simple worn/held bases. Keep the broader historical audit separate.
The Abyssal Whip remains a deliberately bespoke wielded sprite project.

## Completion criteria

Each discovered stale mapping is fixed or explicitly documented as deferred;
known-good original items and their related newer variants use the appropriate
custom family in game. Report coverage and remaining exceptions. A saved note
or correct inventory icon is not completion of the audit.
