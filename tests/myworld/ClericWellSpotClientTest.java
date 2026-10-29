package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import com.openrsc.data.DataOperations;
import orsc.Config;
import orsc.graphics.two.MudClientGraphics;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Actual native decoder, Scene mouse-query path and GL resident/export resources; no display/socket. */
public final class ClericWellSpotClientTest {
 static int checks;static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 static final int BG=0x23262c;static byte[] body;static int offset;
 static RSModel footprint(){RSModel m=new RSModel(body,offset,true);m.key=7;m.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);return m;}
 static RSModel horizontal(int half,int y,int material){RSModel m=new RSModel(4,1);int[] v={m.insertVertex(-half,y,-half),m.insertVertex(half,y,-half),m.insertVertex(half,y,half),m.insertVertex(-half,y,half)};m.insertFace(4,v,material,material,false);m.key=9;m.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);return m;}
 static final class View {
  MudClientGraphics g=new MudClientGraphics(512,384,1);Scene s=new Scene(g,4,16,4);
  View(){s.setMidpoints(192,true,512,256,192,9,256);s.fogLandscapeDistance=4000;s.fogEntityDistance=4000;s.setCamera(0,-4,0,768,0,0,600);}
  void draw(int x,int y){Arrays.fill(g.pixelData,BG);s.setMouseLoc(0,x,y);s.endScene(0);}
  boolean picked(RSModel m){RSModel[] models=s.b((byte)100);for(int i=0;i<s.b(0);i++)if(models[i]==m)return true;return false;}
 }
 static void nativeAndDefinitions(Path root)throws Exception{
  byte[] archive=Files.readAllBytes(root.resolve("Client_Base/Cache/video/models.orsc"));body=Arrays.copyOfRange(archive,6,archive.length);offset=DataOperations.getDataFileOffset("interaction_spot.ob3",body);check(offset>0,"native model exists");
  Config.S_WANT_CUSTOM_SPRITES=true;EntityHandler.load(true);
  NodeList defs=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("server/conf/server/defs/GameObjectDef.xml").toFile()).getElementsByTagName("GameObjectDef");
  check(defs.getLength()==1356 && EntityHandler.objectCount()==1356,"paired registry count");
  for(int id=0;id<1356;id++){
   GameObjectDef d=EntityHandler.getObjectDef(id);Element xml=(Element)defs.item(id);
   check(d.isInteractionOnly()==(id>=1354),"old behavior defaults false and new opt-in "+id);
   if(id>=1354){check(d.getType()==0&&d.getWidth()==1&&d.getHeight()==1,"nonblocking footprint");check(d.getCommand1().equals("Dip sigils")&&d.getCommand2().equals("Examine"),"native commands");check(xml.getElementsByTagName("interactionOnly").item(0).getTextContent().equals("true"),"server flag");check(d.getObjectModel().equals("interaction_spot"),"ordinary registry model selector");}
  }
 }
 static void lifecycle()throws Exception{
  RSModel m=footprint();check(m.vertHead==4&&m.faceHead==1,"real native mesh");check(m.faceTextureFront[0]==Scene.TRANSPARENT&&m.faceTextureBack[0]==Scene.TRANSPARENT,"both sides invisible");
  int v=m.getRenderer3DTransformVersion();m.setInteractionOnly(true);check(m.getRenderer3DTransformVersion()!=v,"flag invalidates geometry cache");v=m.getRenderer3DTransformVersion();m.setInteractionOnly(true);check(m.getRenderer3DTransformVersion()==v,"same flag does not churn geometry cache");
  RSModel clone=m.clone();check(clone.isInteractionOnly(),"clone carries interaction metadata");clone.setTranslate(1,2,3);clone.addRotation(0,64,0);check(clone.isInteractionOnly(),"transform retains metadata");
  RSModel copy=m.copyModel(false,0,false,false,false);check(copy.isInteractionOnly(),"instance copy carries metadata");
  RSModel[] split=m.divideModelByGrid(0,1,1536,-120,1,100,1536,true,0);check(split[0].isInteractionOnly(),"split carries metadata");
  check(!footprint().isInteractionOnly(),"shared native model does not opt in unrelated definition");
  RSModel ordinary=horizontal(60,-4,-12345);ordinary.setRenderer3DGlowEmitter(0xff0000,128,120);
  Renderer3DWorldChunkFrame.ChunkMesh visible=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{ordinary},1);check(visible.getTriangleCount()>0&&visible.getGlowEmitterCount()>0,"ordinary native mesh remains visible/glowing");
  ordinary.setInteractionOnly(true);Renderer3DWorldChunkFrame.ChunkMesh invisible=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{ordinary},1);check(invisible.getTriangleCount()==0&&invisible.getShadowCasterCount()==0&&invisible.getGlowEmitterCount()==0,"flagged shared visible model emits no GL triangles/shadows/glow");
  ordinary.setInteractionOnly(false);check(RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,new RSModel[]{ordinary},1).getTriangleCount()>0,"clear flag restores native geometry without mutating base faces");
 }
 static void mouseAndOcclusion(){
  for(int facing=0;facing<8;facing++){
   View v=new View();RSModel p=footprint();p.addRotation(0,facing*32,0);v.s.addModel(p);v.draw(256,192);check(!v.picked(p),"unflagged transparent scenery not suddenly clickable");p.setInteractionOnly(true);v.draw(256,192);check(v.picked(p),"actual mouse query picks invisible native footprint facing"+facing);check(Arrays.stream(v.g.pixelData).allMatch(n->n==BG),"proxy has zero software pixels");
   if(v.s.getRenderer3DFrame()!=null)for(Renderer3DFrame.FaceCommand f:v.s.getRenderer3DFrame().getWorldFaces())check(f.getTexture()==Scene.TRANSPARENT,"captured GL face remains transparent");
   v.draw(0,0);check(!v.picked(p),"off-footprint pixel not selectable");
  }
  View v=new View();RSModel p=footprint();p.setInteractionOnly(true);v.s.addModel(p);RSModel blocker=horizontal(60,-104,-12345);v.s.addModel(blocker);v.draw(256,192);check(!v.picked(p)&&v.picked(blocker),"opaque nearer wall occludes only invisible proxy");
  v.s.removeModel(blocker);blocker=horizontal(60,96,-12345);v.s.addModel(blocker);v.draw(256,192);check(v.picked(p),"opaque rear floor does not hide nearer proxy");
  v.s.removeModel(blocker);blocker=horizontal(60,-104,Scene.TRANSPARENT);v.s.addModel(blocker);v.draw(256,192);check(v.picked(p),"transparent foreground terrain is not an occluder");
  v.s.removeModel(blocker);blocker=horizontal(60,-104,-2);v.s.addModel(blocker);v.draw(256,192);check(v.picked(p)==!Renderer3DSettings.canSkipLegacyWorldRaster(),"legacy-2 no-raster versus GL solid-material semantics preserved");
  v.s.removeModel(blocker);blocker=horizontal(5,-104,-12345);blocker.setTranslate(18,0,0);blocker.setPickBoundsScale(3);v.s.addModel(blocker);v.draw(256,192);check(v.picked(p),"ordinary expanded pick padding is not opaque world geometry");
  v.s.removeModel(blocker);v.s.setFrustum(0,11,7,1);byte[] texels=new byte[64*64];Arrays.fill(texels,(byte)1);for(int y=28;y<36;y++)for(int x=28;x<36;x++)texels[y*64+x]=0;v.s.loadTexture(0,new int[]{0xff00ff,0x777777},0,texels);blocker=horizontal(60,-104,0);v.s.addModel(blocker);v.draw(256,192);check(v.picked(p),"cutout transparent texel permits picking through hole");v.draw(276,212);check(!v.picked(p)&&v.picked(blocker),"cutout opaque texel occludes proxy while ordinary picking retained");
  v.s.removeModel(blocker);p.setTranslate(100000,0,0);v.draw(256,192);check(!v.picked(p),"out-of-frustum interaction footprint not selectable");
 }
 static void longShoreline(){View v=new View();RSModel center=null;for(int i=0;i<1024;i++){RSModel p=footprint();p.setInteractionOnly(true);p.setTranslate((i%32-16)*128,0,(i/32-16)*128);if(i==16*32+16)center=p;v.s.addModel(p);}long start=System.nanoTime();for(int i=0;i<3;i++)v.draw(256,192);long nanos=System.nanoTime()-start;check(v.picked(center)&&v.s.b(0)<=4,"1024 edge spots remain local to hovered footprint");check(Arrays.stream(v.g.pixelData).allMatch(n->n==BG),"long shoreline renders no proxy pixels");System.out.println("1024-proxy complete Scene mouse-query average ms="+(nanos/3.0/1000000.0));}
 public static void main(String[] args)throws Exception{nativeAndDefinitions(Paths.get(args[0]));lifecycle();mouseAndOcclusion();longShoreline();System.out.println("PASS "+checks+" native footprint/definition/clone/cache/actual mouse/occlusion/cutout/GL export checks; capture="+Renderer3DSettings.isGeometryCaptureEnabled());}
}
