package orsc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Production load-declaration predicate; no GL, server, or time-based assertion. */
public final class BrowserFogTelemetryFixture {
    private static int checks;
    private static void check(boolean value,String reason) {
        checks++;
        if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args)throws Exception {
        Class<?> type=Class.forName("orsc.BrowserWebGLBridge$LoadMeasure");
        Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        Method changed=type.getDeclaredMethod("declarationChanged",type);changed.setAccessible(true);
        Object previous=constructor.newInstance(),current=constructor.newInstance();
        check((Boolean)changed.invoke(current,new Object[]{null}),"First declaration missing");
        check(!(Boolean)changed.invoke(current,previous),"Unchanged steady declaration emitted");
        for(String name:new String[]{"sourceEpoch","geometryEpoch","source","required","sourceVertices","vertices"}) {
            Field field=type.getDeclaredField(name);field.setAccessible(true);
            field.setInt(current,1);
            check((Boolean)changed.invoke(current,previous),"Zero-upload change hidden: "+name);
            field.setInt(previous,1);
            check(!(Boolean)changed.invoke(current,previous),"Committed declaration repeated: "+name);
        }
        Field enabled=type.getDeclaredField("enabled");enabled.setAccessible(true);enabled.setBoolean(current,true);
        check((Boolean)changed.invoke(current,previous),"Enabled-state declaration hidden");
        enabled.setBoolean(previous,true);
        for(String name:new String[]{"ready","newChunks","newVertices","replacedVertices","repacks","exit"}) {
            Field field=type.getDeclaredField(name);field.setAccessible(true);field.setInt(current,5);
            check(!(Boolean)changed.invoke(current,previous),"Progress/upload detail wrongly became steady-state declaration: "+name);
        }
        System.out.println("PASS "+checks+" real load-declaration predicate checks, including zero-upload epoch/count changes; no GPU or speed claim");
    }
}
