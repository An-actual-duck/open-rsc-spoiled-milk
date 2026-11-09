package orsc.graphics.three;

import java.util.*;
import orsc.BrowserFrameSinkRegistry;
import orsc.graphics.Renderer2DFrame;
import java.awt.image.BufferedImage;

/** Ordinary Scene mouse-query results must not depend on an optional CPU depth raster. */
public final class BrowserDepthPickingFixture {
    static int checks;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static BrowserFrameSinkRegistry.Sink sink(final boolean cpu) {
        return new BrowserFrameSinkRegistry.Sink(){
            public void present(BufferedImage image,Renderer2DFrame commands,Renderer3DFrame frame){}
            public boolean needsCpuDepthFrame(){return cpu;}
        };
    }
    static String picks(ClericWellSpotClientTest.View view,int x,int y,boolean cpu) {
        BrowserFrameSinkRegistry.install(null);BrowserFrameSinkRegistry.install(sink(cpu));
        require(Renderer3DSettings.needsCpuDepthFrame()==cpu,"Backend CPU depth declaration not respected");
        view.draw(x,y);Renderer3DFrame f=view.s.getRenderer3DFrame();
        require((f.getDepthFrame()!=null)==cpu,"Producer ignored CPU depth consumer contract");
        int count=view.s.b(0);RSModel[] models=view.s.b((byte)100);StringBuilder result=new StringBuilder();
        for(int n=0;n<count;n++)result.append(models[n].key).append(',');f.release();return result.toString();
    }
    public static void main(String[] args) {
        for(String name:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster"})System.setProperty("spoiledmilk."+name,"true");
        require(Renderer3DSettings.needsCpuDepthFrame(),"Ordinary desktop default lost CPU depth");
        ClericWellSpotClientTest.View view=new ClericWellSpotClientTest.View();
        RSModel back=ClericWellSpotClientTest.horizontal(100,-4,-12345);back.key=1;view.s.addModel(back);
        RSModel front=ClericWellSpotClientTest.horizontal(24,-104,-12345);front.key=2;view.s.addModel(front);
        for(int material:new int[]{-12345,Scene.TRANSPARENT,0}) {
            if(material==0){view.s.setFrustum(0,11,7,1);byte[] indices=new byte[4096];Arrays.fill(indices,(byte)1);for(int y=28;y<36;y++)for(int x=28;x<36;x++)indices[y*64+x]=0;view.s.loadTexture(0,new int[]{0xff00ff,0x777777},0,indices);}
            front.faceTextureFront[0]=material;front.faceTextureBack[0]=material;
            for(int y=140;y<=240;y+=10)for(int x=200;x<=310;x+=10) {
                String cpu=picks(view,x,y,true),gpu=picks(view,x,y,false);
                require(cpu.equals(gpu),"Ordinary mouse picks differ after CPU depth omission at "+x+","+y+" material"+material+": "+cpu+" vs "+gpu);
            }
        }
        BrowserFrameSinkRegistry.install(null);require(Renderer3DSettings.needsCpuDepthFrame(),"Teardown did not restore native/source defaults");
        System.out.println("PASS "+checks+" default, backend declaration, depth ownership and opaque/transparent/cutout ordinary Scene picking checks");
    }
}
