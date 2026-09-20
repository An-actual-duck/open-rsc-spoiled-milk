package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.monsterslayer.BloodveldCombat;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.CollisionFlag;

final class CurrentBloodveldCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			h.openCombatProjectileRectangle(440, 449, 440, 443);
			Npc n = h.npc(868, 440, 440);
			Player p = h.player("bloodveld target", 445, 440);
			p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 300, 300, false);
			check(n.getMeleeOffense() == 80 && n.getMeleeDefense() == 30 && n.getMagicDefense() == 30
				&& n.getRangedDefense() == 30 && n.getLevel(Skill.HITS.id()) == 150, "modern stats");
			check(BloodveldCombat.pullDestination(n,p) != null, "five-tile pull");
			com.openrsc.server.model.world.region.TileValue blocker = h.world().getRegionManager().getMutableTile(
				h.world().getRegionManager().fromRuntimeCompatibilityPoint(Point.location(443,440),p.getWorldLocation(),false));
			byte oldMask=blocker.traversalMask;
			blocker.traversalMask = CollisionFlag.FULL_BLOCK;
			check(BloodveldCombat.pullDestination(n,p) == null, "blocked path never pulls through walls");
			blocker.traversalMask=oldMask;
			h.openCombatProjectileRectangle(440,449,440,443);
			PvmMeleeEvent event = new PvmMeleeEvent(h.world(), n, p);
			n.setPvmMeleeEvent(event);
			event.run();
			check(n.finishedPath() && !BloodveldCombat.attackReady(n), "hold position and shared cooldown");
			check(p.getX() == 445, "windup before displacement");
			h.advanceOneCombatTick();
			check(p.getX() == 441 && p.getY() == 440 && p.getLevel(Skill.HITS.id()) == 300, "pull adjacent without damage");
			int swings = n.getHitsMade(); event.run();
			check(n.getHitsMade() == swings, "no immediate melee after pull");
			check(!BloodveldCombat.tryPull(n,p), "adjacent uses melee only");
			for (int i=0;i<3;i++) h.advanceOneCombatTick();
			event.run(); check(n.getHitsMade() == swings+1, "normal melee resumes");
			p.setLocation(Point.location(446,440));
			check(!BloodveldCombat.tryPull(n,p), "outside range");
			event.run(); check(!n.finishedPath(), "approaches outside tongue range"); n.resetPath();
			p.setLocation(Point.location(445,440));
			for (int i=0;i<3;i++) h.advanceOneCombatTick();
			check(BloodveldCombat.tryPull(n,p), "second pull");
			p.setLocation(Point.location(445,441));
			h.advanceOneCombatTick(); check(p.getX()==445 && p.getY()==441, "movement cancels stale pull");
			for (int i=0;i<3;i++) h.advanceOneCombatTick();
			check(BloodveldCombat.tryPull(n,p), "third pull"); p.advanceCombatLifecycle();
			h.advanceOneCombatTick(); check(p.getX()==445, "lifecycle cancels pull");
			p.setLocation(Point.location(445,1384));
			check(BloodveldCombat.pullDestination(n,p)==null, "other plane excluded");
			p.setLocation(Point.location(441,440));
			for(int mode=0;mode<2;mode++) {
				n.getSkills().setLevel(Skill.HITS.id(),100);
				Object hit = mode==0 ? new PvmMeleeEvent(h.world(),n,p) : new CombatEvent(h.world(),n,p);
				CurrentCombatHarness.invokePrivate(hit,"inflictDamage",new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class},n,p,11,false);
				check(n.getLevel(Skill.HITS.id())==105,"half actual damage rounded down, both event paths");
				CurrentCombatHarness.invokePrivate(hit,"inflictDamage",new Class<?>[]{Mob.class,Mob.class,int.class,boolean.class},n,p,0,false);
				check(n.getLevel(Skill.HITS.id())==105,"zero does not heal");
			}
			n.getSkills().setLevel(Skill.HITS.id(),149); BloodveldCombat.lifesteal(n,20);
			check(n.getLevel(Skill.HITS.id())==150,"healing capped");
			n.getSkills().setLevel(Skill.HITS.id(),0); BloodveldCombat.lifesteal(n,20);
			check(n.getLevel(Skill.HITS.id())==0,"no resurrection");
			System.out.println("PASS Bloodveld modern stats, range, hold/chase, delayed pull, melee switch, cooldown, cancellation and lifesteal");
		}
	}
	private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
