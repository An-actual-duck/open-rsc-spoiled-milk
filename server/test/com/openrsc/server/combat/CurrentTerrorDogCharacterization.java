package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.monsterslayer.TerrorDogCombat;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.handlers.ItemActionHandler;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import java.util.ArrayList;
import java.util.List;

final class CurrentTerrorDogCharacterization {
	public static void main(String[] args) throws Exception {
		List<DamageResult> hits = new ArrayList<>();
		try (CurrentCombatHarness h = new CurrentCombatHarness(hits::add)) {
			pack(h, hits);
			treats(h);
			System.out.println("PASS Feeding Frenzy radius, exclusions, stacked attackers, yellow independent bites, zero/suppressed/protected hits, death cutoff and Dog Treats");
		}
	}
	private static void pack(CurrentCombatHarness h, List<DamageResult> hits) throws Exception {
		h.openCombatProjectileRectangle(440, 445, 440, 445);
		Npc dog = h.npc(867, 440, 440);
		Player p = h.player("dog target", 441, 440);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 1000, 1000, false);
		p.getSkills().setTemporaryLevelAndMaxStat(Skill.DEFENSE.id(), 1, 1, false);
		check(dog.getMeleeOffense() == 80 && dog.getMeleeDefense() == 55
			&& dog.getRangedDefense() == 55 && dog.getMagicDefense() == 55, "modern stats");
		check(NpcCombatProfile.resolve(dog).isMeleeOnly(), "melee only");
		check(TerrorDogCombat.nearbyDogs(dog) == 0, "does not count itself");
		Npc a = h.npc(867, 440, 441), b = h.npc(867, 441, 441);
		check(TerrorDogCombat.nearbyDogs(dog) == 2, "idle pack members count");
		Npc edge = h.npc(867, 442, 442);
		check(TerrorDogCombat.nearbyDogs(dog) == 3, "inclusive two-tile diagonal radius");
		edge.getSkills().setLevel(Skill.HITS.id(), 0);
		check(TerrorDogCombat.nearbyDogs(dog) == 2, "dead dog excluded");
		edge.getSkills().setLevel(Skill.HITS.id(), 90);
		CurrentCombatHarness.invokePrivate(edge, "setRespawning", new Class<?>[]{boolean.class}, true);
		check(TerrorDogCombat.nearbyDogs(dog) == 2, "respawning dog excluded");
		edge.remove();
		h.npc(867, 443, 437);
		h.npc(3, 440, 442);
		h.npc(867, 440, 1384);
		check(TerrorDogCombat.nearbyDogs(dog) == 2, "removed, outside-radius, other species and other floor excluded");
		a.setOpponent(p); b.setOpponent(p);
		for (int mode = 0; mode < 2; mode++) {
			hits.clear();
			for (Npc attacker : new Npc[]{dog, a, b}) {
				// No out-of-range dog may accidentally contribute to the other attackers.
				check(TerrorDogCombat.nearbyDogs(attacker) == 2, "each engaged attacker sees the other two");
				hit(h, mode, attacker, p, 1, false);
			}
			check(hits.size() == 9, "three attackers each make one main plus two extra hits, no recursion");
			for (int i = 0; i < hits.size(); i++) {
				check(hits.get(i).getRequest().getHitSplatType() == (i % 3 == 0 ? HitSplat.TYPE_STANDARD : HitSplat.TYPE_ARMOR_PROC), "red main, yellow extras");
				check(hits.get(i).getRequest().getTick() == h.server().getCurrentTick(), "all bites same tick");
			}
			hits.clear(); hit(h, mode, dog, p, 0, false);
			check(hits.size() == 1, "miss does not trigger frenzy");
			hits.clear(); hit(h, mode, dog, p, 0, true);
			check(hits.size() == 1, "suppression does not trigger frenzy");
			TerrorDogCombat.applyTreats(p);
			hits.clear(); hit(h, mode, dog, p, 1, false);
			check(hits.size() == 1 && hits.get(0).getActualDamage() == 1, "treats block only frenzy, not primary melee");
			h.clock().advanceMillis(600_000);
			hits.clear();
			h.random().reset(7); h.random().scriptDoubles(0.0, 0.999999).scriptInts(1, 1);
			hit(h, mode, dog, p, 1, false);
			check(hits.size() == 3 && hits.get(1).getActualDamage() == 0 && hits.get(2).getActualDamage() > 0, "expiry restores independently rolled bites even after an extra bite misses");
		}
		int[] calls = {0};
		TerrorDogCombat.applyFrenzy(dog, p, 1, false, damage -> { calls[0]++; p.advanceCombatLifecycle(); });
		check(calls[0] == 1, "lifecycle change stops remaining bites");
		hits.clear();
		p.getSkills().setLevel(Skill.HITS.id(), 2);
		h.random().reset(9); h.random().scriptDoubles(0.999999).scriptInts(1);
		hit(h, 0, dog, p, 1, false);
		check(hits.size() == 2 && hits.get(1).isTargetTerminal(), "lethal extra bite stops the rest");
	}
	private static void hit(CurrentCombatHarness h, int mode, Npc dog, Player p, int damage, boolean suppressed) throws Exception {
		Object event = mode == 0 ? new PvmMeleeEvent(h.world(), dog, p) : new CombatEvent(h.world(), dog, p);
		CurrentCombatHarness.invokePrivate(event, "inflictDamage", new Class<?>[]{Mob.class, Mob.class, int.class, boolean.class}, dog, p, damage, suppressed);
	}
	private static void treats(CurrentCombatHarness h) throws Exception {
		Player p = h.player("dog treats", 460, 460);
		p.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		h.recordOutgoingPackets(p);
		OpInvTrigger plugin = (OpInvTrigger) Class.forName("com.openrsc.server.plugins.custom.myworld.itemactions.DogTreats").newInstance();
		plugin.onOpInv(p, 0, new Item(3327), "Scatter");
		check(!TerrorDogCombat.protectedByTreats(p), "absent treats rejected");
		p.getCarriedItems().getInventory().add(new Item(3327));
		while (p.getCarriedItems().getInventory().size() < p.getCarriedItems().getInventory().getCapacity()) p.getCarriedItems().getInventory().add(new Item(3318));
		for (int next : new int[]{3328, 3329, ItemId.EMPTY_VIAL.id()}) {
			Item current = p.getCarriedItems().getInventory().get(0);
			check(TerrorDogCombat.EXAMINE.equals(current.getDef(h.world()).getDescription()), "exact examine all uses");
			check((Boolean) CurrentCombatHarness.invokePrivate(new ItemActionHandler(), "isCombatConsumableAction", new Class<?>[]{Item.class, String.class, Player.class}, current, "Scatter", p), "scatter allowed during combat");
			plugin.onOpInv(p, 0, current, "Scatter");
			check(p.getCarriedItems().getInventory().get(0).getCatalogId() == next, "three uses in same full-inventory slot");
			check(TerrorDogCombat.protectedByTreats(p), "protection granted/refreshed");
			h.clock().advanceMillis(1_000);
			plugin.onOpInv(p, 0, current, "Scatter");
			List<ActiveStatusEntry> statuses = new ArrayList<>();
			TerrorDogCombat.appendStatuses(p, statuses);
			check(statuses.size() == 1 && statuses.get(0).getRemainingSeconds() == 599, "stale item rejected; HUD duration and refresh");
		}
		h.clock().advanceMillis(599_000);
		check(!TerrorDogCombat.protectedByTreats(p), "expires at ten minutes");
		List<ActiveStatusEntry> statuses = new ArrayList<>(); TerrorDogCombat.appendStatuses(p, statuses);
		check(statuses.isEmpty(), "expired HUD row disappears");
		int messages = 0;
		for (Packet packet : (List<Packet>) CurrentCombatHarness.readPrivateField(p, "outgoingPackets")) if (packet.getBuffer().toString(java.nio.charset.StandardCharsets.ISO_8859_1).contains(TerrorDogCombat.USE_MESSAGE)) messages++;
		check(messages == 3, "exact scatter message once per valid use");
	}
	private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
