package orsc;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Pure CPU reference/replay contracts. No GL context or performance claims. */
public final class BrowserSkyFixture {
    static long checks;
    static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static final RendererToneSettings.Mode[] MODES={RendererToneSettings.Mode.DAY,RendererToneSettings.Mode.SUNRISE_AMBER,RendererToneSettings.Mode.ROSE_DUSK,RendererToneSettings.Mode.COOL_NIGHT,RendererToneSettings.Mode.DEEP_BLUE};
    static Renderer3DFrame frame(int level,int fog,int x,int yaw){
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");Scene scene=new Scene(new MudClientGraphics(64,64,1),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);
        scene.fogLandscapeDistance=fog;scene.setCamera(x,-4,x*2,768,yaw,0,600);scene.setRendererSignedWorldLevel(level);scene.endScene(-113);return scene.getRenderer3DFrame();
    }
    static Renderer3DWorldChunkFrame.ChunkMesh chunk(int plane,int originZ,boolean object,long signature){
        return new Renderer3DWorldChunkFrame.ChunkMesh(plane,0,0,0,originZ,new int[]{0,0,0,128,0,0,0,0,128},new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-2},new int[]{0},new Renderer3DModelKind[]{Renderer3DModelKind.TERRAIN},1,0,0,object,signature);
    }
    static WorldSkyGeometry.Sink recording(final SkyStreamRecorder recorder){return new WorldSkyGeometry.Sink(){
        public void layer(int layer){} public void glEnable(int x)throws Exception{recorder.glEnable(x);}public void glDisable(int x)throws Exception{recorder.glDisable(x);}public void glBlendFunc(int a,int b)throws Exception{recorder.glBlendFunc(a,b);}public void glBegin(int x)throws Exception{recorder.glBegin(x);}public void glEnd()throws Exception{recorder.glEnd();}public void glColor4f(int palette,float r,float g,float b,float a)throws Exception{recorder.glColor4f(r,g,b,a);}public void glVertex3f(float x,float y,float z)throws Exception{recorder.glVertex3f(x,y,z);}
    };}
    static float number(DataInputStream in)throws Exception{return Float.intBitsToFloat(in.readInt());}
    static ArrayList<float[]> triangles(byte[] stream)throws Exception {
        ArrayList<float[]> result=new ArrayList<float[]>(),primitive=new ArrayList<float[]>();DataInputStream in=new DataInputStream(new ByteArrayInputStream(stream));float[] color=new float[4];int mode=0;
        while(in.available()>0){int op=in.readInt();switch(op){case 1:case 2:in.readInt();break;case 3:in.readInt();in.readInt();break;case 4:mode=in.readInt();primitive.clear();break;case 5:if(mode==4)result.addAll(primitive);else if(mode==7){for(int at=0;at<primitive.size();at+=4)for(int corner:new int[]{0,1,2,0,2,3})result.add(primitive.get(at+corner));}else throw new AssertionError("Unsupported reference primitive");mode=0;break;case 6:for(int n=0;n<4;n++)color[n]=number(in);break;case 7:float[] vertex=new float[7];for(int n=0;n<3;n++)vertex[n]=number(in);System.arraycopy(color,0,vertex,3,4);primitive.add(vertex);break;default:throw new AssertionError("Unknown source GL command "+op);}}
        return result;
    }
    static void compareReplay(float[] geometry,int[] ranges,float[] values,float[] palette,byte[] reference)throws Exception {
        ArrayList<float[]> expected=triangles(reference);int index=0;
        for(int layer=0;layer<4;layer++){
            if((layer==1||layer==2)&&values[1]!=1||layer==3&&values[2]!=1)continue;
            for(int vertex=ranges[layer*3];vertex<ranges[layer*3]+ranges[layer*3+1];vertex++){
                require(index<expected.size(),"extra replay vertex");float[] old=expected.get(index++);int at=vertex*4,slot=(int)geometry[at+3];
                for(int n=0;n<3;n++)require(Float.floatToRawIntBits(old[n])==Float.floatToRawIntBits(geometry[at+n]),"replay XYZ differs");
                for(int n=0;n<4;n++)require(Float.floatToRawIntBits(old[3+n])==Float.floatToRawIntBits(palette[slot*4+n]),"replay color differs at "+index+" channel"+n+" old"+old[3+n]+" new"+palette[slot*4+n]);
            }
        }
        require(index==expected.size(),"missing replay vertices");
    }
    public static void main(String[] args)throws Exception {
        OpenGLSkySettings.setMode(OpenGLSkySettings.Mode.WORLD_DOME);
        for(RendererToneSettings.Mode mode:MODES)for(float radius:new float[]{2048,3500.25f,10000.8f}){
            RendererDayNightCycle.Presentation p=RendererDayNightCycle.Presentation.fromMode(mode,1,mode.id);OriginalSkyReference old=new OriginalSkyReference();old.emit(radius,p);SkyStreamRecorder now=new SkyStreamRecorder();new WorldSkyGeometry().emit(radius,p,recording(now));
            require(Arrays.equals(old.gl.bytes.toByteArray(),now.bytes.toByteArray()),"native GL stream changed "+mode.id+" radius"+radius);
        }
        for(int fog:new int[]{2000,7000,16000}){
            BrowserSkyData sky=new BrowserSkyData();Renderer3DFrame frame=frame(0,fog,0,0);float[] geometry=null;int[] ranges=null;
            for(RendererToneSettings.Mode mode:MODES){
                RendererToneSettings.setMode(mode);BrowserSkyData.Frame data=sky.prepare(frame);require(data.enabled,"surface sky disabled");
                if(geometry==null){require(data.geometry!=null&&data.ranges!=null,"initial sky geometry missing");geometry=data.geometry;ranges=data.ranges;}else require(data.geometry==null&&data.ranges==null,"color phase regenerated trig geometry");
                require(data.palette.length==4096&&data.values[3]>0&&data.values[3]<=1024,"palette not bounded");
                OriginalSkyReference old=new OriginalSkyReference();old.emit(Math.max(2048,frame.getFogDistance()*.92f),RendererDayNightCycle.Presentation.fromMode(mode,1,mode.id));compareReplay(geometry,ranges,data.values,data.palette,old.gl.bytes.toByteArray());
            }
            require(sky.geometryBuilds()==1,"steady view rebuilt geometry");float[] originalMatrix=sky.prepare(frame).values.clone();
            require(Arrays.equals(Arrays.copyOfRange(originalMatrix,4,20),Arrays.copyOfRange(sky.prepare(frame(0,fog,10000,0)).values,4,20)),"sky translated with camera position");
            require(!Arrays.equals(Arrays.copyOfRange(originalMatrix,4,20),Arrays.copyOfRange(sky.prepare(frame(0,fog,0,128)).values,4,20)),"sky ignored camera yaw");
            require(!sky.prepare(frame(-1,fog,0,0)).enabled&&!sky.prepare(frame(-10,fog,0,0)).enabled,"signed underground sky visible");
            OpenGLSkySettings.setMode(OpenGLSkySettings.Mode.SCREEN);require(!sky.prepare(frame).enabled,"unported legacy screen mode silently substituted");OpenGLSkySettings.setMode(OpenGLSkySettings.Mode.WORLD_DOME);
            require(sky.prepare(frame).geometry==null,"temporary disabled state discarded valid bounded geometry");int builds=sky.geometryBuilds();sky.clear();require(sky.prepare(frame).geometry!=null&&sky.geometryBuilds()==builds+1,"session clear failed to restore geometry");
        }
        Renderer3DFrame policy=frame(0,7000,0,0);policy.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunk(2,0,true,1),chunk(0,0,false,2),chunk(1,0,false,3))));require(BrowserSkyData.enabled(policy),"support/object planes hid surface terrain sky");
        policy.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk(1,0,false,4))));require(!BrowserSkyData.enabled(policy),"legacy upper primary heuristic changed");
        policy.setWorldChunkFrame(Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk(0,3000*128,false,5))));require(!BrowserSkyData.enabled(policy),"legacy underground coordinate heuristic changed");
        System.out.println("PASS browser sky "+checks+" CPU checks:15 exact native primitive streams, all-phase cached XYZ/RGBA replay, camera rotation/translation, signed/legacy scope and bounded cache lifecycle");
    }
}
