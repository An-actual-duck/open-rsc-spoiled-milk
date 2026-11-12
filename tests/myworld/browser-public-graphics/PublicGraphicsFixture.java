package orsc;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.*;
import org.json.*;
import orsc.graphics.three.*;

/** Real settings/menu/packing/uniform APIs against a complete source-bound JAR. No GL. */
public final class PublicGraphicsFixture {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static Object stored(Class<?> c)throws Exception{Field f=c.getDeclaredField("mode");f.setAccessible(true);return f.get(null);}
 static void load(Properties p){RendererFogSettings.loadFromClientSettings(p);RendererLightingSettings.loadFromClientSettings(p);RendererGeometrySettings.loadFromClientSettings(p);}
 static void save(Properties p){RendererFogSettings.saveToClientSettings(p);RendererLightingSettings.saveToClientSettings(p);RendererGeometrySettings.saveToClientSettings(p);}
 static final class View implements RendererSettingsPanel.View {
  int selected;public void clearRows(){}public void setRow(int i,String text,int action){}public void drawString(String s,int x,int y,int c,int f){}public int stringWidth(int font,String s){return s.length();}public int scrollAmount(){return 0;}public int selectedAction(){return selected;}public void drawPanel(){}
 }
 static final class Actions implements RendererSettingsPanel.Actions {RendererSettingsPanel.Action last;public void perform(RendererSettingsPanel.Action a){last=a;}public void rendererTuningChanged(String n,int l,float v){} }
 static Renderer3DFrame frame()throws Exception{
  for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors())if(c.getParameterTypes().length==16){c.setAccessible(true);Object[] a=new Object[16];Arrays.fill(a,0);a[15]=Array.newInstance(Class.forName("orsc.graphics.three.Renderer3DTextureData"),0);return(Renderer3DFrame)c.newInstance(a);}throw new AssertionError("frame constructor");
 }
 public static void main(String[] args)throws Exception{
  boolean publicMode=BrowserPublicGraphicsPreset.isEnabled();
  Renderer3DSettings.setBrowserPresentationAvailable(true);
  OpenGLWorldChunkRenderer packing=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
  int[] catalogue=new int[4];JSONArray words=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8")).getJSONArray("settings");for(int i=0;i<4;i++)catalogue[i]=words.getInt(i);
  check(Arrays.equals(catalogue,new int[]{0,0,1,0}),"Actual accepted catalogue packing tuple");
  Method uniforms=BrowserWebGLBridge.class.getDeclaredMethod("uniforms",Renderer3DFrame.class);uniforms.setAccessible(true);Renderer3DFrame frame=frame();
  for(RendererFogSettings.Mode fog:RendererFogSettings.Mode.values())for(RendererLightingSettings.Mode light:RendererLightingSettings.Mode.values())for(RendererGeometrySettings.Mode geometry:RendererGeometrySettings.Mode.values()){
   Properties p=new Properties();p.setProperty(RendererFogSettings.FOG_PROPERTY_KEY,fog.id);p.setProperty("opengl_fog_strength","old-fog-alias");p.setProperty(RendererLightingSettings.LIGHTING_PROPERTY_KEY,light.id);p.setProperty(RendererGeometrySettings.GEOMETRY_PROPERTY_KEY,geometry.id);p.setProperty("unrelated","preserve");load(p);
   check(stored(RendererFogSettings.class)==fog&&stored(RendererLightingSettings.class)==light&&stored(RendererGeometrySettings.class)==geometry,"Stored preferences remain parsed independently");
   check(RendererFogSettings.getMode()==(publicMode?RendererFogSettings.Mode.ON:fog)&&RendererLightingSettings.getMode()==(publicMode?RendererLightingSettings.Mode.DIRECTIONAL:light)&&RendererGeometrySettings.getMode()==(publicMode?RendererGeometrySettings.Mode.SMOOTH:geometry),"Effective public/private getters");
   Properties before=(Properties)p.clone();save(p);
   if(publicMode){
    check(p.equals(before),"Public save does not overwrite or migrate existing preference keys");Properties absent=new Properties();save(absent);check(absent.isEmpty(),"Public preset does not invent persisted defaults");
    final Properties source=(Properties)before.clone();final Properties[] persisted={null};
    RendererProfileApplier.persist(new RendererProfileApplier.SettingsStore(){public Properties load(){return(Properties)source.clone();}public void save(Properties value){persisted[0]=(Properties)value.clone();}},RendererProfileApplier.Change.FOG,RendererProfileApplier.Change.LIGHTING,RendererProfileApplier.Change.GEOMETRY,RendererProfileApplier.Change.TUNING);
    for(String key:new String[]{RendererFogSettings.FOG_PROPERTY_KEY,"opengl_fog_strength",RendererLightingSettings.LIGHTING_PROPERTY_KEY,RendererGeometrySettings.GEOMETRY_PROPERTY_KEY,"unrelated"})check(source.getProperty(key).equals(persisted[0].getProperty(key)),"Actual aggregate persistence preserves key "+key);
    RendererFogSettings.cycleMode();RendererLightingSettings.cycleMode();RendererGeometrySettings.cycleMode();RendererGeometrySettings.cycleBrowserMode();RendererFogSettings.setMode(RendererFogSettings.Mode.OFF);RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.WIRE);
    check(stored(RendererFogSettings.class)==fog&&stored(RendererLightingSettings.class)==light&&stored(RendererGeometrySettings.class)==geometry,"Disabled setters/cycles preserve original preferences");
    check(Arrays.equals(packing.backendPackingSettings(),catalogue),"Actual backend packing matches strict catalogue for every saved mode combination");
    float[] u=(float[])uniforms.invoke(null,frame);check(u[27]==1&&u[28]==1,"Actual shader lighting/fog flags use effective preset");
   }else{
    check(p.getProperty(RendererFogSettings.FOG_PROPERTY_KEY).equals(fog.id)&&p.getProperty("opengl_fog_strength")==null&&p.getProperty(RendererLightingSettings.LIGHTING_PROPERTY_KEY).equals(light.id)&&p.getProperty(RendererGeometrySettings.GEOMETRY_PROPERTY_KEY).equals(geometry.id),"Private/native save and legacy normalization unchanged");
   }
  }
  RendererSettingsPanel.State state=RendererSettingsPanel.State.capture(false,ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,1f,Arrays.asList(1f,2f),true,false,true,false);
  int fixed=0,editable=0;boolean explanation=false;
  for(RendererSettingsPanel.Row row:RendererSettingsPanel.rows(state)){
   if(row.label.contains("Browser preset (fixed)"))explanation=true;
   if(row.label.contains("Lighting -")||row.label.contains("Geometry -")||row.label.contains("Fog -")){if(row.action==RendererSettingsPanel.SECTION_ROW)fixed++;else editable++;}
  }
  check(publicMode?fixed==3&&editable==0&&explanation:editable==3&&fixed==0&&!explanation,"Actual menu exposes three truthful fixed or editable rows");
  RendererSettingsPanel panel=new RendererSettingsPanel();View view=new View();Actions actions=new Actions();
  for(int action:new int[]{60,61,62}){view.selected=action;actions.last=null;boolean handled=panel.handleSelectedAction(view,state,new RendererSettingsPanel.Input(960,0,0,1,0,false),0,0,actions);check(publicMode?!handled&&actions.last==null:handled&&actions.last!=null,"Direct/stale action dispatch respects public capability");}
  if(publicMode){
   RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.LOW);RendererColorDiagnosticSettings.setDimnessLevel(1);float[] low=(float[])uniforms.invoke(null,frame);
   RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);RendererColorDiagnosticSettings.setDimnessLevel(20);float[] high=(float[])uniforms.invoke(null,frame);
   check(low[0]!=high[0],"Brightness/dimness remains live in actual shader uniforms");check(Arrays.equals(packing.backendPackingSettings(),catalogue),"Brightness changes do not alter unbaked canonical packing");
  }else{
   RendererFogSettings.setMode(RendererFogSettings.Mode.OFF);check(RendererFogSettings.cycleMode()==RendererFogSettings.Mode.ON,"Private fog cycle unchanged");RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);check(RendererLightingSettings.cycleMode()==RendererLightingSettings.Mode.DIRECTIONAL,"Private lighting cycle unchanged");RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);check(RendererGeometrySettings.cycleMode()==RendererGeometrySettings.Mode.FACETED,"Private geometry cycle unchanged");
  }
  System.out.println("PASS checks="+checks+" public="+publicMode+" canonical="+Arrays.toString(catalogue)+" no-GPU=true");
 }
}
