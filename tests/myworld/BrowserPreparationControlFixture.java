package orsc.graphics.two;
import orsc.graphics.three.Renderer3DSettings;
/** Actual configured policy/current surface gate; no emulated renderer. */
public final class BrowserPreparationControlFixture {
 private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  boolean enabled=Boolean.parseBoolean(args[0]);
  check(GraphicsController.isBrowserFramePreparationEnabled()==enabled,"actual class-initialized switch");
  GraphicsController surface=new GraphicsController(4,4,1);
  Renderer3DSettings.setBrowserPresentationAvailable(true);
  surface.setWorldOverlayCoverageEnabled(true);surface.beginRenderer2DFrame();
  check(surface.isBrowserFramePreparationActive()==enabled,"normal opted-in frame");
  surface.beginSoftwareOwnedRenderer2DFrame();check(!surface.isBrowserFramePreparationActive(),"software-owned loading frame");
  surface.beginRenderer2DFrame();check(surface.isBrowserFramePreparationActive()==enabled,"normal ownership restores");
  surface.setWorldOverlayCoverageEnabled(false);check(!surface.isBrowserFramePreparationActive(),"scratch/no coverage");
  Renderer3DSettings.setBrowserPresentationAvailable(false);surface.setWorldOverlayCoverageEnabled(true);surface.beginRenderer2DFrame();
  check(!surface.isBrowserFramePreparationActive(),"native/software backend");
  System.out.println("PASS actual frame-preparation getters; selected="+enabled);
 }
}
