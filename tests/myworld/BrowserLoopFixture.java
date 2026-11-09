package orsc;

import java.lang.reflect.*;

/** Optional browser loop selection cannot change ordinary desktop policy. */
public final class BrowserLoopFixture {
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        boolean primary=args.length>0&&"native".equals(args[0]);
        System.setProperty("spoiledmilk.modernClientLoop","true");
        System.setProperty("spoiledmilk.openglPresenter",String.valueOf(primary));
        System.setProperty("spoiledmilk.openglPrimaryWindow",String.valueOf(primary));
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        mudclient client=(mudclient)((sun.misc.Unsafe)field.get(null)).allocateInstance(mudclient.class);
        Method select=mudclient.class.getDeclaredMethod("shouldUseModernClientLoop");select.setAccessible(true);
        require((Boolean)select.invoke(client)==primary,"Ordinary desktop loop policy changed");
        if(!primary){
            BrowserFrameSinkRegistry.install((image,commands,frame)->{});
            require(!(Boolean)select.invoke(client),"Browser must retain legacy default pending actual cadence proof");
            System.setProperty("spoiledmilk.browserLoop","modern");require((Boolean)select.invoke(client),"Explicit installed GPU sink cannot select modern loop");
            System.setProperty("spoiledmilk.browserLoop","legacy");require(!(Boolean)select.invoke(client),"Controlled legacy comparison not honored");
            BrowserFrameSinkRegistry.install(null);System.setProperty("spoiledmilk.browserLoop","modern");
            require(!(Boolean)select.invoke(client),"Uninstalled browser capability changed software desktop policy");
        }
        System.out.println("PASS "+(primary?"ordinary native-primary":"software desktop/default browser/explicit modern/teardown")+" loop capability selection");
    }
}
