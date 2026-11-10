import java.lang.reflect.*;
import java.util.*;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.Skills;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Actual frozen Entity/Skills + real fixture helper, no World/server/event/thread launch. */
public final class RendererSummonSustainFixture {
    static int checks;static sun.misc.Unsafe unsafe;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static String key(String name)throws Exception{return (String)field(Summoning.class,name).get(null);}
    static void set(String name,Object value)throws Exception{field(RendererBenchmarkServer.class,name).set(null,value);}
    static void call(Player owner,int index)throws Exception{
        Method m=RendererBenchmarkServer.class.getDeclaredMethod("sustainOwnedBenchmarkBear",Player.class,int.class);m.setAccessible(true);
        try{m.invoke(null,owner,index);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void reject(Player owner,int index)throws Exception{try{call(owner,index);throw new AssertionError("Expected bounded fixture rejection");}catch(IllegalStateException expected){checks++;}}
    static <T>T entity(Class<T> c)throws Exception {
        T value=(T)unsafe.allocateInstance(c);field(Entity.class,"attributes").set(value,new java.util.concurrent.ConcurrentHashMap<String,Object>());return value;
    }
    public static void main(String[] args)throws Exception{
        Skill.init(Collections.singletonMap(com.openrsc.server.constants.Skills.HITS,3));
        unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        // Missing/false opt-in must not even resolve private keys or touch a null entity.
        set("phase","combat-real");check(!field(RendererBenchmarkServer.class,"sustainSummonsForBenchmark").getBoolean(null),"Default opted in");
        call(null,-1);check(field(RendererBenchmarkServer.class,"summonSurvivalKeys").get(null)==null,"Default resolved survival metadata");
        set("sustainSummonsForBenchmark",true);set("phase","dense-idle");call(null,-1);set("phase","combat-real");reject(null,-1);
        List<Player> actors=(List<Player>)field(RendererBenchmarkServer.class,"actors").get(null);
        String manual=key("MANUAL_SUMMON_KEY"),ownerKey=key("SUMMON_OWNER_KEY"),current=key("SUMMON_CURRENT_HITS_KEY"),maximum=key("SUMMON_MAX_HITS_KEY"),kind=key("SUMMON_KIND_KEY"),bearKind=key("KIND_BEAR");
        String absorb=key("SUMMON_DAMAGE_ABSORBING_KEY"),percent=key("SUMMON_ABSORB_PERCENT_KEY"),invulnerable=key("SUMMON_INVULNERABLE_KEY");
        for(int index=0;index<8;index++) {
            Player owner=entity(Player.class);field(Player.class,"usernameHash").setLong(owner,1000+index);owner.setAttribute("renderer_benchmark_actor",true);actors.add(owner);
            Npc bear=entity(Npc.class);bear.setAttribute(ownerKey,owner.getUsernameHash());bear.setAttribute(kind,bearKind);bear.setAttribute(current,17);bear.setAttribute(maximum,17);bear.setAttribute(absorb,true);bear.setAttribute(percent,60);owner.setAttribute(manual,bear);
            Skills skills=(Skills)unsafe.allocateInstance(Skills.class);field(Skills.class,"mob").set(skills,bear);field(Skills.class,"levels").set(skills,new int[32]);field(Skills.class,"maxStatsMob").set(skills,new int[32]);field(Mob.class,"skills").set(bear,skills);
            bear.setAttribute(ownerKey,-1L);reject(owner,index);bear.setAttribute(ownerKey,owner.getUsernameHash());
            bear.setAttribute(kind,"unrelated");reject(owner,index);bear.setAttribute(kind,bearKind);
            owner.setAttribute("renderer_benchmark_actor",false);reject(owner,index);owner.setAttribute("renderer_benchmark_actor",true);
            call(owner,index);
            check(Summoning.getSummonCurrentHits(bear)==30000&&Summoning.getSummonMaxHits(bear)==30000,"Actual frozen summon HP metadata mismatch");
            check(skills.getLevel(Skill.HITS.id())==30000&&skills.getMaxStat(Skill.HITS.id())==30000,"Actual frozen skill HP mismatch");
            check(bear.getAttribute(absorb,false)&&bear.getAttribute(percent,0)==60&&!bear.getAttribute(invulnerable,false),"Absorption or invulnerability policy changed");
            bear.setAttribute(current,29990);reject(owner,index);check(Summoning.getSummonCurrentHits(bear)==29990,"Repeated call refilled survival budget");
        }
        reject(actors.get(0),8);check(field(RendererBenchmarkServer.class,"sustainedSummons").getInt(null)==8,"Expected exactly8one-time owned bears");
        check(((Set<?>)field(RendererBenchmarkServer.class,"sustainedBearInstances").get(null)).size()==8,"Identity bound differs");
        System.out.println("PASS "+checks+" actual frozen-key/entity/skills sustain checks; no server started, no combat/FPS proof");
    }
}
