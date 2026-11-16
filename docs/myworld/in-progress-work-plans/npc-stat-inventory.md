# Current NPC Stat Inventory

Status: observed source baseline for review, not approved new balance values.
Recorded 2026-11-14. Final bundle generated from Core `main`, commit
`92306a7b093d6e42edae87c8398e42f50b037ccb`. Source tracing began at
`a89c475b47c755038aa35a435ca3f54e3d7e7870`; unrelated bat-wing work advanced
main during the audit, but every inventoried input hash and all roster results
remained identical. The bundle records its actual generation revision.

This inventory supports the [unified combat statline and NPC authoring guide](npc-explicit-stat-authoring-guide.md).
The owner assigned inventorying current NPCs to this work. The established
power-versus-corresponding-defense combat model and Holy Power's separate
Cleric-effectiveness role remain unchanged. No NPC values or runtime behavior
were edited, and no public server or player database was accessed.

## 1. What was inventoried

All **883 definitions**, runtime IDs **0 through 882**, loaded by the checked-in
MyWorld configuration were inventoried. Of these, **298 are marked attackable**
and **585 are nonattackable**. This is not a count of live enemies, unique
encounters, or placed NPCs. Separate IDs with the same name remain separate rows.

Each row includes:

- Identity, authored JSON ID, final and base name, source file/line, description,
  attackability, aggression, members flag, HP, displayed combat level and respawn.
- Six resolved baseline values: Melee/Ranged/Magic Power and matching Defenses.
- Per-value resolution mode and exact input values with source provenance.
- Original authored stat fields, complete applicable MyWorld override record,
  and loaded fields with explicit presence/default information.
- General attack profile, the rule selecting it, projectile availability and range.
- Formula-only raw maxima, clearly separated from actual attack availability and
  full encounter behavior. These are not damage predictions against a target.
- Holy Power availability, limited source-supported role classification, static
  placement-source reference counts, and review flags.

Here, **baseline** means the ordinary `Npc` getter result before transient fire
or Cleric debuffs and other instance effects. It is not a snapshot of an actual
spawned NPC. The guide distinguishes authored baseline, current effective stats,
combat rolls, and final damage; this inventory does not collapse those layers.

## 2. Full roster and reproducible artifacts

The complete machine-readable record is
[inventory.json](../../../output/diagnostics/npc-stat-inventory/source-64b06dd0/inventory.json).
It preserves full stat provenance and input fingerprints for every row.

Convenient views of the same data:

- [All NPCs, 883 rows](../../../output/diagnostics/npc-stat-inventory/source-64b06dd0/all-npcs.csv).
- [Attackable NPCs, 298 rows](../../../output/diagnostics/npc-stat-inventory/source-64b06dd0/attackable-npcs.csv).
- [Attackable review queue, 63 rows](../../../output/diagnostics/npc-stat-inventory/source-64b06dd0/review-flags.csv).
- [Counts, assumptions, source hashes and flag definitions](../../../output/diagnostics/npc-stat-inventory/source-64b06dd0/summary.json).

These are local generated evidence under the repository's designated diagnostics
category. They have not been committed, published or deployed; output files may
not be included in another clone. The retained generator can reproduce them.
Do not delete an older bundle to make a new result appear identical.

Generator: [scripts/audit-npc-combat-stats.py](../../../scripts/audit-npc-combat-stats.py).
From the repository root:

```bash
# Read-only source analysis and summary; no output files written.
python3 scripts/audit-npc-combat-stats.py

# Read-only regeneration and byte comparison using captured generation metadata.
python3 scripts/audit-npc-combat-stats.py --check source-64b06dd0

# On a fresh checkout, create the bundle in the approved diagnostics category.
# Existing nonidentical output is refused rather than overwritten.
python3 scripts/audit-npc-combat-stats.py --write source-64b06dd0
```

The script uses only Python's standard library, source/JSON reads, read-only Git
queries and the repository output-path resolver. It does not compile Java, start
a server, call game APIs, connect to a database, or modify definition files.

It checks the traced Java files against fixed SHA-256 fingerprints and refuses
source drift. Review and deliberately update the translation/fingerprints when
implementation changes; do not bypass this safeguard. Definitions may change,
so every run records their hashes and any tracked input differences from HEAD.
The script also rejects input or HEAD changes during the inventory pass.
For `--check`, captured generation HEAD/branch are retained for archived-byte
comparison, and any difference from the current checkout is printed explicitly.
All other metadata, input fingerprints, generator fingerprint and report content
must match. This distinguishes unrelated Git history changes from data drift.

An earlier bundle under run label `a89c475b` was preserved; it actually captured
HEAD `e742d0e11b2e2d4bb350dff4d517c205c32719ca`. Its first exact check passed,
then failed when unrelated HEAD metadata advanced while data stayed identical.
The final input-based label above avoids presenting a run name as its revision.
Use the final bundle and updated generator for subsequent review.

At this snapshot, all inventoried tracked inputs match HEAD. Unrelated existing
working-tree changes were preserved. The report's input-tree digest is
`64b06dd0a7528c79fc7ae79f8d9a138bc9e842a5977a7641974322642f477100`.

## 3. Configuration and load authority

Assumed configuration is the checked-in `myworld.conf` / `myworld-host.conf`:
MyWorld enabled, `BASED_CONFIG_DATA=85`, and alternate OSRS melee/ranged formulas
disabled. Both files were checked. The definition directory is the repository's
`server/conf/server/defs`, corresponding to the default server `CONFIG_DIR`.
These are source assumptions, not confirmation of the Oracle deployment.

| Load order | File | IDs | Definitions | Attackable |
| ---: | --- | --- | ---: | ---: |
| 1 | NpcDefs.json | 0–793 | 794 | 276 |
| 2 | NpcDefsCustom.json | 794–845 | 52 | 7 |
| 3 | MonsterSlayerNpcDefs.json | 846–860 | 15 | 0 |
| 4 | VisualTestNpcDefs.json | 861 | 1 | 0 |
| 5 | MyWorldNpcDefs.json | 862 | 1 | 1 |
| 6 | SlayerMovementPreviewNpcDefs.json | 863–870 | 8 | 8 |
| 7 | MonsterSlayerTowerNpcDefs.json | 871–876 | 6 | 0 |
| 8 | GemDragonNpcDefs.json | 877–881 | 5 | 5 |
| 9 | StirgeNpcDefs.json | 882 | 1 | 1 |

The loader appends rows; position establishes runtime ID. Every authored ID was
checked against its append position. The historical patch path is inactive at
configuration version 85. The optional ID-keyed `NpcDefsMyWorld.json` then applies
**325 overrides** by copying definitions and replacing only supplied fields.
There are 323 override multiplier profiles and 23 Strength changes, but no direct
six-stat override fields in this snapshot. `customNpcConditions()` changes
commands, not the stats or attackability inventoried here.

Sources: [EntityHandler.java](../../../server/src/com/openrsc/server/external/EntityHandler.java)
241–266, 351–401, 452–527, 540–562;
[ServerConfiguration.java](../../../server/src/com/openrsc/server/ServerConfiguration.java):143;
[myworld.conf](../../../server/myworld.conf):90,100–101,193;
[myworld-host.conf](../../../server/myworld-host.conf):101,111–112,204.

## 4. Effective-value rules and provenance

The script mirrors these currently active rules, not a proposed migration:

- Positive direct `*Offense` or `*Defense` values win. Missing direct fields
  load as zero, and zero does not win the getter's positive-value test.
- Otherwise Melee Power is loaded legacy Strength.
- Otherwise general-profile Ranged/Magic Power is `max(1, Attack, Strength)`
  when the profile uses that projectile type. Without that profile, Ranged
  falls through to the old boolean stored as 0/1; Magic returns zero.
- Otherwise each defense independently derives from legacy Defense: a supplied
  nonnegative multiplier takes precedence, then a positive divisor, then the
  style default 1.0/0.5/0.5. The result is floored and clamped nonnegative.
  Nonpositive legacy Defense yields zero. A direct defense supersedes ratios.
- Optional override zeros in integer stat fields use the reserved value
  `Integer.MAX_VALUE`. The current override path does not normalize it back.
  The inventory models and flags that hazard rather than silently treating it
  as ordinary zero. No current loaded stat sentinel was found.

Every JSON `stats` entry includes its resolution, dependencies, values and source
locations. `loaded_stat_fields` also records ignored direct zeros and whether a
field was authored or loader-defaulted. `base_authored_stat_fields` and
`override_fields` preserve the inputs rather than overwriting their history.
CSV columns expose the resolved values, resolution labels and input provenance;
use JSON when exact field-presence or original-versus-override detail is needed.

Holy Power is **unsupported as an NPC definition stat**, not zero and not an
inferred value. JSON uses null plus an explicit availability label. The current
player Cleric effectiveness system is not converted into another NPC damage
channel. Holy-looking magic still does not prove NPC Holy Power support.

Sources: [Npc.java](../../../server/src/com/openrsc/server/model/entity/npc/Npc.java):675–751;
[NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java):89–94,240–245,379–473;
[EntityHandler.java](../../../server/src/com/openrsc/server/external/EntityHandler.java):46–54,369–380,498–511,657–658;
[Functions.java](../../../server/src/com/openrsc/server/plugins/Functions.java):108.
See the authoring guide for full damage and Holy Power source traces.

## 5. Current authoring coverage

Among the 298 attackable definitions:

| Stat | Positive direct value | Other current resolution |
| --- | ---: | ---: |
| Melee Power | 9 | 289 |
| Ranged Power | 3 | 295 |
| Magic Power | 3 | 295 |
| Melee Defense | 10 | 288 |
| Ranged Defense | 10 | 288 |
| Magic Defense | 10 | 288 |

The same 10 definitions have all three positive direct defenses. The other
**288 derive all three defenses**. Only **Elder Green Dragon #844** has six
positive direct values. This does not mean zero is an invalid intended value or
that every NPC should use all three attack styles. It describes current field
and getter authority only. Stirge has all six fields present, but its two power
zeros still take the fallback path.

General profile counts among attackable definitions are 195 melee, 20 pure
ranged, 13 pure magic, 14 melee/ranged, 46 melee/magic, 3 frequent-magic mixed,
and 7 rare-magic mixed. This is **not an exhaustive move-set classification**.
Dedicated scripts can add attacks outside the general profile.

The 48 attackable definitions whose active projectile fallback is dominated by
Attack demonstrate why retiring legacy authority requires more than renaming
Strength. They are a review set, not 48 established balance bugs.

## 6. Representative verified rows

All six numbers are baseline getter values. M/R/G below means
Melee/Ranged/Magic; G avoids confusing Magic with Melee in compact columns.

| ID | NPC | Power M/R/G | Defense M/R/G | Important context |
| ---: | --- | --- | --- | --- |
| 22 | Lesser Demon | 70 / 0 / 78 | 59 / 39 / 79 | Strength override lowers melee; magic still uses Attack |
| 40 | skeleton | 20 / 0 / 0 | 5 / 20 / 10 | Independent defense ratios, not a split pool |
| 202 | Blue Dragon | 91 / 0 / 105 | 105 / 78 / 105 | Strength override plus magic fallback |
| 809 | Balrog | 250 / 0 / 999 | 60 / 40 / 80 | Legacy Attack controls magic; raw generic maximum 226 |
| 844 | Elder Green Dragon | 250 / 235 / 270 | 265 / 210 / 265 | Six positive authored values; special encounter |
| 863 | Giant frog | 1 / 30 / 0 | 10 / 20 / 35 | Production Slayer encounter; pure-ranged profile |
| 865 | Banshee | 55 / 0 / 55 | 40 / 40 / 40 | Production mixed profile; special projectile constraints |
| 866 | Naga | 65 / 55 / 0 | 20 / 65 / 95 | Production melee/ranged profile |
| 869 | Dark beast | 110 / 0 / 210 | 120 / 100 / 120 | General melee profile, scripted magic attack |
| 877 | Sapphire Dragon | 90 / 0 / 100 | 125 / 93 / 100 | Intentional-family review context for >1 multiplier |
| 882 | Stirge | 55 / 0 / 0 | 50 / 80 / 50 | Explicit ranged/magic zeros currently fall back to zero |

Source links and exact row start lines are included in each exported row. Selected
navigation: [NpcDefs.json](../../../server/conf/server/defs/NpcDefs.json):861,1563,7881;
[NpcDefsCustom.json](../../../server/conf/server/defs/NpcDefsCustom.json):603,1997;
[NpcDefsMyWorld.json](../../../server/conf/server/defs/NpcDefsMyWorld.json):40,47,103,1851;
[SlayerMovementPreviewNpcDefs.json](../../../server/conf/server/defs/SlayerMovementPreviewNpcDefs.json):4,93,139,273;
[GemDragonNpcDefs.json](../../../server/conf/server/defs/GemDragonNpcDefs.json):4;
[StirgeNpcDefs.json](../../../server/conf/server/defs/StirgeNpcDefs.json):4.

## 7. Review queue: observations, not approved corrections

The 63-row review CSV is the union of named audit flags. Counts overlap. Several
flags identify valid exceptions or script dependencies; absence of a flag does
not certify an encounter correct. Full flag definitions are in summary.json.

### Priority examples

1. **Balrog #809:** Attack999, Strength250, Defense80. Loaded ratios .75/.5/1
   produce 60/40/80, not default 80/40/40. Magic fallback is999 and generic raw
   maximum is `ceil(999/12)+floor(999/7)=226`; ordinary melee maximum is36.
   Its existing splash and post-roll mitigation remain separate. The owner
   identified this legacy dependency as unwanted; the correct future number is
   still undecided. Do not promote999 or226 into official replacement values.
2. **Black Demon #568:** base Attack195/Strength178/Defense168, but override
   Strength91. Current powers91/0/195 and defenses126/84/168 give melee raw14
   and generic magic raw44. It is the other case meeting the audit's large
   Attack/Strength-gap threshold. The inventory does not establish that this
   is unintended. [NpcDefs.json](../../../server/conf/server/defs/NpcDefs.json):22155;
   [NpcDefsMyWorld.json](../../../server/conf/server/defs/NpcDefsMyWorld.json):145.
3. **charlie #550:** marked attackable, HP3, level0, and all six baseline values
   zero. The ordinary melee formula still has a minimum raw maximum of1.
   This requires encounter/usage review, not an assumption of harmlessness,
   a request to remove it, or a claim it is live.
   [NpcDefs.json](../../../server/conf/server/defs/NpcDefs.json):21453.
4. **Battle mages #789–791:** old Attack0 does not yield zero Magic Power;
   Strength52 supplies fallback, so each has Magic52 and generic raw12.
   These are zero-legacy review cases, not the Balrog failure pattern.
5. **Dark beast #869 and Elder Green Dragon #844:** positive power exists outside
   the general projectile profile. Do not delete it as unused. Dark beast's
   scripted lightning consumes Magic210 for a generic raw48, despite a MELEE
   profile. Elder's ranged235 also requires its encounter consumer audit.
6. **Gem dragons #877–881:** all have a defense multiplier above1; this is a
   documented family exception to older prose. Their baseline defenses are
   not normalized down to a shared budget.
7. **Stirge #882:** the only direct-stat zeros currently authored among these
   definitions are its ranged/magic offense fields. They happen to resolve to
   zero because its ordinary profile is melee. This is not proof that authored
   zero is authoritative under the current loader/getter contract.

Audit thresholds are deliberately visible: a large Attack gap means Attack at
least twice `max(1, Strength)` and a difference of at least50, with active
projectile fallback. A high generic magic maximum means at least50. These are
triage filters, **not gameplay caps, balance recommendations or owner decisions**.
The latter filter finds Balrog226, Elder Green Dragon61, and King Black Dragon56.
No attackable row has every resolved defense below its positive legacy Defense;
this agrees with the preservation rationale at this snapshot, without proving
balance parity after quantization, scripts or effects.

No loaded stat sentinel or nonpositive-HP attackable definition was found.
That is a useful negative check, not proof that the zero-override path is safe.

## 8. Context and limits that must travel with the numbers

### Definition roles

- **#863–870 are production encounters**, despite the historical Preview
  filename. [test-slayer-movement-preview.py](../../../tests/myworld/test-slayer-movement-preview.py):35–37
  explicitly records that status. Do not discard them from the migration.
- **Gorak #861 is a developer-only visual fixture**, nonattackable, with a test
  forbidding permanent placement. [test-gorak-visual-npc.py](../../../tests/myworld/test-gorak-visual-npc.py):2,46–62,80–83.
- **#851** is an explicitly unused compatibility definition. Other nonattackable
  Slayer contacts, suppliers and associates are not thereby unused or fixtures.
- Boss, quest and summon roles are not completely classified by definition data.
  Balrog and Elder are explicit Slayer bosses; Kolodion IDs713/757–760 are a
  staged Mage Arena encounter. A summon is an owner-marked runtime instance
  and can reuse an otherwise ordinary definition. Do not treat an ID as
  exclusively summoned or ordinary based on this limited pass.

### General profile is not encounter simulation

Banshee865, Naga866 and Giant frog863 receive ID-based profile rules before name
rules. Kolodion forms precede dragons; dragon IDs/name checks precede wizard and
other name rules. Positive authored power alone does not enable a general style.
Name matching is lowercase but not trimmed.

Dark beast adds scripted magic and charging mitigation. Balrog has secondary
splash. Elder has multi-target and burn mechanics. Kolodion adds staged forms
and elemental effects. Summons can have outgoing damage caps independent of a
base definition. These are sampled reasons to audit consumers, not an exhaustive
list of every special NPC.

Sources: [NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java):58–79,247–270,369–473;
[DarkBeastCombat.java](../../../server/src/com/openrsc/server/content/monsterslayer/DarkBeastCombat.java):50–55,115–129,186–196;
[ProjectileEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java):1212–1245;
[ElderGreenDragonSpecialAttacks.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java):33–87;
[Summoning.java](../../../server/src/com/openrsc/server/content/Summoning.java):557–562,996–1004,1228–1236;
[MonsterSlayerBossTasks.java](../../../server/src/com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerBossTasks.java):9–28;
[MageArena.java](../../../server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java):249–268,455–467.

### Placement references are not live population

Three checked-in sources are counted separately: NpcLocs3612 rows/729 IDs,
MyWorldNpcLocs158 rows/49 IDs, and MyWorldNpcRemovals7 rows/6 IDs. Removal rows
are not spawns and are not subtracted from those counts to invent a population.
A zero in a reference column means absent from that specific file only.

The runtime can bypass legacy placement loading for native layered worlds.
Map/feature settings, resolved world-edit storage, removals and runtime filters
also affect placement. Scripted encounters and summons may spawn independently.
This inventory does not inspect all placement layers or claim that an ID with
no checked-in reference is unused/unplaced in the deployed world.

Source: [WorldPopulator.java](../../../server/src/com/openrsc/server/database/WorldPopulator.java):97–129,175–221,665–738,889–916.

### Raw maxima are formula-only

Every CSV carries a maxima caveat. The exported maxima answer what the ordinary
NPC formula would produce from the given power **if used**, before mitigation
and later effects. Magic assumes the generic profile's `max(1, Power/12)` spell
component, which a dedicated caller may replace. A non-enabled style can still
have a hypothetical formula maximum; do not infer an attack from that column.
These values are not new authored stats, guaranteed damage, or encounter caps.

## 9. Validation receipt and next use

Passed:

- Exact append-ID/load-order validation, override-key/ID validation and source guards.
- Source/config assumptions and no inventoried tracked-input differences from HEAD.
- Arithmetic/precedence self-checks for direct values, fallback, multiplier/divisor,
  zero and reserved-sentinel behavior, profile precedence and Balrog's226.
- Seven independently calculated representative baselines against exported rows:
  Balrog, Elder, Giant frog, Banshee, Naga, Dark beast and Stirge.
- All CSV row IDs/subsets, six values, resolution labels and maxima caveats round-trip
  against the JSON:883 all rows,298 attackable rows,63 flagged rows.
- Independent source review of the generator and snapshot semantics.
- A second `--check source-64b06dd0` regenerated all five artifacts byte-for-byte.
- Repository asset/output-layout check.

Artifact SHA-256 values:

| File | SHA-256 |
| --- | --- |
| inventory.json | `791581c93cd9da5673bf5c8581f408b3854ae5ee043b0be442993b8c5edfc8f7` |
| all-npcs.csv | `c099512706571ba0079a196c86a9418ad2f52f36599401a61bb7033fb8cf16ae` |
| attackable-npcs.csv | `90252fa979fde1af0e0d8b64f88bce0af57a2da796ac386dcfde897c977e2e8a` |
| review-flags.csv | `55d26a4f2260ca2df0a739217227b22b33d82d64def964b3706384396e1bba96` |
| summary.json | `a2c42059d7c9e1914253e039136377794eea595ca0bae90a4158791a6075fb8a` |

Not performed: running the production Java loader, gameplay/combat regression
suite, live deployment verification, exhaustive script/placement audit, NPC
rebalance, migration implementation, commit/push, or any server restart.

Use this as the **current observed** column in the authoring record. Next, review
the outliers and remaining consumer dependencies, decide which NPCs preserve
behavior versus receive an explicit correction, and record approved numbers
separately. The inventory does not assign future values or silently turn existing
fallback outputs into the new authority.
