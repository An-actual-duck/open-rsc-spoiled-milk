# The Monster Slayer's Guild Plan

Status: **definition/state/migration, authoritative task progression and kill
credit, the approved 35-task roster, all six contact routes and promotions,
NPC placement, player-facing multi-currency shops, and the dynamic 30-to-40
Slayer inventory-capacity contract are implemented; private-client validation
has passed; release integration remains pending manager approval**
Owner: An-actual-duck
Audit baseline: published `main` `4be5b9fc5` on 2026-07-16
Audit integration: merged into `main` as `8ec90a4d6`
Foundation design revision baseline: published `main` `368ff655e` on 2026-07-16

Related active plans:

- `docs/myworld/in-progress-work-plans/how-to-acquire-dragon-armor.md`
- `docs/myworld/in-progress-work-plans/dragon-gear-crafting-plan.md`

Source policy:

- This is the only official Monster Slayer implementation plan.

## Release-Readiness Owner Decisions (2026-08-11)

- The formal player-facing quest and guild name is **The Monster Slayer's
  Guild**. Rank proofs (stamp, sticker, button, badge, medal, and crest) are
  dialogue flavour only: they are not items, cosmetics, or another authority.
- Unique Slayer rewards are explicitly deferred; this pass does not add or
  design them.
- Satchel-upgrade prices are the doubled approved baseline: Fledgling `84`, Adept
  `148`, Veteran `138`, Elite `110`, Champion `268`, and Hero `282`. Each is
  exactly twice the prior `ceil(mandatory rewards * 1.10)` price, paid only in
  its own challenge currency.
- The Veteran headquarters is the **Blue Moon Inn, Varrock**. Its persisted
  contact/shop/task key remains `brimhaven`, including the established
  entitlement bit, solely for save compatibility; it must never be presented
  to players as the location name. Bran `848`, the associate `854`, and the
  ambient Veteran `860` occupy fixed Blue Moon Inn tiles `(122,524)`,
  `(120,524)`, and `(123,525)` respectively.
- Private validation has passed for the implemented contacts, typed shops,
  point UI, 30-to-40 inventory expansion, and ordered satchel purchases.
  Release integration remains a manager decision; no release is authorized by
  this plan update.
- `docs/myworld/rough-drafts/slayer-guild-rough-draft-plan.md` is superseded
  historical context, not an implementation source.
- Do not restore the rough draft's monster-drop/certificate turn-ins.
- Do not restore its dragon plate-leg, dragon-skirt, or other finished dragon
  armor rewards. Finished dragon equipment remains owned by the dragon gear
  crafting plan.

## Adept Terminology and Visual Progression (2026-08-11)

- **Adept** is the player-facing name for the second Monster Slayer rank and
  its point currency. Dialogue, shop text, NPC names and descriptions, UI, and
  developer command help must use `Adept` / `Adept points`.
- `INITIATE`, `initiate`, and rank code `2` remain the established internal
  enum, JSON, and player-cache identifiers. They are compatibility keys, not
  player-facing terminology. Definition parsing accepts both `adept` and the
  legacy `initiate` spelling; existing accounts therefore need no migration.
- The visual equipment ladder is a standing presentation contract: Fledgling
  uses bronze, Adept iron, Veteran steel, Elite mithril, Champion adamant, and
  Hero rune. Within a headquarters, the contact, associate, and ambient member
  must remain visibly distinct while retaining their role-specific equipment.

## Current Implementation Checkpoint (2026-08-12)

Published `main` now contains the complete typed definition and persistence
foundation, idempotent Combat Odyssey recognition, authoritative mandatory and
repeatable assignment/completion, contribution-aware NPC-death credit, all six
challenge balances, the approved 35-task roster, and the headless shop
transaction service. Contacts `846..850` plus reused guild Sir Radimus `785`,
associates `852..857`, and ambient members `858..860` are defined and placed.
The retired Orin slot `851` remains an unspawned compatibility definition only,
because removing that appended definition would renumber later custom NPCs.
The beer introduction, rank proof
checks, host-guild gates, promotion dialogue, warnings, active-task reporting,
random repeatables, and transaction/concurrency failure paths have focused
executable coverage.

The player-facing challenge-shop interface is implemented. Every NPC `852..857`
is presented as `Slayer shop associate`; their appearances, locations, and
dialogue personalities remain distinct, but Trade/Shop on any one of them opens
the same six-rank coin picker. Selecting a rank and pressing Next opens that
rank's existing typed-currency reward panel, and Back returns to the picker.
Reward stock is intentionally infinite, quantities multiply every component
safely, and the server owns multi-balance deduction and item-grant rollback.
Shop entry and redemption have no rank, promotion, or host-guild software gate:
the required typed balances are the authority. The selected-item footer uses
the tier-tinted coin presentation, tooltips, and the saved Keep open / Remember
last selected controls.

The six permanent capacity entitlements are active. The server derives a
player's authoritative capacity from the validated mask, the custom client
renders 40 positions in an 8x5 layout, and associate dialogue sells each
satchel upgrade only in order. The capacity receipt precedes the refreshed inventory;
accounts above 30 slots refuse an older client rather than truncating items.

## Product Contract

Monster Slayer is an independently authored distributed guild-standing system.
Combat Odyssey supplies inspiration, legacy flavor, and migration evidence;
its tiers, task order, rewards, and progression are not a Monster Slayer
blueprint and must not be translated one-to-one. Monster Slayer is not a
visible skill and does not award Slayer XP. Players advance through seven named
ranks through one continuous mandatory guild quest, complete deterministic
assignment chains at six contacts, and then use those contacts for repeatable
tasks and six typed-currency challenge shops. The opening assignment is deliberately
not a monster kill: it is the joke beer errand that starts the quest and awards
the first rank.

The intended tone and progression remain:

- An unstamped player brings a beer from the Rising Sun Barmaid to a dedicated
  Monster Slayer contact in the pub and receives a deliberately silly
  `Fledgling` hand stamp.
- Six fixed task chains advance the player through `Adept`, `Veteran`,
  `Elite`, `Champion`, `Hero`, and `Legend`.
- Completed contacts offer repeatable random kill tasks for that contact's
  typed challenge currency: Fledgling, Adept, Veteran, Elite, Champion, or
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
- Each of the six challenge tiers contains one permanent inventory-capacity
  upgrade. Buying all six grows the player's inventory from 30 slots to a
  8-by-5, 40-slot inventory; the upgrade is purchased rather than granted
  automatically. Its own tier coins and every preceding upgrade are required.
- Higher task contacts refuse assignments until the required rank and the host
  guild's normal access requirements are satisfied. Those task gates do not
  limit the universal reward-shop picker.
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
  one long The Monster Slayer's Guild quest, not six unrelated miniquests. Contact
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

### Confirmed: Fledgling Assignments And Adept Reveal

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
- advances the player from `Fledgling` to `Adept` and presents proof of the
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

The Adept promotion and sticker exchange is locked as:

- Contact: `Excellent work! You've done a fine job culling those monsters.`
- Player: `There seem to be just as many as before.`
- Contact: `Imagine how many there would be if you hadn't helped.`
- Contact: `For your efforts, I promote you to Adept. Hold still while I
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
| `Adept -> Veteran` | Giant Bat `43` (level 32) -> Deadly Red Spider `99` (36) -> King Scorpion `136` (36) -> White Wolf `248` (41) -> Ugthanki `653` (45) -> Animated Axe `295` (46) -> Jungle Spider `521` (47) -> Baby Blue Dragon `203` (50) -> Shadow Spider `343` (53) |
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
- Shadow Spiders drain Worship and Poison Spiders introduce poison. These are
  intentional mechanic steps at Adept and Veteran, respectively, and their
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

### Implemented roster synchronization (2026-08-10)

The approved initial kill-count and point-reward pass is now represented by the
35-task `MonsterSlayer.json` ladder: Fledgling `38` total points, Adept
`67`, Veteran `62`, Elite `50`, Champion `121`, and Hero `128`. These remain
playtest baseline values, not a permanent economy lock. Repeatables use equal
launch weight `1` and an injected random source; a per-contact preview is
reserved only long enough to make the warning and committed assignment
identical, without making an account's first choice predictable. Hazards are
typed definition metadata (desert heat, Wilderness, Worship drain, poison, and
dragon fire), never inferred from task-key text. `PRAYER_DRAIN` remains the
stable internal definition key for compatibility; only its player-facing skill
name is Worship.

Hazard warnings are short NPC dialogue lines rendered before task assignment.
Each hazard is its own line, in definition order; multiple hazards are never
joined with a colon, semicolon, or list punctuation:

- `PRAYER_DRAIN`: `You should expect Worship drain.`
- `DESERT_HEAT`: `You should prepare for the desert heat.`
- `WILDERNESS`: `You should know this work is in the Wilderness.`
- `POISON`: `You should bring an antidote for poison.`
- `DRAGON_FIRE`: `You should prepare for dragon fire.`

### Confirmed: Permanent Inventory-Capacity Shop Upgrades

Every challenge tier contains exactly one one-time inventory-capacity upgrade.
The upgrade is not an automatic rank reward: the player must purchase it with
that tier's approved native-challenge price. Initial prices and task rewards are implementation
estimates to be tuned through playtesting, not inherited from Odyssey data.

The accepted release baseline is Fledgling `84` (mandatory `38`), Adept
`148` (`67`), Veteran `138` (`62`), Elite `110` (`50`), Champion `268`
(`121`), and Hero `282` (`128`). Each is twice the previously approved
110%-rounded mandatory-task price, and every capacity price is native to its
own tier only.

The six increments and cumulative capacities are fixed:

| Shop/contact | Native challenge | Slots added | Resulting capacity |
| --- | --- | ---: | ---: |
| Rising Sun/Falador | Fledgling | 1 | 31 |
| Port Sarim | Adept | 1 | 32 |
| Blue Moon Inn, Varrock | Veteran | 1 | 33 |
| Champions Guild | Elite | 2 | 35 |
| Heroes Guild | Champion | 2 | 37 |
| Legends Guild | Hero | 3 | 40 |

The base capacity remains 30 and the final capacity is exactly 40. Upgrades are
independent permanent entitlements and must be purchased in tier order. They
are not items, cannot be traded, dropped,
lost on death, refunded, or purchased more than once. A purchase does not need
a free inventory slot. It atomically validates every preceding entitlement,
confirms that the corresponding upgrade is not already owned, deducts its native-currency
price, records the entitlement, and then refreshes the inventory UI. Failure
at any stage leaves both points and capacity unchanged. The entitlement is
strictly per player: each player may successfully buy each shop's upgrade once
and only once. The implementation may retain a normal visible/restocking shop
entry if that is the least intrusive integration, provided an already owning
player cannot buy it again and receives `You already have this.`

Persist the six purchases as a stable six-bit entitlement mask owned by
`MonsterSlayerState`, with bits mapped explicitly by stable shop key rather
than JSON order or enum ordinal. Derive capacity as `30 +` the sum of the
owned increments; do not persist a second mutable capacity value. Unknown bits
or a non-prefix purchase sequence are invalid state and must be diagnosed rather
than silently granting space. Rank and host-guild access are not entitlement
prerequisites.

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
3. Add the six typed-currency shops with their approved consumables and
   native-only one-time capacity purchases. The later graphical pass replaced
   location/rank entry gates and finite stock with a universal rank picker and
   infinite point-shop stock.
4. Deliver the dynamic 30-to-40 inventory protocol and client UI together,
   then test all capacity boundaries and inventory-bearing systems.
5. Add eleven new unique armored task/shop NPCs plus either the twelfth new
   Legends contact or the approved Radimus task-route rework, then add the
   three generic one-line bar members, dialogue, shortcuts, rank gates, and
   world placements. Finish with end-to-end task, promotion, shop, reconnect,
   and migration tests.

Playtesting follows each economy-bearing slice. Initial numerical estimates
are intentionally adjustable; the contracts for typed currency, infinite
reward stock, sequential one-time purchases, and capacity are not.

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

### Remaining Presentation Decisions

- Choose the formal quest name, quest-list presentation, journal text, and any
  quest-point treatment. Calling the mandatory path one quest settles its
  lifecycle, but not those presentation details.
- Decide whether the Adept sticker is dialogue-only rank flavor, a physical
  inventory item, or a displayable cosmetic. If it is an item, tradeability,
  death behavior, duplicate prevention, storage, reclaim, and whether it is
  consumed when displayed all require explicit contracts.
- Initial mandatory/repeatable rewards and consumable/native currency prices
  are implemented as playtest baselines. Tune them only from owner/player
  evidence while preserving typed currencies and cost-vector rules. The
  consumable lines, stock quantity `10`, restock amount `1`, and capacity
  upgrade rules are settled.

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

Monster Slayer does not change `KillNpcTrigger`, because many quests depend on
its existing final-blow semantics. Its later kill-credit branch uses this rule:

- Aggregate melee, ranged, magic, and owned-summon damage by player UUID.
- Consider only online, living, same-world/layer contributors within 16 tiles
  who have positive attributed damage and whose active task accepts that NPC.
- Credit exactly one such task holder: the largest total damage wins. A
  higher-damage player without a matching task is ignored for Slayer selection.
- Equal total damage resolves by the repository's stable UUID ordering (lowest
  UUID first), so contributor-map iteration cannot choose the winner.
- Match only the active task's validated family IDs.
- Do not grant points per kill; only advance the bounded active count.

This is a Monster Slayer rule, not authorization to change XP, loot, existing
quest credit, personal loot, party behavior, or general NPC kill ownership.

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
| 2 | Adept | Falador chain complete; Port Sarim available |
| 3 | Veteran | Port Sarim chain complete; Blue Moon Inn, Varrock available |
| 4 | Elite | Blue Moon Inn chain complete; Champions Guild available |
| 5 | Champion | Champions chain complete; Heroes Guild available |
| 6 | Hero | Heroes chain complete; Legends Guild available |
| 7 | Legend | Legends chain complete; Hero repeatables available |

Every Monster Slayer location has at least two distinct NPC roles: one task
giver and one nearby Slayer shop associate. The task giver owns only rank,
mandatory, and repeatable-task dialogue; every associate is a universal entry
to the same six-rank shop picker while retaining location-specific flavor and
satchel service. They must never be combined merely because a location reuses an
existing bartender or guild official. Every task giver and shop associate is a
newly authored, unique humanoid NPC, except that the Legends task giver reuses
Sir Radimus `785`, the original borrowed-system task giver. Their
appearance should visibly progress from simple early equipment to better armor
at the higher ranks. Existing bartenders, guildmasters, and quest NPCs retain
their original responsibilities and are not repurposed as Slayer contacts,
apart from the explicitly selected Radimus Slayer-route rework.

| Contact key | New task-giver location | Integration boundary |
| --- | --- | --- |
| `falador` | Rising Sun ground floor | Add a dedicated definition and spawn. The contact directs the player to Barmaid `142` for beer; do not replace or intercept the Barmaid's existing dialogue. |
| `port_sarim` | Rusty Anchor, near the existing bartender | Add a separate task giver without replacing drink or bar-crawl service. |
| `brimhaven` (legacy internal key) | Blue Moon Inn, Varrock, near the existing bartender | Bran and the Veteran associate are dedicated Slayer NPCs; preserve the inn's bartender and all normal service. |
| `champions` | Champions Guild, near Guildmaster `111` | Preserve Dragon Slayer and normal guild-access dialogue on the Guildmaster. |
| `heroes` | Heroes Guild, near Achetties `253` | Preserve Heroes Quest/cape behavior on Achetties; remove the old Odyssey tier transition only in the coordinated activation branch. |
| `legends` | Legends Guild, Sir Radimus `785` | Reuse guild Radimus as the selected Monster Slayer closer. His authentic Legends Quest reward/training route retains Talk-to ownership; after that work and any recoverable legacy Odyssey state are resolved, it delegates to Monster Slayer. No new Odyssey progression is activated. House Radimus `735` remains quest-only. |

The `Task` shortcut is a MyWorld-only primary NPC command override authored in
`tools/generators/npc-overrides/50-interaction-commands.json`. Both client and
server must resolve that primary opcode as `Task` for guild Radimus `785`;
house Radimus `735` keeps an empty command. Combat Odyssey and the authentic
Legends Quest handler retain Talk-to/use-item responsibilities and do not own
`OpNpcTrigger`, so shortcut dispatch goes only to `MonsterSlayerContacts`.

Initial activation content uses task givers `846..850`, reused Radimus `785`,
nearby associates `852..857`, and bar ambient members `858..860`. Slot `851`
is deliberately retained but unspawned so the append-only server definition
loader keeps all following IDs stable. The exact custom start tiles are
recorded in `MyWorldNpcLocs.json`; Radimus retains his authentic `NpcLocs.json`
placement at `514,535`.

### Presentation And Roaming Contract

The current Slayer presentation pass keeps the stable IDs and contact keys but
makes the roster legible at a glance. Every contact wears its tier's plate body
and plate legs, carries the matching sword and square shield, and has a distinct
head/hair/colour combination. Associates and ambient members use the same
bronze-to-rune body-and-weapon progression, but deliberately retain ordinary
legs and no shield so that they read as suppliers and members rather than task
givers. All appearance layers are existing worn-sprite IDs; these NPCs remain
non-attackable visual definitions only.

Every Slayer spawn has a unique clear start tile and a small non-zero indoor
roaming rectangle. The bounds are deliberately local to each headquarters and
avoid the known scenery anchors, doors, counters, stairs, and bartender routes
used by the previous fixed placements. Automated coverage checks unique starts,
non-zero bounds, lack of scenery intersections across checked-in scenery sets,
role-specific equipment layers, and non-attackable definitions. A private
walk-through remains the final confirmation that natural roaming stays within
each rendered room as maps evolve; it must cover the Rising Sun, Rusty Anchor,
Blue Moon Inn, Champions, Heroes, and Legends headquarters.

#### Fledgling Effective-Client Correction

The Rising Sun trio has one extra presentation constraint: NPC IDs `846`
(Hobart), `852` (Slayer shop associate), and `858` (Fledgling Monster
Slayer) are rendered from the desktop client's hard-coded NPC catalogue, not
from the server definition packet. Their server JSON and client catalogue must
therefore use the same layer order: head, shirt, pants, shield, weapon, hat,
body, legs, gloves, boots, amulet, cape. Hobart's proven bronze composition is
head `1`, bronze square shield `98`, bronze sword `48`, bronze plate body `28`,
and bronze plate legs `37`. The associate uses head `4`, bronze battleaxe
`109`, and plate body `28`; the ambient member uses head `7`, bronze mace
`116`, and plate body `28`. The latter two intentionally have no shield or
plate legs. These are client-supported animation layers, not guessed server
sprite fields.

Their Rising Sun roam rectangles are limited to the clear upper interior tiles
around `(318..321,545..546)`. Validation checks every tile in each rectangle
against authored scenery footprints and boundary anchors across the checked-in
map profiles. The bartender retains its existing route; private validation must
confirm the three members remain visually clear of its active movement path.

#### Veteran Blue Moon Correction

The Veteran trio is centered on the Blue Moon Inn's clear floor around
`(122,524)`. Bran `848` roams `(121..123,524)`, the Veteran associate `854`
roams `(120,524..525)`, and ambient Veteran `860` roams `(122..123,525)`.
Their starts are unique and their full bounds avoid checked-in scenery and
boundary footprints, as well as the existing bartender's start tile.

Both effective definition catalogs use proven steel animation identities.
Bran uses head `7`, steel plate body `29`, steel plate legs `38`, steel square
shield `99`, and steel sword `49`. The female associate uses head `3`, female
steel plate body `56`, ordinary legs `2`, and steel battleaxe `110`, with no
shield. The ambient Veteran uses head `5`, steel plate body `29`, ordinary legs
`2`, and steel mace `117`, with no shield. These distinct silhouettes preserve
the role hierarchy while keeping all three within the steel tier.

#### Champion Heroes Guild Interior Correction

The Heroes Guild sect remains exactly two Slayer NPCs: Sella `850` and the
Slayer shop associate `856`. Both use the visually confirmed interior around
`(369,436)`, rather than either former exterior placement around packed Y `1381`
or the separate southern room around Y `443`. Sella starts at `(369,436)` and
roams only `(369..370,435..436)`. The associate starts at `(370,437)` and roams
only `(370..371,437)`. These non-overlapping pockets remain clear of authored
scenery and boundaries, including the 2-by-3 staircase footprint beginning at
`(368,438)`. Helemos already roams across part of this interior, so both pockets
may share his broad room route while neither Slayer NPC reuses his start tile.

Both effective definition catalogs use proven adamant animation identities.
Sella is visually dominant in full adamant equipment: full helm `16`, female
plate body `58`, plate legs `40`, square shield `101`, and sword `51`. The
associate uses head `7`, adamant plate body `31`, ordinary legs `2`, and
adamant battleaxe `112`, with no shield. The client and server both present the
associate as `Slayer shop associate`.

Automated coverage verifies exact client/server sprite parity, two-NPC sect
staffing, separate starts and roaming pockets, containment around the visually
confirmed `(369,436)` interior anchor, no overlap with checked-in scenery or
boundaries, walkable authoritative terrain, connected paths from that interior
tile, and the existing Heroes Quest access gate. A private visual pass should
still confirm that the adamant silhouettes read clearly and the shared room
roaming with Helemos feels natural.

Higher contacts require both the previous Monster Slayer rank and their normal
host-guild access. Early conversation should explain which stamp is required
without bypassing Champions, Heroes, or Legends Guild entry requirements.

### Location Staffing And Ambient Members

The activation is implemented with one dedicated unique-humanoid
shop-associate beside each task giver. Their armor quality follows the same
rising-rank visual progression as the task givers. Contact IDs `846..850` and
`785`, associate IDs `852..857`, ambient IDs `858..860`, names, and start tiles
are authoritative in the base and Monster Slayer definitions and location
files. The Legends associate wears the proven female rune body and rune
battleaxe used by Achetties, paired with the ordinary legs layer used by the
other partial-armour associates. The semantic composition is head `3`, body
`59`, legs `2`, no shield, and battleaxe `113`; the previous `4,59,3` anatomy
ordering incorrectly placed body/head assets into the head/legs slots. This
keeps visible legs and rune armor while remaining distinct from Radimus.
Existing bartenders and guild officials retain their current roles; the new
associates do not replace ordinary drinks, guild access, quests, or training
dialogue.

The three bar locations should additionally receive one generic ambient member
each. These are deliberately non-authoritative, non-unique humanoid world-fill
NPCs: they have no `Task`, `Trade`, or `Shop` shortcut, grant no
task/currency/rank state, and provide one brief optional flavour line only.
The proposed initial roster is:

| Location | Ambient member display name | World role and voice |
| --- | --- | --- |
| Rising Sun / Falador | `Fledgling Monster Slayer` | An eager recruit comparing a fresh hand stamp and boasting about very small monsters. |
| Rusty Anchor / Port Sarim | `Adept Monster Slayer` | A practical hunter checking supplies and talking about keeping a task journal dry at sea. |
| Blue Moon Inn / Varrock | `Veteran Monster Slayer` | A scarred, guarded regular who acknowledges the work but avoids giving a contract. |

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
  current proof before assigning it. For Talk-to, rank eligibility is checked
  **after** the accepted response and proof request so an ineligible player
  still receives the full social refusal; `Task` right-click retains its early
  authoritative gate because it intentionally skips this conversation. The
  proof check is dialogue flavor; the server still validates rank, normal guild
  access, the one-active-task rule, and every mandatory/repeatable state
  transition.
- The contact-proof pattern is reusable across the ladder: every task giver
  asks for the preceding proof, and an ineligible player is directed according
  to the player's **actual current rank**, not merely to the giver immediately
  below the NPC they clicked. This safely sends a severely under-ranked player
  all the way back to their real progression contact. Mara
  specifically asks, `Are you here to slay monsters?` Her natural yes/no
  choices are spoken by the player; on yes she asks for the Adept sticker, and
  a player without one is directed to Hobart in Falador.
- After an eligible player has been asked for their current proof, they answer
  `Right here!` before the preview or assignment continues. An ineligible
  player instead says `Oh, I don't have one.` before the contact's direction
  to the preceding giver. The right-click task shortcut keeps skipping this
  social proof exchange.
- Once a player is eligible, the `Task` right-click option is a shortcut. It
  skips the greeting and the `Yes please / Not now` choice and begins at the
  contact's `Your next task is...` line. It must use the same authoritative
  assignment path as Talk-to. If the player is ineligible, the shortcut must
  not assign anything and renders the same rank-aware, personality-specific
  direction used by Talk-to.
- A player with an active task is shown its current objective and progress
  rather than being given a second task. A player who has finished an
  assignment is sent through completion/promotion handling before another is
  chosen. This preserves the existing one-active-task invariant across all six
  contacts.
- A pending promotion takes over the next interaction with its own task giver,
  including a right-click `Task` interaction: render and acknowledge the
  promotion first, then end that interaction. Do not show the ordinary
  greeting/menu or assign a repeatable in the same interaction. The persisted
  acknowledgement makes this a one-time takeover; later interactions return to
  the ordinary route.
- Assignment text uses the deterministic current entry from the mandatory
  chain, or a chosen family/count from that contact's repeatable pool:
  `Your next task is to slay <count> <family>.` Before an assignment whose
  roster notes poison, Worship drain, dragon fire, desert preparation, or
  Wilderness exposure, render each mandated risk warning as a separate NPC
  line before task state is written. The shortcut does not suppress a required
  warning. The internal `PRAYER_DRAIN` key remains unchanged.
- Each completed mandatory chain promotes the player at its own contact. Task
  completion earns that contact's challenge-point balance, but shop visibility
  is universal. The associate, not the task giver, opens the shop picker.
  This gives each location two clear roles and avoids overloading an existing
  bartender or guild NPC's normal service dialogue.
- The final Fledgling task has a distinct completion lead-in. Hobart confirms
  that it was the player's final Fledgling work, awards the **Adept** rank, and
  explicitly presents the official Adept sticker before reminding the player
  that his associate presents every shop and knows a thing or two about
  satchels.
- Talk-to remains short, associate-specific dialogue and offers the local tier's
  sequential satchel upgrade. Trade/Shop always opens the universal six-rank
  picker. Choosing a coin tier and pressing Next opens that tier's existing
  typed-currency panel; insufficient balances disable or reject a purchase
  without hiding future rewards. Back returns to the rank picker.

#### Rank-Aware Task Refusals

The authoritative current-rank destinations are:

| Current rank | Required progression contact |
| --- | --- |
| Unstamped or Fledgling | Hobart at the Rising Sun in Falador |
| Adept | Mara at the Rusty Anchor in Port Sarim |
| Veteran | Bran at the Blue Moon Inn in Varrock |
| Elite | Doran at the Champions Guild |
| Champion | Sella at the Heroes Guild |
| Hero or Legend | Sir Radimus at the Legends Guild |

Each contacted task giver inserts that destination into one short line in their
own voice:

- Hobart: `Not quite ready for my work yet. Go see [destination] first.`
- Mara: `You're not ready for my work yet. Find [destination] first.`
- Bran: `Not ready for Veteran work! Find [destination] first!`
- Doran: `Not yet! Report to [destination] first!`
- Sella: `Your path continues elsewhere. Seek [destination] before returning.`
- Radimus: `Your standing is insufficient. Continue under [destination].`

Normal Talk-to still shows the missing-proof player response before this line.
The right-click `Task` shortcut omits the social proof exchange but uses the
same authoritative destination and cannot assign a task. Rank refusal is
evaluated before the separate host-guild gate, so an under-ranked player always
receives useful progression direction; an otherwise eligible player must still
meet the Champions, Heroes, or Legends Guild's normal access requirements.

### Developer Test Preparation

`::completeranktasks` is developer-only. It prepares the mandatory chain for
the player's current rank/contact by clearing an incompatible active task and
setting that contact to its final unassigned mandatory task. It does **not**
promote the player, grant points, alter balances, or award skipped task
rewards. The developer must still speak to the appropriate contact, receive
the ordinary final assignment, and complete it through the normal kill-credit
path before the existing promotion dialogue appears. The command reports the
prepared rank and stable contact key; it safely refuses unstamped and Legend
states, which have no current mandatory contact.

### Rank Proofs And Universal Shops

The proof progression deliberately starts silly and becomes ceremonial. A
proof is rank flavor unless a later presentation decision explicitly makes it
an item or cosmetic; it is not a second authority beside the persisted rank.
There are six proof changes for the six promotions before the final standing:

| Player rank after promotion | Proof shown in dialogue | Currency progression reached |
| --- | --- | --- |
| Fledgling | hand stamp | Fledgling tasks begin |
| Adept | sticker | Fledgling repeatables available |
| Veteran | button | Adept repeatables available |
| Elite | badge | Veteran repeatables available |
| Champion | medal | Elite repeatables available |
| Hero | crest | Champion repeatables available |
| Legend | no additional trinket; the rank itself is the final recognition | Hero repeatables available |

The final `Legend` promotion intentionally does not add a seventh trinket. It
ends the escalating stamp/sticker/button/badge/medal/crest joke with the
Legends contact treating status as something demonstrated rather than worn.
Every associate presents all six shops. Purchases spend only the already-defined
typed Monster Slayer currency and retain exact point-vector validation
server-side; rank, promotion flags, and host-guild membership do not authorize
or block shop entry or redemption.

#### Fledgling Associate And Satchel Upgrade

The Fledgling associate's Trade/Shop route is available at every rank and opens
the universal picker. Talk-to begins with two short lines:

> `I can show you every Slayer shop.`
> `Or perhaps you'd like an upgrade to your satchel?`

The player asks `Can you upgrade my satchel?` The associate quotes the exact
authoritative price as `I can, but it'll cost you 84 fledgling coins.`, then
warns `I can only do one upgrade per satchel as well.` The spoken choices are
`Totally worth it.` and `No thanks.` Trade/Shop remains the only route that
opens the graphical reward store; Talk-to remains dialogue.

The server revalidates and atomically purchases the entitlement only after the
affirmative response. Insufficient funds produce `You don't have enough Slayer
coins to afford it.` Missing earlier entitlements produce `Sorry, you don't have
the required prior upgrades to get this one.` An already-owned entitlement
produces `Looks like I already did this upgrade.` On success the associate says `Okay, hold on while I
stitch this.`, pauses briefly, then says `Done! I'm sure you can fit at least
one more thing now.` The authoritative capacity packet/inventory refresh is
unchanged. All player-facing Slayer capacity dialogue uses **satchel**, not
backpack; established internal entitlement names remain compatibility details.

### Contact Dialogue Sheets

Names remain implementation choices. These sheets identify the voice, the
required branch points, and dialogue for the new unique contacts at each
location. A contact name may take light inspiration from a recognizable OSRS
Slayer giver, but that does not require a matching visual, personality, or
one-to-one adaptation. Bracketed text is runtime data, never player-controlled
text.

#### 1. Rising Sun Recruiter — Fledgling Stamp And Adept Sticker

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

**Fledgling completion / Adept promotion**

Use the already-confirmed sticker exchange verbatim, then add:

> Contact: `You've been earning Fledgling Slayer Points while you worked.`
> Contact: `My associate nearby can trade them for useful supplies. And if you
> fancy more points, I can always find more monsters needing culling.`

The nearby associate opens only after `Adept`; before then they use the
Fledgling row in the shop-gate table.

#### 2. Rusty Anchor Contact — Adept Sticker To Veteran Button

Mara is a gruff but kind working woman: practical, sturdy, modest, and quietly
supportive. Her voice should suggest someone accustomed to hard physical work,
without becoming cruel, theatrical, aristocratic, or excessively jokey.

**Normal task route**

> Mara: `Are you here to slay monsters?`
> Player: `Yes, I am.` / `No, not today.`
> Mara (if yes): `Let's see that Adept sticker.`
> Player (if eligible): `Right here!`
> Player (if ineligible): `Oh, I don't have one.`

On the first Port Sarim mandatory assignment only, authoritative cursor zero
and the absence of an active task add this welcome after `Right here!`:

> Mara: `Right, you must be the newest among the Adepts.`
> Mara: `Getting here means you can swing a sword.`
> Mara: `Better than a goblin can stab a spear.`
> Mara: `Glad to have you.`

The normal task preview, warning, and assignment then continue. An assigned
first task, every later mandatory cursor, and repeatable state suppress this
welcome. Later successfully accepted Mara assignments may use exactly one of
these bounded remarks:

- `Steady hands make lighter work.`
- `Take your time and do the job properly.`
- `Keep your footing. Strength is no use flat on your back.`
- `A hard day's work is still just a day. You'll manage.`
- `Pack what you need, and mind yourself out there.`

`Task` shortcut begins at `Your next task is...`; below-rank use says `You need
an Adept sticker first. Hobart in Falador can help.` It skips the social proof
exchange and the first-task welcome.

**Veteran promotion / shop reveal**

> Mara: `Well that was it, the last one.`
> Mara: `At this point I'd say you've proven yourself.`
> Mara: `I award you Veteran status.`
> Mara: `Please accept this button as proof of your rank.`
> Player: `I'm honored. Thank you.`
> Player: `But um...`
> Player: `Why does it say 'I heart PS'?`
> Mara: `To show your Port Sarim pride!`
> Player: `Right, of course.`

This is the standard pending-promotion interception: it replaces the next Mara
interaction, acknowledges only after every line renders, assigns no task in
that interaction, and does not repeat.

#### 3. Blue Moon Inn Contact — Veteran Button To Elite Badge

Tone: a self-styled tough hunter. His bluster falls away at the
promotion, revealing that he has seen what the next step costs.

**Normal task route**

> Contact: `Back for another hard job? The Blue Moon has seen worse.`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Button?`
> Player: `Right here!`

On the first Veteran mandatory assignment only, authoritative Blue Moon cursor
zero and the absence of an active task add:

> Bran: `Hah! A new Veteran!`
> Bran: `Veterans are the best of the best!`
> Bran: `Let's see if you can prove it.`

The normal `Your next task is to slay [count] [family].` line then follows.
An active assignment, every later mandatory cursor, and repeatable state
suppress this welcome. The `Task` shortcut remains concise and skips it.

Every later Bran assignment receives one bounded randomized remark after any
authoritative hazard warning and immediately before the unchanged assignment
line. Hazard-free work draws only from personality lines; these make no claims
about monster weaknesses, locations, drops, or required equipment:

> Bran: `Now that's work worthy of a Veteran!`
> Bran: `Ha! Show them why we're the best!`
> Bran: `Make it loud enough to hear from the Blue Moon!`
> Bran: `A proper hunt! I almost envy you!`
> Bran: `Go on! Give me something worth boasting about!`

Preparation advice is selected only from the assigned task's typed hazard
metadata. The current Veteran roster uses this for poison spiders, producing
one of:

> Bran: `Poison on this one! Bring an antidote and keep swinging!`
> Bran: `Pack an antidote! Be ready before the poison sets in!`

The same typed formatter has bounded lines for `DESERT_HEAT`, `WILDERNESS`,
`PRAYER_DRAIN` (presented as Worship drain), and `DRAGON_FIRE`, so a future
data-authored Veteran task cannot receive advice for a hazard it does not
declare. Bran's first-task welcome remains unchanged and receives no extra
random remark. Active-task reminders, refusal paths, and pending promotion
interception also receive none.

`Task` shortcut begins at the assignment and uses the shared rank-aware refusal
when the player is ineligible.

**Elite promotion / shop reveal**

> Bran: `Hah! You did it! Every last task!`
> Bran: `You've earned Elite rank.`
> Bran: `Take this badge.`
> Bran: `But listen.`
> Bran: `The fun is over now.`
> Bran: `Elite work begins inside the true guilds.`
> Bran: `Not everyone comes back from that work.`
> Bran: `And come back any time to slay more with`
> Bran: `The best of the best!`

This remains the persisted one-time promotion interception. Bran's boisterous
front briefly drops for the warning; the interaction ends without assigning a
new task, and later interactions return to the normal route.

#### 4. Champions Guild Contact — Elite Badge To Champion Medal

Tone: tough, boisterous, friendly, and jovial. Doran talks quickly enough to
interrupt even a grateful player, but remains reassuring rather than cruel.
Preserve Dragon Slayer and ordinary Champions Guild dialogue on the existing
Guildmaster before presenting this optional guild route.

The Champions sect intentionally has only two Slayer NPCs. Doran wears a full
mithril plate composition with mithril plate legs, weapon, and shield. The
Slayer shop associate wears a visibly distinct partial mithril outfit with a
weapon, ordinary legs, and no shield. This is the first of the three formal
guild headquarters and follows the confirmed visual progression from Veteran
steel into Elite mithril.

**Normal task route**

> Contact: `Ah! An Elite hunter. Here for a real challenge?`
> Player: `Yes please.` / `Not now.`
> Contact (if yes): `Badge, if you please!`
> Player: `Right here!`

On the first authoritative Champions mandatory assignment, Doran adds this
one-time typed exchange before assigning the task:

> Doran: `Welcome to your first true stint in a guild sect.`
> Doran: `You're part of the Champions now!`
> Player: `Than-`
> Doran: `You're welcome! Best not dilly-dally.`
> Doran: `Monsters won't be slaying themselves.`

Later mandatory and repeatable assignments select one short Doran-specific
remark from this bounded, hazard-free set:

- `Right! Keep steady, finish the job, and report back!`
- `Good! Straight to it, then straight back!`
- `Ha! A fine assignment for an Elite!`
- `No need for speeches! You know the work!`
- `Off you go! We'll celebrate when it's done!`

When authoritative task metadata declares a hazard, the selected remark is
instead drawn only from that hazard's bounded preparation lines. Advice may
mention desert heat, Wilderness work, Worship drain, poison antidotes, or
dragon-fire protection only when the corresponding typed hazard is present.
The existing natural hazard warning remains before this flavor and the final
authoritative `Your next task...` assignment line. This prevents personality
text from presenting invented mechanical claims as fact.

`Task` shortcut begins at the assignment and uses the shared rank-aware refusal
when the player is ineligible.

**Champion promotion / shop reveal**

> Doran: `'Grats on making it this far!`
> Doran: `I knew you had it in you.`
> Player: `Than-`
> Doran: `Best not keep the Heroes' sect waiting.`
> Doran: `You've earned Champion rank!`
> Doran: `And I present to you the latest and greatest.`
> Doran: `Monster Slayer Guild Medal!`
> Player: `...`
> Doran: `Well, aren't you going to say thank you?`
> Player: `Th-`
> Doran: `Off you go!`

The pending promotion continues to intercept and replace the next ordinary
interaction, acknowledges only after every typed step renders, assigns no new
task during that interaction, and never repeats after acknowledgement.

The Elite associate prepends this line to the usual universal-shop conversation:

> Associate: `Doran is a nice guy, but you can never get a word in edgewise.`

Task rank gates and all approved costs remain unchanged. Shop access is
universal, while satchel upgrades retain their ordered prior-upgrade rule.

#### 5. Heroes Guild Contact — Champion Medal To Hero Crest

Sella is an altruistic, grandiose hero. She frames Monster Slayer work around
protecting other people and inspires Champions to see each contract as service,
not sport. Preserve Heroes Quest and cape behavior before this route.

**Normal task route**

> Sella: `Do you stand ready to defend this world?`
> Player: `Yes please.` / `Not now.`
> Sella (if yes): `Your medal.`
> Player: `Right here!`

Before the first authoritative Heroes assignment only:

> Sella: `I see by your medal a true hero stands before me.`
> Sella: `But I'll put that medal to the test.`
> Sella: `A hero defends the people of this world.`
> Sella: `And to do that you need to defeat some mighty foes.`
> Sella: `I hope you're ready!`
> Player: `I've never been more ready!`

Later mandatory and repeatable assignments select one bounded Sella remark:

- `Stand firm. Every foe defeated leaves someone safer.`
- `Fight with courage, and remember who you fight for.`
- `Let the people of this world sleep easier tonight.`
- `A hero's strength is measured by whom they protect.`
- `Go boldly. The people of this world are counting on us.`

Definition-driven hazard warnings remain separate and precede assignment. The
usual authoritative `Your next task is to slay [count] [family].` line follows
the welcome or remark.

`Task` shortcut begins at the assignment and uses the shared rank-aware refusal
when the player is ineligible.

**Hero promotion / shop reveal**

Sella awards Hero rather than Legend; the Legends Guild contact retains the
subsequent Hero-to-Legend step.

> Sella: `You've fought and slain giants, dragons,`
> Sella: `And beasts from the depths of hell.`
> Sella: `You've done well to protect the world`
> Sella: `From all manner of evil.`
> Sella: `I grant you this crest and the rank of Hero.`
> Sella: `May your name carry the same weight.`
> Sella: `And your foes shudder when they hear it.`
> Player: `It's been an honor and I won't let you down.`

The Champion associate prepends `Sella sure knows how to inspire a person.` to
their usual unlocked supply and satchel conversation.

#### 6. Sir Radimus — Hero Crest To Legend

Sir Radimus `785` is the selected final task giver; Orin is retired. His voice
follows the established Legends material: formal, exacting, proud of the Guild
and its history, mildly pompous, and occasionally playful. He sees diplomacy,
judgment, and resolve as part of becoming a legend rather than treating combat
alone as sufficient. Preserve his Legends Quest behavior and any bounded legacy
Odyssey recovery, but never begin the Cabbage extended task quest for this
route. House Radimus `735` is never a Slayer contact.

**Normal task route**

> Radimus: `Have you come seeking a task worthy of a legend?`
> Player: `I have.` / `Not today.`
> Radimus (if yes): `Then let me see your Hero's crest.`
> Player: `Right here!`

Before the first authoritative Legends assignment only:

> Radimus: `Excellent. Your reputation has brought you far.`
> Radimus: `But reputation alone does not make a legend.`
> Radimus: `The Legends Guild remembers deeds, not promises.`
> Radimus: `Complete the trials I set before you.`
> Radimus: `And your name may yet earn its place in these halls.`
> Player: `I'm ready to make history.`

Later mandatory and repeatable assignments select one bounded Radimus remark:

- `Very well. Let us see whether your reputation is deserved.`
- `The Guild remembers deeds, not excuses.`
- `Do be proactive. Greatness rarely waits to be instructed.`
- `Another trial awaits. I trust you came prepared.`
- `History favors those who finish what they begin.`

Definition-driven hazard warnings remain separate and precede assignment. The
authoritative task line follows the welcome or remark.

`Task` shortcut begins at the assignment and uses the shared rank-aware refusal
when the player is ineligible.

**Legend completion / final open ending**

> Radimus: `Well done. Very well done.`
> Radimus: `You have overcome every trial set before you.`
> Radimus: `You have proven your strength, your resolve,`
> Radimus: `And your place among the greatest adventurers of this age.`
> Radimus: `You've completed your journey for now.`
> Radimus: `You've done well.`
> Player: `And what's my new rank?`
> Radimus: `And what use would you make of it?`
> Player: `Whatever is required of me.`
> Radimus: `A suitable answer.`
> Radimus: `Then rise as a Legend of the Monster Slayer Guild.`
> Player: `It's an honor.`
> Radimus: `See that it remains one.`

After this exchange, the nearby rune-clad associate continues to offer the
universal rank picker and the local Hero-tier satchel upgrade. Their greeting
recognizes Radimus's standards and offers supplies.
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
  and `HERO`; `INITIATE` is the legacy internal code whose player-facing name
  is Adept. There is no global or `LEGEND` point balance: the Legends contact
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
- an inventory-capacity upgrade with any lower-tier component, a cost other
  than twice `ceil(110% of its own mandatory-chain currency total)`, or a
  missing preceding capacity-upgrade prerequisite.

### Historical Foundation Family Inventory And Tuning

The table in this subsection records the superseded merged foundation baseline
for audit history only. The confirmed 35-task ladder is now synchronized in
`MonsterSlayer.json`; do not implement or restore the historical roster,
aggregate totals, or repeatable pools below.

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
| Blue Moon Inn, Varrock (legacy `brimhaven` key) | `brimhaven.jungle_spiders` / `jungle_spider` | `521` | 70 | 200 | 125 | 12 |
| Blue Moon Inn, Varrock (legacy `brimhaven` key) | `brimhaven.scorpions` / `scorpion` | `70` | 36 | 150 | 100 | 12 |
| Blue Moon Inn, Varrock (legacy `brimhaven` key) | `brimhaven.jogres` / `jogre` | `523` | 21 | 150 | 100 | 12 |
| Blue Moon Inn, Varrock (legacy `brimhaven` key) | `brimhaven.moss_giants` / `moss_giant` | `104,594` | 17 | 200 | 100 | 12 |
| Blue Moon Inn, Varrock (legacy `brimhaven` key) | `brimhaven.lesser_demons` / `lesser_demon` | `22,181` | 37 | 150 | 75 | 12 |
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
| Fledgling -> Adept | 5 | 500 | 25 | 5 |
| Adept -> Veteran | 5 | 600 | 40 | 5 |
| Veteran -> Elite | 5 | 850 | 60 | 5 |
| Elite -> Champion | 5 | 1,100 | 90 | 5 |
| Champion -> Hero | 6 | 850 | 150 | 6 |
| Hero -> Legend | 7 | 1,126 | 260 | 6; KBD remains a capstone only |
| **Vector total** | **33** | **5,026** | **Fledgling 25; Adept 40; Veteran 60; Elite 90; Champion 150; Hero 260** | **32** |

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
| `monster_slayer_balance_<challenge>` | Long | Six keys: `fledgling`, `initiate`, `veteran`, `elite`, `champion`, and `hero`. `initiate` is the legacy storage key displayed to players as Adept. They are the sole player-saved currency authority: independently nonnegative, checked on additions/deductions, capped at `2,000,000,000`, and never materialized as an inventory item. No scalar total is persisted or spendable. |
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
  credit normally and any existing typed balance may be spent through the
  universal shop picker.
- Inventory capacity is derived from the validated shop-upgrade entitlement
  mask and is bounded to `30..40`. Purchases are monotonic, one-time, and
  sequential by tier; rank neither gates nor grants an upgrade. Each upgrade
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
later mandatory tasks earn their normal tier currency immediately. Repeatables
use the same rule, and any earned balance can be spent without a separate shop
unlock flag.
Point rewards are intentionally task-specific: a harder, more dangerous, or
less accessible family in a tier earns more than an easier family in that same
tier. No task awards a mix of challenge balances.

Each inventory capacity upgrade costs exactly twice `ceil(110%)` of the native
currency a player earns from that contact's full mandatory chain.
Consequently, completing the main path unlocks the right to buy the upgrade
but does not fully fund it; the player must finish a small number of
repeatable tasks from that same contact. Definition loading and CI must verify:

- `capacityPrice[contact] == 2 * ceil(mandatoryCurrencyTotal[contact] * 1.10)`;
- the margin is documented as a small repeatable-task requirement rather than
  an accidental grind; and
- the price vector contains exactly one positive component, the contact's
  native challenge currency.

### Confirmed: Initial Shop Stock

The initial rollout has one full three-dose potion from each of the three
Herblaw potion families and one crafted, non-fish, multi-stage food per shop.
These are ordinary existing items, not certificates, and are intentionally
useful alternatives to making the same supplies through Herblaw or Cooking.
The Hero shop additionally sells dragon metal scrap as an approved high-value
Smithing material.

| Shop/contact | Native currency | Full potion stock | Food stock | Food total healing | Additional stock |
| --- | --- | --- | --- | ---: | --- |
| Rising Sun/Falador | Fledgling | Brawn v1 `474`; Deftness v1 `489`; Insight v1 `569` | Meat pie `259` | 8 (two bites) | — |
| Port Sarim | Adept | Brawn v2 `477`; Deftness v2 `492`; Insight v2 `963` | Apple pie `257` | 10 (two bites) | — |
| Blue Moon Inn, Varrock | Veteran | Brawn v3 `480`; Deftness v3 `495`; Insight v3 `1411` | Cake `330` | 12 (three slices) | — |
| Champions Guild | Elite | Brawn v4 `483`; Deftness v4 `498`; Insight v4 `1414` | Meat pizza `326` | 14 (two halves) | — |
| Heroes Guild | Champion | Brawn v5 `486`; Deftness v5 `566`; Insight v5 `1468` | Anchovie pizza `327` | 16 (two halves) | — |
| Legends Guild | Hero | Brawn v6 `3198`; Deftness v6 `3201`; Insight v6 `3204` | Pineapple pizza `750` | 20 (two halves) | Dragon metal scrap `3228` |

`Brawn` supports Melee, Mining, Smithing, Woodcutting, and Hits; `Deftness`
supports Ranged, Thieving, Crafting, Agility, and Fishing; and `Insight`
supports Magic, Runecraft, Summoning, Cooking, and Prayer. Each listed potion
is the existing full three-dose item. Food is deliberately ordered by total
healing in clean two-point steps from 8 through 20, and every choice is a
crafted pie, cake, or pizza that is consumed over multiple bites.

The default reward unit is one full potion, one whole food item, or one dragon
metal scrap. Slayer point-shop rewards use the implemented infinite-stock model
shared with the Rangers Guild point shop. The retained JSON stock fields are
compatibility metadata and do not impose ordinary-store depletion. Quantity
multiplication and every typed cost component remain atomically validated.

### Shop Slice Initial Prices (Playtest Baseline)

The first headless shop slice uses these deliberately conservative point prices:

- Potions cost native `2/3/4/5/7/9` points from Fledgling through Hero; each
  later potion also costs `1/2/3/4/5` immediately-lower-tier points.
- Food costs native `3/5/6/8/10/13` and immediately-lower `0/2/3/4/6/8`.
- One dragon metal scrap in the Hero shop costs `24` Champion and `32` Hero
  coins.
- Capacity entitlements cost native-only `84/148/138/110/268/282`. Against the
  implemented mandatory totals `38/67/62/50/121/128`, each is twice the prior
  110%-rounded baseline; repeatable-task tuning remains subject to playtest.

These are balance estimates rather than permanent economy promises. They are
stored as independent typed components and must be adjusted only after owner
playtesting; no scalar conversion or cross-tier substitution is permitted.

The production interface now carries typed point-shop details alongside each
recipe. It renders every required balance as its own tinted coin and amount;
it never sums or converts the six currencies.

### Implemented Player-Facing Challenge Shops

`Trade`/`Shop` on any associate `852..857` is a player-facing entry to the
universal six-rank picker. The existing validated definitions and
`MonsterSlayerShopService` remain the sole cost, balance, and grant authority.

The implemented interface:

- shows all six coin-tier choices without rank or host-guild software gates;
- shows the selected rank's rewards, output quantity,
  every required typed cost component, and the player's corresponding balance;
- supports checked quantities without summing or converting currencies;
- keeps reward stock infinite, matching the Rangers Guild point shop;
- makes cancellation and Back paths state-free;
- reports insufficient typed points, full inventory, invalid quantity, grant
  rollback, and corrupt Slayer state truthfully without spending points on
  failure; and
- uses the existing shop service's atomic redemption result as authority and
  refreshes presented balances after a successful purchase.

Satchel upgrades remain dialogue purchases rather than reward-grid entries.
They use the same authoritative service and are available without a rank flag,
but still require the exact native-tier price, every prior entitlement, and a
compatible expanded-inventory client.

Focused coverage must exercise all six associates, universal picker navigation,
rank-independent redemption and ordered satchel purchases, every
reward/cost vector, quantity `1` and a valid multi-buy, overflow/zero/negative
quantities, cancellation/Back, insufficient individual cost components,
full inventory, successful delivery, rollback,
unrelated-cache preservation, and duplicate/concurrent submissions. Compile
core and plugins and retain the existing Monster Slayer state, contact-route,
and combat transaction gates.

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
- Compiled vector-cost fixture: the `5 Fledgling / 3 Adept / 1 Hero`
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

## Task Progression Slice

Branch: `feat/monster-slayer-task-progression`.

`MonsterSlayerTaskService` is the narrow typed server boundary for future
dialogue and death lifecycle integrations. It persists only validated state
transitions through `MonsterSlayerState`; no plugin or caller accesses raw
Slayer cache keys.

- Mandatory assignment selects only the current contact cursor's deterministic
  task and requires the exact contact rank. Repeatables require that contact's
  completed mandatory chain and an explicit repeatable task key owned by the
  same contact.
- The NPC death lifecycle snapshots Slayer credit before XP settlement clears
  contribution evidence. It credits exactly one online, living, same-world/layer
  task holder within 16 tiles: the positive-damage matching-task contributor
  with the highest total melee/ranged/magic/owned-summon damage. Equal damage
  resolves by stable UUID ordering. A non-matching top-damage player never
  blocks an eligible task holder.
- Wrong family, no active task, invalid contact/rank, duplicate callbacks, and
  invalid repeatable selections leave cache state unchanged. Completion clears
  the task exactly once, increments the lifetime total, advances only the
  owning mandatory cursor/rank when appropriate, and credits only that task's
  native challenge balance.
- This remains intentionally headless: no NPC placement/dialogue, menu,
  reward shop, capacity purchase, inventory-size change, client packet, or UI
  behavior is exposed by this slice.
- Slayer credit is an optional fail-closed boundary inside NPC death handling.
  Every ordinary `RuntimeException` from Slayer state reading, proposal, or
  writing is contained (never `Error`), grants no progress, and is reported
  with a safe known or generic reason. A 256-entry access-ordered transient
  suppression window prevents repeated corrupt kills from spamming logs or
  retaining unbounded process memory.
- `MonsterSlayerState.write` validates the complete candidate before mutating
  cache and snapshots only the Slayer-owned keys. If an individual cache
  mutator fails, those keys are restored exactly while unrelated keys and
  quarantined raw evidence remain untouched; no partial completion, cursor, or
  balance can escape.
- Production death-path coverage executes `Npc.killedBy`/`processLegacyDeath`
  with corrupt and valid contributors in both contribution orders, plus
  balance-cap, lifetime-count-overflow, wrong-family, no-task, and duplicate
  cases. It proves the valid contributor progresses exactly once while failed
  contributors cannot interrupt XP, loot listeners, removal, or respawn.
- Focused state coverage also injects a mid-write cache failure and verifies
  exact owned-key rollback/unrelated-key preservation, then verifies bounded
  duplicate-diagnostic suppression.
