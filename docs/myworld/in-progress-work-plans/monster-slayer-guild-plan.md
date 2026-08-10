# Monster Slayer Guild Plan

Status: **definition foundation and durable player-state activation implemented
and verified; task, reward, shop, dialogue, placement, inventory-capacity, UI,
and protocol activation remain pending**
Owner: An-actual-duck
Audit baseline: published `main` `4be5b9fc5` on 2026-07-16
Audit integration: merged into `main` as `8ec90a4d6`
Foundation design revision baseline: published `main` `368ff655e` on 2026-07-16

Related active plans:

- `docs/myworld/in-progress-work-plans/how-to-acquire-dragon-armor.md`
- `docs/myworld/in-progress-work-plans/dragon-gear-crafting-plan.md`

Source policy:

- This is the only official Monster Slayer implementation plan.
- `docs/myworld/rough-drafts/slayer-guild-rough-draft-plan.md` is superseded
  historical context, not an implementation source.
- Do not restore the rough draft's monster-drop/certificate turn-ins.
- Do not restore its dragon plate-leg, dragon-skirt, or other finished dragon
  armor rewards. Finished dragon equipment remains owned by the dragon gear
  crafting plan.

## Product Contract

Monster Slayer is an independently authored distributed guild-standing system.
Combat Odyssey supplies inspiration, legacy flavor, and migration evidence;
its tiers, task order, rewards, and progression are not a Monster Slayer
blueprint and must not be translated one-to-one. Monster Slayer is not a
visible skill and does not award Slayer XP. Players advance through seven named
ranks through one continuous mandatory guild quest, complete deterministic
assignment chains at six contacts, and then use those contacts for repeatable
tasks and rank-gated challenge shops. The opening assignment is deliberately
not a monster kill: it is the joke beer errand that starts the quest and awards
the first rank.

The intended tone and progression remain:

- An unstamped player brings a beer from the Rising Sun Barmaid to a dedicated
  Monster Slayer contact in the pub and receives a deliberately silly
  `Fledgling` hand stamp.
- Six fixed task chains advance the player through `Initiate`, `Veteran`,
  `Elite`, `Champion`, `Hero`, and `Legend`.
- Completed contacts offer repeatable random kill tasks for that contact's
  typed challenge currency: Fledgling, Initiate, Veteran, Elite, Champion, or
  Hero points.
- Biggum Flodrot's personality and the idea of a legendary capstone survive,
  but the 101-task Odyssey is no longer a required 40,906-kill reward wall.
- Challenge shops primarily turn hunting into useful combat preparation items
  that can otherwise come from skilling: weapons, armor, potions, food,
  ammunition, and similar supplies. Optional unique rewards can provide
  longer-term goals without making every shop item unique.

Core rules:

- One active Monster Slayer task across all contacts.
- The beer is a one-time introduction, not a repeatable material turn-in.
- Mandatory chains are fixed and cannot be cancelled or rerolled.
- Every mandatory or repeatable task awards the currency assigned to its
  contact/challenge level without advancing rank merely by paying currency.
  Its authored `pointReward` reflects the task's expected combat difficulty,
  danger, and effort; harder task families in the same tier earn more of that
  tier's currency.
- Challenge points are awarded on task completion, not per kill. They are six
  non-interchangeable player-saved balances, not inventory items or one scalar
  balance. This bounds cache writes, avoids partial-task farming, and makes the
  economy auditable.
- Shop items use a two-component cost vector: their native tier currency plus
  the immediately preceding tier currency. The Fledgling shop is the sole
  exception and costs only Fledgling currency. No item may consume an older
  lower tier, a higher tier, or an interchangeable total.
- Each of the six challenge shops contains one permanent inventory-capacity
  upgrade. Buying all six grows the player's inventory from 30 slots to a
  8-by-5, 40-slot inventory; the upgrade is purchased rather than granted
  automatically when the shop unlocks.
- Higher contacts refuse assignment/shop access until the required rank and the
  host guild's normal access requirements are satisfied.
- Existing Combat Odyssey state is migrated once without deleting its keys, but
  it never grants Monster Slayer currency; every account begins its new typed
  balances at zero.

Non-goals:

- No new skill, XP curve, skill-guide entry, material proof, certificates, or
  trade-in ledger.
- No dragon plate mail legs, dragon scale mail legs, dragon skirts, or other
  finished dragon equipment in tasks, shops, migration grants, or capstones.
- No broad rewrite of NPC death, loot, XP, quest kill triggers, or party combat
  in the foundation branch.
- No automatic conversion of the old JSON's item rewards into shop stock.
- No one-to-one mapping from Odyssey tiers/tasks/rewards to Monster Slayer
  ranks, chains, balances, categories, or stock.

## Design Decision Ledger

This ledger distinguishes settled refinements from questions that remain open.
The implemented foundation data remains an informed starting point wherever a
later decision has not replaced it.

### Confirmed: One Continuous Mandatory Quest And Beer Opening

- The entire mandatory path from recruitment through the top `Legend` rank is
  one long Monster Slayer Guild quest, not six unrelated miniquests. Contact
  changes and rank awards are stages within that quest. Repeatable assignments
  and challenge-shop purchases are not additional mandatory quest stages.
- The initial contact opens with the Rising Sun exchange:

  - Contact: `'ello there.`
  - Player: `I hear you give Monster Slayer tasks?`
  - Contact: `I sure do! Show me your stamp first.`
  - Player: `Stamp?`
  - Contact: `Blimey! You're not even a member! Right, your first task is...
    Slay my thirst. I require beer!`

- The player may instead choose `Hi. And, uh... bye!`; that ends the dialogue
  without starting the quest or changing Monster Slayer state. Choosing the
  Monster Slayer conversation begins the quest and the beer assignment.

- The speaker is a new dedicated male Monster Slayer NPC placed in the Rising
  Sun, not Barmaid `142`. The existing Barmaid remains the person from whom the
  player obtains the beer.
- On return, the player may either offer the beer or say they do not have it
  yet. Saying they do not have it ends the exchange without changing state. If
  they choose to offer it without actually carrying a beer, the contact tells
  them they have not got the beer yet and does not advance the quest.
- Offering a carried beer consumes one beer and completes the introduction with
  this exchange:

  - Contact: `Excellent, I dub thee an official fledgling Monster Slayer. Hold
    out your hand for your official stamp`
  - Player: `Do I get an official badge as well or something?`
  - Contact: `Nope, just the stamp`
  - Player: `This feels cheap`
  - Contact: `It's an honor. Return to me any time you wish to continue hunting
    monsters!`

- Completing the beer assignment awards `Fledgling`, after which talking to
  this same contact offers the first actual mandatory monster assignment.
- During the entire first Fledgling mandatory batch, dialogue presents only
  the current assignment and the promise that persistent work earns the next
  rank. It does not yet introduce challenge points, repeatable random
  assignments, or the challenge shop.
- Completing that first batch is the teaching/unlock moment for the distinction
  between fixed mandatory rank assignments and optional randomized point
  assignments, and for the Fledgling challenge shop.
- This decision does not yet replace a `MonsterSlayer.json` monster entry: the
  beer introduction is represented by the separate intro state. The later
  player-visible implementation must synchronize the confirmed dialogue and
  single-quest lifecycle; the current foundation intentionally has no dialogue
  or quest registration. It must also add the dedicated NPC definition and
  spawn and replace the foundation JSON's current Falador contact ID `142` with
  that NPC's stable ID.

### Confirmed: Fledgling Assignments And Initiate Reveal

The first monster batch deliberately stays with creatures rather than people.
Goblins are the opening exception to the non-humanoid preference. Exact NPC IDs
keep the early and late versions of otherwise identically named creatures from
collapsing into one task.

| Order | Assignment wording | Counted NPC IDs and repository combat levels | Kills |
| ---: | --- | --- | ---: |
| 1 | Goblins | Goblin `62` (level 7) | 40 |
| 2 | Young giant spiders | Giant Spider `23` (level 8) | 40 |
| 3 | Tougher goblins | Goblins `4,153,154` (level 13) | 50 |
| 4 | Large rats | Rats `47,177` (level 13) | 50 |
| 5 | Scorpions | Scorpion `70` (level 21) | 45 |
| 6 | Bears | Bears `8,188` (levels 24 and 26) | 45 |
| 7 | Desert wolves | Desert Wolf `721` (level 31) | 15 |
| 8 | Black unicorns | Black Unicorn `296` (level 31) | 12 |
| 9 | Giant spiders (level 31) | Giant Spider `74` (level 31) | 10 |

The batch is nine assignments and 307 kills. The level-13-to-21 gap is
intentional: repository inventory found no broadly accessible, non-humanoid
middle target. Dungeon Rats `367` are concentrated in Clock Tower and
Underground Pass spaces and must not become an implicit quest gate. Cows are
livestock; dwarves and dark wizards conflict with the creature-focused tone;
Poison Scorpions introduce an inappropriate cure requirement at this rank.

The three final assignments are short environmental trials rather than grind
counts. Desert Wolves require Shantay Pass and desert-heat preparation. All ten
active Black Unicorn spawns are in the Wilderness. Five of seven level-31 Giant
Spider spawns are in the Wilderness and the other two are isolated underground.
The task giver must warn the player clearly about desert preparation and
Wilderness exposure before assigning those stages; the danger is intentional,
not hidden accessibility debt.

After the ninth kill task, the contact:

- congratulates the player for doing a fine job culling the monsters, despite
  there appearing to be just as many monsters as before;
- advances the player from `Fledgling` to `Initiate` and presents proof of the
  new rank as a sticker that can supposedly be displayed wherever the player
  chooses;
- explains through comedic banter that hand stamps have been retired because
  they are far too impermanent, while stickers are obviously much better;
- reveals that the completed mandatory assignments have already been accruing
  Fledgling Slayer Points even though the system was not explained yet;
- opens the first challenge shop and explains that randomized assignments will
  always be available from this contact for earning more Fledgling Slayer
  Points; and
- introduces the first shop as a source of low-level food and potions. Its
  approved stock and normal ten-unit restock behavior are documented below;
  prices remain a playtest-tuned economy pass.

The Initiate promotion and sticker exchange is locked as:

- Contact: `Excellent work! You've done a fine job culling those monsters.`
- Player: `There seem to be just as many as before.`
- Contact: `Imagine how many there would be if you hadn't helped.`
- Contact: `For your efforts, I promote you to Initiate. Hold still while I
  apply your official rank sticker.`
- Player: `A sticker? What happened to the stamp?`
- Contact: `Stamps have been retired. Far too impermanent.`
- Player: `And stickers are better?`
- Contact: `Infinitely. You may proudly display it wherever you choose.`

This confirms invisible accrual during the first batch: completion dialogue
must reveal the actual balance, not award a second retroactive grant. The
currency's player-facing name is `Fledgling Slayer Points`; internally it
remains the typed `FLEDGLING` challenge balance. The subsequent point-balance,
random-assignment, and shop explanation should remain light and comedic, but
its exact wording depends on the still-unsettled balance and stock.

The merged foundation does not match this decision. A later implementation
sync must replace its five Falador tasks/500 kills and humanoid/livestock
families with the nine tasks/307 kills above, define the newly required
families without overlapping NPC IDs, and update the affected totals and
fixtures. No player-visible Monster Slayer state currently makes those
foundation task keys a live compatibility contract.

### Confirmed: Remaining Mandatory Monster Ladder

The remaining five challenge bands continue the combat curve without moving
backward. Later chains contain fewer families because individual kills become
slower, more dangerous, and more preparation-intensive.

| Challenge rank and promotion | Ordered mandatory monster sequence |
| --- | --- |
| `Initiate -> Veteran` | Giant Bat `43` (level 32) -> Deadly Red Spider `99` (36) -> King Scorpion `136` (36) -> White Wolf `248` (41) -> Ugthanki `653` (45) -> Animated Axe `295` (46) -> Jungle Spider `521` (47) -> Baby Blue Dragon `203` (50) -> Shadow Spider `343` (53) |
| `Veteran -> Elite` | Jogre `523` (58) -> Karamja Wolf `775` (61) -> Moss Giants `104,594` (62) -> Poison Spider `292` (63) -> Grey Wolf `243` (64) -> Ice Spider `263` (64) |
| `Elite -> Champion` | Ice Giant `135` (68) -> Lesser Demons `22,181` (79) -> Greater Demon `184` (87) |
| `Champion -> Hero` | Blue Dragon `202` (105) -> Fire Giant `344` (109) -> Green Dragon `196` (110) -> Hellhound `294` (114) -> Red Dragon `201` (140) |
| `Hero -> Legend` | Black Demon `290` (156) -> Black Dragon `291` (200) -> King Black Dragon `477` (245) |

Together with the confirmed nine-task Fledgling chain, this establishes 35
ordered mandatory kill assignments from level 7 through level 245. Kill counts
and point awards for the five newly confirmed bands remain to be tuned; roster
approval does not silently approve the foundation counts or point vector.

`Monster` means a fantasy creature rather than a normal person or civilized
social NPC. The mandatory ladder excludes pirates, muggers, dwarves, wizards,
knights, paladins, warriors, and similar people. It may include unmistakable
fantasy-monster species such as goblins, Jogres, giants, and demons even when
their sprite is bipedal. The same boundary applies when the randomized family
pools are redesigned.

Repository density and behavior impose these tuning constraints:

- White Wolves have six active spawns; Animated Axes nine, mostly in the
  Wilderness; Baby Blue Dragons eleven; Blue Dragons six; Green Dragons five;
  Red Dragons seven, all in the Wilderness; Black Dragons four; and the King
  Black Dragon one. Their counts must remain substantially below dense-family
  counts.
- Baby Blue Dragons provide the first bounded dragon-fire lesson. Full dragons
  begin only at `Champion`, where dialogue must explicitly warn about dragon
  fire and preparation rather than assuming the player knows the mechanic.
- Shadow Spiders drain Prayer and Poison Spiders introduce poison. These are
  intentional mechanic steps at Initiate and Veteran, respectively, and their
  assignment dialogue must warn the player.
- Wilderness travel is an accepted part of this quest, but every mandatory
  Wilderness-heavy task requires an explicit risk warning before assignment.
- The King Black Dragon remains the final mandatory target and the quest's
  iconic combat capstone. The level-275 Elder Green Dragon is reserved as a
  possible post-`Legend` boss assignment rather than displacing that finale.

Death Wings remain possible post-`Legend` randomized content because their
Legends Quest access and level 80 would break the mandatory level curve.
Blessed Spiders and Dungeon Rats remain excluded from mandatory progression due
to Underground Pass/Clock Tower access coupling. Otherworldly Beings would add
an unrelated Lost City gate. The Balrog remains excluded because it is coupled
to Dwarf Youth Rescue/lava-forge access and its level-217 label hides extreme
repository stats of 999 Attack and 500 Hits.

The merged `MonsterSlayer.json` does not match this confirmed ladder. A later
implementation synchronization must replace all six mandatory family sequences,
rebuild affected family definitions and stable task keys before activation,
recalculate kill/point totals, and update data/state/migration fixtures. It
must not change live Combat Odyssey behavior while performing that sync.

### Confirmed: Permanent Inventory-Capacity Shop Upgrades

Every challenge shop contains exactly one one-time inventory-capacity upgrade.
The upgrade becomes available when that shop is unlocked, but it is not an
automatic rank reward: the player must purchase it with that shop's approved
native-challenge price. Initial prices and task rewards are implementation
estimates to be tuned through playtesting, not inherited from Odyssey data.

The six increments and cumulative capacities are fixed:

| Shop/contact | Native challenge | Slots added | Resulting capacity |
| --- | --- | ---: | ---: |
| Rising Sun/Falador | Fledgling | 1 | 31 |
| Port Sarim | Initiate | 1 | 32 |
| Brimhaven | Veteran | 1 | 33 |
| Champions Guild | Elite | 2 | 35 |
| Heroes Guild | Champion | 2 | 37 |
| Legends Guild | Hero | 3 | 40 |

The base capacity remains 30 and the final capacity is exactly 40. Upgrades are
independent permanent entitlements and must be purchased in shop order because
higher shops are rank-gated. They are not items, cannot be traded, dropped,
lost on death, refunded, or purchased more than once. A purchase does not need
a free inventory slot. It atomically validates the shop unlock, confirms that
the corresponding upgrade is not already owned, deducts its native-currency
price, records the entitlement, and then refreshes the inventory UI. Failure
at any stage leaves both points and capacity unchanged. The entitlement is
strictly per player: each player may successfully buy each shop's upgrade once
and only once. The implementation may retain a normal visible/restocking shop
entry if that is the least intrusive integration, provided an already owning
player cannot buy it again and receives `You already have this.`

Persist the six purchases as a stable six-bit entitlement mask owned by
`MonsterSlayerState`, with bits mapped explicitly by stable shop key rather
than JSON order or enum ordinal. Derive capacity as `30 +` the sum of the
owned increments; do not persist a second mutable capacity value. Unknown bits,
an upgrade whose prerequisite shop is not unlocked, or a non-prefix purchase
sequence are invalid state and must be diagnosed rather than silently granting
space.

The client inventory panel must expand from its current 6-by-5 grid to an
8-by-5 grid containing all 40 slot
positions. The first `current capacity` positions are active in deterministic
display order. Every remaining position is drawn as a greyed-out locked slot
so the player can see future capacity but cannot place, receive, select, drag,
equip from, drop from, or otherwise interact with it. Purchasing an upgrade
turns the next contiguous group of grey slots into normal inventory slots
without moving existing items.

The server remains authoritative for capacity. Implementation must replace or
parameterize every gameplay assumption that inventory capacity is always 30,
including item grants, stack splitting, bank withdrawal, trading, shops,
production, ground-item pickup, equipment removal, death/Ante handling,
teleports or quest rewards that require space, persistence, and admin tools.
The client/server inventory packets and release clients must accept and render
up to 40 entries while remaining safe for a normal 30-slot player. A client UI
change and a server capacity change must ship together; do not activate the
shop reward while either side still truncates or assumes 30.

Required coverage includes every cumulative capacity above; one-time and
out-of-order purchase rejection; atomic multi-balance deduction; reconnect and
save/load persistence; full-inventory purchase; insertion at the new boundary;
rejection beyond the current boundary; bank/trade/equipment/death flows at 30
and 40 slots; locked-slot visuals and input rejection; layout at supported
window/UI scales; and no item loss when a newer client observes an expanded
inventory. A downgrade to a client or server that cannot represent the saved
capacity must fail safely rather than truncate items.

### Confirmed: Initial Activation And Test Order

Implement and validate the player-visible system in this dependency order:

1. Persist and validate six zero-default typed balances plus the capacity
   entitlement mask; migrate every existing account with zero balances.
2. Wire authoritative mandatory/repeatable task completion to the appropriate
   single balance, using initial AI-authored estimates for rewards and focused
   reward/overflow tests.
3. Add the six rank-gated shops with their approved consumables, normal stock
   of 10/restock behavior, typed two-tier consumable costs, and native-only
   one-time capacity purchases.
4. Deliver the dynamic 30-to-40 inventory protocol and client UI together,
   then test all capacity boundaries and inventory-bearing systems.
5. Add eleven new unique armored task/shop NPCs plus either the twelfth new
   Legends contact or the approved Radimus task-route rework, then add the
   three generic one-line bar members, dialogue, shortcuts, rank gates, and
   world placements. Finish with end-to-end task, promotion, shop, reconnect,
   and migration tests.

Playtesting follows each economy-bearing slice. Initial numerical estimates
are intentionally adjustable; the contracts for typed currency, stock,
one-time purchases, rank gates, and capacity are not.

### Dynamic Inventory Capacity Implementation Boundary

The existing code cannot achieve this by changing one constant. Server
`Inventory.MAX_SIZE` is a global 30-slot admission limit; the client allocates
30 inventory entries and its inventory tab draws a fixed six rows by five
columns; several server handlers also use the same constant as the boundary
between inventory and equipment packet slots. The capacity-upgrade branch must
therefore introduce one shared dynamic-capacity contract rather than widening
all players to 40.

The recommended design is:

1. Define `BASE_CAPACITY = 30` and `MAX_SUPPORTED_CAPACITY = 40`. Resolve an
   individual player's active capacity from their validated Monster Slayer
   entitlement mask. Ordinary players always resolve to 30.
2. Replace every admission, free-slot, full-inventory, grant, trade, bank,
   death, equipment-removal, and preset check that currently uses the global
   limit with the owning player's active capacity. The server remains the only
   authority; the client never claims an unlocked slot.
3. Keep `MAX_SUPPORTED_CAPACITY` as the fixed protocol boundary for custom
   client arrays and equipment-slot offsets. A 30-slot player has locked
   inventory positions `30..39`; equipment begins after the maximum inventory
   range, not after that player's current capacity. This prevents an expanded
   inventory from colliding with equipment packet indexes.
4. Add a custom-client, authoritative inventory-capacity receipt sent before
   the first full inventory packet on login and immediately after a successful
   upgrade. Do not reuse the legacy-only `SEND_INVENTORY_SIZE` behavior as the
   new per-player contract. The receipt must be version/capability-gated so an
   older client cannot accept a 31-to-40-slot player and silently truncate
   their items.
5. Allocate the custom client inventory storage to the fixed supported maximum
   of 40, retain a runtime `inventoryCapacity`, and replace the fixed
   six-row/five-column draw and hit-test limits with `5` columns and
   `ceil(inventoryCapacity / 5)` active rows. The panel grows from 6-by-5 to
   8-by-5; partially unlocked rows show inactive positions as locked. The
   count label displays `[count]/[active capacity]`, never `/30`.
6. Anchor the expanded custom UI panel so all eight rows remain usable at the
   fixed classic viewport and supported widescreen scales. If the normal tab
   area cannot fit them, use an explicit expanded-inventory presentation or
   scroll behavior; never leave lower slots drawn but unreachable.

Before implementation, inventory indices, full/update packet ordering,
equipment offsets, bank preset arrays, bank withdrawal, trade, production,
ground-item pickup, death/Ante, drop/use/equip handlers, and client input
paths must be inventoried against this contract. Required tests include each
capacity boundary `30, 31, 32, 33, 35, 37, 40`; reconnect/load with 31-to-40
items; older-client refusal; locked-slot input rejection; equipment index
separation; full-inventory rollback; and no loss through bank, trade, death,
or a capacity upgrade.

### Unresolved Recruitment And First-Shop Details

- Choose the twelve contact identities, exact IDs, and placement tiles. This
  means eleven new unique task-giver/shop-associate NPCs plus either a new
  Legends task giver or the approved reuse of Sir Radimus `785`. New NPCs are
  unique humanoids in armor that improves with rank. Do not repurpose Barmaid
  `142`, bartenders, guildmasters, or quest NPCs except for the explicitly
  approved Radimus task-route option; keep the three generic bar members to
  their one-line ambient role.
- Choose the formal quest name, quest-list presentation, journal text, and any
  quest-point treatment. Calling the mandatory path one quest settles its
  lifecycle, but not those presentation details.
- Decide whether the Initiate sticker is dialogue-only rank flavor, a physical
  inventory item, or a displayable cosmetic. If it is an item, tradeability,
  death behavior, duplicate prevention, storage, reclaim, and whether it is
  consumed when displayed all require explicit contracts.
- Author initial mandatory/repeatable task rewards and all consumable/native
  currency prices as evidence-backed starting estimates, then tune them through
  owner playtesting. The consumable lines, stock quantity of 10, normal
  restock behavior, and capacity-upgrade rules are already settled.

## Evidence-Backed Combat Odyssey Audit

### Activation And Runtime Owners

MyWorld currently sets `want_combat_odyssey: true` in `server/myworld.conf`.
`World` constructs `CombatOdysseyData` and loads its JSON only when that flag is
enabled. New starts are nevertheless intentionally hidden:
`LegendsQuestSirRadimusErkle.doCombatOdyssey` returns `false` for
`NOT_STARTED`, while partial runs, reward claims, and prestige repeats remain
reachable. This is active compatibility code, not dead code.

The maintained implementation is split across:

- `server/conf/server/defs/extras/CombatOdyssey.json`: tier/task/reward data.
- `server/src/com/openrsc/server/content/minigame/combatodyssey/`: the JSON
  loader and positional `Tier`/`Task` models.
- `server/plugins/com/openrsc/server/plugins/custom/minigames/CombatOdyssey.java`:
  intro, Biggum, active task state, final-blow kill tracking, random task order,
  final reward, prestige, and developer controls.
- Eight authentic quest/NPC dialogue owners plus the Legends ladder, Biggum
  visibility, and player-stat display integrations inventoried below.

Do not delete or switch off those paths until Monster Slayer activation has
migrated live state and supplied replacement dialogue behavior.

### JSON Inventory And Semantics

Repository validation of `CombatOdyssey.json` produced:

| Measure | Result |
| --- | ---: |
| Tiers | 14 (`0` through `13`) |
| Tasks | 101 |
| Required kills | 40,906 |
| Unique referenced NPC IDs | 192 |
| Missing NPC definitions | 0 |
| Maximum tasks in one tier | 20 |
| Referenced unattackable NPC IDs | 2 (`375`, `376`) |
| Referenced IDs with no active MyWorld static spawn | 6 |
| Families with no active static target at all | 0 |

The active tier path is:

| Tier | Master/contact and active location | Tasks | Kills | Reward stored on this tier |
| ---: | --- | ---: | ---: | --- |
| 0 | General Wartface `151`, Goblin Village `324,447` | 20 | 8,170 | none |
| 1 | General Wartface `151` | 1 | 500 | 20 stat-restoration certs, 200 giant-carp certs, strength amulet, medium rune helmet |
| 2 | Thormac `300`, `511,1452` | 14 | 5,525 | none |
| 3 | Grew `681`, `663,759` | 19 | 7,600 | rune square shield |
| 4 | Dark Mage `667`, `665,567` | 12 | 5,550 | rune battle axe, 20 cure-poison certs |
| 5 | Dark Mage `667` | 1 | 100 | 200 blood runes |
| 6 | Hazelmere `546`, `532,754` | 13 | 5,800 | 200 lava-eel certs, power amulet, rune helmet |
| 7 | Hazelmere `546` | 1 | 500 | 20 poison-antidote certs, rune paladin shield |
| 8 | Sigbert `573`, `584,3575` | 8 | 3,550 | none |
| 9 | Achetties `253`, Heroes Guild `372,443` | 5 | 860 | 100 prayer-potion certs, charged dragonstone amulet, rune two-handed sword |
| 10 | Sir Radimus `785`, Legends Guild `514,535` | 3 | 1,250 | 100 manta-ray certs, 100 sea-turtle certs, dragonstone ring, 20 each super attack/strength/defense certs |
| 11 | Sir Radimus `785` | 2 | 1,000 | none |
| 12 | Sir Radimus `785` | 1 | 500 | none |
| 13 | Sir Radimus `785` | 1 | 1 | none |

Important implementation facts:

- `CombatOdysseyData.load` assigns each task's ID from its array position.
  `getTier(int)` also indexes the tier list instead of looking up `tierId`.
  Reordering either array silently changes persisted meaning.
- `tierId` is loaded into `Tier` but has no getter and is not used as a lookup
  identity. The new system must not inherit this positional contract.
- Every task contains `taskInfoDialog`, but the loader and `Task` discard it.
  Only `monsterInfoDialog` survives.
- A tier's `rewards` are actually granted after the preceding tier completes:
  dialogue first calls `assignNewTier`, then `giveRewards`. Treating the field
  as a completion reward without understanding this order would shift rewards.
- Mandatory tasks within a tier are random, not deterministic. A bit in the
  current tier mask prevents repeats until all tasks in that tier are done.
- The final reward is not in JSON. `CombatOdyssey.radimusDialog` directly gives
  `DRAGON_PLATE_MAIL_LEGS` (`1429`), removes active keys, and then increments
  prestige. That reward path is excluded from Monster Slayer.
- The old intermediate supplies and equipment are balance evidence only. None
  automatically become Monster Slayer shop stock.

This inventory is an audit and migration decoder, not a conversion table. The
new 33-task inventory was selected independently from current definitions,
attackability, spawn availability, travel friction, and the intended contact
themes. Legacy tier boundaries do not define new ranks; legacy random order
does not define mandatory order; and legacy rewards do not define shop tiers,
costs, or stock.

### Legacy Cache And Completion State

Combat Odyssey uses three player-cache keys and an inventory/bank item:

| State | Type | Current meaning and lifecycle |
| --- | --- | --- |
| `combat_odyssey` | String | Overloaded. Missing/`"0"` is not started, `"1"` is Radimus accepted, `"2"` is Biggum met, and `"tier:task:kills"` is an active run. Colon parsing is positional. |
| `co_tier_progress` | Long | Bit mask for completed tasks in only the current tier. It resets on every new tier and is removed after the final reward. |
| `co_prestige` | Integer | Number of final rewards claimed/full Odyssey completions. It is the only durable completed-state marker after active keys are removed. |
| Biggum item `BIGGUM_FLODROT` | item | Companion/tracker may be in inventory or bank. The courtyard NPC `826` at `511,544` is visible only to a prestiged player not carrying/banking the item. |

Consequences for migration:

- `co_prestige > 0` is authoritative proof of at least one claimed completion.
- A valid tier-13 state with its KBD task complete and no prestige is a
  completed-but-unclaimed Odyssey. It must be honored without granting dragon
  armor.
- A tier number proves every lower tier was completed, because tier assignment
  is gated by `isTierCompleted`; the current tier mask plus active kill count
  provides the remaining partial evidence.
- Item possession is not completion evidence and must not be used to infer a
  reward claim.
- The final legacy flow removes active keys before giving the item and only then
  increments prestige. A crash in that window could leave ambiguous state; the
  migration must fail closed rather than infer completion from item ownership.
- `isTierCompleted` uses exact mask equality. Unknown high bits and malformed
  strings should be diagnosed and quarantined, not normalized silently.

The cache already persists typed primitive values through `Cache`,
`GameDatabase.querySavePlayerCache`, and `PlayerService.loadPlayerCache`; no
database schema change is required for Monster Slayer.

### Current Kill Credit

`Npc.killedBy` currently calls the generic `KillNpcTrigger` before
`handleXpDistribution` selects the top-damage owner. `CombatOdyssey.onKillNpc`
therefore receives the player associated with the killing blow (or the owner of
a summon that delivered it), not every contributor and not necessarily the
top-damage player. It increments exactly one player's active count.

Later in the same death path, `Npc` already aggregates melee, ranged, magic,
and summon-owner damage. Its personal-loot path accepts online, living players
with positive damage and applies a contribution scale with a `0.05` floor.
Those maps are private and cleared after XP/drop processing.

Monster Slayer should not change `KillNpcTrigger`, because many quests depend
on its existing final-blow semantics. Its later kill-credit branch should add a
focused contribution snapshot/hook at the NPC-death layer and use this rule:

- Aggregate melee, ranged, magic, and owned-summon damage by player UUID.
- Credit each online, living contributor still within 16 tiles who dealt at
  least `max(1, ceil(npcMaxHits * 0.05))` damage; always credit the top-damage
  contributor when their damage is positive.
- Credit a player at most once per NPC death even if several damage styles or a
  summon contributed.
- Match only the active task's validated family IDs.
- Do not grant points per kill; only advance the bounded active count.

The five-percent recommendation aligns with the existing personal-loot scale
floor while preventing a one-hit tag on larger monsters. It is a Monster
Slayer rule, not authorization to change XP, loot, or existing quest credit.

### Dialogue And Compatibility Integrations

| Current integration | Active Odyssey responsibility |
| --- | --- |
| `GoblinDiplomacy` generals | Start tier 0 after Biggum, advance `0 -> 1`, grant tier-1 rewards. |
| `ScorpionCatcher` / Thormac | Advance `1 -> 2`. |
| `WatchTowerDialogues` / Grew | Advance `2 -> 3`, grant tier-3 rewards. |
| `npcs/ardougne/west/DarkMage` | Advance `3 -> 4 -> 5`, grant both entry reward sets. |
| `GrandTree` / Hazelmere | Advance `5 -> 6 -> 7`, require the translation item, grant both entry reward sets. |
| `SigbertTheAdventurer` | Advance `7 -> 8`. |
| `HerosQuest` / Achetties | Advance `8 -> 9`, grant tier-9 rewards. |
| `LegendsQuestSirRadimusErkle` | Hide new starts, recover Biggum, advance `9 -> 10 -> 11 -> 12 -> 13`, grant tier-10 rewards, and claim the final reward. |
| `Ladders` | Automatically introduces/recovers Biggum on the Legends Guild upper-floor ladder. Radimus says to see Siegfried, but no Siegfried Odyssey dialogue starts the tasks. |
| `Npc.isInvisibleTo` | Hides/shows courtyard Biggum from prestige and carried/banked item state. |
| `RegularPlayer` stat display | Shows `co_prestige` as Odyssey completions. |

These integrations are maintained compatibility boundaries until activation.
The Monster Slayer foundation must not edit them. A later cutover branch must
disable legacy advancement as one coordinated change, keep the old keys
readable, and replace rather than stack dialogue routes.

## Target Rank And Contact Design

Stable rank codes are part of the save contract and must never be reordered:

| Code | Rank | Advancement contact |
| ---: | --- | --- |
| 0 | Unstamped | Recruit prompt and beer assignment begin the guild quest |
| 1 | Fledgling | Beer completed; first monster assignment available |
| 2 | Initiate | Falador chain complete; Port Sarim available |
| 3 | Veteran | Port Sarim chain complete; Brimhaven available |
| 4 | Elite | Brimhaven chain complete; Champions Guild available |
| 5 | Champion | Champions chain complete; Heroes Guild available |
| 6 | Hero | Heroes chain complete; Legends Guild available |
| 7 | Legend | Legends chain complete; all rank shops available |

Every Monster Slayer location has at least two distinct NPC roles: one task
giver and one nearby shop associate. The task giver owns only rank, mandatory,
and repeatable-task dialogue; the associate owns only the rank-gated challenge
shop dialogue. They must never be combined merely because a location reuses an
existing bartender or guild official. Every task giver and shop associate is a
newly authored, unique humanoid NPC, except that the Legends task giver may
reuse Sir Radimus `785`, the original borrowed-system task giver. Their initial
identity, name, ID, and exact placement are a focused content pass; their
appearance should visibly progress from simple early equipment to better armor
at the higher ranks. Existing bartenders, guildmasters, and quest NPCs retain
their original responsibilities and are not repurposed as Slayer contacts,
apart from the explicitly selected Radimus Slayer-route rework.

| Contact key | New task-giver location | Integration boundary |
| --- | --- | --- |
| `falador` | Rising Sun ground floor | Add a dedicated definition and spawn. The contact directs the player to Barmaid `142` for beer; do not replace or intercept the Barmaid's existing dialogue. |
| `port_sarim` | Rusty Anchor, near the existing bartender | Add a separate task giver without replacing drink or bar-crawl service. |
| `brimhaven` | Dead Man's Chest, near the existing bartender | Add a separate task giver without replacing drink or bar-crawl service. |
| `champions` | Champions Guild, near Guildmaster `111` | Preserve Dragon Slayer and normal guild-access dialogue on the Guildmaster. |
| `heroes` | Heroes Guild, near Achetties `253` | Preserve Heroes Quest/cape behavior on Achetties; remove the old Odyssey tier transition only in the coordinated activation branch. |
| `legends` | Legends Guild, near Sir Radimus `785` | Default to a new task giver, but Sir Radimus `785` may instead be selected and reworked as the task giver because he owned the borrowed system. Preserve his Legends Quest reward/training routes and replace only the old Odyssey task route during activation. |

Higher contacts require both the previous Monster Slayer rank and their normal
host-guild access. Early conversation should explain which stamp is required
without bypassing Champions, Heroes, or Legends Guild entry requirements.

### Location Staffing And Ambient Members

The activation branch must add one dedicated unique-humanoid shop-associate
definition and placement near each task giver. Their armor quality follows the
same rising-rank visual progression as the task givers. Exact NPC IDs, names,
and tiles remain a focused world/content pass, but every placement must be
visibly close enough that promotion dialogue such as `my associate nearby`
remains true. Existing bartenders and guild officials retain their current
roles; a new associate is not a reason to remove ordinary drinks, guild access,
quests, or training dialogue.

The three bar locations should additionally receive one generic ambient member
each. These are deliberately non-authoritative, non-unique humanoid world-fill
NPCs: they have no `Task`, `Trade`, or `Shop` shortcut, grant no
task/currency/rank state, and provide one brief optional flavour line only.
The proposed initial roster is:

| Location | Ambient member display name | World role and voice |
| --- | --- | --- |
| Rising Sun / Falador | `Fledgling Monster Slayer` | An eager recruit comparing a fresh hand stamp and boasting about very small monsters. |
| Rusty Anchor / Port Sarim | `Initiate Monster Slayer` | A practical hunter checking supplies and talking about keeping a task journal dry at sea. |
| Dead Man's Chest / Brimhaven | `Veteran Monster Slayer` | A scarred, guarded regular who acknowledges the work but avoids giving a contract. |

This produces three NPCs at each bar—task giver, shop associate, and ambient
member—while every guild location retains the mandatory two-role minimum. More
ambient members may be added later, but they must remain separate from the
stable task/contact IDs and all state-changing dialogue routes.

## Player-Facing Contact, Task, And Shop Dialogue Contract

This section is the implementation-ready dialogue contract for the six
contacts. It supplements the fixed monster ladder; it does not change task
families, kill counts, point values, normal host-guild access, or the existing
non-Monster-Slayer dialogue that each reused NPC already owns.

### Shared Interaction Rules

- A contact recognizes a player who has reached that contact's entry rank. A
  player below that rank is not silently offered a task, a repeatable, or a
  shop route. The contact explains the missing proof/rank and ends the
  conversation.
- The normal Talk-to route is intentionally social: it gives the player a
  chance to accept or decline the next assignment, then asks to see their
  current proof before assigning it. The proof check is dialogue flavor; the
  server still validates rank, normal guild access, the one-active-task rule,
  and every mandatory/repeatable state transition.
- Once a player is eligible, the `Task` right-click option is a shortcut. It
  skips the greeting and the `Yes please / Not now` choice and begins at the
  contact's `Your next task is...` line. It must use the same authoritative
  assignment path as Talk-to. If the player is ineligible, the shortcut must
  not assign anything and should show a brief NPC line where possible (for
  example, `Not yet. You need an Initiate sticker before I can give you work.`)
  or the equivalent game message when the client cannot open dialogue.
- A player with an active task is shown its current objective and progress
  rather than being given a second task. A player who has finished an
  assignment is sent through completion/promotion handling before another is
  chosen. This preserves the existing one-active-task invariant across all six
  contacts.
- Assignment text uses the deterministic current entry from the mandatory
  chain, or a chosen family/count from that contact's repeatable pool:
  `Your next task is to slay <count> <family>.` Before an assignment whose
  roster notes poison, Prayer drain, dragon fire, desert preparation, or
  Wilderness exposure, append the mandated risk warning before task state is
  written. The shortcut does not suppress a required warning.
- Each completed mandatory chain promotes the player at its own contact. The
  promotion unlocks that contact's nearby associate shop and its corresponding
  challenge-point balance. The associate, not the task giver, opens the shop.
  This gives each location two clear roles and avoids overloading an existing
  bartender or guild NPC's normal service dialogue.
- An eligible associate opens with a short, rank-appropriate acknowledgement
  and a `Show me your wares.` / `Not now.` choice. `Show me your wares.` opens
  that location's existing typed-currency shop; it never spends points or
  grants an item as part of dialogue. Ineligible use—Talk-to or a right-click
  `Trade`/`Shop` shortcut—uses the table's refusal and never opens an empty or
  partially locked shop interface.

### Rank Proofs And Shop Gates

The proof progression deliberately starts silly and becomes ceremonial. A
proof is rank flavor unless a later presentation decision explicitly makes it
an item or cosmetic; it is not a second authority beside the persisted rank.
There are six proof changes for the six promotions before the final standing:

| Player rank after promotion | Proof shown in dialogue | Shop newly unlocked | Associate refusal before unlock |
| --- | --- | --- | --- |
| Fledgling | hand stamp | none; the player is beginning the first chain | `You need to earn your Initiate sticker first.` |
| Initiate | sticker | Rising Sun / Fledgling point shop | `Sorry, can't show you my wares till you're an Initiate.` |
| Veteran | button | Port Sarim / Initiate point shop | `Sorry, can't show you my wares till you're a Veteran.` |
| Elite | badge | Brimhaven / Veteran point shop | `Sorry, can't show you my wares till you're an Elite.` |
| Champion | medal | Champions Guild / Elite point shop | `Sorry, can't show you my wares till you're a Champion.` |
| Hero | crest | Heroes Guild / Champion point shop | `Sorry, can't show you my wares till you're a Hero.` |
| Legend | no additional trinket; the rank itself is the final recognition | Legends Guild / Hero point shop | `Sorry, can't show you my wares till you're a Legend.` |

The final `Legend` promotion intentionally does not add a seventh trinket. It
ends the escalating stamp/sticker/button/badge/medal/crest joke with the
Legends contact treating status as something demonstrated rather than worn.
Every associate shop spends only the already-defined typed Monster Slayer
currency and must retain its rank, point-vector, and normal host-guild access
validation server-side.

### Contact Dialogue Sheets

Names remain implementation choices. These sheets identify the voice, the
required branch points, and dialogue for the new unique contacts at each
location. A contact name may take light inspiration from a recognizable OSRS
Slayer giver, but that does not require a matching visual, personality, or
one-to-one adaptation. Bracketed text is runtime data, never player-controlled
text.

#### 1. Rising Sun Recruiter — Fledgling Stamp And Initiate Sticker

Tone: quaint, chummy, and a little absurd. This is the dedicated new Rising
Sun contact, not Barmaid `142`.

**Unstamped introduction**

> Contact: `'ello there.`
> Player: `I hear you give Monster Slayer tasks?`
> Contact: `I sure do! Show me your stamp first.`
> Player: `Stamp?`
> Contact: `Blimey! You're not even a member! Right, your first task is...`
> `Slay my thirst. I require beer!`

The player may instead choose `Hi. And, uh... bye!`; that ends the dialogue
without starting the guild quest. The beer route, Barmaid handoff, carried-beer
validation, consumption, and confirmed Fledgling stamp exchange remain exactly
as specified in **Confirmed: One Continuous Mandatory Quest And Beer Opening**.

**Fledgling task route**

> Contact: `Oh, it's you again. Another task then?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Stamp?`
> Player: `Here ya go.`
> Contact: `Your next task is to slay [count] [family].`

`Task` shortcut: `Your next task is to slay [count] [family].` If the player
is unstamped, it instead says `No stamp, no task. Fetch the beer first.` and
does not begin an assignment.

**Fledgling completion / Initiate promotion**

Use the already-confirmed sticker exchange verbatim, then add:

> Contact: `You've been earning Fledgling Slayer Points while you worked.`
> Contact: `My associate nearby can trade them for useful supplies. And if you
> fancy more points, I can always find more monsters needing culling.`

The nearby associate opens only after `Initiate`; before then they use the
Fledgling row in the shop-gate table.

#### 2. Rusty Anchor Contact — Initiate Sticker To Veteran Button

Tone: friendly and capable, but no longer quaint; a working-port regular who
treats the guild as honest employment.

**Below rank**

> Contact: `I need to see an Initiate sticker before I can put your name on my
> list. Earn one at the Rising Sun, then come back.`

**Normal task route**

> Contact: `Back for work, are you?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Let's see that sticker.`
> Player: `Here you are.`
> Contact: `Your next task is to slay [count] [family]. Keep your kit dry and
> your head on.`

`Task` shortcut begins at `Your next task is...`; below-rank use says `No
Initiate sticker, no Port Sarim work.`

**Veteran promotion / shop reveal**

> Contact: `You did what you said you would. That's worth more than loud talk.`
> Contact: `You're a Veteran now. Wear this button somewhere it won't fall in
> the drink.`
> Contact: `The trader beside me deals in Initiate Slayer Points. He's cleared
> to serve Veterans.`

#### 3. Dead Man's Chest Contact — Veteran Button To Elite Badge

Tone: a self-styled tough hunter. His bluster falls away at the
promotion, revealing that he has seen what the next step costs.

**Below rank**

> Contact: `A Veteran button gets you a proper job from me. Until then, you're
> drinking in the shallow end.`

**Normal task route**

> Contact: `You've got the look of someone after a dangerous job.`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Button.`
> Player: `Here.`
> Contact: `Your next task is to slay [count] [family]. Don't make me regret
> picking you.`

`Task` shortcut begins at the assignment. Below rank: `Come back with a
Veteran button if you want Brimhaven work.`

**Elite promotion / shop reveal**

> Contact: `Hah. I knew you had it in you. You're Elite now; take the badge.`
> Contact: `Listen, though. You're off to play with the big boys now.`
> Contact: `Not all adventurers survive the big leagues. I didn't. That's why
> I'm here telling stories instead of making them.`
> Contact: `My associate will trade Veteran Slayer Points with an Elite. Spend
> them on something that keeps you alive.`

#### 4. Champions Guild Contact — Elite Badge To Champion Medal

Tone: tough, boisterous, friendly, and jovial. Preserve Dragon Slayer and
ordinary Champions Guild dialogue on the existing Guildmaster before
presenting this optional guild route.

**Below rank**

> Contact: `An Elite badge is the price of a Champion's contract! Earn one
> first, then we'll see what you're made of.`

**Normal task route**

> Contact: `Ah! An Elite hunter. Here for a real challenge?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Badge, if you please!`
> Player: `Here you go.`
> Contact: `Your next task is to slay [count] [family]! Make the Guild
> proud!`

`Task` shortcut begins at the assignment. Below rank: `Bring me an Elite badge
before you ask for Champion work!`

**Champion promotion / shop reveal**

> Contact: `Splendid work! You faced the test and did not blink.`
> Contact: `You are a Champion now. Take this medal, and try not to polish
> it on your sleeve.`
> Contact: `The quartermaster nearby takes Elite Slayer Points. Tell them
> I said a Champion has earned a look at the good stock.`

#### 5. Heroes Guild Contact — Champion Medal To Hero Crest

Tone: a hardened veteran. Respectful rather than theatrical; they know the
cost of the creatures now being assigned. Preserve Heroes Quest and cape
behavior before this route.

**Below rank**

> Contact: `Champion's medal first. These contracts are not lessons.`

**Normal task route**

> Contact: `You came back. Do you want another contract?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Your medal.`
> Player: `Here.`
> Contact: `Your next task is to slay [count] [family]. Prepare before you
> leave; preparation is what brings people home.`

`Task` shortcut begins at the assignment. Below rank: `No Champion medal. No
Heroes Guild contract.`

**Hero promotion / shop reveal**

> Contact: `You completed the work, even when it was hard. That is the part
> people remember.`
> Contact: `You are a Hero. Carry this crest with care.`
> Contact: `The supplier nearby accepts Champion Slayer Points. A Hero has
> earned access.`

#### 6. Legends Guild Contact — Hero Crest To Legend

Tone: stoic, economical, and matter-of-fact. Default to a separate Legends
Guild contact. If Sir Radimus `785` is selected instead, preserve his Legends
Quest behavior and replace only his borrowed Odyssey task route; house Radimus
`735` is never a Slayer contact.

**Below rank**

> Contact: `Hero's crest required. Return when you have earned it.`

**Normal task route**

> Contact: `Another contract?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Crest.`
> Player: `Here.`
> Contact: `Your next task is to slay [count] [family]. Be ready.`

`Task` shortcut begins at the assignment. Below rank: `No Hero's crest. No
Legend contract.`

**Legend completion / final open ending**

> Contact: `You've completed your journey for now. You've done well.`
> Player: `And what's my new rank?`
> Contact: `And what use would you make of it?`
> Player: `...Legend, then?`
> Contact: `If you continue to earn it.`

After this exchange, the Legends associate opens the Hero-point shop. Their
pre-unlock refusal is `Sorry, can't show you my wares till you're a Legend.`
Legend repeatable tasks remain available, but the mandatory quest is complete;
the ending must not promise a further required rank or a finite completion of
all monster content.

## Data Design

Create `server/conf/server/defs/extras/MonsterSlayer.json`; do not extend the
positional Odyssey schema. The systems have different identity, ordering,
repeatable, rank, and migration requirements, and extending the old arrays
would make old cache meaning less safe.

Required top-level shape:

```json
{
  "schemaVersion": 1,
  "families": [],
  "contacts": [],
  "shops": []
}
```

Family shape:

```json
{
  "key": "goblin",
  "displayName": "Goblins",
  "npcIds": [4, 62, 153, 154, 660]
}
```

Contact/task shape. The `npcId: 142` below records the merged foundation data,
not the newly confirmed final contact; a later player-visible implementation
must replace it with the dedicated NPC's approved stable ID:

```json
{
  "key": "falador",
  "npcId": 142,
  "challenge": "FLEDGLING",
  "requiredRank": "FLEDGLING",
  "awardedRank": "INITIATE",
  "mandatoryTasks": [
    {
      "key": "falador.rats",
      "familyKey": "rat",
      "requiredKills": 100,
      "pointReward": 5
    }
  ],
  "repeatableTasks": [
    {
      "key": "falador.rats.repeatable",
      "familyKey": "rat",
      "requiredKills": 75,
      "pointReward": 5,
      "weight": 1
    }
  ]
}
```

`challenge` is the currency type awarded by every mandatory and repeatable task
owned by that contact. The loader must reject a task-level currency override;
there is only one source of truth for the contact's challenge balance.

Reward/category shape supported by the foundation schema (stock remains empty
until a later approved reward branch):

```json
{
  "key": "legends",
  "challenge": "HERO",
  "categories": [
    {
      "key": "combat_supplies",
      "label": "Combat supplies",
      "iconItemId": 0,
      "rewards": [
        {
          "key": "legends.example_supply",
          "itemId": 0,
          "amount": 1,
          "cost": {
            "CHAMPION": 3,
            "HERO": 5
          }
        }
      ]
    }
  ]
}
```

The example Legends cost means three Champion points and five Hero points per
reward unit. It does not mean eight interchangeable points. The Fledgling shop
has exactly one positive Fledgling component; every other shop has exactly two
positive components: its own challenge and the challenge immediately below it.
Cost vectors omit zero entries and are multiplied component-by-component for a
requested quantity using checked `long` arithmetic.

Stable identity rules:

- Persist task/contact/family keys, never JSON array positions or display text.
- Keys are lowercase ASCII dot-separated identifiers and are immutable once
  published. Display text may change without migration.
- A family owns NPC membership. Multiple contacts may reference the same family
  key; do not duplicate its ID list.
- Challenge codes are `FLEDGLING`, `INITIATE`, `VETERAN`, `ELITE`, `CHAMPION`,
  and `HERO`. There is no global or `LEGEND` point balance: the Legends contact
  awards and spends Hero points.
- Mandatory array order is authored progression order, but an active save stores
  the stable task key. Reordering future display data cannot retarget a player.
- Dialogue remains in the contact plugin/service, with optional dialogue keys
  in data only after a localization contract exists. Do not copy the unused
  `taskInfoDialog` field.

Load-time/CI validation must reject:

- unknown/duplicate keys, rank codes, contact NPC IDs, family NPC IDs, or task
  references;
- nonattackable targets or a family with no active MyWorld static spawn;
- duplicate NPC IDs across different families (reuse one family instead);
- zero/negative kills, points, weights, or counts beyond configured safe caps;
- a mandatory chain whose required/awarded ranks do not form the exact ladder;
- duplicate mandatory/repeatable task keys or empty task pools;
- any material/certificate turn-in field or any finished dragon-equipment item
  reward field;
- an unknown/duplicate shop, category, or reward key, unknown item ID,
  nonpositive item amount, empty/negative/overflowing cost vector, or cost in a
  challenge above the shop's challenge tier;
- a Fledgling reward with any component other than a positive Fledgling cost,
  or any higher-shop reward that does not contain exactly positive native and
  immediately-lower challenge components;
- an inventory-capacity upgrade with any lower-tier component, a cost not
  greater than its own mandatory-chain currency total, or a missing preceding
  capacity-upgrade prerequisite.

### Current Foundation Family Inventory And Tuning

The table in this subsection records the merged foundation baseline. Every
mandatory roster and its aggregate totals is superseded by the confirmed
35-task ladder above and requires a later implementation sync. Its randomized
pools also require redesign wherever they conflict with the confirmed monster
taxonomy; none of the foundation repeatable pools is approved merely by being
listed here.

Spawn counts below are active location records for the current MyWorld load set:
base `NpcLocs.json`, enabled discontinued/mod-room/runecraft/auction/harvesting/
custom-quest/other files, `MyWorldNpcLocs.json`, tutorial cleanup, and explicit
MyWorld removals. They are validation evidence, not a promise that every spawn
is equally accessible. All listed IDs resolve, are attackable, and have at least
one active static spawn.

| Contact | Stable task/family | Valid NPC IDs | Active spawns | Mandatory kills | Repeatable kills | Native challenge points |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| Falador | `falador.rats` / `rat` | `19,29,47,177` | 170 | 100 | 75 | 5 |
| Falador | `falador.goblins` / `goblin` | `4,62,153,154,660` | 198 | 100 | 75 | 5 |
| Falador | `falador.cows` / `cow` | `6` | 41 | 100 | 75 | 5 |
| Falador | `falador.dwarves` / `dwarf` | `94,699` | 28 | 100 | 75 | 5 |
| Falador | `falador.dark_wizards` / `dark_wizard` | `57,60` | 33 | 100 | 75 | 5 |
| Port Sarim | `port_sarim.pirates` / `pirate` | `137,264` | 25 | 125 | 100 | 8 |
| Port Sarim | `port_sarim.muggers` / `mugger` | `21` | 7 | 50 | 40 | 8 |
| Port Sarim | `port_sarim.skeletons` / `skeleton` | `40,45,46,179,195` | 175 | 150 | 100 | 8 |
| Port Sarim | `port_sarim.zombies` / `zombie` | `41,52,68,180,214` | 72 | 150 | 100 | 8 |
| Port Sarim | `port_sarim.hobgoblins` / `hobgoblin` | `67,311` | 51 | 125 | 100 | 8 |
| Brimhaven | `brimhaven.jungle_spiders` / `jungle_spider` | `521` | 70 | 200 | 125 | 12 |
| Brimhaven | `brimhaven.scorpions` / `scorpion` | `70` | 36 | 150 | 100 | 12 |
| Brimhaven | `brimhaven.jogres` / `jogre` | `523` | 21 | 150 | 100 | 12 |
| Brimhaven | `brimhaven.moss_giants` / `moss_giant` | `104,594` | 17 | 200 | 100 | 12 |
| Brimhaven | `brimhaven.lesser_demons` / `lesser_demon` | `22,181` | 37 | 150 | 75 | 12 |
| Champions | `champions.giants` / `giant` | `61` | 24 | 250 | 150 | 18 |
| Champions | `champions.dark_warriors` / `dark_warrior` | `199` | 13 | 200 | 125 | 18 |
| Champions | `champions.black_knights` / `black_knight` | `66,108,189` | 45 | 250 | 150 | 18 |
| Champions | `champions.ogres` / `ogre` | `312,525,706` | 67 | 250 | 150 | 18 |
| Champions | `champions.greater_demons` / `greater_demon` | `184` | 16 | 150 | 75 | 18 |
| Heroes | `heroes.baby_blue_dragons` / `baby_blue_dragon` | `203` | 11 | 150 | 75 | 25 |
| Heroes | `heroes.blue_dragons` / `blue_dragon` | `202` | 6 | 100 | 50 | 25 |
| Heroes | `heroes.shadow_warriors` / `shadow_warrior` | `787` | 8 | 150 | 100 | 25 |
| Heroes | `heroes.paladins` / `paladin` | `323,632,633` | 11 | 150 | 100 | 25 |
| Heroes | `heroes.otherworldly_beings` / `otherworldly_being` | `298` | 7 | 150 | 100 | 25 |
| Heroes | `heroes.hellhounds` / `hellhound` | `294` | 9 | 150 | 100 | 25 |
| Legends | `legends.death_wings` / `death_wing` | `768` | 8 | 150 | 75 | 35 |
| Legends | `legends.fire_giants` / `fire_giant` | `344` | 9 | 250 | 125 | 35 |
| Legends | `legends.greater_demons` / `greater_demon` | `184` | 16 | 250 | 125 | 35 |
| Legends | `legends.red_dragons` / `red_dragon` | `201` | 7 | 125 | 50 | 35 |
| Legends | `legends.black_demons` / `black_demon` | `290` | 13 | 250 | 125 | 35 |
| Legends | `legends.black_dragons` / `black_dragon` | `291` | 4 | 100 | 40 | 35 |
| Legends | `legends.king_black_dragon` / `king_black_dragon` | `477` | 1 | 1 | not random | 50 |

Merged foundation totals, retained as synchronization evidence:

| Band/challenge currency | Mandatory tasks | Mandatory kills | Native currency awarded | Random repeatable pool |
| --- | ---: | ---: | ---: | ---: |
| Fledgling -> Initiate | 5 | 500 | 25 | 5 |
| Initiate -> Veteran | 5 | 600 | 40 | 5 |
| Veteran -> Elite | 5 | 850 | 60 | 5 |
| Elite -> Champion | 5 | 1,100 | 90 | 5 |
| Champion -> Hero | 6 | 850 | 150 | 6 |
| Hero -> Legend | 7 | 1,126 | 260 | 6; KBD remains a capstone only |
| **Vector total** | **33** | **5,026** | **Fledgling 25; Initiate 40; Veteran 60; Elite 90; Champion 150; Hero 260** | **32** |

This cuts the mandatory wall to about 12 percent of the old 40,906 kills while
keeping a substantial rank path. The six values form a balance vector, not a
625-point pool. Supply redemption and optional expensive rewards can extend the
lifetime hunt through repeatables without delaying shop access.

The confirmed target now contains 35 mandatory kill assignments. Its kill
total, six-component point-earnings vector, and repeatable count cannot be
restated as settled totals until each new task count, point award, and
randomized pool is approved.

Repeatable policy:

- Equal weight `1` at launch; family-specific counts in the table already
  account for density and difficulty.
- Assignment uses the contact whose mandatory chain is complete. A player may
  use any completed contact, regardless of higher rank.
- Cancelling an accepted repeatable costs half its native challenge reward
  rounded up from that same balance:
  Falador `3`, Port Sarim `4`, Brimhaven `6`, Champions `9`, Heroes `13`,
  Legends `18`. No negative balance and no free replacement if payment fails.
- Task blocks, free rerolls, streak bonuses, and boss randomization are deferred
  until their shop rewards are explicitly approved.

Legacy IDs intentionally excluded from the launch inventory include unspawned
variants (`473`, `583`, `694`, `50`, `359`, `710`), unattackable guards (`375`,
`376`), civilians/quest personalities, and misleading family additions such as
Firebird `252`, Wormbrain `192`, Greldo `109`, Melzar `182`, Gunthor `78`,
Salarin `567`, Colonel Radick `518`, named bandit leaders, Ogre citizen `704`,
Blessed Vermen `630`, target-practice zombies, and happy peasants. They remain
valid historical Odyssey targets but are unsafe defaults for the new guild.

## Player State Design

Use existing typed player-cache storage through one `MonsterSlayerState` owner.
Dialogue and kill handlers must not manipulate raw keys.

| Key | Type | Contract |
| --- | --- | --- |
| `monster_slayer_state_version` | Integer | Current state schema; starts at `1`. |
| `monster_slayer_intro_stage` | Integer | `0` not started, `1` beer requested, `2` beer completed. Stage 2 requires rank at least Fledgling. |
| `monster_slayer_rank` | Integer | Stable rank code `0..7`; never a display/string ordinal. |
| `monster_slayer_balance_<challenge>` | Long | Six keys: `fledgling`, `initiate`, `veteran`, `elite`, `champion`, and `hero`. They are the sole player-saved currency authority: independently nonnegative, checked on additions/deductions, capped at `2,000,000,000`, and never materialized as an inventory item. No scalar total is persisted or spendable. |
| `monster_slayer_active_task` | String | Stable task key; absent means no task. Contact/family/type derive from definitions. |
| `monster_slayer_active_kills` | Integer | Bounded `0..requiredKills`; absent/zero when no active task. |
| `monster_slayer_mandatory_<contact>` | Integer | Six keys storing the number of fixed tasks completed for that contact, bounded by that chain's data length. |
| `monster_slayer_tasks_completed` | Long | Lifetime mandatory plus repeatable completion statistic; no rank authority. |
| `monster_slayer_inventory_upgrades` | Integer | Stable six-bit entitlement mask for the ordered Fledgling through Hero shop upgrades. Capacity is derived from explicit shop-key bits and the fixed `1/1/1/2/2/3` increment table; no separate capacity cache is stored. |
| `monster_slayer_migration_version` | Integer | One-time Odyssey migration marker; version `1` for the rules below. |
| `monster_slayer_legacy_status` | Integer | `0` none, `1` partial, `2` completed-unclaimed, `3` completed-claimed. Preserves future commemorative eligibility. |
| `monster_slayer_legacy_prestige` | Integer | Nonnegative snapshot of `co_prestige`; historical statistic only. |

Invariants:

- Rank is monotonic. Point spending cannot lower rank.
- `MonsterSlayerChallenge` has the explicit stable order Fledgling through Hero;
  enum ordinal is not persistence or authorization. `MonsterSlayerBalances`
  exposes typed access and never treats the vector sum as currency.
- A rank requires every lower contact cursor to equal its chain length.
- The active mandatory task must be exactly the current contact cursor's stable
  key. A repeatable task requires that contact cursor to be complete.
- Missing active-task definitions, out-of-range cursors, negative values, or
  rank/cursor contradictions produce a bounded diagnostic and no mutation.
- Task completion updates progress, cursor/rank, points, lifetime count, and
  active-task clearing through one state-owner method on the game thread.
- Task completion credits only the active definition's contact challenge. It
  cannot credit a caller-selected, lower, or higher balance. Mandatory tasks
  credit normally even before their shop is unlocked; the balance merely cannot
  be spent until its shop gate is met.
- Inventory capacity is derived from the validated shop-upgrade entitlement
  mask and is bounded to `30..40`. Purchases are monotonic, one-time, and
  sequential by unlocked shop; rank alone never grants an upgrade. Each upgrade
  requires every preceding upgrade and spends only its own native challenge
  balance, never a lower balance.
- Multi-cost spending first validates the reward/shop tier, exact allowed
  component set, quantity, checked component multiplication, and the required
  available balances against an immutable snapshot. It computes the complete
  post-spend vector before
  writing any balance. Insufficient currency changes nothing.
- A successful deduction returns the exact typed cost vector as a one-use
  receipt. A later item-grant failure refunds every receipt component; callers
  cannot refund a caller-constructed or already-refunded vector.
- Completion is derived from `rank == LEGEND` and all six mandatory cursors;
  do not add a redundant completion boolean.
- Do not create `monster_slayer_prestige` in version 1. Old `co_prestige` counts
  full 40,906-kill Odyssey completions and is not equivalent to repeating the
  new rank path. A future prestige contract needs its own approved loop first.

## Safe Combat Odyssey Migration

Migration is lazy for accounts with legacy evidence and idempotent through
`monster_slayer_migration_version`. The foundation branch implements and tests
the pure conversion but does not invoke it on live players. The later activation
branch invokes it before Monster Slayer dialogue or kill credit can mutate new
state.

Never delete or rewrite `combat_odyssey`, `co_tier_progress`, or `co_prestige`.
They remain recovery evidence. Do not remove Biggum from inventory/bank in the
migration transaction.

### Classification

1. If migration version is already `1`, return without awarding anything.
2. If `co_prestige > 0`, classify `completed-claimed`, grant `Legend`, complete
   all mandatory cursors, and preserve the prestige count.
3. Otherwise, if a valid tier-13 active record has its sole KBD task marked or
   has a bounded kill count of at least one, classify `completed-unclaimed`,
   grant `Legend`, and complete all mandatory cursors.
4. Otherwise, a valid intro stage `1`/`2` or active colon record is `partial`.
5. Missing/zero intro state with no other evidence is `none`; initialize normal
   defaults without legacy credit.
6. Wrong cache types, malformed strings, unknown tier/task IDs, negative kills,
   impossible masks, or rank contradictions are quarantined. Log the key names
   and validation reason, not a whole cache dump; make no migration award and
   do not set the migration version until repaired.

### Rank And Chain Recognition

Do not map legacy tier ranges onto new ranks or task cursors. That would make
the independently authored ladder a disguised Odyssey translation.

| Legacy evidence | New rank | New chains marked complete |
| --- | --- | --- |
| Intro stage `1`/`2` or any valid partial active run | Fledgling | none |
| Completed-unclaimed or `co_prestige > 0` | Legend | all six |

Every migrated account, including a previously completed or prestiged Odyssey
account, begins with all six Monster Slayer challenge balances at `0`. Legacy
kill totals, active-task progress, prestige count, inventory, and bank contents
do not produce a currency conversion. This keeps the new economy legible and
means any player who wants supplies or capacity upgrades earns their balances
through the new task system.

The conversion records no new active task and initializes the six balance keys
to zero. It never grants old intermediate items, a final dragon item, material
credit, a recreated Odyssey reward, a currency conversion, or a new prestige
count.

### Cutover Rules

The later activation branch must be atomic in behavior even if delivered as a
focused commit:

- migrate before the first new dialogue/kill mutation;
- stop legacy kill advancement and tier assignment for migrated players;
- keep legacy keys/stat display readable and keep Biggum personality content;
- remove the hidden Radimus start/reward role only when the new Legends contact
  can serve migrated players;
- prevent both systems from crediting the same death;
- provide a staff inspection command/report before any repair command;
- back up the player database and test migration on a copy before live use.

## Challenge Shops And Rangers Guild Redemption Model

`RangersGuildPointsVendor` is the interaction and failure-handling reference,
not a reusable scalar-currency implementation. Its maintained flow is:

1. Show authored categories.
2. Let the player select an item within a category and a quantity.
3. Multiply cost and output with overflow checks.
4. Verify the scalar Rangers Guild balance.
5. Verify inventory capacity before spending.
6. Deduct points, grant the item, and refund points if the add unexpectedly
   fails.

Monster Slayer should preserve that order and user experience while replacing
the scalar assumptions:

- Categories should primarily organize useful combat supplies obtainable by
  normal skilling: weapons, armor, potions, food, ammunition, and related
  combat preparation. `Unique/Prestige` and `Task Utility` may be additional
  categories; shops are not restricted to unique items.
- Item definitions remain normal item definitions. The challenge-shop data
  supplies category, output amount, shop tier, and a typed cost vector.
- Quantity multiplies every cost component and output amount using checked
  arithmetic. Affordability requires every component, not the vector sum.
- A Fledgling reward costs only a positive Fledgling balance. A reward at every
  later tier `T` costs exactly positive native `T` and immediately-preceding
  `T - 1` balances. It must never reference an older lower currency, a higher
  currency, or a scalar total.
- Capacity is checked before deduction. Deduction validates and applies the
  whole vector atomically. If item grant fails, refund the exact one-use receipt
  vector before reporting failure.
- No balance exchange, automatic conversion, overpayment from a higher balance,
  or fallback to a lower balance is allowed.
- Each shop also exposes its one permanent inventory-capacity upgrade in a
  dedicated permanent-unlocks category. It uses the same typed vector-cost
  refund guarantees as an item reward, but costs only its own native currency,
  requires every preceding capacity upgrade, and is a one-time entitlement
  mutation rather than an inventory item. It therefore does not perform the
  ordinary free-slot check.

### Confirmed: Currency Presentation, Earnings, And Capacity Prices

Monster Slayer currency is held only in the six saved balances owned by
`MonsterSlayerState`; players never receive, trade, bank, drop, lose, or pick
up a physical coin item. A future UI pass should use one unique coin silhouette
recolored per challenge tier so the balances are visually distinct without
creating six item definitions or a second inventory authority.

Every task stores one positive `pointReward` on its definition and credits only
its contact's challenge balance on successful completion. The opening and all
later mandatory tasks earn their normal tier currency immediately, even when
the player has not yet unlocked that tier's shop. Repeatables use the same rule.
Point rewards are intentionally task-specific: a harder, more dangerous, or
less accessible family in a tier earns more than an easier family in that same
tier. No task awards a mix of challenge balances.

Each inventory capacity upgrade must cost slightly more native currency than
the total a player should earn by completing that contact's full mandatory
chain. Consequently, completing the main path unlocks the right to buy the
upgrade but does not usually fund it; the player must finish a small number of
repeatable tasks from that same contact. The implementing AI should propose
the initial task rewards and prices from task difficulty and this margin, then
adjust them after owner playtesting. CI must verify:

- `capacityPrice[contact] > mandatoryCurrencyTotal[contact]`;
- the margin is documented as a small repeatable-task requirement rather than
  an accidental grind; and
- the price vector contains exactly one positive component, the contact's
  native challenge currency.

### Confirmed: Initial Consumable Shop Stock

The initial rollout has one full three-dose potion from each of the three
Herblaw potion families and one crafted, non-fish, multi-stage food per shop.
These are ordinary existing items, not certificates, and are intentionally
useful alternatives to making the same supplies through Herblaw or Cooking.
Their inclusion is approved; point-cost vectors and any future secondary stock
remain a separate economy pass.

| Shop/contact | Native currency | Full potion stock | Food stock | Food total healing |
| --- | --- | --- | --- | ---: |
| Rising Sun/Falador | Fledgling | Brawn v1 `474`; Deftness v1 `489`; Insight v1 `569` | Meat pie `259` | 8 (two bites) |
| Port Sarim | Initiate | Brawn v2 `477`; Deftness v2 `492`; Insight v2 `963` | Apple pie `257` | 10 (two bites) |
| Brimhaven | Veteran | Brawn v3 `480`; Deftness v3 `495`; Insight v3 `1411` | Cake `330` | 12 (three slices) |
| Champions Guild | Elite | Brawn v4 `483`; Deftness v4 `498`; Insight v4 `1414` | Meat pizza `326` | 14 (two halves) |
| Heroes Guild | Champion | Brawn v5 `486`; Deftness v5 `566`; Insight v5 `1468` | Anchovie pizza `327` | 16 (two halves) |
| Legends Guild | Hero | Brawn v6 `3198`; Deftness v6 `3201`; Insight v6 `3204` | Pineapple pizza `750` | 20 (two halves) |

`Brawn` supports Melee, Mining, Smithing, Woodcutting, and Hits; `Deftness`
supports Ranged, Thieving, Crafting, Agility, and Fishing; and `Insight`
supports Magic, Runecraft, Summoning, Cooking, and Prayer. Each listed potion
is the existing full three-dose item. Food is deliberately ordered by total
healing in clean two-point steps from 8 through 20, and every choice is a
crafted pie, cake, or pizza that is consumed over multiple bites.

The default reward unit is one full potion or one whole food item. Each initial
consumable line starts with normal shop stock of `10` and uses the ordinary shop
restock behavior already used by standard stores. The existing quantity selector
may sell more than one unit only after the cost vector and output multiplication
are verified atomically. No line above authorizes an unbounded shop stock, a
certificate substitute, or a change to the underlying Cooking/Herblaw recipes.

The current production interface cannot represent this faithfully:
`ProductionSession` carries one scalar point value and each `ProductionRecipe`
has one scalar cost/enabled flag. Monster Slayer therefore needs either a
multi-cost reward display or a confirmation step that lists every required
balance and the player's corresponding balances. Do not put a summed number in
the existing scalar field or pretend the challenge currencies are
interchangeable. Redemption UI and protocol/presentation changes are explicitly
outside the foundation branch.

The old Odyssey rewards are neither default stock nor price anchors. Existing
combat supplies may be selected from current item definitions in a later stock
audit, but their skilling acquisition, market value, tradeability, certificate/
stack behavior, and output quantity must be reviewed before a cost vector is
authored.

### Explicit Owner Decisions For Unique Rewards

Useful supply stock does not require every unique decision to be resolved, but
the following choices are required before adding the affected unique:

| Decision | Recommended default | Why it is required |
| --- | --- | --- |
| Giant's Axe | If approved, place a new slow two-handed sidegrade in the Champion challenge shop; owner must choose stats, requirements, special behavior, tradeability, art, output amount, and its full cost vector. | No such current item or authoritative stat/cost line exists. |
| Legends chase reward | Choose the archetype first: weapon, non-armor accessory, reusable contract utility, or prestige cosmetic. Recommended: a monster-hunting utility/accessory, not armor. | Its combat value determines native Hero and lower-tier costs. |
| Legacy completion recognition | Give claimed and completed-unclaimed Odyssey players the same noncombat commemorative entitlement; decide title versus cosmetic item. | Migration preserves an entitlement class but must not improvise a reward. |
| Unique reclaim model | Decide tradeability, death behavior, duplicate ownership, and lost-item reclaim separately for each unique. | These are economy and duplication contracts, not UI details. |
| Convenience unlocks | Decide whether paid rerolls/task blocks become rewards or only the direct cancellation fee remains. Recommended launch: cancellation fee only. | Permanent blocks materially change assignment probability and state. |
| Unique cost vectors | Approve the native and immediately-lower components and quantity for each shop item; Fledgling uniques use only Fledgling currency. | The two-tier rule keeps the prior challenge relevant without creating arbitrary all-tier costs. |

Hard exclusions requiring no further decision: dragon armor and dragon skirts
are not rewards; material/certificate turn-ins are not progression; old item
rewards are not automatically shop stock; and challenge balances are never
exchangeable.

## Current Bounded Foundation Branch

Branch: `feat/monster-slayer-data-state-foundation`.

Implementation status: complete within the boundary below. The branch loads
and validates definitions during MyWorld startup, but no player state is read,
written, or migrated by runtime gameplay. Its state and migration APIs remain
pure/uninvoked foundations for later activation branches.

Scope:

- Add `MonsterSlayer.json` with schema version 1, the stable rank/contact/
  family/task keys, exact mandatory/repeatable counts, challenge ownership, and
  native point values above. The shop array remains empty in committed launch
  data until stock is separately approved.
- Add focused immutable definition types and `MonsterSlayerData` loader/
  validator. Load/validate the data in server startup, but expose no player
  dialogue, task assignment, kill credit, redemption, or reward behavior.
- Add `MonsterSlayerRank` with explicit numeric codes and parsing that does not
  depend on enum ordinal.
- Add `MonsterSlayerChallenge`, immutable six-component balances/costs, reward
  schema types, tier/cost validation, checked quantity multiplication, atomic
  affordability/deduction proposals, and exact one-use refund receipts. These
  are foundation APIs and compiled fixtures, not a redemption UI or live shop.
- Add `MonsterSlayerState` as the only raw-cache-key owner, including default,
  six typed balance keys, validation, bounded arithmetic, active-task
  invariants, and an in-memory snapshot/write API. Do not wire new state
  mutation into login or gameplay.
- Add a pure `CombatOdysseyMigration` converter implementing the rules above.
  It accepts a legacy snapshot plus validated Odyssey/Monster Slayer data and
  returns either a proposed rank/cursor/challenge-balance snapshot or a typed
  validation failure. It does not write a player cache in this branch.
- Document the old data/integration classes as compatibility sources. Do not
  edit their runtime behavior, the eight dialogue integrations, `Npc.killedBy`,
  rewards, items, configs, databases, or the public server.

Tests:

- JSON/schema fixture: unique stable keys, exact rank ladder, exact 33 mandatory
  tasks/5,026 kills, challenge vector `25/40/60/90/150/260`, 32 repeatables,
  positive bounds, resolvable and attackable NPC IDs, active spawn evidence,
  and no unsafe excluded IDs.
- Exclusion fixture: no material/certificate field, finished dragon-equipment
  ID, retired skirt, committed shop stock, or positional persisted task
  identity.
- Compiled data fixture: load the real JSON; resolve keys independent of array
  order; reject duplicates, missing families, bad ranks, nonattackable IDs,
  zero-spawn families, invalid counts, broken contact chains, task/contact
  challenge mismatches, unknown reward components, and costs above shop tier.
- Compiled vector-cost fixture: the `5 Fledgling / 3 Initiate / 1 Hero`
  example, quantity multiplication/overflow, all-component affordability,
  no-partial deduction, exact refund, double-refund rejection, balance caps,
  no scalar sum spending, and each shop-tier boundary.
- Compiled state fixture: defaults, every rank code, cursor/rank consistency,
  active mandatory/repeatable validation, each typed balance, multi-cost
  add/spend/refund bounds, completion derivation, cache round trip, missing
  keys, wrong types, and corrupt values. Assert the old scalar cache key is
  neither read nor written.
- Compiled migration fixture: no-state, intro stages, every tier boundary,
  partial current task, malformed masks/strings/types, completed-unclaimed
  KBD, claimed completion, active prestige repeat, zero initialization of all
  six balances for every migration class, no partial rank/cursor translation,
  legacy-key preservation, and repeated migration idempotence.
- Run the existing dragon-production/removal guard, NPC location cleanup test,
  full server build, plugin build (even though plugins should be unchanged),
  and changed-code static analysis.

Stop conditions:

- Stop if the foundation needs player-visible dialogue, login mutation, kill
  hooks, actual shop stock/items, redemption UI/protocol changes, reward
  balance, database schema changes, or edits to Combat Odyssey compatibility
  behavior.
- Stop on any need to infer completion from inventory, silently repair malformed
  legacy state, reuse positional IDs, or include material/dragon rewards.
- Hand off the tested data/state/vector-cost/migration foundation before
  starting the Falador introduction, redemption UI/stock, or
  contribution-aware kill-credit branch.

## Player-State Activation Slice

Branch: `feat/monster-slayer-player-state-activation`.

This slice wires `MonsterSlayerState` into the authoritative player cache load
lifecycle after the database cache rows have been decoded. It loads the
validated Monster Slayer data and immutable Combat Odyssey decoder data once at
world startup, then performs an idempotent one-time migration before any later
Slayer handler could mutate state.

- New accounts persist schema version, six typed zero balances, the explicit
  zero entitlement mask, and the other valid default state fields.
- Existing valid Odyssey states preserve their original cache keys and receive
  the documented rank/legacy-status recognition, but every Monster Slayer
  balance and capacity entitlement starts at zero.
- The capacity mask uses explicit contact-key bits (`falador` through
  `legends`) and derives only the future `30..40` capacity. This slice does
  not alter live inventory admission, packets, UI, or equipment offsets.
- A completed migration is read-only on reconnect. Malformed Slayer state,
  malformed Odyssey state, unknown entitlement bits, and non-prefix masks are
  quarantined with a bounded player-scoped diagnostic; their raw cache evidence
  is left untouched and no replacement state is written.
- No task assignment/completion, NPC kill credit, shop, currency presentation,
  dialogue, NPC/world placement, client/protocol behavior, or Combat Odyssey
  gameplay behavior is activated here.

Focused executable coverage is
`server/test/com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerPlayerStateCharacterization.java`,
run through `ant test_monster_slayer_player_state`. It covers new-account and
cache-round-trip defaults, reconnect no-write idempotence, all meaningful
legacy classifications, zero-balance migration, unrelated-key preservation,
unknown/non-prefix masks, malformed state, and the derived-capacity boundaries.
