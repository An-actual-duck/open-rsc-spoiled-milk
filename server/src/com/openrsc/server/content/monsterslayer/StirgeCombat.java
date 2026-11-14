package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Bleed;
import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.List;

/** A blood-drinking melee pest; Bug Off prevents its wounds and their healing. */
public final class StirgeCombat {
    public static final int NPC_ID=882, BUG_OFF=3462, BUG_OFF_TWO=3463, BUG_OFF_ONE=3464, EMPTY_CAN=3465, SANGUINARY_GLAND=3466;
    public static final long PROTECTION_MILLIS=600_000L;
    private static final String PROTECTION="slayer_bug_off_until";
    private StirgeCombat() { }
    public static boolean isStirge(Mob mob) {
        return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && mob.getID()==NPC_ID;
    }
    public static int doses(int id) {return id>=BUG_OFF&&id<=BUG_OFF_ONE?BUG_OFF_ONE+1-id:0;}
    public static long protectionRemaining(Player player) {
        if(!player.getCache().hasKey(PROTECTION))return 0;
        return Math.max(0,Math.min(PROTECTION_MILLIS,player.getCache().getLong(PROTECTION)
            -player.getWorld().getServer().getGameClock().currentTimeMillis()));
    }
    public static boolean protectedByBugOff(Player player) {return protectionRemaining(player)>0;}
    public static void applyBugOff(Player player) {
        player.getCache().store(PROTECTION,player.getWorld().getServer().getGameClock().currentTimeMillis()+PROTECTION_MILLIS);
        Bleed.clearStirgeWounds(player);
        player.message("Bug Off keeps stirges from drinking your blood for ten minutes.");
        ActionSender.sendActivePotionEffects(player);
    }
    public static void primaryHit(Mob source,Mob target,int damage,boolean suppressed) {
        if(!suppressed&&damage>0&&isStirge(source)&&target instanceof Player&&!protectedByBugOff((Player)target))
            Bleed.stirgeHit((Npc)source,(Player)target);
    }
    public static void drink(Mob source,int actualDamage) {
        if(!isStirge(source)||source.isRemoved()||source.killed||actualDamage<=0||source.getLevel(Skill.HITS.id())<=0)return;
        int hp=source.getLevel(Skill.HITS.id());int heal=Math.min(actualDamage,source.getSkills().getMaxStat(Skill.HITS.id())-hp);
        if(heal>0){source.getSkills().setLevel(Skill.HITS.id(),hp+heal);source.getUpdateFlags().addHitSplat(new HitSplat(source,HitSplat.TYPE_HEAL,heal));}
    }
    public static void appendStatuses(Player player,List<ActiveStatusEntry> statuses) {
        long remaining=protectionRemaining(player);
        if(remaining>0)statuses.add(ActiveStatusEntry.item("slayer:bug_off",BUG_OFF,(int)((remaining+999)/1000)));
    }
}
