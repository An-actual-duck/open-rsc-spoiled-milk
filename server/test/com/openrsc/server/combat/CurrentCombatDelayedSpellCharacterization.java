package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable A05.4E delayed-spell parity and transaction policies. */
final class CurrentCombatDelayedSpellCharacterization {
	private static final String SUMMON_OWNER_KEY = "myworld_summon_owner";

	private CurrentCombatDelayedSpellCharacterization() {
	}

	static void godSpellAreaPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player caster = spellCaster(
			harness, "delayed god", 520, 700, Spells.SARADOMIN_STRIKE);
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 10, 40, false);
		harness.equip(caster, ItemId.STAFF_OF_SARADOMIN.id(), 1);
		final Npc primary = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 521, 700, 100);
		final Npc child = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 522, 700, 100);
		final Npc distant = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 525, 700, 100);
		final Npc crossLevel = npcWithHits(harness, NpcId.GREATER_DEMON.id(),
			522, LegacyPackedPointAdapter.LEVEL_STRIDE + 700, 100);
		final Npc summon = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 520, 701, 100);
		summon.setAttribute(SUMMON_OWNER_KEY, caster.getUsernameHash());
		final SpellDef spell = harness.server().getEntityHandler()
			.getSpellDef(Spells.SARADOMIN_STRIKE);
		final Map<Integer, Integer> runesBefore = runeCounts(caster, spell);
		final int experienceBefore = caster.getSkills()
			.getExperience(Skill.MAGIC.id());
		harness.random().reset(0xA054E01L);
		harness.random().scriptInts(repeat(Integer.valueOf(-1), 24));

		castOnNpc(caster, primary, Spells.SARADOMIN_STRIKE);
		final GameTickEvent areaEvent = harness.findEvent("God spell area effect");
		assertNotNull(areaEvent, "God spell schedules its delayed area event");
		assertEquals(100, child.getLevel(Skill.HITS.id()),
			"God spell child remains unchanged before the scheduler tick");
		assertRunesConsumed(caster, spell, runesBefore,
			"God spell consumes its rune cost at cast time");
		assertEquals(experienceBefore + spell.getExp(),
			caster.getSkills().getExperience(Skill.MAGIC.id()),
			"God spell awards only its normal cast experience");

		harness.advanceOneCombatTick();

		assertTrue(child.getLevel(Skill.HITS.id()) < 100,
			"God spell damages an eligible child after one scheduler tick");
		assertHit(child, 100 - child.getLevel(Skill.HITS.id()),
			HitSplat.TYPE_STANDARD, "God spell child presentation");
		assertTrue(contribution(child, caster) > 0,
			"God spell child records Magic contribution");
		assertTrue(child.isChasing(),
			"God spell surviving child starts chasing the caster");
		assertEquals(100, distant.getLevel(Skill.HITS.id()),
			"God spell excludes distant NPCs");
		assertEquals(100, crossLevel.getLevel(Skill.HITS.id()),
			"God spell excludes matching coordinates on another level");
		assertEquals(100, summon.getLevel(Skill.HITS.id()),
			"God spell excludes summoned NPCs");
		assertTrue(caster.getLevel(Skill.HITS.id()) > 10,
			"Saradomin area spell applies one aggregate lifesteal after children");
		assertTrue(hasHealHit(caster),
			"Saradomin aggregate lifesteal publishes one heal presentation");
		assertDamageResult(resultFor(harness, child,
			"delayed-god-spell-secondary"), caster, child,
			"delayed-god-spell-secondary", areaEvent.getUUID(),
			100 - child.getLevel(Skill.HITS.id()),
			DamageRequest.Presentation.DAMAGE_AND_HITSPLAT,
			"God spell child transaction");

		characterizeCompatibilityLethalOrder(harness, caster);
	}

	static void ibanBlastAreaPolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player caster = spellCaster(
			harness, "delayed iban", 550, 700, Spells.IBAN_BLAST);
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 10, 40, false);
		caster.setQuestStage(Quests.UNDERGROUND_PASS, -1);
		harness.equip(caster, ItemId.STAFF_OF_IBAN.id(), 1);
		final Npc primary = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 551, 700, 100);
		final Npc child = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 552, 700, 100);
		final Npc distant = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 555, 700, 100);
		final SpellDef spell = harness.server().getEntityHandler()
			.getSpellDef(Spells.IBAN_BLAST);
		final Map<Integer, Integer> runesBefore = runeCounts(caster, spell);
		final int experienceBefore = caster.getSkills()
			.getExperience(Skill.MAGIC.id());
		harness.random().reset(0xA054E02L);
		harness.random().scriptInts(repeat(Integer.valueOf(-1), 24));

		castOnNpc(caster, primary, Spells.IBAN_BLAST);
		final GameTickEvent areaEvent = harness.findEvent("Iban blast area effect");
		assertNotNull(areaEvent, "Iban Blast schedules its delayed area event");
		assertEquals(100, child.getLevel(Skill.HITS.id()),
			"Iban child remains unchanged before the scheduler tick");
		assertRunesConsumed(caster, spell, runesBefore,
			"Iban Blast consumes its rune cost at cast time");
		assertEquals(experienceBefore + spell.getExp(),
			caster.getSkills().getExperience(Skill.MAGIC.id()),
			"Iban Blast awards only its normal cast experience");

		harness.advanceOneCombatTick();

		assertTrue(child.getLevel(Skill.HITS.id()) < 100,
			"Iban Blast damages an eligible child after one scheduler tick");
		assertHit(child, 100 - child.getLevel(Skill.HITS.id()),
			HitSplat.TYPE_STANDARD, "Iban Blast child presentation");
		assertTrue(contribution(child, caster) > 0,
			"Iban Blast child records Magic contribution");
		assertTrue(child.isChasing(),
			"Iban Blast surviving child starts chasing the caster");
		assertEquals(100, distant.getLevel(Skill.HITS.id()),
			"Iban Blast excludes distant NPCs");
		assertEquals(10, caster.getLevel(Skill.HITS.id()),
			"Iban Blast area damage has no aggregate lifesteal");
		assertFalse(hasHealHit(caster),
			"Iban Blast publishes no healing presentation");
		assertDamageResult(resultFor(harness, child,
			"delayed-iban-blast-secondary"), caster, child,
			"delayed-iban-blast-secondary", areaEvent.getUUID(),
			100 - child.getLevel(Skill.HITS.id()),
			DamageRequest.Presentation.DAMAGE_AND_HITSPLAT,
			"Iban Blast child transaction");
	}

	static void salarinStrikePolicies(final CurrentCombatHarness harness)
			throws Exception {
		CurrentCombatCharacterizationTest.resetDamageObserver(harness);
		final Player caster = spellCaster(
			harness, "delayed salarin", 580, 700, Spells.FIRE_STRIKE);
		final Npc salarin = npcWithHits(
			harness, NpcId.SALARIN_THE_TWISTED.id(), 581, 700, 40);
		final SpellDef spell = harness.server().getEntityHandler()
			.getSpellDef(Spells.FIRE_STRIKE);
		final Map<Integer, Integer> runesBefore = runeCounts(caster, spell);
		final int experienceBefore = caster.getSkills()
			.getExperience(Skill.MAGIC.id());
		forceNextLegacyInt(5, 4);

		castOnNpc(caster, salarin, Spells.FIRE_STRIKE);
		final GameTickEvent secondHit = harness.findEvent(
			"Salarin the Twisted Strike");
		assertNotNull(secondHit, "Salarin schedules its delayed second hit");
		assertEquals(40, salarin.getLevel(Skill.HITS.id()),
			"Salarin takes no damage before the scheduler tick");
		assertRunesConsumed(caster, spell, runesBefore,
			"Salarin strike consumes its normal rune cost");
		assertEquals(experienceBefore,
			caster.getSkills().getExperience(Skill.MAGIC.id()),
			"Salarin strike intentionally awards no Magic experience");

		harness.advanceOneCombatTick();

		assertEquals(24, salarin.getLevel(Skill.HITS.id()),
			"Salarin receives fixed Fire Strike then delayed additional damage");
		assertEquals(4,
			salarin.getUpdateFlags().getDamage().get().getDamage(),
			"Salarin delayed damage update overwrites the primary update");
		assertEquals(1, salarin.getUpdateFlags().getHitSplats().size(),
			"Salarin delayed hit intentionally adds no second hitsplat");
		assertEquals(12,
			salarin.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"Salarin retains only the primary hitsplat");
		assertEquals(16, contribution(salarin, caster),
			"Salarin records both primary and delayed Magic contribution");
		assertDamageResult(resultFor(harness, salarin,
			"delayed-salarin-strike-secondary"), caster, salarin,
			"delayed-salarin-strike-secondary", secondHit.getUUID(), 4,
			DamageRequest.Presentation.DAMAGE_ONLY,
			"Salarin delayed transaction");
	}

	private static void characterizeCompatibilityLethalOrder(
			final CurrentCombatHarness harness, final Player caster)
			throws Exception {
		final SpellHandler handler = new SpellHandler();
		final Npc lethal = npcWithHits(
			harness, NpcId.GREATER_DEMON.id(), 530, 700, 5);
		lethal.setShouldRespawn(false);
		lethal.setOpponent(caster);
		final AtomicInteger deaths = new AtomicInteger();
		final AtomicBoolean presentationAtDeath = new AtomicBoolean();
		final AtomicInteger contributionAtDeath = new AtomicInteger();
		lethal.addDeathListener(new NpcLootEvent(harness.world(),
			lethal.getLocation(), lethal.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				deaths.incrementAndGet();
				presentationAtDeath.set(
					lethal.getUpdateFlags().getDamage().get() != null
						|| !lethal.getUpdateFlags().getHitSplats().isEmpty());
				try {
					contributionAtDeath.set(contribution(lethal, caster));
				} catch (final Exception failure) {
					throw new AssertionError(failure);
				}
			}
		});
		final int applied = ((Integer) invoke(handler,
			"applyGodSpellSecondaryDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			caster, lethal, Integer.valueOf(7))).intValue();
		assertEquals(5, applied,
			"God/Iban helper reports capped lethal overkill");
		assertEquals(1, deaths.get(),
			"God/Iban helper invokes one lethal callback");
		assertFalse(presentationAtDeath.get(),
			"God/Iban helper death precedes damage presentation");
		assertEquals(0, contributionAtDeath.get(),
			"God/Iban helper death precedes delayed Magic contribution");
		assertHit(lethal, 7, HitSplat.TYPE_STANDARD,
			"God/Iban helper displayed lethal overkill");
		assertEquals(5, contribution(lethal, caster),
			"God/Iban helper adds delayed Magic contribution after death");
		assertEquals(5, lethal.getLevel(Skill.HITS.id()),
			"Compatibility-helper death retains the pre-death raw Hits value");
		assertNoResultForTarget(harness, lethal,
			"Lethal compatibility settlement remains outside the transaction");
	}

	private static Player spellCaster(final CurrentCombatHarness harness,
			final String name, final int x, final int y, final Spells spell)
			throws Exception {
		final Player caster = harness.player(name, x, y);
		caster.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MAGIC.id(), 99, 99, false);
		final SpellDef definition = harness.server().getEntityHandler()
			.getSpellDef(spell);
		for (Map.Entry<Integer, Integer> rune : definition.getRunesRequired()) {
			assertTrue(caster.getCarriedItems().getInventory().add(
				new Item(rune.getKey(), rune.getValue() + 10)),
				"Spell fixture adds rune " + rune.getKey());
		}
		return caster;
	}

	private static void castOnNpc(final Player caster, final Npc target,
			final Spells spell) throws Exception {
		final SpellStruct payload = new SpellStruct();
		payload.setOpcode(OpcodeIn.CAST_ON_NPC);
		payload.spell = spell;
		payload.targetIndex = target.getIndex();
		new SpellHandler().process(payload, caster);
		final WalkToAction approach = caster.getWalkToAction();
		assertNotNull(approach, spell + " installs a cast approach");
		assertTrue(approach.shouldExecute(), spell + " approach is executable");
		approach.execute();
	}

	private static Map<Integer, Integer> runeCounts(final Player caster,
			final SpellDef spell) {
		final Map<Integer, Integer> counts =
			new LinkedHashMap<Integer, Integer>();
		for (Map.Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			counts.put(rune.getKey(), caster.getCarriedItems().getInventory()
				.countId(rune.getKey()));
		}
		return counts;
	}

	private static void assertRunesConsumed(final Player caster,
			final SpellDef spell, final Map<Integer, Integer> before,
			final String label) {
		for (Map.Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			assertEquals(before.get(rune.getKey()).intValue() - rune.getValue(),
				caster.getCarriedItems().getInventory().countId(rune.getKey()),
				label + " rune " + rune.getKey());
		}
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int npcId, final int x, final int y, final int hits) {
		final Npc npc = harness.npc(npcId, x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static int contribution(final Npc target, final Player caster)
			throws Exception {
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> value = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, "getMageDamageInfoBy",
				new Class<?>[] {java.util.UUID.class}, caster.getUUID());
		return value.getLeft().intValue();
	}

	private static boolean hasHealHit(final Player player) {
		for (HitSplat hit : player.getUpdateFlags().getHitSplats()) {
			if (hit.getType() == HitSplat.TYPE_HEAL) {
				return true;
			}
		}
		return false;
	}

	private static DamageResult resultFor(final CurrentCombatHarness harness,
			final Mob target, final String effectKey) {
		for (DamageResult result : CurrentCombatCharacterizationTest
				.observedDamageResults(harness)) {
			final DamageRequest request = result.getRequest();
			if (request.getTarget() == target
					&& effectKey.equals(request.getEffectKey())) {
				return result;
			}
		}
		throw new AssertionError("No result for " + effectKey
			+ " targeting " + target);
	}

	private static void assertNoResultForTarget(
			final CurrentCombatHarness harness, final Mob target,
			final String label) {
		for (DamageResult result : CurrentCombatCharacterizationTest
				.observedDamageResults(harness)) {
			assertTrue(result.getRequest().getTarget() != target,
				label + ": " + result.getRequest().getEffectKey());
		}
	}

	private static void assertDamageResult(final DamageResult result,
			final Mob source, final Mob target, final String effectKey,
			final UUID eventId, final int damage,
			final DamageRequest.Presentation presentation,
			final String label) {
		final DamageRequest request = result.getRequest();
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			result.getStatus(), label + " status");
		assertTrue(request.getSource() == source, label + " source identity");
		assertTrue(request.getTarget() == target, label + " target identity");
		assertEquals(DamageRequest.SourceCategory.OWNED_EFFECT,
			request.getSourceCategory(), label + " category");
		assertEquals(effectKey, request.getEffectKey(), label + " stable identity");
		assertEquals(CombatStyle.MAGIC, request.getStyle(), label + " style");
		assertEquals(eventId, request.getEventId(), label + " event identity");
		assertEquals(presentation, request.getPresentation(),
			label + " presentation policy");
		assertEquals(damage, request.getResolvedDamage(),
			label + " resolved damage");
		assertEquals(damage, result.getActualDamage(),
			label + " factual damage");
		assertEquals(damage, result.getLegacyDamageDealt(),
			label + " legacy damage");
		assertEquals(0, result.getOverkillDamage(), label + " overkill");
		assertFalse(result.isTargetTerminal(), label + " terminal outcome");
	}

	private static Integer[] repeat(final Integer value, final int count) {
		final Integer[] values = new Integer[count];
		java.util.Arrays.fill(values, value);
		return values;
	}

	private static void forceNextLegacyInt(final int bound,
			final int expected) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextInt(bound) == expected) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy integer seed found");
	}

	private static Object invoke(final Object target, final String method,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws Exception {
		return CurrentCombatHarness.invokePrivate(
			target, method, parameterTypes, arguments);
	}

	private static void assertHit(final Mob target, final int amount,
			final int type, final String label) {
		assertTrue(target.getUpdateFlags().getDamage().get() != null,
			label + " damage update");
		assertEquals(amount,
			target.getUpdateFlags().getDamage().get().getDamage(),
			label + " damage amount");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			label + " hitsplat cardinality");
		assertEquals(amount,
			target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			label + " hitsplat amount");
		assertEquals(type,
			target.getUpdateFlags().getHitSplats().get(0).getType(),
			label + " hitsplat type");
	}

	private static void assertNotNull(final Object value,
			final String message) {
		assertTrue(value != null, message);
	}

	private static void assertTrue(final boolean condition,
			final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition,
			final String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
