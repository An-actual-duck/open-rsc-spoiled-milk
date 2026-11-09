package orsc;

import java.awt.Canvas;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DSettings;

public final class BrowserControlsSettingsFixture {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static RendererSettingsPanel.State state(boolean nativeGpu){
        return RendererSettingsPanel.State.capture(nativeGpu,ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,1f,
            Arrays.asList(1f,2f),true,false,true,false);
    }
    static final class View implements RendererSettingsPanel.View {
        int selected;
        public void clearRows(){} public void setRow(int i,String text,int action){} public void drawString(String s,int x,int y,int c,int f){}
        public int stringWidth(int font,String s){return s.length();}public int scrollAmount(){return 0;}
        public int selectedAction(){return selected;}public void drawPanel(){}
    }
    static final class Actions implements RendererSettingsPanel.Actions {
        RendererSettingsPanel.Action last;String tuning;
        public void perform(RendererSettingsPanel.Action action){last=action;}
        public void rendererTuningChanged(String name,int level,float value){tuning=name;}
    }
    static boolean has(List<RendererSettingsPanel.Row> rows,String text){for(RendererSettingsPanel.Row r:rows)if(r.label.contains(text))return true;return false;}
    static void settings()throws Exception{
        Renderer3DSettings.setBrowserPresentationAvailable(false);
        check(has(RendererSettingsPanel.rows(state(false)),"Scaling -"),"ordinary software still has scaling");
        check(has(RendererSettingsPanel.rows(state(true)),"Window mode"),"native window options preserved");
        check(has(RendererSettingsPanel.rows(state(true)),"Geometry"),"native geometry preserved");
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        RendererSettingsPanel.State browser=state(false);check(browser.browserGpu&&!browser.openGLPrimary,"browser capability separate from native window");
        List<RendererSettingsPanel.Row> rows=RendererSettingsPanel.rows(browser);
        for(String name:new String[]{"Lighting","Shadows","Fog","Terrain Variation","Terrain shading","Object shading","Brightness","Contrast","Gamma","Saturation","Middle mouse"})check(has(rows,name),"browser row "+name);
        for(String name:new String[]{"Scaling -","Scaling type","Window mode","Aspect Ratio","Preset","Geometry"})check(!has(rows,name),"unsupported browser row omitted "+name);
        RendererSettingsPanel panel=new RendererSettingsPanel();View view=new View();Actions actions=new Actions();
        for(int[] mapping:new int[][]{{60,RendererSettingsPanel.Action.FOG.ordinal()},{61,RendererSettingsPanel.Action.LIGHTING.ordinal()},
            {74,RendererSettingsPanel.Action.SHADOW_QUALITY.ordinal()},{64,RendererSettingsPanel.Action.TERRAIN_VARIATION.ordinal()}}){
            view.selected=mapping[0];actions.last=null;
            check(panel.handleSelectedAction(view,browser,new RendererSettingsPanel.Input(960,0,0,1,0,false),0,0,actions),"browser action handled");
            check(actions.last.ordinal()==mapping[1],"shared action dispatch");
        }
        for(int unsupported:new int[]{46,49,56,59,62,63}){view.selected=unsupported;actions.last=null;
            check(!panel.handleSelectedAction(view,browser,new RendererSettingsPanel.Input(960,0,0,1,0,false),0,0,actions)&&actions.last==null,"hidden native action rejected "+unsupported);}
        for(int slider=66;slider<=71;slider++){view.selected=slider;actions.tuning=null;
            RendererReliefSettings.setTerrainLevel(1);RendererReliefSettings.setObjectLevel(1);RendererColorDiagnosticSettings.setDimnessLevel(1);
            RendererColorDiagnosticSettings.setContrastLevel(1);RendererColorDiagnosticSettings.setGammaLevel(1);RendererColorDiagnosticSettings.setSaturationLevel(1);
            panel.handleSelectedAction(view,browser,new RendererSettingsPanel.Input(960,122,0,1,1,false),100,0,actions);
            check(actions.tuning!=null,"browser slider updates shared settings "+slider);
        }
        // Actual production Java exporter consumes live settings each present; no mocked uniform builder.
        Constructor<?> ctor=null;for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors())if(c.getParameterTypes().length==16)ctor=c;
        check(ctor!=null,"production frame constructor");ctor.setAccessible(true);Object[] args=new Object[16];Arrays.fill(args,0);
        args[15]=java.lang.reflect.Array.newInstance(Class.forName("orsc.graphics.three.Renderer3DTextureData"),0);
        Renderer3DFrame frame=(Renderer3DFrame)ctor.newInstance(args);
        Method uniforms=BrowserWebGLBridge.class.getDeclaredMethod("uniforms",Renderer3DFrame.class);uniforms.setAccessible(true);
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);RendererFogSettings.setMode(RendererFogSettings.Mode.OFF);
        RendererTerrainVariationSettings.setMode(RendererTerrainVariationSettings.Mode.OFF);
        float[] off=(float[])uniforms.invoke(null,frame);
        RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
        RendererTerrainVariationSettings.setMode(RendererTerrainVariationSettings.Mode.ON);float[] on=(float[])uniforms.invoke(null,frame);
        check(off[26]==0&&on[26]==1&&off[27]==0&&on[27]==1&&off[28]==0&&on[28]==1,"real WebGL uniform flags change");
        RendererReliefSettings.setTerrainLevel(1);RendererReliefSettings.setObjectLevel(1);RendererColorDiagnosticSettings.setDimnessLevel(1);
        RendererColorDiagnosticSettings.setContrastLevel(1);RendererColorDiagnosticSettings.setGammaLevel(1);RendererColorDiagnosticSettings.setSaturationLevel(1);
        float[] low=(float[])uniforms.invoke(null,frame);
        RendererReliefSettings.setTerrainLevel(20);RendererReliefSettings.setObjectLevel(20);RendererColorDiagnosticSettings.setDimnessLevel(20);
        RendererColorDiagnosticSettings.setContrastLevel(20);RendererColorDiagnosticSettings.setGammaLevel(20);RendererColorDiagnosticSettings.setSaturationLevel(20);
        float[] high=(float[])uniforms.invoke(null,frame);for(int index:new int[]{0,1,2,3,18,19})check(low[index]!=high[index],"real shader tuning changes "+index);
        Renderer3DSettings.setBrowserPresentationAvailable(false);
    }
    static void mouse(){
        Canvas canvas=new Canvas();DesktopMiddleMouseOrbit orbit=new DesktopMiddleMouseOrbit();
        MouseEvent press=BrowserMiddleMouseInput.event(canvas,new int[]{1,10,20,3});
        check(press.getButton()==MouseEvent.BUTTON2&&press.getID()==MouseEvent.MOUSE_PRESSED,"real AWT BUTTON2 press");
        check(press.isShiftDown()&&press.isControlDown()&&(press.getModifiersEx()&InputEvent.BUTTON2_DOWN_MASK)!=0,"modifiers preserved");
        check(orbit.begin(press.getButton(),press.getX(),press.getY()),"existing orbit starts");
        MouseEvent move=BrowserMiddleMouseInput.event(canvas,new int[]{2,35,7,0});
        check(move.getID()==MouseEvent.MOUSE_DRAGGED&&move.getButton()==MouseEvent.NOBUTTON,"ordinary drag event");
        check(orbit.update(move.getX(),move.getY())&&orbit.getDeltaX()==25&&orbit.getDeltaY()==-13,"existing orbit receives exact yaw/pitch deltas");
        MouseEvent release=BrowserMiddleMouseInput.event(canvas,new int[]{3,35,7,0});check(orbit.end(release.getButton())&&!orbit.isActive(),"release ends orbit");
        check(BrowserMiddleMouseInput.event(canvas,new int[]{0,0,0,0})==null,"cancel uses existing null-release cleanup");
        MouseEvent offset=BrowserMiddleMouseInput.event(canvas,new int[]{1,10,20,0},113,17);
        check(offset.getX()==123&&offset.getY()==37&&offset.getX()-113==10&&offset.getY()-17==20,"game-local relay coordinates account for nonzero AWT letterboxing offsets");
        for(int[] invalid:new int[][]{{4,0,0,0},{1,Integer.MAX_VALUE,0,0},{1,0,0,16},{1}}){
            boolean rejected=false;try{BrowserMiddleMouseInput.event(canvas,invalid);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid relay packet rejected");}
    }
    public static void main(String[] args)throws Exception{settings();mouse();System.out.println("PASS "+checks+" browser controls/settings checks");}
}
