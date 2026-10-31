package com.openrsc.server.content.cleric.runtime;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.content.cleric.ClericDirectCombatEffects;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;

/** Primary direct PvE hits only. Terminal reflection never returns through these hooks. */
public final class ClericDirectCombatRuntime {
    private ClericDirectCombatRuntime(){}
    /** Retired accuracy effect contributes nothing; existing unrelated bias remains intact. */
    public static double combineFervorRollChance(Mob attacker,double existing){return Math.max(0,Math.min(1,existing));}
    public static int maximum(Mob attacker,Mob defender,int maximum){
        if(!eligible(attacker,defender)||!(attacker instanceof Player))return maximum;
        ClericBlessingMagnitude m=ClericTimedEffectRuntime.blessing((Player)attacker,ClericEffectFamily.ZAMORAK_BLESSING);
        return ClericTimedEffectRuntime.addPercent(maximum,m==null?0:m.maxDamagePercent());
    }
    public static BeforeDamage beforeDirectDamage(Mob attacker,Mob defender,int damage){
        if(damage<0)throw new IllegalArgumentException("Negative damage");
        int bonus=eligible(attacker,defender)&&attacker instanceof Player?carry((Player)attacker,ClericEffectFamily.ZAMORAK_BLESSING,7,damage):0;
        return new BeforeDamage(ClericDirectCombatEffects.addBounded(damage,bonus),0);
    }
    public static AfterDamage afterExistingLifesteal(Mob attacker,Mob defender,int actual){return afterExistingLifesteal(attacker,defender,actual,true);}
    public static AfterDamage afterExistingLifesteal(Mob attacker,Mob defender,int actual,boolean lifesteal){
        if(actual<=0||!eligible(attacker,defender))return new AfterDamage(0,0);
        int heal=lifesteal?applyDeferredRally(attacker,defender,actual):0;
        int reflect=defender instanceof Player?carry((Player)defender,ClericEffectFamily.GUTHIX_BLESSING,9,actual):0;
        return new AfterDamage(heal,reflect);
    }
    public static int applyDeferredRally(Mob attacker,Mob defender,int actual){
        if(actual<=0||!eligible(attacker,defender)||!(attacker instanceof Player))return 0;
        Player player=(Player)attacker;
        if(!ClericDirectCombatEffects.isBelowPercent(player.getSkills().getLevel(Skill.HITS.id()),player.getHealingMaximumHits(),50))return 0;
        return ClericTimedEffectRuntime.heal(player,carry(player,ClericEffectFamily.ZAMORAK_BLESSING,8,actual));
    }
    private static int carry(Player p,ClericEffectFamily family,int component,int amount){
        if(ClericTimedEffectRuntime.blessing(p,family)==null)return 0;
        return ((ClericEffectRegistry)p.getTransientEffectState()).accumulateComponentPercent(family,component,amount,ClericEffectOrigins.validatorFor(p));
    }
    private static boolean usable(Player p){return p.isLoggedIn()&&!p.isRemoved()&&!p.isUnregistering()&&p.getSkills().getLevel(Skill.HITS.id())>0&&!ClericSupportCasting.isPvpContext(p);}
    private static boolean eligible(Mob a,Mob d){return a!=null&&d!=null&&!Summoning.isSummon(a)&&!Summoning.isSummon(d)&&!(a instanceof Player&&d instanceof Player)&&(!(a instanceof Player)||usable((Player)a))&&(!(d instanceof Player)||usable((Player)d));}
    public static final class BeforeDamage{private final int damage,prevented;private BeforeDamage(int d,int p){damage=d;prevented=p;}public int getDamage(){return damage;}public int getPreventedDamage(){return prevented;}}
    public static final class AfterDamage{private final int heal,reflect;private AfterDamage(int h,int r){heal=h;reflect=r;}public int getRallyHealing(){return heal;}public int getThornsDamage(){return reflect;}}
}
