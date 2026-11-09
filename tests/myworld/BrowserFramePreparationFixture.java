package orsc.graphics.two;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;
import java.util.Comparator;
import orsc.Config;
import orsc.BrowserTelemetryData;
import orsc.RenderTelemetry;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DSettings;
/** Actual software/coverage writers and actual actor snapshot; no GPU fixture. */
public final class BrowserFramePreparationFixture {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void add(MessageDigest hash,int value){hash.update(ByteBuffer.allocate(4).putInt(value).array());}
 static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
 static int commands(Renderer2DFrame f){return f.getSpriteCommands().length+f.getTextCommands().length+f.getPrimitiveCommands().length+f.getRotatedSpriteCommands().length+f.getCircleCommands().length;}
 // Canonical complete frame DTO, including source sprite pixels and capture stats.
 // Object identity / JVM hash codes never enter the comparison.
 static void fingerprint(MessageDigest hash,Object value)throws Exception {
  if(value==null){hash.update((byte)0);return;}
  Class<?> type=value.getClass();hash.update(type.getName().getBytes("UTF-8"));
  if(value instanceof Number||value instanceof Boolean||value instanceof Character||value instanceof String||type.isEnum()){
   hash.update(String.valueOf(value).getBytes("UTF-8"));hash.update((byte)0);return;
  }
  if(type.isArray()){int size=Array.getLength(value);add(hash,size);for(int i=0;i<size;i++)fingerprint(hash,Array.get(value,i));return;}
  for(Class<?> current=type;current!=Object.class;current=current.getSuperclass()){
   Field[] fields=current.getDeclaredFields();Arrays.sort(fields,Comparator.comparing(Field::getName));
   for(Field field:fields)if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);hash.update(field.getName().getBytes("UTF-8"));fingerprint(hash,field.get(value));}
  }
 }
 static void font(){int n=Fonts.inputFilterChars.length()*9,at=n;byte[] data=new byte[n+6];for(int i=0;i<n;i+=9){data[i]=(byte)(at>>14);data[i+1]=(byte)(at>>7&127);data[i+2]=(byte)(at&127);data[i+3]=2;data[i+4]=3;data[i+6]=3;data[i+7]=3;data[i+8]=5;}for(int i=n;i<data.length;i++)data[i]=1;for(int i=0;i<Fonts.fontData.length;i++)Fonts.fontData[i]=data;}
 static GraphicsController start(String mode,Renderer2DFrame.Phase phase){GraphicsController g=WorldOverlayCoverageFixture.graphics();g.beginRenderer2DFrame();if(mode.equals("browser")||mode.equals("native-coverage")||mode.equals("login")){g.setWorldOverlayCoverageEnabled(true);check(g.beginTransparentWorldOverlay(),"coverage begin");}if(mode.equals("login"))g.beginSoftwareOwnedRenderer2DFrame();g.setRenderer2DPhase(phase);return g;}
 public static void main(String[] args)throws Exception {
  if(args[0].equals("telemetry-off")){
   check(!RenderTelemetry.isEnabled()&&RenderTelemetry.now()==0L&&RenderTelemetry.elapsedSince(1L)==0L,"disabled timers must be no-ops");
   Method record=RenderTelemetry.class.getDeclaredMethod("recordClientDrawPreparation",long.class,long.class,long.class,long.class);
   record.setAccessible(true);record.invoke(null,1L,2L,3L,4L);RenderTelemetry.recordRenderer2DFrameSnapshot(5L);
   for(String name:new String[]{"clientDrawBeforeSceneStats","clientDrawResidentObjectsStats","clientDrawWorldOverlayStats","clientDrawUiOverlayStats","renderer2DFrameSnapshotStats"}){
    Field field=RenderTelemetry.class.getDeclaredField(name);field.setAccessible(true);Object stats=field.get(null);
    for(String counter:new String[]{"count","total"}){Field c=stats.getClass().getDeclaredField(counter);c.setAccessible(true);check(c.getLong(stats)==0L,"disabled telemetry recorded "+name);}
   }
   System.out.println("PASS disabled telemetry clocks and counters remain untouched");return;
  }
  Config.S_WANT_CUSTOM_SPRITES=true;font();String mode=args[0];boolean browser=mode.equals("browser")||mode.equals("scratch")||mode.equals("login");Renderer3DSettings.setBrowserPresentationAvailable(browser);Renderer3DSettings.setOpenGLPresentationAvailable(mode.startsWith("native"));
  MessageDigest pixels=MessageDigest.getInstance("SHA-256"),coverage=MessageDigest.getInstance("SHA-256"),commands=MessageDigest.getInstance("SHA-256");int commandCount=0;
  for(boolean interlace:new boolean[]{false,true})for(boolean clip:new boolean[]{false,true})for(WorldOverlayCoverageFixture.Draw op:WorldOverlayCoverageFixture.operations()){
   GraphicsController g=start(mode,Renderer2DFrame.Phase.UI_OVERLAY);g.interlace=interlace;if(clip)g.setClip(2,WorldOverlayCoverageFixture.W-3,WorldOverlayCoverageFixture.H-2,1);int[] base=WorldOverlayCoverageFixture.background(3);System.arraycopy(base,0,g.pixelData,0,base.length);op.run(g);for(int v:g.pixelData)add(pixels,v);WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();if(f!=null){check(f.valid,"writer invalidated coverage");add(coverage,f.mode);if(f.transmittance!=null)for(float t:f.transmittance)add(coverage,Float.floatToIntBits(t));}Renderer2DFrame consumed=g.consumeRenderer2DFrame();commandCount+=commands(consumed);fingerprint(commands,consumed);
  }
  for(Renderer2DFrame.Phase phase:new Renderer2DFrame.Phase[]{Renderer2DFrame.Phase.UI_OVERLAY,Renderer2DFrame.Phase.WORLD_OVERLAY}){
   GraphicsController g=start(mode,phase);g.captureRenderer2DUiBaseFrame();g.drawBox(2,2,6,7,0);g.drawBoxAlpha(4,4,8,8,0xabcdef,128);g.drawSprite(WorldOverlayCoverageFixture.sprite(false),10,11);g.drawColoredString(4,25,"A@red@Bc",0,0xffffff,0);g.drawMinimapSprite(WorldOverlayCoverageFixture.sprite(true),14,18,0,128,20);g.drawCircle(20,18,4,0xff0000,128,-1057205208);for(int v:g.pixelData)add(pixels,v);WorldOverlayCoverage.Frame f=g.borrowWorldOverlayCoverage();if(f!=null&&f.transmittance!=null)for(float t:f.transmittance)add(coverage,Float.floatToIntBits(t));Renderer2DFrame consumed=g.consumeRenderer2DFrame();commandCount+=commands(consumed);fingerprint(commands,consumed);
  }
  // Retained frames preserve RGB/T/mode across consume; no fresh-world clear.
  GraphicsController retained=start(mode,Renderer2DFrame.Phase.UI_OVERLAY);retained.drawBoxAlpha(1,1,6,6,0xabcdef,128);retained.consumeRenderer2DFrame();retained.beginRenderer2DFrame();retained.fade2black(16316665);retained.drawBox(3,3,4,4,0);WorldOverlayCoverage.Frame held=retained.borrowWorldOverlayCoverage();for(int v:retained.pixelData)add(pixels,v);if(held!=null){add(coverage,held.mode);add(coverage,held.retainedFadeCount);if(held.transmittance!=null)for(float t:held.transmittance)add(coverage,Float.floatToIntBits(t));}
  // Scoped scene actor capture remains fully accepted, including masks/skew.
  SceneSpriteRasterPolicyFixture.Actor actor=new SceneSpriteRasterPolicyFixture.Actor();actor.mirror=true;actor.skew=3;Renderer3DFrame frame=SceneSpriteRasterPolicyFixture.frame();SceneSpriteRasterPolicyFixture.start(actor,Renderer2DFrame.Phase.SCENE,true);SceneSpriteRasterPolicyFixture.draw(actor,frame,GraphicsController.SceneSpriteRasterPolicy.GPU_OWNED);String scene=SceneSpriteRasterPolicyFixture.commandFingerprint(frame);check(frame.getWorldSpriteSnapshot(0).getLayerCount()==2,"scene layers lost");check(actor.getSceneSpriteRasterFailure().isEmpty(),"scene ownership failed");int sceneCount=actor.consumeRenderer2DFrame().getSpriteCommands().length;check(sceneCount==2,"scene stream changed");
  String[] names=BrowserTelemetryData.names().split("\\|");check(names.length<=32,"telemetry schema bound");Method record=RenderTelemetry.class.getDeclaredMethod("recordClientDrawPreparation",long.class,long.class,long.class,long.class);record.setAccessible(true);record.invoke(null,1L,2L,3L,4L);double[] data=BrowserTelemetryData.snapshot();for(String name:new String[]{"clientDrawBeforeSceneStats","clientDrawResidentObjectsStats","clientDrawWorldOverlayStats","clientDrawUiOverlayStats","renderer2DFrameSnapshotStats"}){int at=Arrays.asList(names).indexOf(name);check(at>=0&&data[at*2]>0,"missing measured stage "+name);}
  System.out.println("{\"mode\":\""+mode+"\",\"rgb\":\""+hex(pixels.digest())+"\",\"coverage\":\""+hex(coverage.digest())+"\",\"scene\":\""+hex(MessageDigest.getInstance("SHA-256").digest(scene.getBytes("UTF-8")))+"\",\"commands\":"+commandCount+",\"commandFingerprint\":\""+hex(commands.digest())+"\",\"sceneCommands\":"+sceneCount+"}");
 }
}
