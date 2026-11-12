package orsc;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import orsc.graphics.three.*;
import com.openrsc.client.entityhandling.EntityHandler;

/** Literal clone, independently copied source/complete alias inventory; real shared factories. */
public final class CloneTemplateIndependentFixture {
 static int checks,cases,errors;static mudclient client;static MessageDigest digest;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static List<Field> fields(){List<Field> out=new ArrayList<>();for(Field f:RSModel.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())){f.setAccessible(true);out.add(f);}out.sort(Comparator.comparing(Field::getName));return out;}
 static Object copy(Object value,IdentityHashMap<Object,Object> memo){
  if(value==null||!value.getClass().isArray())return value;
  Object prior=memo.get(value);if(prior!=null)return prior;
  int count=Array.getLength(value);Object result=Array.newInstance(value.getClass().getComponentType(),count);memo.put(value,result);
  for(int i=0;i<count;i++)Array.set(result,i,copy(Array.get(value,i),memo));return result;
 }
 static RSModel copyModel(RSModel value)throws Exception{
  RSModel result=new RSModel(0,0);IdentityHashMap<Object,Object> memo=new IdentityHashMap<>();
  for(Field f:fields())if(!Modifier.isFinal(f.getModifiers()))f.set(result,copy(f.get(value),memo));return result;
 }
 static void graph(StringBuilder text,Object value,IdentityHashMap<Object,Integer> ids){
  if(value==null){text.append("null;");return;}
  if(value.getClass().isArray()){Integer prior=ids.get(value);if(prior!=null){text.append('@').append(prior).append(';');return;}
   int id=ids.size();ids.put(value,id);text.append(value.getClass().getName()).append('#').append(id).append('[').append(Array.getLength(value)).append("]{");
   for(int i=0;i<Array.getLength(value);i++)graph(text,Array.get(value,i),ids);text.append('}');return;}
  if(value instanceof Float)text.append('f').append(Integer.toHexString(Float.floatToRawIntBits((Float)value)));
  else if(value instanceof Double)text.append('d').append(Long.toHexString(Double.doubleToRawLongBits((Double)value)));
  else text.append(value.getClass().getName()).append(':').append(value);
  text.append(';');
 }
 static String snapshot(RSModel m)throws Exception{StringBuilder s=new StringBuilder();IdentityHashMap<Object,Integer> ids=new IdentityHashMap<>();for(Field f:fields()){s.append(f.getName()).append('=');graph(s,f.get(m),ids);}return s.toString();}
 static Set<Object> arrays(RSModel m)throws Exception{Set<Object> out=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(Field f:fields())collect(f.get(m),out);return out;}
 static void collect(Object a,Set<Object> out){if(a==null||!a.getClass().isArray()||!out.add(a))return;if(!a.getClass().getComponentType().isPrimitive())for(int i=0;i<Array.getLength(a);i++)collect(Array.get(a,i),out);}
 static void disjoint(RSModel a,RSModel b)throws Exception{Set<Object> left=arrays(a);for(Object node:arrays(b))check(!left.contains(node),"mutable array crossed model ownership");}
 static void source(RSModel m)throws Exception{RSModel[] models=(RSModel[])BaselineReuseFixture.get(client,"modelCache");models[0]=m;}
 static RSModel actual(int owner){return client.cloneBrowserPresentationGameObject(0,owner);}
 static Throwable leaf(Throwable e){while(e.getCause()!=null&&e.getCause()!=e)e=e.getCause();return e;}
 static void pair(RSModel a,RSModel literal,int owner,String label)throws Exception{
  RSModel observed=null,expected=null;Throwable ae=null,ee=null;
  try{observed=actual(owner);}catch(Throwable e){ae=leaf(e);}
  try{expected=literal.clone();}catch(Throwable e){ee=leaf(e);}
  check((ae==null)==(ee==null),label+" exception parity actual="+ae+" expected="+ee);
  if(ae==null){check(snapshot(observed).equals(snapshot(expected)),label+" full clone fields/alias graph");disjoint(a,observed);check(observed!=expected,label+" model identities");digest.update(snapshot(observed).getBytes("UTF-8"));}
  else{check(ae.getClass()==ee.getClass(),label+" literal failure cause");digest.update(ae.getClass().getName().getBytes("UTF-8"));errors++;}
  check(snapshot(a).equals(snapshot(literal)),label+" literal commitTransform source side effects");cases++;
 }
 static void mutate(RSModel m,Field field)throws Exception{
  Object value=field.get(m);Class<?> type=field.getType();
  if(type==int.class){field.setInt(m,field.getInt(m)+1);return;}
  if(type==boolean.class){field.setBoolean(m,!field.getBoolean(m));return;}
  if(type.isEnum()){Object[] choices=type.getEnumConstants();field.set(m,choices[((Enum<?>)value).ordinal()+1==choices.length?0:((Enum<?>)value).ordinal()+1]);return;}
  if(type.isArray()){
   if(value==null){field.set(m,Array.newInstance(type.getComponentType(),0));return;}
   if(type==int[][].class){int[][] matrix=(int[][])value;if(matrix.length>0&&matrix[0]!=null&&matrix[0].length>0)matrix[0][0]^=1;return;}
   if(Array.getLength(value)==0){field.set(m,Array.newInstance(type.getComponentType(),1));return;}
   if(type==int[].class)((int[])value)[0]^=1;else if(type==byte[].class)((byte[])value)[0]^=1;
   else if(type==float[].class)((float[])value)[0]=Float.intBitsToFloat(Float.floatToRawIntBits(((float[])value)[0])^1);
   else throw new AssertionError("unreviewed array "+type);return;
  }
  throw new AssertionError("unreviewed model field "+field.getName()+":"+type);
 }
 static void mutationInventory()throws Exception{
  for(Field f:fields()){
   if(Modifier.isFinal(f.getModifiers())){check(f.getName().equals("m_Vb"),"new final model field requires review");continue;}
   client.clearBrowserPresentationCloneCache();RSModel a=BaselineReuseFixture.model(),b=copyModel(a);source(a);
   pair(a,b,0,"cold "+f.getName());pair(a,b,1,"warm "+f.getName());mutate(a,f);mutate(b,f);pair(a,b,2,"mutated "+f.getName());
  }
 }
 static void aliasesAndConcurrency()throws Exception{
  client.clearBrowserPresentationCloneCache();RSModel a=BaselineReuseFixture.model();
  // Deliberately equal-content distinct source arrays must remain distinguishable;
  // output alias topology follows the literal constructor, not pool interning.
  int[] x=(int[])BaselineReuseFixture.get(a,"vertX");BaselineReuseFixture.set(a,"vertZ",x.clone());
  RSModel literal=copyModel(a);source(a);pair(a,literal,0,"alias cold");pair(a,literal,1,"alias warm");
  RSModel first=actual(2),second=actual(2);check(snapshot(first).equals(snapshot(second)),"distinct copies equal complete state");disjoint(first,second);
  for(Object node:arrays(first)){if(node instanceof int[]&&((int[])node).length>0)((int[])node)[((int[])node).length-1]^=123;}
  check(snapshot(second).equals(snapshot(actual(2))),"mutating all arrays in one copy cannot poison other copies/template");
  String expected=snapshot(second);ExecutorService pool=Executors.newFixedThreadPool(6);List<Future<RSModel>> jobs=new ArrayList<>();
  for(int i=0;i<120;i++){final int owner=i%3;jobs.add(pool.submit(()->actual(owner)));}
  Set<Object> all=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
  for(Future<RSModel> job:jobs){RSModel model=job.get();check(expected.equals(snapshot(model)),"concurrent exact model graph");for(Object node:arrays(model))check(all.add(node),"concurrent mutable arrays disjoint");disjoint(a,model);}
  pool.shutdown();check(pool.awaitTermination(10,TimeUnit.SECONDS),"threads terminated");
 }
 static void edgeModelsAndBudgets()throws Exception{
  List<RSModel> models=new ArrayList<>();models.add(new RSModel(0,0));
  RSModel degenerate=new RSModel(16,4);int v=degenerate.insertVertex(5,9,13);degenerate.insertVertex(99,77,55);degenerate.insertFace(3,new int[]{v,v,v},-12345,-12345,false);models.add(degenerate);
  Constructor<RSModel> ctor=RSModel.class.getDeclaredConstructor(int.class,int.class,boolean.class,boolean.class,boolean.class,boolean.class,boolean.class);ctor.setAccessible(true);
  RSModel aliased=ctor.newInstance(8,4,true,false,false,false,false);int a=aliased.insertVertex(0,0,0),b=aliased.insertVertex(128,0,0),c=aliased.insertVertex(0,-64,128);aliased.insertFace(3,new int[]{a,b,c},-12345,-12345,false);BaselineReuseFixture.set(aliased,"faceParam1",new int[][]{{0},{1},{2},{3}});models.add(aliased);
  for(RSModel model:models){client.clearBrowserPresentationCloneCache();RSModel literal=copyModel(model);source(model);pair(model,literal,0,"edge cold");pair(model,literal,2,"edge warm");}
  RSModel prototype=BaselineReuseFixture.model(),literal=prototype.clone();RSModel.BrowserCloneTemplate recipe=RSModel.browserCloneTemplate(prototype,literal,1<<20);check(recipe!=null,"edge recipe admitted");
  String before=snapshot(prototype),target=snapshot(literal);check(RSModel.browserCloneTemplate(prototype,literal,recipe.retainedBytes()-1)==null,"exact preallocation retained bound");check(before.equals(snapshot(prototype))&&target.equals(snapshot(literal)),"budget refusal leaves inputs unchanged");
  RSModel large=new RSModel(4,1025);a=large.insertVertex(0,0,0);b=large.insertVertex(128,0,0);c=large.insertVertex(0,-64,128);for(int i=0;i<1025;i++)large.insertFace(3,new int[]{a,b,c},-12345,-12345,false);
  client.clearBrowserPresentationCloneCache();source(large);RSModel control=copyModel(large);pair(large,control,0,"large cold refusal");long[] prior=client.getBrowserCloneTemplateWork();pair(large,control,0,"large warm refusal");long[] work=client.getBrowserCloneTemplateWork();if(BrowserPresentationCloneCache.isEnabled())check(work[3]==prior[3]&&work[11]>prior[11],"large valid model uses literal refusal");
  // Exercise the actual cache entry limit using distinct valid definitions, not a mocked table.
  java.util.List defs=(java.util.List)BaselineReuseFixture.field(EntityHandler.class,"objects").get(null);
  for(int id=defs.size();id<300;id++){com.openrsc.client.entityhandling.defs.GameObjectDef def=new com.openrsc.client.entityhandling.defs.GameObjectDef("Tree","fixture","Chop","Examine",1,1,1,0,"tree",id);def.modelID=0;defs.add(def);}
  client.clearBrowserPresentationCloneCache();source(BaselineReuseFixture.model());for(int id=0;id<300;id++){RSModel result=client.cloneBrowserPresentationGameObject(id,2);check(result!=null,"capacity fallback still supplies literal clone");}
  work=client.getBrowserCloneTemplateWork();if(BrowserPresentationCloneCache.isEnabled())check(work[15]==256&&work[11]>0&&work[13]+2*1024*1024<=16*1024*1024,"entry/byte limits retain fallback "+Arrays.toString(work));
  client.clearBrowserPresentationCloneCache();check(client.getBrowserCloneTemplateWork()[13]==0,"reset releases retained recipe payload");
  source(BaselineReuseFixture.model());actual(0);actual(0);long priorStaticHits=client.getBrowserCloneTemplateWork()[3];
  client.cloneBrowserPresentationGameObject(74,0);actual(0);if(BrowserPresentationCloneCache.isEnabled())check(client.getBrowserCloneTemplateWork()[3]==priorStaticHits+1,"animated fallback preserves unrelated warm recipe");
 }
 static void realOwners()throws Exception{
  client.clearBrowserPresentationCloneCache();source(BaselineReuseFixture.model());
  for(int pass=0;pass<4;pass++){
   LegacyStaticSceneDeltaBatch batch=new LegacyStaticSceneDeltaBatch(false);
   for(int i=0;i<30;i++)batch.addTileUpdate(i%3,10+i%10*2,10+i/10*2,(pass+i)%8);
   BaselineReuseFixture.call(client.packetHandler,"applyLegacyGameObjectBatch",new Class<?>[]{LegacyStaticSceneDeltaBatch.class},batch);
   for(int i=0;i<client.getGameObjectInstanceCount();i++)digest.update(snapshot(client.getGameObjectInstanceModel(i)).getBytes("UTF-8"));
   for(int i=0;i<5;i++){RSModel m=(RSModel)BaselineReuseFixture.call(client,"createStaticPresentationGameObjectModel",new Class<?>[]{SceneBaselineState.Record.class,int.class,int.class},new SceneBaselineState.Record(0,35,35,i,0),i,7+pass);digest.update(snapshot(m).getBytes("UTF-8"));}
   BaselineReuseFixture.snapshot(client);
  }
  long[] w=client.getBrowserCloneTemplateWork();if(BrowserPresentationCloneCache.isEnabled()){check(w[3]>0,"DELTA owner actually hits shared cache");check(w[9]>0,"canonical owner actually hits shared cache");}
 }
 public static void main(String[] args)throws Exception{
  digest=MessageDigest.getInstance("SHA-256");BaselineReuseFixture.digest=digest;Config.F_CACHE_DIR=java.nio.file.Paths.get(args[0],"Client_Base/Cache").toString();BaselineReuseFixture.definitions();Renderer3DSettings.setBrowserPresentationAvailable(true);RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);client=BaselineReuseFixture.client();
  mutationInventory();aliasesAndConcurrency();edgeModelsAndBudgets();realOwners();
  StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));System.out.println("TRACE "+hex+" cases="+cases+" expectedFailures="+errors);System.out.println("PASS checks="+checks+" fields="+fields().size()+" work="+Arrays.toString(client.getBrowserCloneTemplateWork())+" independent literal/full-state/alias/concurrency/real-owner review; no GPU/FPS");
 }
}
