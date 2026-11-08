package orsc;

import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import orsc.enumerations.MenuItemAction;
import orsc.graphics.gui.Menu;
import orsc.graphics.three.Renderer3DFrame;

/** Tool-only ordinary AWT mouse callbacks; never calls walkToArea or a shared packet buffer. */
public final class MovementInputProofClient {
    private static Object field(Object owner,Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void set(Object owner,Class<?> type,String name,Object value)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private static void mouse(int id,int x,int y,AtomicLong timestamp)throws Exception {
        EventQueue.invokeAndWait(()->{
            OpenRSC applet=OpenRSC.applet;
            MouseEvent event=new MouseEvent(applet,id,System.currentTimeMillis(),
                id==MouseEvent.MOUSE_PRESSED?java.awt.event.InputEvent.BUTTON1_MASK:0,x,y,1,false,
                id==MouseEvent.MOUSE_MOVED?MouseEvent.NOBUTTON:MouseEvent.BUTTON1);
            if(timestamp!=null)timestamp.set(System.nanoTime());
            if(id==MouseEvent.MOUSE_MOVED)applet.getMouseHandler().mouseMoved(event);
            else if(id==MouseEvent.MOUSE_PRESSED)applet.getMouseHandler().mousePressed(event);
            else applet.getMouseHandler().mouseReleased(event);
        });
    }
    private static void dismissWelcome(mudclient client)throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(Boolean.TRUE.equals(field(client,mudclient.class,"showDialogMessage"))&&System.currentTimeMillis()<deadline) {
            int x=10+client.screenOffsetX,y=10+client.screenOffsetY;
            mouse(MouseEvent.MOUSE_MOVED,x,y,null);mouse(MouseEvent.MOUSE_PRESSED,x,y,null);mouse(MouseEvent.MOUSE_RELEASED,x,y,null);
            Thread.sleep(200);
        }
        if(Boolean.TRUE.equals(field(client,mudclient.class,"showDialogMessage")))throw new IllegalStateException("Ordinary welcome close click was not consumed");
        Thread.sleep(600);
        if(Boolean.TRUE.equals(field(client,mudclient.class,"showDialogMessage")))throw new IllegalStateException("Welcome reopened after login settle");
    }
    private static long completedFrames()throws Exception {
        synchronized(RenderTelemetry.class){return ((Number)field(null,RenderTelemetry.class,"openGLFrames")).longValue();}
    }
    private static int[] ground(Menu menu,mudclient client) {
        int count=menu.getItemCount(-27153);
        for(int i=0;i<count;i++)if(menu.getItemAction(i)==MenuItemAction.LANDSCAPE_WALK_HERE) {
            int x=menu.getItemIndexOrX(i),z=menu.getItemIdOrZ(i);
            int distance=Math.abs(x-client.getLocalPlayerX())+Math.abs(z-client.getLocalPlayerZ());
            if(distance==1&&"Walk here".equals(menu.getItemLabel(i)))return new int[]{x,z};
        }
        return null;
    }
    private static int[] feet(OpenGLFramePresenter presenter,mudclient client)throws Exception {
        long until=System.nanoTime()+2_000_000_000L;
        while(System.nanoTime()<until) {
            Object lock=field(presenter,OpenGLFramePresenter.class,"frameLock");
            synchronized(lock) {
                Object pending=field(presenter,OpenGLFramePresenter.class,"pendingFrame");
                if(pending!=null) {
                    Renderer3DFrame frame=(Renderer3DFrame)field(pending,pending.getClass(),"renderer3DFrame");
                    if(frame!=null)for(Renderer3DFrame.CharacterSprite character:frame.getCharacterSprites())
                        if(character.getServerIndex()==client.getLocalPlayerServerIndex())
                            for(Renderer3DFrame.SpriteAnchor anchor:frame.getSpriteAnchors())if(anchor.getFaceId()==character.getFaceId())
                                return new int[]{anchor.getDrawX()+anchor.getDrawWidth()/2,anchor.getDrawY()+anchor.getDrawHeight()};
                }
            }
            Thread.sleep(2);
        }
        return new int[]{RenderSurfaceSettings.getWidth()/2,RenderSurfaceSettings.getHeight()/2+20};
    }
    private static int[] chooseGround(mudclient client,OpenGLFramePresenter presenter,int sample)throws Exception {
        int[] origin=feet(presenter,client);
        for(int[] delta:new int[][]{{35,15},{-35,15},{35,-10},{-35,-10},{55,0},{-55,0},{0,30},{0,-30},{70,30},{-70,30},{20,30},{-20,30}}) {
            int x=origin[0]+(sample%2==0?delta[0]:-delta[0]),y=origin[1]+delta[1];
            mouse(MouseEvent.MOUSE_MOVED,x,y,null);Thread.sleep(100);
            int[] target=ground(client.menuCommon,client);
            if(target!=null) {
                Thread.sleep(50);int[] verify=ground(client.menuCommon,client);
                if(verify!=null&&verify[0]==target[0]&&verify[1]==target[1])return new int[]{x,y,target[0],target[1]};
            }
        }
        throw new IllegalStateException("No stable ordinary adjacent Walk here target found near player feet");
    }
    private static long completedCaptures(Path run)throws Exception {
        Path index=run.resolve("diagnostics/captures/capture-index.jsonl");
        if(!Files.exists(index))return 0;
        long count=0;for(String line:Files.readAllLines(index,StandardCharsets.UTF_8))
            if(line.contains("\"status\":\"completed\"")&&line.contains("\"failed\":false"))count++;
        return count;
    }
    private static void capture(OpenGLFramePresenter presenter,Path run,String label)throws Exception {
        long before=completedFrames(),beforeCapture=completedCaptures(run);
        Method request=OpenGLFramePresenter.class.getDeclaredMethod("requestFrameCaptureBurst");request.setAccessible(true);request.invoke(presenter);
        set(presenter,OpenGLFramePresenter.class,"frameCaptureBurstRemaining",1);
        long deadline=System.currentTimeMillis()+20_000;
        while((completedFrames()<=before||completedCaptures(run)<=beforeCapture)&&System.currentTimeMillis()<deadline)Thread.sleep(50);
        if(completedFrames()<=before||completedCaptures(run)<=beforeCapture)
            throw new IllegalStateException("Native frame/capture did not complete within bounded capture wait");
        Files.write(run.resolve("capture-checks.jsonl"),("{\"label\":\""+label+"\",\"completedBefore\":"+before+",\"completedAfter\":"+completedFrames()+",\"captureRecordsBefore\":"+beforeCapture+",\"captureRecordsAfter\":"+completedCaptures(run)+"}\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        Thread.sleep(1500);
    }
    public static void main(String[] args) {
        try {run();System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void run()throws Exception {
        Path clientRoot=Paths.get(".").toRealPath(),run=clientRoot.getParent();
        if(!Files.isRegularFile(run.resolve("MOVEMENT-INPUT-PROOF.json")) || !Boolean.getBoolean("spoiledmilk.openglHiddenDiagnostic")
            || !"127.0.0.1".equals(new String(Files.readAllBytes(clientRoot.resolve("Cache/ip.txt")),StandardCharsets.UTF_8).trim())
            || !"43649".equals(new String(Files.readAllBytes(clientRoot.resolve("Cache/port.txt")),StandardCharsets.UTF_8).trim()))
            throw new IllegalStateException("Exact marked hidden loopback fixture required");
        OpenRSC.main(new String[0]);mudclient client=null;boolean loginReady=false;long deadline=System.currentTimeMillis()+90_000;
        while(System.currentTimeMillis()<deadline) {
            client=(mudclient)field(null,ORSCApplet.class,"mudclient");
            if(client!=null&&field(client,mudclient.class,"currentViewMode").toString().equals("LOGIN")&&field(client,mudclient.class,"panelLoginWelcome")!=null){loginReady=true;break;}
            Thread.sleep(250);
        }
        if(!loginReady)throw new IllegalStateException("Client did not reach initialized LOGIN/panel readiness");Thread.sleep(4000);
        set(client,mudclient.class,"autoLoginTimeout",1);
        Method login=mudclient.class.getDeclaredMethod("login",int.class,String.class,String.class,boolean.class);login.setAccessible(true);login.invoke(client,-12,"WalkProof13","walkproof",false);
        deadline=System.currentTimeMillis()+90_000;
        while(System.currentTimeMillis()<deadline&&!field(client,mudclient.class,"currentViewMode").toString().equals("GAME"))Thread.sleep(250);
        if(!field(client,mudclient.class,"currentViewMode").toString().equals("GAME"))throw new IllegalStateException("Ordinary authentication timeout");
        set(client,mudclient.class,"welcomeScreenShown",false);client.setShowDialogMessage(false);Config.C_HIDE_LOGIN_BOX=true;
        client.setCameraZoomSetting(75);client.cameraRotation=128;Thread.sleep(6000);
        dismissWelcome(client);
        OpenGLFramePresenter presenter=(OpenGLFramePresenter)field(ScaledWindow.getInstance(),ScaledWindow.class,"openGLFramePresenter");
        boolean candidate=new String(Files.readAllBytes(run.resolve("MOVEMENT-INPUT-PROOF.json")),StandardCharsets.UTF_8).contains("\"mode\": \"candidate\"");
        String ownerStatus="";long readyDeadline=System.currentTimeMillis()+10_000;
        do {
            ownerStatus=new String(Files.readAllBytes(run.resolve("status.json")),StandardCharsets.UTF_8);
            if(!candidate||ownerStatus.contains("\"idleLaneOwnerEligible\":true"))break;
            Thread.sleep(200);
        }while(System.currentTimeMillis()<readyDeadline);
        Files.write(run.resolve("pre-input-authenticated-owner.json"),ownerStatus.getBytes(StandardCharsets.UTF_8));
        if(candidate&&!ownerStatus.contains("\"idleLaneOwnerEligible\":true"))throw new IllegalStateException("Ordinary authenticated player never became idle-lane eligible");
        capture(presenter,run,"authenticated-before-movement");
        RendererPerformanceDiagnostics.handleCommand("::pf start movement-input");
        for(int sample=0;sample<Integer.getInteger("movement.proof.samples",12);sample++) {
            if(Files.exists(run.resolve("FIXTURE-FAILED")))throw new IllegalStateException("Private server fixture failed");
            if(Boolean.TRUE.equals(field(client,mudclient.class,"showDialogMessage")))throw new IllegalStateException("Welcome dialog unexpectedly reopened before input");
            int[] target=chooseGround(client,presenter,sample);
            ORSCharacter actor=client.getLocalPlayer();int startX=actor.currentX,startZ=actor.currentZ;
            int expectedX=client.getMidRegionBaseX()+target[2],expectedY=client.getMidRegionBaseZ()+target[3];
            long frameBefore=completedFrames();AtomicLong at=new AtomicLong();
            mouse(MouseEvent.MOUSE_PRESSED,target[0],target[1],at);mouse(MouseEvent.MOUSE_RELEASED,target[0],target[1],null);
            long first=0;deadline=System.currentTimeMillis()+2500;
            while(System.currentTimeMillis()<deadline) {
                actor=client.getLocalPlayer();
                if(actor.currentX!=startX||actor.currentZ!=startZ){first=System.nanoTime();break;}Thread.sleep(2);
            }
            String status=new String(Files.readAllBytes(run.resolve("status.json")),StandardCharsets.UTF_8);
            String record="{\"sample\":"+sample+",\"awtPressedNanos\":"+at.get()+",\"firstObservedInterpolationNanos\":"+first+
                ",\"awtToObservedInterpolationMillis\":"+(first==0?-1:(first-at.get())/1_000_000.0)+",\"startX\":"+startX+",\"startZ\":"+startZ+
                ",\"observedX\":"+actor.currentX+",\"observedZ\":"+actor.currentZ+",\"targetLocalX\":"+target[2]+",\"targetLocalZ\":"+target[3]+
                ",\"completedFramesBefore\":"+frameBefore+",\"completedFramesAtObservation\":"+completedFrames()+",\"serverTickSnapshot\":"+status+"}\n";
            Files.write(run.resolve("input-samples.jsonl"),record.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            if(first==0)throw new IllegalStateException("Ordinary adjacent ground click produced no observed movement");
            long settleDeadline=System.currentTimeMillis()+3000;
            while((client.getEditorPlayerWorldX()!=expectedX||client.getEditorPlayerWorldY()!=expectedY)&&System.currentTimeMillis()<settleDeadline)Thread.sleep(20);
            Thread.sleep(900+(sample*137)%430);
            String settled="{\"sample\":"+sample+",\"expectedWorldX\":"+expectedX+",\"expectedWorldY\":"+expectedY+
                ",\"actualClientWorldX\":"+client.getEditorPlayerWorldX()+",\"actualClientWorldY\":"+client.getEditorPlayerWorldY()+
                ",\"serverTickSnapshot\":"+new String(Files.readAllBytes(run.resolve("status.json")),StandardCharsets.UTF_8)+"}\n";
            Files.write(run.resolve("settled-samples.jsonl"),settled.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            if(client.getEditorPlayerWorldX()!=expectedX||client.getEditorPlayerWorldY()!=expectedY)
                throw new IllegalStateException("Ordinary input settled at a different destination");
        }
        RendererPerformanceDiagnostics.handleCommand("::pf stop movement-input");
        capture(presenter,run,"after-ordinary-input-movement");
        RendererDiagnosticSession.close();
        Files.write(run.resolve("CLIENT-DONE"),"Ordinary AWT input and native render captures complete; interpolation timing is not scanout latency.\n".getBytes(StandardCharsets.UTF_8));
    }
}
