package orsc;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.MudClientGraphics;
import orsc.graphics.three.*;

/** CPU-only regression: raw Scene construction races; published frames do not. */
public final class RendererBenchmarkFrameObserverFixture {
    static void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    static Field field(Class<?> owner,String name) throws Exception {
        Field f=owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static Method observer(String name,Class<?>... parameters) throws Exception {
        Method m=RendererBenchmarkClient.class.getDeclaredMethod(name,parameters); m.setAccessible(true); return m;
    }
    static Renderer3DFrame frame() throws Exception {
        Constructor<?> constructor=Renderer3DFrame.class.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object[] args=new Object[constructor.getParameterCount()]; Arrays.fill(args,0); args[3]=960; args[4]=540; args[args.length-1]=null;
        Renderer3DFrame frame=(Renderer3DFrame)constructor.newInstance(args);
        Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,10,9,0,0,
            new int[]{0,0,0,128,0,0,0,0,128},new float[]{0,1,0},new float[]{0,0,1},new int[]{0,0,0},
            new int[]{0,1,2},new int[]{-2},new int[]{8},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},1,0,0,12L);
        frame.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk)));
        return frame;
    }
    static Renderer3DFrame.CharacterSprite actor() throws Exception {
        Constructor<?> constructor=null;
        for(Constructor<?> candidate:Renderer3DFrame.CharacterSprite.class.getDeclaredConstructors()) if(Modifier.isPrivate(candidate.getModifiers())) constructor=candidate;
        require(constructor!=null,"missing native character constructor"); constructor.setAccessible(true);
        Class<?>[] types=constructor.getParameterTypes(); Object[] args=new Object[types.length]; int lastBoolean=-1;
        for(int i=0;i<args.length;i++) {
            args[i]=types[i]==String.class ? "" : types[i]==boolean.class ? Boolean.FALSE : Integer.valueOf(0);
            if(types[i]==boolean.class) lastBoolean=i;
        }
        args[lastBoolean]=true; args[args.length-2]=10; args[args.length-1]=10;
        return (Renderer3DFrame.CharacterSprite)constructor.newInstance(args);
    }
    @SuppressWarnings("unchecked")
    static List<Renderer3DFrame.CharacterSprite> constructionList(Renderer3DFrame frame) throws Exception {
        return (List<Renderer3DFrame.CharacterSprite>)field(Renderer3DFrame.class,"characterSprites").get(frame);
    }
    static void publish(OpenGLFramePresenter presenter,Renderer3DFrame frame) throws Exception {
        Frame pending=Frame.fromImage(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),1,
            ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,new FrameBufferPool(),Renderer2DFrame.EMPTY,frame,null);
        synchronized(field(OpenGLFramePresenter.class,"frameLock").get(presenter)) {
            field(OpenGLFramePresenter.class,"pendingFrame").set(presenter,pending);
        }
    }
    public static void main(String[] ignored) throws Exception {
        Renderer3DFrame stable=frame(), constructing=frame(); Renderer3DFrame.CharacterSprite actor=actor();
        constructionList(stable).add(actor); constructionList(stable).add(actor);
        List<Renderer3DFrame.CharacterSprite> growing=constructionList(constructing); growing.add(actor); growing.add(actor);
        Scene scene=new Scene(new MudClientGraphics(32,32,1),4,16,4);
        field(Scene.class,"renderer3DFrame").set(scene,constructing); // Same early exposure as endScene.
        OpenGLFramePresenter presenter=new OpenGLFramePresenter("CPU-only fixture",false,false);
        publish(presenter,stable);
        Renderer3DFrame sampled=(Renderer3DFrame)observer("publishedFrame",OpenGLFramePresenter.class).invoke(null,presenter);
        Iterator<Renderer3DFrame.CharacterSprite> oldIterator=scene.getRenderer3DFrame().getCharacterSprites().iterator();
        oldIterator.next();
        CountDownLatch mutate=new CountDownLatch(1),mutated=new CountDownLatch(1);
        Thread producer=new Thread(() -> {
            try { require(mutate.await(2,TimeUnit.SECONDS),"producer latch timeout"); growing.add(actor); mutated.countDown(); }
            catch(InterruptedException e) { throw new AssertionError(e); }
        },"scene-construction-fixture");
        producer.start(); mutate.countDown(); require(mutated.await(2,TimeUnit.SECONDS),"construction timeout");
        boolean oldFailed=false;
        try { oldIterator.next(); } catch(ConcurrentModificationException expected) { oldFailed=true; }
        require(oldFailed,"old raw Scene iteration did not reproduce failure");
        Method actors=observer("onScreenActors",Renderer3DFrame.class),roofs=observer("roofState",Renderer3DFrame.class);
        for(int i=0;i<1000;i++) {
            require(((Integer)actors.invoke(null,sampled))==2,"published count changed with raw construction");
            require(roofs.invoke(null,sampled).toString().contains("residentRoofTriangles"),"missing roof observation");
        }
        producer.join(2000); require(!producer.isAlive(),"producer remained alive");
        stable.release(); require(((Integer)actors.invoke(null,sampled))==2,"release recycled immutable character metadata");
        publish(presenter,constructing);
        Renderer3DFrame next=(Renderer3DFrame)observer("publishedFrame",OpenGLFramePresenter.class).invoke(null,presenter);
        require(((Integer)actors.invoke(null,next))==3,"completed new frame not observed");
        String oldJson=observer("shadowSnapshotJson",long[].class).invoke(null,new long[]{1,1,0,3,4,5,6,7,8}).toString();
        require(!oldJson.contains("farMapSize") && oldJson.contains("\"coherent\":true"),"old9 snapshot compatibility");
        String newJson=observer("shadowSnapshotJson",long[].class).invoke(null,new long[]{1,1,0,3,4,5,6,7,8,4096,1,750}).toString();
        require(newJson.contains("\"farMapSize\":4096") && newJson.contains("\"historyActive\":true") && newJson.contains("\"blendPermille\":750"),"new12 snapshot metadata");
        field(OpenGLFramePresenter.class,"pendingFrame").set(presenter,null);
        long began=System.nanoTime(); boolean missingRejected=false;
        try { observer("publishedFrame",OpenGLFramePresenter.class).invoke(null,presenter); }
        catch(InvocationTargetException e) { missingRejected=e.getCause() instanceof IllegalStateException; }
        require(missingRejected && System.nanoTime()-began<4000000000L,"missing published frame did not fail bounded/explicitly");
        System.out.println("PASS: old raw Scene CME; published actor/roof stability, release safety, next frame, bounded missing-frame refusal and9/12 shadow snapshots");
    }
}
