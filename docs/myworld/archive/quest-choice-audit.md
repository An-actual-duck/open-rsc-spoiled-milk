# MyWorld Quest Choice Audit

This note audits quest choices that change rewards, reward skills, or
post-quest utility items.

The goal is not to preserve every old branch exactly. The goal is to make
shortcut completion safe and predictable in `MyWorld`:

- players should not miss important rewards
- players should not be flooded with dead-end junk
- legacy reward skills should be remapped onto the live `MyWorld` skill model
- branch-specific state should only be preserved when it still matters

## MyWorld default policy

Unless a quest is called out as a special case below, the `MyWorld` shortcut
default should be:

1. grant all meaningful reward branches
2. remap retired skill rewards to the live `MyWorld` skills
3. bank overflow rather than dropping it into a full inventory
4. skip one-time logistics items that have no post-quest use in the current
   codebase

### Reward-skill remaps

These are the baseline remaps the shortcut system should apply when an old
quest reward targets a retired or merged skill:

- `Attack` XP -> `Melee`
- `Defense` XP -> `Melee`
- `Strength` XP -> `Melee`
- `Fletching` XP -> `Crafting`

Rationale:

- `MyWorld` folds old `Attack`, `Defense`, and `Strength` progression into
  `Melee` in the live combat model. See
  [`combat-tasklist.md`](/home/justin/Core-Framework/docs/myworld/combat-tasklist.md).
- `MyWorld` folds active `Fletching` progression into `Crafting` in the live
  production model. See
  [`cleanup-and-polish-tasklist.md`](/home/justin/Core-Framework/docs/myworld/cleanup-and-polish-tasklist.md#L38)
  and
  [`SkillGuideInterface.java`](/home/justin/Core-Framework/Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java#L589).

## Choice Audit

### Observatory

Source:

- reward dispatch:
  [`Observatory.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java#L630)

How the choice works now:

- the telescope picks one random constellation
- every completion also gives the base quest reward from `handleReward`
- every constellation also gives an `Uncut sapphire`
- some constellations grant an item
- some constellations grant extra skill XP

Current branch outcomes:

- `Virgo`: `Defense` XP
- `Libra`: `3 law runes`
- `Gemini`: `Black 2-handed sword`
- `Pisces`: `3 tuna`
- `Taurus`: `1 full super strength potion`
- `Aquarius`: `25 water runes`
- `Scorpio`: `1 weapon poison`
- `Aries`: `Attack` XP
- `Sagittarius`: `Maple longbow`
- `Leo`: `Hits` XP
- `Capricorn`: `Strength` XP
- `Cancer`: `Emerald amulet`
- all outcomes also give `1 uncut sapphire`

MyWorld recommendation:

- grant every constellation reward, not a random single one
- remap `Attack`, `Defense`, and `Strength` XP to `Melee`
- keep `Hits` XP as-is
- keep the item rewards, but bank overflow

Why this is safe:

- all item rewards are still real items in the current codebase
- `Emerald amulet` is still a valid base jewelry item in the current item model
- none of these outcomes set conflicting branch state

### Tourist Trap

Source:

- reward menus:
  [`TouristTrap.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java#L300)

How the choice works now:

- the player chooses two of four skill rewards

Current branch outcomes:

- `Fletching`
- `Agility`
- `Smithing`
- `Thieving`

Additional fixed reward with ongoing use:

- `Wrought iron key`
  from
  [`TouristTrap.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java#L191)
  and still checked later in the same quest file at
  [`TouristTrap.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java#L2815)

MyWorld recommendation:

- grant all four skill rewards
- remap `Fletching` XP to `Crafting`
- keep the `Wrought iron key` because it still has post-quest utility in the
  current code

### Hazeel Cult

Source:

- branch state and reward logic:
  [`HazeelCult.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HazeelCult.java#L58)

How the choice works now:

- the player takes a `good_side` or `evil_side` branch

Current branch outcomes:

- both branches ultimately give the same quest XP/QP and `2000 coins`
- the evil branch additionally hands out:
  - `Poison`
  - `Mark of Hazeel`
- the good branch exposes temporary investigation items and the
  `Carnillean armour`, but those are part of the route, not a lasting reward

Post-quest utility audit:

- `Mark of Hazeel`: no downstream code references found outside the quest file
- `Poison`: generic consumable, not a unique quest utility item

MyWorld recommendation:

- grant the shared quest reward package once: XP/QP plus `2000 coins`
- do not auto-grant `Poison`
- do not auto-grant `Mark of Hazeel`
- record the quest as complete without preserving a permanent branch side
  unless later content proves it still matters

Why this is not an “all rewards” case:

- the side choice mostly changes narrative route, not long-term progression
- the evil-only item is not carrying meaningful post-quest utility in the
  current codebase

### Temple of Ikov

Source:

- Lucien completion:
  [`TempleOfIkov.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java#L431)
- Guardian/Lucien-fight completion:
  [`TempleOfIkov.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java#L642)

How the choice works now:

- one route gives the `Staff of Armadyl` to Lucien and completes with stage
  `-2`
- the other route opposes Lucien and completes with the normal complete stage

Branch-only items encountered during the quest:

- `Pendant of Lucien`
- `Pendant of Armadyl`

Post-quest utility audit:

- `Pendant of Armadyl` is only used to attack Lucien in the quest flow
- `Pendant of Lucien` is only used to pass the quest chamber checks
- no separate post-quest progression value was found for either pendant

MyWorld recommendation:

- treat both endings as awarding the same lasting progression
- grant the quest completion reward once
- do not auto-grant either pendant as a lasting shortcut reward
- prefer the anti-Lucien completion state if one canonical post-quest state is
  needed, because the Lucien route uses the special `-2` stage

Why this is not an “all rewards” case:

- the branch difference is mostly narrative state
- the branch items are quest-route tools, not meaningful post-quest rewards

### Shield of Arrav

Source:

- gang route state:
  [`ShieldOfArrav.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ShieldOfArrav.java#L81)

How the choice works now:

- the player joins either the `Black Arm` or `Phoenix` route
- the base quest completion reward is shared
- the major long-tail difference is gang affiliation and its follow-on items

Branch-specific long-tail items and state:

- `Black Arm` route can later grant `Master thief armband`
  in
  [`ShieldOfArrav.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ShieldOfArrav.java#L255)
  and
  [`ManPhoenix.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/ManPhoenix.java#L47)
- `Master thief armband` is used in `Hero's Quest`
  at
  [`DoorAction.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/defaults/DoorAction.java#L220)
  and
  [`HerosQuest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HerosQuest.java#L344)
- `Phoenix` route sets the alternate gang affiliation and key access state

MyWorld recommendation:

- do not simply set both gang affiliations at once
- after selecting `I've already done this quest`, ask which gang the player
  joined
- record the matching completed route for `Shield of Arrav`
- use that same answer to drive follow-on shortcut handling such as
  `Hero's Quest`

Why this is a special case:

- the reward difference is really a world-state and faction-state difference
- “grant all rewards” is not enough here because dual gang membership is not a
  clean state in the current quest code

### Family Crest

Source:

- base gauntlet reward:
  [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L149)
- upgrade branches:
  [`Chef.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/catherby/Chef.java#L32),
  [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L345),
  [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L501)

How the choice works now:

- quest completion grants `Steel gauntlets`
- later the player can convert them into one specialization at a time:
  - `Cooking`
  - `Goldsmithing`
  - `Chaos`

Post-quest utility audit:

- `Cooking` gauntlets: cooking benefit check in
  [`Formulae.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/util/rsc/Formulae.java#L179)
- `Goldsmithing` gauntlets: gold-smelting benefit in
  [`Smelting.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java#L310)
- `Chaos` gauntlets: bolt-spell benefit in
  [`SpellHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java#L1819)

MyWorld recommendation:

- shortcut completion should continue to grant only `Steel gauntlets`
- do not auto-grant all three upgrade variants at completion
- instead preserve the ability to choose and swap later

Why this is a special case:

- the reward state is a single active `famcrest_gauntlets` cache value
- granting all variants as items would fight the one-active-enchantment model

### Legends Quest

Source:

- reward selection:
  [`LegendsQuestSirRadimusErkle.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestSirRadimusErkle.java#L124)

How the choice works now:

- the player chooses `4` training rewards from `12` skills

Current selectable skills:

- `Attack`
- `Defense`
- `Strength`
- `Hits`
- `Prayer`
- `Magic`
- `Woodcutting`
- `Crafting`
- `Smithing`
- `Herblaw`
- `Agility`
- `Thieving`

Other notable quest items:

- `Gilded totem pole` can be retrieved again from Gujuo, but is mainly a
  flavor/display item:
  [`LegendsQuestGujuo.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestGujuo.java#L217)

MyWorld recommendation:

- grant all `12` training rewards
- remap `Attack`, `Defense`, and `Strength` to `Melee`
- keep the rest as-is
- do not auto-grant `Gilded totem pole` as part of shortcut completion unless
  you explicitly want quest memorabilia preserved

Why this is safe:

- these are pure training choices, not conflicting world-state branches
- the only deprecated part is the old combat-skill split

## Special reward items that need follow-through

These are not all branch rewards, but they are the most relevant “don’t lose
this on shortcut” quest items adjacent to the choice audit.

### Keep

- `Wrought iron key`
  from `Tourist Trap`
- `Steel gauntlets`
  from `Family Crest`
- `King Lathas amulet`
  from `Biohazard`, if you want strict original reward parity even though no
  downstream code use was found

### Usually skip

- `Mark of Hazeel`
  unless later content proves it still matters
- `Pendant of Lucien`
- `Pendant of Armadyl`
- `Gilded totem pole`
  unless you want pure memorabilia preserved

## Implementation order recommendation

1. Apply the “grant all + remap legacy skills” policy to:
   - `Observatory`
   - `Tourist Trap`
   - `Legends Quest`
2. Apply “shared reward only, suppress branch junk” to:
   - `Hazeel Cult`
   - `Temple of Ikov`
3. Treat faction/state quests as special work:
   - `Shield of Arrav`
   - `Family Crest`

## Change Log

- `2026-04-04` Added a full audit of reward-driving quest choices and the
  proposed `MyWorld` default behavior for each branch family.
- `2026-04-04` Expanded the note into a quest-wide shortcut matrix and a
  clarification ledger for the remaining design calls.

## Full Quest Shortcut Matrix

See [`quest-audit.md`](/home/justin/Core-Framework/docs/myworld/quest-audit.md)
for the starter-NPC / initiation references. This section is the
shortcut-design pass: what each quest should do in `MyWorld`, what should be
preserved, and whether a clarification is still needed before implementation.

### Free quests

- `Black Knights' Fortress`: straightforward shortcut; grant normal quest
  reward only.
- `Cook's Assistant`: straightforward shortcut; grant normal quest reward only.
- `Demon Slayer`: straightforward shortcut; grant normal quest reward only.
- `Doric's Quest`: straightforward shortcut; grant normal quest reward only.
- `Dragon Slayer`: straightforward shortcut; grant normal quest reward only.
- `Ernest the Chicken`: straightforward shortcut; grant normal quest reward
  only.
- `Goblin Diplomacy`: straightforward shortcut; grant normal quest reward only.
- `Imp Catcher`: preserve `Amulet of accuracy`; already called out in
  [`quest-audit.md`](/home/justin/Core-Framework/docs/myworld/quest-audit.md#category-b-final-reward-includes-scripted-item-payout-before-or-after-sendquestcomplete).
- `The Knight's Sword`: straightforward shortcut; grant normal quest reward
  only.
- `Pirate's Treasure`: straightforward shortcut; grant normal quest reward
  only.
- `Prince Ali Rescue`: straightforward shortcut; grant normal quest reward
  only.
- `Romeo & Juliet`: straightforward shortcut; grant normal quest reward only.
- `Sheep Shearer`: straightforward shortcut; grant normal quest reward only.
- `Shield of Arrav`: clarification needed; gang state and `Master thief
  armband` affect later progression.
- `The Restless Ghost`: straightforward shortcut; grant normal quest reward
  only.
- `Vampire Slayer`: straightforward shortcut; grant normal quest reward only.
- `Witch's Potion`: straightforward shortcut; grant normal quest reward only.

### Members quests

- `Biohazard`: preserve `King Lathas amulet`; clarification needed on whether
  the shortcut should also stage or bypass the `Underground Pass` lead-in.
- `Clock Tower`: straightforward shortcut; grant normal quest reward only.
- `Druidic Ritual`: straightforward shortcut; grant normal quest reward only.
- `Dwarf Cannon`: preserve cannon utility package. Minimum safe set is
  `Cannon ammo mould` plus the quest-complete cannon ownership state; likely
  also `Instruction manual`. Clarification needed on whether shortcut players
  should receive a full recoverable cannon set immediately or rely on the
  normal recovery dialogue.
- `Family Crest`: preserve `Steel gauntlets` and `famcrest_gauntlets = steel`;
  clarification still needed only if you want shortcut players to start with
  more than the base gauntlets.
- `Fight Arena`: straightforward shortcut; grant normal quest reward only.
- `Fishing Contest`: straightforward shortcut; grant normal quest reward only.
- `Gertrude's Cat`: preserve `Kitten`; skip `Chocolate cake` and `Stew` unless
  strict parity matters more than inventory cleanliness.
- `The Hazeel Cult`: clarification needed; current recommendation is shared
  reward only, with no `Mark of Hazeel` or evil-branch junk by default.
- `Hero's Quest`: clarification needed together with `Shield of Arrav`; the
  current quest still expects gang-route state and `Master thief armband`
  access.
  Resolved: `Hero's Quest` should reuse the existing `Shield of Arrav` gang
  state when present, or ask the same gang question and backfill the shield
  completion when shortcutting directly.
- `Jungle Potion`: straightforward shortcut; grant normal quest reward only.
- `Lost City`: preserve `Dramen staff`; already implemented.
- `Merlin's Crystal`: straightforward shortcut; grant normal quest reward only.
- `Monk's Friend`: preserve `8 law runes`; no further clarification needed.
- `Murder Mystery`: preserve `2000 coins`; skip investigation items and clue
  junk.
- `Observatory`: clarification needed only for generosity level. Current
  recommendation is to grant every constellation reward, remapping combat XP
  to `Melee`, and bank overflow.
- `Plague City`: preserve `Magic scroll`; already implemented.
- `Scorpion Catcher`: straightforward shortcut; grant normal quest reward only.
- `Sea Slug`: preserve `Quest oyster pearls`; no further clarification needed.
- `Sheep Herder`: preserve `3100 coins`; skip `Poisoned animal feed`.
- `Temple of Ikov`: clarification needed; current recommendation is one
  canonical post-quest state, shared reward only, and no pendant carry-over.
- `The Holy Grail`: straightforward shortcut; grant normal quest reward only.
- `Tree Gnome Village`: preserve `Emerald amulet`; skip `Orb of protection`
  route items unless you explicitly want memorabilia retained.
- `Tribal Totem`: straightforward shortcut; grant normal quest reward only.
- `Waterfall Quest`: preserve `Glarial's amulet`; already implemented.
- `Witch's House`: straightforward shortcut; grant normal quest reward only.
- `Digsite`: preserve the `2 gold bars` reward; skip dig-route logistics items.
- `Grand Tree`: likely straightforward shortcut. `Tree gnome translation` is
  retrievable but not a meaningful progression gate; no clarification needed
  unless you want strict memorabilia parity.
- `Legends' Quest`: clarification needed only for generosity level. Current
  recommendation is to grant all `12` training rewards with combat remapped to
  `Melee`, while skipping pure route items like `Gilded totem pole`.
- `Shilo Village`: straightforward shortcut; grant normal quest reward only.
- `Tourist Trap`: clarification needed only for generosity level. Current
  recommendation is to grant all four skill rewards, remap `Fletching` to
  `Crafting`, and preserve `Wrought iron key`.
- `Underground Pass`: preserve `Staff of Iban`; clarification needed on
  whether the shortcut should hand out the finished staff directly or the
  broken/recoverable path state instead.
- `Watchtower`: preserve `Spell scroll` and `5000 coins`; already implemented.

### MyWorld quest

- `Peeling the Onion`: preserve the post-quest unlock cache state; normal
  shortcut can stay narrow because the important long-tail rewards are cache
  and dialogue unlocks rather than inventory payload.

## Clarifications To Review

These are the concrete design calls reviewed so far for the remaining shortcut
rollout.

### 1. Shield of Arrav / Hero's Quest state

Resolved:

- after selecting `I've already done this quest`, the starter should ask which
  gang the player joined
- that answer should drive the post-quest gang state used by follow-on content
  such as `Hero's Quest`

Implementation note:

- this should remain a mutually exclusive branch choice, not a “grant both”
  state

### 2. Biohazard / Underground Pass chain

Resolved:

- quest-line shortcuts should complete the whole line from the first relevant
  quest interaction

Implementation note:

- `Biohazard` shortcuting should also advance the player through the
  `Underground Pass` lead-in state instead of leaving the line half-open

### 3. Dwarf Cannon package

Resolved:

- shortcut completion should give the player the cannon immediately

### 4. Observatory generosity

Resolved:

- grant all constellation outcomes

### 5. Tourist Trap generosity

Resolved:

- grant all four training rewards

### 6. Legends' Quest generosity

Resolved:

- grant all `12` training rewards

### 7. Hazeel Cult branch fidelity

Resolved:

The real branch difference here is not the final payout. Both sides end in the
same core reward package from `handleReward`: quest completion, XP, quest
points, and `2000` coins.

What actually differs:

- `good_side`
  - keeps the player aligned with Ceril
  - drives later dialogue that exposes Butler Jones
  - culminates in handing Ceril the proof from the cupboard and completing via
    the “good” narrative route
- `evil_side`
  - gives the player `Poison` earlier in the quest
  - uses different cache/state checks throughout the quest dialogue
  - unlocks the crate/bookcase/chest path to obtain the `Script of Hazeel`
  - can grant `Mark of Hazeel`

What matters post-quest in the current code:

- there is no obvious later-quest dependency on `good_side` vs `evil_side`
- `Mark of Hazeel` does not appear to be used by later content outside this
  quest
- `Script of Hazeel` and the route items are mainly quest-route artifacts, not
  enduring progression gates

Recommended default:

- do not preserve permanent branch identity
- mark the quest complete with the shared lasting reward package only
- skip `Mark of Hazeel`, `Poison`, and route-specific junk unless you want
  strict narrative memorabilia

- do not preserve branch identity for the shortcut path
- grant only the shared lasting reward package
- skip `Mark of Hazeel`, `Poison`, and route junk

### 8. Temple of Ikov canonical state

Resolved:

This quest has two different completed states:

- `-1`: anti-Lucien route
  - the player works with the Guardians of Armadyl
  - the player gets `Pendant of Armadyl`
  - the player defeats Lucien in the final confrontation
- `-2`: pro-Lucien route
  - the player gives Lucien the `Staff of Armadyl`
  - Lucien removes himself after the handoff
  - the guardians treat the player as Lucien's agent after completion

What matters post-quest in the current code:

- both `-1` and `-2` are considered completed states for most quest checks
- the main difference is dialogue flavor and who the player sided with
- `Pendant of Lucien` and `Pendant of Armadyl` are route tools, not strong
  long-tail rewards in the current codebase
- `-1` is the cleaner general-purpose post-quest state because it does not
  leave the player aligned with Lucien by default

Approved default:

- after selecting `I've already done this quest`, ask which ending the player
  took
- if they sided with Lucien, complete the shortcut in the `-2` state
- if they sided with the guardians, complete the shortcut in the `-1` state
- do not auto-grant `Pendant of Lucien` or `Pendant of Armadyl`
  leave the player flagged as Lucien's ally

Recommended default:

- use the anti-Lucien `-1` state for shortcut completion
- grant the lasting quest reward once
- do not auto-grant either pendant as a permanent shortcut reward

The question to decide:

- does `MyWorld` want the morally/structurally cleaner anti-Lucien default, or
  should the shortcut ask which ending the player chose?

### 9. Gertrude's Cat consumables

Resolved:

- preserve `Kitten`
- skip `Chocolate cake` and `Stew`

### 10. Tree Gnome Village memorabilia

Resolved:

- preserve `Emerald amulet`
- skip `Orb of protection` / `Orbs of protection`

### 11. Underground Pass staff state

Resolved:

- shortcut completion should give a finished `Staff of Iban`
## Recommended Next Rollout Order

After the clarification pass, the next implementation order should be:

1. `Gertrude's Cat`, `Sea Slug`, `Dwarf Cannon`
2. `Imp Catcher`, `Biohazard`, `Monk's Friend`, `Murder Mystery`
3. `Observatory`, `Tourist Trap`, `Legends' Quest`
4. `Hazeel Cult`, `Temple of Ikov`
5. `Shield of Arrav`, `Hero's Quest`, `Underground Pass`
