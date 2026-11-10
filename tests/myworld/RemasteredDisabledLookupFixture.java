package orsc.remastered;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import java.lang.reflect.*;
import java.util.*;
/** Actual new resolver vs literal frozen old resolver; settings availability altered only in isolated test classes. */
public final class RemasteredDisabledLookupFixture {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class CountingAnimation extends AnimationDef {
        int reads;
        CountingAnimation(){super(" Demon "," NPC ",0,0,0,false,false,1);}
        @Override public String getName(){reads++;return name;}
    }
    static Sprite canonical(){
        RemasteredSpriteCatalog.Entry e=RemasteredSpriteCatalog.get("sprite/npc/demon/0");
        Sprite s=new Sprite(new int[e.getPixelWidth()*e.getPixelHeight()],e.getPixelWidth(),e.getPixelHeight());s.setRequiresShift(e.requiresShift());s.setShift(e.getShiftX(),e.getShiftY());s.setSomething(e.getBoundWidth(),e.getBoundHeight());s.setName(37,"fixture");return s;
    }
    static RemasteredAnimationKeyCache cache(RemasteredSpriteResolver r)throws Exception{Field f=RemasteredSpriteResolver.class.getDeclaredField("animationKeyCache");f.setAccessible(true);return (RemasteredAnimationKeyCache)f.get(r);}
    static void same(RemasteredSpriteResolver candidate,FrozenRemasteredSpriteResolver old,AnimationDef a,int frame,Sprite canonical){
        Sprite next=candidate.resolve(a,frame,canonical),before=old.resolve(a,frame,canonical);
        check((next==canonical)==(before==canonical),"fallback/override identity differs");check((next==null)==(before==null),"null result differs");
        if(next!=null){check(Arrays.equals(next.getPixels(),before.getPixels()),"pixel result differs");check(next.getWidth()==before.getWidth()&&next.getHeight()==before.getHeight()&&next.requiresShift()==before.requiresShift()&&next.getXShift()==before.getXShift()&&next.getYShift()==before.getYShift()&&next.getSomething1()==before.getSomething1()&&next.getSomething2()==before.getSomething2(),"placement differs");}
        check(candidate.diagnostics().equals(old.diagnostics()),"selection/fallback/decode counters differ");check(candidate.loadedKeys().equals(old.loadedKeys()),"loaded key set differs");
    }
    public static void main(String[] args)throws Exception{
        boolean available=Boolean.parseBoolean(args[0]);check(RemasteredSpriteSettings.isAvailable()==available,"isolated availability selection");RemasteredSpriteSettings.setEnabled(false);
        Sprite canonical=canonical();int[] untouched=canonical.getPixels().clone();CountingAnimation animation=new CountingAnimation();RemasteredSpriteResolver fast=new RemasteredSpriteResolver();
        check(fast.resolve(animation,0,null)==null,"null canonical semantics");check(animation.reads==0&&fast.diagnostics().contains("fallbackDisabled=0"),"null canonical performed lookup/counting");
        for(int n=0;n<1000;n++)check(fast.resolve(animation,n%300,canonical)==canonical,"disabled changed canonical identity");
        check(animation.reads==0,"disabled resolver inspected mutable animation name");check(cache(fast).definitionCount()==0&&cache(fast).cachedSlotCount()==0,"disabled path retained keys");check(fast.diagnostics().contains("fallbackDisabled=1000")&&fast.diagnostics().contains("decodes=0"),"disabled counters changed");
        check(fast.resolve((AnimationDef)null,-1,canonical)==canonical,"disabled null/negative fallback");check(Arrays.equals(untouched,canonical.getPixels()),"canonical pixels changed");
        RemasteredSpriteResolver candidate=new RemasteredSpriteResolver();FrozenRemasteredSpriteResolver old=new FrozenRemasteredSpriteResolver();
        for(int n=0;n<30;n++)same(candidate,old,animation,n%19,n%4==0?null:canonical);
        RemasteredSpriteSettings.setEnabled(true);check(RemasteredSpriteSettings.isEnabled()==available,"production owner pause changed");
        same(candidate,old,animation,0,canonical);
        if(available){check(candidate.loadedKeys().contains("sprite/npc/demon/0"),"enabled actual PNG did not decode");check(candidate.diagnostics().contains("decodes=1"),"positive decode not counted");}
        Sprite incompatible=canonical();incompatible.setShift(incompatible.getXShift()+1,incompatible.getYShift());same(candidate,old,animation,0,incompatible);
        for(int n=0;n<100;n++){
            if(n%9==0)RemasteredSpriteSettings.setEnabled(n%18==0);
            animation.name=new String[]{"demon","legs1","unknown"," bad key ",null}[n%5];animation.category=new String[]{"npc","players"," NPC ",null}[n%4];
            same(candidate,old,n%17==0?null:animation,new int[]{-1,0,17,255,256}[n%5],n%13==0?null:canonical);
        }
        RemasteredSpriteSettings.setEnabled(false);int before=animation.reads;animation.name="body1";animation.category="players";same(candidate,old,animation,0,canonical);
        // Frozen resolver intentionally reads the key while disabled; only candidate avoids it.
        check(animation.reads>before,"frozen disabled resolver did not inspect the definition");
        RemasteredSpriteSettings.setEnabled(true);animation.name="demon";animation.category="npc";same(candidate,old,animation,0,canonical);same(candidate,old,null,0,canonical);same(candidate,old,animation,-1,canonical);same(candidate,old,animation,0,null);
        System.out.println("PASS "+checks+" actual resolver disabled/null/enabled/mutable-key/PNG/counter differential checks; availability="+available);
    }
}
