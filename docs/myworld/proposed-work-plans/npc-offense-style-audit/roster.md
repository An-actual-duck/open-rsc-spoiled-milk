# Complete attackable NPC style audit

All 298 definitions; **source profiles are not guarantees of actual attack exclusivity**. Recommendations are not implemented. A=first-pass candidate, B=role/variant review, C=special mechanics or optional extension, D=retain for now.

## PURE_MAGIC (13)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 57 | Darkwizard | 13 | 12 / 0 / 15 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 60 | Darkwizard | 25 | 27 / 0 / 27 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 79 | Witch | 25 | 30 / 0 / 35 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 81 | Wizard | 16 | 18 / 0 / 18 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 93 | Monk | 13 | 12 / 0 / 12 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 200 | Druid | 29 | 28 / 0 / 28 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 270 | Chaos Druid | 19 | 14 / 0 / 18 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 358 | Necromancer | 34 | 28 / 0 / 28 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 498 | skeleton mage | 21 | 23 / 0 / 24 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 713 | kolodion | 12 | 10 / 0 / 20 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 789 | Battle mage | 52 | 52 / 0 / 52 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 790 | Battle mage | 52 | 52 / 0 / 52 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |
| 791 | Battle mage | 52 | 52 / 0 / 52 | MAGIC_ONLY | A | Existing nominal magic specialist: retain projectile and enforce exclusive style, shot-hold and pursuit rules.  |

## MELEE_MAGIC (46)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 22 | Lesser Demon | 79 | 70 / 0 / 78 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 35 | Delrith | 30 | 37 / 0 / 42 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 53 | Ghost | 25 | 23 / 0 / 23 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 80 | Ghost | 25 | 23 / 0 / 23 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 104 | Moss Giant | 62 | 65 / 0 / 65 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 135 | Ice Giant | 68 | 70 / 0 / 70 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 139 | Monk of Zamorak | 29 | 28 / 0 / 28 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions.  |
| 140 | Monk of Zamorak | 19 | 18 / 0 / 18 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions.  |
| 158 | Ice warrior | 57 | 59 / 0 / 59 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 178 | Ghost | 25 | 23 / 0 / 23 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 181 | Lesser Demon | 79 | 70 / 0 / 78 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 184 | Greater Demon | 87 | 77 / 0 / 86 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 196 | Dragon | 110 | 110 / 0 / 110 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 201 | Red Dragon | 140 | 98 / 0 / 140 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 202 | Blue Dragon | 105 | 91 / 0 / 105 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 203 | Baby Blue Dragon | 50 | 84 / 0 / 84 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 216 | tree spirit | 95 | 105 / 0 / 105 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 254 | Ice queen | 103 | 104 / 0 / 105 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions.  |
| 290 | Black Demon | 156 | 91 / 0 / 155 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 291 | Black Dragon | 200 | 182 / 0 / 210 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 293 | Monk of Zamorak | 47 | 48 / 0 / 48 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions.  |
| 298 | Otherworldly being | 66 | 66 / 0 / 66 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 315 | Chronozon | 121 | 182 / 0 / 183 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 323 | Paladin | 71 | 88 / 0 / 88 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 344 | Fire Giant | 109 | 105 / 0 / 110 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 361 | The Fire warrior of lesarkus | 63 | 72 / 0 / 72 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 364 | Lucien | 21 | 23 / 0 / 24 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions.  |
| 477 | King Black Dragon | 245 | 224 / 0 / 250 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 555 | Chaos Druid warrior | 44 | 48 / 0 / 48 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 567 | Salarin the twisted | 69 | 68 / 0 / 68 | MAGIC_ONLY | A | Existing mixed magic user with caster identity; preserve quest/prayer/special restrictions. Salarin player-cast response drains Melee; separate from ordinary NPC projectile attack. |
| 568 | Black Demon | 175 | 91 / 0 / 195 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 594 | Moss Giant | 62 | 65 / 0 / 65 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 615 | Nazastarool Ghost | 83 | 90 / 0 / 95 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 632 | Paladin | 71 | 88 / 0 / 88 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 633 | Paladin | 71 | 88 / 0 / 88 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 664 | Ghost | 29 | 30 / 0 / 33 | MAGIC_ONLY | B | Existing supernatural mixed caster: recommended exclusive magic; quest forms retain encounter restrictions.  |
| 809 | Balrog | 217 | 250 / 0 / 999 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 843 | Fire warrior | 61 | 64 / 0 / 64 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Existing elemental mixed user: preserve body/weapon melee identity and existing elemental projectiles.  |
| 844 | Elder Green Dragon | 275 | 250 / 235 / 270 | MELEE_MAGIC_RANGED_SCRIPTED | C | Already all three damage styles: ordinary magic/melee, scripted melee sweep, Ranged fireshot and separate burn. Boss power/weights require individual review. Scripted Elder Ranged fireshot, melee sweep, Magic/Burn; ordinary profile is melee/magic. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 862 | Green Dragon | 110 | 98 / 0 / 110 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 865 | Banshee | 50 | 55 / 0 / 55 | MAGIC_ONLY | C | Banshee theme fits exclusive magical wail; preserve earplug protection, wail replacement, windups and lifecycle. Distance>1 enforced for generic projectile preference; adjacency currently favors melee. |
| 877 | Sapphire Dragon | 97 | 90 / 0 / 100 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 878 | Emerald Dragon | 118 | 110 / 0 / 122 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 879 | Ruby Dragon | 142 | 132 / 0 / 146 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 880 | Diamond Dragon | 166 | 153 / 0 / 174 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |
| 881 | Dragonstone Dragon | 192 | 178 / 0 / 202 | MELEE_MAGIC_MIXED_REVIEW_PRIMARY | C | Existing dragon magic plus separate breath handling. Choose primary power/style deliberately; defense family does not determine offense. Dragon breath paths require separate source/mitigation review; not a generic spell classification. |

## MELEE_FREQUENT_MAGIC (3)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 757 | kolodion | 65 | 70 / 0 / 72 | QUEST_FORM_SPECIFIC_MIXED | C | Preserve separately authored Kolodion forms; intro remains magic-only. Existing adjacency preferences are 85/15 for forms757-759 and 10/90 for760, not guaranteed attack shares.  |
| 758 | kolodion | 68 | 78 / 0 / 78 | QUEST_FORM_SPECIFIC_MIXED | C | Preserve separately authored Kolodion forms; intro remains magic-only. Existing adjacency preferences are 85/15 for forms757-759 and 10/90 for760, not guaranteed attack shares.  |
| 759 | kolodion | 46 | 23 / 0 / 38 | QUEST_FORM_SPECIFIC_MIXED | C | Preserve separately authored Kolodion forms; intro remains magic-only. Existing adjacency preferences are 85/15 for forms757-759 and 10/90 for760, not guaranteed attack shares.  |

## MELEE_RARE_MAGIC (7)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 66 | Black Knight | 46 | 48 / 0 / 48 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 102 | White Knight | 56 | 58 / 0 / 58 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 108 | Black Knight | 46 | 48 / 0 / 48 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 189 | Black Knight | 46 | 48 / 0 / 48 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 401 | Black Knight titan | 146 | 148 / 0 / 148 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |
| 760 | kolodion | 98 | 98 / 0 / 105 | QUEST_FORM_SPECIFIC_MIXED | C | Preserve separately authored Kolodion forms; intro remains magic-only. Existing adjacency preferences are 85/15 for forms757-759 and 10/90 for760, not guaranteed attack shares.  |
| 836 | Grey Knight | 56 | 58 / 0 / 58 | MELEE_PRIMARY_MAGIC_SECONDARY | B | Armoured/warrior identity: keep melee emphasis and make magical support secondary; intended rare-cast profiles may retain stronger melee weighting.  |

## PURE_RANGED (20)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 64 | Thief | 21 | 23 / 24 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 65 | Guard | 28 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 100 | Guard | 28 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 127 | Jailguard | 34 | 36 / 36 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 321 | Guard | 28 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 342 | Rogue | 21 | 23 / 24 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 351 | Thief | 21 | 23 / 24 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 352 | Head Thief | 34 | 33 / 34 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 420 | carnillean guard | 28 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 551 | Gnome guard | 31 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 562 | Gnome guard | 23 | 23 / 23 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 582 | Gnome guard | 27 | 17 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 651 | Goblin guard | 48 | 51 / 51 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 684 | Ogre guard | 96 | 90 / 98 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 697 | Ogre guard | 78 | 90 / 92 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 703 | Bedabin Nomad Guard | 70 | 70 / 70 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 710 | Draft Mercenary Guard | 50 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 716 | Rowdy Guard | 50 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Already nominal ranged guard; inspect visible equipment/role per ID before treating every guard as a bow user.  |
| 717 | Shantay Pass Guard | 32 | 32 / 32 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 863 | Giant frog | 20 | 1 / 30 / 0 | KEEP_EXISTING_RANGED_SPECIALIST | C | Giant Frog already acid-spits through Ranged and explicitly cannot melee. Retain unless owner wants a different style; no need to invent a melee fallback.  |

## MELEE_RANGED (14)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 137 | Pirate | 27 | 30 / 35 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 232 | Bandit | 29 | 26 / 32 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 234 | Bandit | 29 | 26 / 32 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 264 | Pirate | 30 | 33 / 38 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 407 | Khazard troop | 28 | 31 / 31 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 409 | gnome troop | 3 | 3 / 3 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 421 | Tribesman | 39 | 40 / 40 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 574 | Yanille Watchman | 33 | 41 / 41 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise.  |
| 668 | Mercenary | 50 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 669 | Mercenary Captain | 64 | 48 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 670 | Mercenary | 39 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 690 | Mercenary | 39 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 692 | Mercenary | 39 | 32 / 48 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | Existing mixed ranged troops/mercenaries; ranged emphasis is plausible where equipment supports it.  |
| 866 | Naga | 60 | 65 / 55 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | A | Existing bolt/dart/knife/arrow/thrown-scimitar support; retain short-range melee as secondary. Naga has an explicit adjacent-melee override to revise. Distance>1 enforced for generic projectile preference; adjacency currently favors melee. |

## MELEE (195)

| ID | NPC | Level | Current Power M/R/G | Recommendation | Priority | Context |
| ---: | --- | ---: | --- | --- | --- | --- |
| 0 | Unicorn | 21 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 3 | Chicken | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 4 | Tough Goblin | 13 | 7 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 5 | Hans | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 6 | cow | 8 | 7 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 8 | Bear | 24 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 11 | Man | 9 | 11 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 19 | Rat | 8 | 10 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 21 | mugger | 10 | 8 / 0 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | New throwing-knife candidate matching existing thief/rogue support; new per-ID profile assignment and equipment review needed.  |
| 23 | Young Giant Spider | 8 | 10 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 25 | Jonny the beard | 10 | 5 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 29 | Rat | 2 | 2 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 34 | spider | 2 | 1 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 37 | Weaponsmaster | 25 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 40 | skeleton | 21 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 41 | zombie | 24 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 43 | Giant bat | 32 | 32 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 45 | skeleton | 31 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 46 | skeleton | 25 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 47 | Large Rat | 13 | 15 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 50 | skeleton | 19 | 21 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 52 | zombie | 19 | 19 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 61 | Giant | 37 | 40 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 62 | Goblin | 7 | 9 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 63 | farmer | 15 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 67 | Hobgoblin | 32 | 34 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 68 | zombie | 32 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 70 | Scorpion | 21 | 22 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 72 | Man | 9 | 11 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 74 | Giant Spider | 31 | 34 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 76 | Barbarian | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 78 | Gunthor the Brave | 37 | 38 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 86 | Warrior | 18 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 89 | Highwayman | 13 | 13 / 0 / 0 | RANGED_PRIMARY_MELEE_SECONDARY | B | New throwing-knife candidate matching existing thief/rogue support; new per-ID profile assignment and equipment review needed.  |
| 94 | Dwarf | 18 | 20 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential dwarf crossbow/throwing-weapon roles, conditional on visible equipment and new projectile assignment; do not convert every civilian dwarf.  |
| 96 | Count Draynor | 43 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 99 | Deadly Red spider | 36 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 109 | Greldo | 7 | 9 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 114 | Imp | 5 | 5 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 136 | King Scorpion | 36 | 39 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 153 | Tough Goblin | 13 | 13 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 154 | Tough Goblin | 13 | 13 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 159 | Warrior | 27 | 30 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 177 | Large Rat | 13 | 7 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 179 | skeleton | 31 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 180 | zombie | 32 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 182 | Melzar the mad | 45 | 47 / 0 / 0 | MAGIC_ONLY | B | Currently general melee despite caster identity; new ordinary spell selection needed (reuse suitable existing effects).  |
| 188 | Bear | 26 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 190 | chaos Dwarf | 59 | 60 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential dwarf crossbow/throwing-weapon roles, conditional on visible equipment and new projectile assignment; do not convert every civilian dwarf.  |
| 192 | Wormbrain | 7 | 9 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 195 | skeleton | 54 | 55 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 199 | Dark Warrior | 21 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 214 | zombie | 32 | 35 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 236 | Donny the lad | 39 | 36 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 237 | Black Heather | 39 | 36 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 238 | Speedy Keith | 39 | 36 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 239 | White wolf sentry | 31 | 31 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 243 | Grey wolf | 64 | 65 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 244 | shapeshifter | 24 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 248 | White wolf | 41 | 41 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 249 | Pack leader | 71 | 71 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 251 | Thug | 18 | 17 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 252 | Firebird | 6 | 7 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 259 | Grip | 46 | 31 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 262 | Guard Dog | 46 | 46 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 263 | Ice spider | 64 | 65 / 0 / 0 | OPTIONAL_MAGIC_SECONDARY | C | Magical spider variants could add web/frost/shadow casts; currently general melee. New attack behavior/effects need deliberate design.  |
| 265 | Jailer | 51 | 53 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 266 | Lord Darquarius | 76 | 78 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 271 | Poison Scorpion | 26 | 27 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 276 | Sir Mordred | 58 | 60 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 277 | Renegade knight | 51 | 53 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 292 | Poison Spider | 63 | 68 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 294 | Hellhound | 114 | 114 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 295 | Animated axe | 46 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 296 | Black Unicorn | 31 | 33 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 311 | Hobgoblin | 48 | 48 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 312 | Ogre | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 318 | Man | 9 | 11 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 319 | farmer | 15 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 320 | Warrior | 27 | 30 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 322 | Knight | 56 | 58 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 324 | Hero | 83 | 88 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 338 | zoo keeper | 20 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 343 | Shadow spider | 53 | 52 / 0 / 0 | OPTIONAL_MAGIC_SECONDARY | C | Magical spider variants could add web/frost/shadow casts; currently general melee. New attack behavior/effects need deliberate design.  |
| 348 | Forester | 21 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 356 | Mountain Dwarf | 28 | 30 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential dwarf crossbow/throwing-weapon roles, conditional on visible equipment and new projectile assignment; do not convert every civilian dwarf.  |
| 359 | zombie | 24 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 367 | Dungeon Rat | 16 | 22 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 383 | General Khazard | 100 | 78 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 384 | Khazard Ogre | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 386 | Khazard Scorpion | 46 | 49 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 388 | Bouncer | 122 | 130 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 399 | local gnome | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 402 | kalron | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 410 | khazard warlord | 100 | 78 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 416 | unhappy peasant | 25 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 417 | happy peasant | 25 | 28 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 425 | cult member | 20 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 426 | Lord hazeel | 100 | 78 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 428 | Khazard commander | 41 | 45 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 438 | Citizen | 11 | 10 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 439 | Citizen | 10 | 8 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 440 | Citizen | 12 | 14 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 441 | Citizen | 20 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 442 | Citizen | 15 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 462 | Platform Fisherman | 30 | 15 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 463 | Platform Fisherman | 30 | 15 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 464 | Platform Fisherman | 30 | 15 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 473 | Rat | 7 | 8 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 495 | Mourner | 22 | 30 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 502 | Mourner | 25 | 25 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 516 | target practice zombie | 24 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 518 | Colonel Radick | 51 | 30 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 519 | Soldier | 28 | 31 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 521 | Jungle Spider | 47 | 47 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 523 | Jogre | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 525 | Ogre | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 531 | Ogre chieftan | 78 | 90 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 542 | UndeadOne | 62 | 50 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 550 | charlie | 0 | 0 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 557 | Shipyard worker | 44 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 558 | Shipyard worker | 44 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 559 | Shipyard worker | 44 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 583 | Gnome child | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 584 | Earth warrior | 52 | 54 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 585 | Gnome child | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 586 | Gnome child | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 592 | Gnome local | 9 | 9 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 593 | Gnome local | 3 | 3 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 595 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 597 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 598 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 599 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 600 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 602 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 603 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 604 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 605 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 606 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 607 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 608 | Gnome Baller | 70 | 70 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 613 | Nazastarool Zombie | 83 | 90 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 614 | Nazastarool Skeleton | 83 | 90 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 630 | Blessed Vermen | 14 | 7 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 631 | Blessed Spider | 35 | 34 / 0 / 0 | OPTIONAL_MAGIC_SECONDARY | C | Magical spider variants could add web/frost/shadow casts; currently general melee. New attack behavior/effects need deliberate design.  |
| 634 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 635 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 636 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 637 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 638 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 639 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 640 | slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 641 | Kalrag | 78 | 78 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 644 | Souless | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 645 | Othainian | 78 | 78 / 0 / 0 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 646 | Doomion | 98 | 98 / 0 / 0 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 647 | Holthion | 78 | 78 / 0 / 0 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 653 | Ugthanki | 45 | 45 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 655 | Souless | 24 | 23 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 656 | Dungeon spider | 22 | 10 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 658 | Iban disciple | 19 | 18 / 0 / 0 | MAGIC_ONLY | B | Currently general melee despite caster identity; new ordinary spell selection needed (reuse suitable existing effects).  |
| 660 | Goblin | 19 | 18 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential goblin stone/spear throwers. Prefer selected distinct variants with readable equipment/projectile art, not blanket conversion of all goblinoids.  |
| 663 | San Tojalon | 120 | 120 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 671 | Mining Slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 683 | Gorad | 78 | 90 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 694 | Dwarf | 18 | 20 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential dwarf crossbow/throwing-weapon roles, conditional on visible equipment and new projectile assignment; do not convert every civilian dwarf.  |
| 699 | Dwarf | 18 | 20 / 0 / 0 | OPTIONAL_RANGED_PRIMARY_VARIANTS | C | Potential dwarf crossbow/throwing-weapon roles, conditional on visible equipment and new projectile assignment; do not convert every civilian dwarf.  |
| 702 | Captain Siad | 48 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 704 | Ogre citizen | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 705 | Rock of ages | 150 | 150 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 706 | Ogre | 58 | 70 / 0 / 0 | OPTIONAL_RANGED_ATTACK | C | Potential ogre/giant rock-throw variants; preserve obvious melee enemies by default. New attack/presentation, not existing general ranged support.  |
| 718 | Rowdy Slave | 16 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 721 | Desert Wolf | 31 | 31 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 729 | civillian | 18 | 18 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 761 | Irvig Senay | 125 | 125 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 762 | Ranalph Devere | 130 | 130 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 766 | Ungadulu | 75 | 75 / 0 / 0 | MAGIC_ONLY | C | Shaman/dead-sorcerer quest actor; separate normal/possessed forms and quest triggers before activation.  |
| 767 | Ungadulu | 75 | 75 / 0 / 0 | MAGIC_ONLY | C | Shaman/dead-sorcerer quest actor; separate normal/possessed forms and quest triggers before activation.  |
| 768 | Death Wing | 80 | 80 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 769 | Nezikchened | 172 | 178 / 0 / 0 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 772 | Viyeldi | 80 | 80 / 0 / 0 | MAGIC_ONLY | C | Shaman/dead-sorcerer quest actor; separate normal/possessed forms and quest triggers before activation.  |
| 775 | Karamja Wolf | 61 | 61 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 776 | Jungle Savage | 87 | 100 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 777 | Oomlie Bird | 32 | 20 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 786 | Pit Scorpion | 35 | 48 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 787 | Shadow Warrior | 64 | 61 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 798 | Gaia | 79 | 79 / 0 / 0 | MAGIC_ONLY | B | Currently general melee despite caster identity; new ordinary spell selection needed (reuse suitable existing effects).  |
| 802 | Greatwood | 300 | 245 / 0 / 0 | OPTIONAL_MAGIC_SECONDARY | C | Greatwood could gain roots/wood magic; currently general melee. Do not infer existing magic from appearance or high level.  |
| 816 | PK Bot | 57 | 230 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 864 | Cockatrice | 35 | 40 / 0 / 0 | OPTIONAL_MAGIC_PRIMARY | C | Cockatrice gaze is a thematic candidate, not existing generic magic. Preserve mirror-shield/gaze protections and inspect combat hooks first.  |
| 867 | Terror dog | 75 | 80 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 868 | Bloodveld | 85 | 80 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
| 869 | Dark beast | 105 | 110 / 0 / 210 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Existing scripted lightning outside general MELEE profile; preserve charge/detonation and cadence, do not stack a new generic cast on top. Scripted DarkBeast lightning (Magic). |
| 870 | Abyssal demon | 125 | 125 / 0 / 0 | MAGIC_PRIMARY_MELEE_SECONDARY | C | Demonic mixed candidate; some currently melee-only. Retain boss/quest and Abyssal mobility mechanics; generic magic alone is not a complete encounter audit.  |
| 882 | Stirge | 55 | 55 / 0 / 0 | KEEP_CURRENT_FOR_NOW | D | No style change recommended in this pass; special/quest mechanics still require their own review.  |
