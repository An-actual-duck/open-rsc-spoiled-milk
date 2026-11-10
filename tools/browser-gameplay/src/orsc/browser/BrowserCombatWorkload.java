package orsc.browser;

import java.lang.reflect.Field;
import orsc.ORSCharacter;
import orsc.mudclient;

/** Read-only once-per-second observations; never starts combat, advances effects or replaces assets. */
final class BrowserCombatWorkload {
    static final String NAMES="cameraPinned|npcCount|playerCount|welcomeVisible|activeProjectileActors|activeImpactActors|activeCombatEffectActors|pendingCombatEffectActors|activeClericBuffActors|activeClericCurseActors|activeHitSplatActors|damagedHealthActors|loadedCombatEffectKinds|loadedCombatEffectFrames|loadedProjectileKinds|loadedProjectileFrames|loadedProjectileImpactKinds|loadedProjectileImpactFrames|iceCrystalFrames|thunderStrikeFrames|batteringRamFrames|summonChargeFrames|summonCombatFrames|summonSupportFrames|summonUtilityFrames";
    private final Field projectileCounts=field("projectileEffectFrameCounts"),impactCounts=field("projectileImpactFrameCounts");
    private static Field field(String name) {
        try {Field result=mudclient.class.getDeclaredField(name);result.setAccessible(true);return result;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Private combat receipt layout changed: "+name,error);}
    }
    int[] observe(mudclient client,int[] camera)throws IllegalAccessException {
        if(camera==null||camera.length!=4)throw new IllegalArgumentException("Camera receipt requires four counters");
        int[] result=new int[25];System.arraycopy(camera,0,result,0,4);
        if(!client.isGameView())return result;
        // Counts are received local actors, not projected or unoccluded pixels.
        for(int i=0;i<client.getNpcCount();i++)actor(result,client.getNpc(i));
        for(int i=0;i<client.getPlayerCount();i++)actor(result,client.getPlayer(i));
        for(int effect=1;effect<=mudclient.COMBAT_EFFECT_COUNT;effect++) {
            int frames=client.getCombatEffectFrameCount(effect);
            if(frames>0){result[12]++;result[13]+=frames;}
        }
        frames(result,14,(int[])projectileCounts.get(client));
        frames(result,16,(int[])impactCounts.get(client));
        int[] named={mudclient.COMBAT_EFFECT_ICE_CRYSTAL,mudclient.COMBAT_EFFECT_THUNDER_STRIKE,mudclient.COMBAT_EFFECT_BATTERING_RAM,
            mudclient.COMBAT_EFFECT_SUMMON,mudclient.COMBAT_EFFECT_SUMMON_COMBAT,mudclient.COMBAT_EFFECT_SUMMON_SUPPORT,mudclient.COMBAT_EFFECT_SUMMON_UTILITY};
        for(int i=0;i<named.length;i++)result[18+i]=client.getCombatEffectFrameCount(named[i]);
        return result;
    }
    private static void frames(int[] result,int offset,int[] counts) {
        if(counts==null)return;
        for(int count:counts)if(count>0){result[offset]++;result[offset+1]+=count;}
    }
    private static void actor(int[] result,ORSCharacter actor) {
        if(actor==null)return;
        if(actor.projectileRange>0)result[4]++;
        if(actor.projectileImpactTime>0)result[5]++;
        if(actor.combatEffectTime>0)result[6]++;
        if(actor.pendingCombatEffectType>0)result[7]++;
        if(actor.clericBuffPlayback!=null&&actor.clericBuffPlayback.getRemainingTicks()>0)result[8]++;
        if(actor.clericCursePlayback!=null&&actor.clericCursePlayback.getRemainingTicks()>0)result[9]++;
        if(actor.floatingHitSplats.isActive())result[10]++;
        if(actor.healthMax>0&&actor.healthCurrent<actor.healthMax)result[11]++;
    }
}
