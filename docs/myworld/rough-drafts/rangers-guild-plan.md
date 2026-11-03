# Rangers Guild Plan

## Goal

Repurpose the mostly empty Seers Party Hall building at `494,467` into a real gameplay space: the Rangers Guild.

The guild should become the main late-game ranged hub, with:

- Ranged master and Ranged cape access.
- Standard ranged supply shop continuity for Varrock.
- Dragon ranged weapon and ammo vendors.
- High-end ranged ammo and equipment vendors.
- Bank access near the high-end vendors.
- A caged ranged-training basement with ammo-focused enemy drops and Rangers Guild points.

## Existing Space

Current building identity in code: `Seers Party Hall`.

Current bounds:

- Ground floor: `490,464` to `500,471`
- Upper floor: `490,1408` to `500,1415`

Current static contents:

- Ground floor:
  - `stairs` at `490,466`
  - barrels at `500,467` and `500,464`
  - fireplace at `496,471`
  - basement stairs at `499,469`
- Upper floor:
  - `stairs` at `490,1410`

Current non-static/event use:

- `Event.toggleSeersParty` can spawn a temporary party chest at `495,467` or `495,1411`.
- `SeersPartyChest` lets players add tradable items to that chest and drops them randomly in the party hall bounds.
- Telegrab is blocked on upstairs party-hall items.

Repurposing work should either remove this party-hall behavior from the area or move it elsewhere. Leaving the party chest behavior active would conflict with the new guild identity and could create odd item-drop behavior in a guild shop/training space.

## Access Requirement

Entry requirement: `66 Ranged`, matching the Wizard Guild style of access.

Current implementation:

- The south entrance uses closed double-door scenery at `495,463`.
- The doors briefly open, move the player through, and close behind them.
- The doors require level 66 Ranged to pass through.
- A basic `Ranger` (`NpcId.RANGERS_GUILD_RANGER`, id `840`) stands outside the front entrance at `497,463`.
- The Ranger has no shop commands, explains the guild, and tells under-level players they need 66 Ranged.

Player-facing rejection text should be direct, for example:

> You need level 66 Ranged to enter the Rangers Guild.

The Ranger can also explain that the guild contains the Ranged master, upstairs ranged vendors, and a basement training area where Ranged XP earns Rangers Guild points.

## Ground Floor

Purpose: normal guild floor, Ranged master, standard identity, and transition to upper floor/basement.

First-pass scenery layout:

- `490,464`: display shield, oriented west-wall style.
- `491,471`: bookcase, rotated 180 degrees from the first pass.
- `494,471`: wall bench near the fireplace side of the room.
- `494,468`: stool.
- `495,468`: small round table.
- `496,468`: stool.
- `499,469`: basement stairs down, rotated 180 degrees from the first pass; footprint occupies `499-500`.

Existing objects preserved:

- `490,466`: stairs up.
- `496,471`: fireplace.
- `500,467` and `500,464`: ale barrels.

Notes:

- The old party chest spawn tile at `495,467` is intentionally left clear until the party-hall behavior is removed or relocated.
- The stairs and nearby approach tiles are left open.

NPC changes:

- `Lowe, Ranged Master` (`NpcId.LOWE`, id `58`) is moved from Varrock to the Rangers Guild ground floor at `493,466`.
- Lowe keeps the Ranged cape path and explains the guild, basement training, Rangers Guild points, and upstairs vendors.
- Lowe no longer has shop/trade commands or right-click/ctrl-click trade shortcuts.

Varrock replacement:

- `Arlen` (`NpcId.LOWES_ARCHERY_SHOPKEEPER`, id `839`) replaces Lowe in the old Varrock archery shop at `115,515`.
- Arlen keeps Lowe's old archery shop stock and standard archery-shop dialogue.
- Arlen has right-click `Trade` and secondary `Shop` shortcuts when right-click trading is enabled.
- Arlen does not sell the Ranged cape.

Entrance:

- The south entrance now has closed double-door scenery at `495,463`.
- The doors require level 66 Ranged to pass through.

Current Lowe stock to preserve for the Varrock replacement:

- Tin through mithril arrows.
- Copper through mithril bolts.
- Tin through mithril arrow heads.
- Shortbows and longbows through yew plus current custom tiers in that shop.
- Crossbows through yew plus current custom tiers in that shop.

Scenery goals:

- Add archery targets, bow racks, arrow barrels, weapon racks, crates, guild signage, and simple training decor.
- Keep paths clear around stairs, entrance, and NPC interaction tiles.

## Upper Floor

Purpose: high-end vendors and bank access.

First-pass scenery layout:

- `496,1408`: archery target, rotated 180 degrees from the first pass.
- `498,1408`: archery target, rotated 180 degrees from the first pass.
- `493,1415`: future vendor counter.
- `496,1415`: future vendor counter.
- `499,1415`: bank chest.
- `500,1412` and `500,1413`: supply crates.

Existing objects preserved:

- `490,1410`: stairs down.

Notes:

- The old party chest spawn tile at `495,1411` is intentionally left clear until the party-hall behavior is removed or relocated.
- The upper floor is more shop/training themed than the ground floor.

Required objects:

- Bank chest.
- Stairs back down to ground floor.
- High-end ranged shop decor: target dummies, crates, bow/arrow racks, maybe a guild ledger/table.

## NPC Roles

Front Ranger:

- Basic NPC named `Ranger` (`NpcId.RANGERS_GUILD_RANGER`, id `840`), placed outside the guild entrance at `497,463`.
- The door logic uses him as the visible rejection speaker for under-level players.
- Stops under-level players and tells them they need 66 Ranged to enter.
- Gives basic information about the guild, including the Ranged master and basement training.
- Should not sell items.
- Should not replace Lowe's Ranged master/cape role.

Ranged master:

- Use Lowe unless a later design decision replaces him with a new NPC.
- He should be the guild's general guide.
- Dialogue should explain the basement training area, the Rangers Guild point system, and the upstairs vendors.
- He should continue selling the Ranged cape to eligible players.
- He should not open the regular Varrock archery shop after being moved to the guild.

Dragon ranged vendor:

- `Aeron` (`NpcId.RANGERS_GUILD_DRAGON_VENDOR`, id `841`) is a standard shop NPC upstairs at `493,1414`.
- He wears ordinary clothes with a green cape.
- Sells dragon ranged gear and dragon ranged ammo for coins.
- Uses normal shop/trade behavior and shortcuts.

Points vendor:

- `Talia` (`NpcId.RANGERS_GUILD_POINTS_VENDOR`, id `842`) is the unique vendor for the high-end ranged point shop.
- She is upstairs at `496,1414`.
- Should not have a `Trade` option because purchases use Rangers Guild points rather than coins.
- Primary action should be `Redeem`.
- `Redeem` is available through right-click and ctrl-click shortcuts.
- Talking to the vendor explains what Rangers Guild points are, how points are earned from Ranged XP in the basement, and how to redeem points for high-end ranged supplies.
- The custom redeem flow can show the player's current point total before purchase choices.

Vendor 1: Dragon ranged vendor

Known existing dragon ranged items:

- `DRAGON_ARROWS` (`1449`)
- `POISON_DRAGON_ARROWS` (`1450`)
- `DRAGON_BOLTS` (`1451`)
- `POISON_DRAGON_BOLTS` (`1452`)
- `DRAGON_CROSSBOW` (`1453`)
- `DRAGON_LONGBOW` (`1454`)

Notes:

- Current vendor: `Aeron`, upstairs in the Rangers Guild.
- This vendor is the first clear acquisition route for dragon ranged weapons and ammo.
- Dragon darts, throwing knives, and shuriken do not appear to currently exist in `ItemId`.
- If those are desired later, they should be separate item-creation work before adding them to this vendor.

Vendor 2: High-end ranged ammo and equipment vendor

This vendor should accept Rangers Guild points instead of coins. The intent is for the shop to be one of the only practical sources of high-end ranged equipment and ammo, with the basement training floor feeding the point loop.

Current vendor: `Talia`, using a custom `Redeem` interface rather than a normal coin shop.

Current UI direction:

- `Redeem` opens a custom production-style redemption interface rather than a normal shop.
- First page is a category picker: `Longbows`, `Shortbows`, `Crossbows`, `Throwing Knives`, `Darts`, `Arrows`, `Bolts`, and `Shuriken`.
- Selecting a category and pressing `Next` opens that category's item picker.
- Item pages show up to 10 icons and include the full normal non-dragon range for that category.
- Dragon ranged gear stays on the dragon ranged vendor and is not part of the points redemption UI.
- The redemption page shows hover text at the top, selected item details at the bottom, points owned, total point cost, and total items received.
- Quantity controls are `-100`, `-50`, `-1`, `+1`, `+50`, and `+100`.
- Purchases spend tracked Rangers Guild points directly. Talia does not have a physical inventory and players cannot sell items to her.
- Poison variants are intentionally left out of the first production-style UI pass so each category remains a single page. They can be added later as poison-specific categories or paged variants.

Current stock bands:

- Rune ranged ammo where it exists.
- Titan Steel ranged ammo and thrown weapons.
- Adamantite ranged ammo and thrown weapons.
- Orichalcum ranged ammo and thrown weapons.
- Magic, ebony, and blood bows/crossbows.

Candidate stock families:

- Arrows: rune, titan steel, orichalcum.
- Bolts: rune, titan steel, adamantite, orichalcum.
- Darts: rune, titan steel, adamantite, orichalcum.
- Throwing knives: rune, titan steel, adamantite, orichalcum.
- Shuriken: rune, titan steel, adamantite, orichalcum.
- Poisoned versions can be included if the design goal is convenience; otherwise keep poison application in Herblaw to preserve production value.

Initial balance:

- Points are awarded at `1` Rangers Guild point per `10` final Ranged XP earned in the basement.
- Hidden remainder progress is stored so partial XP is not lost between hits.
- Ammo and thrown weapon purchases are sold in bundles of `100`.
- Poison rune, titan, adamantite, and orichalcum ammo/thrown variants are included where item IDs exist.
- Dragon ranged items remain on the coin vendor and are not part of the points vendor.

### Rangers Guild Points

Purpose: reward actual Ranged training in the Rangers Guild and gate the high-end ranged vendor behind guild activity instead of raw coin wealth.

Working design:

- Do not create a physical currency item.
- Do not use the certificate visual; points are tracked on the player instead.
- Award points from Ranged XP earned while inside the Rangers Guild basement training area.
- Only Ranged XP should count. Melee, magic, hitpoints, crafting, quest XP, or other skill XP should not award Rangers Guild points.
- Award points from the final XP actually credited to the player after fatigue checks, XP multipliers, and party sharing. This keeps the point reward aligned with what the player truly earned.
- Stronger enemies naturally award more points because they generally award more Ranged XP.
- Store points persistently per player in cache key `rangers_guild_points`.
- Track hidden remainder progress in cache key `rangers_guild_point_remainder` so fractional progress is not lost.
- The high-end ranged ammo/equipment vendor should price stock in points rather than coins.
- Shop prices should create a reasonable trade rate so basement training steadily converts into high-end ranged supplies without bypassing normal progression too quickly.

Current implementation:

- `RangersGuildPoints.awardFromExperience` handles point awards.
- `Skills.addExperience` calls the point award hook using final credited XP.
- The valid basement point-award area is `484,3281` through `515,3310`.
- Only `Skill.RANGED` XP awards points.
- The vendor validates point balance and inventory space before spending points.

Suggested first-pass conversion:

- Start with a simple ratio, for example `1 point per 10 Ranged XP`, with hidden remainder tracking.
- Tune vendor prices after stock is finalized.

Shop implementation note:

- The existing normal shop flow removes `ItemId.COINS` directly when buying, so the points vendor does not use the standard shop buy handler.
- The current implementation uses the shared production interface and a custom server-side starter hook. The server validates point balance, inventory space, selected item, and quantity before spending points and adding the item.
- Higher-effort future option: generalize the normal shop system to support alternate per-player currencies. That would be cleaner for UI consistency, but it has broader risk because it touches shared shop purchase behavior.

Open balance decisions:

- Decide the display name for the points.
- Decide the exact XP-to-point conversion rate.
- Decide whether the dragon ranged vendor should also use points or remain a normal coin shop.
- Decide exact shop prices after the high-end vendor stock list is final.

## Basement Training Floor

Purpose: caged ranged-training space with ammo-oriented bonus drops and Rangers Guild point earning.

Current implemented layout:

- New lower level is in place and connected to the ground floor.
- Ground-floor stairs at `499,469` route to `499,3295`; the 2x3 stair footprint uses overlay `8` across `499-500` to read visually as a down-stair opening.
- Basement stairs at `498,3296` route back to `499,468`; the return-stair footprint occupies `498-499`.
- The central stair square is a solid grey overlay (`5`) with no ground texture, keeping it visually distinct from the cage floors.
- The current basement layout has two large cage areas and three smaller side areas, with no gates by design.

Current NPC setup:

| Enemy | NPC ID | Combat level | Count | Cage | Movement bounds |
|---|---:|---:|---:|---|---|
| Zombie | `68` | 32 | 12 | North large cage | `488,3282` to `509,3290` |
| Skeleton | `45` | 31 | 12 | South large cage | `488,3303` to `510,3309` |
| Chaos dwarf | `190` | 59 | 8 | Left merged side cage | `485,3287` to `492,3306` |
| Ice giant | `135` | 68 | 4 | Upper-right small cage | `506,3286` to `514,3295` |
| Lesser demon | `22` | 79 | 4 | Lower-right small cage | `506,3297` to `514,3306` |

Intended NPC/drop plan:

- Keep the current stock NPC IDs for the first implementation.
- Do not create cloned NPC variants unless later design requires unique names, stats, examine text, or behavior.
- Add an area-based Rangers Guild basement bonus-drop hook for extra ammo only.
- Preserve normal NPC drops. Basement ammo should be a bonus drop on top of the normal drop table, not a replacement.
- Award Rangers Guild points separately from drops, based on Ranged XP earned in the basement training area.
- Keep the bonus table focused on ranged supplies so the basement does not become a general money farm.

Intended first-pass ammo bonus drops:

| Enemy | Ammo band |
|---|---|
| Zombie | Bronze to steel |
| Skeleton | Iron to mithril |
| Chaos dwarf | Steel to adamantite |
| Ice giant | Steel to adamantite |
| Lesser demon | Mithril to orichalcum |

Technical requirement:

- Cages must prevent NPCs from reaching the player while still allowing ranged attacks through.
- If the chosen fence/wall boundaries block projectiles, add or choose projectile-friendly cage boundaries.
- Validate that arrows, bolts, darts, knives, shuriken, and thrown projectile visuals can pass through the cage boundaries.

## Seers Party Hall Cleanup

Since the area becomes the Rangers Guild:

- Rename area labels from `Seers Party Hall` to `Rangers Guild`.
- Remove or relocate the `toggleSeersParty` command behavior.
- Remove the Seers party chest item-use behavior from this area, unless the party hall is moved elsewhere.
- Remove the upstairs telegrab restriction if it only existed for the party chest.
- Update any messages that refer to the party hall.

## Current Implementation State

Already in place:

- First-pass ground-floor and upper-floor scenery.
- Basement terrain and stair routing.
- Current basement stock-NPC spawn setup.
- Closed 66 Ranged guild-door entrance.
- Front Ranger info/requirement NPC outside the entrance.
- Upstairs dragon ranged vendor, `Aeron`, with dragon longbow, dragon crossbow, dragon arrows, poison dragon arrows, dragon bolts, and poison dragon bolts.
- Lowe moved to the Rangers Guild as `Lowe, Ranged Master`.
- Arlen added to the old Varrock archery shop with Lowe's old shop stock and trade shortcuts.
- Layout regression coverage for the current basement terrain, stairs, scenery, NPC spawn counts, entrance doors, front Ranger, Aeron, and Lowe/Arlen split.

Still planned:

- Area identity cleanup from Seers Party Hall to Rangers Guild.
- Upper-floor points vendor with `Redeem` action and point-shop dialogue.
- Persistent Rangers Guild point tracking from Ranged XP earned in the basement.
- Basement ammo bonus-drop hook.
- Points-based high-end vendor pricing.
- Projectile clipping validation through the finished cage walls.

## Remaining Implementation Order

1. Repurpose area identity and remove party-hall conflicts.
2. Add upper-floor high-end ammo/equipment points vendor.
3. Add persistent Rangers Guild point tracking for Ranged XP earned in the basement.
4. Add basement bonus-drop hook for ammo.
5. Add points vendor `Redeem` action, right-click shortcut, ctrl-click shortcut, and point-shop dialogue.
6. Validate projectile clipping through cages.
7. Add tests for area naming, points shop behavior, point accrual, shortcuts, and drop-table wiring.

## Tests And Validation

Recommended checks:

- Lowe no longer appears in Varrock after the move.
- New Varrock shopkeeper appears and opens the old archery shop.
- Front Ranger appears outside the guild entrance.
- Front Ranger blocks players below 66 Ranged.
- Front Ranger gives basic guild information.
- Aeron appears upstairs and sells dragon ranged weapons and ammo.
- Aeron has right-click and ctrl-click shop shortcuts.
- Lowe appears in the Rangers Guild and still sells the Ranged cape to eligible players.
- Lowe explains the basement, Rangers Guild points, and upstairs vendors.
- Players below 66 Ranged cannot enter.
- Players with 66+ Ranged can enter.
- Upper-floor bank chest works.
- Dragon vendor opens the intended dragon ranged shop.
- High-end vendor opens the intended high-end ranged shop.
- High-end vendor requires Rangers Guild points rather than coins.
- Points vendor has `Redeem`, not `Trade`, as the visible shortcut action.
- Points vendor supports both right-click and ctrl-click `Redeem`.
- Points vendor dialogue explains how points are earned and redeemed.
- Basement stairs route correctly both directions.
- Caged enemies cannot path out to the player.
- Player ranged attacks can hit caged enemies.
- Scythe/shuriken style multi-target behavior does not hit summons or unintended NPCs through unrelated cages.
- Ammo drops use the intended tier bands.
- Ranged XP earned inside the basement awards Rangers Guild points.
- Ranged XP earned outside the basement does not award Rangers Guild points.
- Non-Ranged XP earned inside the basement does not award Rangers Guild points.
- Basement ammo bonus drops do not replace normal drops.

## Open Questions

- What should the new Varrock archery shopkeeper be named?
- What should the Rangers Guild points be called in player-facing text?
- What should the XP-to-point conversion rate be?
- Should the high-end vendor sell poisoned ammo directly?
- Should dragon darts, dragon throwing knives, or dragon shuriken be created later?
- Should the old Seers Party Hall event feature be removed completely or moved to another empty building?
