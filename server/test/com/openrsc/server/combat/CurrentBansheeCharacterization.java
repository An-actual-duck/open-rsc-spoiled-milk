package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.monsterslayer.BansheeCombat;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.Point;
import com.openrsc.server.net.Packet;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.util.ArrayList;
import java.util.List;

public final class CurrentBansheeCharacterization {
	public static void main(String[] args) throws Exception {
		try (CurrentCombatHarness h = new CurrentCombatHarness()) {
			damage(h);
			position(h);
			earplugs(h);
		}
	}
	private static void damage(CurrentCombatHarness h) throws Exception {
		Npc banshee = h.npc(865, 440, 440);
		check(banshee.getMeleeOffense() == 55 && banshee.getMagicOffense() == 55, "explicit dual offense");
		check(banshee.getMeleeDefense() == 40 && banshee.getRangedDefense() == 40 && banshee.getMagicDefense() == 40, "explicit defenses");
		for (int mode = 0; mode < 3; mode++) {
			Player p = h.player("wail" + mode, 441, 440);
			h.recordOutgoingPackets(p);
			Object event = mode == 0 ? new PvmMeleeEvent(h.world(), banshee, p)
				: mode == 1 ? new CombatEvent(h.world(), banshee, p)
				: new ProjectileEvent(h.world(), banshee, p, 999, 1, false);
			int max = BansheeCombat.maxWailDamage(p);
			check(max == 20, "fifty percent of maximum HP");
			h.random().reset(1);
			h.random().scriptInts(max);
			hit(event, banshee, p, 999);
			check(p.getSkills().getLevel(Skill.HITS.id()) == 20, "wail replaces rather than adds, full health survives path " + mode);
			check(messages(p) == 1, "exact wail text per damaging unprotected hit");
			p.getSkills().setLevel(Skill.HITS.id(), 40);
			h.random().reset(1);
			h.random().scriptInts(0);
			if (mode == 2) event = new ProjectileEvent(h.world(), banshee, p, 999, 1, false);
			hit(event, banshee, p, 999);
			check(p.getSkills().getLevel(Skill.HITS.id()) == 40 && messages(p) == 1, "zero roll no damage/no wail text");
			// Create the projectile before protection, then insert before impact.
			if (mode == 2) event = new ProjectileEvent(h.world(), banshee, p, 5, 1, false);
			BansheeCombat.applyEarplugs(p);
			hit(event, banshee, p, 5);
			check(p.getSkills().getLevel(Skill.HITS.id()) == 35 && messages(p) == 1, "protected normal damage path " + mode);
			p.getSkills().setLevel(Skill.HITS.id(), 40);
			if (mode == 2) event = new ProjectileEvent(h.world(), banshee, p, 0, 1, false);
			h.clock().advanceMillis(600_000);
			h.random().reset(1);
			h.random().scriptInts(20);
			hit(event, banshee, p, 0);
			check(p.getSkills().getLevel(Skill.HITS.id()) == 20 && messages(p) == 2, "expired protection restores wail even if normal roll missed");
			p.getSkills().setLevel(Skill.HITS.id(), 40);
			if (mode == 2) {
				banshee.applyStartleDebuff(p);
				event = new ProjectileEvent(h.world(), banshee, p, 999, 1, false);
				hit(event, banshee, p, 999);
			} else {
				CurrentCombatHarness.invokePrivate(event, "inflictDamage",
					new Class<?>[]{Mob.class, Mob.class, int.class, boolean.class}, banshee, p, 0, true);
			}
			check(p.getSkills().getLevel(Skill.HITS.id()) == 40 && messages(p) == 2, "suppressed attack cannot wail");
		}
		Player tiny = h.player("tiny wail", 441, 441);
		for (int hp : new int[]{1, 2, 10, 99, 100}) {
			tiny.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), hp, hp, false);
			check(BansheeCombat.maxWailDamage(tiny) == hp / 2 && BansheeCombat.maxWailDamage(tiny) < hp, "safe integer cap at HP " + hp);
		}
		System.out.println("PASS Banshee damage replacement, full-HP safety, normal protected hits, in-flight protection and exact text");
	}
	private static void hit(Object event, Npc banshee, Player target, int damage) throws Exception {
		if (event instanceof ProjectileEvent) CurrentCombatHarness.invokePrivate(event, "projectileDamage", new Class<?>[0]);
		else CurrentCombatHarness.invokePrivate(event, "inflictDamage", new Class<?>[]{Mob.class, Mob.class, int.class}, banshee, target, damage);
	}
	private static void position(CurrentCombatHarness h) throws Exception {
		Npc n = h.npc(865, 450, 450);
		Player p = h.player("banshee range", 453, 450);
		h.openRectangle(450, 455, 450, 451);
		BansheeCombat.applyEarplugs(p);
		NpcCombatProfile profile = NpcCombatProfile.resolve(n);
		for (int i = 0; i < 50; i++) {
			check(!profile.prefersProjectileAtDistance(1) && profile.prefersProjectileAtDistance(2)
				&& profile.prefersProjectileAtDistance(5) && !profile.prefersProjectileAtDistance(6), "deterministic distance style");
		}
		check(profile.selectMagicAttack().getProjectileVisual() == Projectile.HOLY_MAGIC, "holy projectile");
		PvmMeleeEvent melee = new PvmMeleeEvent(h.world(), n, p);
		n.setPvmMeleeEvent(melee);
		melee.run();
		check(n.finishedPath() && !BansheeCombat.attackReady(n), "melee loop casts without closing gap");
		long next = n.getAttribute("slayer_banshee_next_attack_tick", -1L);
		check(n.getBehavior().tryBansheeProjectileAttack(p), "behavior holds range on shared cooldown");
		check(n.<Long>getAttribute("slayer_banshee_next_attack_tick", -1L) == next, "no duplicate cast");
		p.setInitialLocation(Point.location(451, 450));
		int hits = n.getHitsMade();
		melee.run();
		check(n.getHitsMade() == hits, "moving adjacent cannot bypass cast cooldown");
		for (int i = 0; i < 3; i++) h.advanceOneCombatTick();
		check(BansheeCombat.attackReady(n), "three-tick cast interval");
		check(!n.getBehavior().tryBansheeProjectileAttack(p), "no magic adjacent");
		melee.run();
		check(n.getHitsMade() == hits + 1 && !BansheeCombat.attackReady(n), "adjacent melee swing");
		System.out.println("PASS Banshee range holding, holy magic, deterministic melee switching and shared cooldown");
	}
	private static void earplugs(CurrentCombatHarness h) throws Exception {
		Player p = h.player("wax uses", 460, 460);
		p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		OpInvTrigger plugin = (OpInvTrigger) Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.WaxEarplugs").newInstance();
		plugin.onOpInv(p, 0, new Item(3324), "Insert");
		check(!BansheeCombat.protectedByEarplugs(p), "absent item denied");
		p.getCarriedItems().getInventory().add(new Item(3324));
		while (p.getCarriedItems().getInventory().size() < p.getCarriedItems().getInventory().getCapacity())
			check(p.getCarriedItems().getInventory().add(new Item(3318)), "fill inventory");
		long id = p.getCarriedItems().getInventory().get(0).getItemId();
		for (int next : new int[]{3325, 3326, ItemId.EMPTY_VIAL.id()}) {
			Item current = p.getCarriedItems().getInventory().get(0);
			check("Desolve after 10 minutes".equals(current.getDef(h.world()).getDescription()), "exact examine");
			java.lang.reflect.Method allowed = com.openrsc.server.net.rsc.handlers.ItemActionHandler.class
				.getDeclaredMethod("isCombatConsumableAction", Item.class, String.class, Player.class);
			allowed.setAccessible(true);
			check((Boolean) allowed.invoke(new com.openrsc.server.net.rsc.handlers.ItemActionHandler(), current, "Insert", p), "insert during combat");
			plugin.onOpInv(p, 0, current, "Insert");
			check(p.getCarriedItems().getInventory().get(0).getCatalogId() == next
				&& p.getCarriedItems().getInventory().get(0).getItemId() == id, "three uses in same slot");
			h.clock().advanceMillis(1_000);
			plugin.onOpInv(p, 0, current, "Insert");
			List<ActiveStatusEntry> rows = new ArrayList<>();
			BansheeCombat.appendStatuses(p, rows);
			check(rows.size() == 1 && rows.get(0).getRemainingSeconds() == 599, "stale use denied, ten-minute HUD timer");
		}
		h.clock().advanceMillis(599_000);
		check(!BansheeCombat.protectedByEarplugs(p), "exact ten-minute expiry");
		System.out.println("PASS Wax earplugs uses, full inventory, expiry, HUD duration and in-combat action");
	}
	@SuppressWarnings("unchecked") private static int messages(Player p) throws Exception {
		int count = 0;
		for (Packet packet : (List<Packet>) CurrentCombatHarness.readPrivateField(p, "outgoingPackets"))
			if (packet.getBuffer().toString(java.nio.charset.StandardCharsets.ISO_8859_1).contains(BansheeCombat.WAIL_MESSAGE)) count++;
		return count;
	}
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
