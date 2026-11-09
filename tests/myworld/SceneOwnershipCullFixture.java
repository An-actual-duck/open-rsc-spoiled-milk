package orsc.graphics.three;

import java.util.*;
import orsc.BrowserFrameSinkRegistry;

/** Differential retained geometry, ordinary picks and pixels for early/late ownership tests. */
public final class SceneOwnershipCullFixture {
    static int checks,clipped;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class View {
        final ClericWellSpotClientTest.View view;
        final RSModel front;
        View(boolean early) {
            System.setProperty("spoiledmilk.browserEarlyFaceCull",String.valueOf(early));view=new ClericWellSpotClientTest.View();
            add(Renderer3DModelKind.TERRAIN,300,96,-23456,1,false);
            front=add(Renderer3DModelKind.GAME_OBJECT,60,-60,-12345,2,false);
            RSModel proxy=add(Renderer3DModelKind.WALL_OBJECT,20,-30,Scene.TRANSPARENT,3,true);proxy.setPickBoundsScale(3);
            add(Renderer3DModelKind.WALL,30,100,-12345,4,false);
            add(Renderer3DModelKind.ROOF,40,200,-12345,5,false);
            RSModel vertical=new RSModel(4,1);int[] v={vertical.insertVertex(-80,-240,0),vertical.insertVertex(80,-240,0),vertical.insertVertex(80,240,0),vertical.insertVertex(-80,240,0)};
            vertical.insertFace(4,v,-12345,-12345,false);vertical.key=6;vertical.setRenderer3DModelKind(Renderer3DModelKind.UNCLASSIFIED);view.s.addModel(vertical);
            RSModel far=add(Renderer3DModelKind.GAME_OBJECT,10,0,-12345,7,false);far.setTranslate(100000,0,0);
            view.s.setFrustum(0,11,7,1);byte[] texels=new byte[4096];Arrays.fill(texels,(byte)1);for(int y=25;y<39;y++)for(int x=25;x<39;x++)texels[y*64+x]=0;
            view.s.loadTexture(0,new int[]{0xff00ff,0x777777},0,texels);
        }
        RSModel add(Renderer3DModelKind kind,int half,int y,int material,int key,boolean proxy) {
            RSModel model=ClericWellSpotClientTest.horizontal(half,y,material);model.key=key;model.setRenderer3DModelKind(kind);model.setInteractionOnly(proxy);view.s.addModel(model);return model;
        }
        Result draw(int x,int y,int distance,int pitch,int yaw,int material,boolean force) {
            front.faceTextureFront[0]=material;front.faceTextureBack[0]=material;
            view.s.setCamera(0,-4,0,pitch,yaw,0,distance);if(force)view.s.forceLegacyWorldRasterOnce();view.draw(x,y);
            Renderer3DFrame frame=view.s.getRenderer3DFrame();StringBuilder value=new StringBuilder();
            RSModel[] picked=view.s.b((byte)100);for(int n=0;n<view.s.b(0);n++)value.append('P').append(picked[n].key).append(',');
            for(Renderer3DFrame.FaceCommand face:frame.getWorldFaces()) {
                value.append('|').append(face.getModelKind()).append(':').append(face.getModelIndex()).append(':').append(face.getFaceId()).append(':').append(face.getTexture()).append(':').append(face.getColor()).append(':').append(face.getOrientation()).append(':').append(face.getAverageDepth()).append(':').append(face.getLegacyDrawOrder());
                value.append(Arrays.toString(face.getCameraX())).append(Arrays.toString(face.getCameraY())).append(Arrays.toString(face.getCameraZ()));
                value.append(Arrays.toString(face.getRenderCameraX())).append(Arrays.toString(face.getRenderCameraY())).append(Arrays.toString(face.getRenderCameraZ()));
                value.append(Arrays.toString(face.getRenderScreenX())).append(Arrays.toString(face.getRenderScreenY())).append(Arrays.toString(face.getRenderLight())).append(Arrays.toString(face.getRenderBaseLight()));
                value.append(Arrays.toString(face.getRenderTextureU())).append(Arrays.toString(face.getRenderTextureV()));
                int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;for(int z:face.getCameraZ()){min=Math.min(min,z);max=Math.max(max,z);}if(min<frame.getNearPlane()&&max>frame.getNearPlane())clipped++;
            }
            value.append("depth:").append(frame.getDepthFrame()!=null);int[] pixels=view.g.pixelData.clone();frame.release();return new Result(value.toString(),pixels);
        }
    }
    static final class Result {final String values;final int[] pixels;Result(String values,int[] pixels){this.values=values;this.pixels=pixels;}}
    public static void main(String[] args) {
        for(String name:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster"})System.setProperty("spoiledmilk."+name,"true");
        System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
        BrowserFrameSinkRegistry.install(new BrowserFrameSinkRegistry.Sink(){public void present(java.awt.image.BufferedImage i,orsc.graphics.Renderer2DFrame c,Renderer3DFrame f){}public boolean needsCpuDepthFrame(){return false;}});
        require(Renderer3DSettings.canSkipProjectedWorldCapture()&&Renderer3DSettings.canUseResidentObjectChunks(),"Fixture did not activate real resident ownership path");
        View late=new View(false),early=new View(true);require(!late.view.s.isEarlyResidentFaceCullEnabled()&&early.view.s.isEarlyResidentFaceCullEnabled(),"Controlled early/late paths not selected");
        int[][] points={{256,192},{210,160},{300,225},{0,0},{511,383}};
        for(int distance:new int[]{20,100,600})for(int pitch:new int[]{640,768,900})for(int yaw:new int[]{0,256})for(int material:new int[]{-12345,Scene.TRANSPARENT,0})for(boolean force:new boolean[]{false,true})for(int[] point:points) {
            Result a=late.draw(point[0],point[1],distance,pitch,yaw,material,force),b=early.draw(point[0],point[1],distance,pitch,yaw,material,force);
            require(a.values.equals(b.values),"Retained faces/picks differ: distance"+distance+" pitch"+pitch+" yaw"+yaw+" material"+material+" force"+force+" point"+Arrays.toString(point));
            require(Arrays.equals(a.pixels,b.pixels),"Raster pixels differ under early ownership");
        }
        require(clipped>0,"Fixture did not exercise actual near-plane crossing geometry");
        BrowserFrameSinkRegistry.install(null);require(!early.view.s.isEarlyResidentFaceCullEnabled(),"Desktop fallback retained browser optimization");
        System.out.println("PASS "+checks+" actual Scene early/late retained face/pick/pixel differential checks, near-plane crossings="+clipped);
    }
}
