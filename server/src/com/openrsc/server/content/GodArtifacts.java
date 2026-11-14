package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.List;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.say;

public final class GodArtifacts {
	public static final int REQUIRED_PRAYER_LEVEL = 80;
	public static final int REQUIRED_DEVOTION = 800;
	public static final int DEVOTION_COST = 400;

	private static final String CLAIMED_CACHE_PREFIX = "god_artifact_claimed_";

	private static final int[] SARADOMIN_ARTIFACTS = {
		ItemId.SARADOMIN_CAPE.id(),
		ItemId.STAFF_OF_SARADOMIN.id(),
		ItemId.SARADOMIN_MACE.id(),
		ItemId.SARADOMIN_CROSSBOW.id()
	};
	private static final int[] ZAMORAK_ARTIFACTS = {
		ItemId.ZAMORAK_CAPE.id(),
		ItemId.STAFF_OF_ZAMORAK.id(),
		ItemId.ZAMORAK_MACE.id(),
		ItemId.ZAMORAK_CROSSBOW.id()
	};
	private static final int[] GUTHIX_ARTIFACTS = {
		ItemId.GUTHIX_CAPE.id(),
		ItemId.STAFF_OF_GUTHIX.id(),
		ItemId.GUTHIX_MACE.id(),
		ItemId.GUTHIX_CROSSBOW.id()
	};

	private GodArtifacts() {
	}

	public static boolean offerIfEligible(final Player player, final PrayerCatalog.GodLine godLine) {
		if (!isEligible(player, godLine)) {
			return false;
		}

		showOfferDialogue(godLine);
		final int option = multi(player, "Yes, I desire it.", "No, I am not worthy.");
		if (option != 0) {
			say(player, "No, I am not worthy.");
			showDeclineDialogue(godLine);
			return true;
		}
		say(player, "Yes, I desire it.");

		final int artifactId = selectRandomUnclaimedArtifact(player, godLine);
		if (artifactId < 0) {
			return false;
		}

		final Item artifact = new Item(artifactId);
		if (!player.getCarriedItems().getInventory().canHold(artifact)) {
			mes("You need enough inventory space to receive an artifact.");
			delay(3);
			return true;
		}

		if (!player.getCarriedItems().getInventory().add(artifact)) {
			mes("The artifact cannot be placed in your inventory.");
			delay(3);
			return true;
		}

		markClaimed(player, godLine, artifactId);
		Devotion.removeDevotionLevels(player, godLine, DEVOTION_COST);
		showAcceptDialogue(godLine);
		mes("You receive " + artifact.getDef(player.getWorld()).getName() + ".");
		delay(3);
		return true;
	}

	public static boolean isEligible(final Player player, final PrayerCatalog.GodLine godLine) {
		if (player == null || godLine == null || !player.getConfig().WANT_MYWORLD) {
			return false;
		}
		if (player.getPrayerBook() != godLine) {
			return false;
		}
		if (player.getSkills().getMaxStat(Skill.PRAYER.id()) < REQUIRED_PRAYER_LEVEL) {
			return false;
		}
		if (Devotion.getDevotionLevel(player, godLine) < REQUIRED_DEVOTION) {
			return false;
		}
		return !getUnclaimedArtifacts(player, godLine).isEmpty();
	}

	public static boolean isClaimed(final Player player, final PrayerCatalog.GodLine godLine, final int itemId) {
		return player != null && godLine != null && player.getCache().hasKey(getClaimedCacheKey(godLine, itemId));
	}

	private static int selectRandomUnclaimedArtifact(final Player player, final PrayerCatalog.GodLine godLine) {
		final List<Integer> artifacts = getUnclaimedArtifacts(player, godLine);
		if (artifacts.isEmpty()) {
			return -1;
		}
		return artifacts.get(DataConversions.random(0, artifacts.size() - 1));
	}

	private static List<Integer> getUnclaimedArtifacts(final Player player, final PrayerCatalog.GodLine godLine) {
		final int[] pool = getArtifactPool(godLine);
		final List<Integer> artifacts = new ArrayList<Integer>();
		for (int itemId : pool) {
			if (!isClaimed(player, godLine, itemId)) {
				artifacts.add(itemId);
			}
		}
		return artifacts;
	}

	private static void markClaimed(final Player player, final PrayerCatalog.GodLine godLine, final int itemId) {
		player.getCache().store(getClaimedCacheKey(godLine, itemId), true);
	}

	private static String getClaimedCacheKey(final PrayerCatalog.GodLine godLine, final int itemId) {
		return CLAIMED_CACHE_PREFIX + godLine.name().toLowerCase() + "_" + itemId;
	}

	private static int[] getArtifactPool(final PrayerCatalog.GodLine godLine) {
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			return SARADOMIN_ARTIFACTS;
		}
		if (godLine == PrayerCatalog.GodLine.ZAMORAK) {
			return ZAMORAK_ARTIFACTS;
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			return GUTHIX_ARTIFACTS;
		}
		return new int[0];
	}

	private static void showOfferDialogue(final PrayerCatalog.GodLine godLine) {
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			mes("You have walked long in the light of wisdom.");
			delay(3);
			mes("Your devotion has not gone unseen.");
			delay(3);
			mes("If you desire it, I will entrust you with one of my sacred artifacts.");
			delay(3);
			return;
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			mes("You have kept faith with the balance.");
			delay(3);
			mes("Devotion given freely returns in its own season.");
			delay(3);
			mes("If you desire it, I will grant you one artifact of the natural order.");
			delay(3);
			return;
		}
		mes("You have endured, and you have not broken.");
		delay(3);
		mes("Your devotion has strength enough to be rewarded.");
		delay(3);
		mes("If you dare claim it, I will grant you one of my artifacts.");
		delay(3);
	}

	private static void showAcceptDialogue(final PrayerCatalog.GodLine godLine) {
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			mes("Then carry it with purpose, not pride.");
			delay(3);
			mes("Let it serve as a reminder that power is a duty.");
			delay(3);
			return;
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			mes("Then let it be used with balance.");
			delay(3);
			mes("Take only what is needed. Give back what you can.");
			delay(3);
			return;
		}
		mes("Then take it, and prove you deserved it.");
		delay(3);
		mes("Weak hands will make even power useless.");
		delay(3);
	}

	private static void showDeclineDialogue(final PrayerCatalog.GodLine godLine) {
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			mes("Wisdom often begins with restraint.");
			delay(3);
			mes("Return when your heart is ready.");
			delay(3);
			return;
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			mes("Then the balance remains undisturbed.");
			delay(3);
			mes("Return when the moment is right.");
			delay(3);
			return;
		}
		mes("Then crawl away with your doubt.");
		delay(3);
		mes("Return when your will is sharper.");
		delay(3);
	}
}
