package orsc;

import com.openrsc.client.model.Sprite;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
import orsc.graphics.three.*;
import orsc.graphics.two.GraphicsController;

/** Independent observable CPU world snapshot. No source-product construction or RAM implementation. */
public final class WholeRamWorldState {
 private WholeRamWorldState() {}
 static Field field(Class<?> type,String name)throws Exception{
  while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);
 }
 static Object get(Object owner,String name)throws Exception{return field(owner.getClass(),name).get(owner);}
 static int number(Object owner,String name)throws Exception{return field(owner.getClass(),name).getInt(owner);}
 static final class Digest {
  final MessageDigest digest;
  Digest()throws Exception{digest=MessageDigest.getInstance("SHA-256");}
  void word(int x){digest.update((byte)x);digest.update((byte)(x>>>8));digest.update((byte)(x>>>16));digest.update((byte)(x>>>24));}
  void array(int[] x,int used){if(x==null){word(-1);return;}if(used<0||used>x.length)throw new IllegalArgumentException("Snapshot array extent");word(used);for(int n=0;n<used;n++)word(x[n]);}
  void matrix(int[][] x){if(x==null){word(-1);return;}word(x.length);for(int[] row:x)array(row,row==null?0:row.length);}
  String finish(){StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format("%02x",b&255));return out.toString();}
 }
 static String grid(Object owner,String name)throws Exception{Digest d=new Digest();d.matrix((int[][])get(owner,name));return d.finish();}
 /** Invoke actual production transform evaluation: normalized cached base coordinates may differ legitimately. */
 static String model(RSModel model,World world)throws Exception{
  Digest d=new Digest();if(model==null){d.word(-1);return d.finish();}
  Method apply=RSModel.class.getDeclaredMethod("resetTransformCache",int.class);apply.setAccessible(true);apply.invoke(model,7972);
  int vertices=number(model,"vertHead"),faces=number(model,"faceHead");d.word(vertices);d.word(faces);d.word(model.key);d.word(model.getRenderer3DModelKind().ordinal());d.word(model.getRenderer3DMaterialFamily().ordinal());d.word(model.isInteractionOnly()?1:0);d.word(model.m_cb?1:0);
  for(String name:new String[]{"vertXTransform","vertYTransform","vertZTransform"})d.array((int[])get(model,name),vertices);
  // Flat faces never consume vertex diffuse light (Scene3369/3627 and object builder).
  // The reused ordinary accumulator leaves these unused words history-dependent.
  int[] faceLight=(int[])get(model,"faceDiffuseLight"),vertexLight=(int[])get(model,"vertDiffuseLight");int[][] faceVertices=(int[][])get(model,"faceIndices");int[] faceCounts=(int[])get(model,"faceIndexCount");int gouraud=number(model,"m_Vb");
  for(int f=0;f<faces;f++)if(faceLight[f]==gouraud){d.word(f);for(int v=0;v<faceCounts[f];v++)d.word(vertexLight[faceVertices[f][v]]);}
  byte[] other=(byte[])get(model,"vertLightOther");d.word(other==null?-1:vertices);if(other!=null)for(int n=0;n<vertices;n++)d.word(other[n]);
  for(String name:new String[]{"faceIndexCount","faceDiffuseLight","faceTextureFront","faceTextureBack","facePickIndex"})d.array((int[])get(model,name),faces);
  int[][] indices=(int[][])get(model,"faceIndices");int[] count=(int[])get(model,"faceIndexCount");d.word(faces);for(int n=0;n<faces;n++)d.array(indices[n],count[n]);
  // Execute the production front/back vertex-light selector, including flat-face branches.
  Scene scene=(Scene)get(world,"scene");Method select=Scene.class.getDeclaredMethod("renderer3DBaseLightForVertex",RSModel.class,int.class,int.class,int.class);select.setAccessible(true);
  for(int f=0;f<faces;f++)for(int v=0;v<count[f];v++){d.word((Integer)select.invoke(scene,model,f,-1,indices[f][v]));d.word((Integer)select.invoke(scene,model,f,1,indices[f][v]));}
  // Shade/normal inputs and bounds participate in actual picking/culling, unlike cache/version counters.
  for(String name:new String[]{"diffuseParam1","diffuseParam2","diffuseDirX","diffuseDirY","diffuseDirZ","diffuseMag","minX","maxX","minY","maxY","minZ","maxZ","maxFaceDimension","pickBoundsScale"})d.word(number(model,name));
  for(String name:new String[]{"faceTextureUStart","faceTextureUScale"}){float[] values=(float[])get(model,name);d.word(values==null?-1:faces);if(values!=null)for(int n=0;n<faces;n++)d.word(Float.floatToRawIntBits(values[n]));}
  // Only consumed terrain pick IDs matter; unused backing-array tails are not published state.
  int[] picks=model.facePickIndex;for(int f=0;f<faces;f++){int pick=picks==null?0:picks[f];if(pick>=200000&&pick-200000<world.faceTileX.length){int id=pick-200000;d.word(f);d.word(world.faceTileX[id]);d.word(world.faceTileZ[id]);}}
  d.word(model.getRenderer3DGlowColor());d.word(model.getRenderer3DGlowRadius());d.word(model.getRenderer3DGlowIntensity());return d.finish();
 }
 public static JSONObject capture(World world,Scene scene,GraphicsController minimap)throws Exception{
  JSONObject result=new JSONObject();for(String name:new String[]{"collisionFlags","tileElevationCache","tileDirection"})result.put(name,grid(world,name));
  Digest sectors=new Digest();for(com.openrsc.client.model.Sector s:(com.openrsc.client.model.Sector[])get(world,"sectors")){sectors.word(s.isLoaded()?1:0);for(int i=0;i<2304;i++){com.openrsc.client.model.Tile t=s.getTile(i);for(int word:new int[]{t.groundElevation,t.diagonalWalls,t.groundTexture,t.groundOverlay,t.roofTexture,t.horizontalWall,t.verticalWall,t.editorPaintedOverlay?1:0})sectors.word(word);}}result.put("sectors",sectors.finish());
  NativeLayeredTerrainSnapshot source=world.getWholeOverworldSourceSnapshot();if(source!=null)result.put("source",new JSONObject().put("manifest",source.getManifestSha256()).put("worldSpace",source.getWorldSpace()).put("signedLevel",source.getLevel()).put("centerX",source.getCurrentChunkX()).put("centerY",source.getCurrentChunkY()));
  Sprite sprite=minimap.minimapSprite;Digest pixels=new Digest();pixels.word(sprite.getWidth());pixels.word(sprite.getHeight());int[] sourcePixels=sprite.getPixels();pixels.array(sourcePixels,sourcePixels==null?0:sourcePixels.length);result.put("minimap",pixels.finish());
  JSONArray grids=new JSONArray();for(String name:new String[]{"modelLandscapeGrid","modelWallGrid","modelRoofGrid"}){Object value=get(world,name);JSONArray layers=new JSONArray();if(value instanceof RSModel[][]){for(RSModel[] row:(RSModel[][])value){JSONArray models=new JSONArray();for(RSModel m:row)models.put(model(m,world));layers.put(models);}}else{JSONArray models=new JSONArray();for(RSModel m:(RSModel[])value)models.put(model(m,world));layers.put(models);}grids.put(new JSONObject().put("field",name).put("layers",layers));}result.put("modelGrids",grids);
  int count=number(scene,"modelCount");RSModel[] models=(RSModel[])get(scene,"models");JSONArray order=new JSONArray();for(int n=0;n<count;n++)order.put(model(models[n],world));result.put("sceneOrder",order);result.put("scope","Actual post-transform CPU world state; source/collision/elevation/direction/minimap/pick/model traversal. Excludes unconsumed capacity tails and cache/version counters. Invokes production model transforms on owned active models.");return result;
 }
 public static void requireEqual(JSONObject literal,JSONObject prepared,String label){if(!literal.toString().equals(prepared.toString()))throw new AssertionError("CPU world mismatch: "+label);}
}
