package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.CustomProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.FireCannonEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.combat.ProjectileResourceLedger.Coverage;
import com.openrsc.server.model.combat.ProjectileResourceLedger.ExperienceAward;
import com.openrsc.server.model.combat.ProjectileResourceLedger.ItemCost;
import com.openrsc.server.model.combat.ProjectileResourceLedger.Preservation;
import com.openrsc.server.model.combat.ProjectileResourceLedger.RecoveryDestination;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable A06.4 launch-resource and progression settlement policies. */
final class CurrentCombatProjectileResourceCharacterization {
	private static final String MANUAL_SUMMON_KEY = "myworld_manual_summon";
	private static final String SUMMON_OWNER_KEY = "myworld_summon_owner";
	private static final String SUMMON_KIND_KEY = "myworld_summon_kind";

	private CurrentCombatProjectileResourceCharacterization() {
	}

	static void ledgerContractAndCoverage(
			final CurrentCombatHarness harness) {
		for (ProjectileLaunchSpecification.Producer producer
				: ProjectileLaunchSpecification.Producer.values()) {
			final ProjectileResourceLedger ledger =
				ProjectileResourceLedger.defaultFor(producer);
			assertEquals(expectedCoverage(producer), ledger.getCoverage(),
				producer + " default resource coverage");
			assertEquals(ProjectileResourceLedger.State.SEALED,
				ledger.getState(), producer + " default ledger is immutable");
		}

		final Player source = harness.player("resource contract", 610, 760);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 611, 760);
		final ProjectileResourceLedger tracked =
			ProjectileResourceLedger.trackedLaunch(
				ProjectileLaunchSpecification.Producer.PLAYER_BOW);
		tracked.recordItemCost(ItemId.TIN_ARROWS.id(), 1, 1,
			ProjectileResourceLedger.ItemSource.EQUIPMENT,
			Preservation.NONE);
		tracked.recordRecovery(ItemId.TIN_ARROWS.id(), 0,
			RecoveryDestination.NOT_RECOVERED, target.getWorldLocation());
		tracked.recordExperience(Skill.RANGED.id(), 4, 4,
			ProjectileResourceLedger.ExperienceBasis.RANGED_HIT);
		tracked.seal();
		final ProjectileEvent event = new ProjectileEvent(
			harness.world(), source, target,
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.PLAYER_BOW, 1, 2)
				.build(), tracked);

		assertEquals(event.getUUID(), tracked.getEventId(),
			"resource receipt binds to one projectile identity");
		assertTrue(event.getProjectileResourceLedger() == tracked,
			"projectile exposes its exact launch receipt");
		assertThrows(UnsupportedOperationException.class,
			() -> tracked.getItemCosts().add(null),
			"resource snapshots are unmodifiable");
		assertThrows(IllegalStateException.class,
			() -> tracked.recordExperience(Skill.RANGED.id(), 1, 1,
				ProjectileResourceLedger.ExperienceBasis.RANGED_HIT),
			"sealed receipt rejects later progression writes");
		assertThrows(IllegalStateException.class,
			() -> tracked.bindEvent(UUID.randomUUID(),
				ProjectileLaunchSpecification.Producer.PLAYER_BOW),
			"receipt cannot bind to a second event");
		event.setCanceled(true);
		event.action();
	}

	static void rangedAndThrownLaunchSettlement(
			final CurrentCombatHarness harness) throws Exception {
		final Player ranger = harness.player("resource bow", 620, 760);
		final Npc rangedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 621, 760);
		harness.equip(ranger, ItemId.SHORTBOW.id(), 1);
		harness.equip(ranger, ItemId.TIN_ARROWS.id(), 3);
		final int rangedExperienceBefore = ranger.getSkills()
			.getExperience(Skill.RANGED.id());
		final RangeEvent range = new RangeEvent(
			harness.world(), ranger, 1L, rangedTarget);
		ranger.setRangeEvent(range);
		harness.random().reset(0xA064B01L);
		range.run();
		final ProjectileEvent bowProjectile = findProjectile(
			harness, ranger,
			ProjectileLaunchSpecification.Producer.PLAYER_BOW, 0);
		assertNotNull(bowProjectile, "bow launch schedules one projectile");
		final ProjectileResourceLedger bowLedger =
			bowProjectile.getProjectileResourceLedger();
		assertTrackedAndSealed(bowLedger, "bow receipt");
		assertCost(bowLedger.getItemCosts().get(0),
			ItemId.TIN_ARROWS.id(), 1, 1,
			ProjectileResourceLedger.ItemSource.EQUIPMENT,
			Preservation.NONE, "bow equipment cost");
		assertEquals(2, equipmentAmount(ranger, ItemId.TIN_ARROWS.id()),
			"bow removes one equipped arrow at launch");
		assertRangedExperienceReceipt(bowProjectile, bowLedger,
			rangedExperienceBefore, ranger, rangedTarget, "bow XP");
		assertEquals(1, bowLedger.getRecoveries().size(),
			"bow makes exactly one recovery decision");

		final int bowAmmoAfterLaunch =
			equipmentAmount(ranger, ItemId.TIN_ARROWS.id());
		final int bowExperienceAfterLaunch = ranger.getSkills()
			.getExperience(Skill.RANGED.id());
		ranger.setLoggedIn(false);
		bowProjectile.action();
		bowProjectile.action();
		assertEquals(bowAmmoAfterLaunch,
			equipmentAmount(ranger, ItemId.TIN_ARROWS.id()),
			"invalid and duplicate bow impacts cannot consume or refund ammo");
		assertEquals(bowExperienceAfterLaunch, ranger.getSkills()
			.getExperience(Skill.RANGED.id()),
			"invalid and duplicate bow impacts cannot replay XP");
		ranger.setLoggedIn(true);

		final Player thrower = harness.player("resource shuriken", 630, 760);
		harness.openRectangle(628, 632, 758, 762);
		final Npc primary = harness.npc(
			NpcId.GREATER_DEMON.id(), 631, 760);
		harness.npc(NpcId.GREATER_DEMON.id(), 630, 761);
		harness.npc(NpcId.GREATER_DEMON.id(), 631, 761);
		harness.equip(thrower, ItemId.TIN_SHURIKEN.id(), 4);
		final int thrownExperienceBefore = thrower.getSkills()
			.getExperience(Skill.RANGED.id());
		final ThrowingEvent throwing = new ThrowingEvent(
			harness.world(), thrower, 1L, primary);
		thrower.setThrowingEvent(throwing);
		harness.random().reset(0xA064701L);
		harness.random().scriptInts(
			Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(-1),
			Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(-1),
			Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(-1),
			Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(-1));
		throwing.run();
		final List<ProjectileEvent> siblings = findProjectiles(
			harness, thrower,
			ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN);
		assertEquals(3, siblings.size(),
			"one shuriken volley creates three resource siblings");
		assertEquals(1, equipmentAmount(thrower, ItemId.TIN_SHURIKEN.id()),
			"three shuriken siblings consume exactly three projectiles");
		final Set<UUID> receiptEvents = new HashSet<UUID>();
		int recordedExperience = 0;
		for (ProjectileEvent sibling : siblings) {
			final ProjectileResourceLedger ledger =
				sibling.getProjectileResourceLedger();
			assertTrackedAndSealed(ledger, "shuriken sibling receipt");
			assertCost(ledger.getItemCosts().get(0),
				ItemId.TIN_SHURIKEN.id(), 1, 1,
				ProjectileResourceLedger.ItemSource.EQUIPMENT,
				Preservation.NONE, "shuriken sibling cost");
			assertEquals(1, ledger.getRecoveries().size(),
				"each shuriken has one recovery decision");
			assertEquals(1, ledger.getExperienceAwards().size(),
				"each shuriken has one XP decision");
			recordedExperience += ledger.getExperienceAwards().get(0)
				.getAppliedAmount();
			receiptEvents.add(ledger.getEventId());
		}
		assertEquals(3, receiptEvents.size(),
			"shuriken siblings retain distinct receipt identities");
		assertEquals(thrower.getSkills().getExperience(Skill.RANGED.id())
			- thrownExperienceBefore, recordedExperience,
			"shuriken sibling XP receipts conserve the awarded total");
		final int shurikenAfterLaunch =
			equipmentAmount(thrower, ItemId.TIN_SHURIKEN.id());
		final int shurikenExperienceAfterLaunch = thrower.getSkills()
			.getExperience(Skill.RANGED.id());
		thrower.getSkills().setLevel(Skill.HITS.id(), 0);
		for (ProjectileEvent sibling : siblings) {
			sibling.action();
			sibling.action();
		}
		assertEquals(shurikenAfterLaunch,
			equipmentAmount(thrower, ItemId.TIN_SHURIKEN.id()),
			"source death and duplicate sibling callbacks do not alter ammo");
		assertEquals(shurikenExperienceAfterLaunch, thrower.getSkills()
			.getExperience(Skill.RANGED.id()),
			"source death and duplicate sibling callbacks do not alter XP");

		final Player dartUser = harness.player("resource dart", 635, 760);
		final Npc dartTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 636, 760);
		harness.equip(dartUser, ItemId.TIN_THROWING_DART.id(), 2);
		final ThrowingEvent dartThrow = new ThrowingEvent(
			harness.world(), dartUser, 1L, dartTarget);
		dartUser.setThrowingEvent(dartThrow);
		harness.random().reset(0xA064DA7L);
		dartThrow.run();
		final ProjectileEvent dartProjectile = findProjectile(
			harness, dartUser,
			ProjectileLaunchSpecification.Producer.PLAYER_THROWN, 0);
		assertNotNull(dartProjectile,
			"ordinary thrown launch schedules its own producer family");
		assertCost(dartProjectile.getProjectileResourceLedger()
			.getItemCosts().get(0), ItemId.TIN_THROWING_DART.id(), 1, 1,
			ProjectileResourceLedger.ItemSource.EQUIPMENT,
			Preservation.NONE, "ordinary thrown cost");
		assertEquals(1,
			equipmentAmount(dartUser, ItemId.TIN_THROWING_DART.id()),
			"ordinary thrown launch consumes exactly one item");
		stopOwnedEvents(harness, ranger, thrower, dartUser);
	}

	static void inventoryModeAndRecoverySettlement(
			final CurrentCombatHarness harness) throws Exception {
		final boolean equipmentMode =
			harness.server().getConfig().WANT_EQUIPMENT_TAB;
		try {
			harness.server().getConfig().WANT_EQUIPMENT_TAB = false;
			final Player ranger = harness.player(
				"resource inventory bow", 640, 760);
			ranger.getClientLimitations().maxItemId = Integer.MAX_VALUE;
			final Npc target = harness.npc(
				NpcId.GREATER_DEMON.id(), 641, 760);
			final Item bow = new Item(ItemId.SHORTBOW.id(), 1);
			final Item arrows = new Item(ItemId.TIN_ARROWS.id(), 3);
			assertTrue(ranger.getCarriedItems().getInventory().add(bow),
				"inventory-mode bow fixture");
			assertTrue(ranger.getCarriedItems().getInventory().add(arrows),
				"inventory-mode arrow fixture");
			ranger.getCarriedItems().getInventory().get(
				ranger.getCarriedItems().getInventory()
					.getLastIndexById(ItemId.SHORTBOW.id())).setWielded(true);
			ranger.getCarriedItems().getInventory().get(
				ranger.getCarriedItems().getInventory()
					.getLastIndexById(ItemId.TIN_ARROWS.id())).setWielded(true);
			final RangeEvent range = new RangeEvent(
				harness.world(), ranger, 1L, target);
			ranger.setRangeEvent(range);
			harness.random().reset(0xA064102L);
			range.run();
			final ProjectileEvent projectile = findProjectile(
				harness, ranger,
				ProjectileLaunchSpecification.Producer.PLAYER_BOW, 0);
			assertNotNull(projectile,
				"inventory-mode bow schedules a projectile");
			assertCost(projectile.getProjectileResourceLedger()
				.getItemCosts().get(0), ItemId.TIN_ARROWS.id(), 1, 1,
				ProjectileResourceLedger.ItemSource.INVENTORY,
				Preservation.NONE, "inventory-mode bow cost");
			assertEquals(2, ranger.getCarriedItems().getInventory()
				.countId(ItemId.TIN_ARROWS.id()),
				"inventory-mode bow removes one arrow");
			projectile.stop();
		} finally {
			harness.server().getConfig().WANT_EQUIPMENT_TAB = equipmentMode;
		}

		final Player groundOwner = harness.player(
			"resource ground", 650, 760);
		groundOwner.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		final Npc groundTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 651, 760);
		harness.random().scriptInts(Integer.valueOf(0));
		assertEquals(RecoveryDestination.NOT_RECOVERED,
			RangeUtils.settleProjectileRecovery(harness.world(), groundOwner,
				groundTarget, 1, ItemId.TIN_ARROWS.id()),
			"recovery roll zero consumes the projectile");
		harness.random().scriptInts(Integer.valueOf(1), Integer.valueOf(1));
		assertEquals(RecoveryDestination.GROUND_NEW_STACK,
			RangeUtils.settleProjectileRecovery(harness.world(), groundOwner,
				groundTarget, 1, ItemId.TIN_ARROWS.id()),
			"first recoverable arrow creates an owned target-tile stack");
		assertEquals(RecoveryDestination.GROUND_EXISTING_STACK,
			RangeUtils.settleProjectileRecovery(harness.world(), groundOwner,
				groundTarget, 1, ItemId.TIN_ARROWS.id()),
			"second recoverable arrow extends the target-tile stack");
		final GroundItem groundStack = groundTarget.getViewArea()
			.getVisibleGroundItem(ItemId.TIN_ARROWS.id(),
				groundTarget.getLocation(), groundOwner);
		assertNotNull(groundStack, "recovered arrow stack is visible to owner");
		assertEquals(2, groundStack.getAmount(), "recovered stack amount");
		assertEquals(groundTarget.getWorldLocation(),
			groundStack.getWorldLocation(), "recovery uses target world location");
		assertEquals(groundOwner.getUsernameHash(),
			groundStack.getOwnerUsernameHash(), "recovery retains player ownership");

		final Player avariceOwner = harness.player(
			"resource avarice", 655, 760);
		avariceOwner.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		final Npc avariceTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 656, 760);
		harness.equip(avariceOwner, ItemId.RING_OF_AVARICE.id(), 1);
		harness.random().scriptInts(Integer.valueOf(1));
		assertEquals(RecoveryDestination.RING_OF_AVARICE,
			RangeUtils.settleProjectileRecovery(harness.world(), avariceOwner,
				avariceTarget, 1, ItemId.TIN_ARROWS.id()),
			"Avarice wins recovery priority");
		assertEquals(1, avariceOwner.getCarriedItems().getInventory()
			.countId(ItemId.TIN_ARROWS.id()), "Avarice collects recovered arrow");

		final Player goblinOwner = harness.player(
			"resource goblin", 660, 760);
		goblinOwner.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		final Npc goblinTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 661, 760);
		final Npc goblin = harness.npc(NpcId.LOOT_GOBLIN.id(), 660, 761);
		goblin.setAttribute(SUMMON_OWNER_KEY, goblinOwner.getUsernameHash());
		goblin.setAttribute(SUMMON_KIND_KEY, "loot_goblin");
		goblinOwner.setAttribute(MANUAL_SUMMON_KEY, goblin);
		harness.random().scriptInts(Integer.valueOf(1));
		assertEquals(RecoveryDestination.LOOT_GOBLIN,
			RangeUtils.settleProjectileRecovery(harness.world(), goblinOwner,
				goblinTarget, 1, ItemId.TIN_ARROWS.id()),
			"Loot Goblin follows Avarice in recovery priority");
		assertEquals(1, goblinOwner.getCarriedItems().getInventory()
			.countId(ItemId.TIN_ARROWS.id()),
			"Loot Goblin collects recovered arrow");

		final Player fullOwner = harness.player(
			"resource full", 665, 760);
		fullOwner.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		final Npc fullTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 666, 760);
		harness.equip(fullOwner, ItemId.RING_OF_AVARICE.id(), 1);
		final Npc fullGoblin = harness.npc(
			NpcId.LOOT_GOBLIN.id(), 665, 761);
		fullGoblin.setAttribute(SUMMON_OWNER_KEY, fullOwner.getUsernameHash());
		fullGoblin.setAttribute(SUMMON_KIND_KEY, "loot_goblin");
		fullOwner.setAttribute(MANUAL_SUMMON_KEY, fullGoblin);
		while (fullOwner.getCarriedItems().getInventory().getFreeSlots() > 0) {
			assertTrue(fullOwner.getCarriedItems().getInventory().add(
				new Item(ItemId.BONES.id(), 1)),
				"full-inventory recovery fixture");
		}
		harness.random().scriptInts(Integer.valueOf(1));
		assertEquals(RecoveryDestination.GROUND_NEW_STACK,
			RangeUtils.settleProjectileRecovery(harness.world(), fullOwner,
				fullTarget, 1, ItemId.TIN_ARROWS.id()),
			"full Avarice and Loot Goblin inventories fall back to ground");
	}

	static void magicLaunchSettlement(
			final CurrentCombatHarness harness) throws Exception {
		final Player ordinary = spellCaster(
			harness, "resource magic", 670, 760, Spells.WIND_STRIKE);
		final Npc ordinaryTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 671, 760);
		final SpellDef spell = harness.server().getEntityHandler()
			.getSpellDef(Spells.WIND_STRIKE);
		final int ordinaryXpBefore = ordinary.getSkills()
			.getExperience(Skill.MAGIC.id());
		castOnNpc(ordinary, ordinaryTarget, Spells.WIND_STRIKE);
		final ProjectileEvent ordinaryProjectile = findProjectile(
			harness, ordinary,
			ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 0);
		assertNotNull(ordinaryProjectile,
			"ordinary combat spell schedules a tracked projectile");
		final ProjectileResourceLedger ordinaryLedger =
			ordinaryProjectile.getProjectileResourceLedger();
		assertSpellReceipt(spell, ordinaryLedger, Preservation.NONE,
			"ordinary spell");
		assertEquals(ordinary.getSkills().getExperience(Skill.MAGIC.id())
			- ordinaryXpBefore,
			ordinaryLedger.getExperienceAwards().get(0).getAppliedAmount(),
			"ordinary spell receipt records actual XP");

		final Player capeCaster = spellCaster(
			harness, "resource cape", 675, 760, Spells.WIND_STRIKE);
		final Npc capeTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 676, 760);
		harness.equip(capeCaster, ItemId.MAGIC_CAPE.id(), 1);
		final int[] capeRunesBefore = runeCounts(capeCaster, spell);
		DataConversions.getRandom().setSeed(4096L);
		castOnNpc(capeCaster, capeTarget, Spells.WIND_STRIKE);
		final ProjectileResourceLedger capeLedger = findProjectile(
			harness, capeCaster,
			ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 0)
			.getProjectileResourceLedger();
		assertSpellReceipt(spell, capeLedger, Preservation.MAGIC_CAPE,
			"Magic-cape spell");
		assertRuneCounts(capeCaster, spell, capeRunesBefore,
			"Magic cape preserves all requested runes");

		final Player staffCaster = spellCaster(
			harness, "resource staff", 680, 760, Spells.WIND_STRIKE);
		final Npc staffTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 681, 760);
		harness.equip(staffCaster, ItemId.STAFF_OF_AIR.id(), 1);
		final int[] staffRunesBefore = runeCounts(staffCaster, spell);
		DataConversions.getRandom().setSeed(0L);
		castOnNpc(staffCaster, staffTarget, Spells.WIND_STRIKE);
		final ProjectileResourceLedger staffLedger = findProjectile(
			harness, staffCaster,
			ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 0)
			.getProjectileResourceLedger();
		assertTrackedAndSealed(staffLedger, "staff spell receipt");
		boolean equipmentPreserved = false;
		boolean ordinaryRemoval = false;
		for (ItemCost cost : staffLedger.getItemCosts()) {
			equipmentPreserved |= cost.getPreservation()
				== Preservation.EQUIPMENT_EFFECT && cost.getRemovedAmount() == 0;
			ordinaryRemoval |= cost.getPreservation() == Preservation.NONE
				&& cost.getRemovedAmount() == cost.getRequestedAmount();
		}
		assertTrue(equipmentPreserved,
			"staff receipt distinguishes a preserved rune");
		assertTrue(ordinaryRemoval,
			"staff receipt distinguishes a consumed rune");
		assertRuneDeltaMatchesReceipt(staffCaster, spell,
			staffRunesBefore, staffLedger, "staff rune conservation");

		final int[] ordinaryRunesAfter = runeCounts(ordinary, spell);
		final int ordinaryXpAfter = ordinary.getSkills()
			.getExperience(Skill.MAGIC.id());
		ordinary.setLoggedIn(false);
		ordinaryProjectile.action();
		ordinaryProjectile.action();
		assertRuneCounts(ordinary, spell, ordinaryRunesAfter,
			"logout and duplicate magic impact cannot replay rune costs");
		assertEquals(ordinaryXpAfter, ordinary.getSkills()
			.getExperience(Skill.MAGIC.id()),
			"logout and duplicate magic impact cannot replay cast XP");
		ordinary.setLoggedIn(true);
		stopOwnedEvents(harness, ordinary, capeCaster, staffCaster);
	}

	static void cannonAndShutdownSettlement(
			final CurrentCombatHarness harness) throws Exception {
		final Player owner = harness.player("resource cannon", 690, 760);
		owner.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		assertTrue(owner.getCarriedItems().getInventory().add(
			new Item(ItemId.MULTI_CANNON_BALL.id(), 2)),
			"cannonball fixture");
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 691, 760);
		owner.getLocalNpcs().add(target);
		final FireCannonEvent cannon = new FireCannonEvent(
			harness.world(), owner);
		owner.setCannonEvent(cannon);
		cannon.run();
		final ProjectileEvent projectile = findProjectile(
			harness, owner,
			ProjectileLaunchSpecification.Producer.CANNON, 0);
		assertNotNull(projectile, "cannon schedules a tracked projectile");
		assertCost(projectile.getProjectileResourceLedger()
			.getItemCosts().get(0), ItemId.MULTI_CANNON_BALL.id(), 1, 1,
			ProjectileResourceLedger.ItemSource.INVENTORY,
			Preservation.NONE, "cannon launch cost");
		assertEquals(1, owner.getCarriedItems().getInventory()
			.countId(ItemId.MULTI_CANNON_BALL.id()),
			"cannon consumes one ball at launch");
		assertEquals(0, projectile.getProjectileResourceLedger()
			.getExperienceAwards().size(),
			"cannon launch invents no progression award");
		assertEquals(0, projectile.getProjectileResourceLedger()
			.getRecoveries().size(), "cannon launch invents no recovery");

		final int ballsAfterLaunch = owner.getCarriedItems().getInventory()
			.countId(ItemId.MULTI_CANNON_BALL.id());
		projectile.stop();
		cannon.stop();
		harness.server().getGameEventHandler().cleanupEvents();
		assertEquals(ballsAfterLaunch, owner.getCarriedItems().getInventory()
			.countId(ItemId.MULTI_CANNON_BALL.id()),
			"shutdown cancellation neither refunds nor duplicates cannon cost");

		final Player failedSource = harness.player(
			"resource failed callback", 695, 760);
		failedSource.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		final Npc failedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 696, 760);
		assertTrue(failedSource.getCarriedItems().getInventory().add(
			new Item(ItemId.TIN_ARROWS.id(), 2)),
			"failed callback resource fixture");
		assertTrue(failedSource.getCarriedItems().remove(
			new Item(ItemId.TIN_ARROWS.id(), 1)) != -1,
			"failed callback settles one launch cost");
		final ProjectileResourceLedger failedLedger =
			ProjectileResourceLedger.trackedLaunch(
				ProjectileLaunchSpecification.Producer.MAGIC_SCRIPTED_EFFECT);
		failedLedger.recordItemCost(ItemId.TIN_ARROWS.id(), 1, 1,
			ProjectileResourceLedger.ItemSource.INVENTORY, Preservation.NONE);
		failedLedger.seal();
		final AtomicInteger failedInvocations = new AtomicInteger();
		final CustomProjectileEvent failed = new CustomProjectileEvent(
			harness.world(), failedSource, failedTarget,
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.MAGIC_SCRIPTED_EFFECT,
				0, 1).build(), failedLedger) {
			@Override
			public void doSpell() {
				failedInvocations.incrementAndGet();
				throw new IllegalStateException(
					"deliberate resource callback fixture failure");
			}
		};
		assertThrows(IllegalStateException.class, failed::action,
			"failed scripted callback remains a deliberate failure");
		assertEquals(ProjectileImpactLedger.State.FAILED,
			failed.getProjectileImpactState(),
			"failed callback closes impact ownership");
		failed.action();
		assertEquals(2, failed.getProjectileImpactCallbackCount(),
			"impact ledger records the duplicate attempt");
		assertEquals(1, failedInvocations.get(),
			"failed scripted work cannot replay");
		assertEquals(1, failedSource.getCarriedItems().getInventory()
			.countId(ItemId.TIN_ARROWS.id()),
			"failed and duplicate callbacks cannot refund or consume again");
		assertEquals(Coverage.CALLER_OWNED,
			ProjectileResourceLedger.defaultFor(
				ProjectileLaunchSpecification.Producer.GNOME_BALL).getCoverage(),
			"Gnome Ball retains caller-owned delayed item transfer");
	}

	private static Coverage expectedCoverage(
			final ProjectileLaunchSpecification.Producer producer) {
		switch (producer) {
			case PLAYER_BOW:
			case PLAYER_THROWN:
			case PLAYER_SHURIKEN:
			case PLAYER_MAGIC:
			case PLAYER_IBAN_MAGIC:
			case CANNON:
			case LEGACY_NPC_RANGED:
			case MAGIC_SCRIPTED_EFFECT:
			case LEGENDS_HOLY_WATER:
				return Coverage.UNRECORDED_TRACKED_PRODUCER;
			case GNOME_BALL:
				return Coverage.CALLER_OWNED;
			case COMPATIBILITY:
				return Coverage.UNCLASSIFIED_COMPATIBILITY;
			default:
				return Coverage.NO_PROJECTILE_RESOURCES;
		}
	}

	private static void assertTrackedAndSealed(
			final ProjectileResourceLedger ledger, final String label) {
		assertEquals(Coverage.TRACKED_LAUNCH, ledger.getCoverage(),
			label + " coverage");
		assertEquals(ProjectileResourceLedger.State.SEALED, ledger.getState(),
			label + " state");
		assertNotNull(ledger.getEventId(), label + " event identity");
	}

	private static void assertCost(final ItemCost cost, final int itemId,
			final int requested, final int removed,
			final ProjectileResourceLedger.ItemSource source,
			final Preservation preservation, final String label) {
		assertEquals(itemId, cost.getItemId(), label + " item");
		assertEquals(requested, cost.getRequestedAmount(),
			label + " requested");
		assertEquals(removed, cost.getRemovedAmount(), label + " removed");
		assertEquals(source, cost.getSource(), label + " source");
		assertEquals(preservation, cost.getPreservation(),
			label + " preservation");
	}

	private static void assertRangedExperienceReceipt(
			final ProjectileEvent projectile,
			final ProjectileResourceLedger ledger, final int experienceBefore,
			final Player player, final Npc target, final String label) {
		assertEquals(1, ledger.getExperienceAwards().size(),
			label + " one decision");
		final ExperienceAward award = ledger.getExperienceAwards().get(0);
		final int damage = projectile.getLaunchSnapshot().getSpecification()
			.getProposedDamage();
		final int expectedBase = (player.getConfig().RANGED_GIVES_XP_HIT
			&& damage > 0) ? Formulae.rangedHitExperience(target, damage) : 0;
		assertEquals(Skill.RANGED.id(), award.getSkillId(), label + " skill");
		assertEquals(expectedBase, award.getBaseAmount(), label + " base");
		assertEquals(player.getSkills().getExperience(Skill.RANGED.id())
			- experienceBefore, award.getAppliedAmount(), label + " applied");
	}

	private static int equipmentAmount(final Player player, final int itemId) {
		final Equipment equipment = player.getCarriedItems().getEquipment();
		final int slot = equipment.searchEquipmentForItem(itemId);
		return slot == -1 ? 0 : equipment.get(slot).getAmount();
	}

	private static Player spellCaster(final CurrentCombatHarness harness,
			final String name, final int x, final int y, final Spells spell)
			throws Exception {
		final Player caster = harness.player(name, x, y);
		caster.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		caster.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MAGIC.id(), 99, 99, false);
		for (Map.Entry<Integer, Integer> rune : harness.server()
				.getEntityHandler().getSpellDef(spell).getRunesRequired()) {
			assertTrue(caster.getCarriedItems().getInventory().add(
				new Item(rune.getKey(), rune.getValue() + 10)),
				"spell fixture adds rune " + rune.getKey());
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

	private static void assertSpellReceipt(final SpellDef spell,
			final ProjectileResourceLedger ledger,
			final Preservation expectedPreservation, final String label) {
		assertTrackedAndSealed(ledger, label + " receipt");
		assertEquals(spell.getRunesRequired().size(), ledger.getItemCosts().size(),
			label + " rune-entry count");
		for (ItemCost cost : ledger.getItemCosts()) {
			assertEquals(expectedPreservation, cost.getPreservation(),
				label + " preservation for rune " + cost.getItemId());
			assertEquals(expectedPreservation == Preservation.NONE
				? cost.getRequestedAmount() : 0, cost.getRemovedAmount(),
				label + " removed amount for rune " + cost.getItemId());
		}
		assertEquals(1, ledger.getExperienceAwards().size(),
			label + " XP decision");
		assertEquals(spell.getExp(), ledger.getExperienceAwards().get(0)
			.getBaseAmount(), label + " base spell XP");
	}

	private static int[] runeCounts(final Player caster, final SpellDef spell) {
		final int[] counts = new int[spell.getRunesRequired().size()];
		int index = 0;
		for (Map.Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			counts[index++] = caster.getCarriedItems().getInventory()
				.countId(rune.getKey());
		}
		return counts;
	}

	private static void assertRuneCounts(final Player caster,
			final SpellDef spell, final int[] expected, final String label) {
		int index = 0;
		for (Map.Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			assertEquals(expected[index++], caster.getCarriedItems().getInventory()
				.countId(rune.getKey()), label + " rune " + rune.getKey());
		}
	}

	private static void assertRuneDeltaMatchesReceipt(final Player caster,
			final SpellDef spell, final int[] before,
			final ProjectileResourceLedger ledger, final String label) {
		int index = 0;
		for (Map.Entry<Integer, Integer> rune : spell.getRunesRequired()) {
			ItemCost matching = null;
			for (ItemCost cost : ledger.getItemCosts()) {
				if (cost.getItemId() == rune.getKey()) {
					matching = cost;
				}
			}
			assertNotNull(matching, label + " receipt for rune " + rune.getKey());
			assertEquals(before[index++] - matching.getRemovedAmount(),
				caster.getCarriedItems().getInventory().countId(rune.getKey()),
				label + " rune " + rune.getKey());
		}
	}

	private static ProjectileEvent findProjectile(
			final CurrentCombatHarness harness, final Player source,
			final ProjectileLaunchSpecification.Producer producer,
			final int ordinal) {
		final List<ProjectileEvent> events =
			findProjectiles(harness, source, producer);
		return events.size() <= ordinal ? null : events.get(ordinal);
	}

	private static List<ProjectileEvent> findProjectiles(
			final CurrentCombatHarness harness, final Player source,
			final ProjectileLaunchSpecification.Producer producer) {
		final List<ProjectileEvent> matches = new ArrayList<ProjectileEvent>();
		for (GameTickEvent candidate
				: harness.server().getGameEventHandler().getEvents()) {
			if (!(candidate instanceof ProjectileEvent)) {
				continue;
			}
			final ProjectileEvent projectile = (ProjectileEvent) candidate;
			if (projectile.getLaunchSnapshot().getSourceSnapshot()
					.matchesIdentityAndSession(source)
					&& projectile.getLaunchSnapshot().getSpecification()
						.getProducer() == producer) {
				matches.add(projectile);
			}
		}
		return matches;
	}

	private static void stopOwnedEvents(final CurrentCombatHarness harness,
			final Player... owners) {
		for (GameTickEvent event
				: harness.server().getGameEventHandler().getEvents()) {
			for (Player owner : owners) {
				if (event.belongsTo(owner)
						|| (event instanceof ProjectileEvent
							&& ((ProjectileEvent) event).getLaunchSnapshot()
								.getSourceSnapshot()
								.matchesIdentityAndSession(owner))) {
					event.stop();
				}
			}
		}
		harness.server().getGameEventHandler().cleanupEvents();
	}

	private static void assertThrows(
			final Class<? extends Throwable> expected,
			final ThrowingRunnable operation, final String message) {
		try {
			operation.run();
		} catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message + ": expected "
				+ expected.getName() + " but received " + actual, actual);
		}
		throw new AssertionError(message + ": expected " + expected.getName());
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

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected=" + expected
				+ " actual=" + actual);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
