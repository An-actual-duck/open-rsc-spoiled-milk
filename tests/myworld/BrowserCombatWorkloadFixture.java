package orsc.browser;

import java.lang.reflect.Field;
import java.util.Arrays;
import orsc.ORSCharacter;
import orsc.mudclient;
import orsc.enumerations.GameMode;

/** Actual observer against real client fields; no UI, networking, server or GPU. */
public final class BrowserCombatWorkloadFixture {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Field field(String name)throws Exception{Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception {
        Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        mudclient c=(mudclient)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(mudclient.class);
        BrowserCombatWorkload observer=new BrowserCombatWorkload();
        field("currentViewMode").set(c,GameMode.LOGIN);
        check(Arrays.equals(observer.observe(c,new int[]{0,0,0,0}),new int[25]),"Login touched uninitialized game state");
        field("currentViewMode").set(c,GameMode.GAME);
        ORSCharacter npc=new ORSCharacter(),player=new ORSCharacter();
        field("npcs").set(c,new ORSCharacter[]{npc});field("players").set(c,new ORSCharacter[]{player});
        field("npcCount").setInt(c,1);field("playerCount").setInt(c,1);
        npc.projectileRange=40;npc.projectileImpactTime=3;npc.combatEffectType=35;npc.combatEffectTime=12;npc.pendingCombatEffectType=34;
        player.projectileRange=2;player.healthMax=30;player.healthCurrent=21;
        player.clericBuffPlayback=new orsc.ClericBuffPlayback();player.clericBuffPlayback.enqueue(92,11);
        npc.clericCursePlayback=new orsc.ClericCursePlayback();npc.clericCursePlayback.enqueue(111,13);
        int[] combat=new int[mudclient.COMBAT_EFFECT_COUNT+1];combat[34]=22;combat[35]=34;combat[37]=14;combat[26]=9;combat[27]=6;combat[28]=7;combat[29]=8;
        field("combatEffectFrameCounts").set(c,combat);field("projectileEffectFrameCounts").set(c,new int[]{3,0,7});field("projectileImpactFrameCounts").set(c,new int[]{0,4});
        int[] values=observer.observe(c,new int[]{1,1,1,0});
        check(values.length==BrowserCombatWorkload.NAMES.split("\\|").length,"Receipt schema differs");
        check(values[4]==2&&values[5]==1&&values[6]==1&&values[7]==1,"Actual dynamic counters differ");
        check(values[8]==1&&values[9]==1&&values[11]==1,"Cleric or health observations differ");
        check(values[12]==7&&values[13]==100,"Loaded combat totals differ");
        check(values[14]==2&&values[15]==10&&values[16]==1&&values[17]==4,"Loaded projectile totals differ");
        check(Arrays.equals(Arrays.copyOfRange(values,18,25),new int[]{34,22,14,9,6,7,8}),"Named spell/summon loaded frames differ");
        check(npc.projectileRange==40&&npc.combatEffectTime==12&&npc.projectileImpactTime==3&&player.clericBuffPlayback.getRemainingTicks()==11&&npc.clericCursePlayback.getRemainingTicks()==13,"Observer advanced or fabricated effects");
        check(Arrays.equals(values,observer.observe(c,new int[]{1,1,1,0})),"Read-only snapshots changed game state");
        check(BrowserBootstrap.privateFixturePort(null)==43629&&BrowserBootstrap.privateFixturePort("repeatable-v1")==43649&&BrowserBootstrap.privateFixturePort("renderer-combat-v1")==43639,"Fixture socket ports differ");
        System.setProperty("spoiledmilk.browserRepeatableFixture","renderer-combat-v1");
        System.setProperty("spoiledmilk.browserBenchmark","true");
        try {BrowserBootstrap.main(new String[]{"software"});throw new AssertionError("Software activated private combat fixture");}
        catch(IllegalArgumentException expected){checks++;}
        System.setProperty("spoiledmilk.browserBenchmark","false");
        try {BrowserBootstrap.main(new String[]{"webgl"});throw new AssertionError("Non-benchmark activated combat fixture");}
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS "+checks+" actual client counters/frames/read-only/route checks; no visible VFX or FPS claim");
    }
}
