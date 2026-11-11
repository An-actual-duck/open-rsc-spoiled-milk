package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.json.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
import com.openrsc.client.entityhandling.EntityHandler;

/** Independent original loadSections versus cooked activation; no production source overlay. */
public final class WholeRamActivationFixture {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
 static final class Active {
  final MudClientGraphics surface=new MudClientGraphics(64,64,1);
  final Scene scene=new Scene(surface,4,16,4);
  final World world=new World(scene,surface);
  Active(Scene actualAssets)throws Exception{
   int[][] palettes=(int[][])get(actualAssets,"m_L");byte[][] pixels=(byte[][])get(actualAssets,"m_g");int[] types=(int[])get(actualAssets,"m_Hb");
   scene.setFrustum(0,11,7,palettes.length);
   for(int i=0;i<palettes.length;i++)if(palettes[i]!=null&&pixels[i]!=null)scene.loadTexture(i,palettes[i].clone(),types[i],pixels[i].clone());
  }
  void scope(NativeLayeredTerrainSnapshot snapshot){world.setLayeredTerrainScope(true,snapshot,0,0);}
  JSONObject state()throws Exception{return WholeRamWorldState.capture(world,scene,surface);}
 }
 static void equal(JSONObject a,JSONObject b,String label)throws Exception{
  for(String key:a.keySet())check(a.get(key).toString().equals(b.get(key).toString()),label+" field="+key);
 }
 static void debugModels(Active a,Active b)throws Exception{
  for(String name:new String[]{"modelWallGrid","modelRoofGrid"}){RSModel[][] aa=(RSModel[][])get(a.world,name),bb=(RSModel[][])get(b.world,name);for(int row=0;row<aa.length;row++)for(int i=0;i<aa[row].length;i++){RSModel x=aa[row][i],y=bb[row][i];if(x==null||y==null)continue;if(WholeRamWorldState.model(x,a.world).equals(WholeRamWorldState.model(y,b.world)))continue;JSONObject diff=new JSONObject().put("grid",name).put("row",row).put("index",i);JSONArray fields=new JSONArray();for(Field f:RSModel.class.getDeclaredFields()){if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);StringBuilder one=new StringBuilder(),two=new StringBuilder();WholeRamModelFieldFixture.graph(one,f.get(x),new IdentityHashMap<Object,Integer>());WholeRamModelFieldFixture.graph(two,f.get(y),new IdentityHashMap<Object,Integer>());if(!one.toString().equals(two.toString())){JSONObject d=new JSONObject().put("name",f.getName());Object av=f.get(x),bv=f.get(y);if(av instanceof int[]&&bv instanceof int[]){int[] ax=(int[])av,bx=(int[])bv;d.put("lengths",new JSONArray(new int[]{ax.length,bx.length}));for(int n=0;n<Math.min(ax.length,bx.length);n++)if(ax[n]!=bx[n]){d.put("first",new JSONArray(new int[]{n,ax[n],bx[n]}));break;}}else if(av instanceof Number)d.put("values",new JSONArray().put(av).put(bv));fields.put(d);}}diff.put("fields",fields);System.out.println("MODEL_DIFF "+diff);}}
 }
 static String geometry(Renderer3DWorldChunkFrame.ChunkMesh c,boolean centralOnly)throws Exception{
  WholeRamWorldState.Digest d=new WholeRamWorldState.Digest();
  for(int t=0;t<c.getTriangleCount();t++){
   long x=0,z=0;for(int n=0;n<3;n++){int v=c.getIndex(t*3+n)*3;x+=c.getVertexCoord(v);z+=c.getVertexCoord(v+2);}
   if(centralOnly&&(x<6144L*3||x>=12288L*3||z<6144L*3||z>=12288L*3))continue;
   for(int word:new int[]{c.getTriangleTexture(t),c.getTriangleFallbackColor(t),c.getTriangleModelKind(t).ordinal(),c.getTriangleMaterialFamily(t).ordinal(),c.getTriangleTerrainVariationMask(t)})d.word(word);
   for(int n=0;n<3;n++){int v=c.getIndex(t*3+n);for(int axis=0;axis<3;axis++)d.word(c.getVertexCoord(v*3+axis));for(int word:new int[]{Float.floatToRawIntBits(c.getVertexTextureU(v)),Float.floatToRawIntBits(c.getVertexTextureV(v)),c.getVertexLight(v),c.getVertexNormalX(v),c.getVertexNormalY(v),c.getVertexNormalZ(v),c.getVertexTerrainBlendColor(v),c.getVertexTerrainBlendStrength(v)})d.word(word);}
  }return d.finish();
 }
 static void query(Active original,WholeRamPrepared prepared,int x,int y)throws Exception{
  Renderer3DWorldChunkFrame.ChunkMesh full=original.world.getRenderer3DWorldChunkFrame().getChunks().get(0),small=prepared.queryFrame(x,y).getChunks().get(0);
  check(full.getCenterSectionX()==x&&full.getCenterSectionY()==y&&full.getPlane()==0,"Actual original primary query identity");
  check(small.getVertexCount()>0&&small.getTriangleCount()<full.getTriangleCount(),"Nonvacuous compact canonical representation");
  check(geometry(full,true).equals(geometry(small,false)),"Central world triangles all coords/UV/light/normals/blend/material words");
  check(full.getStorageSignature()!=small.getStorageSignature(),"New representation cannot reuse full storage cache identity");
  check(small.getRoofCoverageAxis()==143&&small.getRoofCoveredTileCount()==full.getRoofCoveredTileCount(),"Actual143-axis roof metadata count");
  int hits=0;for(int xx=-1;xx<=144;xx++)for(int yy=-1;yy<=144;yy++){boolean a=full.isRoofCoveredTile(xx,yy),b=small.isRoofCoveredTile(xx,yy);check(a==b,"Exact roof bit and out-of-bounds classification");if(a)hits++;}check(hits==small.getRoofCoveredTileCount(),"Every roof covered bit agrees with count");
  Method height=TerrainDefinitionField.class.getDeclaredMethod("terrainHeightSignature",Renderer3DWorldChunkFrame.ChunkMesh.class);height.setAccessible(true);TerrainDefinitionField warm=new TerrainDefinitionField(),fresh=new TerrainDefinitionField();height.invoke(warm,full);long cached=(Long)height.invoke(warm,small),uncached=(Long)height.invoke(fresh,small);check(cached==uncached,"Normal then prepared same-center height memo equals fresh computed result");
  long[] bits=prepared.recipe(x,y).roofBits().clone();bits[0]^=1L;Renderer3DWorldChunkFrame.ChunkMesh changed=small.rebaseWholeRamQuery(0,0,bits,143,small.getRoofCoveredTileCount()+(small.isRoofCoveredTile(0,0)?-1:1));check(changed.getStorageSignature()!=small.getStorageSignature()&&changed.getSignature()!=small.getSignature(),"Changed roof bits invalidate wrapper identities");
 }
 static void collision(Active literal,Active cached,Active baseline,WholeRamPrepared prepared,int sx,int sy)throws Exception{
  int object=-1,wall=-1;for(int i=0;i<EntityHandler.objectCount();i++)if(EntityHandler.getObjectDef(i).getType()==1&&EntityHandler.getObjectDef(i).getWidth()==1&&EntityHandler.getObjectDef(i).getHeight()==1){object=i;break;}for(int i=0;i<EntityHandler.doorCount();i++)if(EntityHandler.getDoorDef(i).getDoorType()==1){wall=i;break;}
  check(object>=0&&wall>=0,"Actual blocking object and door definitions");int[][] flags=(int[][])get(literal.world,"collisionFlags");int x=-1,z=-1;outer:for(int xx=1;xx<142;xx++)for(int zz=1;zz<142;zz++)if(flags[xx][zz]!=0){x=xx;z=zz;break outer;}check(x>0,"Actual pre-existing terrain/wall blockage overlap");
  // Apply old live state, remove it, then re-activate the immutable baseline.
  // The reference is a complete original rebuild with the SAME final live objects.
  cached.world.publishWholeRamActivation(prepared.recipe(sx,sy),prepared.queryFrame(sx,sy));cached.world.addGameObject_UpdateCollisionMap(x,z,object,false);cached.world.applyWallToCollisionFlags(wall,x,z,0);cached.world.removeGameObject_CollisonFlags(object,x,z);cached.world.removeWallObject_CollisionFlags(true,0,z,x,wall);
  cached.world.publishWholeRamActivation(prepared.recipe(sx,sy),prepared.queryFrame(sx,sy));equal(baseline.state(),cached.state(),"Last-object empty restores unrelated baseline flags");
  for(Active active:new Active[]{literal,cached}){active.world.addGameObject_UpdateCollisionMap(x,z,object,false);active.world.applyWallToCollisionFlags(wall,x,z,1);}
  equal(literal.state(),cached.state(),"Full ordinary baseline plus final overlapping live tree/door state");
 }
 static void mutate(Active a)throws Exception{
  ((int[][])get(a.world,"collisionFlags"))[60][60]^=0x12345;
  ((int[][])get(a.world,"tileElevationCache"))[60][60]^=0x12345;
  a.surface.minimapSprite.getPixels()[100]^=0xffffff;
  for(String name:new String[]{"modelWallGrid","modelRoofGrid"})for(RSModel[] row:(RSModel[][])get(a.world,name))for(RSModel m:row)if(m!=null){m.setTranslate(777,23,-911);m.addRotation(0,64,0);int[] v=(int[])get(m,"vertX");if(v.length>0)v[v.length-1]^=123;}
  com.openrsc.client.model.Sector[] sectors=(com.openrsc.client.model.Sector[])get(a.world,"sectors");sectors[0].getTile(0).groundElevation^=123;
 }
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),source=Paths.get(args[1]),mini=Paths.get(args[2]),preparedFile=Paths.get(args[3]),out=Paths.get(args[4]);Files.createDirectories(out);
  for(String flag:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldReplacementComposite","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster"})System.setProperty("spoiledmilk."+flag,"true");
  Class<?> type=Class.forName("orsc.BrowserWholeZoneBaker$Context");Constructor<?> constructor=type.getDeclaredConstructor(Path.class,Path.class,Path.class,Path.class);constructor.setAccessible(true);Object context=constructor.newInstance(root,source,mini,out.resolve("context"));
  Method snapshot=type.getDeclaredMethod("snapshot",int.class,int.class,int.class);snapshot.setAccessible(true);Renderer3DSettings.setBrowserPresentationAvailable(true);Scene actualAssets=((mudclient)get(context,"client")).getScene();
  WholeRamPrepared prepared;try(InputStream in=new BufferedInputStream(Files.newInputStream(preparedFile))){prepared=WholeRamPrepared.read(in);}
  check(prepared.recipes()==3&&prepared.queryCells()==9,"Explicit bounded real pilot counts");JSONArray cases=new JSONArray();
  for(int[] center:new int[][]{{2,12},{2,13},{3,13}}){
   int x=center[0],y=center[1];NativeLayeredTerrainSnapshot s=(NativeLayeredTerrainSnapshot)snapshot.invoke(context,0,x,y);Active literal=new Active(actualAssets),cached=new Active(actualAssets);literal.scope(s);cached.scope(s);
   // Original publication is the reference; no Provider installed and no RAM property enabled.
   literal.world.loadSections(x*48,y*48,0);JSONObject expected=literal.state();
   query(literal,prepared,x,y);
   long bytes=cached.world.publishWholeRamActivation(prepared.recipe(x,y),prepared.queryFrame(x,y));JSONObject actual=cached.state();
   Files.write(out.resolve(x+"-"+y+"-literal.json"),expected.toString(2).getBytes("UTF-8"));Files.write(out.resolve(x+"-"+y+"-prepared.json"),actual.toString(2).getBytes("UTF-8"));debugModels(literal,cached);equal(expected,actual,"Original/cooked "+x+":"+y);check(bytes>0,"Actual mutable-workspace copy accounting");
   mutate(cached);cached.world.publishWholeRamActivation(prepared.recipe(x,y),prepared.queryFrame(x,y));equal(expected,cached.state(),"Mutate/reactivate "+x+":"+y);
   Active second=new Active(actualAssets);second.scope(s);second.world.publishWholeRamActivation(prepared.recipe(x,y),prepared.queryFrame(x,y));mutate(cached);equal(expected,second.state(),"Cross-active-instance isolation "+x+":"+y);
   collision(literal,cached,second,prepared,x,y);
   cases.put(new JSONObject().put("centerX",x).put("centerY",y).put("copiedWorkspaceBytes",bytes));
  }
  System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("cases",cases).put("scope","Three real selected source centers; original World.loadSections versus decoded WRP1 recipe publication, exact transformed model/pick/order/collision/elevation/minimap plus source and repeated mutation isolation. Partial pilot is not full residency or readiness; no browser/GPU/performance claim.").toString());
 }
}
