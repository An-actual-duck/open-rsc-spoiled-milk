package orsc;
import orsc.graphics.three.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;
/** Literal production canonical model builder versus actual baked object bytes. */
public final class WholeZoneObjectParity {
 static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}
 static Method method(Class<?> c,String n,Class<?>...t)throws Exception{Method m=c.getDeclaredMethod(n,t);m.setAccessible(true);return m;}
 static JSONObject json(Path p)throws Exception{return new JSONObject(new String(Files.readAllBytes(p),"UTF-8"));}
 static void check(boolean b,String n){if(!b)throw new AssertionError(n);}
 static String hex(long n){return String.format("%016x",n);}
 static byte[] pack(float[] v,int[] i,int[] b){ByteBuffer out=ByteBuffer.allocate(16+(v.length+i.length+b.length)*4).order(ByteOrder.LITTLE_ENDIAN);out.putInt(0x575a4331).putInt(v.length/28).putInt(i.length).putInt(b.length);for(float x:v)out.putInt(Float.floatToRawIntBits(x));for(int x:i)out.putInt(x);for(int x:b)out.putInt(x);return out.array();}
 @SuppressWarnings("unchecked")public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),source=Paths.get(args[1]),mini=Paths.get(args[2]),catalogPath=Paths.get(args[3]),setup=Paths.get(args[4]);Files.createDirectories(setup);JSONObject catalog=json(catalogPath);check(catalog.getJSONArray("objectCells").length()<=64,"Bounded pilot object-cell budget; select an explicit pilot rather than full catalogue");
  Class<?> context=Class.forName("orsc.BrowserWholeZoneBaker$Context");Constructor<?> ctor=context.getDeclaredConstructor(Path.class,Path.class,Path.class,Path.class);ctor.setAccessible(true);Object ctx=ctor.newInstance(root,source,mini,setup);mudclient client=(mudclient)get(ctx,"client");Renderer3DFrame frame=(Renderer3DFrame)get(ctx,"frame");OpenGLWorldChunkRenderer renderer=(OpenGLWorldChunkRenderer)get(ctx,"packing");
  Map<Integer,JSONObject> resources=new HashMap<>();for(Object item:catalog.getJSONArray("chunks")){JSONObject c=(JSONObject)item;resources.put(c.getInt("id"),c);}
  Class<?> builder=Class.forName("orsc.mudclient$ResidentObjectChunkInputBuilder"),identity=Class.forName("orsc.mudclient$ResidentObjectCanonicalIdentity"),input=Class.forName("orsc.mudclient$ResidentObjectChunkInput");Constructor<?> bc=builder.getDeclaredConstructor(Renderer3DWorldChunkFrame.ChunkMesh.class,long.class,int.class,int.class,int.class,int.class,int.class,int.class);bc.setAccessible(true);Constructor<?> ic=identity.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class,int.class,int.class);ic.setAccessible(true);
  int cells=0,empty=0,placements=0;long compared=0,vertices=0;JSONArray cases=new JSONArray();
  for(Object item:catalog.getJSONArray("objectCells")){JSONObject c=(JSONObject)item;int plane=c.getInt("plane"),x=c.getInt("cellX"),y=c.getInt("cellY");if(c.isNull("resourceId")){empty++;continue;}
   Renderer3DWorldChunkFrame.ChunkMesh anchor=new Renderer3DWorldChunkFrame.ChunkMesh(plane,x/2,y/2,x*24*128,y*24*128,new int[0],new float[0],new float[0],new int[0],new int[0],new int[0],new int[0],new Renderer3DModelKind[0],0,0,0,false,0L);
   Object b=bc.newInstance(anchor,0L,0,0,x,y,Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,24);List<Object> identities=(List<Object>)get(b,"canonicalIdentities");JSONArray words=c.getJSONArray("identities");for(int at=0;at<words.length();at+=7)identities.add(ic.newInstance(words.getInt(at),words.getInt(at+1),words.getInt(at+2),words.getInt(at+3),words.getInt(at+4),words.getInt(at+5),words.getInt(at+6)));
   Object literalInput=method(builder,"build").invoke(b);check(hex(((Long)get(literalInput,"canonicalContentKey")).longValue()).equals(c.getString("canonicalContentKey")),"Production canonical key differs "+plane+":"+x+":"+y);
   set(client,"midRegionBaseX",x*24);set(client,"midRegionBaseZ",y*24);Renderer3DWorldChunkFrame.ChunkMesh mesh=(Renderer3DWorldChunkFrame.ChunkMesh)method(mudclient.class,"buildResidentObjectChunkMesh",input).invoke(client,literalInput);float[] v=renderer.copyBackendChunkVertices(mesh,frame);BrowserChunkMaterials.Buffers material=BrowserChunkMaterials.pack(renderer,frame,mesh);byte[] literal=pack(v,material.indices,material.batches);JSONObject resource=resources.get(c.getInt("resourceId"));byte[] actual=Files.readAllBytes(catalogPath.getParent().resolve(resource.getString("path")));
   if(!Arrays.equals(literal,actual)){int at=0;while(at<Math.min(literal.length,actual.length)&&literal[at]==actual[at])at++;throw new AssertionError("Object geometry mismatch "+plane+":"+x+":"+y+" byte="+at+" actual="+actual.length+" literal="+literal.length);}
   cells++;placements+=c.getInt("placementCount");vertices+=v.length/28;compared+=literal.length;cases.put(new JSONObject().put("plane",plane).put("cellX",x).put("cellY",y).put("vertices",v.length/28).put("bytes",literal.length));
  }
  check(cells>0&&placements>0&&compared>0,"Nonvacuous object comparison");frame.release();System.out.println(new JSONObject().put("passed",true).put("objectResources",cells).put("emptyCells",empty).put("placements",placements).put("vertices",vertices).put("comparedBytes",compared).put("cases",cases).put("scope","Actual unchanged production canonical builder/model transforms, all112B vertex raw words, material batches and index bytes equal actual bake. Conditional on supplied source-joined seven-word identities; does not independently establish elevation words, terrain/seams, GPU allocation or pixels.").toString());
 }
}
