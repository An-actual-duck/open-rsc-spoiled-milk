package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.ClericSupportTargeting;
import com.openrsc.server.content.cleric.effect.ClericEffectCatalog;
import com.openrsc.server.content.cleric.effect.ClericEffectClock;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigin;
import com.openrsc.server.content.cleric.effect.ClericEffectOriginValidator;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRankDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.content.party.PartyRank;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable current-state combat specifications for the authoritative Ant build. */
public final class CurrentCombatCharacterizationTest {
	private static final String SUMMARY_FILE =
		System.getProperty("combat.summary.file", "");
	private static final boolean FORCE_ZERO_SCENARIOS =
		Boolean.getBoolean("combat.forceZeroScenarios");
	private static int passed;

	private CurrentCombatCharacterizationTest() {
	}

	public static void main(final String[] arguments) throws Exception {
		if (FORCE_ZERO_SCENARIOS) {
			writeSummary();
			return;
		}

		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			run(harness, "melee_ranged_magic_damage_share_xp",
				CurrentCombatCharacterizationTest::damageShareExperience);
			run(harness, "attack_eligibility_precedes_plugin_callback",
				CurrentCombatCharacterizationTest::attackEligibilityAndPluginOrder);
			run(harness, "layered_domain_and_line_of_effect_reject_cross_level",
				CurrentCombatCharacterizationTest::layeredDomainAndLineOfEffect);
			run(harness, "ordinary_npc_retreat_then_hostile_reengagement",
				CurrentCombatCharacterizationTest::ordinaryNpcRetreatAndReengagement);
			run(harness, "npc_poison_clears_on_death_and_respawn",
				CurrentCombatCharacterizationTest::poisonDeathAndRespawn);
			run(harness, "cleric_ward_aegis_rally_thorns_order",
				CurrentCombatCharacterizationTest::clericDirectEffectOrder);
			run(harness, "summon_damage_contributes_to_owner_credit_not_style_xp",
				CurrentCombatCharacterizationTest::summonOwnerContribution);
			run(harness, "scythe_cleave_selects_adjacent_secondary_npcs",
				CurrentCombatCharacterizationTest::scytheCleaveSelection);
			run(harness, "shuriken_selects_three_unique_valid_targets",
				CurrentCombatCharacterizationTest::shurikenSelection);
			run(harness, "support_aoe_excludes_caster_cross_level_and_blocked",
				CurrentCombatCharacterizationTest::supportAreaSelection);
			run(harness, "npc_death_listener_is_exactly_once",
				CurrentCombatCharacterizationTest::exactlyOnceDeathCallback);
		}
		writeSummary();
		System.out.println("Combat characterization scenarios passed: " + passed);
	}

	private static void damageShareExperience(final CurrentCombatHarness harness)
			throws Exception {
		final Player player = harness.player("damage share", 100, 100);
		final Npc npc = harness.npc(3, 101, 100);
		player.setHitsXpFocus(com.openrsc.server.constants.Skills.AGGRESSIVE_MODE);

		final int meleeBefore = player.getSkills().getExperience(Skill.MELEE.id());
		final int rangedBefore = player.getSkills().getExperience(Skill.RANGED.id());
		final int magicBefore = player.getSkills().getExperience(Skill.MAGIC.id());
		final int hitsBefore = player.getSkills().getExperience(Skill.HITS.id());
		CurrentCombatHarness.invokePrivate(npc, "awardDamageShareXp",
			new Class<?>[] {Player.class, int.class, int.class, int.class, int.class},
			player, Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1),
			Integer.valueOf(12));

		assertEquals(12, player.getSkills().getExperience(Skill.MELEE.id()) - meleeBefore,
			"melee primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.RANGED.id()) - rangedBefore,
			"ranged primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.MAGIC.id()) - magicBefore,
			"magic primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.HITS.id()) - hitsBefore,
			"shared Hits-focus XP");
	}

	private static void attackEligibilityAndPluginOrder(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingAttackPlugin plugin = new RecordingAttackPlugin();
		harness.installPlugin(AttackNpcTrigger.class, plugin);
		final Player player = harness.player("attack order", 130, 130);
		final Npc npc = harness.npc(3, 131, 130);
		npc.setHostile(player, HostileState.HostilityType.PROVOKED);

		processNpcAttack(player, npc);
		final WalkToAction action = player.getWalkToAction();
		assertNotNull(action, "eligible attack walk action");
		assertTrue(action.shouldExecute(), "eligible adjacent attack must execute");
		assertEquals(Collections.emptyList(), plugin.events(),
			"plugin callbacks must wait for attack eligibility and approach");
		action.execute();
		assertEquals(Collections.singletonList("block"), plugin.events(),
			"plugin block callback must precede its action callback");

		PluginTickEvent pluginEvent = null;
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (event instanceof PluginTickEvent
					&& event.getDescriptor().contains("RecordingAttackPlugin")) {
				pluginEvent = (PluginTickEvent) event;
				break;
			}
		}
		assertNotNull(pluginEvent, "scheduled attack plugin action");
		pluginEvent.run();
		assertEquals(Arrays.asList("block", "action"), plugin.events(),
			"attack plugin callback order");
	}

	private static void layeredDomainAndLineOfEffect(
			final CurrentCombatHarness harness) throws Exception {
		final boolean previous = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player player = harness.player("layer source", 160, 160);
			final Npc npc = harness.npc(
				3, 161, LegacyPackedPointAdapter.LEVEL_STRIDE + 160);
			assertFalse(player.sharesSpatialDomain(npc),
				"same packed geography on another signed level must be a different domain");
			assertFalse(PathValidation.checkHostileProjectilePath(
				harness.world(), player.getWorldLocation(), npc.getWorldLocation()),
				"hostile line of effect must reject cross-level targets");

			final RecordingAttackPlugin plugin = new RecordingAttackPlugin();
			harness.installPlugin(AttackNpcTrigger.class, plugin);
			npc.setHostile(player, HostileState.HostilityType.PROVOKED);
			processNpcAttack(player, npc);
			final WalkToAction action = player.getWalkToAction();
			assertNotNull(action, "cross-level request remains an approach attempt");
			assertFalse(action.shouldExecute(),
				"cross-level attack approach must fail before plugin dispatch");
			assertEquals(Collections.emptyList(), plugin.events(),
				"cross-level rejection must not invoke attack plugins");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = previous;
		}
	}

	private static void ordinaryNpcRetreatAndReengagement(
			final CurrentCombatHarness harness) throws Exception {
		final Player player = harness.player("retreat gate", 190, 190);
		final Npc npc = harness.npc(3, 191, 190);
		npc.clearHostility();
		npc.setRanAwayTimer();
		processNpcAttack(player, npc);
		assertNull(player.getWalkToAction(),
			"ordinary retreat window must reject a fresh attack request");

		npc.setHostile(player, HostileState.HostilityType.PROVOKED);
		processNpcAttack(player, npc);
		assertNotNull(player.getWalkToAction(),
			"current hostile target may re-engage during the retreat tick");
	}

	private static void poisonDeathAndRespawn(final CurrentCombatHarness harness) {
		final Player source = harness.player("poison source", 220, 220);
		final Npc npc = harness.npc(3, 221, 220);
		npc.applyPoison(120, source);
		assertTrue(npc.getCurrentPoisonPower() > 0, "poison fixture must be active");
		npc.remove();
		assertEquals(0, npc.getCurrentPoisonPower(), "death removal poison power");
		assertEquals(0, npc.getPoisonMaxPower(), "death removal poison ceiling");
		npc.applyPoison(120, source);
		assertEquals(0, npc.getCurrentPoisonPower(),
			"respawning NPC must reject stale poison application");

		final GameTickEvent respawn = harness.findEvent("Respawn NPC");
		assertNotNull(respawn, "scheduled production respawn callback");
		respawn.run();
		assertFalse(npc.isRespawning(), "respawn callback must restore live state");
		assertEquals(0, npc.getCurrentPoisonPower(), "respawn poison power");
		assertTrue(npc.canReceivePoison(), "new NPC lifetime may receive new poison");
	}

	private static void clericDirectEffectOrder(final CurrentCombatHarness harness)
			throws Exception {
		final Player caster = harness.player("cleric caster", 250, 550);
		final Player defender = harness.player("cleric defender", 251, 550);
		final Player attacker = harness.player("cleric attacker", 250, 551);
		final Npc enemy = harness.npc(3, 251, 551);
		final Party party = party(caster, defender, attacker);
		assertTrue(caster.getParty() == party && defender.getParty() == party,
			"Cleric origin party fixture");

		final ClericEffectRegistry defenderEffects = registry(defender);
		final ClericEffectOrigin defenderOrigin = ClericEffectOrigins.current(
			caster, defender);
		final ClericEffectOriginValidator defenderValidator =
			ClericEffectOrigins.validatorFor(defender);
		apply(defenderEffects, ClericSpellId.WARD, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.BeforeDamage ward =
			ClericDirectCombatRuntime.beforeDirectDamage(enemy, defender, 20);
		assertEquals(15, ward.getDamage(), "Ward 25 percent protection");
		assertEquals(5, ward.getPreventedDamage(), "Ward prevented damage");

		apply(defenderEffects, ClericSpellId.AEGIS, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.BeforeDamage aegis =
			ClericDirectCombatRuntime.beforeDirectDamage(enemy, defender, 20);
		assertEquals(10, aegis.getDamage(), "Aegis replaces Ward before damage");
		assertEquals(10, aegis.getPreventedDamage(), "Aegis prevented damage");

		apply(defenderEffects, ClericSpellId.THORNS, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.AfterDamage thorns =
			ClericDirectCombatRuntime.afterExistingLifesteal(enemy, defender, 20);
		assertEquals(1, thorns.getThornsDamage(),
			"Thorns resolves after established direct damage");

		final ClericEffectRegistry attackerEffects = registry(attacker);
		apply(attackerEffects, ClericSpellId.RALLY, 1,
			ClericEffectOrigins.current(caster, attacker),
			ClericEffectOrigins.validatorFor(attacker));
		attacker.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 40, 100, false);
		ClericDirectCombatRuntime.AfterDamage rally =
			ClericDirectCombatRuntime.afterExistingLifesteal(attacker, enemy, 10);
		assertEquals(2, rally.getRallyHealing(),
			"Rally resolves after existing lifesteal");
		assertEquals(42, attacker.getSkills().getLevel(Skill.HITS.id()),
			"Rally applies its returned healing");
	}

	@SuppressWarnings("unchecked")
	private static void summonOwnerContribution(final CurrentCombatHarness harness)
			throws Exception {
		final Player owner = harness.player("summon owner", 280, 280);
		final Npc target = harness.npc(3, 281, 280);
		target.addSummonDamage(owner, 2);
		assertTrue(target.hasDamageBy(owner),
			"summon damage must count toward owner contribution");
		assertTrue(target.getPreferredThreatTarget() == owner,
			"summon owner contribution participates in current threat selection");
		final List<Object> styleContributors = (List<Object>)
			CurrentCombatHarness.invokePrivate(target, "getAllDamageDealerIds",
				new Class<?>[0]);
		assertTrue(styleContributors.isEmpty(),
			"summon-only contribution remains excluded from combat-style XP");
	}

	private static void scytheCleaveSelection(final CurrentCombatHarness harness)
			throws Exception {
		final Player player = harness.player("scythe user", 310, 310);
		final Npc primary = harness.npc(3, 311, 310);
		final Npc adjacent = harness.npc(3, 310, 311);
		final Npc distant = harness.npc(3, 313, 310);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), player, primary);
		final Method selection = PvmMeleeEvent.class.getDeclaredMethod(
			"isValidScytheCleaveTarget", Player.class, Npc.class, Npc.class);
		selection.setAccessible(true);
		assertTrue(((Boolean) selection.invoke(event, player, primary, adjacent)).booleanValue(),
			"adjacent secondary NPC is scythe-cleave eligible");
		assertFalse(((Boolean) selection.invoke(event, player, primary, primary)).booleanValue(),
			"primary NPC cannot be selected twice by scythe cleave");
		assertFalse(((Boolean) selection.invoke(event, player, primary, distant)).booleanValue(),
			"distant NPC is outside scythe cleave");
	}

	@SuppressWarnings("unchecked")
	private static void shurikenSelection(final CurrentCombatHarness harness)
			throws Exception {
		harness.openRectangle(338, 344, 338, 344);
		final Player player = harness.player("shuriken user", 340, 340);
		final Npc primary = harness.npc(3, 341, 340);
		final Npc second = harness.npc(3, 340, 341);
		final Npc third = harness.npc(3, 341, 341);
		final ThrowingEvent event = new ThrowingEvent(
			harness.world(), player, 1, primary);
		final int itemId = ItemId.TIN_SHURIKEN.id();
		final List<Mob> targets = (List<Mob>) CurrentCombatHarness.invokePrivate(
			event, "selectThrowingTargets",
			new Class<?>[] {Player.class, int.class, int.class},
			player, Integer.valueOf(itemId),
			Integer.valueOf(RangeUtils.getThrowingAttackRadius(itemId)));
		assertEquals(3, targets.size(), "shuriken target cap");
		assertTrue(targets.contains(primary) && targets.contains(second)
			&& targets.contains(third), "all three valid shuriken targets selected");
		assertEquals(3, new java.util.HashSet<Mob>(targets).size(),
			"shuriken target identities must be unique");
	}

	private static void supportAreaSelection(final CurrentCombatHarness ignored) {
		final AreaCandidate caster = new AreaCandidate("caster", "global", 0, 0, 0, true);
		final AreaCandidate valid = new AreaCandidate("valid", "global", 0, 2, 2, true);
		final AreaCandidate crossLevel = new AreaCandidate("cross", "global", 1, 1, 1, true);
		final AreaCandidate blocked = new AreaCandidate("blocked", "global", 0, 1, 0, false);
		final AreaCandidate distant = new AreaCandidate("distant", "global", 0, 3, 0, true);
		final List<AreaCandidate> resolved = ClericSupportTargeting.resolve(
			caster, Arrays.asList(caster, valid, valid, crossLevel, blocked, distant),
			2, AreaCandidate.VIEW);
		assertEquals(Collections.singletonList(valid), resolved,
			"shared support AoE eligibility");
	}

	private static void exactlyOnceDeathCallback(final CurrentCombatHarness harness) {
		final Player player = harness.player("death owner", 370, 370);
		final Npc npc = harness.npc(3, 371, 370);
		npc.setShouldRespawn(false);
		npc.addCombatDamage(player, npc.getDef().getHits());
		npc.getSkills().setLevel(Skill.HITS.id(), 0);
		final AtomicInteger callbacks = new AtomicInteger();
		npc.addDeathListener(new NpcLootEvent(
			harness.world(), npc.getLocation(), npc.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer, final Npc ignoredNpc) {
				callbacks.incrementAndGet();
			}
		});
		npc.killedBy(player);
		npc.killedBy(player);
		assertEquals(1, callbacks.get(), "NPC loot listener callback count");
		assertTrue(npc.isUnregistering(),
			"non-respawning NPC remains queued for terminal unregister");
	}

	private static void processNpcAttack(final Player player, final Npc npc)
			throws Exception {
		final TargetMobStruct payload = new TargetMobStruct();
		payload.setOpcode(OpcodeIn.NPC_ATTACK);
		payload.serverIndex = npc.getIndex();
		new AttackHandler().process(payload, player);
	}

	private static ClericEffectRegistry registry(final Player player) {
		final ClericEffectRegistry registry = new ClericEffectRegistry(
			ClericEffectClock.system(player.getConfig().GAME_TICK));
		player.installTransientEffectState(registry);
		return registry;
	}

	private static void apply(final ClericEffectRegistry registry,
			final ClericSpellId spell, final int rank,
			final ClericEffectOrigin origin,
			final ClericEffectOriginValidator validator) {
		final ClericEffectRankDefinition<?> definition =
			ClericEffectCatalog.get(spell, rank);
		assertTrue(registry.apply(definition, origin, validator).isUseful(),
			"Cleric effect application " + spell + "/" + rank);
	}

	private static Party party(final Player... players) throws Exception {
		final Party party = new Party(players[0].getWorld());
		final Constructor<PartyPlayer> constructor =
			PartyPlayer.class.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		final Method setReference = PartyPlayer.class.getDeclaredMethod(
			"setPlayerReference", Player.class);
		setReference.setAccessible(true);
		for (Player player : players) {
			player.setParty(party);
			final PartyPlayer membership = constructor.newInstance(player.getUsername());
			setReference.invoke(membership, player);
			membership.setRank(party.getPlayers().isEmpty()
				? PartyRank.LEADER : PartyRank.NORMAL);
			party.getPlayers().add(membership);
			if (party.getLeader() == null) {
				party.setLeader(membership);
			}
		}
		return party;
	}

	private static void run(final CurrentCombatHarness harness,
			final String name, final Scenario scenario) throws Exception {
		try {
			scenario.run(harness);
			passed++;
			System.out.println("PASS " + name);
		} catch (Throwable failure) {
			System.err.println("FAIL " + name + ": " + failure.getMessage());
			if (failure instanceof Exception) {
				throw (Exception) failure;
			}
			if (failure instanceof Error) {
				throw (Error) failure;
			}
			throw new AssertionError(failure);
		}
	}

	private static void writeSummary() throws Exception {
		if (SUMMARY_FILE.isEmpty()) {
			throw new IllegalStateException("combat.summary.file is required");
		}
		final File summary = new File(SUMMARY_FILE);
		final File parent = summary.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException("Unable to create combat summary directory");
		}
		final Properties properties = new Properties();
		properties.setProperty("combat.summary.pass", Integer.toString(passed));
		properties.setProperty("combat.summary.scenarios", Integer.toString(passed));
		try (FileOutputStream output = new FileOutputStream(summary)) {
			properties.store(output, "current combat characterization receipt");
		}
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition, final String message) {
		assertTrue(!condition, message);
	}

	private static void assertNull(final Object value, final String message) {
		assertTrue(value == null, message + ": expected null, got " + value);
	}

	private static void assertNotNull(final Object value, final String message) {
		assertTrue(value != null, message + ": expected a value");
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private interface Scenario {
		void run(CurrentCombatHarness harness) throws Exception;
	}

	private static final class RecordingAttackPlugin implements AttackNpcTrigger {
		private final List<String> events = Collections.synchronizedList(
			new ArrayList<String>());

		@Override
		public boolean blockAttackNpc(final Player player, final Npc npc) {
			events.add("block");
			return true;
		}

		@Override
		public void onAttackNpc(final Player player, final Npc npc) {
			events.add("action");
		}

		List<String> events() {
			synchronized (events) {
				return new ArrayList<String>(events);
			}
		}
	}

	private static final class AreaCandidate {
		private static final ClericSupportTargeting.CandidateView<AreaCandidate> VIEW =
			new ClericSupportTargeting.CandidateView<AreaCandidate>() {
				@Override
				public boolean isEligibleRecipient(final AreaCandidate candidate) {
					return true;
				}

				@Override
				public Object getWorldSpace(final AreaCandidate candidate) {
					return candidate.worldSpace;
				}

				@Override
				public int getSignedLevel(final AreaCandidate candidate) {
					return candidate.level;
				}

				@Override
				public int getX(final AreaCandidate candidate) {
					return candidate.x;
				}

				@Override
				public int getY(final AreaCandidate candidate) {
					return candidate.y;
				}

				@Override
				public boolean hasLineOfEffect(final AreaCandidate caster,
						final AreaCandidate candidate) {
					return candidate.lineOfEffect;
				}
			};

		private final String name;
		private final String worldSpace;
		private final int level;
		private final int x;
		private final int y;
		private final boolean lineOfEffect;

		private AreaCandidate(final String name, final String worldSpace,
				final int level, final int x, final int y,
				final boolean lineOfEffect) {
			this.name = name;
			this.worldSpace = worldSpace;
			this.level = level;
			this.x = x;
			this.y = y;
			this.lineOfEffect = lineOfEffect;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
