# MyWorld Dev And Admin Commands

This is the working command reference for MyWorld development accounts. Commands are typed in-game with the `::` prefix, for example `::setstat 99 melee`.

## Dev Account

- `DevDuck` is set to group `1`, `Admin`, in `server/inc/sqlite/myworld_dev.db`.
- Admin also satisfies the server's moderator, developer, and event-command checks.
- If the character is already logged in when the database role is changed, log out and back in so the role is loaded again.

## Groups

- `0`: Owner
- `1`: Admin
- `2`: Super Moderator
- `3`: Moderator
- `5`: Developer
- `7`: Event
- `8`: Player Moderator
- `10`: User

Useful group command:

- `::setgroup [name] [group_id/group_name]`
- Aliases: `::setrank`, `::group`, `::rank`
- Example: `::setgroup DevDuck admin`

## Skill Level Commands

The server already has commands for setting every skill level.

- `::setstat [level]`
  - Sets all of your skills to the level.
- `::setstat [level] [skill]`
  - Sets one of your skills to the level.
- `::setstat [player] [level]`
  - Sets all skills for an online player.
- `::setstat [player] [level] [skill]`
  - Sets one skill for an online player.
- Aliases: `::stat`, `::stats`, `::setstats`

Examples:

- `::setstat 99 melee`
- `::setstat 99 defense`
- `::setstat DevDuck 99 enchanting`
- `::setstats DevDuck 99`

Supported skill names in the current MyWorld layout:

- `melee`
- `defense`
- `strength`
- `hits`
- `ranged`
- `prayer`
- `magic`
- `cooking`
- `woodcutting` or `woodcut`
- `fletching`
- `fishing`
- `firemaking`
- `crafting`
- `smithing`
- `mining`
- `herblaw`
- `agility`
- `thieving`
- `enchanting`
- `harvesting`

## Skill Experience Commands

- `::setxp [experience]`
  - Sets all of your skills to the experience value.
- `::setxp [experience] [skill]`
  - Sets one of your skills to the experience value.
- `::setxp [player] [experience]`
  - Sets all skills for an online player.
- `::setxp [player] [experience] [skill]`
  - Sets one skill for an online player.
- Aliases: `::xpstat`, `::xpstats`, `::setxpstat`, `::setxpstats`

Example:

- `::setxp DevDuck 13034431 melee`

## Current Stat Commands

These adjust the current temporary level rather than the max level or experience.

- `::currentstat [level]`
- `::currentstat [level] [skill]`
- `::currentstat [player] [level]`
- `::currentstat [player] [level] [skill]`
- Aliases: `::currentstats`, `::setcurrentstat`, `::setcurrentstats`, `::curstat`, `::curstats`, `::setcurstat`, `::setcurstats`

## Common Admin Commands

Inventory and items:

- `::item [item_id] [amount]`
- `::bankitem [item_id] [amount]`
- `::bitem [item_id] [amount]`
- `::addbank [item_id] [amount]`
- `::grounditem [item_id] [amount]`
- `::gitem [item_id] [amount]`
- `::gi [item_id] [amount]`
- `::ritem [item_id] [amount]`
- `::rbitem [item_id] [amount]`
- `::swapitem [from_item_id] [to_item_id]`
- `::certeditem [item_id] [amount]`
- `::noteditem [item_id] [amount]`
- `::runes`
  - Admin command. Adds `1000` of each rune to your inventory.
  - Aliases: `::allrunes`, `::giverunes`
- `::wipeinventory`
- `::wipeinv`
- `::clearinventory`
- `::clearinv`
- `::wipebank`
- `::clearbank`
- `::givetools`
- `::givemodtools`
- `::quickbank`

Player health and combat:

- `::heal`
- `::recharge`
- `::healprayer`
- `::healp`
- `::hp [player] [amount]`
- `::sethp [player] [amount]`
- `::hits [player] [amount]`
- `::sethits [player] [amount]`
- `::prayer [player] [amount]`
- `::setprayer [player] [amount]`
- `::kill [player]`
- `::damage [player] [amount]`
- `::dmg [player] [amount]`
- `::combatstyle`
- `::setcombatstyle [style]`

NPC and combat testing:

- `::spawnnpc [npc_id] [amount]`
- `::massnpc [npc_id] [amount]`
- `::smitenpc [npc_id/target] [amount]`
- `::damagenpc [npc_id/target] [amount]`
- `::dmgnpc [npc_id/target] [amount]`
- `::setnpcstats [npc_id] [attack] [defense] [strength] [hits]`
- `::getnpcstats [npc_id]`
- `::aggroall [radius]`
- `::aggronear [radius]`
- `::forceaggro [radius]`
- `::npcrangedlvl`
- `::npcfightevent`
- `::npcrangeevent`
- `::npcevent`
- `::stopnpcevent`
- `::getnpcevent`

Movement and visibility:

- `::teleport [x] [y]`
- `::tp [x] [y]`
- `::tele [x] [y]`
- `::goto [player]`
- `::tpto [player]`
- `::tpat [player]`
- `::return`
- `::blink`
- `::invisible`
- `::invis`
- `::invulnerable`
- `::invul`
- `::norender`
- `::renderself`
- `::groupteleport [player]`
- `::returngroup`

Server and diagnostics:

- `::saveall`
- `::restart`
- `::shutdown`
- `::update`
- `::reloadworld`
- `::reloadland`
- `::serverstats`
- `::coords`
- `::hash [username]`
- `::unhash [username_hash]`
- `::walktrace`
- `::npctrace`
- `::droptest`
- `::sound [sound_name]`

## Developer World Editing Commands

NPCs:

- `::createnpc [npc_id]`
- `::cnpc [npc_id]`
- `::radiusnpc [npc_id] [radius]`
- `::removenpc`
- `::rnpc`
- `::rpc`

Scenery and boundaries:

- `::createobject [object_id]`
- `::cobject [object_id]`
- `::addobject [object_id]`
- `::aobject [object_id]`
- `::createscenery [object_id]`
- `::cscenery [object_id]`
- `::addscenery [object_id]`
- `::ascenery [object_id]`
- `::removeobject`
- `::robject`
- `::removescenery`
- `::rscenery`
- `::createwallobject [boundary_id]`
- `::cwallobject [boundary_id]`
- `::addwallobject [boundary_id]`
- `::awallobject [boundary_id]`
- `::createboundary [boundary_id]`
- `::cboundary [boundary_id]`
- `::addboundary [boundary_id]`
- `::aboundary [boundary_id]`
- `::rotateobject`
- `::rotatescenery`

Inspection helpers:

- `::tile`
- `::debugregion`
- `::getappearance`
- `::boundarydemo`
- `::scenerydemo`
- `::cycleclothing`
- `::cyclescenery`
- `::filtertest`

## Existing Full Reference

The inherited OpenRSC command reference is preserved at `docs/inherited-openrsc/Commands.md`. That file is broader and older; this MyWorld reference is the practical list for the current dev account.
