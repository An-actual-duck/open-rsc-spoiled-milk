# In-game guide review: recommended updates

Review-only handoff, 2026-11-06. Base: `5d805c233aabe3fe9f9041c65ff1e1ccc7e59633`.
No guide, gameplay, map, asset, account or deployment changes are included.

## Scope and evidence

Reviewed the client skill guide and its Cleric/Magic catalogs, sampled quest-help metadata and renderer, and compared the relevant claims with server production, combat, summoning, equipment and Slayer code. Recommendations describe implemented source, not earlier design discussions or deferred Tier 4/boss content. Public Oracle source was reported by the manager as `53adcce6e09d7e2a74862cee989dac7355a2837a`; this review base additionally includes the bat projectile and Slayer starter-supply fixes awaiting deployment.

Source references below are relative to the repository root. `SG` means `Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java`; `CG` means `Client_Base/src/com/openrsc/interfaces/misc/ClericSpellGuideCatalog.java`; `MC` means `Client_Base/src/orsc/mudclient.java`. Line numbers refer to the review base and may move after the parallel altar edit.

Local evidence: `output/reviews/in-game-guide-review/` contains the native-font width audit source/results, a Smithing icon identity table and `review-evidence.json` with check results. Generated evidence is not packaged or committed.

## Confirmed inaccuracies and presentation defects

### 1. Name the equipment condition for stronger mind-rune spells

**Guide:** SG:781 says, “Spells using mind runes will do damage equal to chaos runes,” without a condition. This implies the upgraded damage cap is universal.

**Implementation:** `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java:2295` requires equipped Chaos gauntlets **and** the matching Family Crest gauntlet selection before promoting the cap to 60%; `SpellClassification.java:80` identifies the eligible spells. Ordinary mind-rune spells retain their normal cap, subject to separate equipment effects.

**Replace with:** “Chaos gauntlets raise mind-rune combat spells to the chaos-rune damage cap.” Keep the exact condition in the guide; do not change spell balance to fit the old sentence.

### 2. Tourist Trap help promises the retired Fletching reward

**Guide:** MC:1339 lists a `10 Fletching` requirement; MC:1340 lists two reward choices from Agility, Fletching, Thieving and Smithing.

**Confirmed reward mismatch:** `server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java:318` and :340 offer **Crafting**, Agility, Smithing and Thieving, and award Crafting on that choice. Players cannot choose the advertised Fletching reward.

**Replace reward wording with:** “Choose two experience rewards from Agility, Crafting, Smithing and Thieving.” Preserve the existing formula only after checking it against the quest reward definition. Review the `10 Fletching` requirement against the actual progression checks separately; do not blindly substitute a new requirement. Other quest rows also use Attack/Strength/Defense terminology (MC:1339–1340); this audit does not certify every quest requirement or XP formula.

### 3. Devotion's offering XP bonus is understated for higher-tier remains

**Guide:** SG:1488 says “+1 Worship XP per offering for each devotion.” That is the unscaled bones/ashes bonus, not the bonus for every offering type.

**Implementation:** `server/src/com/openrsc/server/content/Devotion.java:68` takes non-negative existing Devotion, then :84 passes it through `OfferingExperience.scaleDisplayedExperience`. `server/src/com/openrsc/server/content/OfferingExperience.java:28` applies the offering multiplier: bones/ashes 100%, big bones 120%, bat bones 130%, demon ash 140%, dragon bones 160%, rounded down. For example, 100 positive Devotion adds 160 bonus XP to a dragon-bone offering before other applicable bonuses, rather than 100.

**Replace with:** “Positive Devotion adds bonus Worship XP, scaled by the offering type.” A short follow-up can list the same multipliers already used for base offering XP. Avoid presenting fractional per-Devotion gains as separately rounded increments.

### 4. Six Smithing scythe rows display mace icons

**Guide:** SG:1751–1761 passes mace/scythe arguments in the wrong order for Bronze, Iron, Steel, Mithril, Adamantite and Rune. The helper signature at :1777 expects `maceId, scytheId`; :1783 renders `scytheId` beside “scythe - 3 bars.” Tin, Copper, Titan steel, Orichalcum and Exalted Rune are already ordered correctly.

| Row | Current displayed item | Required scythe ID |
|---|---|---|
| Bronze, SG:1751 | 94, Bronze Mace | 3183 |
| Iron, SG:1752 | 0, Iron Mace | 3184 |
| Steel, SG:1754 | 95, Steel Mace | 3185 |
| Mithril, SG:1755 | 96, Mithril Mace | 3186 |
| Adamantite, SG:1758 | 97, Adamantite Mace | 3188 |
| Rune, SG:1761 | 98, Rune Mace | 3190 |

**Recommendation:** Swap those six argument pairs. Item identities were checked against `server/conf/server/defs/ItemDefs.json`, `ItemDefsCustom.json` and `ItemDefsMyWorld.json`; the full extracted table is in local evidence. This is guide artwork selection, not proof of a gameplay item-ID offset.

### 5. Ordinary guide rows do not wrap and some exceed the panel

**Renderer:** SG:25 fixes width at 430px. SG:327–331 draws ordinary text once, with no wrapping; item rows start 85px into the panel (335px with a 10px right margin), full-width Info rows start 10px in (420px remaining). Cleric pages use their own wrapped renderer at SG:384 and are not the same issue.

**Measured with the actual native font 2:** Thunder's explanation (SG:782) is **603px**, exceeding even the full-width Info budget. The coating recipe (SG:978) is **395px**, exceeding the item-row space. The Mourning Unicorn trait (SG:1269) is **422px** and the Foundry Dragon recipe (SG:1241) is **360px**, both drawn with the NPC text inset. Twenty of 655 sampled literal rows exceed the conservative 335px item budget, but not all twenty are defects because full-width Info rows have more room.

**Recommendation:** Wrap ordinary rows to their actual text-column width and let their height grow, or split/shorten these specific rows first. For example: “Startle staggers for 1 attack.” / “Then 5 unstaggered attacks grant immunity.” Keep the coating ingredients readable across two lines. Add native-font bounds checks for ordinary guide rows as well as Cleric pages. Enlarging only the spellbook dropdown will not fix this separate guide renderer.

## Useful missing explanations and catalog entries

These are confirmed coverage gaps, not claims that the underlying mechanics are broken. They can follow the corrections above without a general guide rewrite.

### 6. Finish the existing Crafting catalog

**Where:** SG:1577–1597 lists twenty hide families but omits six implemented ones. `server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java:1624–1653` provides: Giant frog (base Crafting 8), Ugthanki (22), Naga (30), Terror dog (38), Bloodveld (38), Dark beast (46). Add each at its level using the actual coif/cuirass family convention; piece requirements rise above the base level (:1742), so label a family row “from level X” rather than implying every piece unlocks together.

SG:1599–1605 also omits Wool gloves and Wool boots. The recipes at `Crafting.java:200–206` make each from two balls of wool at Crafting 1. Add those two entries; note that an unenchanted cape's menu name is “White cape,” where the guide currently says “Wool cape.”

### 7. Explain the restrictions that matter during Cleric/PvP play

**Where:** CG:75 mentions party/clan safety but omits friends; CG:92 and :128–130 describe Unify without teleport restrictions. General Melee Info (SG:602–611) and Ranged Info (SG:1331–1335) contain no current PvP overview; Wood's “another NPC” at SG:785 is incomplete for legal PvP secondary targets.

**Evidence:** `server/src/com/openrsc/server/util/rsc/CombatEffectUtil.java:81–106` protects the caster's outgoing friend list, party and clan from automatic/area damage, requires a player primary for player splash, and checks Wilderness legality for each recipient. `server/src/com/openrsc/server/model/combat/PlayerPvpCombat.java:29` applies that ally rule to automatic enemy selection, including hostile Cleric targeting (`content/cleric/runtime/ClericHostileRuntime.java:116`). `util/rsc/TeleportRestrictions.java:9–31` blocks teleports at Wilderness level **20 or higher**, during active duels and in specified restricted locations; `content/cleric/runtime/ClericSupportCasting.java:42`, :133 and :157 checks Unify's caster, recipient and landing place.

**Suggested small additions:**
- “Your area attacks and curses exclude party members, clanmates and players on your friends list. Adding a friend protects them from your effects; it does not protect you from theirs.”
- “Player splash requires a player target. Every recipient must be legally attackable in the Wilderness; NPC attacks do not splash players.”
- “Unify follows teleport restrictions, including no use at Wilderness level 20 or higher or during a duel.”

Keep direct deliberate attacks separate from automatic/splash protection; the guide should not promise that social membership makes all PvP damage impossible. Describe Wood as hitting an additional eligible enemy rather than only an NPC.

### 8. Connect the existing sigil manufacturing entries into a usable recipe

**Where:** SG:1213–1224 gives a useful overview and infusion levels; SG:1631–1632 lists metal blanks; SG:1735 gives Moonsilver smelting. Missing: the tool/input action for carving and the distinction between **Crafting** levels and **Blessing** levels.

**Evidence:** `server/src/com/openrsc/server/content/cleric/ClericSigilMaterial.java:5–7`: Stone carves/infuses at 1/1, Silver at 20/16, Moonsilver at 40/40. `plugins/custom/myworld/skills/blessing/SigilProduction.java:41–65` opens carving by using a chisel with the material; :205–262 blesses god sigils at their altar and spends Devotion. `content/cleric/ClericMetalSigils.java:8` specifies three blanks per bar; `ClericSigilProductionCatalog.java:91–98` gives the extra-output interval.

**Suggested addition:** “Use a chisel on Stone or a blank metal sigil to carve its design. Silver and Moonsilver bars first make 3 blanks with a sigil mould at a furnace. Carving needs Crafting 1/20/40; infusion needs Blessing 1/16/40. Take god designs to their god's altar, Grace to Divine wellsprings, and Blight to Ichor wells.” Split into short readable rows. Existing per-spell costs are already generated from authoritative metadata; no new static cost table is necessary.

### 9. Finish the Summoning Info explanation of ongoing cost and XP

**Where:** SG:1279 says support summons last one minute, then lists upkeep escalation/recovery, but never states that life runes automatically sustain them or how this gives XP.

**Evidence:** `server/src/com/openrsc/server/content/Summoning.java:1392–1401` renews support duration when upkeep succeeds and dismisses it otherwise. :2050–2063 awards **10 base displayed Summoning XP per successful upkeep consumption**, not per rune; :1436–1477 separately awards active-time Command XP once per minute. Rune preservation can avoid consumption XP while Command XP still progresses.

**Suggested addition:** “Support summons automatically consume life runes to renew their duration. They leave if you cannot pay upkeep. Consuming upkeep runes and commanding an active support summon earn Summoning XP.” Exact advanced equipment modifiers can stay with equipment details. Existing Inner Demon naming, summon unlocks and basic costs already match the inspected profiles; no Abyssal Demon rename is still needed.

### 10. Add one practical coating-use note

**Where:** SG:978 lists the recipe but no Info row explains its target, persistence or cleaning.

**Evidence:** `server/src/com/openrsc/server/content/coatings/BattleaxeCoatings.java:22–34` adds 4 Slow power per positive primary hit, up to the Slow 1 cap; `content/Slow.java:16–19` and :83–88 distinguish accumulating power from actual Slow tier. Coating application and wool cleaning are at `BattleaxeCoatings.java:123–143`; `content/PoisonPower.java:15–22` supplies weaker poison power for coated axes.

**Suggested addition:** “Apply one dose to an uncoated battleaxe. Its hits build Slow and can apply weak poison. The coating stays until removed with wool; cleaning consumes the wool and coating.” If numeric detail is included elsewhere, explain the buildup before promising Slow on the first hit. Keep the current player-friendly examines and do not restore the removed Wipe clean menu option.

## Optional discoverability/polish

- **Crafting Guild extension:** SG:1679 mentions only the level-40 guild entrance. Add “Gem-dragon caves below the Crafting Guild require 70 Crafting; prepare for dangerous combat.” This is enforced and warned by `server/plugins/com/openrsc/server/plugins/authentic/npcs/rimmington/MasterCrafter.java:20–27` and :76. Gem cutter gloves/monocle can have a short pointer in the gem-cutting section (SG:1613), rather than an exhaustive drop-table reference; their effects are implemented in `content/gemdragons/GemCuttingEquipment.java:10–31`.
- **Slayer:** Existing task dialogue warns about hazards and, in this source revision, explicitly explains starter supplies and where to buy more (`plugins/custom/myworld/npcs/MonsterSlayerContacts.java:176–187`). Do not report that assignment guidance as missing. An optional guide index entry could link players to the task giver/associates; it must describe the new supplies only once the corresponding server update is activated. There is no existing full Slayer skill-guide catalog to repair.
- **Quest-help limitation:** `Client_Base/src/com/openrsc/interfaces/misc/QuestGuideInterface.java:88–89` replaces active-quest help with “Quest progress coming soon...” and hides the start/requirements block. A useful minimal improvement is to retain the start contact/requirements with “Detailed walkthrough unavailable” instead of implying a forthcoming feature. Actual walkthrough writing is a separate, larger task.
- **Cleric navigation:** CG:21 sorts all spells by level and :37 splits each into pages; SG:18–19 shows one page at a time. A god-line jump/filter would reduce scrolling, but this review did not find an unreachable final Cleric page or justify a mandatory redesign.

## Already covered or owned elsewhere

Cleric tiers, Holy Power thresholds **0/20/45/75/115/160**, cumulative effects, stacking/replacement rules, the three current god lines, and generated per-cast costs are already present in CG:72–106 and :107–216. Costs agree with `content/cleric/ClericSpellDefinition.java:123–141` and `ClericSigilCost.java:20–27`: god+purpose Stone at T1, Stone+Silver at T2, Silver+Moonsilver at T3; Unify 2 Stone Grace; Greater Unify 3 Stone + 3 Silver Grace. No recommendation to return to retired neutral sigils, old buffs or manual formula duplication.

Rune-altar coordinate/level corrections and the related Cosmic/Death jewelry destinations are assigned to the parallel `fix/rune-altar-guide-coordinates` worker. Manager/worker evidence identifies the installed layered map and distinguishes legacy altar interiors; this report does not duplicate those edits or recommend substituting the old interiors. The specific confirmed stale primary entries were Cosmic, Soul and Death, and all fourteen guide listings are being reviewed for signed-level display.

## Verification and limits

- Passed: skill-guide Info pass; guide text polish; quest-guide UI source checks; Thieving runtime/guide comparison.
- Passed: existing Cleric schema5 client guide fixture, **403 checks**, using the existing client JAR and recorded wire fixture. This is useful existing coverage, not a new build or proof from a fresh live server payload.
- Two existing gates fail on retired expectations: `tests/myworld/test-skill-guides.py:157` expects normal Antidote at level 8 (current guide separates Weak 8 / normal 22 / Strong 38); `test-magic-guide-and-chaos-gauntlets.py:67` expects the prior Thunder sentence rather than current shared-Stagger wording. Repair those expectations when implementing guide changes; passing substring checks alone will not catch the conditional gauntlet wording, swapped scythe IDs or unwrapped rows.
- `git diff --check` passed. `workspace-layout.py check` reports inherited ignored asset/output entries in this reused worker; none points to the new `output/reviews/in-game-guide-review/` category. Those unrelated files were preserved.
- Native-font width audit used the actual packaged font library and client font loader, without a login or network. No screenshot-level claim is made about every page, scroll position or viewport.
- Not a full quest reward/requirement certification, full NPC tutorial rewrite, exhaustive equipment/drop encyclopedia, map route walk-test, localization/accessibility review or public deployment audit. Legacy Rune Mysteries metadata and old tutorial dialogue require reachability/configuration checks before calling them player-visible defects.

Recommended order: correct items 1–5 and refresh the stale guide gates; add the small practical explanations in 6–10; consider optional navigation and broader quest work separately. All proposed wording remains for manager/user review.
