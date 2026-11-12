package orsc.graphics.three;

import java.lang.reflect.*;
import java.util.*;
import orsc.Config;
import orsc.graphics.two.GraphicsController;

/** Actual constructed Scene operations, paired same-artifact private ON/OFF. */
public final class NullModelRemovalFixture {
    private static int checks,operations;
    private static final Field MODELS=field("models"),COUNT=field("modelCount"),METADATA=field("m_jb"),SENTINEL=field("m_T");
    private static Field field(String name){try{Field field=Scene.class.getDeclaredField(name);field.setAccessible(true);return field;}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static Scene scene()throws Exception{
        Config.S_WANT_CUSTOM_SPRITES=true;
        Constructor<GraphicsController> ctor=GraphicsController.class.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);
        return new Scene(ctor.newInstance(32,32,1),1,8,2);
    }
    private static void selected(boolean enabled){System.setProperty("spoiledmilk.browserStaticSceneReuse",Boolean.toString(enabled));}
    private static void equal(Scene expected,Scene actual)throws Exception{
        int count=COUNT.getInt(expected);check(count==COUNT.getInt(actual),"active count");
        RSModel[] e=(RSModel[])MODELS.get(expected),a=(RSModel[])MODELS.get(actual);int[] em=(int[])METADATA.get(expected),am=(int[])METADATA.get(actual);
        check(e.length==a.length&&em.length==am.length,"capacity and parallel capacity");
        for(int i=0;i<e.length;i++){check(e[i]==a[i],"full model storage/order/tail "+i);check(em[i]==am[i],"full parallel metadata "+i);if(i<count)check(a[i]!=null,"legal nonnull active prefix");}
    }
    private static void remove(Scene off,Scene on,RSModel model)throws Exception{selected(false);off.removeModel(model);selected(true);on.removeModel(model);operations++;equal(off,on);}
    private static void add(Scene off,Scene on,RSModel model)throws Exception{selected(false);off.addModel(model);selected(true);on.addModel(model);operations++;equal(off,on);}
    private static void clear(Scene off,Scene on)throws Exception{selected(false);off.removeAllGameObjects(false);selected(true);on.removeAllGameObjects(false);operations++;equal(off,on);}
    private static void bulk(Scene off,Scene on,RSModel[] models)throws Exception{selected(false);off.removeModels(models,models.length);selected(true);on.removeModels(models,models.length);operations++;equal(off,on);}
    public static void main(String[] args)throws Exception{
        Scene off=scene(),on=scene();RSModel[] pool=new RSModel[40];for(int i=0;i<pool.length;i++)pool[i]=new RSModel(1,1);
        remove(off,on,null);add(off,on,null);check(COUNT.getInt(on)==0,"empty/addnull remains empty");
        for(int i=0;i<80;i++)add(off,on,pool[i%pool.length]);
        check(((RSModel[])MODELS.get(on)).length>1,"actual capacity growth exercised");
        int count=COUNT.getInt(on);for(int i=0;i<count;i++){((int[])METADATA.get(off))[i]=i*17+3;((int[])METADATA.get(on))[i]=i*17+3;}
        // Match render's legal sentinel boundary, using a shared sentinel only outside the prefix.
        Method capacity=Scene.class.getDeclaredMethod("ensureModelCapacity",int.class);capacity.setAccessible(true);capacity.invoke(off,count+1);capacity.invoke(on,count+1);
        RSModel sentinel=(RSModel)SENTINEL.get(on);((RSModel[])MODELS.get(off))[count]=sentinel;((RSModel[])MODELS.get(on))[count]=sentinel;
        for(int signed:new int[]{0,-1,10}){off.setRendererSignedWorldLevel(signed);on.setRendererSignedWorldLevel(signed);for(int i=0;i<720;i++)remove(off,on,null);}
        check(((RSModel[])MODELS.get(on))[count]==sentinel,"null removal preserves outside-prefix sentinel");
        remove(off,on,pool[0]);remove(off,on,pool[0]);remove(off,on,new RSModel(1,1));clear(off,on);
        for(int i=0;i<3;i++)add(off,on,pool[0]);remove(off,on,pool[0]);check(COUNT.getInt(on)==1,"legacy adjacent duplicate skip preserved");remove(off,on,pool[0]);check(COUNT.getInt(on)==0,"duplicate remainder preserved");
        Random random=new Random(7192321L);
        for(int step=0;step<4000;step++){
            int op=random.nextInt(12),n=COUNT.getInt(on);RSModel model=random.nextInt(3)==0?null:pool[random.nextInt(pool.length)];
            if(op<5&&n<180)add(off,on,model);else if(op<10)remove(off,on,model);else if(op==10)bulk(off,on,new RSModel[]{null,pool[random.nextInt(pool.length)],pool[random.nextInt(pool.length)]});else clear(off,on);
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"operations\":"+operations+",\"scope\":\"Constructed production Scene legal-state ON/OFF differential; no browser/GPU/FPS or live array capture claim.\"}");
    }
}
