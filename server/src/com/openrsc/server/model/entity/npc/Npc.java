package com.openrsc.server.model.entity.npc;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.*;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Npc extends Mob {
	private static final double DEFAULT_MELEE_DEFENSE_MULTIPLIER = 1.0D;
	private static final double DEFAULT_RANGED_DEFENSE_MULTIPLIER = 0.5D;
	private static final double DEFAULT_MAGIC_DEFENSE_MULTIPLIER = 0.5D;

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private long healTimer = 0;
	private boolean shouldRespawn = true;
	private boolean isRespawning = false;
	private boolean executedAggroScript = false;
	private NpcBehavior npcBehavior;
	private ArrayList<NpcLootEvent> deathListeners = new ArrayList<NpcLootEvent>(1); // TODO: Should use a more generic class. Maybe PlayerKilledNpcListener, but that is in plugins jar.
	private static int[] removeHandledInPlugin = {
		NpcId.RAT_TUTORIAL.id(),
		NpcId.DELRITH.id(),
		NpcId.COUNT_DRAYNOR.id(),
		NpcId.CHRONOZON.id(),
		NpcId.SIR_MORDRED.id(),
		NpcId.LUCIEN_EDGE.id(),
		NpcId.BLACK_KNIGHT_TITAN.id(),
		NpcId.PETER_SKIPPIN.id(),
		NpcId.SPOOKIE.id(),
		NpcId.SCARIE.id()
	};

	/**
	 * The definition of this npc
	 */
	protected NPCDef def;
	/**
	 * The location of this npc
	 */
	private NPCLoc loc;

	/**
	 * Holds players that did damage with combat
	 */
	private Map<UUID, Pair<Integer, Long>> combatDamagers = new HashMap<UUID, Pair<Integer,Long>>();
	/**
	 * Holds players that did damage with mage
	 */
	private Map<UUID, Pair<Integer, Long>> mageDamagers = new HashMap<UUID, Pair<Integer,Long>>();
	/**
	 * Holds players that did damage with range
	 */
	private Map<UUID, Pair<Integer, Long>> rangeDamagers = new HashMap<UUID, Pair<Integer,Long>>();


	/**
	 * Tracking for timing out the multi menu if another player attempts to talk to an NPC locked in dialog
	 */
	private long multiTimeout = -1;

	/**
	 * Another player wants to access the NPC, and can't access it right now.
	 */
	private boolean playerWantsNpc = false;

	private Player interactingPlayer = null;

	public Npc(final World world, final int id, final int x, final int y) {
		this(world, new NPCLoc(id, x, y, x - 5, x + 5, y - 5, y + 5));
	}

	public Npc(final World world, final int id, final int x, final int y, final int radius) {
		this(world, new NPCLoc(id, x, y, x - radius, x + radius, y - radius, y + radius));
	}

	public Npc(final World world, final int id, final int startX, final int startY, final int minX, final int maxX, final int minY, final int maxY) {
		this(world, new NPCLoc(id, startX, startY, minX, maxX, minY, maxY));
	}

	public Npc(final World world, final NPCLoc loc) {
		super(world, EntityType.NPC);

		for (int i : Constants.UNDEAD_NPCS) {
			if (loc.getId() == i) {
				setAttribute("isUndead", true);
			}
		}
		for (int i : Constants.ARMOR_NPCS) {
			if (loc.getId() == i) {
				setAttribute("hasArmor", true);
			}
		}
		def = getWorld().getServer().getEntityHandler().getNpcDef(loc.getId());
		if (def == null) {
			throw new NullPointerException("NPC definition is invalid for NPC ID: " + loc.getId() + ", coordinates: " + "("
				+ loc.startX() + ", " + loc.startY() + ")");
		}
		this.loc = loc;
		this.setNpcBehavior(new NpcBehavior(this));
		super.setID(loc.getId());
		super.setLocation(Point.location(loc.startX(), loc.startY()), true);

		getSkills().setLevelTo(Skill.ATTACK.id(), def.getAtt());
		getSkills().setLevelTo(Skill.DEFENSE.id(), def.getDef());
		getSkills().setLevelTo(Skill.RANGED.id(), def.getRanged());
		getSkills().setLevelTo(Skill.STRENGTH.id(), def.getStr());
		getSkills().setLevelTo(Skill.HITS.id(), def.getHits());

		getWorld().getServer().getGameEventHandler().add(getStatRestorationEvent());
	}

	/**
	 * Adds combat damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addCombatDamage(final Player mob, final int damage) {
		if (combatDamagers.containsKey(mob.getUUID())) {
			combatDamagers.put(mob.getUUID(), Pair.of(combatDamagers.get(mob.getUUID()).getLeft() + damage, mob.getUsernameHash()));
		} else {
			combatDamagers.put(mob.getUUID(), Pair.of(damage, mob.getUsernameHash()));
		}
	}

	/**
	 * Adds mage damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addMageDamage(final Player mob, final int damage) {
		if (mageDamagers.containsKey(mob.getUUID())) {
			mageDamagers.put(mob.getUUID(), Pair.of(mageDamagers.get(mob.getUUID()).getLeft() + damage, mob.getUsernameHash()));
		} else {
			mageDamagers.put(mob.getUUID(), Pair.of(damage, mob.getUsernameHash()));
		}
	}

	/**
	 * Adds range damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addRangeDamage(final Player mob, final int damage) {
		if (rangeDamagers.containsKey(mob.getUUID())) {
			rangeDamagers.put(mob.getUUID(), Pair.of(rangeDamagers.get(mob.getUUID()).getLeft() + damage, mob.getUsernameHash()));
		} else {
			rangeDamagers.put(mob.getUUID(), Pair.of(damage, mob.getUsernameHash()));
		}
	}

	public void displayNpcTeleportBubble(final int x, final int y) {
		for (Object o : getViewArea().getPlayersInView()) {
			Player player = ((Player) o);
			ActionSender.sendTeleBubble(player, x, y, false);
		}
		setTeleporting(true);
	}

	public int getNPCCombatLevel() {
		return getDef().combatLevel;
	}

	/**
	 * Combat damage done by Mob ID
	 *
	 * @param ID uuid of mob
	 * @return Pair
	 */
	private Pair<Integer, Long> getCombatDamageInfoBy(final UUID ID) {
		if (!combatDamagers.containsKey(ID)) {
			return Pair.of(0, 0L);
		}
		int dmgDone = combatDamagers.get(ID).getLeft();
		return Pair.of(Math.min(dmgDone, this.getDef().getHits()), combatDamagers.get(ID).getRight());
	}

	/**
	 * Iterates over combatDamagers map and returns the keys
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getCombatDamagers() {
		return new ArrayList<UUID>(combatDamagers.keySet());
	}

	public int getCombatStyle() {
		return 0;
	}

	public NPCDef getDef() {
		return getWorld().getServer().getEntityHandler().getNpcDef(getID());
	}

	public NPCLoc getLoc() {
		return loc;
	}

	/**
	 * Mage damage done by Mob ID
	 *
	 * @param ID uuid of mob
	 * @return Pair
	 */
	private Pair<Integer, Long> getMageDamageInfoBy(final UUID ID) {
		if (!mageDamagers.containsKey(ID)) {
			return Pair.of(0, 0L);
		}
		int dmgDone = mageDamagers.get(ID).getLeft();
		return Pair.of(Math.min(dmgDone, this.getDef().getHits()), mageDamagers.get(ID).getRight());
	}

	/**
	 * Iterates over mageDamagers map and returns the keys
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getMageDamagers() {
		return new ArrayList<UUID>(mageDamagers.keySet());
	}

	/**
	 * Range damage done by Mob ID
	 *
	 * @param ID uuid of mob
	 * @return Pair
	 */
	private Pair<Integer, Long> getRangeDamageInfoBy(final UUID ID) {
		if (!rangeDamagers.containsKey(ID)) {
			return Pair.of(0, 0L);
		}
		int dmgDone = rangeDamagers.get(ID).getLeft();
		return Pair.of(Math.min(dmgDone, this.getDef().getHits()), rangeDamagers.get(ID).getRight());
	}

	/**
	 * Iterates over rangeDamagers map and returns the keys
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getRangeDamagers() {
		return new ArrayList<UUID>(rangeDamagers.keySet());
	}

	private int getTotalDamageBy(final UUID id) {
		return getCombatDamageInfoBy(id).getLeft()
			+ getRangeDamageInfoBy(id).getLeft()
			+ getMageDamageInfoBy(id).getLeft();
	}

	private long getUsernameHashForDamageOwner(final UUID id) {
		Pair<Integer, Long> combatInfo = getCombatDamageInfoBy(id);
		if (combatInfo.getLeft() > 0) {
			return combatInfo.getRight();
		}
		Pair<Integer, Long> rangeInfo = getRangeDamageInfoBy(id);
		if (rangeInfo.getLeft() > 0) {
			return rangeInfo.getRight();
		}
		return getMageDamageInfoBy(id).getRight();
	}

	private void clearPlayerPvmMeleeEvents() {
		for (UUID id : getCombatDamagers()) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player != null) {
				player.resetCombatEvent();
			}
		}
		for (UUID id : getRangeDamagers()) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player != null) {
				player.resetCombatEvent();
			}
		}
		for (UUID id : getMageDamagers()) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player != null) {
				player.resetCombatEvent();
			}
		}
	}

	private Pair<UUID, Long> getTopDamageDealer(final Mob fallbackAttacker) {
		UUID topDamageUuid = fallbackAttacker.getUUID();
		long topDamageHash = fallbackAttacker instanceof Player ? ((Player) fallbackAttacker).getUsernameHash() : 0L;
		int highestDamage = fallbackAttacker.isPlayer() ? getTotalDamageBy(fallbackAttacker.getUUID()) : 0;

		for (UUID id : getCombatDamagers()) {
			int totalDamage = getTotalDamageBy(id);
			if (totalDamage > highestDamage) {
				topDamageUuid = id;
				topDamageHash = getUsernameHashForDamageOwner(id);
				highestDamage = totalDamage;
			}
		}
		for (UUID id : getRangeDamagers()) {
			int totalDamage = getTotalDamageBy(id);
			if (totalDamage > highestDamage) {
				topDamageUuid = id;
				topDamageHash = getUsernameHashForDamageOwner(id);
				highestDamage = totalDamage;
			}
		}
		for (UUID id : getMageDamagers()) {
			int totalDamage = getTotalDamageBy(id);
			if (totalDamage > highestDamage) {
				topDamageUuid = id;
				topDamageHash = getUsernameHashForDamageOwner(id);
				highestDamage = totalDamage;
			}
		}

		return Pair.of(topDamageUuid, topDamageHash);
	}

	private Player selectPreferredThreat(final Player currentBest, final Map<UUID, Pair<Integer, Long>> damagers, final boolean requireMeleeRange) {
		Player bestPlayer = currentBest;
		int bestDamage = bestPlayer == null ? -1 : getTotalDamageBy(bestPlayer.getUUID());
		for (Map.Entry<UUID, Pair<Integer, Long>> entry : damagers.entrySet()) {
			Player player = getWorld().getPlayerByUUID(entry.getKey());
			if (player == null || player.isRemoved() || player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				continue;
			}
			if (!player.getLocation().inBounds(loc.minX() - 4, loc.minY() - 4, loc.maxX() + 4, loc.maxY() + 4)) {
				continue;
			}
			if (requireMeleeRange && !player.withinRange(this, 1)) {
				continue;
			}

			int playerCombatLevel = player.getCombatLevel();
			int bestCombatLevel = bestPlayer == null ? Integer.MAX_VALUE : bestPlayer.getCombatLevel();
			int damage = Math.min(getTotalDamageBy(entry.getKey()), getDef().getHits());
			if (bestPlayer == null
				|| playerCombatLevel < bestCombatLevel
				|| (playerCombatLevel == bestCombatLevel && damage > bestDamage)) {
				bestPlayer = player;
				bestDamage = damage;
			}
		}
		return bestPlayer;
	}

	private Player getLowestCombatLevelThreat(final boolean requireMeleeRange) {
		Player bestPlayer = null;
		bestPlayer = selectPreferredThreat(bestPlayer, combatDamagers, requireMeleeRange);
		bestPlayer = selectPreferredThreat(bestPlayer, rangeDamagers, requireMeleeRange);
		return selectPreferredThreat(bestPlayer, mageDamagers, requireMeleeRange);
	}

	public Player getPreferredThreatTarget() {
		Player meleeRangeThreat = getLowestCombatLevelThreat(true);
		if (meleeRangeThreat != null) {
			return meleeRangeThreat;
		}

		return getLowestCombatLevelThreat(false);
	}

	private Map<Player, Double> getPersonalLootRecipients() {
		Map<Player, Double> recipients = new LinkedHashMap<Player, Double>();
		addPersonalLootRecipients(recipients, getCombatDamagers());
		addPersonalLootRecipients(recipients, getRangeDamagers());
		addPersonalLootRecipients(recipients, getMageDamagers());
		return recipients;
	}

	private void addPersonalLootRecipients(final Map<Player, Double> recipients, final ArrayList<UUID> damagerIds) {
		for (UUID id : damagerIds) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player == null || player.isRemoved() || player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				continue;
			}
			int damage = getTotalDamageBy(id);
			if (damage <= 0) {
				continue;
			}

			double contributionScale = Math.min(1.0D, Math.max(0.05D, damage / (double) getDef().getHits()));
			if (!recipients.containsKey(player) || recipients.get(player) < contributionScale) {
				recipients.put(player, contributionScale);
			}
		}
	}

	public boolean tryTakeMeleeFocus(final Player challenger) {
		if (challenger == null || challenger.isRemoved() || challenger.getSkills().getLevel(Skill.HITS.id()) <= 0
			|| isRespawning() || isRemoved() || getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}

		if (getConfig().WANT_MYWORLD) {
			challenger.startCombat(this);
			return true;
		}

		if (!inCombat() || getOpponent() == null) {
			challenger.startCombat(this);
			return true;
		}

		Mob currentOpponent = getOpponent();
		if (!currentOpponent.isPlayer()) {
			return false;
		}

		Player currentPlayerOpponent = (Player) currentOpponent;
		if (currentPlayerOpponent.equals(challenger)) {
			return true;
		}

		if (challenger.getCombatLevel() >= currentPlayerOpponent.getCombatLevel()) {
			return false;
		}

		currentOpponent.resetCombatEvent();
		challenger.startCombat(this);
		return true;
	}

	public int getArmourPoints() {
		return 0;
	}

	@Override
	public int getMeleeOffense() {
		return getDef().getStr();
	}

	@Override
	public int getRangedOffense() {
		int profileOffense = NpcAttackStyleProfile.forNpc(this).getRangedOffense(this);
		if (profileOffense > 0) {
			return profileOffense;
		}
		return getDef().getRanged();
	}

	@Override
	public int getMagicOffense() {
		return NpcAttackStyleProfile.forNpc(this).getMagicOffense(this);
	}

	@Override
	public int getMeleeDefense() {
		int defense = getDef().getMeleeDefense() > 0
			? getDef().getMeleeDefense()
			: deriveDefense(getDef().getDef(), getDef().getMeleeDefenseMultiplier(), getDef().getMeleeDefenseDivisor(), DEFAULT_MELEE_DEFENSE_MULTIPLIER);
		return applyFireDefenseDebuffToValue(defense);
	}

	@Override
	public int getRangedDefense() {
		int defense = getDef().getRangedDefense() > 0
			? getDef().getRangedDefense()
			: deriveDefense(getDef().getDef(), getDef().getRangedDefenseMultiplier(), getDef().getRangedDefenseDivisor(), DEFAULT_RANGED_DEFENSE_MULTIPLIER);
		return applyFireDefenseDebuffToValue(defense);
	}

	@Override
	public int getMagicDefense() {
		int defense = getDef().getMagicDefense() > 0
			? getDef().getMagicDefense()
			: deriveDefense(getDef().getDef(), getDef().getMagicDefenseMultiplier(), getDef().getMagicDefenseDivisor(), DEFAULT_MAGIC_DEFENSE_MULTIPLIER);
		return applyFireDefenseDebuffToValue(defense);
	}

	@Override
	public double getDamageRollHighBiasChance() {
		return 0.0D;
	}

	@Override
	public double getArmorSpeedMultiplier() {
		return 1.0D;
	}

	private int deriveDefense(int legacyDefense, double configuredMultiplier, double legacyDivisor, double defaultMultiplier) {
		if (legacyDefense <= 0) {
			return 0;
		}
		double multiplier = configuredMultiplier >= 0.0D ? configuredMultiplier : -1.0D;
		if (multiplier < 0.0D && legacyDivisor > 0.0D) {
			multiplier = 1.0D / legacyDivisor;
		}
		if (multiplier < 0.0D) {
			multiplier = defaultMultiplier;
		}
		return Math.max(0, (int) Math.floor(legacyDefense * multiplier));
	}

	public int getWeaponAimPoints() {
		return 0;
	}

	public int getWeaponPowerPoints() {
		return 0;
	}

	public boolean stateIsInvisible() {
		return false;
	}

	public boolean stateIsInvulnerable() {
		return false;
	}

	@Override
	public void killedBy(Mob mob) {
		if (mob == null) {
			this.cure();
			deathListeners.clear();
			return;
		}
		if (this.killed) return;
		//this.killed = true; remove() assures everything went fine, and set killed to true

		Player owner = getWorld().getPlayerByUUID(mob.getUUID());
		if (owner == null) {
			Npc npcKiller = getWorld().getNpcByUUID(mob.getUUID());
			if (npcKiller != null && npcKiller.relatedMob instanceof Player)
				// owner is Npc with a related Player
				owner = (Player) npcKiller.relatedMob;
		}

		// Remove poison event(s)
		this.cure();

		if (owner == null) {
			deathListeners.clear();
			remove();
			return;
		}

		owner.getWorld().getServer().getPluginHandler().handlePlugin(KillNpcTrigger.class, owner, new Object[]{owner, this});
		for (int npcId : removeHandledInPlugin) {
			if (this.getID() == npcId) {
				if (this.getID() == NpcId.RAT_TUTORIAL.id()) {
					remove();
				}
				return;
			}
		}

		// Reset the player's range timer
		owner.setAttribute("can_range_again", getWorld().getServer().getCurrentTick());

		// Defense skillcape message
		int totalBlockedDamage = owner.getTrackedBlockedDamage(this);
		if (totalBlockedDamage > 0) {
			owner.playerServerMessage(MessageType.QUEST, "@dcy@Your defense cape blocked " + totalBlockedDamage + " damage!");
		}

		owner.setLastNpcKilledId(this.getID());

		Player killCreditOwner = owner;
		Map<Player, Double> personalLootRecipients = getPersonalLootRecipients();
		Pair<UUID, Long> ownerInfo = handleXpDistribution(mob);
		owner = getWorld().getPlayerByUUID(ownerInfo.getLeft());

		if (owner == null) {
			if (personalLootRecipients.isEmpty()) {
				if (killCreditOwner == null) {
					deathListeners.clear();
					remove();
					return;
				}
				owner = killCreditOwner;
			} else {
				owner = personalLootRecipients.keySet().iterator().next();
			}
		}

		ActionSender.sendSound(owner, "victory");
		owner.getWorld().getServer().getAchievementSystem().checkAndIncSlayNpcTasks(owner, this);
		owner.incNpcKills();

		//If NPC kill messages are enabled and the filter is enabled and the NPC is in the list of NPCs, display the messages,
		//otherwise we will display the message for all NPCs if NPC kill messages are enabled if there is no filter.
		//Also, if we don't have NPC kill logging enabled, we can't have NPC kill messages.
		if (getConfig().NPC_KILL_LOGGING) {
			logNpcKill(owner);
		}

		ActionSender.sendNpcKills(owner);

		/** Item Drops **/
		dropPersonalItems(personalLootRecipients, owner);

		for (NpcLootEvent e : deathListeners) {
			if (mob instanceof Player) {
				e.onLootNpcDeath((Player) mob, this);
			}
		}

		deathListeners.clear();
		remove();
	}

	private void logNpcKill(Player owner) {
		if (owner.getCache().hasKey("npc_kc_messages") && (owner.getCache().getBoolean("npc_kc_messages"))
			&& getConfig().NPC_KILL_MESSAGES) {
			owner.addNpcKill(this, !getConfig().NPC_KILL_MESSAGES_FILTER
				|| getConfig().NPC_KILL_MESSAGES_NPCs.contains(this.getDef().getName()));
		} else
			owner.addNpcKill(this, false);
	}

	public void dropItems(Player owner) {
		dropItems(owner, 1.0D, false);
	}

	private void dropPersonalItems(Map<Player, Double> recipients, Player fallbackOwner) {
		if (recipients.isEmpty() && fallbackOwner != null) {
			recipients.put(fallbackOwner, 1.0D);
		}

		for (Map.Entry<Player, Double> entry : recipients.entrySet()) {
			dropItems(entry.getKey(), entry.getValue(), true);
		}
	}

	private void dropItems(Player owner, double contributionScale, boolean personalDrop) {
		/* 1. Custom Rare Drops */
		if (getConfig().WANT_CUSTOM_SPRITES && !getConfig().WANT_OPENPK_POINTS) {
			if (this.getID() == NpcId.KING_BLACK_DRAGON.id()) {
				calculateCustomKingBlackDragonDrop(owner, contributionScale, personalDrop); // Custom KDB Specific RDT
			}
		}

		/* 2. Drop bones (or nothing). */
		int bones = getBonesDrop();
		if (bones != ItemId.NOTHING.id() && !Summoning.tryAutoBuryDrop(owner, bones, 1)) {
			final ItemDefinition boneDef = getWorld().getServer().getEntityHandler().getItemDef(bones);
			if (boneDef == null || boneDef.isStackable()
				|| !owner.getCarriedItems().getEquipment().tryBankMonsterLootWithLawNecklace(new Item(bones, 1))) {
				GroundItem groundItem = createNpcGroundItem(owner, bones, 1, false, personalDrop);
				getWorld().registerItem(groundItem);
			}
		}

		/* 3. Get the rest of the mob's drops. */
		DropTable drops = getWorld().npcDrops.getDropTable(this.getID());
		if (drops == null) {
			// Some enemies have no drops
			return;
		}
		drops = drops.clone(drops.getDescription());

		/* 4. Drop items that should always drop, that are not bones. */
		ArrayList<Item> invariableItems = drops.invariableItems(owner);
		for (Item item : invariableItems) {
			if (!worldAllowsDrop(item)) {
				continue;
			}
			if (Summoning.tryAutoBuryDrop(owner, item.getCatalogId(), item.getAmount())) {
				continue;
			}
			if (getWorld().getServer().getEntityHandler().getItemDef(item.getCatalogId()).isStackable()) {
				dropStackItem(item.getCatalogId(), item.getAmount(), owner, personalDrop);
			} else {
				dropStandardItem(item, owner, personalDrop);
			}
		}

		/* 5. Roll for drops. */
		if (drops.getTotalWeight() > 0) {
			ArrayList<Item> items = personalDrop ? drops.rollPersonalLoot(owner, contributionScale) : drops.rollItem(owner);
			for (Item item : items) {
				if (item != null) {
					if (!worldAllowsDrop(item)) {
						continue;
					}
					if (Summoning.tryAutoBuryDrop(owner, item.getCatalogId(), item.getAmount())) {
						continue;
					}

					if (getWorld().getServer().getEntityHandler().getItemDef(item.getCatalogId()).isStackable()) {
						dropStackItem(item.getCatalogId(), item.getAmount(), owner, personalDrop);
					} else {
						dropStandardItem(item, owner, personalDrop);
					}
				}
			}
		}
	}

	private GroundItem createNpcGroundItem(Player owner, int itemId, int amount, boolean noted, boolean personalDrop) {
		GroundItem groundItem = new GroundItem(owner.getWorld(), itemId, getX(), getY(), amount, owner, noted);
		groundItem.setAttribute("npcdrop", true);
		if (personalDrop) {
			groundItem.setAttribute("personalNpcDrop", true);
		}
		return groundItem;
	}

	private boolean worldAllowsDrop(Item item) {
		if ((getWorld().getServer().getConfig().RESTRICT_ITEM_ID >= 0 && item.getCatalogId() > getWorld().getServer().getConfig().RESTRICT_ITEM_ID)
			|| (getWorld().getServer().getConfig().ONLY_BASIC_RUNES
			&& getWorld().getServer().getEntityHandler().getItemDef(item.getCatalogId()).getName().endsWith("-Rune")
			&& item.getCatalogId() >= ItemId.LIFE_RUNE.id())) {
			// world does not allow drop
			return false;
		}
        // No p2p drops on f2p world. On openrsc we can just drop nothing.
        // In OSRS, they have a different f2p drop instead usually, but there wouldn't be data on this for RSC.
        return getWorld().getServer().getConfig().MEMBER_WORLD || !item.getDef(getWorld()).isMembersOnly();
    }

	private int getBoneTier(int boneId) {
		switch(ItemId.getById(boneId)) {
			case BONES:
			case BAT_BONES:
			default:
				return 0;
			case BIG_BONES:
				return 1;
			case DRAGON_BONES:
				return 2;
		}
	}

	private int getHerbTier(int boneId) {
		switch(ItemId.getById(boneId)) {
			case UNIDENTIFIED_GUAM_LEAF:
			case UNIDENTIFIED_MARRENTILL:
			case UNIDENTIFIED_TARROMIN:
			case UNIDENTIFIED_HARRALANDER:
			default:
				return 0;
			case UNIDENTIFIED_RANARR_WEED:
			case UNIDENTIFIED_IRIT_LEAF:
			case UNIDENTIFIED_AVANTOE:
				return 1;
			case UNIDENTIFIED_KWUARM:
			case UNIDENTIFIED_CADANTINE:
			case UNIDENTIFIED_DWARF_WEED:
				return 2;
		}
	}

	private void calculateCustomKingBlackDragonDrop(Player owner) {
		calculateCustomKingBlackDragonDrop(owner, 1.0D, false);
	}

	private void calculateCustomKingBlackDragonDrop(Player owner, double contributionScale, boolean personalDrop) {
		getWorld().getNpcDrops().getBadLuckMitigation().incrementKills(owner,
			getWorld().getNpcDrops().getKbdTableCustom().getDropTableId(),
			ItemId.DRAGON_2_HANDED_SWORD.id());

		if (getWorld().getNpcDrops().getKbdTableCustom().rollAccess(this.getID()) && passesPersonalRareDropGate(contributionScale)) {
			ArrayList<Item> kbdSpecificLoot = getWorld().getNpcDrops().getKbdTableCustom().rollItem(owner);
			if (kbdSpecificLoot != null) {
				for (Item item : kbdSpecificLoot) {
					GroundItem groundItem = createNpcGroundItem(owner, item.getCatalogId(), item.getAmount(), item.getNoted(), personalDrop);
					getWorld().registerItem(groundItem);
					getWorld().getServer().submitSqlLogging(() -> {
						try {
							getWorld().getServer().getDatabase().addDropLog(
								owner, this, item.getCatalogId(), item.getAmount());
						} catch (final GameDatabaseException ex) {
							LOGGER.catching(ex);
						}
					});
					if (item.getCatalogId() == ItemId.DRAGON_2_HANDED_SWORD.id()) {
						owner.message("Congratulations! You have received a dragon 2-Handed Sword!");

						getWorld().getNpcDrops().getBadLuckMitigation().resetKills(owner,
							getWorld().getNpcDrops().getKbdTableCustom().getDropTableId(),
							item.getCatalogId());
					}
				}
			}
		}
	}

	private boolean passesPersonalRareDropGate(double contributionScale) {
		double scaledChance = Math.max(0.05D, Math.min(1.0D, contributionScale));
		return DataConversions.getRandom().nextDouble() < scaledChance;
	}

	public static ArrayList<Item> calculateCustomKingBlackDragonDropTest(Player owner, boolean ringOfWealth) {
		ArrayList<Item> returnMe = new ArrayList<Item>();

		owner.getWorld().getNpcDrops().getBadLuckMitigation().incrementKills(owner,
			owner.getWorld().getNpcDrops().getKbdTableCustom().getDropTableId(),
			ItemId.DRAGON_2_HANDED_SWORD.id());

		if (owner.getWorld().getNpcDrops().getKbdTableCustom().rollAccess(NpcId.KING_BLACK_DRAGON.id())) {
			ArrayList<Item> kbdSpecificLoot = owner.getWorld().getNpcDrops().getKbdTableCustom().rollItem(ringOfWealth, owner);
			if (kbdSpecificLoot != null) {
				for (Item kbdDrop : kbdSpecificLoot) {
					if (kbdDrop.getCatalogId() == ItemId.DRAGON_2_HANDED_SWORD.id()) {
						owner.getWorld().getNpcDrops().getBadLuckMitigation().resetKills(owner,
							owner.getWorld().getNpcDrops().getKbdTableCustom().getDropTableId(),
							kbdDrop.getCatalogId());
					}
				}
				return kbdSpecificLoot;
			}
		}
		return returnMe;
	}

	private int getBonesDrop() {
		int bones = ItemId.NOTHING.id();
		// Big Bones
		if (getWorld().npcDrops.isBigBoned(this.getID())) {
			bones = boneItem(ItemId.BIG_BONES.id());
		}
		// Bat
		else if (getWorld().npcDrops.isBatBoned(this.getID())) {
			bones = boneItem(ItemId.BAT_BONES.id());
		}
		// Dragon
		else if (getWorld().npcDrops.isDragon(this.getID())) {
			bones = boneItem(ItemId.DRAGON_BONES.id());
		}
		// Demon
		else if (getWorld().npcDrops.isDemon(this.getID())) {
			bones = ItemId.DEMON_ASH.id();
		}
		// Not boneless
		else if (!getWorld().npcDrops.isBoneless(this.getID())) {
			bones = boneItem(ItemId.BONES.id());
		}
		return bones;
	}

	private int boneItem(int boneId) {
		return getConfig().ONLY_REGULAR_BONES ? ItemId.BONES.id() : boneId;
	}

	private void dropStackItem(final int dropID, int amount, Player owner) {
		dropStackItem(dropID, amount, owner, false);
	}

	private void dropStackItem(final int dropID, int amount, Player owner, boolean personalDrop) {
		// Gold Drops
		if (dropID == ItemId.COINS.id() && owner.getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_SPLENDOR.id())) {
			amount += Formulae.getSplendorBoost(amount);
			owner.message("Your ring of splendor shines brightly!");
		}
		final int finalAmount = amount;
		getWorld().getServer().submitSqlLogging(() -> {
			try {
				getWorld().getServer().getDatabase().addDropLog(owner, this, dropID, finalAmount);
			} catch (final GameDatabaseException ex) {
				LOGGER.catching(ex);
			}
		});

		if (!DropTable.handleRingOfAvarice(owner, new Item(dropID, amount))) {
			ItemDefinition itemDef = getWorld().getServer().getEntityHandler().getItemDef(dropID);
			if (itemDef != null && !itemDef.isStackable()
				&& owner.getCarriedItems().getEquipment().tryBankMonsterLootWithLawNecklace(new Item(dropID, amount))) {
				return;
			}
			GroundItem groundItem = createNpcGroundItem(owner, dropID, amount, false, personalDrop);
			getWorld().registerItem(groundItem);
		}
	}

	private void dropStandardItem(Item item, Player owner) {
		dropStandardItem(item, owner, false);
	}

	private void dropStandardItem(Item item, Player owner, boolean personalDrop) {
		int dropID = item.getCatalogId();
		int amount = item.getAmount();
		final int finalAmount = amount;
		getWorld().getServer().submitSqlLogging(() -> {
			try {
				getWorld().getServer().getDatabase().addDropLog(owner, this, dropID, finalAmount);
			} catch (final GameDatabaseException ex) {
				LOGGER.catching(ex);
			}
		});
		GroundItem groundItem;

		// We need to drop multiple counts of "1" item if it's not a stack
		// But if it's noted, just drop it all.
		int loop = amount;
		if (item.getNoted()) loop = 1;
		else amount = 1;
		for (int count = 0; count < loop; count++) {
			final ItemDefinition itemDef = dropID == ItemId.NOTHING.id() ? null :
				getWorld().getServer().getEntityHandler().getItemDef(dropID);
			if (dropID != ItemId.NOTHING.id()
				&& itemDef.isMembersOnly()
				&& !getConfig().MEMBER_WORLD) {
				continue; // Members item on a non-members world.
			} else if (dropID != ItemId.NOTHING.id()) {
				if (!item.getNoted()
					&& owner.getCarriedItems().getEquipment().tryBankMonsterLootWithLawNecklace(new Item(dropID, amount))) {
					continue;
				}
				groundItem = createNpcGroundItem(owner, dropID, amount, item.getNoted(), personalDrop);
				getWorld().registerItem(groundItem);
			}
		}
	}

	/**
	 * Distributes kill XP by each player's damage share and returns top damage for kill credit.
	 */
	private Pair<UUID, Long> handleXpDistribution(final Mob attacker) {
		final int totalCombatXP = Formulae.combatExperience(this);
		Pair<UUID, Long> topDamageDealer = getTopDamageDealer(attacker);

		for (UUID id : getAllDamageDealerIds()) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player == null) {
				continue;
			}

			awardDamageShareXp(
				player,
				getCombatDamageInfoBy(id).getLeft(),
				getRangeDamageInfoBy(id).getLeft(),
				getMageDamageInfoBy(id).getLeft(),
				totalCombatXP
			);
			player.resetTrackedDamageAndBlockedDamage(this);
		}

		return topDamageDealer;
	}

	private ArrayList<UUID> getAllDamageDealerIds() {
		ArrayList<UUID> ids = new ArrayList<UUID>();
		addMissingDamageDealerIds(ids, getCombatDamagers());
		addMissingDamageDealerIds(ids, getRangeDamagers());
		addMissingDamageDealerIds(ids, getMageDamagers());
		return ids;
	}

	private void addMissingDamageDealerIds(final ArrayList<UUID> allIds, final ArrayList<UUID> idsToAdd) {
		for (UUID id : idsToAdd) {
			if (!allIds.contains(id)) {
				allIds.add(id);
			}
		}
	}

	private void awardDamageShareXp(final Player player, final int meleeDamage, final int rangedDamage, final int magicDamage, final int totalCombatXP) {
		awardMeleeDamageShareXp(player, meleeDamage, totalCombatXP);
		awardRangedDamageShareXp(player, rangedDamage, totalCombatXP);
		awardMagicDamageShareXp(player, magicDamage, totalCombatXP);
	}

	private int getDamageShareXp(final int totalXp, final int damage) {
		if (damage <= 0 || getDef().hits <= 0) {
			return 0;
		}
		return (int) (((double) totalXp / (double) getDef().hits) * (double) damage);
	}

	private void awardMeleeDamageShareXp(final Player player, final int damage, final int totalCombatXP) {
		int meleeXpShare = getDamageShareXp(totalCombatXP, damage);
		if (meleeXpShare <= 0) {
			return;
		}

		awardCombatXpWithHitsFocus(player, Skill.MELEE, meleeXpShare * 4);
	}

	private void awardRangedDamageShareXp(final Player player, final int damage, final int totalCombatXP) {
		int rangedXpShare = getDamageShareXp(totalCombatXP * 4, damage);
		if (rangedXpShare <= 0) {
			return;
		}

		int alreadyGivenXp = getWorld().getServer().getConfig().RANGED_GIVES_XP_HIT ? 16 * damage / 3 : 0;
		int remainderXP = rangedXpShare - alreadyGivenXp;
		if (remainderXP > 0) {
			awardCombatXpWithHitsFocus(player, Skill.RANGED, remainderXP);
			ActionSender.sendStat(player, Skill.RANGED.id());
		}
	}

	private void awardMagicDamageShareXp(final Player player, final int damage, final int totalCombatXP) {
		int magicXpShare = getDamageShareXp(totalCombatXP * 4, damage);
		if (magicXpShare <= 0) {
			return;
		}

		awardCombatXpWithHitsFocus(player, Skill.MAGIC, magicXpShare);
		ActionSender.sendStat(player, Skill.MAGIC.id());
	}

	private void awardCombatXpWithHitsFocus(final Player player, final Skill primarySkill, final int totalXp) {
		int hitsXp = Math.min(totalXp, getHitsXpFromFocus(player, totalXp));
		int primaryXp = Math.max(0, totalXp - hitsXp);
		if (hitsXp <= 0) {
			player.incExp(primarySkill.id(), totalXp, true);
			return;
		}
		if (primaryXp <= 0) {
			player.incExp(Skill.HITS.id(), hitsXp, true);
			ActionSender.sendStat(player, Skill.HITS.id());
			return;
		}

		player.incExp(primarySkill.id(), primaryXp, true);
		player.incExp(Skill.HITS.id(), hitsXp, true);
		ActionSender.sendStat(player, Skill.HITS.id());
	}

	private int getHitsXpFromFocus(final Player player, final int totalXp) {
		switch (player.getHitsXpFocus()) {
			case Skills.CONTROLLED_MODE:
				return 0;
			case Skills.ACCURATE_MODE:
				return totalXp / 2;
			case Skills.DEFENSIVE_MODE:
				return totalXp;
			case Skills.AGGRESSIVE_MODE:
			default:
				return totalXp / 4;
		}
	}

	public void initializeTalkScript(final Player player) {
		final Npc npc = this;
		getWorld().getServer().getGameEventHandler().add(new ImmediateEvent(getWorld(), "Init Talk Script") {
			@Override
			public void action() {
				NpcInteraction interaction = NpcInteraction.NPC_TALK_TO;
				NpcInteraction.setInteractions(npc, player, interaction);
				npc.setMultiTimeout(-1);
				npc.setPlayerWantsNpc(false);
				getWorld().getServer().getPluginHandler().handlePlugin(TalkNpcTrigger.class, player, new Object[]{player, npc});
			}
		});
	}

	public void setMultiTimeout(long currentTimeMillis) {
		this.multiTimeout = currentTimeMillis;
	}

	public void setInteractingPlayer(Player player) {
		this.interactingPlayer = player;
	}

	public void setPlayerWantsNpc(boolean wantsNpc) {
		this.playerWantsNpc = wantsNpc;
	}

	public void remove() {
		this.killed = true;
		resetCombatEvent();
		clearPlayerPvmMeleeEvents();
		double respawnMult = getConfig().NPC_RESPAWN_MULTIPLIER;
		Npc n = this;
		//In RSC, the player only gets updated about combat ending the tick after the kill.
		//Causes issues with retro clients.
		//TODO: Come up with a solution that works with retro clients? May not be authentic for older clients anyway.
		if(getConfig().BASED_CONFIG_DATA > 18) {
			getWorld().getServer().getGameEventHandler().add(new GameTickEvent(getWorld(), null, 0, "Remove Combat Event", DuplicationStrategy.ONE_PER_MOB) {
				@Override
				public void run() {
					n.resetCombatEvent();
					running = false;
				}
			});
		} else {
			n.resetCombatEvent();
		}
		this.setLastOpponent(null);
		if (!isRemoved() && shouldRespawn && def.respawnTime() > 0) {
			super.remove();
			startRespawning();
			setRespawning(true);
			getWorld().getServer().getGameEventHandler().add(new DelayedEvent(getWorld(), null, (long) (def.respawnTime() * respawnMult * 1000), "Respawn NPC", DuplicationStrategy.ONE_PER_MOB) {
				public void run() {
					n.killed = false;
					n.setRemoved(false);
					n.getRegion().addEntity(n);

					// Take 4 ticks away from the current time to get a 1 tick pause while the npc spawns,
					// before it is allowed to attack (if aggressive).
					teleport(loc.startX, loc.startY);
					face(loc.startX, loc.startY - 1);
					setCombatTimer(-getConfig().GAME_TICK * 4);
					setRespawning(false);
					getSkills().normalize();
					tryResyncHitEvent();

					running = false;
					mageDamagers.clear();
					rangeDamagers.clear();
					combatDamagers.clear();
				}
			});
		} else if (!shouldRespawn) {
			setUnregistering(true);
		}
	}

	@Override
	public boolean isInvisibleTo(final Entity observer) {
		if (!getConfig().WANT_INVISIBLE_NPCS) {
			return false;
		}

		if (!observer.isPlayer()) {
			return super.isInvisibleTo(observer);
		}

		Player playerObserver = (Player)observer;

		if (playerObserver.isAdmin()) {
			return false;
		}

		if (getConfig().WANT_COMBAT_ODYSSEY && getID() == NpcId.BIGGUM_FLODROT.id()) {
			int prestige = playerObserver.getCache().hasKey("co_prestige") ? playerObserver.getCache().getInt("co_prestige") : 0;
			boolean playerHasBiggum = playerObserver.getCarriedItems().hasCatalogID(ItemId.BIGGUM_FLODROT.id())
				|| playerObserver.getBank().hasItemId(ItemId.BIGGUM_FLODROT.id());
			return !(prestige >= 1 && !playerHasBiggum);
		}

		if (getConfig().ARMY_OF_OBSCURITY && getID() == NpcId.ASH.id()) {
			return (playerObserver.getCache().hasKey("army_of_obscurity") ? playerObserver.getCache().getInt("army_of_obscurity") : 0) == -1;
		}

		return false;
	}

	private void startRespawning() {

	}

	public void setShouldRespawn(final boolean respawn) {
		shouldRespawn = respawn;
	}

	public boolean shouldRespawn() {
		return shouldRespawn;
	}

	public void teleport(final int x, final int y) {
		setLocation(Point.location(x, y), true);
	}

	@Override
	public String toString() {
		return "[NPC:" + getIndex() + ":" + getDef().getName() + " @ (" + getX() + ", " + getY() + ")]";
	}

	/**
	 * Gets the NPC to move to an adjacent tile, with a priority system.
	 */
	public void moveToAdjacentTile() {
		ArrayList<Point> possiblePoints = new ArrayList<>();
		//Walk priority seems to be positives first? This is different from the client pathfinding.
		//TODO: More investigation on the direction an NPC would move towards in this case.
		for (int x = 1; x >= -1; x = x - 2) {
			possiblePoints.add(new Point(getX() + x, getY()));
		}
		for (int y = 1; y >= -1; y = y - 2) {
			possiblePoints.add(new Point(getX(), getY() + y));
		}

		possiblePoints.add(new Point(getX() + 1, getY() + 1));
		possiblePoints.add(new Point(getX() + 1, getY() - 1));
		possiblePoints.add(new Point(getX() - 1, getY() + 1));
		possiblePoints.add(new Point(getX() - 1, getY() - 1));

		for (Point possiblePoint : possiblePoints) {
			if (possiblePoint.inBounds(getLoc().minX(), getLoc().minY(), getLoc().maxX(), getLoc().maxY())
				&& canWalk(getWorld(), possiblePoint.getX(), possiblePoint.getY())) {
				walk(possiblePoint.getX(), possiblePoint.getY());
				break;
			}
		}
	}

	public void updatePosition() {
		updatePosition(getWorld().getPlayers().size() > 0);
	}

	public void updatePosition(final boolean hasPlayers) {
		updateBehavior(hasPlayers);
		updateMovementOnly();
	}

	public void updateBehavior() {
		updateBehavior(getWorld().getPlayers().size() > 0);
	}

	public void updateBehavior(final boolean hasPlayers) {
		NpcInteraction interaction = getNpcInteraction();
		Player player = getInteractingPlayer();
		if (player != null && player.getInteractingNpc() == this) {
			switch (interaction) {
				//Interactions that should reset the NPC's path.
				case NPC_TALK_TO:
				case NPC_USE_ITEM:
					resetPath();
					resetRange();
				default:
					break;
			}

			switch (interaction) {
				//Other interaction specific handling.
				case NPC_TALK_TO:
					// NPCs on the same tile as you will walk somewhere else.
					if (player.getLocation().equals(getLocation())) {
						moveToAdjacentTile();
					}
				case NPC_USE_ITEM:
				case NPC_GNOMEBALL_OP:
					if (finishedPath() && !inCombat()) face(player);
					break;
				case NPC_OP:
				default:
					break;
			}
		} else {
			getNpcBehavior().tick(hasPlayers);
		}
	}

	public void updateMovementOnly() {
		super.updatePosition();
	}

	private boolean canWalk(World world, int x, int y) {
		int myX = getX();
		int myY = getY();
		int newX = x;
		int newY = y;
		boolean myXBlocked = false, myYBlocked = false, newXBlocked = false, newYBlocked = false;
		if (myX > x) {
			myXBlocked = checkBlocking(world,myX - 1, myY, 8); // Check right
			// tiles
			newX = myX - 1;
		} else if (myX < x) {
			myXBlocked = checkBlocking(world,myX + 1, myY, 2); // Check left
			// tiles
			newX = myX + 1;
		}
		if (myY > y) {
			myYBlocked = checkBlocking(world, myX, myY - 1, 4); // Check top tiles
			newY = myY - 1;
		} else if (myY < y) {
			myYBlocked = checkBlocking(world, myX, myY + 1, 1); // Check bottom
			// tiles
			newY = myY + 1;
		}

		if ((myXBlocked && myYBlocked) || (myXBlocked && myY == newY) || (myYBlocked && myX == newX)) {
			return false;
		}

		if (newX > myX) {
			newXBlocked = checkBlocking(world, newX, newY, 2);
		} else if (newX < myX) {
			newXBlocked = checkBlocking(world, newX, newY, 8);
		}

		if (newY > myY) {
			newYBlocked = checkBlocking(world, newX, newY, 1);
		} else if (newY < myY) {
			newYBlocked = checkBlocking(world, newX, newY, 4);
		}
		if ((newXBlocked && newYBlocked) || (newXBlocked && myY == newY) || (myYBlocked && myX == newX)) {
			return false;
		}
		if ((myXBlocked && newXBlocked) || (myYBlocked && newYBlocked)) {
			return false;
		}
		return true;
	}

	private boolean checkBlocking(World world, int x, int y, int bit) {
		TileValue t = world.getTile(x, y);
		Point point = new Point(x, y);
		for (Npc n : getViewArea().getNpcsInView()) {
			if (n.getLocation().equals(point)) {
				return true;
			}
		}
		for (Player areaPlayer : getViewArea().getPlayersInView()) {
			if (areaPlayer.getLocation().equals(point)) {
				return true;
			}
		}
		return isBlocking(t.traversalMask, (byte) bit);
	}

	private boolean isBlocking(int objectValue, byte bit) {
		if ((objectValue & bit) != 0) { // There is a wall in the way
			return true;
		}
		if ((objectValue & 16) != 0) { // There is a diagonal wall here:
			// \
			return true;
		}
		if ((objectValue & 32) != 0) { // There is a diagonal wall here:
			// /
			return true;
		}
		if ((objectValue & 64) != 0) { // This tile is unwalkable
			return true;
		}
		return false;
	}

	public void produceUnderAttack() {
		getWorld().produceUnderAttack(this);
	}

	public boolean checkUnderAttack() {
		return getWorld().checkUnderAttack(this);
	}

	public void releaseUnderAttack() {
		getWorld().releaseUnderAttack(this);
	}

	public boolean isChasing() {
		return getNpcBehavior().isChasing();
	}

	public void setChasing(final Player player) {
		getNpcBehavior().setChasing(player);
	}

	public void setChasing(final Npc npc) {
		getNpcBehavior().setChasing(npc);
	}

	public NpcBehavior getBehavior() {
		return getNpcBehavior();
	}

	public boolean isRespawning() {
		return isRespawning;
	}

	private void setRespawning(final boolean isRespawning) {
		this.isRespawning = isRespawning;
	}

	public void superRemove() {
		super.remove();
	}

	public boolean addDeathListener(final NpcLootEvent event) {
		return deathListeners.add(event);
	}

	public boolean cantHeal() {
		return healTimer - System.currentTimeMillis() > 0;
	}

	public void setHealTimer(final long l) {
		healTimer = System.currentTimeMillis() + l;
	}

	public void setExecutedAggroScript(final boolean executed) {
		this.executedAggroScript = executed;
	}

	public boolean executedAggroScript() {
		return this.executedAggroScript;
	}

	public Point walkablePoint(final Point minP, final Point maxP) {
		final int currX = getX();
		final int currY = getY();
		final int radius = 8;
		final int minX = insetMin(minP.getX(), maxP.getX());
		final int maxX = insetMax(minP.getX(), maxP.getX());
		final int minY = insetMin(minP.getY(), maxP.getY());
		final int maxY = insetMax(minP.getY(), maxP.getY());
		final int newX = DataConversions.random(Math.max(minX, currX - radius), Math.min(maxX, currX + radius));
		final int newY = DataConversions.random(Math.max(minY, currY - radius), Math.min(maxY, currY + radius));
		// gnome agility course
		if (Point.location(newX, newY).inBounds(680, 491, 696, 511)) {
			return Point.location(currX, currY);
		}
		return Point.location(newX, newY);
	}

	public boolean inRoamBounds(final Point point) {
		final int minX = insetMin(getLoc().minX(), getLoc().maxX());
		final int maxX = insetMax(getLoc().minX(), getLoc().maxX());
		final int minY = insetMin(getLoc().minY(), getLoc().maxY());
		final int maxY = insetMax(getLoc().minY(), getLoc().maxY());
		return point.inBounds(minX, minY, maxX, maxY);
	}

	private int insetMin(final int min, final int max) {
		return max - min > 2 ? min + 1 : min;
	}

	private int insetMax(final int min, final int max) {
		return max - min > 2 ? max - 1 : max;
	}

	public NpcBehavior getNpcBehavior() {
		return npcBehavior;
	}

	public void setNpcBehavior(final NpcBehavior npcBehavior) {
		this.npcBehavior = npcBehavior;
	}

	public void walkToRespawn() {
		walkToEntityAStar(loc.startX, loc.startY);
	}

	public long getMultiTimeout() {
		return multiTimeout;
	}

	public boolean getPlayerWantsNpc() {
		return playerWantsNpc;
	}

	public Player getInteractingPlayer() {
		return interactingPlayer;
	}
}
