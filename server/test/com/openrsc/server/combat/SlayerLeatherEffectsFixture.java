package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.monsterslayer.SlayerLeatherEffects;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;

/** Real equipment and settlement checks for the new full-set effects. */
public final class SlayerLeatherEffectsFixture {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			Player terror = h.player("ferocious", 440, 440);
			set(h, terror, MyWorldItemId.TERROR_DOG_COIF);
			Npc victim = h.npc(3, 441, 440);
			victim.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
			check(terror.applyBearMaulDamage(10) == 7, "Ferocious rounds down each hit");
			int[] hit = {-1};
			check(BearMaulSecondHit.tryApply(terror, victim, 7, damage -> hit[0] = damage)
				&& hit[0] == 7, "Ferocious second hit reuses settled first hit");
			check(!BearMaulSecondHit.tryApply(terror, victim, 0, damage -> hit[0] = damage), "zero has no second hit");
			Player hump = h.player("hump", 442, 440);
			set(h, hump, MyWorldItemId.UGTHANKI_COIF);
			check(SlayerLeatherEffects.storageHump(hump, 6) == 7, "food nearest downward");
			check(SlayerLeatherEffects.storageHump(hump, 8) == 10, "food nearest upward");
			Player naga = h.player("cold blooded", 444, 440);
			set(h, naga, MyWorldItemId.NAGA_COIF);
			check(naga.applyRobeDamageMitigation(10, NpcMagicElement.FIRE) == 8, "fire resistance path");
			check(naga.applyRobeDamageMitigation(10, NpcMagicElement.ICE) == 8, "ice resistance path");
			check(naga.applyRobeDamageMitigation(10, NpcMagicElement.WATER) == 10, "water is not ice");
			check(naga.applyRobeDamageMitigation(10) == 10, "untyped damage unaffected");
			check(naga.applyRobeDamageMitigation(1, NpcMagicElement.ICE) == 1, "resistance does not round tiny hits into immunity");
			for (int npcId : new int[]{135, 158, 254}) {
				Npc ice = h.npc(npcId, 444, 441);
				check(com.openrsc.server.model.entity.npc.NpcAttackStyleProfile.PURE_MAGIC.getMagicElement(ice)
					== NpcMagicElement.ICE, "existing ice NPC has explicit ice tag");
			}
			java.lang.reflect.Method element = Class.forName("com.openrsc.server.net.rsc.handlers.SpellClassification")
				.getDeclaredMethod("getResistanceElement", com.openrsc.server.constants.Spells.class);
			element.setAccessible(true);
			check(element.invoke(null, com.openrsc.server.constants.Spells.ICE_CRYSTAL)
				== NpcMagicElement.ICE, "ice spell producer tag");
			check(element.invoke(null, com.openrsc.server.constants.Spells.FIRE_STRIKE)
				== NpcMagicElement.FIRE, "fire spell producer tag");
			Player blood = h.player("absorption", 446, 440);
			set(h, blood, MyWorldItemId.BLOODVELD_COIF);
			blood.getSkills().setTemporaryLevelAndMaxStat(3, 50, 100, false);
			for (DamageRequest.SourceCategory category : new DamageRequest.SourceCategory[] {
				DamageRequest.SourceCategory.ACTOR, DamageRequest.SourceCategory.DOT,
				DamageRequest.SourceCategory.OWNED_EFFECT }) {
				int before = blood.getLevel(3);
				damage(blood, victim, category, 1);
				check(blood.getLevel(3) == before + 1, "one heal for " + category);
				damage(blood, victim, category, 0);
				check(blood.getLevel(3) == before + 1, "zero does not heal " + category);
			}
			Npc summon = h.npc(3, 447, 440);
			// Use production summon identity/ownership keys, not an arbitrary direct-player substitute.
			java.lang.reflect.Field summonKey = com.openrsc.server.content.Summoning.class.getDeclaredField("SUMMON_OWNER_KEY");
			summonKey.setAccessible(true);
			summon.setAttribute((String)summonKey.get(null), blood.getUsernameHash());
			check(com.openrsc.server.content.Summoning.isSummon(summon), "summon fixture identity");
			damage(summon, victim, DamageRequest.SourceCategory.ACTOR, 2);
			check(blood.getLevel(3) == 54, "summon owner healed once");
			victim.getSkills().setLevel(3, 1);
			damage(blood, victim, DamageRequest.SourceCategory.OWNED_EFFECT, 20);
			check(blood.getLevel(3) == 55, "overkill heals one, not requested damage");
			damage(blood, victim, DamageRequest.SourceCategory.OWNED_EFFECT, 20);
			check(blood.getLevel(3) == 55, "already dead target gives no heal");
			victim.getSkills().setLevel(3, 100);
			blood.getSkills().setLevel(3, 100);
			damage(blood, victim, DamageRequest.SourceCategory.DOT, 1);
			check(blood.getLevel(3) == 100, "absorption respects max health");
			sticky(h);
			stickyScheduler(h);
			charged(h);
			cleanse(h);
			food(h, hump);
			partialSets(h);
			System.out.println("PASS all six leather effects, full-set gates, sticky timing, charge radius/reset/PvP, elemental tags, food rounding, cleanse stacking and owned damage healing");
		}
	}
	private static void sticky(CurrentCombatHarness h) throws Exception {
		Player frog = h.player("sticky armor", 460, 460);
		set(h, frog, MyWorldItemId.GIANT_FROG_COIF);
		frog.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
		Npc enemy = h.npc(3, 461, 460);
		h.random().reset(1); h.random().scriptInts(0);
		damage(enemy, frog, DamageRequest.SourceCategory.ACTOR, 0);
		check(!SlayerLeatherEffects.delayAttack(enemy), "zero does not apply sticky");
		damage(enemy, frog, DamageRequest.SourceCategory.DOT, 1);
		check(!SlayerLeatherEffects.delayAttack(enemy), "DOT does not apply sticky");
		damage(enemy, frog, DamageRequest.SourceCategory.ACTOR, 1);
		// A pending effect must survive until the actual next attack, not expire on a normal cooldown.
		h.advanceOneCombatTick(); h.advanceOneCombatTick();
		check(SlayerLeatherEffects.delayAttack(enemy), "next attack delayed even after cooldown");
		damage(enemy, frog, DamageRequest.SourceCategory.ACTOR, 1);
		check(SlayerLeatherEffects.delayAttack(enemy), "same tick still blocked, no stack");
		h.advanceOneCombatTick();
		check(!SlayerLeatherEffects.delayAttack(enemy), "attack resumes exactly next tick");
		check(!com.openrsc.server.content.monsterslayer.SlayerCombatEffects.attacksBlocked(enemy), "no full action lock");
		h.random().scriptInts(0);
		damage(enemy, frog, DamageRequest.SourceCategory.ACTOR, 1);
		enemy.advanceCombatLifecycle();
		check(!SlayerLeatherEffects.delayAttack(enemy), "pending slow not inherited after death");
	}
	private static void charged(CurrentCombatHarness h) throws Exception {
		Player charged = h.player("charged armor", 480, 480);
		set(h, charged, MyWorldItemId.DARK_BEAST_COIF);
		charged.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
		Npc enemy = h.npc(3, 481, 480), nearby = h.npc(3, 482, 482), far = h.npc(3, 483, 480);
		for (Npc npc : new Npc[]{enemy, nearby, far}) npc.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
		Player friendly = h.player("charge bystander", 479, 480);
		friendly.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
		for (int i = 0; i < 9; i++) {
			h.random().reset(1); h.random().scriptInts(2);
			damage(enemy, charged, DamageRequest.SourceCategory.ACTOR, 1);
		}
		check(SlayerLeatherEffects.charge(charged) == 27, "each hit adds d3, not its damage");
		damage(enemy, charged, DamageRequest.SourceCategory.DOT, 1);
		damage(enemy, charged, DamageRequest.SourceCategory.OWNED_EFFECT, 1);
		damage(enemy, charged, DamageRequest.SourceCategory.ACTOR, 0);
		check(SlayerLeatherEffects.charge(charged) == 27, "no DOT/proc/zero charge or loops");
		h.random().reset(1); h.random().scriptInts(2);
		damage(enemy, charged, DamageRequest.SourceCategory.ACTOR, 1);
		check(SlayerLeatherEffects.charge(charged) == 0, "threshold discharges/reset");
		check(enemy.getUpdateFlags().getCombatEffect().get() != null, "attacker gets thunder");
		check(nearby.getUpdateFlags().getCombatEffect().get() != null, "radius two diagonal gets thunder");
		check(far.getUpdateFlags().getCombatEffect().get() == null, "radius three excluded");
		check(friendly.getLevel(3) == 100, "PvP disabled excludes players");
		h.random().reset(1); h.random().scriptInts(1);
		damage(enemy, charged, DamageRequest.SourceCategory.ACTOR, 1);
		check(SlayerLeatherEffects.charge(charged) == 2, "new charge after discharge");
		charged.getCarriedItems().getEquipment().remove(charged.getCarriedItems().getEquipment().get(5), 1, false);
		set(h, charged, MyWorldItemId.DARK_BEAST_COIF);
		check(SlayerLeatherEffects.charge(charged) == 0, "unequip clears even after re-equip");
		h.random().scriptInts(1); damage(enemy, charged, DamageRequest.SourceCategory.ACTOR, 1);
		charged.advanceCombatLifecycle();
		check(SlayerLeatherEffects.charge(charged) == 0, "no charge in new life");
	}
	private static void stickyScheduler(CurrentCombatHarness h) throws Exception {
		h.openCombatProjectileRectangle(450, 453, 450, 452);
		Player frog = h.player("sticky scheduler", 451, 450);
		frog.getSkills().setTemporaryLevelAndMaxStat(3, 200, 200, false);
		set(h, frog, MyWorldItemId.GIANT_FROG_COIF);
		Npc naga = h.npc(866, 450, 450);
		com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent event =
			new com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent(h.world(), naga, frog);
		naga.setPvmMeleeEvent(event);
		h.random().reset(1); h.random().scriptInts(0);
		damage(naga, frog, DamageRequest.SourceCategory.ACTOR, 1);
		com.openrsc.server.content.monsterslayer.NagaCombat.recordAttack(naga, 2);
		int swings = naga.getHitsMade();
		event.run();
		h.advanceOneCombatTick(); h.advanceOneCombatTick();
		event.run();
		check(naga.getHitsMade() == swings, "sticky remains pending throughout normal cooldown");
		h.advanceOneCombatTick(); event.run();
		check(naga.getHitsMade() == swings + 1, "real melee scheduler attacks one tick later: swings="
			+ naga.getHitsMade() + " baseline=" + swings + " ready="
			+ com.openrsc.server.content.monsterslayer.NagaCombat.attackReady(naga)
			+ " current=" + naga.isCurrentPvmMeleeEvent(event) + " path=" + naga.finishedPath()
			+ " tick=" + h.server().getCurrentTick());
		h.logout(frog); naga.remove();
	}
	private static void cleanse(CurrentCombatHarness h) throws Exception {
		int[] bases = {1860,1870,1890}, values = {2,3,5};
		for (int i = 0; i < bases.length; i++) {
			Player player = h.player("cleanse " + i, 500+i, 500);
			player.getSkills().setTemporaryLevelAndMaxStat(3, 100, 100, false);
			set(h, player, bases[i]);
			check(SlayerLeatherEffects.carapaceCleanse(player) == values[i], "tier cleanse");
			check(player.getCarriedItems().getEquipment().getMeleePoisonArmorProcChance() == 0
				&& player.getCarriedItems().getEquipment().getRangedPoisonArmorProcChance() == 0
				&& player.getCarriedItems().getEquipment().getMagicPoisonArmorProcChance() == 0, "old offensive procs removed");
			com.openrsc.server.event.rsc.impl.PoisonEvent poison = new com.openrsc.server.event.rsc.impl.PoisonEvent(
				h.world(), player, 30, (java.util.UUID)null);
			poison.run();
			check(poison.getPoisonPower() == 30 - 3 - values[i], "actual poison decay uses armor");
			check(player.getLevel(3) == 97, "cleanse changes future poison, not this damage tick");
			for (int id = 0; id < h.server().getEntityHandler().items.size(); id++) {
				int bonus = com.openrsc.server.content.EnchantingItemEffects.getNatureCleansingPoisonDecayBonus(id);
				if (bonus <= 0) continue;
				h.equip(player, id, 1);
				poison.setPoisonPower(30); poison.run();
				check(poison.getPoisonPower() == 30 - 3 - values[i] - bonus, "necklace and armor cleansing add");
				break;
			}
			if (bases[i] == 1890) {
				java.lang.reflect.Method penalty = player.getCarriedItems().getEquipment().getClass().getDeclaredMethod(
					"getArmorPowerPenalty", com.openrsc.server.model.entity.player.PrayerCatalog.CombatStyle.class);
				penalty.setAccessible(true);
				check((Integer)penalty.invoke(player.getCarriedItems().getEquipment(),
					com.openrsc.server.model.entity.player.PrayerCatalog.CombatStyle.MAGIC) > 0, "magic-spider now pays normal magic penalty");
			}
			player.getCarriedItems().getEquipment().remove(player.getCarriedItems().getEquipment().get(5), 1, false);
			check(SlayerLeatherEffects.carapaceCleanse(player) == 0, "partial set no cleanse");
		}
	}
	private static void food(CurrentCombatHarness h, Player player) throws Exception {
		player.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		player.getSkills().setTemporaryLevelAndMaxStat(3, 50, 100, false);
		player.getSkills().setTemporaryLevelAndMaxStat(Skill.COOKING.id(), 1, 1, false);
		com.openrsc.server.model.container.Item food = new com.openrsc.server.model.container.Item(ItemId.BREAD.id());
		int expected = SlayerLeatherEffects.foodHealing(player, food.eatingHeals(h.world()));
		player.getCarriedItems().getInventory().add(food);
		Class<?> eating = Class.forName("com.openrsc.server.plugins.authentic.itemactions.Eating");
		eating.getMethod("onOpInv", Player.class, Integer.class, com.openrsc.server.model.container.Item.class, String.class)
			.invoke(eating.getConstructor().newInstance(), player, 0, food, "eat");
		check(player.getLevel(3) == 50 + expected, "actual eating uses Storage Hump");
		for (int id = 0; id < h.server().getEntityHandler().items.size(); id++) {
			double bonus = com.openrsc.server.content.EnchantingItemEffects.getNatureFoodHealingBonus(id);
			if (bonus <= 0) continue;
			h.equip(player, id, 1);
			check(SlayerLeatherEffects.foodHealing(player, 8) == (int)Math.round(8 * (1.2 + bonus)), "food bonuses add then round once");
			break;
		}
	}
	private static void set(CurrentCombatHarness h, Player player, int first) throws Exception {
		player.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0, 0, 0, 0, 1, 2));
		for (int id = first; id < first + 5; id++) h.equip(player, id, 1);
	}
	private static void partialSets(CurrentCombatHarness h) throws Exception {
		int[] sets = {MyWorldItemId.GIANT_FROG_COIF, MyWorldItemId.NAGA_COIF,
			MyWorldItemId.TERROR_DOG_COIF, MyWorldItemId.BLOODVELD_COIF,
			MyWorldItemId.DARK_BEAST_COIF, MyWorldItemId.UGTHANKI_COIF};
		for (int i = 0; i < sets.length; i++) {
			Player player = h.player("partial " + i, 530+i, 530);
			set(h, player, sets[i]);
			check(SlayerLeatherEffects.hasSet(player, sets[i]), "five pieces activate set");
			player.getCarriedItems().getEquipment().remove(player.getCarriedItems().getEquipment().get(5), 1, false);
			check(!SlayerLeatherEffects.hasSet(player, sets[i]), "four pieces never activate new set");
		}
	}
	private static void damage(Mob source, Mob target, DamageRequest.SourceCategory category, int amount) {
		target.getWorld().getServer().getResolvedDamageTransaction().apply(
			DamageRequest.resolvedLegacy(source, target, category, "leather-fixture", amount).build());
	}
	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}
}
