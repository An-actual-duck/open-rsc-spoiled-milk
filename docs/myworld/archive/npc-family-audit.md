# NPC Family Audit

This is a first-pass grouping of killable NPCs in the current game data for `myworld` planning.

Scope:
- attackable NPCs from `NpcDefs.json` and `NpcDefsCustom.json`
- non-attackable NPCs are intentionally excluded
- quest and special-encounter NPCs are split out on purpose so they do not accidentally inherit hide or material drops
- `human-equivalent` means humans and near-human civilized combatants such as knights, guards, rogues, pirates, druids, monks, and wizards
- humanoid monsters such as giants, dwarves, goblins, ogres, and demons are kept in creature families instead

Notes:
- this is a design bucket list, not a runtime taxonomy yet
- levels are listed to help with loose tier-band mapping
- some families are clearly material candidates
- some families are mainly useful for broad defense-profile assignment rather than hide drops

## Creature Families

### Low Animals And Beasts
Melee: 1, Ranged: 0.1, Magic: 0.1

- `Chicken`: `3`
- `cow`: `8`
- `Bear`: `24`, `26`
- `Unicorn`: `21`
Melee: 0.5, Ranged: 0.1, Magic: 1
- `Black Unicorn`: `31`
Melee: 0.5, Ranged: 0.5, Magic: 1
- `Firebird`: `6`
Melee: 0.1, Ranged: 0.1, Magic: 1
- `Oomlie Bird`: `32`
- `Ugthanki`: `45`

### Rats And Vermin
Melee: 0.1, Ranged: 1, Magic: 0.1

- `Rat`: `2`, `7`, `8`, `13`
- `Dungeon Rat`: `16`
- `Blessed Vermen`: `14`
- `Giant bat`: `32`

### Wolves And Canines
Melee: 1, Ranged: 0.5, Magic: 0.1

- `Desert Wolf`: `31`
- `White wolf sentry`: `31`
- `White wolf`: `41`
- `Karamja Wolf`: `61`
- `Grey wolf`: `64`
- `Pack leader`: `71`
- `Guard Dog`: `46`
- `Hellhound`: `114`
Melee: 1, Ranged: 0.5, Magic: 0.5

### Spiders
Melee: 0.25, Ranged: 1, Magic: 0.1
- `spider`: `2`
- `Giant Spider`: `8`, `31`
- `Dungeon spider`: `22`
- `Blessed Spider`: `35`
- `Deadly Red spider`: `36`
- `Jungle Spider`: `47`
- `Shadow spider`: `53`
Melee: 0.25, Ranged: 1, Magic: 0.5
- `Poison Spider`: `63`
Melee: 0.25, Ranged: 1, Magic: 0.5
- `Ice spider`: `64`
Melee: 0.25, Ranged: 1, Magic: 0.5

### Scorpions
Melee: 0.25, Ranged: 1, Magic: 0.1
- `Scorpion`: `21`
- `Poison Scorpion`: `26`
- `Pit Scorpion`: `35`
- `King Scorpion`: `36`
- `Khazard Scorpion`: `46`

### Goblinoids
Melee: 1, Ranged: 0.5, Magic: 0.1
- `Goblin`: `7`, `13`, `19`
- `Goblin guard`: `48`
- `Hobgoblin`: `32`, `48`
- `Imp`: `5`

### Giants
Melee: 1, Ranged: 0.25, Magic: 0.75
- `Giant`: `37`
Melee: 1, Ranged: 0.25, Magic: 0.25
- `Moss Giant`: `62`
- `Ice Giant`: `68`
- `Fire Giant`: `109`

### Ogres
Melee: 1, Ranged: 0.75, Magic: 0.25
- `Jogre`: `58`
- `Khazard Ogre`: `58`
- `Ogre`: `58`
- `Ogre citizen`: `58`
- `Ogre chieftan`: `78`
- `Ogre guard`: `78`, `96`

### Dwarves
Melee: 1, Ranged: 0.5, Magic: 0.1
- `Dwarf`: `18`
- `Mountain Dwarf`: `28`
- `chaos Dwarf`: `59`
Melee: 1, Ranged: 0.5, Magic: 0.5

### Gnomes
Melee: 0.1, Ranged: 0.5, Magic: 1
- `Gnome child`: `3`
- `Gnome local`: `3`, `9`
- `local gnome`: `3`
- `gnome troop`: `3`
- `Gnome guard`: `23`, `27`, `31`
- `Gnome Baller`: `70`

### Skeletons
Melee: 0.25, Ranged: 1, Magic: 0.5
- `skeleton`: `19`, `21`, `25`, `31`, `54`
- `skeleton mage`: `21`
- `Nazastarool Skeleton`: `83`

### Zombies
Melee: 1, Ranged: 0.5, Magic: 0.5
- `zombie`: `19`, `24`, `32`
- `target practice zombie`: `24`
- `Nazastarool Zombie`: `83`

### Ghosts And Spirits
Melee: 0.5, Ranged: 1, Magic: 0.25
- `Ghost`: `25`, `29`
- `Souless`: `16`, `24`
- `tree spirit`: `95`
Melee: 0.75, Ranged: 0.75, Magic: 1
- `Nazastarool Ghost`: `83`

### Dragons
Melee: 1, Ranged: 0.75, Magic: 1
- `Baby Blue Dragon`: `50`
Melee: 0.5, Ranged: 0.5, Magic: 1
- `Blue Dragon`: `105`
- `Dragon`: `110`
- `Red Dragon`: `140`
- `Black Dragon`: `200`
- `King Black Dragon`: `245`
- `Death Wing`: `80`

### Demons And Other Infernal Creatures
Melee: 0.75, Ranged: 0.5, Magic: 1
- `Lesser Demon`: `79`
- `Greater Demon`: `87`
- `Black Demon`: `156`, `175`
- `Balrog`: `217`
- `Otherworldly being`: `66`

### Elemental Or Magical Creatures
Melee: 0.5, Ranged: 0.5, Magic: 1
- `Animated axe`: `46`
- `Earth warrior`: `52`
- `Ice warrior`: `57`
- `Shadow Warrior`: `64`
- `The Fire warrior of lesarkus`: `63`
- `shapeshifter`: `24`
Melee: 0.25, Ranged: 1, Magic: 0.25
- `Rock of ages`: `150`
Melee: 1, Ranged: 1, Magic: 0.5
- `Greatwood`: `300`
Melee: 1, Ranged: 1, Magic: 1
- `Gaia`: `79`

## Human-Equivalent

These should default to no hide or shell drop, even if they are valid for broad defense-family tuning.

### Civilians And Low-Level Humans
Melee: 1, Ranged: 0.1, Magic: 0.1
- `Hans`: `3`
- `Man`: `9`
- `Citizen`: `10`, `11`, `12`, `15`, `20`
- `civillian`: `18`
- `farmer`: `15`
- `zoo keeper`: `20`
- `Forester`: `21`
- `Platform Fisherman`: `30`
- `Shipyard worker`: `44`

### Criminals And Rogues
Melee: 1, Ranged: 0.1, Magic: 0.1
- `mugger`: `10`
- `Highwayman`: `13`
- `Thief`: `21`
- `Head Thief`: `34`
- `Thug`: `18`
- `Rogue`: `21`
- `Bandit`: `29`

### Soldiers, Guards, And Fighters
Melee: 1, Ranged: 0.25, Magic: 0.1
- `Barbarian`: `16`
Melee: 1, Ranged: 0.1, Magic: 0.1
- `Warrior`: `18`, `27`
- `Soldier`: `28`
- `Guard`: `28`
- `Shantay Pass Guard`: `32`
- `Rowdy Guard`: `50`
- `Draft Mercenary Guard`: `50`
- `Mercenary`: `39`, `50`
- `Mercenary Captain`: `64`
- `Captain Siad`: `48`
- `Bedabin Nomad Guard`: `70`
- `Khazard troop`: `28`
- `Khazard commander`: `41`
- `khazard warlord`: `100`
- `Jailguard`: `34`
- `Jailer`: `51`

### Knights, Paladins, And Nobles
Melee: 1, Ranged: 0.1, Magic: 0.1
- `Knight`: `56`
- `Renegade knight`: `51`
- `Black Knight`: `46`
- `White Knight`: `56`
- `Paladin`: `71`
- `Hero`: `83`

### Religious, Magical, And Cultic Humans
Melee: 0.1, Ranged: 0.1, Magic: 1
- `Darkwizard`: `13`, `25`
- `Wizard`: `16`
- `Battle mage`: `52`
- `Druid`: `29`
- `Chaos Druid`: `19`
- `Chaos Druid warrior`: `44`
- `Monk`: `13`
- `Monk of Zamorak`: `19`, `29`, `47`
- `cult member`: `20`
- `Necromancer`: `34`
- `Witch`: `25`

### Pirate, Tribal, And Desert Human Variants
Melee: 1, Ranged: 0.25, Magic: 0.1
- `Pirate`: `27`, `30`
- `Tribesman`: `39`
- `Jungle Savage`: `87`
- `Warrior`: `18`, `27`

### Human-Adjacent Forced-Labor Or Captive Variants
Melee: 1, Ranged: 0.1, Magic: 0.1
- `Mining Slave`: `16`
- `slave`: `16`
- `Rowdy Slave`: `16`
- `Mourner`: `22`, `25`
- `Iban disciple`: `19`
Melee: 0.1, Ranged: 0.1, Magic: 1

## Quest And Special-Encounter Bucket

These should be treated as opt-in only for material drops. The safest default is that they drop no hide or shell unless intentionally assigned.

- `Delrith`: `30`
- `Count Draynor`: `43`
- `Greldo`: `7`
- `Wormbrain`: `7`
- `Jonny the beard`: `10`
- `Black Heather`: `39`
- `Donny the lad`: `39`
- `Speedy Keith`: `39`
- `Melzar the mad`: `45`
- `Grip`: `46`
- `Chronozon`: `121`
- `Lucien`: `21`
- `Lord Darquarius`: `76`
- `Lord hazeel`: `100`
- `General Khazard`: `100`
- `Bouncer`: `122`
- `Sir Mordred`: `58`
- `Ice queen`: `103`
- `Kolodion`: `12`, `46`, `65`, `68`, `98`
- `Kalrag`: `78`
- `Holthion`: `78`
- `Othainian`: `78`
- `Doomion`: `98`
- `Ungadulu`: `75`
- `Viyeldi`: `80`
- `Gorad`: `78`
- `San Tojalon`: `120`
- `Ranalph Devere`: `130`
- `Irvig Senay`: `125`
- `Nezikchened`: `172`
- `Nazastarool Zombie`: `83`
- `Nazastarool Skeleton`: `83`
- `Nazastarool Ghost`: `83`
- `The Fire warrior of lesarkus`: `63`
- `Black Knight titan`: `146`
- `carnillean guard`: `28`
- `happy peasant`: `25`
- `unhappy peasant`: `25`
- `charlie`: `0`

### Quest-Specific Variants Of Otherwise Normal Families

- `Rat`: `13` variant used as a quest-specific keyed form
- `Ghost`: `25` variant used as a quest-specific keyed form
- `skeleton`: `31` variant used as a quest-specific keyed form
- `zombie`: `32` variant used as a quest-specific keyed form
- `Lesser Demon`: `79` variant used as a quest-specific keyed form

## Manual Review Bucket

These are killable NPCs that do not cleanly fit a broad creature family, a quest/special bucket, or the human-equivalent bucket yet.

Melee: 1, Ranged: 0.1, Magic: 0.1
- `Colonel Radick`: `51`
- `Gnome local`: `3`, `9`
- `Gunthor the Brave`: `37`
- `kalron`: `3`
- `PK Bot`: `57`
- `Salarin the twisted`: `69`

## Suggested Use

- use the creature families first when assigning broad defense ratios
- use the `quest and special-encounter` bucket as a default no-material list
- use `human-equivalent` as a separate broad defense bucket with no hide or shell material
- treat `manual review` as the short list to resolve before automating material-source rules
