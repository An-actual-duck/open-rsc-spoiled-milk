#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WALK_REQUEST = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "WalkRequest.java"
ITEM_ACTION_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "ItemActionHandler.java"
SPELL_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"
ATTACK_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "AttackHandler.java"
NPC = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "Npc.java"
NPC_BEHAVIOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcBehavior.java"
PROJECTILE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ProjectileEvent.java"
MOB = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "Mob.java"
GROUND_ITEM = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "GroundItem.java"
DROP_TABLE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "DropTable.java"
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"
PVM_MELEE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "PvmMeleeEvent.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_contains(path: Path, needle: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle not in text:
        fail(f"{path.name} missing expected text: {needle}")


def require_not_contains(path: Path, needle: str) -> None:
    text = path.read_text(encoding="utf-8")
    if needle in text:
        fail(f"{path.name} still contains retired text: {needle}")


def main() -> None:
    require_not_contains(WALK_REQUEST, "You can't retreat during the first 3 rounds of combat")
    require_not_contains(SPELL_HANDLER, "Your opponent can't retreat during the first 3 rounds of combat")
    require_contains(ITEM_ACTION_HANDLER, "isCombatConsumableAction")
    require_contains(ITEM_ACTION_HANDLER, "final boolean combatConsumableAction = isCombatConsumableAction(item, command, player)")
    require_contains(ITEM_ACTION_HANDLER, "player.inCombat() && !combatConsumableAction")
    require_contains(ITEM_ACTION_HANDLER, "command.equalsIgnoreCase(\"drink\") || command.equalsIgnoreCase(\"eat\")")
    require_contains(ATTACK_HANDLER, "canRetargetNpcWhileInCombat")
    require_contains(ATTACK_HANDLER, "retargetingNpcWithRangedWhileInCombat")
    require_contains(ATTACK_HANDLER, "player.inCombat() && !retargetingNpcWhileInCombat")
    require_contains(MOB, "private void startPvmCombat(final Mob victim)")
    require_contains(MOB, "pvmMeleeEvent = new PvmMeleeEvent(getWorld(), this, victim);")
    require_contains(MOB, "public PvmMeleeEvent getPvmMeleeEvent()")
    require_contains(PVM_MELEE_EVENT, "class PvmMeleeEvent")
    require_contains(PVM_MELEE_EVENT, "public Mob getAttacker()")
    require_contains(PVM_MELEE_EVENT, "public Mob getTarget()")
    require_contains(NPC, "public Player getPreferredThreatTarget()")
    require_contains(NPC, "private Player getLowestCombatLevelThreat(final boolean requireMeleeRange)")
    require_contains(NPC, "requireMeleeRange && !player.withinRange(this, 1)")
    require_contains(NPC, "playerCombatLevel < bestCombatLevel")
    require_contains(NPC, "Player meleeRangeThreat = getLowestCombatLevelThreat(true);")
    require_contains(NPC, "return getLowestCombatLevelThreat(false);")
    require_contains(NPC, "public boolean tryTakeMeleeFocus(final Player challenger)")
    require_contains(NPC, "challenger.getCombatLevel() >= currentPlayerOpponent.getCombatLevel()")
    require_contains(NPC_BEHAVIOR, "Player preferredThreatTarget = npc.getPreferredThreatTarget();")
    require_contains(NPC_BEHAVIOR, "npc.startCombat(target);")
    require_contains(PROJECTILE_EVENT, "Player preferredThreatTarget = npc.getPreferredThreatTarget();")
    require_contains(ATTACK_HANDLER, "getPlayer().startCombat(npc);")
    require_contains(NPC, "private int getTotalDamageBy(final UUID id)")
    require_contains(NPC, "private Pair<UUID, Long> getTopDamageDealer(final Mob fallbackAttacker)")
    require_contains(NPC, "private ArrayList<UUID> getAllDamageDealerIds()")
    require_contains(NPC, "private void awardDamageShareXp")
    require_contains(NPC, "getDamageShareXp(totalCombatXP, damage)")
    require_contains(NPC, "getDamageShareXp(totalCombatXP * 4, damage)")
    require_contains(NPC, "player.incExp(Skill.MAGIC.id(), magicXpShare, true)")
    require_not_contains(NPC, "if (this.getWorld().getServer().getConfig().WANTS_KILL_STEALING && attacker.isPlayer())")
    require_contains(NPC, "private void clearPlayerPvmMeleeEvents()")
    require_contains(NPC, "return topDamageDealer;")
    require_contains(NPC, "clearPlayerPvmMeleeEvents();")
    require_contains(NPC, "private Map<Player, Double> getPersonalLootRecipients()")
    require_contains(NPC, "dropPersonalItems(personalLootRecipients, owner);")
    require_contains(NPC, "dropItems(entry.getKey(), entry.getValue(), true);")
    require_contains(NPC, "drops.rollPersonalLoot(owner, contributionScale)")
    require_contains(NPC, "groundItem.setAttribute(\"personalNpcDrop\", true);")
    require_contains(GROUND_ITEM, "getAttribute(\"personalNpcDrop\", false)")
    require_contains(GROUND_ITEM, "return player.getUsernameHash() == ownerUsernameHash;")
    require_contains(DROP_TABLE, "public ArrayList<Item> rollPersonalLoot(Player owner, double contributionScale)")
    require_contains(DROP_TABLE, "RARE_NORMAL_DROP_MAX_WEIGHT = 2")
    require_contains(DROP_TABLE, "RARE_NORMAL_DROP_IDS")
    require_contains(DROP_TABLE, "drop.table.isRare() && (suppressRareTables || !passesContributionGate(contributionScale))")
    require_contains(DROP_TABLE, "private static boolean isRareNormalDrop(Drop drop)")
    require_contains(SPELL_HANDLER, "retargetingNpcWhileInCombat")
    require_contains(SPELL_HANDLER, "player.getConfig().BLOCK_USE_MAGIC_IN_COMBAT && player.inCombat() && !retargetingNpcWhileInCombat")
    require_contains(RANGE_EVENT := ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeEvent.java", "player.inCombat() && !(target.isNpc()")
    require_contains(THROWING_EVENT := ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ThrowingEvent.java", "player.inCombat() && !(target.isNpc()")
    print("PASS: combat interaction gates validated")


if __name__ == "__main__":
    main()
