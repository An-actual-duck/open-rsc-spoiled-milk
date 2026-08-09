package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcContributionLedger;
import com.openrsc.server.model.entity.npc.NpcContributionRole;
import com.openrsc.server.model.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/** Executable parity specification for the distinct NPC contribution roles. */
final class CurrentCombatContributionLedgerCharacterization {
	private CurrentCombatContributionLedgerCharacterization() {
	}

	static void typedLedgerKeepsFactualRolesSeparate(
			final CurrentCombatHarness ignored) {
		final NpcContributionLedger ledger = new NpcContributionLedger();
		final UUID player = UUID.fromString("00000000-0000-0000-0000-000000000111");
		ledger.record(NpcContributionRole.MELEE, player, 11L, 3);
		ledger.record(NpcContributionRole.MELEE, player, 12L, 2);
		ledger.record(NpcContributionRole.RANGED, player, 12L, 0);
		ledger.record(NpcContributionRole.SUMMON, player, 12L, 4);

		assertEquals(5, ledger.get(NpcContributionRole.MELEE, player).getDamage(),
			"same role retains legacy accumulated damage");
		assertEquals(12L,
			ledger.get(NpcContributionRole.MELEE, player).getUsernameHash(),
			"latest factual owner hash mirrors the legacy map replacement");
		assertEquals(0, ledger.get(NpcContributionRole.RANGED, player).getDamage(),
			"zero-damage ranged touch remains factual");
		assertTrue(ledger.contributorIds(NpcContributionRole.RANGED).contains(player),
			"zero-damage touch remains in the style contributor identity list");
		assertTrue(NpcContributionRole.MAGIC.isCombatStyleExperienceEligible(),
			"magic remains style-XP eligible");
		assertFalse(NpcContributionRole.SUMMON.isCombatStyleExperienceEligible(),
			"summon contribution remains outside style XP");
		ledger.clear();
		assertTrue(ledger.contributorIds(NpcContributionRole.MELEE).isEmpty(),
			"lifetime reset clears all factual entries");
	}

	@SuppressWarnings("unchecked")
	static void settlementRolesPreserveTieOfflineAndLifecyclePolicy(
			final CurrentCombatHarness harness) throws Exception {
		final Player melee = harness.player("ledger melee", 410, 410);
		final Player ranged = harness.player("ledger ranged", 411, 410);
		final Player summoner = harness.player("ledger summon", 412, 410);
		final Player directSource = harness.player("ledger direct", 409, 410);
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), 413, 410);

		target.addCombatDamage(melee, 2);
		target.addRangeDamage(ranged, 2);
		target.addRangeDamage(ranged, 0);
		target.addSummonDamage(summoner, 3);
		assertTrue(target.hasDamageBy(summoner),
			"summon owner remains a factual contributor");

		final ArrayList<UUID> styleContributorIds = (ArrayList<UUID>)
			CurrentCombatHarness.invokePrivate(target, "getAllDamageDealerIds",
				new Class<?>[0]);
		assertTrue(styleContributorIds.contains(melee.getUUID()),
			"melee owner participates in style-XP candidate list");
		assertTrue(styleContributorIds.contains(ranged.getUUID()),
			"zero-touched ranged owner remains a legacy style candidate");
		assertFalse(styleContributorIds.contains(summoner.getUUID()),
			"summon owner remains excluded from style-XP candidates");

		final Pair<UUID, Long> top = (Pair<UUID, Long>)
			CurrentCombatHarness.invokePrivate(target, "getTopDamageDealer",
				new Class<?>[] {Mob.class}, melee);
		assertEquals(summoner.getUUID(), top.getLeft(),
			"highest factual summon contribution retains top-credit eligibility");

		final Npc tieTarget = harness.npc(NpcId.GREATER_DEMON.id(), 414, 410);
		tieTarget.addCombatDamage(melee, 4);
		tieTarget.addRangeDamage(ranged, 4);
		final Pair<UUID, Long> tiedTop = (Pair<UUID, Long>)
			CurrentCombatHarness.invokePrivate(tieTarget, "getTopDamageDealer",
				new Class<?>[] {Mob.class}, directSource);
		assertEquals(melee.getUUID(), tiedTop.getLeft(),
			"equal cross-role damage keeps the legacy melee-before-ranged candidate");
		assertFalse(directSource.getUUID().equals(tiedTop.getLeft()),
			"a direct causal source with no factual damage is not made top credit");

		final Map<Player, Double> onlineRecipients = (Map<Player, Double>)
			CurrentCombatHarness.invokePrivate(target, "getPersonalLootRecipients",
				new Class<?>[0]);
		assertEquals(3, onlineRecipients.size(),
			"all positive factual roles receive personal-loot consideration");
		assertEquals(Double.valueOf(0.05D), onlineRecipients.get(melee),
			"minimum contribution scale remains unchanged");

		harness.logout(summoner);
		final Map<Player, Double> offlineRecipients = (Map<Player, Double>)
			CurrentCombatHarness.invokePrivate(target, "getPersonalLootRecipients",
				new Class<?>[0]);
		assertFalse(offlineRecipients.containsKey(summoner),
			"offline contributors remain in factual history but cannot receive drops");

		final Player reloggedSummoner = harness.player("ledger summon", 412, 410);
		assertEquals(summoner.getUUID(), reloggedSummoner.getUUID(),
			"player UUID remains the stable relog identity");
		final Map<Player, Double> reloggedRecipients = (Map<Player, Double>)
			CurrentCombatHarness.invokePrivate(target, "getPersonalLootRecipients",
				new Class<?>[0]);
		assertTrue(reloggedRecipients.containsKey(reloggedSummoner),
			"relogged stable identity regains legacy loot eligibility");

		final int magicBefore = summoner.getSkills().getExperience(Skill.MAGIC.id());
		CurrentCombatHarness.invokePrivate(target, "awardDamageShareXp",
			new Class<?>[] {Player.class, int.class, int.class, int.class, int.class},
			summoner, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0),
			Integer.valueOf(100));
		assertEquals(magicBefore, summoner.getSkills().getExperience(Skill.MAGIC.id()),
			"summon-only factual contribution does not synthesize style XP");

		target.remove();
		final Npc replacement = harness.npc(NpcId.GREATER_DEMON.id(), 415, 410);
		assertFalse(replacement.hasDamageBy(melee),
			"replacement NPC lifetime starts without predecessor contribution");
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition, final String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
