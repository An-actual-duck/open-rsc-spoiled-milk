package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;

/**
 * Shared all-or-nothing path for ordinary altar blessings.
 */
public final class PrayerBlessingTransaction {
	private PrayerBlessingTransaction() {
	}

	public static boolean bless(
		final Player player,
		final PrayerCatalog.GodLine godLine,
		final Item source,
		final int productId,
		final int devotionRequirement,
		final int devotionOfferingCost,
		final int basePrayerXp,
		final String successMessage
	) {
		if (player == null || godLine == null || source == null || source.getNoted()
			|| source.getAmount() != 1 || productId < 0 || devotionRequirement < 0
			|| devotionOfferingCost < 0 || basePrayerXp < 0) {
			return false;
		}

		synchronized (player) {
			if (player.getPrayerBook() != godLine) {
				player.message("You must worship " + formatGodLine(godLine) + " to bless items at this altar.");
				return false;
			}

			final int currentDevotion = Devotion.getDevotionLevel(player, godLine);
			if (currentDevotion < devotionRequirement) {
				player.message("You need " + devotionRequirement + " devotion to " + formatGodLine(godLine) + " to bless that item.");
				player.message("Your current devotion to " + formatGodLine(godLine) + " is " + currentDevotion + ".");
				return false;
			}
			if (Devotion.getOfferings(player, godLine) < devotionOfferingCost) {
				return false;
			}

			// Preserve the approved XP formula at the Devotion level that
			// qualified the blessing, before its small fractional cost is paid.
			final int prayerXp = Devotion.getBlessingPrayerXp(player, godLine, basePrayerXp);
			final boolean completed = PrayerBlessingLimit.completeSuccessfulBlessing(
				player,
				godLine,
				() -> {
					if (!player.getCarriedItems().getInventory().replaceExact(source, new Item(productId), true)) {
						return false;
					}
					if (devotionOfferingCost > 0) {
						final int actualChange = Devotion.adjustDevotionOfferings(player, godLine, -devotionOfferingCost);
						if (actualChange != -devotionOfferingCost) {
							throw new IllegalStateException("Validated blessing Devotion cost was not applied in full");
						}
					}
					return true;
				}
			);
			if (!completed) {
				return false;
			}

			if (prayerXp > 0) {
				player.incExp(Skill.PRAYER.id(), prayerXp, true);
			}
			if (successMessage != null && !successMessage.isEmpty()) {
				player.message(successMessage);
			}
			if (devotionOfferingCost > 0) {
				player.message(
					"The blessing consumes "
						+ formatDevotionOfferingAmount(devotionOfferingCost)
						+ " devotion to "
						+ formatGodLine(godLine)
						+ "."
				);
			}
			return true;
		}
	}

	private static String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String formatDevotionOfferingAmount(final int offerings) {
		if (offerings % Devotion.OFFERINGS_PER_DEVOTION_LEVEL == 0) {
			return String.valueOf(offerings / Devotion.OFFERINGS_PER_DEVOTION_LEVEL);
		}
		return String.valueOf(offerings / (double) Devotion.OFFERINGS_PER_DEVOTION_LEVEL);
	}
}
