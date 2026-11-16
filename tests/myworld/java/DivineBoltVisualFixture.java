package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.model.Sprite;
import orsc.graphics.two.*;
import orsc.graphics.three.Scene;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Packaged artwork, actual flight renderer and camera projection, without a server. */
public final class DivineBoltVisualFixture {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Method method(String n,Class<?>...types)throws Exception{Method m=mudclient.class.getDeclaredMethod(n,types);m.setAccessible(true);return m;}
    static void export(Sprite s,Path path)throws Exception{
        BufferedImage im=new BufferedImage(s.getWidth(),s.getHeight(),BufferedImage.TYPE_INT_ARGB);
        int[] pixels=s.getPixels().clone();for(int i=0;i<pixels.length;i++)pixels[i]=(pixels[i]&0xffffff)==0xff00ff||pixels[i]==0?0:0xff000000|(pixels[i]&0xffffff);
        im.setRGB(0,0,s.getWidth(),s.getHeight(),pixels,0,s.getWidth());ImageIO.write(im,"png",path.toFile());
    }
    public static void main(String[]args)throws Exception{
        Path out=Paths.get(args[0]);Files.createDirectories(out);
        Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);
        Config.F_CACHE_DIR=Paths.get(mudclient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().resolve("Cache").toString();EntityHandler.load(true);
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        mudclient c=(mudclient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(mudclient.class);
        MudClientGraphics g=new MudClientGraphics(640,300,5000);c.setSurface(g);
        Scene scene=new Scene(g,16,32,128);set(c,"scene",scene);
        set(scene,"m_Zb",320);set(scene,"m_Nb",150);set(scene,"rot1024_vp_src",9);
        for(String f:new String[]{"cameraProjX","cameraProjY","cameraProjZ","rot1024_off_x","rot1024_off_y","rot1024_off_z"})set(scene,f,0);
        // This path must work even when no GPU geometry frame exists.
        check(scene.getRenderer3DFrame()==null,"software-only scene fixture");
        int[] p=new int[3];check(scene.projectWorldPoint(80,-40,512,p)&&p[0]==400&&p[1]==110&&p[2]==512,"camera projection");
        check(!scene.projectWorldPoint(0,0,0,p),"near plane rejected");
        ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Files.createTempDirectory("divine-bolt-packaged-"),mudclient.class);
        set(c,"externalAssetLoader",loader);
        Sprite[][] sprites=new Sprite[39][64];set(c,"projectileEffectSprites",sprites);
        for(String f:new String[]{"boltCasterProjection","boltVictimProjection","boltTravelProjection"})set(c,f,new int[3]);
        Method load=method("loadProjectileAnimationSheet",ProjectileAnimationCatalog.Definition.class,Sprite[].class);
        Method draw=method("drawDirectedBolt",int.class,int.class,int.class,ORSCharacter.class,ORSCharacter.class,int.class,int.class);
        int[] ids={20,43,44,45};String[] names={"basic","saradomin","zamorak","guthix"};
        for(int i=0;i<ids.length;i++){
            ProjectileAnimationCatalog.Definition d=ProjectileAnimationCatalog.getProjectileFallback(ids[i]);
            int count=(Integer)load.invoke(c,d,sprites[ids[i]-7]);
            check(count==(i==0?1:6),"actual packaged native bolt loader "+ids[i]);
            for(int f=0;f<count;f++){
                Sprite s=sprites[ids[i]-7][f];check(s.getWidth()==96&&s.getHeight()==96,"native pivot and phase canvas retained");
                export(s,out.resolve(names[i]+"-"+f+".png"));
            }
            ORSCharacter caster=new ORSCharacter(),target=new ORSCharacter();caster.currentZ=target.currentZ=512;
            int[][] directions={{100,0},{100,70},{0,70},{-100,70},{-100,0},{-100,-70},{0,-70},{100,-70}};
            for(int a=0;a<directions.length;a++){
                target.currentX=directions[a][0];int y=directions[a][1];
                for(int f=0;f<count;f++){
                    Arrays.fill(g.pixelData,0x202830);
                    draw.invoke(c,ids[i],f,20,caster,target,0,y);
                    int changed=0;for(int rgb:g.pixelData)if(rgb!=0x202830)changed++;
                    check(changed>8,"actual directed flight visible at angle "+a);
                    boolean preserved=true;for(int rgb:g.pixelData)if(rgb==0){preserved=false;break;}
                    check(preserved,"transparent bolt canvas must preserve background at angle "+a);
                }
            }
        }
        // Verify the real queue routes basic/holy bolts without requiring actor
        // screen anchors, and retains the exact caller-supplied projectile heights.
        int[] frameCounts=new int[39];for(int id:ids)frameCounts[id-7]=id==20?1:6;
        set(c,"projectileEffectFrameCounts",frameCounts);
        set(c,"queuedStaticProjectileCaster",new ORSCharacter[32]);set(c,"queuedStaticProjectileVictim",new ORSCharacter[32]);
        for(String field:new String[]{"queuedStaticProjectileId","queuedStaticProjectileFrame","queuedStaticProjectileRange","queuedStaticProjectileCasterY","queuedStaticProjectileVictimY"})set(c,field,new int[32]);
        Method queue=method("queueStaticProjectile",com.openrsc.client.entityhandling.defs.SpriteDef.class,ORSCharacter.class,ORSCharacter.class,int.class,int.class,int.class);
        ORSCharacter caster=new ORSCharacter(),target=new ORSCharacter();caster.currentZ=target.currentZ=512;target.currentX=100;
        for(int id:ids)check((Boolean)queue.invoke(c,EntityHandler.projectiles.get(id),caster,target,20,0,20),"basic and holy bolts enter directed queue");
        method("drawQueuedStaticProjectiles").invoke(c);
        // The real rotated-sprite raster must put the bolt's head on the target side.
        int[] markerPixels=new int[9*5];for(int y=1;y<4;y++)for(int x=1;x<8;x++)markerPixels[y*9+x]=x<3?0x0000ff:x>5?0xff0000:0x00ff00;
        Sprite marker=new Sprite(markerPixels,9,5);marker.setSomething(9,5);marker.setRequiresShift(true);
        for(int a=0;a<8;a++){
            int dx=(int)Math.round(Math.cos(a*Math.PI/4)*100),dy=(int)Math.round(Math.sin(a*Math.PI/4)*100);
            Arrays.fill(g.pixelData,0);g.drawMinimapSprite(marker,150,320,0,1024,BoltFlightVisuals.angle(dx,dy));
            double redX=0,redY=0,blueX=0,blueY=0;int red=0,blue=0;
            for(int y=0;y<300;y++)for(int x=0;x<640;x++){int v=g.pixelData[y*640+x]&0xffffff;if(v==0xff0000){redX+=x;redY+=y;red++;}if(v==0x0000ff){blueX+=x;blueY+=y;blue++;}}
            check(red>0&&blue>0,"both marker ends rasterized at "+a+" red="+red+" blue="+blue);
            double vx=redX/red-blueX/blue,vy=redY/red-blueY/blue;
            check(vx*dx+vy*dy>0&&Math.abs(vx*dy-vy*dx)<200,"bolt points toward target for octant "+a);
        }
        check(BoltFlightVisuals.scale(9,512)==128&&BoltFlightVisuals.scale(9,1024)==64,"perspective zoom scale");
        Method screen=method("getCombatEffectScreenSize",int.class,int.class),world=method("getCombatEffectSceneSize",int.class);
        check((Integer)screen.invoke(c,117,64)==108&&(Integer)world.invoke(c,117)==384,"Revelation four times original");
        check((Integer)screen.invoke(c,118,64)==82&&(Integer)world.invoke(c,118)==288,"bleed exactly doubled");
        check((Integer)screen.invoke(c,119,64)==164&&(Integer)world.invoke(c,119)==576,"poison visible size corrected");
        for(int effect:new int[]{117,118,119}){
            CombatEffectAnimationCatalog.Definition d=CombatEffectAnimationCatalog.getDefinition(effect);Sprite[] frames=new Sprite[64];
            File file=Paths.get("dev/myworld/assets/runtime/animations",d.getCategory(),d.getSheetPath()).toFile();
            int count=loader.appendExternalAnimationGridSheetFrames(file,frames,d.getMaxTargetSize(),d.getColumns(),d.getRows(),d.getFirstFrame(),d.getFrameCount(),0);
            check(count==d.getFrameCount(),"packaged impact frames");for(int f=0;f<count;f++)export(frames[f],out.resolve("effect-"+effect+"-"+f+".png"));
        }
        // Exercise the actual attached, Cleric and detached/world queue paths,
        // rather than checking only a scaling helper in isolation.
        Sprite[][] combat=new Sprite[120][64];int[] combatCounts=new int[120];
        set(c,"combatEffectSprites",combat);set(c,"combatEffectFrameCounts",combatCounts);
        for(int id:new int[]{87,117,118,119}){
            CombatEffectAnimationCatalog.Definition d=CombatEffectAnimationCatalog.getDefinition(id);
            combatCounts[id]=loader.appendExternalAnimationGridSheetFrames(new File("dev/myworld/assets/runtime/animations/"+d.getCategory()+"/"+d.getSheetPath()),combat[id],d.getMaxTargetSize(),d.getColumns(),d.getRows(),d.getFirstFrame(),d.getFrameCount(),0);
        }
        ORSCharacter actor=new ORSCharacter();actor.combatEffectType=119;actor.combatEffectTime=c.getCombatEffectDuration(119)/2;
        Method attached=method("drawCombatEffectOverlay",ORSCharacter.class,int.class,int.class,int.class,int.class);
        for(int h:new int[]{32,64,128,256}){
            attached.invoke(c,actor,150,0,128,h);
            check(actor.combatEffectScreenWidth==AnimationSizing.onEntity(164,h),"actual attached poison follows zoom "+h);
            check(actor.combatEffectScreenX+actor.combatEffectScreenWidth/2==214,"attached center remains stable");
        }
        ClericEffectPlayback cleric=new ClericEffectPlayback(false);cleric.enqueue(87,10);
        Method clericDraw=method("drawClericEffectOverlay",ClericEffectPlayback.class,int.class,int.class,int.class,int.class);
        for(int h:new int[]{64,128,256}){Arrays.fill(g.pixelData,0);clericDraw.invoke(c,cleric,200,0,128,h);int painted=0;for(int pixel:g.pixelData)if(pixel!=0)painted++;check(painted>0,"Cleric effect remains visible at zoom "+h);}
        for(String field:new String[]{"queuedCombatEffectSprite","queuedCombatEffectX","queuedCombatEffectY","queuedCombatEffectWidth","queuedCombatEffectHeight"})set(c,field,new int[512]);
        Method queueEffect=mudclient.class.getMethod("queueCombatEffectOverlay",int.class,int.class,int.class,int.class,int.class);
        int sceneIndex=mudclient.spriteCombatEffectBase+(119-1)*64;
        for(int projected:new int[]{144,288,576})check((Boolean)queueEffect.invoke(c,sceneIndex,20,30,projected,projected),"world/detached effect accepted");
        Field widths=mudclient.class.getDeclaredField("queuedCombatEffectWidth");widths.setAccessible(true);int[] w=(int[])widths.get(c);
        check(Math.abs(w[1]-2*w[0])<=1&&Math.abs(w[2]-2*w[1])<=1,"world/detached zoom remains proportional");
        check(AnimationSizing.onEntity(108,128)==108&&AnimationSizing.onEntity(82,128)==82,"approved reference sizes preserved");
        Sprite[][] impactFrames=new Sprite[39][64];int[] impactCounts=new int[39];int impactId=7;
        impactCounts[impactId-7]=1;impactFrames[impactId-7][0]=marker;
        set(c,"projectileImpactSprites",impactFrames);set(c,"projectileImpactFrameCounts",impactCounts);
        set(c,"queuedProjectileImpactSprite",new Sprite[32]);
        for(String field:new String[]{"queuedProjectileImpactX","queuedProjectileImpactY","queuedProjectileImpactSize"})set(c,field,new int[32]);
        ORSCharacter impacted=new ORSCharacter();impacted.projectileImpactId=impactId;impacted.projectileImpactTime=1;
        Method impact=method("queueProjectileImpactOverlay",ORSCharacter.class,int.class,int.class,int.class,int.class);
        impact.invoke(c,impacted,0,0,64,64);impact.invoke(c,impacted,0,0,128,128);impact.invoke(c,impacted,0,0,256,256);
        Field impactSize=mudclient.class.getDeclaredField("queuedProjectileImpactSize");impactSize.setAccessible(true);int[] sizes=(int[])impactSize.get(c);
        check(sizes[0]>0&&sizes[1]==2*sizes[0]&&sizes[2]==2*sizes[1],"actual projectile impact follows zoom");
        for(String icon:new String[]{"divine-bolts","thorned-bolts","bolts-of-wrath"}){
            Sprite s=loader.loadExternalPrayerIconFile(new File("dev/myworld/assets/runtime/ui/prayer/"+icon+".png"));check(s!=null,"packaged prayer icon "+icon);export(s,out.resolve(icon+".png"));
        }
        System.out.println("PASS "+checks+" packaged icons/native phases, real renderer angles, software projection, zoom and doubled-effect checks; custom="+args[1]);
    }
}
