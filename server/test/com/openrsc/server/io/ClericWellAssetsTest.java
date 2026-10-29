package com.openrsc.server.io;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.extras.TextureDef;
import java.io.File;import java.lang.reflect.*;import java.nio.file.*;import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;import org.w3c.dom.*;
import orsc.Config;import orsc.graphics.three.*;import orsc.graphics.two.*;import orsc.multiclient.ClientPort;
/** Offline real client loading, palette, software raster and GL-resource parity. No game/socket/session. */
public final class ClericWellAssetsTest {
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static Field f(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static Method method(Class<?> c,String n)throws Exception{Method m=c.getDeclaredMethod(n);m.setAccessible(true);return m;}
 static String value(Element e,String n){return e.getElementsByTagName(n).item(0).getTextContent();}
 public static void main(String[]args)throws Exception {
  Path root=Paths.get(args[0]);Config.F_CACHE_DIR=root.resolve("Client_Base/Cache").toString();Config.S_WANT_CUSTOM_SPRITES=true;EntityHandler.load(true);
  check(EntityHandler.textureCount()==73,"same full texture domain in both modes");
  for(int i=0;i<55;i++)check(EntityHandler.getTextureDef(i).getNativeSpriteId()==-1,"original contiguous mapping unchanged");
  for(int i=55;i<73;i++)check(EntityHandler.getTextureDef(i).getNativeSpriteId()==4100+i-55,"extension native mapping");
  for(int bad:new int[]{-2,65536}){boolean rejected=false;try{new TextureDef("bad","",bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"native mapping bound");}
  NodeList floors=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("server/conf/server/defs/TileDef.xml").toFile()).getElementsByTagName("TileDef");
  check(EntityHandler.tileCount()==floors.getLength()&&floors.getLength()==28,"floor count parity");
  for(int i=26;i<28;i++){Element e=(Element)floors.item(i);check(EntityHandler.getTileDef(i).getColour()==Integer.parseInt(value(e,"colour"))&&Integer.parseInt(value(e,"colour"))==i+41,"floor texture parity");check(EntityHandler.getTileDef(i).getObjectType()==1&&Integer.parseInt(value(e,"objectType"))==1,"liquid blocks walking");check(EntityHandler.getTileDef(i).getTileValue()==3&&Integer.parseInt(value(e,"unknown"))==3,"native liquid edge rule");}
  NodeList walls=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("server/conf/server/defs/DoorDef.xml").toFile()).getElementsByTagName("DoorDef");check(walls.getLength()==228&&EntityHandler.doorCount()==228,"walls parity");
  for(int i=220;i<228;i++){Element e=(Element)walls.item(i);com.openrsc.client.entityhandling.defs.DoorDef d=EntityHandler.getDoorDef(i);check(d.getName().equals(value(e,"name"))&&d.getDescription().equals(value(e,"description")),"wall text parity");check(d.getWallObjectHeight()==Integer.parseInt(value(e,"modelVar1"))&&d.getWallObjectHeight()==((i&1)==0?192:275),"wall heights");check(d.getModelVar2()==69+(i-220)/2&&d.getModelVar2()==Integer.parseInt(value(e,"modelVar2"))&&d.getModelVar3()==Integer.parseInt(value(e,"modelVar3")),"both wall face materials");check(d.getDoorType()==1&&Integer.parseInt(value(e,"doorType"))==1&&d.unknown==0,"static blocking wall");}
  Class<?> u=Class.forName("sun.misc.Unsafe");Object unsafe=f(u,"theUnsafe").get(null);orsc.mudclient c=(orsc.mudclient)u.getMethod("allocateInstance",Class.class).invoke(unsafe,orsc.mudclient.class);
  orsc.mudclient.clientPort=(ClientPort)Proxy.newProxyInstance(ClientPort.class.getClassLoader(),new Class<?>[]{ClientPort.class},(p,m,a)->{if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;return null;});
  Class<?> ext=Class.forName("orsc.ClientExternalAssetLoader");Constructor<?> ctor=ext.getDeclaredConstructor();ctor.setAccessible(true);f(orsc.mudclient.class,"externalAssetLoader").set(c,ctor.newInstance());
  int[][] nativeRgba=new int[73][];
  for(boolean custom:new boolean[]{false,true}){
   Config.S_WANT_CUSTOM_SPRITES=custom;MudClientGraphics graphics=new MudClientGraphics(640,512,4501);c.setSurface(graphics);if(custom)check(graphics.fillSpriteTree(),"real custom unpacker");Scene scene=new Scene(graphics,16,4096,4);scene.setMidpoints(320,true,640,256,320,9,256);f(orsc.mudclient.class,"scene").set(c,scene);
   method(orsc.mudclient.class,custom?"loadTextures":"loadTexturesAuthentic").invoke(c);
   Renderer3DTextureData[] resources=(Renderer3DTextureData[])f(Scene.class,"renderer3DTextures").get(scene);
   for(int i=0;i<73;i++){check(resources[i]!=null&&resources[i].getWidth()>0,"real software+GL texture resource exists "+i);if(!custom)nativeRgba[i]=resources[i].getRgbaPixels();else if(i>=55)check(Arrays.equals(nativeRgba[i],resources[i].getRgbaPixels()),"extension palette identical across modes "+i);}
   for(int texture:new int[]{67,68,69,70,71,72}){Arrays.fill(graphics.pixelData,0x23262c);RSModel wall=new RSModel(4,1);int[] vertices={wall.insertVertex(0,0,0),wall.insertVertex(0,-275,0),wall.insertVertex(256,-275,0),wall.insertVertex(256,0,0)};wall.insertFace(4,vertices,texture,texture,false);scene.addModel(wall);scene.fogLandscapeDistance=4000;scene.fogEntityDistance=4000;scene.setCamera(128,-137,0,944,512,0,700);scene.forceLegacyWorldRasterOnce();scene.endScene(0);long pixels=Arrays.stream(graphics.pixelData).filter(v->v!=0x23262c).count();check(pixels>1000,"actual texture raster visible "+texture);scene.removeModel(wall);}
  }
  System.out.println("PASS "+checks+" Cleric well native/custom load, quantizer/GL-resource, render, definition, collision policy and safe-slot checks");
 }
}
