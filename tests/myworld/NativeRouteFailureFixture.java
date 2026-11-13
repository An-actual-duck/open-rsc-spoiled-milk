package orsc;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;
/** Actual probe resource ownership, without launching a client, server or GPU. */
public final class NativeRouteFailureFixture {
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]);
        for(String mode:new String[]{"route","sample","observer"}) {
            Path capture=root.resolve(mode);Files.createDirectories(capture);Throwable caught=null;
            try(NativeRouteProbe probe=new NativeRouteProbe(capture,capture,null)) {
                if(mode.equals("route"))probe.route(); // Missing plan: actual route failure.
                else if(mode.equals("sample"))throw new java.io.IOException("injected context sample failure");
                else {
                    Field stages=NativeRouteProbe.class.getDeclaredField("stages");stages.setAccessible(true);
                    Object stage=((List<?>)stages.get(probe)).get(0);
                    Field seen=stage.getClass().getDeclaredField("seen");seen.setAccessible(true);seen.setLong(stage,1);
                    // Current count is zero: close's final drain must reject reset,
                    // but still preserve its CSV and observer failure receipt.
                }
            } catch(Throwable error){caught=error;}
            if(caught==null)throw new AssertionError("Failure accepted: "+mode);
            if(mode.equals("sample")&&!"injected context sample failure".equals(caught.getMessage()))throw new AssertionError("Original failure lost");
            if(!Files.isRegularFile(capture.resolve("unique-frame-events.csv")))throw new AssertionError("Buffered evidence lost");
            JSONObject observer=new JSONObject(new String(Files.readAllBytes(capture.resolve("observer-overhead.json")),"UTF-8"));
            if(observer.getBoolean("failed")!=mode.equals("observer"))throw new AssertionError("Incorrect observer failure receipt");
            if(Files.exists(capture.resolve("CLIENT-DONE")))throw new AssertionError("Failed evidence accepted");
            System.out.println("PASS preserved "+mode+" failure evidence");
        }
    }
}
