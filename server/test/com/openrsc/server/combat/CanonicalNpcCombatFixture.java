package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Slow;
import com.openrsc.server.content.npccombat.*;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.npc.*;
import com.openrsc.server.model.entity.player.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Offline actual production catalog, entities, behavior and scheduler; no sockets/accounts. */
public final class CanonicalNpcCombatFixture {
    private static int checks;
    private static void check(boolean yes, String detail) {
        checks++; if (!yes) throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            JSONObject data = new JSONObject(new String(Files.readAllBytes(
                Paths.get("conf/server/defs/NpcCombatMyWorld.json")), StandardCharsets.UTF_8));
            NpcCombatCatalog catalog = h.server().getNpcCombatCatalog();
            check(catalog.canonicalCount() == 275, "275 canonical definitions");
            for (int i = 0; i < data.getJSONArray("npcs").length(); i++) {
                JSONObject row = data.getJSONArray("npcs").getJSONObject(i);
                Npc n = new Npc(h.world(), i, 440, 440, 5);
                NpcCombatDefinition d = CanonicalNpcCombat.definition(n);
                if (!row.getString("policy").equals("canonical")) {
                    check(d == null && catalog.compatibilityPolicy(i) != null, "explicit compatibility " + i);
                    continue;
                }
                JSONObject p = row.getJSONObject("power"), f = row.getJSONObject("defense");
                check(n.getMeleeOffense() == p.getInt("melee") && n.getRangedOffense() == p.getInt("ranged")
                    && n.getMagicOffense() == p.getInt("magic"), "raw powers " + i);
                check(n.getMeleeDefense() == f.getInt("melee") && n.getRangedDefense() == f.getInt("ranged")
                    && n.getMagicDefense() == f.getInt("magic"), "raw defenses " + i);
                check(n.getHolyPower() == 0 && n.isBoss() == row.getBoolean("boss"), "inert Holy/boss " + i);
                if (d.genericDispatch) {
                    NpcCombatProfile profile = NpcCombatProfile.resolve(n);
                    check(profile.usesMagicProjectiles() == d.uses(CombatStyle.MAGIC)
                        && profile.usesRangedProjectiles() == d.uses(CombatStyle.RANGED), "dispatch " + i);
                }
            }
            rejects(h, data, r -> r.getJSONObject("power").remove("magic"));
            rejects(h, data, r -> r.getJSONObject("defense").put("melee", -1));
            rejects(h, data, r -> r.getJSONObject("defense").put("melee", Integer.MAX_VALUE));
            rejects(h, data, r -> r.getJSONObject("defense").put("melee", "0"));
            rejects(h, data, r -> r.getJSONObject("weights").put("magic", 99));
            rejects(h, data, r -> r.put("styles", new JSONArray("[\"typo\"]")));
            rejects(h, data, r -> r.put("magicGod", "unknown"));
            rejects(h, data, r -> r.remove("magicGod"));
            rejects(h, data, r -> r.getJSONObject("power").put("magic", 0));
            JSONObject missing = new JSONObject(data.toString()); missing.getJSONArray("npcs").remove(23);
            expectReject(h, missing);
            JSONObject duplicate = new JSONObject(data.toString()); duplicate.getJSONArray("npcs").put(duplicate.getJSONArray("npcs").getJSONObject(23));
            expectReject(h, duplicate);
            zeroAndCompatibility(h);
            meleePresentation(h);
            timing(h);
            alignedMagic(h);
            trueDamage(h);
        }
        System.out.println("PASS canonical NPC combat: " + checks + " assertions; catalog coverage/schema, explicit zeros, compatibility, dispatch, weights, shared cadence, alignment");
    }
    private static void rejects(CurrentCombatHarness h, JSONObject data, java.util.function.Consumer<JSONObject> mutate) {
        JSONObject altered = new JSONObject(data.toString());
        // Darkwizard 57 is a canonical Magic-only NPC.
        mutate.accept(altered.getJSONArray("npcs").getJSONObject(57)); expectReject(h, altered);
    }
    private static void expectReject(CurrentCombatHarness h, JSONObject altered) {
        boolean rejected = false;
        try { NpcCombatCatalog.parse(h.server(), altered); } catch (IllegalArgumentException | JSONException expected) { rejected = true; }
        check(rejected, "malformed/missing catalog must fail closed");
    }
    private static void zeroAndCompatibility(CurrentCombatHarness h) {
        Npc spider = h.npc(23, 440, 440);
        check(spider.getMeleeDefense() == 0 && spider.getMagicDefense() == 0 && spider.getRangedDefense() == 10, "young spider literal zeros");
        int oldDef = spider.getDef().defense, oldStr = spider.getDef().strength, oldAtt = spider.getDef().attack;
        try {
            spider.getDef().defense = 999; spider.getDef().strength = 999; spider.getDef().attack = 999;
            check(spider.getMeleeDefense() == 0 && spider.getRangedDefense() == 10 && spider.getMeleeOffense() != 999, "canonical values ignore A/S/D");
        } finally { spider.getDef().defense = oldDef; spider.getDef().strength = oldStr; spider.getDef().attack = oldAtt; }
        Npc minigame = h.npc(595, 442, 440);
        check(CanonicalNpcCombat.definition(minigame) == null, "Gnome baller protected");
        check(!CanonicalNpcCombat.generic(minigame), "minigame not generic rewritten dispatch");
        Npc owned = h.npc(23, 443, 440); owned.setAttribute("myworld_summon_owner", 1L);
        check(CanonicalNpcCombat.definition(owned) == null, "owned summon skips canonical roster");
        Player p = h.player("zero roll", 441, 440);
        check(CanonicalNpcCombat.defenseRollMinimum(p, spider) == 0, "modern inclusive zero");
        h.random().reset(211); String emptyDraws = h.random().describeState();
        check(CombatFormula.calculateMagicDamage(spider, p, 10) == 0
            && CombatFormula.doRangedDamage(spider, 188, 11, p, false) == 0, "disabled primary style cannot roll incidental damage");
        check(emptyDraws.equals(h.random().describeState()), "disabled style skips all combat RNG");
        check(CanonicalNpcCombat.defenseRollMinimum(p, owned) == 1 && CanonicalNpcCombat.defenseRollMinimum(p, minigame) == 1, "legacy compatibility dice");
        // Pin actual mitigation, not merely a catalog zero: identical player offense,
        // ranged defense cap two, with the new zero-capable random draw.
        h.random().reset(19); h.random().scriptInts(0, 0);
        int damage = CombatFormula.doRangedDamage(p, 188, 11, spider, false);
        check(damage >= 1, "zero defense roll no longer guarantees blocking one");
        check(h.random().describeState().contains("int(3)=0"), "defense die includes zero through cap two");
        spider.remove(); owned.remove(); minigame.remove(); h.logout(p);
    }
    private static void meleePresentation(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(450,456,450,452);
        Npc mixed=h.npc(104,450,450); Player p=h.player("mixed stance",451,450);
        p.getCache().store("setting_auto_retaliate",false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),500,500,false);
        h.random().reset(992); h.random().scriptInts(0);
        PvmMeleeEvent attack=new PvmMeleeEvent(h.world(),mixed,p);mixed.setPvmMeleeEvent(attack);
        attack.run();
        check(mixed.getHitsMade()==1&&mixed.inCombat(),"mixed NPC starts actual melee swing");
        check(mixed.getBehavior().tryCanonicalProjectileAttack(p),"mixed NPC holds projectile cooldown");
        check(mixed.inCombat()&&mixed.getSprite()>=8,"cooldown must preserve active melee animation stance");
        for(int i=0;i<2;i++)h.advanceOneCombatTick();
        h.random().reset(994);h.random().scriptInts(99);
        check(mixed.getBehavior().tryCanonicalProjectileAttack(p),"mixed NPC commits eligible Magic cast");
        check(mixed.getSprite()<8,"actual Magic cast switches away from melee swing");
        check(mixed.getBehavior().tryCanonicalProjectileAttack(p)&&mixed.getSprite()<8,
            "Magic cooldown does not invent a melee swing");
        mixed.remove();h.logout(p);
        Npc distant=h.npc(104,450,450);Player far=h.player("distant style",454,450);
        far.getCache().store("setting_auto_retaliate",false);
        h.random().reset(993);h.random().scriptInts(0);
        check(distant.getBehavior().tryCanonicalProjectileAttack(far),"distant melee cannot be selected just because an adjacency edge check passes");
        distant.remove();h.logout(far);
    }
    private static void timing(CurrentCombatHarness h) throws Exception {
        h.openCombatProjectileRectangle(440, 454, 440, 445);
        Npc mage = h.npc(57, 440, 440); Player p = h.player("spell target", 441, 440);
        p.getCache().store("setting_auto_retaliate", false);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 500, 500, false);
        check(mage.getBehavior().tryCanonicalProjectileAttack(p), "adjacent dedicated mage fires");
        check(!CanonicalNpcCombat.ready(mage), "projectile commits shared cooldown");
        int swings = mage.getHitsMade();
        PvmMeleeEvent melee = new PvmMeleeEvent(h.world(), mage, p); mage.setPvmMeleeEvent(melee);
        melee.run(); check(mage.getHitsMade() == swings, "melee engine cannot double fire/swing");
        mage.walkToEntityAStar(443, 440); check(mage.getBehavior().tryCanonicalProjectileAttack(p) && mage.finishedPath(), "clear shot holds still on cooldown");
        for (int i = 0; i < 4; i++) h.advanceOneCombatTick();
        check(CanonicalNpcCombat.select(mage, p, true, true) == CombatStyle.MAGIC, "adjacent mage remains Magic");
        p.setLocation(Point.location(446, 440));
        mage.setChasing(p);
        mage.resetPath();
        melee.run();
        check(!mage.finishedPath(), "out-of-range caster uses existing pursuit: current="+mage.isCurrentPvmMeleeEvent(melee)+" running="+melee.isRunning()+" hp="+mage.getLevel(Skill.HITS.id())+" removed="+mage.isRemoved()+" respawning="+mage.isRespawning()+" position="+mage.getLocation()+" target="+p.getLocation()+" leash="+mage.getLoc().minX()+".."+mage.getLoc().maxX());
        mage.remove(); h.logout(p);
        Npc mixed = h.npc(254, 450, 440); Player q = h.player("mixed target", 451, 440);
        h.random().reset(21); h.random().scriptInts(99);
        CombatStyle first = CanonicalNpcCombat.select(mixed, q, true, true);
        String state = h.random().describeState();
        for (int i = 0; i < 20; i++) check(CanonicalNpcCombat.select(mixed, q, true, true) == first, "cached style until commitment");
        check(state.equals(h.random().describeState()), "polls consume no extra selection RNG");
        h.random().reset(23);
        int magic = 0;
        for (int i = 0; i < 4000; i++) {
            if (CanonicalNpcCombat.select(mixed, q, true, true) == CombatStyle.MAGIC) magic++;
            CanonicalNpcCombat.committed(mixed, 1); h.advanceOneCombatTick();
        }
        check(magic > 2800 && magic < 3200, "primary 75% weighting: " + magic);
        CanonicalNpcCombat.committed(mixed, 3);
        check(!CanonicalNpcCombat.ready(mixed), "melee commitment prevents projectile bypass");
        Player changed = h.player("retargeted", 452, 440);
        check(CanonicalNpcCombat.select(mixed, changed, true, true) == null, "retarget cannot erase cooldown");
        h.logout(changed);
        mixed.remove(); h.logout(q);
        Npc ranged = h.npc(21, 444, 444); Player r = h.player("ranged target", 445, 444);
        check(CanonicalNpcCombat.select(ranged, r, false, true) == CombatStyle.RANGED, "Mugger shoots when melee unavailable");
        h.random().reset(24); h.random().scriptInts(99);
        // Target change invalidates a pending decision and permits the 25% secondary.
        Player s = h.player("other target", 443, 444);
        check(CanonicalNpcCombat.select(ranged, s, true, true) == CombatStyle.RANGED, "weighted primary is ranged");
        ranged.remove(); h.logout(r); h.logout(s);
    }
    private static void alignedMagic(CurrentCombatHarness h) {
        Player p = h.player("god target", 441, 440);
        for (int id : new int[]{93,139,200}) {
            Npc n = h.npc(id, 440, 440); NpcCombatDefinition d = CanonicalNpcCombat.definition(n);
            ProjectileLaunchSpecification shot = ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.NPC_MAGIC, 11, 1)
                .npcMagicGodLine(NpcCombatProfile.resolve(n).selectMagicAttack().getGodLine()).build();
            for (PrayerCatalog.GodLine god : PrayerCatalog.GodLine.values()) {
                p.setPrayerBook(god);
                check(GodAlignedNpcMagic.reduce(n,p,shot,11) == (god == d.magicGod ? 8 : 11), "20% matching alignment " + id + "/" + god);
            }
            p.setPrayerBook(d.magicGod);
            check(GodAlignedNpcMagic.reduce(n,p,shot,1) == 0, "no forced minimum after reduction");
            check(GodAlignedNpcMagic.reduce(p,p,shot,11) == 11, "player source not reduced");
            // Exercise the actual impact path, so an unhooked helper cannot pass.
            p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 200, 200, false);
            new com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent(h.world(), n, p, shot).action();
            check(p.getSkills().getLevel(Skill.HITS.id()) == 192, "actual aligned Magic impact reduces once");
            n.setAttribute("myworld_summon_owner", 1L);
            check(GodAlignedNpcMagic.reduce(n,p,shot,11) == 11, "owned summon not aligned NPC magic");
            n.remove();
        }
        h.logout(p);
    }
    private static void trueDamage(CurrentCombatHarness h) throws Exception {
        Player p = h.player("breath target", 441, 440);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 100, 100, false);
        com.openrsc.server.event.rsc.impl.combat.scripts.all.DragonFireBreath breath =
            new com.openrsc.server.event.rsc.impl.combat.scripts.all.DragonFireBreath();
        check(breath.baseDependentFireDamage(1.0, 477, p) == 20, "legacy unprotected HP breath");
        check(breath.baseDependentFireDamage(0.2, 477, p) == 4, "legacy KBD shield HP breath");
        Npc n = h.npc(477, 440, 440);
        h.openCombatProjectileRectangle(440, 442, 440, 442);
        h.equip(p, com.openrsc.server.constants.ItemId.ANTI_DRAGON_BREATH_SHIELD.id(), 1);
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.DEFENSE.id(), 99, 99, false);
        breath.executeScript(p, n);
        check(p.getSkills().getLevel(Skill.HITS.id()) == 96, "actual shield breath bypasses high normal defense: HP="+p.getSkills().getLevel(Skill.HITS.id())+" shield="+p.getCarriedItems().getEquipment().hasEquipped(com.openrsc.server.constants.ItemId.ANTI_DRAGON_BREATH_SHIELD.id())+" path="+com.openrsc.server.model.PathValidation.checkNpcDragonFireBreathPath(h.world(),n.getWorldLocation(),p.getWorldLocation()));
        for (int i = 0; i < 10; i++) {
            p.getSkills().setLevel(Skill.HITS.id(), 100);
            breath.executeScript(n, p);
            int left = p.getSkills().getLevel(Skill.HITS.id());
            check(left >= 87 && left <= 100, "NPC-first shield branch retains reduced 0..13 die");
        }
        check(NpcTrueDamage.DRAGON_BREATH == NpcTrueDamage.Kind.TRUE_DAMAGE, "breath classified True damage");
        n.remove(); h.logout(p);
    }
}
