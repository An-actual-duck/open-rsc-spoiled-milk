package com.openrsc.server.plugins.authentic.commands;

import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcDrops;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.worldedit.WorldEditorSessionManager;
import com.openrsc.server.content.worldedit.WorldEditorAccessService;
import com.openrsc.server.diagnostics.LayeredCoordinateParityObserver;
import com.openrsc.server.diagnostics.LayeredCoordinateParityObserver.PackedRegionEventRecoveryNoOpMetadata;
import com.openrsc.server.io.WorldEditorTerrainSaveFiles;
import com.openrsc.server.external.ObjectFishDef;
import com.openrsc.server.external.ObjectFishingDef;
import com.openrsc.server.external.ObjectWoodcuttingDef;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.world.WorldDayNightClock;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAttribution;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionTopologyAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionPreservationBurdenAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionDynamicObjectPreservationRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventAtomicTargetRevalidation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventTargetObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerEventContinuityAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementReadiness;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementReassessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementSafetyAssessment;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestResidencyComparison;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestOwnershipLedger;
import com.openrsc.server.model.world.coordinate.LayeredRegionResidencyMirror;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementDecisionArbiter;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementEligibilityLedger;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;
import com.openrsc.server.model.world.region.LayeredAdjacentStepCollisionComparison;
import com.openrsc.server.model.world.region.LayeredRegionTileSnapshot;
import com.openrsc.server.model.world.region.LayeredTileNeighborhoodParityComparison;
import com.openrsc.server.model.world.region.LayeredTileStateParityComparison;
import com.openrsc.server.model.world.region.LayeredTraversalCollisionComparison;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.authentic.quests.members.touristtrap.Tourist_Trap_Mechanism;
import com.openrsc.server.plugins.authentic.skills.fishing.Fishing;
import com.openrsc.server.plugins.authentic.skills.woodcutting.Woodcutting;
import com.openrsc.server.plugins.triggers.CommandTrigger;
import com.openrsc.server.util.MessageFilter;
import com.openrsc.server.util.WorldNpcEditFiles;
import com.openrsc.server.util.WorldSceneryEditFiles;
import com.openrsc.server.util.rsc.AppearanceRetroConverter;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static com.openrsc.server.plugins.Functions.*;

public final class Development implements CommandTrigger {
	private static final Logger LOGGER = LogManager.getLogger(Development.class);
	private static final LinkedHashMap<String, WorldSceneryEditFiles.Edit> PENDING_SCENERY_EDITS =
		new LinkedHashMap<String, WorldSceneryEditFiles.Edit>();
	private static final LinkedHashMap<String, WorldNpcEditFiles.Edit> PENDING_NPC_EDITS =
		new LinkedHashMap<String, WorldNpcEditFiles.Edit>();
	private static final HashMap<String, Integer> LAST_SCENERY_PLACEMENT_IDS =
		new HashMap<String, Integer>();

	public static String messagePrefix = null;
	public static String badSyntaxPrefix = null;

	public boolean blockCommand(Player player, String command, String[] args) {
		return player.isDev();
	}

	public static boolean abortFlag = false;

	/**
	 * Template for ::dev commands
	 * Development usable commands in general
	 */
	@Override
	public void onCommand(Player player, String command, String[] args) {
		if(messagePrefix == null) {
			messagePrefix = config().MESSAGE_PREFIX;
		}
		if(badSyntaxPrefix == null) {
			badSyntaxPrefix = config().BAD_SYNTAX_PREFIX;
		}

		if (command.equalsIgnoreCase("worldeditormode")) {
			openWorldEditor(player);
		}
		else if (command.equalsIgnoreCase("radiusnpc") || command.equalsIgnoreCase("createnpc") || command.equalsIgnoreCase("cnpc")|| command.equalsIgnoreCase("cpc")) {
			createNpc(player, command, args);
		}
		else if (command.equalsIgnoreCase("rpc") || command.equalsIgnoreCase("rnpc") || command.equalsIgnoreCase("removenpc")){
			removeNpc(player, command, args);
		}
		else if (command.equalsIgnoreCase("removeobject") || command.equalsIgnoreCase("robject") || command.equalsIgnoreCase("removescenery") || command.equalsIgnoreCase("rscenery")) {
			removeObject(player, command, args);
		}
		else if (command.equalsIgnoreCase("createobject") || command.equalsIgnoreCase("cobject") || command.equalsIgnoreCase("addobject") || command.equalsIgnoreCase("aobject") || command.equalsIgnoreCase("createscenery") || command.equalsIgnoreCase("cscenery") || command.equalsIgnoreCase("addscenery") || command.equalsIgnoreCase("ascenery")) {
			createObject(player, command, args);
		}
		else if (command.equalsIgnoreCase("r") || command.equalsIgnoreCase("repeatobject") || command.equalsIgnoreCase("repeatscenery")) {
			repeatLastSceneryObject(player, command, args);
		}
		else if (command.equalsIgnoreCase("createwallobject") || command.equalsIgnoreCase("cwallobject") || command.equalsIgnoreCase("addwallobject") || command.equalsIgnoreCase("awallobject") || command.equalsIgnoreCase("createboundary") || command.equalsIgnoreCase("cboundary") || command.equalsIgnoreCase("addboundary") || command.equalsIgnoreCase("aboundary")) {
			createWallObject(player, command, args);
		}
		else if (command.equalsIgnoreCase("rotateobject") || command.equalsIgnoreCase("rotatescenery")) {
			rotateObject(player, command, args);
		}
		else if (command.equalsIgnoreCase("worldedits") || command.equalsIgnoreCase("listworldedits")) {
			listWorldEdits(player);
		}
		else if (command.equalsIgnoreCase("saveworldedits")) {
			saveWorldEdits(player);
		}
		else if (command.equalsIgnoreCase("clearworldedits") || command.equalsIgnoreCase("discardworldedits")) {
			clearWorldEdits(player);
		}
		else if (command.equalsIgnoreCase("tile")) {
			tileInformation(player);
		}
		else if (command.equalsIgnoreCase("debugregion")) {
			regionInformation(player, command, args);
		}
		else if (command.equalsIgnoreCase("coords")) {
			currentCoordinates(player, args);
		}
		else if (command.equalsIgnoreCase("lp")
			|| command.equalsIgnoreCase("layerparity")
			|| command.equalsIgnoreCase("layeredparity")) {
			layeredCoordinateParity(player, command, args);
		}
		else if (command.equalsIgnoreCase("serverstats")) {
			serverStats(player, args);
		}
		else if (command.equalsIgnoreCase("settime")) {
			setWorldTime(player, command, args);
		}
		else if (command.equalsIgnoreCase("advtime")) {
			advanceWorldTime(player, command, args);
		}
		else if (command.equalsIgnoreCase("devotion")) {
			setDevotion(player, command, args);
		}
		else if (command.equalsIgnoreCase("error")) {
			// used to verify logging of errors/stdout
			System.out.println(args[0]);
		}
		else if (command.equalsIgnoreCase("droptest")) {
			testNpcDrops(player, command, args);
		}
		else if (command.equalsIgnoreCase("fishingRate")) {
			fishingRate(player, command, args);
		}
		else if (command.equalsIgnoreCase("setcombatstyle")) {
			setCombatStyle(player, args);
		}
		else if (command.equalsIgnoreCase("protodarts")) {
			protoDartTipsTest(player, args);
		}
		else if (command.equalsIgnoreCase("logRate")) {
			logRate(player, args);
		}
		else if (command.equalsIgnoreCase("points")) {
			points(player, args);
		}
		else if (command.equalsIgnoreCase("sound")) {
			playSound(player, args);
		}
		else if (command.equalsIgnoreCase("cyclescenery")) {
			cycleScenery(player, args);
		}
		else if (command.equalsIgnoreCase("cycleclothing")) {
			cycleClothing(player, args);
		}
		else if (command.equalsIgnoreCase("abort")) {
			setAbortFlag();
		}
		else if (command.equalsIgnoreCase("getappearance")) {
			dumpAppearance(player, args);
		}
		else if (command.equalsIgnoreCase("boundarydemo")) {
			showBoundaries(player, command, args);
		}
		else if (command.equalsIgnoreCase("scenerydemo")) {
			showScenery(player, command, args);
		}
		else if (command.equalsIgnoreCase("filtertest")) {
			filterTest(player, command, args, true);
		}
		else if (command.equalsIgnoreCase("summonbear")) {
			Summoning.summonTestBear(player);
		}
		else if (command.equalsIgnoreCase("summonrat")) {
			Summoning.summonTestRat(player);
		}
		else if (command.equalsIgnoreCase("summoncamel")) {
			Summoning.summonTestCamel(player);
		}
		else if (command.equalsIgnoreCase("summonunicorn")) {
			Summoning.summonTestUnicorn(player);
		}
		else if (command.equalsIgnoreCase("clearsummons")) {
			Summoning.dismissAll(player);
			player.message("Summons cleared.");
		}
		else if (command.equalsIgnoreCase("aggroall") || command.equalsIgnoreCase("aggronear") || command.equalsIgnoreCase("forceaggro")) {
			forceNearbyNpcAggro(player, command, args);
		}
		else if (command.equalsIgnoreCase("nearbynpcs") || command.equalsIgnoreCase("npcsnear") || command.equalsIgnoreCase("npcnear")) {
			listNearbyNpcs(player, command, args);
		}
		else if (command.equalsIgnoreCase("killnearnpcs") || command.equalsIgnoreCase("killnearcombat") || command.equalsIgnoreCase("killcombatnear")) {
			killNearbyCombatNpcs(player, command, args);
		}
	}

	private void setWorldTime(Player player, String command, String[] args) {
		int timeMillis = parseMinuteSecondArgument(player, command, args, false);
		if (timeMillis < 0) {
			return;
		}

		WorldDayNightClock clock = player.getWorld().getServer().getWorldDayNightClock();
		clock.setCurrentCycleMillis(timeMillis);
		syncWorldTimeToCustomClients(player);
		player.message(messagePrefix + "World time set to " + formatMinuteSecond(timeMillis) + ".");
	}

	private void advanceWorldTime(Player player, String command, String[] args) {
		int advanceMillis = parseMinuteSecondArgument(player, command, args, true);
		if (advanceMillis < 0) {
			return;
		}
		if (advanceMillis == 0) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " MMSS must be greater than 0000");
			return;
		}

		WorldDayNightClock clock = player.getWorld().getServer().getWorldDayNightClock();
		clock.advanceSmoothly(advanceMillis);
		syncWorldTimeToCustomClients(player);
		player.message(messagePrefix + "World time advancing by " + formatMinuteSecond(advanceMillis)
			+ " at " + WorldDayNightClock.ADVANCE_RATE_MULTIPLIER + "x.");
	}

	private int parseMinuteSecondArgument(Player player, String command, String[] args, boolean allowHourOverflow) {
		if (args.length != 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " MMSS");
			return -1;
		}

		String value = args[0].trim();
		if (value.length() == 0 || value.length() > 4) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " MMSS");
			return -1;
		}

		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " MMSS");
				return -1;
			}
		}

		int split = Math.max(0, value.length() - 2);
		int minutes = split == 0 ? 0 : Integer.parseInt(value.substring(0, split));
		int seconds = Integer.parseInt(value.substring(split));
		if (seconds >= 60 || (!allowHourOverflow && minutes >= 60)) {
			player.message(badSyntaxPrefix + command.toUpperCase()
				+ (allowHourOverflow ? " MMSS (seconds must be 00-59)" : " MMSS (00:00-59:59)"));
			return -1;
		}

		return (minutes * 60 + seconds) * 1000;
	}

	private void syncWorldTimeToCustomClients(Player sourcePlayer) {
		for (Player onlinePlayer : sourcePlayer.getWorld().getPlayers()) {
			ActionSender.sendWorldTime(onlinePlayer);
		}
	}

	private String formatMinuteSecond(int millis) {
		int totalSeconds = Math.max(0, millis / 1000);
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return twoDigits(minutes) + ":" + twoDigits(seconds);
	}

	private String twoDigits(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private void setDevotion(Player player, String command, String[] args) {
		if (args.length != 1 && args.length != 2) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [value] OR ::" + command + " [player] [value]");
			return;
		}

		Player targetPlayer = player;
		String valueText = args[0];
		if (args.length == 2) {
			targetPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
			valueText = args[1];
			if (targetPlayer == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}
		}

		final int devotionLevel;
		try {
			devotionLevel = Integer.parseInt(valueText);
		} catch (NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [value] OR ::" + command + " [player] [value]");
			return;
		}

		if (devotionLevel < Devotion.MIN_DEVOTION_LEVEL || devotionLevel > Devotion.MAX_DEVOTION_LEVEL) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " devotion must be between "
				+ Devotion.MIN_DEVOTION_LEVEL + " and " + Devotion.MAX_DEVOTION_LEVEL);
			return;
		}

		final PrayerCatalog.GodLine godLine = targetPlayer.getPrayerBook();
		Devotion.setDevotionLevel(targetPlayer, godLine, devotionLevel);
		final int updatedDevotion = Devotion.getDevotionLevel(targetPlayer, godLine);
		player.message(messagePrefix + targetPlayer.getUsername() + "'s " + formatGodLine(godLine)
			+ " devotion set to " + updatedDevotion + ".");
		if (targetPlayer != player) {
			targetPlayer.message(messagePrefix + "Your " + formatGodLine(godLine)
				+ " devotion was set to " + updatedDevotion + ".");
		}
	}

	private String formatGodLine(PrayerCatalog.GodLine godLine) {
		final PrayerCatalog.GodLine safeGodLine = godLine == null ? PrayerCatalog.getDefaultGodLine() : godLine;
		final String lower = safeGodLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private void forceNearbyNpcAggro(Player player, String command, String[] args) {
		int radius = parseNearbyNpcRadius(player, command, args);
		if (radius < 0) {
			return;
		}

		int forced = 0;
		int skipped = 0;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc == null
				|| npc.isRemoved()
				|| npc.isRespawning()
				|| npc.getDef() == null
				|| !npc.getDef().isAttackable()
				|| Summoning.isSummon(npc)
				|| npc.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !player.withinRange(npc, radius)) {
				skipped++;
				continue;
			}
			npc.startCombat(player);
			forced++;
		}

		player.message("Forced " + forced + " nearby NPCs to attack within " + radius + " tiles.");
		if (skipped > 0) {
			player.message("Skipped " + skipped + " NPCs that were out of range or not valid attackers.");
		}
	}

	private void listNearbyNpcs(Player player, String command, String[] args) {
		int radius = parseNearbyNpcRadius(player, command, args);
		if (radius < 0) {
			return;
		}

		int listed = 0;
		int total = 0;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (!isNearbyNpcCandidate(player, npc, radius)) {
				continue;
			}
			total++;
			if (listed >= 12) {
				continue;
			}
			player.message(formatNearbyNpcLine(player, npc));
			listed++;
		}

		if (total == 0) {
			player.message(messagePrefix + "No NPCs found within " + radius + " tiles.");
		} else if (total > listed) {
			player.message(messagePrefix + "Listed " + listed + " of " + total + " nearby NPCs.");
		} else {
			player.message(messagePrefix + "Listed " + listed + " nearby NPCs.");
		}
	}

	private void killNearbyCombatNpcs(Player player, String command, String[] args) {
		int radius = parseNearbyNpcRadius(player, command, args);
		if (radius < 0) {
			return;
		}

		int killed = 0;
		int skipped = 0;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (!isNearbyCombatNpcCandidate(player, npc, radius)) {
				skipped++;
				continue;
			}
			int damage = Math.max(1, npc.getSkills().getLevel(Skill.HITS.id()));
			npc.addCombatDamage(player, Math.max(damage, npc.getDef().getHits()));
			npc.getUpdateFlags().setDamage(new Damage(npc, damage));
			npc.getSkills().setLevel(Skill.HITS.id(), 0);
			if (npc.killed) {
				npc.killed = false;
			}
			npc.killedBy(player);
			killed++;
		}

		player.message(messagePrefix + "Killed " + killed + " nearby combat NPCs within " + radius + " tiles.");
		if (skipped > 0) {
			player.message(messagePrefix + "Skipped " + skipped + " NPCs that were out of range or not valid combat NPCs.");
		}
	}

	private int parseNearbyNpcRadius(Player player, String command, String[] args) {
		int radius = 8;
		if (args.length >= 1) {
			try {
				radius = Integer.parseInt(args[0]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (radius)");
				return -1;
			}
		}
		if (radius < 1) {
			return 1;
		}
		return Math.min(radius, 20);
	}

	private boolean isNearbyNpcCandidate(Player player, Npc npc, int radius) {
		return npc != null
			&& !npc.isRemoved()
			&& player.withinRange(npc, radius);
	}

	private boolean isNearbyCombatNpcCandidate(Player player, Npc npc, int radius) {
		return isNearbyNpcCandidate(player, npc, radius)
			&& !npc.isRespawning()
			&& npc.getDef() != null
			&& npc.getDef().isAttackable()
			&& !Summoning.isSummon(npc)
			&& npc.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private String formatNearbyNpcLine(Player player, Npc npc) {
		String npcName = npc.getDef() == null ? "unknown" : npc.getDef().getName();
		String state = npc.isRespawning()
			? "respawning"
			: npc.inCombat()
				? "combat"
				: npc.getDef() != null && npc.getDef().isAggressive()
					? "aggressive"
					: "idle";
		String combat = npc.getDef() != null && npc.getDef().isAttackable() ? "combat" : "noncombat";
		int distance = Math.max(Math.abs(player.getX() - npc.getX()), Math.abs(player.getY() - npc.getY()));
		int hits = npc.getDef() == null ? 0 : npc.getDef().getHits();
		return messagePrefix
			+ "#" + npc.getIndex()
			+ " id=" + npc.getID()
			+ " " + npcName
			+ " hp=" + npc.getSkills().getLevel(Skill.HITS.id()) + "/" + hits
			+ " " + combat
			+ " " + state
			+ " d=" + distance
			+ " @ " + npc.getX() + "," + npc.getY();
	}

	private void filterTest(Player player, String command, String[] args, boolean production) {
		if (production) {
			player.message("disabled on production; recompile with production bool false to test");
			return;
		}
		if (!MessageFilter.badwordsContains("ass")) {
			MessageFilter.addBadWord("ass");
		}
		if (!MessageFilter.badwordsContains("clown")) {
			MessageFilter.addBadWord("clown");
		}
		if (!MessageFilter.badwordsContains("suck")) {
			MessageFilter.addBadWord("suck");
		}
		if (!MessageFilter.badwordsContains("hell")) {
			MessageFilter.addBadWord("hell");
		}
		if (!MessageFilter.badwordsContains("cow")) {
			MessageFilter.addBadWord("cow");
		}

		if (!MessageFilter.goodwordsContains("class")) {
			MessageFilter.addGoodWord("class");
		}
		if (!MessageFilter.goodwordsContains("sucks")) {
			MessageFilter.addGoodWord("sucks");
		}
		if (!MessageFilter.goodwordsContains("hello")) {
			MessageFilter.addGoodWord("hello");
		}
		if (!MessageFilter.goodwordsContains("one")) {
			MessageFilter.addGoodWord("one");
		}

		final String[] testStrings = {
			"Hello",
			"Hey there Hello!",
			"Sucks to be y0u, clown",
			"Class clown",
			"Runescape Classic",
			"Runescape classic",
			"(()vv",
			"( ()v v",
			"( () ___ vv",
			"Holy (0vv",
			"c 0 w",
			"( 0 w",
			"pre c 0 w cw0 co vv post",
			"Holy hell",
			"I am a (ow irl",
			"H.O.L.Y. C.O.W!",
			"H.O.L.Y. (!0!W!",
			"cow c o w c o w c co c co w ass COW coassw hello hell clown (0w ( 0 w yeah",
			"c@ran@ow",
			"@ran@",
			"@cow@",
			"you are a @cow@",
			"Hi everyone, how is everyone doing?",
			"one one hell hello one one cow class sucks class hello"
		};
		for (String testString : testStrings) {
			player.playerServerMessage(MessageType.QUEST, "@red@" + testString);
			player.playerServerMessage(MessageType.QUEST, "@gre@" + MessageFilter.filter(player, testString, "filtertest"));
			delay();
		}

	}

	private void serverStats(Player player, String[] args) {
		if (player.getConfig().WANT_DISCORD_MONITORING_UPDATES) {
			player.getWorld().getServer().getDiscordService().monitoringSendServerBehind(
				"Profiling information requested by **" + player.getUsername() + "** for world **" + player.getWorld().getServer().getName() + "**:\n\n" +
				player.getWorld().getServer().getGameEventHandler().buildProfilingDebugInformation(false)
				, false);
		}
		ActionSender.sendBox(player, player.getWorld().getServer().getGameEventHandler().buildProfilingDebugInformation(true),true);
	}

	private void showBoundaries(Player player, String command, String[] args) {
		int boundariesInARow = player.getClientLimitations().maxBoundaryId;
		if (args.length >= 1) {
			try {
				int candidateBoundariesInARow = Integer.parseInt(args[0]);
				if (candidateBoundariesInARow > 0) {
					boundariesInARow = candidateBoundariesInARow;
				}
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (boundaries in a row) (limit) (spacing)");
				return;
			}
		}

		int limit = player.getClientLimitations().maxBoundaryId;
		if (args.length >= 2) {
			try {
				int candidateLimit = Integer.parseInt(args[1]);
				limit = Math.min(candidateLimit, player.getClientLimitations().maxBoundaryId);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (boundaries in a row) (limit) (spacing)");
				return;
			}
		}

		int spacing = 2;
		if (args.length >= 3) {
			try {
				int candidateSpacing = Integer.parseInt(args[2]);
				if (candidateSpacing > 0) {
					spacing = candidateSpacing;
				}
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (boundaries in a row) (limit) (spacing)");
				return;
			}
		}

		int id = 0;
		for (int y = player.getY(); id < limit; y += spacing) {
			for (int x = player.getX(); x < boundariesInARow + player.getX() && id < limit; x++) {
				final GameObject newObject = new GameObject(player.getWorld(), Point.location(x, y), id++, 0, 1);
				player.getWorld().registerGameObject(newObject);
			}
		}
	}

	private void showScenery(Player player, String command, String[] args) {
		int sceneryInARow = player.getClientLimitations().maxSceneryId;
		if (args.length >= 1) {
			try {
				int candidateBoundariesInARow = Integer.parseInt(args[0]);
				if (candidateBoundariesInARow > 0) {
					sceneryInARow = candidateBoundariesInARow;
				}
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (scenery in a row) (limit) (spacing)");
				return;
			}
		}

		int limit = player.getClientLimitations().maxSceneryId;
		if (args.length >= 2) {
			try {
				int candidateLimit = Integer.parseInt(args[1]);
				limit = Math.min(candidateLimit, player.getClientLimitations().maxSceneryId);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (scenery in a row) (limit) (spacing)");
				return;
			}
		}

		int spacing = 2;
		if (args.length >= 3) {
			try {
				int candidateSpacing = Integer.parseInt(args[2]);
				if (candidateSpacing > 0) {
					spacing = candidateSpacing;
				}
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (scenery in a row) (limit) (spacing)");
				return;
			}
		}

		int id = 0;
		for (int y = player.getY(); id < limit; y += spacing) {
			for (int x = player.getX(); x < (sceneryInARow * spacing) + player.getX() && id < limit; x += spacing) {
				final GameObject newObject = new GameObject(player.getWorld(), Point.location(x, y), id++, 0, 0);
				player.getWorld().registerGameObject(newObject);
			}
		}

	}

	private void dumpAppearance(Player player, String[] args) {
		for (int i = 0; i < player.getSettings().getAppearance().getSprites().length; i++) {
			mes(i + ": " +  player.getSettings().getAppearance().getSprites()[i]);
		}
		mes("Top color: " + player.getSettings().getAppearance().getTopColour());
		mes("Trouser color: " + player.getSettings().getAppearance().getTrouserColour());
	}

	private void openWorldEditor(Player player) {
		WorldEditorAccessService.open(player);
	}

	private void createNpc(Player player, String command, String[] args) {
		if (args.length < 2 || args.length == 3) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] [radius] (x) (y)");
			return;
		}

		int id = -1;
		try {
			id = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] [radius] (x) (y)");
			return;
		}

		int radius = -1;
		try {
			radius = Integer.parseInt(args[1]);
		} catch (NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] [radius] (x) (y)");
			return;
		}
		if (radius < 0) {
			player.message(messagePrefix + "NPC radius must be 0 or greater.");
			return;
		}

		int x = -1;
		int y = -1;
		if(args.length >= 4) {
			try {
				x = Integer.parseInt(args[2]);
				y = Integer.parseInt(args[3]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [id] [radius] (x) (y)");
				return;
			}
		}
		else {
			x = player.getX();
			y = player.getY();
		}

		if(!player.getWorld().withinWorld(x, y))
		{
			player.message(messagePrefix + "Invalid coordinates");
			return;
		}

		Point npcLoc = new Point(x,y);
		final Npc n = new Npc(player.getWorld(), id, x, y, x - radius, x + radius, y - radius, y + radius);

		if (player.getWorld().getServer().getEntityHandler().getNpcDef(id) == null) {
			player.message(messagePrefix + "Invalid npc id");
			return;
		}

		player.getWorld().registerNpc(n);
		n.setShouldRespawn(true);
		queueWorldNpcUpsert(player, n.getLoc());
		player.message(messagePrefix + "Added NPC: " + n.getDef().getName() + " at " + npcLoc + " with radius " + radius);
	}

	private void removeNpc(Player player, String command, String[] args) {
		if (args.length < 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [npc_instance_id]");
			return;
		}

		int id = -1;
		try {
			id = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [npc_instance_id]");
			return;
		}

		Npc npc = player.getWorld().getNpc(id);

		if(npc == null) {
			player.message(messagePrefix + "Invalid npc instance id");
			return;
		}

		player.message(messagePrefix + "Removed NPC: " + npc.getDef().getName() + " with instance ID " + id);
		queueWorldNpcRemoval(player, npc.getLoc());
		player.getWorld().unregisterNpc(npc);
	}

	private void createObject(Player player, String command, String[] args) {
		if (args.length < 1 || args.length == 2) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (x) (y)");
			return;
		}

		int id = -1;
		try {
			id = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (x) (y)");
			return;
		}

		int x = -1;
		int y = -1;
		if(args.length >= 3) {
			try {
				x = Integer.parseInt(args[1]);
				y = Integer.parseInt(args[2]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (x) (y)");
				return;
			}
		}
		else {
			x = player.getX();
			y = player.getY();
		}

		if(!player.getWorld().withinWorld(x, y))
		{
			player.message(messagePrefix + "Invalid coordinates");
			return;
		}

		Point objectLoc = Point.location(x, y);
		final GameObject object = player.getViewArea().getGameObject(objectLoc);

		if (object != null && object.getType() != 1) {
			player.message("There is already scenery in that spot: " + object.getGameObjectDef().getName());
			return;
		}

		if (player.getWorld().getServer().getEntityHandler().getGameObjectDef(id) == null) {
			player.message(messagePrefix + "Invalid scenery id");
			return;
		}

		final GameObject newObject = new GameObject(player.getWorld(), Point.location(x, y), id, 0, 0);

		player.getWorld().registerGameObject(newObject);
		queueWorldSceneryUpsert(player, newObject);
		rememberLastSceneryPlacement(player, id);
		player.message(messagePrefix + "Added scenery: " + newObject.getGameObjectDef().getName() + " with ID " + newObject.getID() + " at " + newObject.getLocation());
	}

	private void repeatLastSceneryObject(Player player, String command, String[] args) {
		if (args.length != 0) {
			player.message(badSyntaxPrefix + command.toUpperCase());
			return;
		}

		Integer id;
		synchronized (LAST_SCENERY_PLACEMENT_IDS) {
			id = LAST_SCENERY_PLACEMENT_IDS.get(player.getUsername());
		}

		if (id == null) {
			player.message(messagePrefix + "No scenery placement to repeat. Use ::addobject [id] first.");
			return;
		}

		createObject(player, "addobject", new String[] { String.valueOf(id) });
	}

	private void rememberLastSceneryPlacement(Player player, int id) {
		synchronized (LAST_SCENERY_PLACEMENT_IDS) {
			LAST_SCENERY_PLACEMENT_IDS.put(player.getUsername(), id);
		}
	}

	private void createWallObject(Player player, String command, String[] args) {
		if (args.length < 1 || args.length == 3) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (dir) (x) (y)");
			return;
		}

		int id = -1;
		try {
			id = Integer.parseInt(args[0]);
		}
		catch(NumberFormatException ex) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (dir) (x) (y)");
			return;
		}

		int dir = 0;
		if (args.length >= 2) {
			try {
				dir = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (dir) (x) (y)");
				return;
			}
		}

		int x = -1;
		int y = -1;
		if(args.length >= 4) {
			try {
				x = Integer.parseInt(args[2]);
				y = Integer.parseInt(args[3]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " [id] (dir) (x) (y)");
				return;
			}
		}
		else {
			x = player.getX();
			y = player.getY();
		}

		if(!player.getWorld().withinWorld(x, y))
		{
			player.message(messagePrefix + "Invalid coordinates");
			return;
		}


		Point objectLoc = Point.location(x, y);
		final GameObject object = player.getViewArea().getGameObject(objectLoc);

		if (object != null && object.getType() == 1) {
			player.message("There is already a boundary in that spot: " + object.getGameObjectDef().getName());
			return;
		}

		/* TODO: check boundary id is within bounds properly per server & not per client
		if (player.getWorld().getServer().getEntityHandler().getGameObjectDef(id) == null) {
			player.message(messagePrefix + "Invalid scenery id");
			return;
		}*/
		if (id > player.getClientLimitations().maxBoundaryId) {
			player.message(messagePrefix + "Invalid boundary id");
			return;
		}

		final GameObject newObject = new GameObject(player.getWorld(), Point.location(x, y), id, dir, 1);

		player.getWorld().registerGameObject(newObject);
		player.message(messagePrefix + "Added boundary: " + newObject.getGameObjectDef().getName() + " with ID " + newObject.getID() + " at " + newObject.getLocation());
	}

	private void removeObject(Player player, String command, String[] args) {
		if(args.length == 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y)");
			return;
		}

		int x = -1;
		if(args.length >= 1) {
			try {
				x = Integer.parseInt(args[0]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y)");
				return;
			}
		} else {
			x = player.getX();
		}

		int y = -1;
		if(args.length >=2) {
			try {
				y = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y)");
				return;
			}
		} else {
			y = player.getY();
		}

		if(!player.getWorld().withinWorld(x, y))
		{
			player.message(messagePrefix + "Invalid coordinates");
			return;
		}

		final Point objectLocation = Point.location(x, y);
		final GameObject object = player.getViewArea().getGameObject(objectLocation);

		if(object == null)
		{
			player.message(messagePrefix + "There is no scenery at coordinates " + objectLocation);
			return;
		}

		player.message(messagePrefix + "Removed scenery: " + object.getGameObjectDef().getName() + " with ID " + object.getID());
		player.getWorld().unregisterGameObject(object);
		queueWorldSceneryRemoval(player, object);
	}

	private void cycleScenery(Player player, String[] args) {
		// render player invisible
		for (int i = 0; i < 12; i++) {
			player.updateWornItems(i, 0);
		}
		player.toggleDenyAllLogoutRequests();

		player.message("Now displaying all scenery in RSC in 5 second intervals.");

		int maxScenery;
		if (player.getConfig().RESTRICT_SCENERY_ID >= 0) {
			maxScenery = Math.min(player.getClientLimitations().maxSceneryId, player.getConfig().RESTRICT_SCENERY_ID);
		} else {
			maxScenery = player.getClientLimitations().maxSceneryId;
		}
		for (int id = 0; id <= maxScenery; id++) {
			GameObject object = player.getViewArea().getGameObject(player.getLocation());
			if (object != null) {
				player.getWorld().unregisterGameObject(object);
			}
			GameObject newObject = new GameObject(player.getWorld(), player.getLocation(), id, 0, 0);
			player.getWorld().registerGameObject(newObject);
			player.message("scenery id: " + id);
			delay(8);
			if (abortFlag) {
				player.message("Aborting cycle!");
				abortFlag = false;
				return;
			}
		}
		player.message("That is all of the scenery in RSC!");
		player.message("If you'd like to see it lit from a different angle, I'd suggest editing map tile " + player.getLocation().pointToJagexPoint());
		player.message("Then play this same replay again.");
		delay(8);
		player.toggleDenyAllLogoutRequests();
	}

	private void cycleClothing(Player player, String[] args) {
		// render player invisible
		for (int i = 0; i < 12; i++) {
			player.updateWornItems(i, 0);
		}
		player.toggleDenyAllLogoutRequests();

		boolean isRetroClient = player.isUsing38CompatibleClient() || player.isUsing39CompatibleClient();
		int delayLen = Integer.parseInt(args[0]);

		player.message("Now displaying all animations in RSC in 5 second intervals.");

		for (int id = 0; id <= player.getClientLimitations().maxAnimationId; id++) {
			player.message("animation id: " + (isRetroClient ? AppearanceRetroConverter.convert(id) : id));
			player.updateWornItems(AppearanceId.SLOT_BODY, id);
			delay(delayLen);
			if (abortFlag) {
				player.message("Aborting cycle!");
				abortFlag = false;
				return;
			}
		}
		player.message("That is all of the animations in RSC!");
		delay(8);
		player.toggleDenyAllLogoutRequests();
	}

	private void setAbortFlag() { abortFlag = true; }

	private void rotateObject(Player player, String command, String[] args) {
		if(args.length == 1) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y) (direction)");
			return;
		}

		int x = -1;
		if(args.length >= 1) {
			try {
				x = Integer.parseInt(args[0]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y) (direction)");
				return;
			}
		} else {
			x = player.getX();
		}

		int y = -1;
		if(args.length >= 2) {
			try {
				y = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y) (direction)");
				return;
			}
		} else {
			y = player.getY();
		}

		if (!player.getWorld().getServer().getConfig().WANT_CUSTOM_LANDSCAPE) {
			player.message(messagePrefix + "@red@Warning: @dre@This function will only work for inauthentic clients!");
			player.message("@dre@It is not possible to dynamically rotate scenery under any authentic protocol of RSC.");
		}

		if(!player.getWorld().withinWorld(x, y))
		{
			player.message(messagePrefix + "Invalid coordinates");
			return;
		}

		final Point objectLocation = Point.location(x, y);
		final GameObject object = player.getViewArea().getGameObject(objectLocation);

		if(object == null)
		{
			player.message(messagePrefix + "There is no object at coordinates " + objectLocation);
			return;
		}

		int direction = -1;
		if(args.length >= 3) {
			try {
				direction = Integer.parseInt(args[2]);
			} catch (NumberFormatException ex) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (x) (y) (direction)");
				return;
			}
		} else {
			direction = object.getDirection() + 1;
		}

		direction %= 8;
		direction = Math.abs(direction);

		player.getWorld().unregisterGameObject(object);

		GameObject newObject = new GameObject(player.getWorld(), Point.location(x, y), object.getID(), direction, object.getType());
		player.getWorld().registerGameObject(newObject);
		queueWorldSceneryUpsert(player, newObject);

		player.message(messagePrefix + "Rotated object: " + newObject.getGameObjectDef().getName() + " to rotation " + newObject.getDirection() + " with instance ID " + newObject.getID() + " at " + newObject.getLocation());
	}

	private void queueWorldSceneryUpsert(Player player, GameObject object) {
		queueWorldSceneryEdit(player, WorldSceneryEditFiles.Edit.upsert(
			object.getID(),
			object.getX(),
			object.getY(),
			object.getDirection(),
			object.getType()
		));
	}

	private void queueWorldSceneryRemoval(Player player, GameObject object) {
		queueWorldSceneryEdit(player, WorldSceneryEditFiles.Edit.remove(
			object.getID(),
			object.getX(),
			object.getY(),
			object.getDirection(),
			object.getType()
		));
	}

	private void queueWorldNpcUpsert(Player player, NPCLoc loc) {
		queueWorldNpcEdit(player, WorldNpcEditFiles.Edit.upsert(loc));
	}

	private void queueWorldNpcRemoval(Player player, NPCLoc loc) {
		queueWorldNpcEdit(player, WorldNpcEditFiles.Edit.remove(loc));
	}

	private void queueWorldNpcEdit(Player player, WorldNpcEditFiles.Edit edit) {
		int pendingCount;
		synchronized (PENDING_NPC_EDITS) {
			PENDING_NPC_EDITS.put(edit.key(), edit);
			pendingCount = PENDING_NPC_EDITS.size();
		}
		player.message(messagePrefix + "Queued NPC world edit. Pending NPC edits: " + pendingCount
			+ ". Use ::saveworldedits to persist.");
	}

	private void queueWorldSceneryEdit(Player player, WorldSceneryEditFiles.Edit edit) {
		if (edit.type != 0) {
			player.message(messagePrefix + "World edit persistence currently supports scenery objects only.");
			return;
		}

		int pendingCount;
		synchronized (PENDING_SCENERY_EDITS) {
			PENDING_SCENERY_EDITS.put(edit.key(), edit);
			pendingCount = PENDING_SCENERY_EDITS.size();
		}
		player.message(messagePrefix + "Queued world edit. Pending edits: " + pendingCount + ". Use ::saveworldedits to persist.");
	}

	private void listWorldEdits(Player player) {
		List<WorldSceneryEditFiles.Edit> edits;
		List<WorldNpcEditFiles.Edit> npcEdits;
		synchronized (PENDING_SCENERY_EDITS) {
			edits = new ArrayList<WorldSceneryEditFiles.Edit>(PENDING_SCENERY_EDITS.values());
		}
		synchronized (PENDING_NPC_EDITS) {
			npcEdits = new ArrayList<WorldNpcEditFiles.Edit>(PENDING_NPC_EDITS.values());
		}

		int terrainEdits=player.getWorld().getServer().getWorldEditorSessions().terrainDraftSize();
		int terrainSectors=player.getWorld().getServer().getWorldEditorSessions().terrainDraftSectorCount();
		if (edits.isEmpty() && npcEdits.isEmpty() && terrainEdits==0) {
			player.message(messagePrefix + "No pending world edits.");
			return;
		}

		player.message(messagePrefix + "Pending world edits: terrain " + terrainEdits+" tiles / "+terrainSectors+" sectors, scenery " + edits.size()
			+ ", NPCs " + npcEdits.size() + ".");
		int shown = 0;
		for (WorldSceneryEditFiles.Edit edit : edits) {
			if (shown >= 8) {
				player.message(messagePrefix + "...and " + (edits.size() + npcEdits.size() - shown) + " more.");
				return;
			}
			player.message(messagePrefix + edit.describe());
			shown++;
		}
		for (WorldNpcEditFiles.Edit edit : npcEdits) {
			if (shown >= 8) {
				player.message(messagePrefix + "...and " + (edits.size() + npcEdits.size() - shown) + " more.");
				return;
			}
			player.message(messagePrefix + edit.describe());
			shown++;
		}
	}

	private void saveWorldEdits(Player player) {
		List<WorldSceneryEditFiles.Edit> edits;
		List<WorldNpcEditFiles.Edit> npcEdits;
		synchronized (PENDING_SCENERY_EDITS) {
			edits = new ArrayList<WorldSceneryEditFiles.Edit>(PENDING_SCENERY_EDITS.values());
		}
		synchronized (PENDING_NPC_EDITS) {
			npcEdits = new ArrayList<WorldNpcEditFiles.Edit>(PENDING_NPC_EDITS.values());
		}

		WorldEditorSessionManager editor=player.getWorld().getServer().getWorldEditorSessions();int terrainEdits=editor.terrainDraftSize();
		if (edits.isEmpty() && npcEdits.isEmpty() && terrainEdits==0) {
			player.message(messagePrefix + "No pending world edits to save.");
			return;
		}
		if(terrainEdits>0&&!editor.ownsActiveSession(player)){player.message(messagePrefix+"Open and own ::worldeditormode before saving the terrain draft.");return;}

		try {
			WorldEditorTerrainSaveFiles.SaveResult terrainResult=terrainEdits==0?null:editor.saveTerrainDraft(player);
			WorldSceneryEditFiles.SaveResult sceneryResult = null;
			WorldNpcEditFiles.SaveResult npcResult = null;
			com.openrsc.server.content.worldedit.WorldEditStorageContext storage = player.getWorld().getServer().getWorldEditStorage();
			java.nio.file.Path configDir = storage.configDirectory();
			if (!edits.isEmpty()) {
				storage.validateWorkingAuthoredFile(WorldSceneryEditFiles.sceneryLocsPath(configDir));
				storage.validateWorkingAuthoredFile(WorldSceneryEditFiles.sceneryRemovalsPath(configDir));
				sceneryResult = WorldSceneryEditFiles.save(configDir, edits);
				synchronized (PENDING_SCENERY_EDITS) {
					for (WorldSceneryEditFiles.Edit edit : edits) {
						PENDING_SCENERY_EDITS.remove(edit.key());
					}
				}
			}
			if (!npcEdits.isEmpty()) {
				storage.validateWorkingAuthoredFile(WorldNpcEditFiles.npcLocsPath(configDir));
				storage.validateWorkingAuthoredFile(WorldNpcEditFiles.npcRemovalsPath(configDir));
				npcResult = WorldNpcEditFiles.save(configDir, npcEdits);
				synchronized (PENDING_NPC_EDITS) {
					for (WorldNpcEditFiles.Edit edit : npcEdits) {
						PENDING_NPC_EDITS.remove(edit.key());
					}
				}
			}
			int saved = (terrainResult == null ? 0 : terrainResult.tilesSaved)+(sceneryResult == null ? 0 : sceneryResult.editsApplied)
				+ (npcResult == null ? 0 : npcResult.editsApplied);
			player.message(messagePrefix + "Saved " + saved + " world edits.");
			if(terrainResult!=null){
				player.message(messagePrefix+"Terrain: "+terrainResult.tilesSaved+" tiles across "+terrainResult.sectorsChanged+" sectors; hash "+terrainResult.resultSha256.substring(0,12)+".");
				LOGGER.info(player.getUsername()+" saved "+terrainResult.tilesSaved+" terrain edits to "+terrainResult.serverArchive+" and "+terrainResult.clientArchive+"; backup "+terrainResult.backupArchive+"; sha256 "+terrainResult.resultSha256);
			}
			if (sceneryResult != null) {
				player.message(messagePrefix + "Scenery locs: " + sceneryResult.sceneryLocsWritten
					+ ", removals: " + sceneryResult.removalsWritten + ".");
				LOGGER.info(player.getUsername() + " saved " + sceneryResult.editsApplied + " world scenery edits to "
					+ sceneryResult.sceneryLocsPath + " and " + sceneryResult.removalsPath);
			}
			if (npcResult != null) {
				player.message(messagePrefix + "NPC locs: " + npcResult.npcLocsWritten
					+ ", removals: " + npcResult.removalsWritten + ".");
				LOGGER.info(player.getUsername() + " saved " + npcResult.editsApplied + " world NPC edits to "
					+ npcResult.npcLocsPath + " and " + npcResult.removalsPath);
			}
		} catch (Exception e) {
			LOGGER.error(e);
			player.message(messagePrefix + "Failed to save world edits: " + e.getMessage());
		}
	}

	private void clearWorldEdits(Player player) {
		int count;
		int npcCount;
		synchronized (PENDING_SCENERY_EDITS) {
			count = PENDING_SCENERY_EDITS.size();
			PENDING_SCENERY_EDITS.clear();
		}
		synchronized (PENDING_NPC_EDITS) {
			npcCount = PENDING_NPC_EDITS.size();
			PENDING_NPC_EDITS.clear();
		}
		int terrainCount=player.getWorld().getServer().getWorldEditorSessions().terrainDraftSize();
		player.message(messagePrefix + "Cleared " + (count + npcCount)
			+ " pending entity edits. Live entities were not reverted."+(terrainCount>0?" Terrain draft retained: "+terrainCount+" tiles.":""));
	}

	private void tileInformation(Player player) {
		TileValue tv = player.getWorld().getTile(player.getLocation());
		player.message(messagePrefix + "traversal: " + tv.traversalMask + ", vertVal:" + (tv.verticalWallVal & 0xff) + ", horiz: "
			+ (tv.horizontalWallVal & 0xff) + ", diagVal: " + (tv.diagWallVal & 0xff) + ", projectile: " + tv.projectileAllowed);
		player.message("originalProjectileAllowed: " + tv.originalProjectileAllowed);
	}

	private void regionInformation(Player player, String command, String[] args) {
		boolean debugPlayers ;
		if(args.length >= 1) {
			try {
				debugPlayers = DataConversions.parseBoolean(args[0]);
			} catch (NumberFormatException e) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (debug_players) (debug_npcs) (debug_items) (debug_objects)");
				return;
			}
		} else {
			debugPlayers = true;
		}

		boolean debugNpcs ;
		if(args.length >= 2) {
			try {
				debugNpcs = DataConversions.parseBoolean(args[1]);
			} catch (NumberFormatException e) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (debug_players) (debug_npcs) (debug_items) (debug_objects)");
				return;
			}
		} else {
			debugNpcs = true;
		}

		boolean debugItems ;
		if(args.length >= 3) {
			try {
				debugItems = DataConversions.parseBoolean(args[2]);
			} catch (NumberFormatException e) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (debug_players) (debug_npcs) (debug_items) (debug_objects)");
				return;
			}
		} else {
			debugItems = true;
		}

		boolean debugObjects ;
		if(args.length >= 1) {
			try {
				debugObjects = DataConversions.parseBoolean(args[3]);
			} catch (NumberFormatException e) {
				player.message(badSyntaxPrefix + command.toUpperCase() + " (debug_players) (debug_npcs) (debug_items) (debug_objects)");
				return;
			}
		} else {
			debugObjects = true;
		}

		ActionSender.sendBox(player, player.getRegion().toString(debugPlayers, debugNpcs, debugItems, debugObjects)
			.replaceAll("\n", "%"), true);
	}

	private void currentCoordinates(Player player, String[] args) {
		Player targetPlayer;
		if (args.length > 0) {
			targetPlayer = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
		} else {
			player.tellCoordinates();
			return;
		}

		if (targetPlayer != null)
			player.message(messagePrefix + targetPlayer.getStaffName() + " is at: " + targetPlayer.getLocation());
		else
			player.message(messagePrefix + "Invalid name or player is not online");
	}

	private void layeredCoordinateParity(Player player, String command, String[] args) {
		if (!player.getConfig().WANT_LAYERED_MAP_PARITY_OBSERVER) {
			player.message(messagePrefix + "Layered parity capture is disabled on this server."
				+ " Enable OPENRSC_LAYERED_MAP_PARITY_OBSERVER only for private/local testing.");
			return;
		}
		try {
			player.getLayeredVisibilityWindow();
			player.getLayeredInterestOwnerSnapshot();
		} catch (IllegalStateException failure) {
			player.message(messagePrefix + "Layered player mirror mismatch: " + failure.getMessage());
			return;
		}

		String action = args.length == 0 ? "status" : args[0].toLowerCase();
		LayeredCoordinateParityObserver.Status status;
		try {
			if ("start".equals(action)) {
				if (args.length != 1) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.start(
					player.getDatabaseID(), player.getUsernameHash(), player.getLocation(),
					player.getConfig().VIEW_DISTANCE, layeredTileSnapshotSource(player),
					layeredTileParitySource(player), layeredTileNeighborhoodSource(player),
					layeredAdjacentCollisionSource(player),
					layeredTraversalCollisionSource(player),
					layeredRegionResidencySource(player),
					layeredInterestOwnershipSource(player),
					layeredRegionRetirementSource(player),
					layeredRegionRetirementDecisionSource(player),
					layeredPackedRegionRetirementSafetySource(player),
					layeredPackedRegionAuthoredConstructionSource(player),
					layeredPackedRegionAuthoredProvenanceSource(player),
					layeredPackedRegionAuthoredReconstructionSource(player),
					layeredPackedRegionAuthoredReconstructionCohortSource(player),
					layeredPackedRegionAuthoredReconstructionCohortAttributionSource(
						player),
					layeredPackedRegionAuthoredReconstructionTopologySource(player),
					layeredPackedRegionAuthoredReconstructionDependencySemanticsSource(
						player),
					layeredPackedRegionActiveNpcResidencySource(player),
					layeredPackedRegionRetirementRefinementReassessmentSource(player),
					layeredPackedRegionPreservationBurdenSource(player),
					layeredPackedRegionDynamicObjectPreservationSource(player),
					layeredPackedRegionEventOwnershipSource(player));
			} else if ("snapshot".equals(action) || "capture".equals(action)) {
				if (args.length != 1) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.snapshot(
					player.getDatabaseID(), player.getUsernameHash(), player.getLocation());
			} else if ("mark".equals(action)) {
				if (args.length != 2) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.mark(
					player.getDatabaseID(), player.getUsernameHash(), player.getLocation(), args[1]);
			} else if ("recover-noop".equals(action)
				|| "recovernoop".equals(action)) {
				if (args.length != 1) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.recoverNoOp(
					player.getDatabaseID(), player.getUsernameHash(),
					player.getLocation());
			} else if ("stop".equals(action)) {
				if (args.length != 1) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.stop(
					player.getDatabaseID(), player.getUsernameHash(), player.getLocation());
			} else if ("status".equals(action)) {
				if (args.length != 1) {
					layeredParitySyntax(player, command);
					return;
				}
				status = LayeredCoordinateParityObserver.status(
					player.getDatabaseID(), player.getUsernameHash());
			} else {
				layeredParitySyntax(player, command);
				return;
			}
		} catch (IllegalArgumentException failure) {
			player.message(messagePrefix + "Layered parity request refused: " + failure.getMessage());
			return;
		}

		player.message(messagePrefix + "Layered parity "
			+ (status.isEnabled() ? "ACTIVE" : "inactive")
			+ "; records=" + status.getRecordCount() + "; log=" + status.getPath());
		if (status.getLastSnapshot() != null) {
			player.message(messagePrefix + status.getLastSnapshot().toCompactString());
		}
		if (status.getError() != null) {
			player.message(messagePrefix + "Capture error: " + status.getError());
		}
	}

	private LayeredCoordinateParityObserver.TileSnapshotSource
		layeredTileSnapshotSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.TileSnapshotSource() {
			@Override
			public LayeredCoordinateParityObserver.TileSnapshotMetadata capture(
				final WorldRegionKey logicalRegionKey) {
				LayeredRegionTileSnapshot snapshot =
					regionManager.getLayeredRegionTileSnapshot(logicalRegionKey);
				return LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
					snapshot.getLogicalRegionKey(),
					snapshot.getSourceFragmentCount(),
					snapshot.getMissingSourceRegionCount(),
					snapshot.getSupportedTileCount(),
					snapshot.getTargetTileCount(),
					snapshot.isComplete(),
					snapshot.getFingerprint());
			}
		};
	}

	private LayeredCoordinateParityObserver.TileParitySource
		layeredTileParitySource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.TileParitySource() {
			@Override
			public LayeredCoordinateParityObserver.TileParityMetadata capture(
				final Point current) {
				LayeredTileStateParityComparison comparison =
					regionManager.compareLayeredTileState(current);
				return LayeredCoordinateParityObserver.TileParityMetadata.of(
					comparison.getLogicalLocation(),
					comparison.getAddress().getLegacyPoint(),
					comparison.isPackedSourcePresent(),
					comparison.isMissingPackedSource(),
					comparison.isComparable(),
					comparison.isExact());
			}
		};
	}

	private LayeredCoordinateParityObserver.TileNeighborhoodSource
		layeredTileNeighborhoodSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.TileNeighborhoodSource() {
			@Override
			public LayeredCoordinateParityObserver.TileNeighborhoodMetadata capture(
				final Point current) {
				LayeredTileNeighborhoodParityComparison comparison =
					regionManager.compareLayeredTileNeighborhood(current);
				return LayeredCoordinateParityObserver.TileNeighborhoodMetadata.of(
					comparison.getCenter(),
					comparison.getLegacyRepresentableCount(),
					comparison.getPackedSourcePresentCount(),
					comparison.getMissingPackedSourceCount(),
					comparison.getComparableCount(),
					comparison.getExactCount(),
					comparison.isComplete(),
					comparison.isExact());
			}
		};
	}

	private LayeredCoordinateParityObserver.AdjacentCollisionSource
		layeredAdjacentCollisionSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.AdjacentCollisionSource() {
			@Override
			public LayeredCoordinateParityObserver.AdjacentCollisionMetadata capture(
				final Point current) {
				List<LayeredAdjacentStepCollisionComparison> comparisons =
					regionManager.compareLayeredAdjacentStepCollisions(current);
				List<LayeredCoordinateParityObserver.AdjacentDirectionMetadata> directions =
					new ArrayList<LayeredCoordinateParityObserver.AdjacentDirectionMetadata>(
						comparisons.size());
				for (LayeredAdjacentStepCollisionComparison comparison : comparisons) {
					directions.add(
						LayeredCoordinateParityObserver.AdjacentDirectionMetadata.of(
							comparison.getOffsetX(),
							comparison.getOffsetY(),
							comparison.getDestination(),
							comparison.getRequiredCellCount(),
							comparison.getExactRequiredStateCount(),
							comparison.getLogicalPassable(),
							adjacentReason(comparison.getLogicalBlockingReason()),
							comparison.getPackedPassable(),
							adjacentReason(comparison.getPackedBlockingReason())));
				}
				return LayeredCoordinateParityObserver.AdjacentCollisionMetadata.of(
					comparisons.get(0).getSource(), directions);
			}
		};
	}

	private LayeredCoordinateParityObserver.AdjacentBlockingReason adjacentReason(
		final LayeredAdjacentStepCollisionComparison.BlockingReason reason) {
		return reason == null ? null
			: LayeredCoordinateParityObserver.AdjacentBlockingReason.valueOf(
				reason.name());
	}

	private LayeredCoordinateParityObserver.TraversalCollisionSource
		layeredTraversalCollisionSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.TraversalCollisionSource() {
			@Override
			public LayeredCoordinateParityObserver.RecentTraversalMetadata capture(
				final List<WorldLocation> route,
				final int droppedStepCount,
				final int discontinuityCount) {
				LayeredTraversalCollisionComparison traversal =
					regionManager.compareLayeredTraversalCollision(route);
				List<LayeredCoordinateParityObserver.TraversalStepMetadata> steps =
					new ArrayList<LayeredCoordinateParityObserver.TraversalStepMetadata>(
						traversal.getStepCount());
				int index = 0;
				for (LayeredAdjacentStepCollisionComparison comparison
					: traversal.getSteps()) {
					steps.add(LayeredCoordinateParityObserver.TraversalStepMetadata.of(
						index,
						comparison.getSource(),
						comparison.getOffsetX(),
						comparison.getOffsetY(),
						comparison.getDestination(),
						comparison.getRequiredCellCount(),
						comparison.getExactRequiredStateCount(),
						comparison.getLogicalPassable(),
						adjacentReason(comparison.getLogicalBlockingReason()),
						comparison.getPackedPassable(),
						adjacentReason(comparison.getPackedBlockingReason())));
					index++;
				}
				return LayeredCoordinateParityObserver.RecentTraversalMetadata.of(
					steps, droppedStepCount, discontinuityCount);
			}
		};
	}

	private LayeredCoordinateParityObserver.RegionResidencySource
		layeredRegionResidencySource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.RegionResidencySource() {
			@Override
			public LayeredCoordinateParityObserver.RegionResidencyMetadata capture(
				final WorldRegionWindow previousWindow,
				final WorldRegionWindow currentWindow,
				final int maximumRegionsPerWindow) {
				LayeredRegionInterestResidencyComparison comparison =
					regionManager.compareLayeredRegionInterestResidency(
						previousWindow, currentWindow, maximumRegionsPerWindow);
				return LayeredCoordinateParityObserver.RegionResidencyMetadata.of(
					comparison.getMirrorVersion(),
					comparison.getInterestDelta().getRetained().size()
						+ comparison.getInterestDelta().getExited().size(),
					comparison.getInterestDelta().getEntered().size()
						+ comparison.getInterestDelta().getRetained().size(),
					comparison.getInterestDelta().getEntered().size(),
					comparison.getInterestDelta().getRetained().size(),
					comparison.getInterestDelta().getExited().size(),
					comparison.getInterestDelta().changesWorldSpace(),
					comparison.getInterestDelta().changesLevel(),
					comparison.getInterestDelta().isNoOp(),
					comparison.getResidentCurrentCount(),
					comparison.getPartialCurrentCount(),
					comparison.getMissingCurrentCount(),
					layeredRegionResidencyCandidates(
						comparison.getLoadCandidates()),
					layeredRegionResidencyCandidates(
						comparison.getReleaseCandidates()),
					layeredRegionResidencyCandidates(
						comparison.getUnsupportedCurrent()));
			}
		};
	}

	private LayeredCoordinateParityObserver.InterestOwnershipSource
		layeredInterestOwnershipSource(final Player player) {
		return new LayeredCoordinateParityObserver.InterestOwnershipSource() {
			@Override
			public LayeredCoordinateParityObserver.InterestOwnershipMetadata capture(
				final WorldRegionWindow currentWindow,
				final int maximumRegionsPerWindow) {
				LayeredRegionInterestOwnershipLedger.OwnerSnapshot snapshot =
					player.getLayeredInterestOwnerSnapshot();
				snapshot.requireWindow(currentWindow);
				if (snapshot.getReferences().size() > maximumRegionsPerWindow) {
					throw new IllegalArgumentException(
						"Interest owner exceeds the diagnostic Region budget");
				}
				return LayeredCoordinateParityObserver.InterestOwnershipMetadata
					.fromOwnerSnapshot(snapshot);
			}
		};
	}

	private LayeredCoordinateParityObserver.RegionRetirementSource
		layeredRegionRetirementSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.RegionRetirementSource() {
			@Override
			public LayeredCoordinateParityObserver.RegionRetirementMetadata capture(
				final List<WorldRegionKey> transitionKeys,
				final List<WorldRegionKey> trackedCandidateKeys,
				final long droppedCandidateCount,
				final int maximumRegions) {
				LinkedHashSet<WorldRegionKey> observed =
					new LinkedHashSet<WorldRegionKey>(transitionKeys);
				observed.addAll(trackedCandidateKeys);
				if (observed.size() > maximumRegions) {
					throw new IllegalArgumentException(
						"Region retirement evidence exceeds the diagnostic budget");
				}
				List<LayeredRegionRetirementEligibilityLedger.Snapshot> snapshots =
					regionManager.getLayeredRegionRetirementEligibilitySnapshots(
						new ArrayList<WorldRegionKey>(observed), maximumRegions);
				return LayeredCoordinateParityObserver.RegionRetirementMetadata
					.fromSnapshots(
						snapshots, transitionKeys, trackedCandidateKeys,
						droppedCandidateCount);
			}
		};
	}

	private LayeredCoordinateParityObserver.RegionRetirementDecisionSource
		layeredRegionRetirementDecisionSource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver.RegionRetirementDecisionSource() {
			@Override
			public LayeredCoordinateParityObserver.RegionRetirementDecisionMetadata
				capture(
					final List<LayeredRegionRetirementEligibilityLedger.Snapshot>
						candidates,
					final long droppedCandidateCount,
					final int maximumRegions) {
				List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
					regionManager.evaluateLayeredRegionRetirementCandidates(
						candidates, maximumRegions);
				return LayeredCoordinateParityObserver
					.RegionRetirementDecisionMetadata.fromDecisions(
						decisions, droppedCandidateCount);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionRetirementSafetySource
		layeredPackedRegionRetirementSafetySource(final Player player) {
		final RegionManager regionManager = player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver
			.PackedRegionRetirementSafetySource() {
			@Override
			public LayeredPackedRegionRetirementSafetyAssessment capture(
				final LayeredPackedRegionRetirementReadiness readiness,
				final int maximumPackedSources) {
				return regionManager.assessLayeredPackedRegionRetirementSafety(
					readiness, maximumPackedSources);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionAuthoredConstructionSource
		layeredPackedRegionAuthoredConstructionSource(final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredConstructionSource() {
			@Override
			public LayeredPackedRegionAuthoredConstructionObservation capture(
				final LayeredPackedRegionRetirementSafetyAssessment safety,
				final int maximumPackedSources) {
				return LayeredPackedRegionAuthoredConstructionObservation.observe(
					player.getWorld().getWorldLoader().getWorldPopulator()
						.getAuthoredConstructionInventory(),
					safety, maximumPackedSources);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionAuthoredProvenanceSource
		layeredPackedRegionAuthoredProvenanceSource(final Player player) {
		final RegionManager regionManager =
			player.getWorld().getRegionManager();
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredProvenanceSource() {
			@Override
			public LayeredPackedRegionAuthoredProvenanceObservation capture(
				final LayeredPackedRegionRetirementSafetyAssessment safety) {
				return regionManager.captureAuthoredProvenance(
					player.getWorld().getWorldLoader().getWorldPopulator()
						.getAuthoredPlacementManifest(),
					player.getWorld().getWorldLoader().getWorldPopulator()
						.getAuthoredPopulationOutcome(),
					safety, player.getWorld().getServer().getCurrentTick());
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionAuthoredReconstructionSource
		layeredPackedRegionAuthoredReconstructionSource(final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionSource() {
			@Override
			public LayeredPackedRegionAuthoredReconstructionObservation capture(
				final LayeredPackedRegionRetirementSafetyAssessment safety,
				final int maximumSafetySources,
				final int maximumRequirementSources) {
				return LayeredPackedRegionAuthoredReconstructionObservation.observe(
					player.getWorld().getWorldLoader().getWorldPopulator()
						.getAuthoredReconstructionRecipe(),
					safety, maximumSafetySources, maximumRequirementSources);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionAuthoredReconstructionCohortSource
			layeredPackedRegionAuthoredReconstructionCohortSource(
				final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionCohortSource() {
			@Override
			public LayeredPackedRegionAuthoredReconstructionCohortAnalysis capture(
				final LayeredPackedRegionRetirementSafetyAssessment safety,
				final int maximumCohortSources,
				final int maximumRequirementSources) {
				return LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.analyze(
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						safety, maximumCohortSources,
						maximumRequirementSources);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionAuthoredReconstructionCohortAttributionSource
			layeredPackedRegionAuthoredReconstructionCohortAttributionSource(
				final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionCohortAttributionSource() {
			@Override
			public LayeredPackedRegionAuthoredReconstructionCohortAttribution
				capture(
					final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
						cohort,
					final int maximumEdges,
					final int maximumBridgePlacements) {
				return LayeredPackedRegionAuthoredReconstructionCohortAttribution
					.analyze(
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						cohort, maximumEdges, maximumBridgePlacements);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionAuthoredReconstructionTopologySource
			layeredPackedRegionAuthoredReconstructionTopologySource(
				final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionTopologySource() {
			@Override
			public LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
				capture(
					final LayeredPackedRegionAuthoredReconstructionCohortAnalysis
						cohort,
					final int maximumSources,
					final int maximumRelationships) {
				return LayeredPackedRegionAuthoredReconstructionTopologyAnalysis
					.analyze(
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						cohort, maximumSources, maximumRelationships);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionAuthoredReconstructionDependencySemanticsSource
			layeredPackedRegionAuthoredReconstructionDependencySemanticsSource(
				final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionDependencySemanticsSource() {
			@Override
			public
				LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
					capture(
						final LayeredPackedRegionRetirementSafetyAssessment safety,
						final int maximumSelectedSources,
						final int maximumSupportSources,
						final int maximumIncomingOwners,
						final int maximumIncomingPlacements) {
				return
					LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
						.analyze(
							player.getWorld().getWorldLoader().getWorldPopulator()
								.getAuthoredReconstructionRecipe(),
							safety, maximumSelectedSources,
							maximumSupportSources, maximumIncomingOwners,
							maximumIncomingPlacements);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionActiveNpcResidencySource
		layeredPackedRegionActiveNpcResidencySource(final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionActiveNpcResidencySource() {
			@Override
			public LayeredPackedRegionActiveNpcResidencyObservation capture(
				final LayeredPackedRegionRetirementSafetyAssessment safety,
				final int maximumInstances,
				final int maximumRelevantDetails) {
				return player.getWorld().getRegionManager()
					.captureActiveNpcResidency(
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						safety, player.getWorld().getServer().getCurrentTick(),
						maximumInstances, maximumRelevantDetails);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionRetirementRefinementReassessmentSource
			layeredPackedRegionRetirementRefinementReassessmentSource(
				final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionRetirementRefinementReassessmentSource() {
			@Override
			public LayeredPackedRegionRetirementRefinementReassessment
				captureIfFresh(
					final LayeredPackedRegionRetirementRefinementProposal
						previousProposal,
					final int maximumCandidateSources,
					final int maximumSupportSources,
					final int maximumNpcInstances,
					final int maximumRelevantNpcDetails,
					final int maximumActiveNpcRequirements) {
				return player.getWorld().getRegionManager()
					.captureLayeredPackedRegionRetirementRefinementReassessmentIfFresh(
						previousProposal,
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						maximumCandidateSources, maximumSupportSources,
						maximumNpcInstances, maximumRelevantNpcDetails,
						maximumActiveNpcRequirements);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionPreservationBurdenSource
		layeredPackedRegionPreservationBurdenSource(final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionPreservationBurdenSource() {
			@Override
			public LayeredPackedRegionPreservationBurdenAssessment capture(
				final LayeredPackedRegionRetirementRefinementProposal proposal,
				final int maximumCandidateSources) {
				return player.getWorld().getRegionManager()
					.assessLayeredPackedRegionPreservationBurden(
						proposal, maximumCandidateSources);
			}
		};
	}

	private LayeredCoordinateParityObserver
		.PackedRegionDynamicObjectPreservationSource
			layeredPackedRegionDynamicObjectPreservationSource(final Player player) {
		return new LayeredCoordinateParityObserver
			.PackedRegionDynamicObjectPreservationSource() {
			@Override
			public LayeredPackedRegionDynamicObjectPreservationRecord capture(
				final LayeredPackedRegionRetirementRefinementProposal proposal,
				final int maximumCandidateSources,
				final int maximumDynamicObjects) {
				return player.getWorld().getRegionManager()
					.captureLayeredPackedRegionDynamicObjectPreservationRecord(
						proposal, maximumCandidateSources, maximumDynamicObjects);
			}
		};
	}

	private LayeredCoordinateParityObserver.PackedRegionEventOwnershipSource
		layeredPackedRegionEventOwnershipSource(final Player player) {
		return new LayeredCoordinateParityObserver.PackedRegionEventOwnershipSource() {
			@Override
			public LayeredPackedRegionEventOwnershipInventory capture(
				final LayeredPackedRegionRetirementRefinementProposal proposal,
				final int maximumEvents,
				final int maximumSpatialReferences) {
				return player.getWorld().getServer().getGameEventHandler()
					.captureLayeredPackedRegionEventOwnershipInventory(
						proposal, player.getWorld().getServer().getCurrentTick(),
						maximumEvents, maximumSpatialReferences);
			}

			@Override
			public LayeredPackedRegionEventTargetObservation captureTargets(
				final LayeredPackedRegionEventOwnershipInventory inventory,
				final int maximumTargetRecords) {
				return player.getWorld().getRegionManager()
					.captureLayeredPackedRegionEventTargetObservation(
						inventory, maximumTargetRecords);
			}

			@Override
			public LayeredPackedRegionEventAtomicTargetRevalidation
				captureAtomicTargetRevalidation(
					final LayeredPackedRegionEventOwnershipInventory inventory,
					final int maximumTargetRecords) {
				return player.getWorld().getServer().getGameEventHandler()
					.captureLayeredPackedRegionEventAtomicTargetRevalidation(
						inventory, maximumTargetRecords);
			}

			@Override
			public LayeredPackedRegionNpcOwnerEventContinuityAssessment
				captureNpcOwnerContinuity(
					final LayeredPackedRegionRetirementRefinementProposal proposal,
					final LayeredPackedRegionEventOwnershipInventory inventory,
					final int maximumCandidateSources,
					final int maximumNpcInstances,
					final int maximumRelevantNpcDetails,
					final int maximumEventDetails) {
				return player.getWorld().getRegionManager()
					.captureLayeredPackedRegionNpcOwnerEventContinuity(
						proposal, inventory,
						player.getWorld().getWorldLoader().getWorldPopulator()
							.getAuthoredReconstructionRecipe(),
						maximumCandidateSources, maximumNpcInstances,
						maximumRelevantNpcDetails, maximumEventDetails);
			}

			@Override
			public PackedRegionEventRecoveryNoOpMetadata captureRecoveryNoOp(
				final LayeredPackedRegionEventOwnershipInventory inventory,
				final int maximumCandidates) {
				return player.getWorld().getServer().getGameEventHandler()
					.captureLayeredPackedRegionEventRecoveryNoOpDiagnostic(
						inventory, maximumCandidates);
			}
		};
	}

	private List<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>
		layeredRegionResidencyCandidates(
			final List<LayeredRegionInterestResidencyComparison.Entry> entries) {
		List<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>
			candidates = new ArrayList<
				LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>(
					entries.size());
		for (LayeredRegionInterestResidencyComparison.Entry entry : entries) {
			LayeredRegionResidencyMirror.Snapshot snapshot =
				entry.getResidencySnapshot();
			candidates.add(
				LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata.of(
					entry.getLogicalRegionKey(),
					LayeredCoordinateParityObserver.RegionInterestState.valueOf(
						entry.getInterestState().name()),
					LayeredCoordinateParityObserver.RegionResidencyState.valueOf(
						entry.getResidencyState().name()),
					snapshot.getSourceCount(),
					snapshot.getResidentSourceCount(),
					snapshot.getLegacySupportedTileCount(),
					snapshot.getResidentTileCount(),
					snapshot.isLegacyCoverageComplete()));
		}
		return candidates;
	}

	private void layeredParitySyntax(Player player, String command) {
		player.message(badSyntaxPrefix + command.toUpperCase()
			+ " [start|status|snapshot|mark LABEL|recover-noop|stop]");
	}

	private void testNpcDrops(Player player, String command, String[] args) {
		Thread t = new Thread(new DropTest(player, args));
		t.start();
	}


	private void fishingRate(Player player, String command, String[] args) {
		if (args.length < 2) {
			mes("::fishingrate [fishing spot name (see Development.java)] [level] (trials)");
			return;
		}
		String spotName = args[0];
		int level = Integer.parseInt(args[1]);
		int trials = 10000;
		if (args.length == 3) {
			trials = Integer.parseInt(args[2]);
		}

		if (spotName.equals("bigNet")) {
			bigNetFishingRate(level, trials, player);
			return;
		}

		HashMap<String, ObjectFishingDef> fishingDefs = new HashMap<>();
		fishingDefs.put("pike", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(192, 1));
		fishingDefs.put("troutSalmon", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(192, 0));
		fishingDefs.put("sardineHerring", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(193, 1));
		fishingDefs.put("shrimpAnchovies", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(193, 0));
		fishingDefs.put("lobster", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(194, 1));
		fishingDefs.put("tunaSwordfish", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(194, 0));
		fishingDefs.put("shark", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(261, 1));
		fishingDefs.put("bigNet", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(261, 0));
		fishingDefs.put("tunaSwordfish2", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(376, 1));
		fishingDefs.put("lobster2", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(376, 0));
		fishingDefs.put("tutShrimp", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(493, 0));
		fishingDefs.put("lobster3", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(557, 1));
		fishingDefs.put("tunaSwordfish3", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(557, 0));
		fishingDefs.put("lavaeel", player.getWorld().getServer().getEntityHandler().getObjectFishingDef(271, 0));

		HashMap<Integer,Integer> results = new HashMap<Integer, Integer>();
		for (int i = 0; i < trials; i++) {
			ObjectFishDef fish = fishingDefs.get(args[0]).fishingAttemptResult(Integer.parseInt(args[1]));
			int result = -1;
			if (fish != null) {
				result = fish.getId();
			}
			if (results.get(result) != null) {
				results.put(result, results.get(result) + 1);
			} else {
				results.put(result, 1);
			}
		}
		mes("@whi@At level @gre@" + level + "@whi@ in @gre@" + trials + "@whi@ attempts:");
		for (int key : results.keySet()) {
			mes("@whi@We got @gre@" + results.get(key) + "@whi@ of id @mag@" + key);
		}
	}

	private void bigNetFishingRate(int level, int trials, Player player) {
		Fishing fishy = new Fishing();
		fishy.testBigNetFishing(level, trials, player);
	}

	// test combat style desync
	private void setCombatStyle(Player player, String[] args) {
		if (args.length == 0) {
			player.setCombatStyle(Skills.CONTROLLED_MODE);
		}
		if (args.length == 1) {
			try {
				int proposedStyle = Integer.parseInt(args[0]);
				player.setCombatStyle(proposedStyle);
			} catch (Exception e) {}
		}
	}

	private void protoDartTipsTest(Player player, String[] args) {
		if (args.length < 1) {
			mes("::protodarts [level] (trials)");
			return;
		}

		int level = Integer.parseInt(args[0]);
		int trials = 10000;
		if (args.length == 2) {
			trials = Integer.parseInt(args[1]);
		}

		int craftSuccesses = 0;
		int smithSuccesses = 0;
		for (int i = 0; i < trials; i++) {
			if (Tourist_Trap_Mechanism.protoDartCraftSuccessful(level)) ++craftSuccesses;
			if (Tourist_Trap_Mechanism.protoDartSmithSuccessful(level)) ++smithSuccesses;
		}

		mes("@whi@At level @mag@" + level + "@whi@:");
		mes("@gre@" + craftSuccesses + "@whi@ crafting successes, @lre@" + (trials - craftSuccesses) + "@whi@ failures.");
		mes("@gre@" + smithSuccesses + "@whi@ smithing successes, @lre@" + (trials - smithSuccesses) + "@whi@ failures.");

	}
	private void logRate(Player player, String[] args) {
		// parse input
		if (args.length < 3) {
			mes("::lograte [log name] [level] [axe name] (trials)");
			return;
		}
		String logName = args[0];
		int level = Integer.parseInt(args[1]);
		String axe = args[2];
		int trials = 10000;
		if (args.length == 4) {
			trials = Integer.parseInt(args[3]);
		}

		// translate log name to ObjectWoodcuttingDef
		int treeId = -1;
		if (logName.equalsIgnoreCase("normal")) {
			treeId = 0; // 1 & 70 are identical
		} else if (logName.equalsIgnoreCase("oak")) {
			treeId = 306;
		} else if (logName.equalsIgnoreCase("willow")) {
			treeId = 307;
		} else if (logName.equalsIgnoreCase("maple")) {
			treeId = 308;
		} else if (logName.equalsIgnoreCase("yew")) {
			treeId = 309;
		} else if (logName.equalsIgnoreCase("magic")) {
			treeId = 310;
		} else {
			mes("invalid tree type specified");
			return;
		}
		final ObjectWoodcuttingDef def = player.getWorld().getServer().getEntityHandler().getObjectWoodcuttingDef(treeId);

		// translate axe name to axeid
		int axeId = -1;
		if (axe.equalsIgnoreCase("bronze")) {
			axeId = ItemId.BRONZE_AXE.id();
		} else if (axe.equalsIgnoreCase("iron")) {
			axeId = ItemId.IRON_AXE.id();
		} else if (axe.equalsIgnoreCase("steel")) {
			axeId = ItemId.STEEL_AXE.id();
		} else if (axe.equalsIgnoreCase("black")) {
			axeId = ItemId.BLACK_AXE.id();
		} else if (axe.equalsIgnoreCase("mithril")) {
			axeId = ItemId.MITHRIL_AXE.id();
		} else if (axe.equalsIgnoreCase("adamantite") || axe.equalsIgnoreCase("addy") || axe.equalsIgnoreCase("adamant")) {
			axeId = ItemId.ADAMANTITE_AXE.id();
		} else if (axe.equalsIgnoreCase("rune")) {
			axeId = ItemId.RUNE_AXE.id();
		} else if (axe.equalsIgnoreCase("dragon")) {
			axeId = ItemId.DRAGON_WOODCUTTING_AXE.id();
		}

		int logs = 0;
		for (int i = 0; i < trials; i++) {
			Woodcutting woody = new Woodcutting();
			if (woody.getLog(def, level, axeId)) logs++;
		}

		mes("@whi@At level @mag@" + level + "@whi@ woodcut:");
		mes("@gre@" + logs + " @whi@" + logName + " logs were received in @lre@" + trials + "@whi@ attempts with the @cya@" + axe + " axe");
	}

	private void points(Player player, String[] args) {
		if (args.length == 0) {
			player.message("You have " + player.getOpenPkPoints() + " points.");
		} else {
			long points = Long.parseLong(args[0]);
			player.message("Setting points to " + points);
			player.setOpenPkPoints(points);
		}
	}

	private void playSound(Player player, String[] args) {
		if (args.length == 1) {
			ActionSender.sendSound(player, args[0]);
		}
	}
}

class DropTest implements Runnable {
	private long packCatalogAmount(int catalogId, int amount) {
		return ((long)catalogId << 32 | amount);
	}

	private int[] unpackCatalogAmount(long packedCatalogAmount) {
		return new int[] { (int)((packedCatalogAmount & 0xFFFF0000) >> 32), (int)(packedCatalogAmount & 0xFFFF) };
	}
	Player player;
	String[] args;
	private static final Logger LOGGER = LogManager.getLogger(DropTest.class);

	DropTest(Player player, String[] args) {
		this.player = player;
		this.args = args;
	}


	@Override
	public void run() {
		if (args.length < 1) {
			player.playerServerMessage(MessageType.QUEST, "::droptest [npc_id] (count) (ring of wealth)");
			return;
		}
		int npcId = Integer.parseInt(args[0]);
		long count = 1;
		boolean ringOfWealth = false;
		if (args.length > 1) {
			count = Long.parseLong(args[1]);
		}
		if (args.length > 2) {
			ringOfWealth = Integer.parseInt(args[2]) == 1;
		};

		NpcDrops npcDrops = player.getWorld().getNpcDrops();
		DropTable dropTable = npcDrops.getDropTable(npcId);
		if (dropTable == null) {
			player.playerServerMessage(MessageType.QUEST, "No NPC for id: " + npcId);
			return;
		}

		if (count >= 20000000)
			player.playerServerMessage(MessageType.QUEST, "Calculating...");

		HashMap<Long, Integer> droppedCount = new HashMap<>();
		for (long i = 0; i < count; i++) {
			ArrayList<Item> items = dropTable.rollItem(ringOfWealth, player);
			if (items.size() == 0) {
				// increment item ID -1, amount 0
				droppedCount.put(-4294967296L,
					droppedCount.getOrDefault(-4294967296L, 0) + 1);
			} else {
				for (Item item : items) {
					droppedCount.put(packCatalogAmount(item.getCatalogId(), item.getAmount()),
						droppedCount.getOrDefault(packCatalogAmount(item.getCatalogId(), item.getAmount()), 0) + 1);
				}
			}
		}

		if (player.getConfig().WANT_CUSTOM_SPRITES && npcId == 477) {
			for (long i = 0; i < count; i++) {
				for (Item item : Npc.calculateCustomKingBlackDragonDropTest(player, ringOfWealth)) {
					droppedCount.put(packCatalogAmount(item.getCatalogId(), item.getAmount()),
						droppedCount.getOrDefault(packCatalogAmount(item.getCatalogId(), item.getAmount()), 0) + 1);
				}
			}
		}

		String rowUsed = "Dropped counts out of " + count + " trials (RoW: " + ringOfWealth + "):";
		LOGGER.info(rowUsed);
		player.playerServerMessage(MessageType.QUEST, rowUsed);
		final long finalCount = count;
		droppedCount.forEach((key, value) -> {
			String itemName = "NOTHING";
			int[] unpacked = unpackCatalogAmount(key);
			int catalogId = unpacked[0];
			int amount = unpacked[1];
			Item i = new Item(catalogId, amount);
			if (i.getCatalogId() > -1) {
				itemName = i.getDef(player.getWorld()).getName();
			}

			StringBuilder output = new StringBuilder();
			output.append("@cya@").append(itemName).append(" (").append(amount).append("): @yel@ ");
			double rate128 = (value / (double)finalCount) * 128;
			if (rate128 > 1) {
				output.append(String.format("%,.2f", rate128)).append(" in 128");
			} else {
				output.append("1 in ").append(String.format("%,.1f", (double)finalCount / value));
			}
			output.append(" @whi@ (").append(value).append(String.format(" drop%s)", value == 1 ? "" : "s"));

			LOGGER.info(output.toString().replaceAll("@...@", ""));
			player.playerServerMessage(MessageType.QUEST, output.toString());
		});

	}
}
