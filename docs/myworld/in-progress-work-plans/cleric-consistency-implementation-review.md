# Cleric presentation and hostile effects implementation review

Worker: `Core-Framework-ai-1`, `feat/cleric-presentation-and-hostile-effects`.
Starting source: `a38414a86b182e247590b2ff1601353c962c991d`.
Status: scoped implementation complete; exact READY handoff for Core review.
No game sessions, accounts, maps, imports, toolkit files or prepared update packages are changed.

## Behavior and identities

The active book has17 entries: Unify1; nine god blessings12–20; Greater Unify21;
Guthix Unease/Dread/Terror22–24; Saradomin Censure/Condemnation/Unmaking25–27.
Retired codes0/2–11 remain reserved and rejected. Code12 retains its stable
`cleric.saradomin_warmth` key while displaying **Saradomin's Light**.
No Zamorak hostile entry is active. The owner wants a future luck-centered Cosmic
redesign and has not selected one; current Cosmic chance/cap and eligibility stay unchanged.

All active blessings expose their spell title, inherited named components and
numeric mechanics. Sap Blood has one label for poison cleanse and drained-stat
restoration. All nine approved receiver messages use the revised tier association.
Hostile spells have no invented receiver flavor. Canonical mechanical names and
units live in generated `ClericBlessingContract`; source-owned receiver flavor is
in `ClericSpellText`. Schema4/client10055 adds receiver flavor and target kind to
the checked metadata carrier; old schema3 is rejected. Existing NPC capacity
protocol threshold10053 is preserved.

Saradomin's Hallowed Strikes adds25/35/45/55/65/75% against undead on eligible
primary direct melee/ranged/Magic attacks. Regeneration starts at tier2; ten
six-second delayed healing pulses start at tier3. Same-god refresh/precedence,
expiry and final pulse behavior remain shared. Barkskin increases ranged defense
only. Zamorak's maximum/dealt bonuses apply only to melee; Blood Hunger retains
its existing eligible direct-hit scope and strictly-below-half activation gate.

Hallowed uses separate fractional carry. Its order is existing mitigation/gear,
then Zamorak melee dealt bonus, then Hallowed, then existing Dark Beast mitigation.
Offhand, cannon, summons, periodic damage and secondary/reflected effects do not
inherit the bonus. Natural zero remains zero.

## Undead audit

`NPCDef.undead` is optional and overrides the legacy fallback for Hallowed only.
Explicit true additions are359(zombie),498(skeleton mage),613/614/615(Nazastarool
undead forms) and865(Banshee). The authoritative merged319 definition is an
ordinary Farmer, so it is explicitly false. Legacy19 membership and constructor
`isUndead` attributes, sounds and Crumble Undead's separate quest/classic gate
are preserved. Untagged definitions retain legacy fallback; no runtime display-name
guessing is introduced. Death Wing768 remains ambiguous and is not newly tagged.
No definition IDs, sprite archives or native assets are allocated or reordered.

## Hostile admission, resources and lifetime

Hostile casts scan the caster's ordinary view snapshot and require current,
registered, attackable enemy NPCs, the same logical world space/signed level,
ordinary combat admission and line of effect. No party or pre-existing fight is
required. Summons, dead/respawning/unregistered and unattackable NPCs are excluded;
PvP remains disabled. Successful casts use the existing non-damaging hostile
spell chase/retaliation rule, without replacing an existing fight. Refusals do not
provoke NPCs.

Initial reviewable manager defaults mirror the blessing tiers with Blight:
1stone god+1Stone Blight;2stone god+2Stone Blight;2silver god+2Silver Blight.
These economic values are not separately claimed as owner-selected. The full
unnoted vector is exchanged once per useful cast, never per NPC. No useful target
or a wholly weaker application spends nothing. NPC-owned registries share the
existing tier/power replacement rules and60-second duration. Origin maps are
bounded to current slots, synchronized with their NPC, cleared on last expiry
and removed with stale lifetime state; they do not retain old Player sessions.

Guthix weaknesses are10/15/20/25/30/35%. Poison damage rounds its positive bonus
up; cleansing subtracts a rounded-up percentage with positive floor1. Existing
poison admission/protection, status and cadence are preserved. Ordinary living NPCs
have no separate poison-immunity field; no such field or exclusion is invented. Summon and recoil/reflection damage
use separate fractional carry, with no minimum-one bonus. Each request belongs
to one applicable family and is amplified once. The adjusted immutable request
retains source, session/lifetime snapshot, event/encounter identity, roll/commit
receipt and durable periodic provenance. Owner contribution consumes the settled
amount, including summon projectiles.

Magical Allergy subtracts10/13/16/19/22/25% Magic defense with rounded-up reduction.
Muscular Atrophy subtracts5/7/9/11/13/15% maximum melee damage, including NPC offhand
maxima, with rounded-up reduction and positive maximum floor1 (zero stayszero).
Other styles and existing water/defense debuffs retain their own ordering.

## Non-corporeal state and harm families

The owner explicitly confirmed that only **magical combat harm** gets through,
including indirect harm. Incoming/outgoing nonmagical combat harm settles as a
distinct blocked result with factual and legacy damage0. It is not an ordinary
zero/miss and does not manufacture contact procs, contribution or healing.
Phase admission happens at settlement, including already-launched attacks;
Stagger and critical launch receipts remain immutable and commit once.

| Family | Nature / phase treatment |
|---|---|
| Ordinary melee/ranged/thrown/cannon, physical extra hits and cleave | Nonmagical; each actual victim settles separately |
| Ordinary poison, even spell-applied | Nonmagical; cleanse/status continue, no damage or catch-up; chat reports actual loss |
| Standard/NPC Magic and spell child damage | Magical; existing admission, mitigation and attribution retained |
| Authored elemental sword and Blue/Earth/Red/Black/KBD armor or weapon breath | Explicit magical nature; original true-damage calculation, delivery style, RNG and death order retained |
| Frostbite reflection | Existing magical nature retained |
| Ordinary recoil, Cleric Thorns, Divine Retribution | Nonmagical reflection; no recursive reflected request generated |
| Elder Green armor true/acid damage and its untyped armor burn | Existing untyped nonmagical classification, explicitly reviewed as a family rather than inferred from artwork |
| Raw DragonFireBreath and RangeUtils dragon flames | Reviewed magical exception; original `Mob.damage` path remains unchanged, without new callbacks |
| Genuine SCRIPT/ENVIRONMENT and tutorial survival HP paths | Outside combat intangibility; unchanged |

Attempt-level children keep their original Stagger gate and independent AoE
selection. A phase-rejected positive physical root feeds0 to positive-hit helpers;
physical contact Ogre is gated centrally even when the delivery style is Magic.
Independent magical effects can still pass; a tangible neighboring cleave victim
is not canceled because the primary victim is phased. No new proc chance is added.

Molecular Antipode rolls5/7/9/11/13/15% once per eligible world game tick. It lasts
1/1/1/2/2/3 game ticks. The reusable actor-owned state tracks its own lifetime,
expiry/source and rearm receipt. One tangible attack attempt rearms it, even if
Stagger forces that attempt to0; this is deliberately separate from Stagger's
five **unstaggered** recovery attacks. The next phase roll waits until at least
the following tick, so scheduler ordering cannot phase the same action midway.
Polls, getter reads, impacts and extra victims do not rearm. Recasts/source changes
do not reset the receipt. Death/respawn clears lifetime state.

NPC opacity is an independent appearance flag(type16), about50%, with no art or
summon-flag replacement. It travels only to matching clients, uses runtime NPC
indices, and is reset on removal/reuse and client session changes. Existing sprite
layers, animation IDs, menu targeting and overlays remain unchanged; software and
OpenGL use the existing shared color-transform/alpha pipeline.

## Validation and evidence

Durable evidence:
`/home/justin/.local/share/cleric-completion-review-20261031/worker-evidence/`.
The command receipts, Ant aggregate, native metadata bytes, source/asset audit,
final offscreen renders and `artifact-hashes.json` are retained there.

- Matching maintained server and desktop client Java8 builds passed, with512MiB
  SerialGC and serial heavy jobs; generated contract checks passed.
- Native buff consistency88; hostile admission/phase119; real bow/thrown/Magic,
  contact/independent elemental and cleave/poison feedback30; actual summon,
  Thorns/Frostbite/recoil/Elder armor-burn producers24; retained blessing360.
  These run through `test-cleric-consistency-buffs.py`, `test-cleric-hostile.py`,
  `test-cleric-projectile.py`, `test-cleric-hostile-producers.py` and
  `test-cleric-blessings.py` with the matching jars and temporary sparse runtime.
- `test-cleric-three-tier-client.py --wire /tmp/cleric-schema4.bin --render <evidence>/render`
  passed6,455 checks (5,005 truncation cases plus1,450 other checks), plus68
  retained generic HUD checks. Actual opcode dispatch, all17 entries, mixed costs,
  old schema3 rejection, all9 flavor metadata, grouped HUD labels, complete-effect
  guide pagination and native font widths are covered. Core independently viewed
  the final HUD, power tooltips and representative Light/Resplendence/Unmaking
  guide pages; offline-only visual limits remain explicit.
- Full16,384-NPC registry/native carrier and maintained client capacity fixtures
  passed at512MiB. Added phase proof covers runtime index16,383 versus definition65,535,
  a complete1,024-record/4,098-byte appearance batch, removal and reuse reset,
  alongside unchanged classic/10053 expanded carriers.
- Existing Cleric foundation, effect-state, transaction, C09/C10, Purify/Restore,
  C12 devotion/animations, status inventory, sigil assets/production, staff,
  Holy Power equipment, well infusion223 and well spots364 plus1,428 client
  native picking/rendering checks passed. Effect-state consumer/family assertions
  were deliberately extended for the two new hostile slots; the Holy Power source
  guard now checks the current typed god-spell roll path. Legacy fixtures remain
  isolated historical coverage, not active retired casts.
- Shared Stagger/Crit/delay native171 and pure state gates, poison model/balance,
  poison lifecycle and software/OpenGL alpha gates passed.
- The official Ant targets passed: all nine Slayer regressions, Slow115,
  battleaxe coating runtime1,042, antidote102 and combat144/144. The exact aggregate
  and output are `cleric-completion-regressions.xml` and `ant-regressions.log`.

**Inherited strict art-receipt exception:** `test-battleaxe-coatings.py` still
fails its whole-archive historical manifest assertion, exactly as starting source
`a38414a86` does. Both archives are byte-for-byte identical to that baseline.
The receipt is missing previously approved unrelated additions: custom texture
entries67–72 and classic sprite entries4100–4117. It has no removed/changed
preserved entries. No archive or historical manifest is regenerated or changed.
The baseline failure, exact entry diff and explicitly scoped diagnostics are
preserved as `coating-baseline-check.log`, `coating-inherited-manifest-diff.json`,
`coating-owned-generator.log` and `coating-owned-client.log`. The diagnostics
independently verify all15 coating forms, native held/dose bytes, original
archive headers/payloads,34 Aseprite roundtrips and both packaged client modes,
with only those enumerated unrelated additions admitted. This does not relabel
the strict historical receipt as passing. The actual1,042-assertion coating
runtime gate passes unchanged.

Projectile ring recoil retains its historical no-NPC-contribution behavior while
preserving the request's owner and boosted settlement; this task does not add new
loot attribution policy. Thorns/Frostbite/summon producers consume their settled
amount in their existing contribution roles. Actor poison keeps durable owner
and lifetime provenance even when phase blocks all harm. Elder armor burn spends
its normal pulse while blocked and never catches up later.

Private gameplay acceptance is not claimed by these offline checks. Zamorak
hostile effects/Cosmic redesign and live/package activation remain separate work.
