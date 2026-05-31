# Spoiled Milk Edition

Welcome to my take on RuneScape Classic.

I am calling it **Spoiled Milk Edition**, mostly because absolutely nothing has
been preserved. I tinkered with just about everything, a lot of which was
probably unnecessary, and the name gives people who hate it an easy joke to
make. That seems fair.

This is still RuneScape Classic at its foundation, but it is not trying to be a
museum piece. Skills have been overhauled, new skills have been added, some
skills have been merged together, some have been retired, and a lot of the old
systems were rebuilt around faster gameplay and more interesting choices.

I also want to be up front about development. I built this entirely with Codex.
I do not have the coding skills to have made this on my own, and I know AI-coded
projects are a touchy topic, so I would rather be direct about it. New art has
also been added, with purchased art from a real artist alongside a small amount
of my own sprite work. The distributable art sources are:

- Pimen: the included added runtime animations, distributed with the artist's
  confirmation that source-available distribution is permitted.
- Original work: custom sprites including the fishing rod equipment sprites.

Artist/source links:

- Pimen: https://pimen.itch.io/

## Navigation

- [What This Version Is Trying To Do](#what-this-version-is-trying-to-do)
- [Core Gameplay Changes](#core-gameplay-changes)
- [Combat](#combat)
  - [Loot Drops](#loot-drops)
- [Equipment](#equipment)
- [Interface And Quality Of Life](#interface-and-quality-of-life)
- [Skills](#skills)
  - [Melee](#melee)
  - [Magic](#magic)
  - [Ranged](#ranged)
  - [Summoning](#summoning)
  - [Enchanting](#enchanting)
  - [Prayer](#prayer)
  - [Mining](#mining)
  - [Smithing](#smithing)
  - [Woodcutting](#woodcutting)
  - [Crafting](#crafting)
  - [Fishing](#fishing)
  - [Harvesting](#harvesting)
  - [Herblaw](#herblaw)
  - [Cooking](#cooking)
  - [Thieving](#thieving)
  - [Agility](#agility)
  - [Firemaking](#firemaking)
- [Quests And Legacy Cleanup](#quests-and-legacy-cleanup)
- [Credits](#credits)
- [Current State](#current-state)
- [Development](#development)

## What This Version Is Trying To Do

The main goal is to make RuneScape Classic feel faster, broader, and less
hostile to actually playing it for long stretches. That does not mean simply
turning up global speed and XP rates and calling it done. Most of the speed-up
is built into the mechanics themselves.

Combat resolves faster because attacks do not miss in the old accuracy-driven
way. Skilling moves faster because better tools can produce more resources and
because resource systems have better side rewards. Production skills use proper
interfaces so players are not fighting the menu system just to make basic gear.

After that, the goal became expansion:

- more equipment tiers
- more useful armor identities
- more reasons to train every combat style
- more interesting jewelry
- more useful prayer
- more meaningful magic
- more visual feedback
- less legacy clutter that only exists because RuneScape Classic happened to
  work that way

This is not meant to be perfectly conservative. It is meant to be a version of
RuneScape Classic where I followed a lot of "what if this was fleshed out?"
threads to their natural, and sometimes unreasonable, conclusion.

## Core Gameplay Changes

These are the changes that affect the whole game rather than one skill.

### Faster Progression Without A Simple Global Multiplier

The game is generally faster, but not only because numbers are higher. The
speed is built into systems:

- better tools can gather multiple resources
- rare skilling side rewards can be tuned by the player
- production skills use menus instead of awkward item-on-object guessing
- combat spends less time on missed attacks
- spells, summons, set bonuses, poison, and jewelry all add more ways to build
  a character

The intention is that progress feels better moment to moment, not just that a
number goes up faster after doing the same old thing.

### Ten-Tier Progression

A lot of the game has been pushed toward a ten-tier ladder. Some systems already
had decent progression, but others were shallow, uneven, or stopped too early.

Current major ladders include:

- ten metals, from tin through rune
- dragon as a special high-end metal line
- ten wood tiers
- ten enchanted cloth tiers
- ten leather/carapace armor tiers
- ten fishing rods
- ten pickaxe and hatchet tiers
- ten shears tiers

This makes gear distribution, shops, drops, skilling, and combat scaling much
easier to reason about.

### PvM First

This version is built around PvM. PvP is currently deactivated. A lot of the new systems,
especially summoning, multi-attacker melee, always-hit combat, and special jewelry, are designed with
monsters in mind first, and team tactics.

## Combat

Combat is one of the biggest changes.

The old RuneScape Classic combat presentation locked the player and enemy into
a shared animation setup. That had to go. In Spoiled Milk Edition, players and
enemies occupy their own tiles properly. You are no longer glued to an enemy,
and rotating the camera no longer drags combatants around just to preserve the
old visual trick.

This matters because melee combat now supports true multi-person attacks.
Multiple players can attack the same target in melee, and multiple enemies can
attack the player at the same time. The game needed combatants to actually stay
where they were for that to make sense.

Other combat changes:

- you can eat during combat
- you can drink potions during combat
- you can retreat without waiting for the old combat lock
- auto retaliate is toggleable
- enemies can use melee, ranged, or magic
- enemies can have separate melee, ranged, and magic defenses
- player combat level is built around the highest main combat style, with hits,
  prayer, and summoning contributing

### Always-Hit Combat

Accuracy as the old hit-or-miss concept is gone. Attacks connect, but damage can
still roll to zero after defenses are considered.

The goal is simple: fewer dead turns. Hitting constant zeros is not interesting,
especially when combat is already slow. Defense still matters, but it now shapes
the damage roll instead of constantly deleting the attack.

### Poison

Poison had to be rebuilt because faster combat would have made the old system
either too weak or too explosive.

Poison now uses poison power. Poison can build upward over time, drain back down
over time, and be refreshed by later procs. Weapons and armor can both
contribute, but they interact through the same poison-power model instead of
stacking separate poison effects on top of each other.

The practical result is that poison should feel like a pressure mechanic. With
luck and sustained attacks, the player can build it up and keep it there.

### Extra Damage Sources

Combat damage is no longer just the weapon swing or spell hit.

New damage sources include:

- armor set procs
- poison ticks
- AoE spells
- recoil effects
- dragon breath effects
- summon attacks
- jewelry effects

Because of that, the interface had to show damage more clearly, which is covered
in the UI section.

### Loot Drops

Monster loot is personal in PvM.

Everyone who meaningfully participates in a fight gets their own drop roll
instead of the party fighting over one shared pile. If several players help kill
the same enemy, each contributor can receive their own bones, fixed drops, and
rolled loot from that enemy.

Damage contribution still matters. The more damage you deal, the better your
chance is at high-value outcomes such as rare-table rolls and rare normal
drops. Lower-damage contributors are still included, but the best odds go to
the players who carried more of the fight.

### Wilderness Encounters

The Wilderness has a broader hostile population pass so it is less empty and
more useful for PvM. New MyWorld placements add hostile pockets across every
ten-level Wilderness band, including expanded Dark Warrior and Greater Demon
areas plus new Hellhound, Zamorak monk, Chaos Druid Warrior, Necromancer,
Hobgoblin, and Shadow Warrior presence.

The intended result is a set of recognizable dangerous regions that support
multi-target combat, rather than enemies scattered randomly across travel paths.

## Equipment

Equipment was rebuilt around the idea that armor should be defensive first.

I did not follow the later RuneScape idea that leather is "ranged armor" and
robes are "magic armor" in the offensive sense. Armor does not need to be the
source of combat power. Weapons, spells, prayers, jewelry, summons, and set
bonuses already give plenty of room for offense.

### Metals

Metal progression now starts earlier and stretches farther.

The normal metal ladder is:

1. Tin
2. Copper
3. Bronze
4. Iron
5. Steel
6. Mithril
7. Titan Steel
8. Adamantite
9. Orichalcum
10. Rune

Dragon exists as a special high-end line rather than just another standard
smithing tier.

Tin and copper were added as the introductory metal tiers, which pushes bronze
up to third place. Titan Steel and Orichalcum were added to fill the upper
middle of the progression so there is a clean climb to rune.

God-aligned steel equipment also has live sources. White Knights provide
Saradomin equipment, Grey Knights provide Guthix equipment, and Dark Warriors
provide direct Zamorak equipment. Ordinary steel gear can also be blessed at
the matching god altar into white, grey, or black knight equipment. Those lines
require matching worship when equipped.

### Woods

Wood also has a proper progression now. More trees are cuttable, more logs
exist, and bows, crossbows, staves, and fishing rods all use the expanded wood
ladder.

The current wood progression includes normal wood, pine, oak, willow, palm,
maple, yew, ebony, magic, and blood.

### Armor Families

Armor is split into three broad defensive families:

- metal armor
- leather/carapace armor
- cloth armor

Each family has a role.

Metal armor is the heavy defensive line. Leather and carapace armor copies the
defensive identity of the creature it came from. Cloth armor is magic-defense
focused and exists largely to support rune preservation for mages.

Armor also has tradeoffs. Major armor slots can reduce the weapon power of one
combat style:

- metal head/chest/legs reduce ranged power
- leather/carapace head/chest/legs reduce magic power
- cloth head/chest/legs reduce melee power

Gloves, boots, and back-slot items do not apply those penalties.

### Leather And Carapace Armor

Leather armor is one of the larger equipment overhauls.

Instead of a generic leather line mostly made from cow hide, many creatures now
have their own hides or materials. The armor made from those materials inherits
the defensive flavor of the creature it came from.

For example, if a creature is naturally better against magic than melee, its
armor can reflect that. The result is that leather armor is not one thing. It is
a whole set of creature-themed defensive options.

Full matching sets also have set bonuses. A few examples:

- Cow armor gives more max hits.
- Unicorn armor gives a prayer bonus.
- Bear armor can intimidate enemies.
- Demon armor can trigger fire damage.
- Dragon armor can trigger breath attacks.
- Wolf and hellhound armor create spirit companions.

The full list is large enough that it belongs in the in-game guide and a
dedicated equipment page later.

### Cloth Armor

Cloth armor starts as wool equipment:

- wool hat
- wool robe top
- wool robe bottom
- wool gloves
- wool boots

These pieces can be enchanted at rune altars. Enchanted cloth gives magic
defense and a chance to preserve the rune matching the altar it was enchanted
at.

Each matching cloth piece gives a 10% chance to preserve that rune. A full
matching set gives 50%.

Staves can also be enchanted at rune altars, and a matching staff gives another
50%. So a full Blood cloth set plus a Blood staff gives a 100% chance to
preserve blood runes.

That is the main reason cloth exists: not raw defense, but making magic smoother
and more sustainable.

### Jewelry

Jewelry has been massively expanded.

Rings, necklaces, and amulets can be enchanted at rune altars, and each altar
theme gives a different family of effects. Gem tier controls the strength of the
effect.

Examples of jewelry effects include:

- offensive power bonuses
- defensive bonuses
- lifesteal
- experience bonuses
- food healing bonuses
- faster poison decay
- recoil
- death item protection
- teleportation
- banking or transport effects
- rare reward interaction
- summon improvements

This is one of the systems that most needs a dedicated guide page, because there
is a lot of it and it is intentionally more interesting than "this ring gives
one stat".

## Interface And Quality Of Life

The UI needed work because the new systems ask the player to understand more
information at once.

### Hitsplats

The old hitsplat setup was replaced with multiple damage indicators. The game
can now show several types of damage at once without them fighting for the same
space.

Current color meanings:

- red: main combat damage
- green: poison damage
- yellow: secondary or special damage
- blue: healing

This matters because combat now has weapon hits, poison, armor procs, AoE,
recoil, healing, and summon-related damage all happening in the same fight.

### Production Interfaces

Production skills now use real interfaces.

Clicking an anvil opens a metal selection window, then a production window for
what to make. Furnaces use a production interface for smelting and mould work.
Crafting, smithing, enchanting, and related skills are much less dependent on
guessing which item needs to be used on which object.

The goal is not to make production automatic. It is to make the choices visible.

### Magic And Prayer Menus

Magic and prayer now use icon-based menus. Magic supports scrolling icons,
spell details, and auto-cast behavior. Prayer uses god-specific books and its
own icon layout.

Magic auto-cast is especially important. Combat spells can be toggled for
auto-cast, and if a combat spell is set, it takes priority over normal weapon
attacks.

### Bank Shortcuts

The custom bank supports quicker stack handling. Holding `Ctrl` while
left-clicking a bank item withdraws its full available quantity, subject to
inventory space. Holding `Ctrl` while left-clicking an inventory item deposits
all copies of that item.

### Minimap And Status Display

The minimap can be kept open and moved to different corners through options.
Health has a clearer on-screen bar. Coordinates can be displayed without
crowding the old UI.

### Gathering Progress

Gathering actions now show a progress bar with the relevant tool icon. This is
mostly there so players can tell when they are about to interrupt their own
action by walking away too early.

## Skills

This section is the quick tour. Each skill probably deserves its own page later,
but this should give the shape of the changes.

### Melee

Melee combines the old Attack, Strength, and Defense skills into one skill.

That change makes melee line up better with Ranged and Magic. Instead of melee
being three separate skills while the other styles are one each, melee is now
the one main skill for melee combat.

Melee itself is still familiar. The bigger differences come from the ability to
have multiple melee attackers at once and expanded weapon families.

### Magic

Magic changed a lot.

Elemental spells are no longer tiered in among themselves. Combat spells
instead tier each element on equal footing. To give the elements distinct
identities, each elemental line has its own debuff:

- Air applies Unsteady.
- Water applies Dampen.
- Earth applies Slow.
- Fire applies Scorch.

The old stand-alone debuff spells were effectively folded into elemental combat
magic so damage and utility happen together.

Other magic changes:

- all elemental spell lines were renamed and reorganized
- god spells are AoE spells
- a second set of stronger god spells were added
- dual-element spells add special procs: Thunder can negate a target's next
  attack, Acid can apply poison, Ice can reflect an incoming attack, and Wood
  can `Splinter` into a random nearby enemy for half damage
- teleport unlock levels were reduced
- teleports have a charge time
- Low Alchemy and High Alchemy were simplified into Alchemy (whoever used low alchemy?)
- Bones to Bananas was replaced by healing spells
- healing spells exist as Lesser Heal and Greater Heal
- magic has proper animations
- the magic menu uses icons
- staves exist for all ten wood tiers

Enchanting was removed from Magic and became its own skill.

### Ranged

Ranged has a more complete equipment ladder now.

Bows, crossbows, throwing knives, throwing darts, and ammunition have all been
expanded into proper tier lines.

Throwing knives are ranged weapons now, not awkward melee/ranged hybrids.
Spears are melee weapons, not throwing weapons. That distinction keeps weapon
categories cleaner.

### Summoning

Summoning is a new skill.

Summons come in three types:

- combat summons
- support summons
- utility summons

Combat summons stay until they die, are dismissed, or are replaced. They attack
what the player attacks and can absorb some damage for the player.

Support summons do not attack and do not take damage. They give passive effects
for a limited time. If the player has life runes, some support summons can
consume life runes to stay active longer.

Utility summons appear to do a task, then leave. A rat can convert items into
certificates. A camel can send items to the bank. Those are the kinds of effects
I want utility summons to explore.

Summoning requires a charge time and can be interrupted by damage. The goal is
that summoning is powerful, but not something you freely spam in the middle of
danger.

Summons use runes and creature-appropriate resources. Life runes are part of
every summon, and the summon usually also needs bones, ashes, or another
resource that fits the creature.

### Enchanting

Enchanting includes rune crafting and a lot more.

Every rune altar is in game and now exists in the overworld. There are no talisman or tiara entry
routes, no alternate altar dimensions, and no mysterious ruin transport system.
The altar is the altar.

Rune crafting is straightforward:

1. Mine Stone from regular rocks.
2. Bring Stone to a rune altar.
3. Use the Stone on the altar.
4. The inventory of Stone becomes runes.

Enchanting also handles:

- robes
- hats
- gloves
- boots
- staves
- rings
- necklaces
- amulets

Robes and staves focus on rune preservation. Jewelry focuses on special effects.
Altar choice determines the theme, and item tier determines the strength.

Life runes and the Life altar have also been added.

### Prayer

Prayer no longer drains over time.

Instead, Prayer uses a reservation system. Turning on a prayer reserves some of
your prayer points. Turning the prayer off refunds those points. So instead of
chugging prayer potions to keep a timer alive, the choice is about how much of
your total prayer budget you want to commit to what buffs.

Full damage immunity prayers are gone. They have been replaced with stackable
resistance and bonus effects.

Prayer is split into three god books:

- Saradomin
- Zamorak
- Guthix

You switch books by worshipping at that god's altar. Each book has its own
offense, defense, and XP multiplier identity.

God equipment also matters more. God staves and god capes require worshipping
the matching god. Blessed staves can be made through prayer-aligned altar
interaction. Maces and crossbows also have prayer-supporting roles for melee and
ranged.

Devotion is the long-term progression layer for god-aligned Prayer gear. Each
god tracks devotion separately, up to `1000`. Offerings build devotion with the
god you are worshipping, and devotion is then used to bless supported combat
gear at that god's altar.

Blessing equipment costs devotion based on the resource cost of the base item.
For example, a plate body takes more devotion to bless than a one-bar weapon.
Blessing an item grants Prayer XP, but does not directly grant more devotion.

Blessed gear is strongest when it matches the god you currently worship.
Matching blessed gear gains Prayer bonuses immediately, and its combat stats
begin scaling upward once you reach `250` devotion with that god. At high
devotion, blessed combat gear becomes a serious long-term equipment path rather
than just a cosmetic god version of normal gear.

### Mining

Mining uses equipable pickaxes.

Pickaxes have more tiers as a result of the new metals, and influence gathering yield.
Mining can produce multiple resources from one successful gather depending on
level and tool quality.

Mining also has a focus selection when a pickaxe is equipped:

- Just the ore
- A few gems
- Plenty of gems
- Lots of gems

This lets the player decide whether they want maximum normal resource focus or
better rare gem chances.

Stone is now important because it feeds rune crafting. Regular plain rocks can
produce Stone, which is used at rune altars to make runes.

### Smithing

Smithing was expanded around the new metal ladder.

New metals:

- Tin
- Copper
- Titan Steel
- Orichalcum

Tin and copper are the new starter metals. Titan Steel sits above mithril.
Orichalcum sits above adamantite. This gives the normal metal ladder a much
cleaner climb from early game to rune.

Smelting has also changed. Furnaces open a production interface, and recipes
have been adjusted. Iron can produce Pig Iron when smelting fails, and Pig Iron
can be used in steel production in place of iron ore.

Smithing now covers the expanded weapon, armor, tool, and ammo-related metal
items that fit the skill.

### Woodcutting

Woodcutting has more trees, more logs, and expanded tool progression.

New or expanded wood tiers include pine, palm, ebony, and blood wood. These
support the broader equipment system, including bows, crossbows, staves, and
fishing rods.

Woodcutting also has its own rare loot tables with seed rewards. These seeds
can grow temporary resource trees. Depending on the seed, the tree might
provide logs, ores, gems, XP, or gold.

Like mining, Woodcutting has a focus selection when a hatchet is equipped:

- No seeds for me
- A few seeds
- More seeds
- Even more seeds!

### Crafting

Crafting absorbed Fletching.

That puts bows, arrows, ranged crafting, leather armor, cloth armor, jewelry,
and related production under one broader skill. It makes Crafting develop much
faster and gives it a much stronger identity.

Crafting now includes:

- leather and carapace armor
- wool cloth armor
- bow and crossbow production
- arrow shafts and headless arrows
- jewelry
- pottery and glass-style production
- mould-based ranged components

New moulds exist for things like arrow heads, bolts, dart tips, and throwing
knives. Darts still need feathers after the tips are made, and arrows still use
the normal shaft/head/feather structure.

### Fishing

Fishing was rebuilt around one tool family: fishing rods.

Old fishing tools are mostly retired from normal progression. Fishing rods are
craftable, equipable, and follow the wood ladder.

Fishing spots are now more location-based. Instead of one spot in an area being
"the tuna spot" and another being "the lobster spot", a location has a pool of
fish. Your rod tier and Fishing level determine what you can catch and how
likely you are to catch the better fish in that pool.

High-tier rods also stop catching the lowest-tier fish, so players can influence
the results instead of always dragging the entire low-end table with them.

Fishing has a rare side-reward focus too:

- Just the fish
- A little loot
- Plenty of loot
- Lots of loot

Side rewards include things like oysters, seaweed, caskets, and leather or
carapace gloves and boots.

### Harvesting

Harvesting uses shears as its main tool.

Shears are tiered, equipable, and smithable. The equipped shears determine what
the player can harvest, similar to how fishing rods determine what fish can be
caught.

Harvesting has rare seed rewards like Woodcutting, but the reward categories are
different. Harvesting seeds focus on:

- food plants
- herb plants
- potion-ingredient plants
- knowledge plants
- money plants

The idea is that Woodcutting and Harvesting both have special seed rewards, but
they do not step on each other completely.

### Herblaw

Herblaw needed a lot of cleanup because many old potion effects no longer made
sense.

Prayer does not drain anymore, combat works differently, and several old stat
relationships changed. That means some old potions were useless, too strong, or
just aimed at systems that do not exist in the same form.

New potions have been added, especially around XP boosts and the current combat
model. This is still a skill I expect to tune more over time.

### Cooking

Cooking is mostly unchanged for now.

It is already a well-rounded skill, but to stay on theme I will probably mess
with it eventually.

### Thieving

Thieving is also mostly unchanged.

It is another well-rounded skill, but I do not feel right leaving something
untouched. For now it remains as is.

### Agility

Agility now has reward pouches.

Completing obstacle courses can award pouches containing runes, logs, ores, and
other resources. Pouches are stackable, so they do not clog inventory during
training.

Higher-tier courses give better pouches.

### Firemaking

Firemaking is retired as a normal progression skill.

Everything Firemaking used to gate can basically just be done now. I did not
think the skill had enough going on to justify expanding it just for the sake of
keeping it.

The Firemaking cape can still exist as a special item, but it should not be
handed out through the old normal skillcape route. It needs a more interesting
source later.

## Quests And Legacy Cleanup

A lot of old content had to be cleaned up because the surrounding systems
changed.

Examples:

- old fletching rewards now map into Crafting
- old attack/strength/defense rewards map into Melee
- obsolete runecrafting talisman and tiara assumptions were removed
- old magic equipment and robe assumptions were replaced by wool and altar
  enchantment systems
- shops, drops, guides, and production recipes were updated to point at the new
  item tiers

Quest rewards and dialogue still need ongoing review, because RuneScape Classic
has a lot of tiny one-off assumptions hiding in old quest code. However, I made
all quests skippable with an option "I've already done this quest" in each quest
giver's dialogue. Most people here have probably done the quests before, and
whether you revisit them or not should be up to you. The same rewards are
awarded whether you skip the quest or do it.

## Credits

New visual assets use the following source breakdown:

- **Pimen**: the included added runtime animations, distributed with the
  artist's confirmation that source-available distribution is permitted.
- **Original work by the project author**: sprite additions including the
  fishing rod equipment sprites.

CraftPix-derived icons and Phoenix/Kraken animations were removed from the
source tree and player package because the downloadable client would make image
files extractable.

Sources:

- Pimen: https://pimen.itch.io/

## Current State

The broad systems are in place, but some areas still need field testing,
balance tuning, and final presentation polish. Summoning, rare skilling
rewards, encounter density, and legacy dialogue are the largest areas where I
expect details to keep changing as I test more.

That said, this is the shape of the project: a very changed RuneScape Classic
fork, held together by a suspicious amount of ambition, Codex, and a complete
lack of respect for preservation.

## Limited Alpha Downloads

The first player-facing release is designed for invited testers connecting to
a hosted Spoiled Milk world. It will be published as a GitHub prerelease with:

- a Windows x64 zip containing a bundled Java runtime and one-click launcher
- a generic Java 17+ zip for macOS, Linux, and Windows
- SHA-256 checksums for both downloads

Release downloads and player launch instructions are tracked in
[`docs/releases/`](docs/releases/). The alpha package is client-only; hosting
your own world remains a development/operator workflow.

## Development

This repository is MyWorld-first and is built on the inherited OpenRSC/Cabbage
engine foundation. Current development documentation is indexed in
[`docs/myworld/README.md`](docs/myworld/README.md).

### Quick Start

```bash
./scripts/build-client.sh
./scripts/run-client.sh
./scripts/build-server.sh
./scripts/run-server.sh
```

Useful validation and local database commands:

```bash
./scripts/check.sh
./scripts/test.sh
./scripts/init-sqlite.sh
```

The SQLite reset command replaces local development state from the clean
MyWorld seed at `server/inc/sqlite/myworld_seed.db`.

### Main Paths

- `Client_Base/` and `PC_Client/`: client sources.
- `server/`: server code, plugins, config, definitions, and database files.
- `scripts/`: preferred build, run, reset, and test wrappers.
- `tools/generators/`: authored item/NPC override sources and generators.
- `tests/myworld/`: MyWorld validation suite.
- `dev/myworld/assets/`: active source tree for new visual assets.
- `docs/releases/`: player download and limited-release operator checklists.
- `docs/myworld/`: current design, testing, and work-item documentation.
- `docs/inherited-openrsc/`: archived inherited documentation and reference
  material.

Java 8 remains the normal client/server build target. A Java 17+ server
launcher is also available through `./scripts/run-server-zgc.sh`.
