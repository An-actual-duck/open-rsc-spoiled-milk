package com.openrsc.server.model.entity.npc;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerFailureDiagnostics;
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
import com.openrsc.server.model.combat.CombatEngagementTerminalReason;
import com.openrsc.server.model.entity.*;
import com.openrsc.server.model.entity.death.DeathContext;
import com.openrsc.server.model.entity.death.DeathTransition;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldLocation;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Npc extends Mob {
	private static final double DEFAULT_MELEE_DEFENSE_MULTIPLIER = 1.0D;
	private static final double DEFAULT_RANGED_DEFENSE_MULTIPLIER = 0.5D;
	private static final double DEFAULT_MAGIC_DEFENSE_MULTIPLIER = 0.5D;
	private static final double GROUP_COMBAT_XP_STEP = 0.1D;
	private static final double GROUP_COMBAT_XP_MAX_MULTIPLIER = 2.0D;

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	public static final String DEATH_VISUAL_TICK_ATTRIBUTE = "npc_death_visual_tick";

	private long healTimer = 0;
	private boolean shouldRespawn = true;
	private boolean isRespawning = false;
	private boolean executedAggroScript = false;
	private final NpcOwnerPreservationLifecycleGate
		layeredOwnerLifecycleGate =
			new NpcOwnerPreservationLifecycleGate();
	private NpcBehavior npcBehavior;
	private ArrayList<NpcLootEvent> deathListeners = new ArrayList<NpcLootEvent>(1); // TODO: Should use a more generic class. Maybe PlayerKilledNpcListener, but that is in plugins jar.
	private static final int[] PLUGIN_OWNED_DEATH_NPC_IDS = {
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
	 * Factual player-owned damage for this NPC lifetime. Settlement keeps its
	 * distinct legacy roles (final hit, loot, style XP, and summon XP) rather
	 * than collapsing them into one generic "owner".
	 */
	private final NpcContributionLedger contributionLedger =
		new NpcContributionLedger();
	private Map<Long, PendingSummoningExperience> pendingSummoningExperience = new HashMap<Long, PendingSummoningExperience>();


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

	/** Creates a runtime NPC at an exact signed location. */
	public Npc(
		final World world,
		final int id,
		final WorldLocation location) {
		this(world, id, location, 5);
	}

	/** Creates a runtime NPC with exact location and configured roam radius. */
	public Npc(
		final World world,
		final int id,
		final WorldLocation location,
		final int radius) {
		this(
			world,
			createRuntimeNpcLoc(world, id, location, radius),
			location);
	}

	public Npc(final World world, final int id, final int x, final int y, final int radius) {
		this(world, new NPCLoc(id, x, y, x - radius, x + radius, y - radius, y + radius));
	}

	public Npc(final World world, final int id, final int startX, final int startY, final int minX, final int maxX, final int minY, final int maxY) {
		this(world, new NPCLoc(id, startX, startY, minX, maxX, minY, maxY));
	}

	public Npc(final World world, final NPCLoc loc) {
		this(world, loc, null);
	}

	private Npc(
		final World world,
		final NPCLoc loc,
		final WorldLocation initialWorldLocation) {
		super(world, EntityType.NPC);
		if (loc.getAuthoredPlacementIdentity() != null) {
			assignAuthoredPlacementIdentity(
				loc.getAuthoredPlacementIdentity());
		}

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
		if (initialWorldLocation != null
			&& getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			getWorld().getRegionManager().prepareNativeLayeredTransition(
				null, initialWorldLocation, true);
			super.setWorldLocation(initialWorldLocation, true);
		} else {
			super.setLocation(
				Point.location(loc.startX(), loc.startY()), true);
		}

		getSkills().setLevelTo(Skill.ATTACK.id(), def.getAtt());
		getSkills().setLevelTo(Skill.DEFENSE.id(), def.getDef());
		getSkills().setLevelTo(Skill.RANGED.id(), def.getRanged());
		getSkills().setLevelTo(Skill.STRENGTH.id(), def.getStr());
		getSkills().setLevelTo(Skill.HITS.id(), def.getHits());

		getWorld().getServer().getGameEventHandler().add(getStatRestorationEvent());
	}

	private static NPCLoc createRuntimeNpcLoc(
		final World world,
		final int id,
		final WorldLocation location,
		final int radius) {
		World checkedWorld = Objects.requireNonNull(world, "world");
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		Point runtimePoint = checkedWorld.getServer().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY
			? checkedWorld.getRegionManager()
				.toRuntimeCompatibilityPoint(checkedLocation)
			: LegacyPackedPointAdapter.toLegacyPoint(checkedLocation);
		int x = runtimePoint.getX();
		int y = runtimePoint.getY();
		return new NPCLoc(
			id, x, y,
			x - radius, x + radius,
			y - radius, y + radius);
	}

	/**
	 * Adds combat damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addCombatDamage(final Player mob, final int damage) {
		addContribution(NpcContributionRole.MELEE, mob, damage);
	}

	/**
	 * Adds mage damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addMageDamage(final Player mob, final int damage) {
		addContribution(NpcContributionRole.MAGIC, mob, damage);
	}

	/**
	 * Adds range damage done by a player
	 *
	 * @param mob    mob dealing damage
	 * @param damage current attack's damage
	 */
	public void addRangeDamage(final Player mob, final int damage) {
		addContribution(NpcContributionRole.RANGED, mob, damage);
	}

	public void addSummonDamage(final Player mob, final int damage) {
		addContribution(NpcContributionRole.SUMMON, mob, damage);
	}

	private void addContribution(final NpcContributionRole role,
			final Player player, final int damage) {
		contributionLedger.record(role, player.getUUID(),
			player.getUsernameHash(), damage);
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
		return getContributionDamageInfo(NpcContributionRole.MELEE, ID);
	}

	/**
	 * Returns factual melee contributor identities.
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getCombatDamagers() {
		return contributionLedger.contributorIds(NpcContributionRole.MELEE);
	}

	public boolean hasDamageFrom(final Player player) {
		if (player == null) {
			return false;
		}
		final UUID id = player.getUUID();
		return getCombatDamageInfoBy(id).getLeft() > 0
			|| getRangeDamageInfoBy(id).getLeft() > 0
			|| getMageDamageInfoBy(id).getLeft() > 0
			|| getSummonDamageInfoBy(id).getLeft() > 0;
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
		return getContributionDamageInfo(NpcContributionRole.MAGIC, ID);
	}

	/**
	 * Returns factual Magic contributor identities.
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getMageDamagers() {
		return contributionLedger.contributorIds(NpcContributionRole.MAGIC);
	}

	/**
	 * Range damage done by Mob ID
	 *
	 * @param ID uuid of mob
	 * @return Pair
	 */
	private Pair<Integer, Long> getRangeDamageInfoBy(final UUID ID) {
		return getContributionDamageInfo(NpcContributionRole.RANGED, ID);
	}

	/**
	 * Returns factual ranged contributor identities.
	 *
	 * @return ArrayList<String>
	 */
	private ArrayList<UUID> getRangeDamagers() {
		return contributionLedger.contributorIds(NpcContributionRole.RANGED);
	}

	private Pair<Integer, Long> getSummonDamageInfoBy(final UUID ID) {
		return getContributionDamageInfo(NpcContributionRole.SUMMON, ID);
	}

	private ArrayList<UUID> getSummonDamagers() {
		return contributionLedger.contributorIds(NpcContributionRole.SUMMON);
	}

	private Pair<Integer, Long> getContributionDamageInfo(
			final NpcContributionRole role, final UUID id) {
		final NpcContributionLedger.Contribution contribution =
			contributionLedger.get(role, id);
		return Pair.of(Math.min(contribution.getDamage(),
			this.getDef().getHits()), contribution.getUsernameHash());
	}

	private int getTotalDamageBy(final UUID id) {
		return getCombatDamageInfoBy(id).getLeft()
			+ getRangeDamageInfoBy(id).getLeft()
			+ getMageDamageInfoBy(id).getLeft()
			+ getSummonDamageInfoBy(id).getLeft();
	}

	public boolean hasDamageBy(final Player player) {
		return player != null && getTotalDamageBy(player.getUUID()) > 0;
	}

	public void recordPendingSummoningExperience(final Player player, final int experience, final long expiresTick) {
		if (player == null || experience <= 0 || expiresTick <= getWorld().getServer().getCurrentTick()) {
			return;
		}
		pendingSummoningExperience.put(player.getUsernameHash(), new PendingSummoningExperience(experience, expiresTick));
	}

	private void awardPendingSummoningExperience() {
		if (pendingSummoningExperience.isEmpty()) {
			return;
		}
		final long currentTick = getWorld().getServer().getCurrentTick();
		for (Map.Entry<Long, PendingSummoningExperience> entry : new HashMap<Long, PendingSummoningExperience>(pendingSummoningExperience).entrySet()) {
			final PendingSummoningExperience pending = entry.getValue();
			if (pending.expiresTick < currentTick) {
				continue;
			}
			final Player player = getWorld().getPlayer(entry.getKey());
			if (player == null || player.isRemoved() || !hasDamageBy(player)) {
				continue;
			}
			player.incExp(Skill.SUMMONING.id(), pending.experience, true);
		}
		pendingSummoningExperience.clear();
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
		Pair<Integer, Long> summonInfo = getSummonDamageInfoBy(id);
		if (summonInfo.getLeft() > 0) {
			return summonInfo.getRight();
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
		for (UUID id : getSummonDamagers()) {
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
		for (UUID id : getSummonDamagers()) {
			int totalDamage = getTotalDamageBy(id);
			if (totalDamage > highestDamage) {
				topDamageUuid = id;
				topDamageHash = getUsernameHashForDamageOwner(id);
				highestDamage = totalDamage;
			}
		}

		return Pair.of(topDamageUuid, topDamageHash);
	}

	private Player selectPreferredThreat(final Player currentBest,
			final NpcContributionRole role,
			final boolean requireMeleeRange) {
		Player bestPlayer = currentBest != null
			&& sharesSpatialDomain(currentBest) ? currentBest : null;
		int bestDamage = bestPlayer == null ? -1 : getTotalDamageBy(bestPlayer.getUUID());
		for (UUID id : contributionLedger.contributorIds(role)) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player == null || player.isRemoved()
				|| player.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !sharesSpatialDomain(player)) {
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
			int damage = Math.min(getTotalDamageBy(id), getDef().getHits());
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
		bestPlayer = selectPreferredThreat(bestPlayer, NpcContributionRole.MELEE,
			requireMeleeRange);
		bestPlayer = selectPreferredThreat(bestPlayer, NpcContributionRole.RANGED,
			requireMeleeRange);
		bestPlayer = selectPreferredThreat(bestPlayer, NpcContributionRole.MAGIC,
			requireMeleeRange);
		return selectPreferredThreat(bestPlayer, NpcContributionRole.SUMMON,
			requireMeleeRange);
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
		addPersonalLootRecipients(recipients, getSummonDamagers());
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
		int explicitOffense = getDef().getMeleeOffense();
		if (explicitOffense > 0) {
			return explicitOffense;
		}
		return getDef().getStr();
	}

	@Override
	public int getRangedOffense() {
		int explicitOffense = getDef().getRangedOffense();
		if (explicitOffense > 0) {
			return explicitOffense;
		}
		int profileOffense = NpcCombatProfile.resolve(this).getRangedOffense();
		if (profileOffense > 0) {
			return profileOffense;
		}
		return getDef().getRanged();
	}

	@Override
	public int getMagicOffense() {
		int explicitOffense = getDef().getMagicOffense();
		if (explicitOffense > 0) {
			return explicitOffense;
		}
		return NpcCombatProfile.resolve(this).getMagicOffense();
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
		if (this.killed) {
			this.curePoison();
			return;
		}
		if (isPluginOwnedDeathCompatibility()) {
			processLegacyDeath(mob, true);
			return;
		}
		final DeathTransition transition = tryBeginDeathLifecycle(mob);
		if (!transition.isStarted()) {
			this.curePoison();
			return;
		}
		final DeathContext context = transition.getContext();
		try {
			processLegacyDeath(mob, false);
		} catch (final RuntimeException failure) {
			if (!killed) {
				try {
					deathListeners.clear();
					remove();
				} catch (final RuntimeException cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
			}
			finalizeDeathLifecycle(context);
			LOGGER.error(
				"NPC death processing failed after ownership was acquired: npc={} lifecycle={}",
				getID(), context.getLifecycleId(), failure);
			throw failure;
		} catch (final Error failure) {
			finalizeDeathLifecycle(context);
			throw failure;
		}
		finalizeDeathLifecycle(context);
	}

	private void processLegacyDeath(final Mob mob,
			final boolean pluginOwnsDeath) {
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
		if (pluginOwnsDeath) {
			if (this.getID() == NpcId.RAT_TUTORIAL.id()) {
				remove();
			}
			return;
		}

		// Reset the player's range timer
		owner.setAttribute("can_range_again", getWorld().getServer().getCurrentTick());

		// Defense skillcape message
		int totalBlockedDamage = owner.getTrackedBlockedDamage(this);
		if (totalBlockedDamage > 0) {
			owner.playerServerMessage(MessageType.QUEST, "@dcy@Your defense cape blocked " + totalBlockedDamage + " damage!");
		}

		owner.setLastNpcKilledId(this.getID());
		creditMonsterSlayerEligibleContributors();

		Player killCreditOwner = owner;
		Map<Player, Double> personalLootRecipients = getPersonalLootRecipients();
		Pair<UUID, Long> ownerInfo = handleXpDistribution(mob);
		awardPendingSummoningExperience();
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
		owner.chargeDeathRingFromKill(this);
		owner.applyDeathAmuletBurst(this);
		owner.applySoulAmuletBurst(this);

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

	/** Snapshot Slayer eligibility before XP settlement clears contribution evidence. */
	private void creditMonsterSlayerEligibleContributors() {
		if (getWorld().getMonsterSlayerTaskService() == null) return;
		int threshold = Math.max(1, (int) Math.ceil(getDef().getHits() * 0.05D));
		ArrayList<UUID> contributors = getAllDamageDealerIds();
		UUID topContributor = null;
		int topDamage = 0;
		for (UUID id : contributors) {
			int damage = getTotalDamageBy(id);
			if (damage > topDamage) {
				topDamage = damage;
				topContributor = id;
			}
		}
		for (UUID id : contributors) {
			Player player = getWorld().getPlayerByUUID(id);
			if (player == null || player.isRemoved() || player.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !sharesSpatialDomain(player)
				|| !getLocation().withinRange(player.getLocation(), 16)
				|| (getTotalDamageBy(id) < threshold && !id.equals(topContributor))) continue;
			MonsterSlayerTaskService.CreditResult credit = getWorld()
				.getMonsterSlayerTaskService().tryCreditEligibleKill(player, getID());
			if (!credit.isSuccessful()) logMonsterSlayerCreditFailure(player, credit.getFailureReason());
		}
	}

	private void logMonsterSlayerCreditFailure(Player player, String reason) {
		if (MonsterSlayerFailureDiagnostics.shouldLog(player.getUUID(), getID(), reason)) {
			LOGGER.warn("Monster Slayer credit skipped: player={} npc={} reason={}",
				player.getUsername(), getID(), reason);
		}
	}

	private boolean isPluginOwnedDeathCompatibility() {
		for (final int npcId : PLUGIN_OWNED_DEATH_NPC_IDS) {
			if (getID() == npcId) {
				return true;
			}
		}
		return false;
	}

	private void finalizeDeathLifecycle(final DeathContext context) {
		if (!killed) {
			// Legacy failures before remove() left the NPC retryable.
			abandonDeathLifecycle(context);
			return;
		}
		markDeathLifecycleDead(context);
		if (isRespawning()) {
			markDeathLifecycleRespawning(context);
		}
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
		dropHiddenUniqueItems(owner, contributionScale, personalDrop);

		/* 2. Drop bones (or nothing). */
		int bones = getBonesDrop();
		if (bones != ItemId.NOTHING.id()) {
			final Item boneDrop = applyDeathNecklaceGuaranteedDropBonus(owner, new Item(bones, 1));
			final int boneAmount = boneDrop.getAmount();
			if (!Summoning.tryAutoBuryDrop(owner, bones, boneAmount)) {
				final ItemDefinition boneDef = getWorld().getServer().getEntityHandler().getItemDef(bones);
				if (boneDef == null || boneDef.isStackable()
					|| !owner.getCarriedItems().getEquipment().tryBankMonsterLootWithLawNecklace(new Item(bones, boneAmount))) {
					final boolean stackGroundItem = boneDef == null || boneDef.isStackable();
					final int groundItemCount = stackGroundItem ? 1 : boneAmount;
					final int groundItemAmount = stackGroundItem ? boneAmount : 1;
					for (int count = 0; count < groundItemCount; count++) {
						GroundItem groundItem = createNpcGroundItem(owner, bones, groundItemAmount, false, personalDrop);
						getWorld().registerItem(groundItem);
					}
				}
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
			item = applyDeathNecklaceGuaranteedDropBonus(owner, item);
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

	private void dropHiddenUniqueItems(final Player owner, final double contributionScale, final boolean personalDrop) {
		final ArrayList<Item> items = getWorld().getNpcDrops().rollHiddenUniqueDrops(this.getID(), contributionScale);
		for (Item item : items) {
			if (item == null || !worldAllowsDrop(item)) {
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

	private Item applyDeathNecklaceGuaranteedDropBonus(final Player owner, final Item item) {
		if (owner == null || item == null || item.getCatalogId() == ItemId.NOTHING.id() || item.getAmount() <= 0) {
			return item;
		}
		final int bonusItems = owner.getCarriedItems().getEquipment().rollDeathNecklaceGuaranteedDropBonus();
		if (bonusItems <= 0) {
			return item;
		}
		owner.playerServerMessage(MessageType.QUEST, "@ora@Your death necklace shines brightly");
		return new Item(item.getCatalogId(), item.getAmount() + bonusItems, item.getNoted());
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
					if (!owner.getCarriedItems().getEquipment().tryAlchemyMonsterLootWithNatureAmulet(item)) {
						GroundItem groundItem = createNpcGroundItem(owner, item.getCatalogId(), item.getAmount(), item.getNoted(), personalDrop);
						getWorld().registerItem(groundItem);
					}
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
		return getWorld().getServer().getCombatRandom().nextDouble() < scaledChance;
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

		if (owner.getCarriedItems().getEquipment().tryAlchemyMonsterLootWithNatureAmulet(new Item(dropID, amount))) {
			return;
		}
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
				if (owner.getCarriedItems().getEquipment().tryAlchemyMonsterLootWithNatureAmulet(new Item(dropID, amount, item.getNoted()))) {
					continue;
				}
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
		final ArrayList<UUID> damageDealerIds = getAllDamageDealerIds();
		final int totalCombatXP = getGroupCombatExperience(damageDealerIds);
		Pair<UUID, Long> topDamageDealer = getTopDamageDealer(attacker);

		for (UUID id : damageDealerIds) {
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

	private int getGroupCombatExperience(final ArrayList<UUID> damageDealerIds) {
		final int baseCombatXP = Formulae.combatExperience(this);
		final int contributorCount = getOnlineCombatContributorCount(damageDealerIds);
		if (contributorCount < 2) {
			return baseCombatXP;
		}

		final double multiplier = Math.min(
			GROUP_COMBAT_XP_MAX_MULTIPLIER,
			1.0D + GROUP_COMBAT_XP_STEP * contributorCount
		);
		return (int) (baseCombatXP * multiplier);
	}

	private int getOnlineCombatContributorCount(final ArrayList<UUID> damageDealerIds) {
		int contributorCount = 0;
		for (UUID id : damageDealerIds) {
			if (getWorld().getPlayerByUUID(id) != null) {
				contributorCount++;
			}
		}
		return contributorCount;
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

	private static final class PendingSummoningExperience {
		private final int experience;
		private final long expiresTick;

		private PendingSummoningExperience(final int experience, final long expiresTick) {
			this.experience = experience;
			this.expiresTick = expiresTick;
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

	/**
	 * Enters one short read-only preservation scope only when no NPC lifecycle
	 * operation is already in progress. New movement, behavior, removal, and
	 * respawn operations wait until the scope returns.
	 *
	 * <p>The operation must not invoke NPC lifecycle methods. This gate does not
	 * retain the NPC, stop an event, or create authority after it returns.</p>
	 */
	public final boolean withinLayeredOwnerPreservationLifecycleBoundary(
		final LayeredOwnerPreservationLifecycleOperation operation) {
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		return layeredOwnerLifecycleGate.withinPreservationBoundary(
			boundary -> operation.execute(
				new LayeredOwnerPreservationLifecycleBoundary(
					boundary.isPreservationGateActive(),
					boundary.getLifecycleOperationsAtEntry())));
	}

	/**
	 * Iteratively enters one all-or-nothing preservation scope for an exact
	 * stable-order NPC set.
	 *
	 * <p>If any owner is active, every earlier gate is released immediately and
	 * the operation is not invoked. This method adds no registry or retained
	 * owner handle; the caller's list remains valid only by its outer runtime
	 * boundary.</p>
	 */
	public static boolean
		withinLayeredOwnerPreservationLifecycleBoundaries(
			final List<Npc> owners,
			final LayeredOwnerPreservationLifecycleSetOperation operation) {
		if (owners == null) {
			throw new NullPointerException("owners");
		}
		if (operation == null) {
			throw new NullPointerException("operation");
		}
		List<NpcOwnerPreservationLifecycleGate> gates =
			new ArrayList<NpcOwnerPreservationLifecycleGate>(owners.size());
		for (int index = 0; index < owners.size(); index++) {
			Npc owner = owners.get(index);
			if (owner == null) {
				throw new NullPointerException("owners[" + index + "]");
			}
			gates.add(owner.layeredOwnerLifecycleGate);
		}
		return NpcOwnerPreservationLifecycleGate
			.withinPreservationBoundaries(gates, boundary ->
				operation.execute(
					new LayeredOwnerPreservationLifecycleSetBoundary(
						boundary.getOwnerCount(),
						boundary.isCompleteSetHeld())));
	}

	private void beginLayeredOwnerLifecycleOperation() {
		layeredOwnerLifecycleGate.beginOperation();
	}

	private void endLayeredOwnerLifecycleOperation() {
		layeredOwnerLifecycleGate.endOperation();
	}

	@FunctionalInterface
	public interface LayeredOwnerPreservationLifecycleOperation {
		void execute(
			LayeredOwnerPreservationLifecycleBoundary boundary);
	}

	@FunctionalInterface
	public interface LayeredOwnerPreservationLifecycleSetOperation {
		void execute(
			LayeredOwnerPreservationLifecycleSetBoundary boundary);
	}

	/** Closed facts valid only while one complete iterative owner set is held. */
	public static final class
		LayeredOwnerPreservationLifecycleSetBoundary {
		private final int ownerCount;
		private final boolean completeSetHeld;

		private LayeredOwnerPreservationLifecycleSetBoundary(
			final int ownerCount,
			final boolean completeSetHeld) {
			if (ownerCount < 0 || !completeSetHeld) {
				throw new IllegalArgumentException(
					"NPC owner preservation lifecycle set is invalid");
			}
			this.ownerCount = ownerCount;
			this.completeSetHeld = true;
		}

		public int getOwnerCount() { return ownerCount; }
		public boolean isCompleteSetHeld() { return completeSetHeld; }
		public boolean isPointInTimeOnly() { return true; }
		public boolean isPreservationFactEstablished() { return false; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	/** Closed facts valid only while the supplied operation is executing. */
	public static final class LayeredOwnerPreservationLifecycleBoundary {
		private final boolean preservationGateActive;
		private final int lifecycleOperationsAtEntry;

		private LayeredOwnerPreservationLifecycleBoundary(
			final boolean preservationGateActive,
			final int lifecycleOperationsAtEntry) {
			this.preservationGateActive = preservationGateActive;
			this.lifecycleOperationsAtEntry = lifecycleOperationsAtEntry;
			if (!preservationGateActive || lifecycleOperationsAtEntry != 0) {
				throw new IllegalArgumentException(
					"NPC owner preservation lifecycle boundary is invalid");
			}
		}

		public boolean isPreservationGateActive() {
			return preservationGateActive;
		}
		public int getLifecycleOperationsAtEntry() {
			return lifecycleOperationsAtEntry;
		}
		public boolean isPointInTimeOnly() { return true; }
		public boolean isPreservationFactEstablished() { return false; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	public void remove() {
		beginLayeredOwnerLifecycleOperation();
		try {
			removeWithinLayeredOwnerLifecycle();
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	private void removeWithinLayeredOwnerLifecycle() {
		final DeathContext deathContext = getActiveDeathContext();
		advanceCombatLifecycle(CombatEngagementTerminalReason.DESPAWN);
		setAttribute(DEATH_VISUAL_TICK_ATTRIBUTE, getWorld().getServer().getCurrentTick());
		// Removal is the authoritative end of this NPC lifetime, including
		// scripted and unattributed paths that do not complete killedBy().
		cure();
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
					n.beginLayeredOwnerLifecycleOperation();
					try {
						// The same NPC object is reused after its delay. Keep the new
						// lifetime clean even if a stale/late effect escaped removal.
						n.curePoison();
						n.killed = false;
						n.setRemoved(false);
						n.removeAttribute(DEATH_VISUAL_TICK_ATTRIBUTE);
						// Rejoin both authoritative logical membership and the
						// packed compatibility Region before moving home.
						n.updateRegion();

						// Take 4 ticks away from the current time to get a 1 tick pause while the npc spawns,
						// before it is allowed to attack (if aggressive).
						teleport(loc.startX, loc.startY);
						face(loc.startX, loc.startY - 1);
						setCombatTimer(-getConfig().GAME_TICK * 4);
						setRespawning(false);
						getSkills().normalize();
						tryResyncHitEvent();

						running = false;
						contributionLedger.clear();
						if (deathContext != null) {
							n.completeDeathLifecycleRespawn(deathContext);
						}
					} finally {
						n.endLayeredOwnerLifecycleOperation();
					}
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
		beginLayeredOwnerLifecycleOperation();
		try {
			shouldRespawn = respawn;
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	public boolean shouldRespawn() {
		return shouldRespawn;
	}

	@Override
	public void setLocation(final Point point, final boolean teleported) {
		beginLayeredOwnerLifecycleOperation();
		try {
			super.setLocation(point, teleported);
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	public void teleport(final int x, final int y) {
		terminateCombatOwnership(CombatEngagementTerminalReason.TELEPORT);
		setLocation(Point.location(x, y), true);
	}

	/** Relocates this NPC to an exact signed location. */
	public void teleport(final WorldLocation location) {
		WorldLocation destination = Objects.requireNonNull(location, "location");
		if (!getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			Point legacyDestination = LegacyPackedPointAdapter.toLegacyPoint(
				destination);
			teleport(legacyDestination.getX(), legacyDestination.getY());
			return;
		}
		beginLayeredOwnerLifecycleOperation();
		try {
			terminateCombatOwnership(CombatEngagementTerminalReason.TELEPORT);
			getWorld().getRegionManager().prepareNativeLayeredTransition(
				getWorldLocation(), destination, true);
			super.setWorldLocation(destination, true);
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	@Override
	public String toString() {
		return "[NPC:" + getIndex() + ":" + getDef().getName() + " @ (" + getX() + ", " + getY() + ")]";
	}

	/**
	 * Gets the NPC to move to an adjacent tile, with a priority system.
	 */
	public void moveToAdjacentTile() {
		beginLayeredOwnerLifecycleOperation();
		try {
			moveToAdjacentTileWithinLayeredOwnerLifecycle();
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	private void moveToAdjacentTileWithinLayeredOwnerLifecycle() {
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

		final int startIndex = DataConversions.random(0, possiblePoints.size() - 1);
		for (int i = 0; i < possiblePoints.size(); i++) {
			Point possiblePoint = possiblePoints.get((startIndex + i) % possiblePoints.size());
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
		beginLayeredOwnerLifecycleOperation();
		try {
			updateBehavior(hasPlayers);
			updateMovementOnly();
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	public void updateBehavior() {
		updateBehavior(getWorld().getPlayers().size() > 0);
	}

	public void updateBehavior(final boolean hasPlayers) {
		beginLayeredOwnerLifecycleOperation();
		try {
			updateBehaviorWithinLayeredOwnerLifecycle(hasPlayers);
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
	}

	private void updateBehaviorWithinLayeredOwnerLifecycle(
		final boolean hasPlayers) {
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
		beginLayeredOwnerLifecycleOperation();
		try {
			super.updatePosition();
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
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
		TileValue t = getTileAtCurrentLevel(x, y);
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

	/** Poison belongs to one live NPC lifetime and never crosses respawn. */
	public boolean canReceivePoison() {
		return !killed && !isRemoved() && !isRespawning();
	}

	private void setRespawning(final boolean isRespawning) {
		this.isRespawning = isRespawning;
	}

	public void superRemove() {
		beginLayeredOwnerLifecycleOperation();
		try {
			advanceCombatLifecycle(CombatEngagementTerminalReason.DESPAWN);
			super.remove();
		} finally {
			endLayeredOwnerLifecycleOperation();
		}
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
		final int lowX = Math.max(minX, currX - radius);
		final int highX = Math.min(maxX, currX + radius);
		final int lowY = Math.max(minY, currY - radius);
		final int highY = Math.min(maxY, currY + radius);
		if (lowX > highX || lowY > highY) {
			return Point.location(currX, currY);
		}
		final int newX = DataConversions.random(lowX, highX);
		final int newY = DataConversions.random(lowY, highY);
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
