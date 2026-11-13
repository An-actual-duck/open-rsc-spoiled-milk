package orsc;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Fixture-only collector. No runtime hooks, no GL calls, no per-frame file writes. */
final class NativeRouteProbe implements AutoCloseable {
    private final Path run, capture;
    private final mudclient client;
    private final List<String> frames = new ArrayList<String>();
    private final JSONArray legs = new JSONArray();
    private final Map<String,Long> seen = new HashMap<String,Long>();
    private volatile boolean running = true;
    private volatile Throwable failure;
    private final Thread sampler;
    private static Object field(Object o, Class<?> c, String name) throws Exception {
        Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    NativeRouteProbe(Path run, Path capture, mudclient client) throws Exception {
        this.run=run;this.capture=capture;this.client=client;
        collect(); // Establish a count baseline, excluding pre-window samples.
        sampler=new Thread(() -> {try {while(running){Thread.sleep(50);collect();}}catch(Throwable e){failure=e;}},"native-route-sampler");
        sampler.setDaemon(true);sampler.start();
    }
    private void collect() throws Exception {
        synchronized(RenderTelemetry.class) {
            long anchor=((Number)field(null,RenderTelemetry.class,"lastOpenGLFrameNanos")).longValue();
            for(String name:new String[]{"openGLFrameIntervalStats","openGLUploadStats","openGLRenderStats","openGLWorldStats"}) {
                Object stats=field(null,RenderTelemetry.class,name);Class<?> c=stats.getClass();
                long count=((Number)field(stats,c,"count")).longValue();Long prior=seen.put(name,count);
                if(prior==null)continue;
                long delta=count-prior;
                long[] ring=(long[])field(stats,c,"recentSamples");
                int index=((Number)field(stats,c,"recentIndex")).intValue(),size=((Number)field(stats,c,"recentCount")).intValue();
                if(delta<0||delta>size)throw new IllegalStateException("Lost unique "+name+" samples: "+delta+" / "+size);
                long end=anchor;
                for(int offset=0;offset<delta;offset++) {
                    long value=ring[(index-1-offset+ring.length)%ring.length];
                    if(frames.size()>=250000)throw new IllegalStateException("Bounded frame collector full");
                    frames.add(name+","+(count-offset)+","+end+","+value);
                    if(name.equals("openGLFrameIntervalStats"))end-=value;
                }
            }
        }
    }
    private JSONObject status() throws Exception {
        if(Files.exists(run.resolve("FIXTURE-FAILED")))throw new IllegalStateException("Private fixture failed");
        if(failure!=null)throw new IllegalStateException("Sampler failed",failure);
        return new JSONObject(new String(Files.readAllBytes(run.resolve("status.json")),StandardCharsets.UTF_8));
    }
    private JSONObject pose() throws Exception {
        PacketHandler packets=(PacketHandler)field(client,mudclient.class,"packetHandler");
        OpenGLFramePresenter presenter=(OpenGLFramePresenter)field(ScaledWindow.getInstance(),ScaledWindow.class,"openGLFramePresenter");
        OpenGLViewportPresenter viewport=(OpenGLViewportPresenter)field(presenter,OpenGLFramePresenter.class,"viewportPresenter");
        return new JSONObject().put("shadowQuality",String.valueOf(RendererShadowSettings.getQuality())).put("profile",String.valueOf(RendererProfileSettings.getMode())).put("lighting",String.valueOf(RendererLightingSettings.getMode())).put("fog",String.valueOf(RendererFogSettings.getMode())).put("zoom",osConfig.C_LAST_ZOOM).put("rotation",client.cameraRotation).put("viewportWidth",viewport.framebufferViewport().width).put("viewportHeight",viewport.framebufferViewport().height).put("x",packets.getLogicalPlayerX(client.getEditorPlayerWorldX())).put("y",packets.getLogicalPlayerY(client.getEditorPlayerWorldX(),client.getMidRegionBaseZ()+client.getLocalPlayerZ()))
            .put("level",packets.getLogicalWorldLevel(client.getMidRegionBaseZ()+client.getLocalPlayerZ())).put("ready",client.isRenderer3DWorldReady()).put("players",client.getPlayerCount()).put("npcs",client.getNpcCount()).put("context",packets.getLayeredSceneContextDebugSummary()).put("baseX",client.getMidRegionBaseX()).put("baseZ",client.getMidRegionBaseZ());
    }
    void route() throws Exception {
        JSONObject plan=new JSONObject(new String(Files.readAllBytes(run.resolve("route-plan.json")),StandardCharsets.UTF_8));
        Files.write(capture.resolve("route-plan.json"),plan.toString(2).getBytes(StandardCharsets.UTF_8));
        JSONArray steps=plan.getJSONArray("legs");
        for(int i=0;i<steps.length();i++) {
            JSONObject step=steps.getJSONObject(i);JSONObject before=pose();
            long start=System.nanoTime();
            JSONObject command=new JSONObject(step.toString()).put("id",i+1);
            Files.write(run.resolve("route-request.next"),command.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(run.resolve("route-request.next"),run.resolve("route-request.json"),StandardCopyOption.REPLACE_EXISTING);
            long deadline=start+45_000_000_000L,arrived=0;JSONObject state=null,after=null;
            while(System.nanoTime()<deadline) {
                state=status();after=pose();
                if(state.optInt("routeId",-1)==i+1 && state.getInt("x")==step.getInt("x")
                    && state.getInt("y")==step.getInt("y") && state.getInt("level")==step.getInt("level")
                    && after.getInt("x")==step.getInt("x") && after.getInt("y")==step.getInt("y")
                    && after.getInt("level")==step.getInt("level") && after.getBoolean("ready")) {arrived=System.nanoTime();break;}
                Thread.sleep(50);
            }
            if(arrived==0)throw new IllegalStateException("Route arrival timeout at leg "+i+": "+state+" client="+after);
            if(i>0 && step.getString("kind").equals("walk") && before.getInt("baseX")==after.getInt("baseX") && before.getInt("baseZ")==after.getInt("baseZ"))
                throw new IllegalStateException("Walking did not cross a native region boundary");
            if(state.getInt("controlledPlayers")!=16||state.getInt("controlledNpcs")!=32)throw new IllegalStateException("Population missing");
            Thread.sleep(3000); // Identical settling allowance; not labeled render-ready latency.
            if(client.getPlayerCount()<16||client.getNpcCount()<32)throw new IllegalStateException("Received crowd missing after settle");
            JSONObject receipt=new JSONObject().put("index",i).put("step",step).put("before",before).put("after",pose())
                .put("requestNanos",start).put("observedArrivalNanos",arrived).put("endNanos",System.nanoTime()).put("server",state);
            legs.put(receipt);
            Files.write(capture.resolve("route.json"),new JSONObject().put("accepted",false).put("legs",legs).toString(2).getBytes(StandardCharsets.UTF_8));
        }
        Files.write(capture.resolve("route.json"),new JSONObject().put("accepted",true).put("legs",legs)
            .put("arrivalMeaning","Authoritative and client pose acknowledgement, not first coherent presented frame. Diagnostic boundary traces provide separate render evidence.").toString(2).getBytes(StandardCharsets.UTF_8));
    }
    public void close() throws Exception {
        running=false;sampler.join();collect();
        List<String> output=new ArrayList<String>();output.add("stage,sequence,anchorNanos,durationNanos");output.addAll(frames);
        Files.write(capture.resolve("unique-frame-events.csv"),output,StandardCharsets.UTF_8);
        if(failure!=null)throw new IllegalStateException("Frame collector lost events",failure);
    }
}
