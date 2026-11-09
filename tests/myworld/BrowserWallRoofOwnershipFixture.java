package orsc.graphics.three;

import java.util.*;
import orsc.BrowserFrameSinkRegistry;
import orsc.graphics.two.BrowserOwnershipGraphics;

/** Real Scene ownership differential: picks/occluders, sprite anchors/order, pixels and resident meshes. */
public final class BrowserWallRoofOwnershipFixture {
    static int checks,removed;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Result {
        String picks,anchors,resident,faces;int wallRoofCount;int[] pixels;
    }
    static final class View {
        final BrowserOwnershipGraphics g=new BrowserOwnershipGraphics();final Scene s;final RSModel[] models=new RSModel[6];final RSModel front,wall,roof;
        View(boolean full) {
            System.setProperty("spoiledmilk.browserOwnsWallsAndRoofs",String.valueOf(full));s=new Scene(g,4,16,4);
            s.setMidpoints(192,true,512,256,192,9,256);s.fogLandscapeDistance=4000;s.fogEntityDistance=4000;
            models[0]=model(Renderer3DModelKind.TERRAIN,300,96,-23456,1,false);
            front=models[1]=model(Renderer3DModelKind.GAME_OBJECT,60,-60,-12345,2,false);
            models[2]=model(Renderer3DModelKind.WALL_OBJECT,20,-30,Scene.TRANSPARENT,3,true);models[2].setPickBoundsScale(3);
            wall=models[3]=model(Renderer3DModelKind.WALL,60,-120,-12345,4,false);wall.m_db=true;
            roof=models[4]=model(Renderer3DModelKind.ROOF,45,-90,-12345,5,false);roof.m_db=true;
            models[5]=model(Renderer3DModelKind.UNCLASSIFIED,30,200,-12345,6,false);
            for(RSModel m:models)s.addModel(m);
            s.drawSprite(101,-20,5001,20,-4,32,64,(byte)0);s.drawSprite(102,20,5002,-20,-4,32,64,(byte)0);
            s.setFrustum(0,11,7,1);byte[] texels=new byte[4096];Arrays.fill(texels,(byte)1);for(int y=25;y<39;y++)for(int x=25;x<39;x++)texels[y*64+x]=0;
            s.loadTexture(0,new int[]{0xff00ff,0x777777},0,texels);
        }
        RSModel model(Renderer3DModelKind kind,int half,int y,int material,int key,boolean proxy) {
            RSModel m=ClericWellSpotClientTest.horizontal(half,y,material);m.key=key;m.setRenderer3DModelKind(kind);m.setInteractionOnly(proxy);return m;
        }
        Result draw(int x,int y,int distance,int pitch,int yaw,int material,boolean force) {
            for(RSModel m:new RSModel[]{front,wall,roof}){m.faceTextureFront[0]=material;m.faceTextureBack[0]=material;}
            s.setCamera(0,-4,0,pitch,yaw,0,distance);if(force)s.forceLegacyWorldRasterOnce();g.entities.clear();Arrays.fill(g.pixelData,0x23262c);s.setMouseLoc(0,x,y);s.endScene(0);
            Renderer3DFrame frame=s.getRenderer3DFrame();Result result=new Result();StringBuilder pick=new StringBuilder(),anchors=new StringBuilder(),faces=new StringBuilder(),resident=new StringBuilder();
            RSModel[] selected=s.b((byte)100);int[] faceIds=s.getQB((byte)0);for(int n=0;n<s.b(0);n++)pick.append(selected[n].key).append('/').append(faceIds[n]).append(',');
            for(Renderer3DFrame.SpriteAnchor a:frame.getSpriteAnchors())anchors.append(a.getSpriteId()).append(':').append(a.getPickIndex()).append(':').append(a.getCameraX()).append(':').append(a.getCameraY()).append(':').append(a.getCameraZ()).append(':').append(a.getDrawX()).append(':').append(a.getDrawY()).append(':').append(a.getDrawWidth()).append(':').append(a.getDrawHeight()).append(';');
            anchors.append(g.entities.toString());
            for(Renderer3DFrame.FaceCommand face:frame.getWorldFaces()) {
                if(face.getModelKind()==Renderer3DModelKind.WALL||face.getModelKind()==Renderer3DModelKind.ROOF)result.wallRoofCount++;
                faces.append(face.getModelKind()).append(':').append(face.getFaceId()).append(':').append(face.getTexture()).append(':').append(face.getColor()).append(Arrays.toString(face.getRenderCameraX())).append(Arrays.toString(face.getRenderCameraY())).append(Arrays.toString(face.getRenderCameraZ())).append(Arrays.toString(face.getRenderLight()));
            }
            Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,models,models.length);
            for(int n=0;n<mesh.getVertexCount()*3;n++)resident.append(mesh.getVertexCoord(n)).append(',');
            for(int n=0;n<mesh.getVertexCount();n++)resident.append(mesh.getVertexTextureU(n)).append(':').append(mesh.getVertexTextureV(n)).append(':').append(mesh.getVertexLight(n)).append(',');
            for(int n=0;n<mesh.getIndexCount();n++)resident.append(mesh.getIndex(n)).append(',');
            for(int n=0;n<mesh.getTriangleCount();n++)resident.append(mesh.getTriangleTexture(n)).append(':').append(mesh.getTriangleFallbackColor(n)).append(':').append(mesh.getTriangleModelKind(n)).append(',');
            result.picks=pick.toString();result.anchors=anchors.toString();result.faces=faces.toString();result.resident=resident.toString();result.pixels=g.pixelData.clone();frame.release();return result;
        }
    }
    static BrowserFrameSinkRegistry.Sink sink(final boolean cpu) {return new BrowserFrameSinkRegistry.Sink(){public void present(java.awt.image.BufferedImage i,orsc.graphics.Renderer2DFrame c,Renderer3DFrame f){}public boolean needsCpuDepthFrame(){return cpu;}};}
    public static void main(String[] args) {
        for(String name:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserEarlyFaceCull"})System.setProperty("spoiledmilk."+name,"true");
        System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");BrowserFrameSinkRegistry.install(sink(false));
        View ordinary=new View(false),full=new View(true);require(!ordinary.s.isBrowserFullResidentOwnershipEnabled()&&full.s.isBrowserFullResidentOwnershipEnabled(),"GPU-only ownership declaration not selected");
        int[][] points={{256,192},{210,160},{300,225},{0,0},{511,383}};
        for(int distance:new int[]{20,100,600})for(int pitch:new int[]{640,768,900})for(int yaw:new int[]{0,256})for(int material:new int[]{-12345,Scene.TRANSPARENT,0})for(boolean force:new boolean[]{false,true})for(int[] point:points) {
            Result a=ordinary.draw(point[0],point[1],distance,pitch,yaw,material,force),b=full.draw(point[0],point[1],distance,pitch,yaw,material,force);
            require(a.picks.equals(b.picks),"Wall/roof ownership altered model/sprite/interaction-proxy picks");require(a.anchors.equals(b.anchors),"Sprite anchors or relative entity draw order changed");
            require(a.resident.equals(b.resident),"Resident mesh content changed after projected face omission");require(Arrays.equals(a.pixels,b.pixels),"Raster pixels changed");
            require(b.wallRoofCount<=a.wallRoofCount,"Ownership added projected wall/roof faces");removed+=a.wallRoofCount-b.wallRoofCount;
            if(force)require(a.faces.equals(b.faces),"Forced legacy frame lost projected geometry");
        }
        require(removed>0,"Fixture never omitted a resident wall/roof face");
        BrowserFrameSinkRegistry.install(null);BrowserFrameSinkRegistry.install(sink(true));require(!full.s.isBrowserFullResidentOwnershipEnabled(),"CPU-depth consumer lost wall/roof capture");
        Result a=ordinary.draw(256,192,600,768,0,0,false),b=full.draw(256,192,600,768,0,0,false);require(a.faces.equals(b.faces)&&a.picks.equals(b.picks),"CPU-depth consumer output changed");
        BrowserFrameSinkRegistry.install(null);require(!full.s.isBrowserFullResidentOwnershipEnabled(),"Desktop retained optional browser ownership");
        System.out.println("PASS "+checks+" wall/roof GPU ownership pick/occlusion/anchor/resident/pixel/default checks; omittedFaces="+removed);
    }
}
