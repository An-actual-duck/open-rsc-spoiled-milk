package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.monsterslayer.NagaCombat;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.model.entity.update.Projectile;
import java.util.ArrayList;
import java.util.List;

final class CurrentNagaCharacterization {
	public static void main(String[] args) throws Exception {
		List<DamageResult> outcomes = new ArrayList<>();
		try (CurrentCombatHarness h = new CurrentCombatHarness(outcomes::add)) {
			position(h);
			dualHits(h, outcomes);
			System.out.println("PASS Naga modern stats, deterministic style, chase, shared cooldown, independent capped offhand and red/yellow hits");
		}
	}

	private static void position(CurrentCombatHarness h) throws Exception {
		h.openCombatProjectileRectangle(440, 448, 440, 442);
		Npc n = h.npc(866, 440, 440);
		Player p = h.player("naga range", 444, 440);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 200, 200, false);
		check(n.getMeleeOffense() == 65 && n.getRangedOffense() == 55, "modern offenses");
		check(n.getMeleeDefense() == 20 && n.getRangedDefense() == 65 && n.getMagicDefense() == 95, "defense priorities");
		NpcCombatProfile profile = NpcCombatProfile.resolve(n);
		for (int i = 0; i < 30; i++) check(!profile.prefersProjectileAtDistance(1)
			&& profile.prefersProjectileAtDistance(2) && profile.prefersProjectileAtDistance(5)
			&& !profile.prefersProjectileAtDistance(6), "no random adjacent throws");
		check(profile.getRangedProjectileVisual() == Projectile.NAGA_SCIMITAR, "dedicated scimitar projectile");
		PvmMeleeEvent event = new PvmMeleeEvent(h.world(), n, p);
		n.setPvmMeleeEvent(event);
		event.run();
		check(n.finishedPath() && !NagaCombat.attackReady(n), "throw pauses movement and starts shared cooldown");
		long next = n.getAttribute("slayer_naga_next_attack_tick", -1L);
		check(!n.getBehavior().tryNagaProjectileAttack(p), "behavior does not throw again on cooldown");
		event.run();
		check(!n.finishedPath(), "advances between throws");
		n.getWalkingQueue().processNextMovement();
		check(n.getX() == 441, "actual step toward player");
		check(n.<Long>getAttribute("slayer_naga_next_attack_tick", -1L) == next, "chasing does not restart cooldown");
		p.setLocation(Point.location(442, 440));
		int swings = n.getHitsMade();
		event.run();
		check(n.finishedPath() && n.getHitsMade() == swings, "adjacency cannot bypass throw cooldown");
		for (int i = 0; i < 3; i++) h.advanceOneCombatTick();
		check(!n.getBehavior().tryNagaProjectileAttack(p), "never throws adjacent");
		event.run();
		check(n.getHitsMade() == swings + 1, "one swing despite two damage rolls");
		h.logout(p);
		n.remove();
	}

	private static void dualHits(CurrentCombatHarness h, List<DamageResult> outcomes) throws Exception {
		h.openCombatProjectileRectangle(460, 463, 460, 462);
		Npc n = h.npc(866, 460, 460);
		Player p = h.player("naga blades", 461, 460);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 200, 200, false);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.DEFENSE.id(), 1, 1, false);
		// Force separately seeded rolls: a main-hand miss does not force an offhand miss.
		h.random().reset(4);
		h.random().scriptDoubles(0.0, 0.999999).scriptInts(1, 1);
		int main = CombatFormula.doMeleeDamage(n, p);
		int off = CombatFormula.doOffhandMeleeDamage(n, p);
		check(main == 0 && off > 0 && off <= 5, "independent offhand succeeds after main miss");
		for (int i = 0; i < 200; i++) check(CombatFormula.doOffhandMeleeDamage(n, p) <= 5, "half cap before normal defense roll");
		PvmMeleeEvent event = new PvmMeleeEvent(h.world(), n, p);
		n.setPvmMeleeEvent(event);
		outcomes.clear();
		event.run();
		List<DamageResult> strikes = new ArrayList<>();
		for (DamageResult result : outcomes) if (result.getRequest().getSource() == n && result.getRequest().getTarget() == p) strikes.add(result);
		check(strikes.size() == 2, "two resolved strikes per swing");
		check(strikes.get(0).getRequest().getHitSplatType() == HitSplat.TYPE_STANDARD, "main red");
		check(strikes.get(1).getRequest().getHitSplatType() == HitSplat.TYPE_ARMOR_PROC, "offhand yellow");
		check(strikes.get(0).getRequest().getTick() == strikes.get(1).getRequest().getTick(), "simultaneous hits in one game tick");
		check("naga-melee-offhand".equals(strikes.get(1).getRequest().getEffectKey()), "distinct offhand transaction");
		int count = outcomes.size();
		event.run();
		check(outcomes.size() == count, "cannot double swing same tick");
		for (int i = 0; i < 2; i++) h.advanceOneCombatTick();
		n.applyStartleDebuff(p);
		outcomes.clear();
		event.run();
		check(outcomes.stream().noneMatch(r -> "naga-melee-offhand".equals(r.getRequest().getEffectKey())), "suppression blocks offhand too");
		for (int i = 0; i < 2; i++) h.advanceOneCombatTick();
		p.getSkills().setLevel(Skill.HITS.id(), 1);
		h.random().reset(5);
		h.random().scriptDoubles(0.999999).scriptInts(1);
		outcomes.clear();
		event.run();
		check(outcomes.stream().anyMatch(r -> r.getRequest().getTarget() == p && r.isTargetTerminal()), "main hand can kill normally");
		check(outcomes.stream().noneMatch(r -> "naga-melee-offhand".equals(r.getRequest().getEffectKey())), "lethal main hand does not hit respawned player");
		p.getSkills().setLevel(Skill.HITS.id(), 0);
		check(!NagaCombat.canOffhand(n, p, false), "no offhand against dead target");
		p.getSkills().setLevel(Skill.HITS.id(), 200);
		n.getSkills().setLevel(Skill.HITS.id(), 0);
		check(!NagaCombat.canOffhand(n, p, false), "no offhand after attacker dies to reflection");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
