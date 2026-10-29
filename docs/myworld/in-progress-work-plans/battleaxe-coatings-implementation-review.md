# Battleaxe coating implementation — Core review

Complete Phase 2 candidate on `feat/battleaxe-coatings-implementation`, based on
published `3925067cefa19fee8962e825b8928c8b7a4c91a3`. The owner's October 29
“Use those recommendations” approval supersedes the Phase 1 three-dose and
unresolved-design text. This is ready for Core source review; owner visual and
private gameplay acceptance remain unrun. No server or saved map was changed.

## Final content and initial economy

One unfinished Avantoe 460 + Cockatrice Eye 3342 + Sticky Saliva Gland 3341 makes
**one single-use Viscous weapon coating 3410**, at Herblaw 46 and XP 300 before
ordinary multipliers. Use either rare ingredient on the unfinished potion, or
use the two rare ingredients together while carrying the unfinished potion.
All three exact inventory instances are validated before mutation. Existing
Druidic Ritual, member-world and fatigue rules apply. No dead-end intermediate,
Drink action or multi-dose/decanting family was added.

The coating's base value is 300; each counterpart is base weapon value+300.
That follows the approved single-use economy instead of pricing three weapon
applications from one recipe. Existing independent Eye/Gland drops stay 1/128;
`NpcDrops.java` is source-identical to baseline. No new supplies, drop chances,
shops or Slayer/application level gates are introduced.

**Necessary prerequisite correction:** unfinished Avantoe 450→460 actually required
Herblaw 50 (the adjacent Irit row was 45). It is now 46, matching the approved recipe,
and the client mixing label is 46. Cleaning Avantoe remains 43 and all completed
Avantoe v 4 potion requirements remain 50. Production tests exercise 45 reject,
46 success and 50 success for both unfinished creation and coating completion.

Coating permanently converts one unnoted, unequipped eligible inventory weapon,
leaving one empty vial. “Wipe clean” returns the original weapon without refund.
Both input orders and full inventories work. Notes, equipped items, stale or
copied instance references, invalid weapons, ordinary poison, already-coated
weapons and unsupported output IDs reject without consumption. The fixed-size
exchange holds the actual Inventory list lock, validates all inputs/outputs
first, preserves weapon unique item identity/durability, then publishes once.
A real `GameDatabase.savePlayerInventory` snapshot is captured into an in-memory
sink and round-tripped through maintained Item construction; no database
constructor, connection, credentials or account is used.

| Base | Coated | Name | Poison tier | Applied /source cap |
|---|---|---|---:|---|
|89|3411|Viscous Iron battle Axe|4|8 /20|
|90|3412|Viscous Steel battle Axe|5|10 /25|
|91|3413|Viscous Mithril battle Axe|6|12 /30|
|92|3414|Viscous Adamantite battle Axe|8|16 /40|
|93|3415|Viscous Rune battle Axe|10|20 /50|
|205|3416|Viscous Bronze battle Axe|3|6 /15|
|429|3417|Viscous Black battle Axe|5|10 /25|
|2002|3418|Viscous Tin battle Axe|1|2 /5|
|2013|3419|Viscous Copper battle Axe|2|4 /10|
|2024|3420|Viscous Titan Steel battle Axe|7|14 /35|
|2035|3421|Viscous Orichalcum battle Axe|9|18 /45|
|2156|3422|Viscous White battle Axe|5|10 /25|
|2752|3423|Viscous Dragon battle Axe|11|22 /55|
|3118|3424|Viscous Grey battle Axe|5|10 /25|
|3269|3425|Viscous Exalted Rune battle Axe|12|24 /60|

All fifteen full server templates retain every stat, speed, required level/skill,
prayer, hand slot and trade/member/note policy. Dragon retains the actual Hero's
Quest equip rejection/success path. High-roll membership maps back to the base;
Grey/Exalted intentionally keep their existing non-member behavior rather than
silently fixing unrelated base policy. Plain Titan Steel gains no intrinsic
poison. Effective client ItemDef flags/value/commands are checked after loading;
combat stats/requirements remain server-authoritative equipment data, not invented
unused client fields. Wield and Wipe clean coexist. Item text uses “Slow1” and
“weaker poison,” leaving backend numerics to design/evidence.

## Combat contract and exact timing distinction

On a resolved, positive **primary main-hand** hit to a surviving legal NPC,
the committed coated weapon contributes 4 Slow power with source cap 12. There
is no Slow RNG. Zero, suppressed, downstream-zero, killing, offhand, secondary,
reflection, DOT and PvP paths have no new Slow producer. Weapon ID is captured
before primary hit rolls/settlement and passed through the settlement hook;
equipping after commitment cannot retroactively add a coating effect.

Shared Slow remains the maintained engine:10 power/tier,2 decay per 4 ticks,
cap 12 buffer, retained higher source caps and committed combat deadlines.
Repeated coating hits alone reach Slow1; a retained 22 cap can be refilled by
weaker sources. Retarget/lifecycle/HUD behavior remains under the existing shared
state rather than a per-weapon timer.

Poison deliberately retains the **ordinary pre-mitigation policy**, proc timing,
weapon/armor roll order and max-applied/summed-cap aggregation. Only the coated
weapon payload changes to tier×2 /tier×5, without a floor. One existing dynamic
weapon roll feeds the existing target-owned poison pool. This differs from the
new actual-damage Slow eligibility: if charging Dark Beast mitigation changes a
formula 1 into actual 0, ordinary and coated weapon poison still follow their
existing pre-mitigation proc, while Slow does not apply. A real production
counterexample verifies this distinction, alongside a legacy armor-policy/RNG
comparison. Moving armor poison later or rolling it twice was avoided.

PvP's generic melee helper ignores only the new coated weapon poison contribution;
existing armor contribution remains. There is no new PvP Slow or poison effect.
Tin applies 2/cap 5 and uses ordinary subthreshold cleanup, with no invented grace
period or minimum damage. It can still add 2 to an already stronger shared pool
and transfer provenance only on actual addition. Natural poison drain 3/8 ticks,
antidote cleansing, ownership, persistence and lifecycle code are unchanged.

## Native art and discovery

`dev/myworld/battleaxe-coatings/manifest.json` is the single maintained roster and
balance/asset allocation input. `scripts/generate-battleaxe-coatings.py` derives
server definitions, client definitions/aliases, explicit gameplay mapping and
sprite outputs. IDs 3410–3425 are appended after gem equipment 3408/3409; historical
antidote 3400–3407 and worker 1/3 references do not collide. New appearances 1097–1111
share one native `equipment:viscousbattleaxe` family. Existing IDs are unchanged.

Fifteen inventory/ground PNGs preserve the authentic 84/Dragon 272 silhouettes,
48×32 canvases, handles and **original palette masks**. Teal edits only existing
reviewed blade-edge pixels. Held art preserves all 18 native crops, shifts, offsets,
64×102/84×102 canvases and transparent hand gaps; mirrored review is included.
White/Grey preserve their base black held palette mismatch rather than changing
the original weapons. Thirty-four editable Aseprite sources are single-frame,
native-canvas files with exact PNG roundtrips (`--oneframe` prevents implicit
numbered-image animation import). No paid artwork, install or generation service
was used.

Custom native archive adds one family; classic ZIP adds 18 Sprite.unpack records
4000–4017 in an unused range within the existing 4501 sprite capacity. The normal
classic allocator/loader shares this family across all aliases. No new runtime
PNG fallback loader remains. All 1002 original custom serialized entries and all
2259 original classic payloads, metadata, local headers and compressed bytes are
retained exactly. The generator only replaces its owned appended ZIP tail.

Core's independent unmodified-toolkit probe found all 16 unnoted views with correct
selected-source assets as **CANDIDATE**, not RESOLVED, under existing classpath
resource-authority limitations. Noted projection has an existing EntityDef:11
MEMBER_SELECT limitation also reproduced on Antidote 1474 and Monocle 3409. Native
client note rendering tests pass separately. The discarded PNG loader's generic
postload refusal is gone. No toolkit roster, source bypass or toolkit code change
was introduced. Independent logs are copied beside this review.

## Validation and remaining acceptance

- Final focused production coating fixture: **1042 assertions**, including all
 15 effective server templates,30 full-inventory apply/save/wipe rounds,90 invalid
 conversion cases, real recipe/unfinished level boundaries, exact XP multiplier
 path, primary damage/poison, caps/buffering, decay/lifecycle, ownership, PvP,
 committed-weapon swapping and downstream-zero/armor RNG counterexamples.
- Antidote 102 assertions, shared Slow and strict combat **144/144** pass.
- All nine independent Slayer regression targets pass.
- Actual native client fixture: **192180 checks per mode**, custom and classic.
 It executes authentic alias allocation, native ZIP unpacking, named selection,
 software rasterization and production OpenGL texture-mask conversion, including
 every unchanged inventory metal/handle pixel and all 18 held frames. This is not
 a claim of an interactive GPU/driver or equipped-character gameplay test.
- Generator/asset/ASE/archive checks, client build, Herblaw, poison, potion/HUD,
 retired items, components and equipment animation checks pass. Commands,
 concise logs, exact matrix and artifact hashes are in
 `battleaxe-coatings-implementation-evidence/`.

Portable review folder:
`output/battleaxe-coatings-implementation-review/` — self-contained `review.html`
embeds all images; the folder also has inventory contact and held/mirror sheets.
Reproduce with `python3 scripts/review-battleaxe-coatings.py`. Core may copy the
whole folder into staged `art/battleaxe-coatings`. Native/zoom inventory and
representative Tin/Dragon held sheets were visually inspected. No supported
existing offline full-character compositor was found: body/hand/camera overlap,
owner art acceptance and actual private gameplay remain explicit Core/owner gates.

Both authored map areas stay untouched. This task performs no live operations,
main integration, account access, owner map import or installed-toolkit change.
