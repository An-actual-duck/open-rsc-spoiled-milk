package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.NPCDef;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.model.Sprite;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import orsc.enumerations.ORSCharacterDirection;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.*;
import orsc.graphics.two.*;
import orsc.graphics.two.SpriteArchive.*;
/** Actual nonempty NPC body path, actual software raster and production GPU compositor. */
public final class BrowserNpcLayerPreparationFixture {
 static int cases,commands,nonempty;static long nativeLookups,gpuLookups;static MessageDigest hash;
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void set(Object o,String name,Object value)throws Exception{Field f=mudclient.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static void fingerprint(Object value)throws Exception{
  if(value==null){hash.update((byte)0);return;}Class<?> type=value.getClass();hash.update(type.getName().getBytes("UTF-8"));
  if(value instanceof Number||value instanceof Boolean||value instanceof String||type.isEnum()){hash.update(String.valueOf(value).getBytes("UTF-8"));hash.update((byte)0);return;}
  if(type.isArray()){for(int i=0;i<Array.getLength(value);i++)fingerprint(Array.get(value,i));return;}
  for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);hash.update(f.getName().getBytes("UTF-8"));fingerprint(f.get(value));}
 }
 static long lookups(MudClientGraphics g)throws Exception{
  Field f=GraphicsController.class.getDeclaredField("remasteredSpriteResolver");f.setAccessible(true);Object resolver=f.get(g);Field count=resolver.getClass().getDeclaredField("disabledFallbackCount");count.setAccessible(true);return count.getLong(resolver);
 }
 static Renderer3DFrame frame()throws Exception{
  Constructor<?> ctor=null;for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors())if(c.getParameterCount()==16)ctor=c;
  check(ctor!=null,"frame constructor");ctor.setAccessible(true);Object[] args=new Object[16];Arrays.fill(args,0);args[3]=96;args[4]=96;args[15]=Array.newInstance(Class.forName("orsc.graphics.three.Renderer3DTextureData"),0);
  Renderer3DFrame f=(Renderer3DFrame)ctor.newInstance(args);Method anchor=null;for(Method m:Renderer3DFrame.class.getDeclaredMethods())if(m.getName().equals("addSpriteAnchor"))anchor=m;
  anchor.setAccessible(true);anchor.invoke(f,0,20000,5,0,123,0,0,123,20,20,16,16,40,48,100,3,true);return f;
 }
 static Sprite sprite(int id,int frame){
  int w=6+frame%2,h=7;int[] pixels=new int[w*h];for(int n=0;n<pixels.length;n++)pixels[n]=n%5==0?0:n%5==1?0xff000000:n%5==2?0x888888:n%5==3?0xff8888:((id+1)*0x123457+frame*0x10203)&0xffffff;
  Sprite s=new Sprite(pixels,w,h);s.setSomething(8+frame%2,9);s.setRequiresShift(frame%2==0);s.setShift(1,1);return s;
 }
 public static void main(String[] args)throws Exception{
  hash=MessageDigest.getInstance("SHA-256");
  Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);mudclient mc=(mudclient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(mudclient.class);
  MudClientGraphics g=new MudClientGraphics(96,96,256);g.sprites=new Sprite[256];g.mudClientRef=mc;mc.setSurface(g);
  set(mc,"animFrameToSprite_Walk",new int[]{0,1,2,1});set(mc,"animFrameToSprite_CombatA",new int[]{0,1,2,1,0,0,0,0});set(mc,"animFrameToSprite_CombatB",new int[]{0,0,0,0,0,1,2,1});
  int[][] order=new int[8][12];for(int d=0;d<8;d++)for(int l=0;l<12;l++)order[d][l]=(l+d)%12;set(mc,"animDirLayer_To_CharLayer",order);
  set(mc,"floatingHitSplatRenderer",new FloatingHitSplatRenderer());set(mc,"combatSpriteVisibleBoundsCache",new IdentityHashMap<Sprite,int[][]>());
  ORSCharacter npc=new ORSCharacter();npc.npcId=0;set(mc,"npcs",new ORSCharacter[]{npc});
  Field definitions=EntityHandler.class.getDeclaredField("animations");definitions.setAccessible(true);List<AnimationDef> animations=(List<AnimationDef>)definitions.get(null);animations.clear();
  Map<String,Entry> sheets=new HashMap<String,Entry>();g.spriteTree.put("fixture",sheets);
  for(int id=0;id<4;id++){
   AnimationDef def=new AnimationDef("layer"+id,"fixture",id,0,true,id%2==0,id*40);animations.add(def);Entry entry=new Entry(def.name,Entry.TYPE.NPC,orsc.graphics.two.SpriteArchive.Frame.LAYER.values()[0],40);sheets.put(def.name,entry);
   for(int f=0;f<40;f++){Sprite s=sprite(id,f);g.sprites[id*40+f]=s;orsc.graphics.two.SpriteArchive.Frame cell=new orsc.graphics.two.SpriteArchive.Frame(s.getWidth(),s.getHeight(),s.requiresShift(),s.getXShift(),s.getYShift(),s.getSomething1(),s.getSomething2());System.arraycopy(s.getPixels(),0,cell.getPixels(),0,s.getPixels().length);entry.getFrames()[f]=cell;}
  }
  while(animations.size()<=230)animations.add(animations.get(0));animations.set(230,new AnimationDef("layer3","fixture",2,0,true,true,120));
  NPCDef def=new NPCDef("fixture npc","fixture","Talk",1,1,10,1,true,new int[12],0x443322,0x556677,0x998866,0xc0a080,0,0,1,5,10,0);EntityHandler.npcs.clear();EntityHandler.npcs.add(def);
  for(int backend=0;backend<3;backend++)for(boolean custom:new boolean[]{false,true})for(ORSCharacterDirection direction:ORSCharacterDirection.values())for(int opacity=0;opacity<3;opacity++)for(int wield=0;wield<3;wield++){
   boolean gpu=backend==1,browser=backend>0;Config.S_WANT_CUSTOM_SPRITES=custom;Config.S_WANT_CUSTOM_LANDSCAPE=true;
   Renderer3DSettings.setBrowserPresentationAvailable(browser);npc.direction=direction;npc.nonCorporeal=opacity==1;npc.spiritSummon=opacity==2;npc.wield=wield==0?0:2;npc.wield2=wield==2?3:0;npc.stepFrame=cases%12;mc.cameraRotation=(cases%8)*32;set(mc,"frameCounter",cases);
   for(int layer=0;layer<12;layer++)def.sprites[layer]=layer%3==0?230:layer%4;
   animations.set(1,new AnimationDef("layer1","fixture",cases%4,0,cases%5!=0,cases%2==0,40));
   // Mutation between invocations must always affect the new draw (no retained preparation).
   def.skinColour=0x80a0c0^(cases*127);if(cases%23==0)g.sprites[0].setPixels(sprite(0,0).getPixels());g.sprites[0].getPixels()[2]^=0x10001;g.sprites[0].setSomething(8+cases%2,9);
   Sprite mutable=sheets.get("layer0").getFrames()[0].getSprite();mutable.getPixels()[2]^=0x10001;mutable.setSomething(8+cases%2,9);
   g.beginRenderer2DFrame();g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);g.setWorldOverlayCoverageEnabled(browser);if(gpu)g.beginTransparentWorldOverlay();else{g.beginOpaqueWorldOverlay();Arrays.fill(g.pixelData,0x23262c);}
   long lookupStart=lookups(g);Renderer3DFrame f=frame();g.drawSceneEntity(20000,16,16,40,48,100,cases%3-1,5,0,0,f,gpu?GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED:GraphicsController.SceneSpriteRasterPolicy.FORCE_SOFTWARE);
   if(gpu)gpuLookups+=lookups(g)-lookupStart;else nativeLookups+=lookups(g)-lookupStart;
   check(g.getSceneSpriteRasterFailure().isEmpty(),"actual actor ownership failed");Renderer2DFrame capture=g.consumeRenderer2DFrame();Renderer2DFrame.SpriteCommand[] layers=capture.getSpriteCommands();check(!gpu&&opacity>0||layers.length>0,"empty body oracle case="+cases+" gpu="+gpu+" custom="+custom+" direction="+direction+" opacity="+opacity+" wield="+wield);commands+=layers.length;
   fingerprint(capture);fingerprint(g.pixelData);
   CompositeWorldSpriteTexture composite=OpenGLSpriteTextureBuilder.buildCompositeCharacterSpriteTexture(Arrays.asList(layers));
   if(composite!=null){ByteBuffer rgba=composite.textureData.pixels();byte[] bytes=new byte[rgba.remaining()];rgba.get(bytes);fingerprint(bytes);fingerprint(new int[]{composite.x,composite.y,composite.width,composite.height});}else{check(!gpu,"empty GPU composite");fingerprint(null);}
   if(!gpu)for(int pixel:g.pixelData)if(pixel!=0x23262c){nonempty++;break;}
   f.release();cases++;
  }
  // Actual malformed inputs must still throw and scoped capture must restore ownership.
  for(int error=0;error<3;error++){
   npc.direction=error==2?ORSCharacterDirection.NORTH_EAST:ORSCharacterDirection.NORTH;mc.cameraRotation=0;npc.wield=2;npc.wield2=3;def.walkModel=error==0?0:1;order[0][0]=error==1?99:0;
   if(error==2){animations.set(0,null);Arrays.fill(def.sprites,0);npc.wield=0;npc.wield2=0;}
   g.beginRenderer2DFrame();g.setWorldOverlayCoverageEnabled(true);g.beginTransparentWorldOverlay();g.setRenderer2DPhase(Renderer2DFrame.Phase.SCENE);Renderer3DFrame f=frame();
   boolean failed=false;java.io.PrintStream stderr=System.err;try{System.setErr(new java.io.PrintStream(new java.io.ByteArrayOutputStream()));g.drawSceneEntity(20000,16,16,40,48,100,0,5,0,0,f,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);}catch(RuntimeException expected){failed=true;for(Throwable t=expected;t!=null;t=t.getCause())fingerprint(t.getClass().getName()+":"+t.getMessage());}finally{System.setErr(stderr);f.release();}
   check(failed,"malformed NPC stopped throwing");check(!g.isGpuOwnedSceneSpriteCaptureActive(),"exception leaked actor ownership");
  }
  check(nonempty>0&&commands>cases,"body/duplicate layers not exercised");StringBuilder result=new StringBuilder();for(byte b:hash.digest())result.append(String.format("%02x",b&255));System.out.println(cases+":"+commands+":"+nonempty+":"+result+":"+nativeLookups+":"+gpuLookups);
 }
}
