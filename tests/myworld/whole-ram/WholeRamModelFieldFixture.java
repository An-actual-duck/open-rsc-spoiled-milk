package orsc;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import org.json.*;
import orsc.graphics.three.*;

/** Independent reflection inventory: future production fields must not silently escape the codec. */
public final class WholeRamModelFieldFixture {
 static int checks;
 static int transparent()throws Exception{Field f=Scene.class.getDeclaredField("TRANSPARENT");f.setAccessible(true);return f.getInt(null);}
 static void check(boolean yes,String label){checks++;if(!yes)throw new AssertionError(label);}
 static List<Field> fields(){List<Field> out=new ArrayList<>();for(Field f:RSModel.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);out.add(f);}Collections.sort(out,Comparator.comparing(Field::getName));return out;}
 static Object array(Class<?> type,int seed){
  if(type==int[].class){int[] a=new int[13+seed%3];for(int n=0;n<a.length;n++)a[n]=seed*257+n*11;return a;}
  if(type==byte[].class){byte[] a=new byte[17];for(int n=0;n<a.length;n++)a[n]=(byte)(seed+n*29);return a;}
  if(type==float[].class)return new float[]{Float.intBitsToFloat(0x80000000),Float.intBitsToFloat(0x7fc12345),Float.intBitsToFloat(1),seed+.25f,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
  if(type==long[].class)return new long[]{Long.MIN_VALUE,0x7123456789abcdefL,seed,Long.MAX_VALUE};
  if(type==int[][].class)return new int[][]{(int[])array(int[].class,seed),null,new int[0],(int[])array(int[].class,seed+1)};
  throw new AssertionError("Unaudited production array field "+type);
 }
 static void set(RSModel m,String name,Object value)throws Exception{Field f=RSModel.class.getDeclaredField(name);f.setAccessible(true);f.set(m,value);}
 static RSModel sentinel(boolean polarity)throws Exception{
  RSModel m=new RSModel(0,0);int n=1;
  for(Field f:fields()){
   if(Modifier.isFinal(f.getModifiers())){check(f.getName().equals("m_Vb")&&f.getType()==int.class&&f.getInt(m)==12345678,"Only documented immutable constructor constant may be excluded");continue;}
   Class<?> t=f.getType();int seed=n++;
   if(t==int.class)f.setInt(m,0x1020304+seed*257);else if(t==boolean.class)f.setBoolean(m,polarity);else if(t==long.class)f.setLong(m,0x7123456789abc000L+seed);else if(t==float.class)f.setFloat(m,seed+.25f);else if(t==double.class)f.setDouble(m,seed+.125);else if(t.isEnum()){Object[] values=t.getEnumConstants();f.set(m,values[polarity?values.length-1:Math.min(1,values.length-1)]);}else if(t.isArray())f.set(m,array(t,seed));else throw new AssertionError("Unaudited nonstatic production reference "+f.getName()+":"+t);
  }
  // Equal-content distinct groups, cross-field alias, nested alias, null and capacity tails.
  int[] shared={3,5,7,11,13,17,19,23,29,31,37,41,43,47,53,59,61};set(m,"vertX",shared);set(m,"vertXTransform",shared);set(m,"vertexParam2",shared);set(m,"faceDiffuseLight",shared.clone());set(m,"faceIndexCount",shared.clone());int[][] matrix={shared,null,shared,new int[0],shared.clone()};set(m,"faceIndices",matrix);set(m,"faceParam1",matrix);return m;
 }
 static void graph(StringBuilder s,Object value,IdentityHashMap<Object,Integer> ids){
  if(value==null){s.append("null;");return;}Class<?> type=value.getClass();
  if(type.isArray()){Integer prior=ids.get(value);if(prior!=null){s.append('@').append(prior).append(';');return;}int id=ids.size();ids.put(value,id);int length=Array.getLength(value);s.append(type.getName()).append('#').append(id).append('[').append(length).append("]{");for(int n=0;n<length;n++)graph(s,Array.get(value,n),ids);s.append('}');}
  else if(value instanceof Float)s.append("f").append(Integer.toHexString(Float.floatToRawIntBits((Float)value))).append(';');else if(value instanceof Double)s.append("d").append(Long.toHexString(Double.doubleToRawLongBits((Double)value))).append(';');else if(value instanceof Number||value instanceof Boolean||type.isEnum())s.append(type.getName()).append(':').append(value).append(';');else throw new AssertionError("Unaudited graph object "+type);
 }
 static String snapshot(RSModel m)throws Exception{StringBuilder s=new StringBuilder();IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();for(Field f:fields()){s.append(f.getName()).append('=');graph(s,f.get(m),ids);}return s.toString();}
 static void collect(Object value,Set<Object> arrays){if(value==null||!value.getClass().isArray()||!arrays.add(value))return;if(!value.getClass().getComponentType().isPrimitive())for(int n=0;n<Array.getLength(value);n++)collect(Array.get(value,n),arrays);}
 static Set<Object> arrays(RSModel m)throws Exception{Set<Object> out=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(Field f:fields())collect(f.get(m),out);return out;}
 static void disjoint(RSModel a,RSModel b)throws Exception{Set<Object> left=arrays(a);for(Object x:arrays(b))check(!left.contains(x),"Mutable arrays must not cross model/activation ownership");}
 static void mutateArrays(RSModel m)throws Exception{for(Object a:arrays(m)){Class<?> t=a.getClass().getComponentType();if(t.isPrimitive()&&Array.getLength(a)>0){if(t==int.class)Array.setInt(a,Array.getLength(a)-1,0x55667788);else if(t==byte.class)Array.setByte(a,Array.getLength(a)-1,(byte)0xA5);else if(t==float.class)Array.setFloat(a,Array.getLength(a)-1,-17.25f);else if(t==long.class)Array.setLong(a,Array.getLength(a)-1,-1);}}}
 public static void main(String[] args)throws Exception{
  JSONArray inventory=new JSONArray();for(Field f:fields())inventory.put(f.getName()+":"+f.getType().getName());int synthetic=0;
  for(boolean polarity:new boolean[]{false,true}){
   RSModel source=sentinel(polarity);String expected=snapshot(source);WholeRamData.Pool pool=new WholeRamData.Pool();int descriptor=RSModel.capturePreparedState(source,pool);RSModel a=RSModel.instantiatePreparedState(descriptor,pool),b=RSModel.instantiatePreparedState(descriptor,pool);check(expected.equals(snapshot(a)),"Every field/value/full extent/alias survives capture");check(expected.equals(snapshot(b)),"Second model exact inventory");disjoint(source,a);disjoint(a,b);mutateArrays(source);mutateArrays(a);a.key=-2;check(expected.equals(snapshot(b)),"Source/active mutation cannot change second model");RSModel c=RSModel.instantiatePreparedState(descriptor,pool);check(expected.equals(snapshot(c)),"Mutation then reinstantiate exact frozen state");disjoint(a,c);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();pool.write(new DataOutputStream(bytes));WholeRamData.Pool reread=WholeRamData.Pool.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(expected.equals(snapshot(RSModel.instantiatePreparedState(descriptor,reread))),"Wire pool preserves all raw fields/NaNs/tails/alias graph");synthetic++;
  }
  RSModel source=new RSModel(8,8);int a=source.insertVertex(0,0,0),b=source.insertVertex(128,0,0),c=source.insertVertex(0,0,128);source.insertFace(3,new int[]{a,b,c},7,transparent(),false);source.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);source.setDiffuseLightAndColor(-50,-10,-50,60,24,false,122);String expected=snapshot(source);WholeRamData.Pool pool=new WholeRamData.Pool();int descriptor=RSModel.capturePreparedState(source,pool);RSModel active=RSModel.instantiatePreparedState(descriptor,pool);active.setTranslate(129,7,-35);active.addRotation(0,32,0);active.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);active.clone();check(expected.equals(snapshot(RSModel.instantiatePreparedState(descriptor,pool))),"Actual transform/light/commit-clone then reinstantiate");check(expected.equals(snapshot(source)),"Actual model use cannot mutate captured source");disjoint(source,active);
  System.out.println(new JSONObject().put("passed",true).put("checks",checks).put("nonstaticFields",inventory.length()).put("inventory",inventory).put("syntheticPolarities",synthetic).put("scope","Independent reflection inventory of every nonstatic model field; full capacities/tails/raw float bits and alias graphs. Only immutable constructor constant m_Vb excluded from sentinel assignment. Actual model use/reinstantiation plus wire pool roundtrip; no browser/RAM full coverage proof.").toString());
 }
}
