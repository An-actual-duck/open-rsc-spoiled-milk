package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.SpellDamages;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.monsterslayer.SlayerRewardCombat;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.combat.ProjectileResourceLedger;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.EntityType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.util.rsc.DataConversions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cast packets, rune payment, production power lookup, projectile launch and real impacts. */
public final class ThunderSpellCastingFixture {
	public static void main(String[] args) throws Exception {
		List<DamageResult> outcomes = new ArrayList<>();
		try (CurrentCombatHarness h = new CurrentCombatHarness(outcomes::add)) {
			Spells[] thunder = {Spells.THUNDER_BALL, Spells.THUNDER_SPLASH, Spells.THUNDER_STRIKE};
			for (int tier = 1; tier <= 3; tier++) {
				cast(h, outcomes, thunder[tier - 1], tier, false);
				cast(h, outcomes, thunder[tier - 1], tier, true);
			}
			for (Spells spell : new Spells[]{Spells.ICICLE_SHOT, Spells.ICE_BURST,
				Spells.ACID_DROP, Spells.ACID_FROG, Spells.BRANCH_SPORE, Spells.WOOD_DRILL}) {
				int tier = spell == Spells.ICICLE_SHOT || spell == Spells.ACID_DROP || spell == Spells.BRANCH_SPORE ? 1 : 2;
				cast(h, outcomes, spell, tier, false);
			}
			SpellDamages table = h.server().getConstants().getSpellDamages();
			for (Spells spell : Spells.values()) for (EntityType target : new EntityType[]{EntityType.NPC, EntityType.PLAYER}) {
				double lower = table.getSpellDamage(spell, target, SpellDamages.MagicType.F2PONLYMAGIC);
				if (lower >= 0) check(lower == table.getSpellDamage(spell, target, SpellDamages.MagicType.MODERNMAGIC),
					"modern book contains existing lower-tier power: " + spell);
			}
			boolean rejected = false;
			try { ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 1, 1)
				.thunderSpire(1, -1).build(); } catch (IllegalArgumentException expected) { rejected = true; }
			check(rejected, "invalid power guard remains active");
			System.out.println("PASS real thunder casting with/without staff: lookup, caps, runes, launch, impact, splash radii/scaling, PvP exclusion and duplicate settlement; sibling spells and table coverage");
		}
	}

	private static void cast(CurrentCombatHarness h, List<DamageResult> outcomes,
			Spells spell, int tier, boolean staff) throws Exception {
		h.openCombatProjectileRectangle(440, 449, 440, 445);
		Player caster = h.player("cast " + spell.ordinal() + " " + staff, 440, 440);
		caster.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		caster.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(), 99, 99, false);
		if (staff) h.equip(caster, MyWorldItemId.THUNDER_SPIRE_STAFF, 1);
		Npc primary = h.npc(NpcId.GREATER_DEMON.id(), 442, 440);
		Npc[] others = {h.npc(3, 443, 440), h.npc(3, 444, 440), h.npc(3, 445, 440), h.npc(3, 446, 440)};
		Player bystander = h.player("bystander " + spell.ordinal() + " " + staff, 443, 441);
		for (Mob mob : new Mob[]{primary, others[0], others[1], others[2], others[3], bystander})
			mob.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
		SpellDef definition = h.server().getEntityHandler().getSpellDef(spell);
		for (Map.Entry<Integer, Integer> rune : definition.getRunesRequired())
			check(caster.getCarriedItems().getInventory().add(new Item(rune.getKey(), rune.getValue() + 20)), "fixture runes");
		double power = new double[]{0, 4.8, 7.2, 9.6}[tier];
		double cap = staff && tier == 3 ? 1.0 : new double[]{0, .4, .6, .8}[tier];
		h.random().reset(49);
		int expectedPrimary = CombatFormula.calculateMagicDamage(caster, primary, power, cap);
		h.random().reset(49);
		DataConversions.getRandom().setSeed(0);
		SpellStruct request = new SpellStruct();
		request.setOpcode(OpcodeIn.CAST_ON_NPC); request.spell = spell; request.targetIndex = primary.getIndex();
		new SpellHandler().process(request, caster);
		WalkToAction approach = caster.getWalkToAction();
		check(approach != null && approach.shouldExecute(), spell + " reaches cast commitment");
		approach.execute();
		ProjectileEvent projectile = null;
		for (GameTickEvent event : h.server().getGameEventHandler().getEvents()) {
			if (event instanceof ProjectileEvent && ((ProjectileEvent)event).getLaunchSnapshot()
				.getSourceSnapshot().matchesIdentityAndSession(caster)) {
				check(projectile == null, "one projectile per cast"); projectile = (ProjectileEvent)event;
			}
		}
		check(projectile != null, spell + " schedules projectile");
		ProjectileLaunchSpecification spec = projectile.getLaunchSnapshot().getSpecification();
		check(spec.getThunderSpirePower() == power, spell + " uses production spell power");
		check(spec.getThunderSpireTier() == (staff ? tier : 0), spell + " staff-only splash metadata");
		check(spec.getProposedDamage() == expectedPrimary, spell + " correct capped primary roll");
		check(projectile.getProjectileResourceLedger().getState() == ProjectileResourceLedger.State.SEALED, "launch receipt sealed");
		check(projectile.getProjectileResourceLedger().getItemCosts().size() == definition.getRunesRequired().size(), "all rune costs recorded");
		assertRunes(caster, definition);
		// Settle the projectile created by the cast, including its real splash metadata.
		outcomes.clear(); h.random().reset(71);
		projectile.action();
		check(outcomes.stream().anyMatch(result -> result.getRequest().getTarget() == primary), "primary impact settled");
		int splashCount = 0;
		for (DamageResult result : outcomes) {
			if (!SecondaryEffectPolicy.THUNDER_SPIRE_SPLASH.getStableKey().equals(result.getRequest().getEffectKey())) continue;
			splashCount++;
			check(result.getRequest().getTarget() != primary && result.getRequest().getTarget() != bystander, "splash excludes primary/player");
		}
		check(splashCount == (staff ? tier : 0), spell + " splash count/radius, got " + splashCount);
		for (int i = 0; i < others.length; i++) {
			Npc candidate = others[i];
			boolean hit = outcomes.stream().anyMatch(result -> result.getRequest().getTarget() == candidate
				&& SecondaryEffectPolicy.THUNDER_SPIRE_SPLASH.getStableKey().equals(result.getRequest().getEffectKey()));
			check(hit == (staff && i < tier), spell + " boundary at radius " + (i + 1));
		}
		if (staff) check(SlayerRewardCombat.thunderSecondaryPower(tier, power)
			== (int)Math.ceil(power * new double[]{0, .15, .25, .40}[tier]), "approved splash power scaling");
		check(bystander.getLevel(Skill.HITS.id()) == 1000, "PvP disabled");
		int settled = outcomes.size(); projectile.action();
		check(outcomes.size() == settled, "duplicate impact cannot settle again");
		assertRunes(caster, definition);
		h.logout(caster); h.logout(bystander); primary.remove();
		for (Npc npc : others) npc.remove();
	}
	private static void assertRunes(Player player, SpellDef definition) {
		for (Map.Entry<Integer, Integer> rune : definition.getRunesRequired())
			check(player.getCarriedItems().getInventory().countId(rune.getKey()) == 20, "runes consumed exactly once");
	}
	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}
}
