package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.*;

/** Recipient-owned atomic god blessings; every component uses one registry snapshot. */
public final class ClericTimedEffectRuntime {
    private ClericTimedEffectRuntime(){}
    public static ClericCastTransaction.PreparedApplication prepare(final Player caster,final Player recipient,final ClericSpellDefinition spell,final int power){
        if(!spell.getId().isBlessing())throw new IllegalArgumentException("Not an active blessing");
        final boolean install=recipient.getTransientEffectState()==TransientEffectState.empty();
        final ClericEffectRegistry registry=install?new ClericEffectRegistry(ClericEffectClock.game(recipient.getWorld().getServer().getGameClock(),recipient.getConfig().GAME_TICK)):(ClericEffectRegistry)recipient.getTransientEffectState();
        if(install)registry.setPeriodicListener(entry->settlePeriodic(recipient,entry));
        final ClericEffectRankDefinition<?> definition=ClericEffectCatalog.get(spell.getId(),power);
        final ClericEffectOrigin origin=ClericEffectOrigins.current(caster,recipient);
        final ClericEffectOriginValidator validator=ClericEffectOrigins.validatorFor(recipient);
        final boolean useful=registry.preview(definition,origin,validator).isUseful();
        return new ClericCastTransaction.PreparedApplication(){
            public boolean isUseful(){return useful;}
            public void commit(){
                if(!useful)throw new IllegalStateException("Ineffective blessing commit");
                if(install)recipient.installTransientEffectState(registry);
                if(!registry.apply(definition,origin,validator).isUseful())throw new IllegalStateException("Blessing changed during atomic cast");
                ClericHealingPulseEvent.restart(recipient);
                String flavor=recipientFlavor(spell.getId());if(flavor!=null)recipient.message(flavor);
                else recipient.message(spell.getDisplayName()+" surrounds you.");
                ActionSender.sendActivePotionEffects(recipient);
            }
        };
    }
    public static ClericBlessingMagnitude blessing(Player player,ClericEffectFamily family){
        if(player==null||!player.isLoggedIn()||player.isRemoved()||player.isUnregistering()||player.getSkills().getLevel(Skill.HITS.id())<=0||ClericSupportCasting.isPvpContext(player)||!(player.getTransientEffectState() instanceof ClericEffectRegistry))return null;
        Optional<ClericEffectEntry> entry=((ClericEffectRegistry)player.getTransientEffectState()).get(family,ClericEffectOrigins.validatorFor(player));
        return entry.isPresent()&&entry.get().getDefinition().getMagnitude() instanceof ClericBlessingMagnitude?(ClericBlessingMagnitude)entry.get().getDefinition().getMagnitude():null;
    }
    /** Existing natural restoration clock uses additional rate, not an immediate heal. */
    public static double getRespiteSpeedBonus(Player player){ClericBlessingMagnitude m=blessing(player,ClericEffectFamily.SARADOMIN_BLESSING);return m==null?0:(m.regenerationPercent()-100)/100.0D;}
    public static int poisonCleanse(Player player){ClericBlessingMagnitude m=blessing(player,ClericEffectFamily.GUTHIX_BLESSING);return m==null?0:m.poisonCleanse();}
    public static int defense(Player player,int base){ClericBlessingMagnitude m=blessing(player,ClericEffectFamily.GUTHIX_BLESSING);return addPercent(base,m==null?0:m.defensePercent());}
    public static int addPercent(int base,int percent){return (int)Math.min(Integer.MAX_VALUE,Math.max(0L,(long)base+(long)Math.max(0,base)*percent/100L));}
    private static void settlePeriodic(Player player,ClericEffectEntry entry){
        if(player.getSkills().getLevel(Skill.HITS.id())<=0||ClericSupportCasting.isPvpContext(player))return;
        ClericBlessingMagnitude m=(ClericBlessingMagnitude)entry.getDefinition().getMagnitude();
        if(m.healingPerPulse()>0)heal(player,m.healingPerPulse());
        if(m.restoreIntervalSeconds()>0){
            for(int skill=0;skill<player.getWorld().getServer().getConstants().getSkills().getSkillsCount();skill++)if(skill!=Skill.HITS.id()){
                int current=player.getSkills().getLevel(skill),normal=player.getEquipmentAdjustedNormalLevel(skill);
                if(current<normal)player.getSkills().setLevel(skill,Math.min(normal,current+1),true,true);
            }
        }
    }
    static boolean tick(Player player){
        if(player==null||!player.isLoggedIn()||player.isRemoved()||player.isUnregistering()||!(player.getTransientEffectState() instanceof ClericEffectRegistry))return false;
        ClericEffectRegistry registry=(ClericEffectRegistry)player.getTransientEffectState();
        int size=registry.size(ClericEffectOrigins.validatorFor(player));ActionSender.sendActivePotionEffects(player);return size>0;
    }
    public static int heal(Player player,int amount){int current=player.getSkills().getLevel(Skill.HITS.id());if(current<=0)return 0;int healed=Math.min(amount,Math.max(0,player.getHealingMaximumHits()-current));if(healed>0){player.getSkills().setLevel(Skill.HITS.id(),current+healed);player.getUpdateFlags().addHitSplat(new HitSplat(player,HitSplat.TYPE_HEAL,healed));ActionSender.sendStat(player,Skill.HITS.id());if(player.getParty()!=null)player.getParty().sendParty();}return healed;}
    // Compatibility symbol for old tests; no legacy healing spells remain active.
    public enum PulseResult{MISSING_OR_INVALID,CONTINUES,COMPLETED}
    static PulseResult pulseHealing(Player player){return tick(player)?PulseResult.CONTINUES:PulseResult.MISSING_OR_INVALID;}
    private static String recipientFlavor(ClericSpellId id){switch(id){
        case SARADOMIN_WARMTH:return "You feel your blood rush and your spirits lift";
        case SARADOMIN_AURA:return "Your wounds seal up as soon as they are opened";
        case GUTHIX_RESPITE:return "Your blood feels thick in your veins like sap. Your ailments slow to a halt";
        case GUTHIX_PROTECTION:return "Your skin grows a thick layer of bark";
        case GUTHIX_VENGEANCE:return "Sharp spikes protrude from you, piercing your foes.";
        case ZAMORAK_FERVOR:return "Your muscles bulge and your mind feels sharper";
        case ZAMORAK_FURY:return "Your enemies seem much smaller than before";
        case ZAMORAK_WRATH:return "Your weapon will breathe new life into you should you feel weak";
        default:return null;}}
}
