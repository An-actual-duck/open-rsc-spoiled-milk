package orsc.browser;

import java.lang.reflect.Field;
import orsc.mudclient;

/** Private fixed-actor benchmark only. No runtime/frame/animation code is replaced. */
final class RepeatableCamera {
    private final Field driftX=field("cameraAutoMoveX"),driftZ=field("cameraAutoMoveZ");
    private final Field amountX=field("cameraAutoMoveAmountX"),amountZ=field("cameraAutoMoveAmountZ");
    private final Field automatic=field("optionCameraModeAuto"),pitch=field("cameraPitch");
    private final Field count=field("npcCount"),players=field("playerCount");
    private mudclient pinned;
    private static Field field(String name) {
        try {Field field=mudclient.class.getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Repeatable camera layout changed: "+name,error);}
    }
    int[] observe(mudclient client)throws IllegalAccessException {
        if(!client.isGameView()){pinned=null;return new int[]{0,0,0};}
        if(client.isRenderer3DWorldReady()&&pinned!=client) {
            // Zero both offsets and future random-walk increments. Pin once at
            // ready; no per-frame reflection or post-projection matrix override.
            driftX.setInt(client,0);driftZ.setInt(client,0);amountX.setInt(client,0);amountZ.setInt(client,0);
            automatic.setBoolean(client,false);pitch.setInt(client,112);client.cameraRotation=128;client.setCameraZoomSetting(75);
            pinned=client;
        }
        return new int[]{pinned==client?1:0,count.getInt(client),players.getInt(client)};
    }
}
