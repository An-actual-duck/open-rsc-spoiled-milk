package orsc.graphics.three;

import orsc.graphics.two.MudClientGraphics;

/** Actual client Scene publishes immutable render-only scope epochs, without networking. */
public final class ShadowContextEpochFixture {
    static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        MudClientGraphics graphics=new MudClientGraphics(64,64,1);
        Scene scene=new Scene(graphics,4,16,4);
        scene.setMidpoints(32,true,64,32,32,9,64);
        scene.setCamera(0,-4,0,768,0,0,600);
        scene.endScene(0);Renderer3DFrame before=scene.getRenderer3DFrame();
        require(before!=null && before.getShadowContextEpoch()==0,"initial context");
        scene.endScene(0);require(scene.getRenderer3DFrame().getShadowContextEpoch()==0,"ordinary frame changed context");
        scene.advanceRendererShadowContext();scene.endScene(0);
        Renderer3DFrame after=scene.getRenderer3DFrame();
        require(after.getShadowContextEpoch()==1,"scope epoch not published");
        require(before.getShadowContextEpoch()==0,"published old frame mutated");
        scene.advanceRendererShadowContext();scene.endScene(0);
        require(scene.getRenderer3DFrame().getShadowContextEpoch()==2,"second scope change lost");
        require(after.getShadowContextEpoch()==1,"intermediate published frame mutated");
        System.out.println("PASS render-only scope epochs: true changes advance, normal frames stable, published frame identity immutable.");
    }
}
