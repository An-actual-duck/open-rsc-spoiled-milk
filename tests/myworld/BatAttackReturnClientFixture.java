package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.SpriteDef;
import com.openrsc.client.entityhandling.SlayerMovementPreview;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;

/** Actual client JAR trajectory, mirror, frame-clock and incoming NPC packet checks. */
public final class BatAttackReturnClientFixture {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static Object call(Object object, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(object, arguments);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static int coordinate(mudclient client, SpriteDef definition, int source, int target, int remaining) throws Exception {
        return (Integer) call(client, "getProjectileTravelCoordinate",
            new Class<?>[] {SpriteDef.class, int.class, int.class, int.class}, definition, source, target, remaining);
    }
    private static boolean mirror(mudclient client, SpriteDef definition, ORSCharacter source, ORSCharacter target) throws Exception {
        return (Boolean) call(client, "shouldMirrorProjectile",
            new Class<?>[] {SpriteDef.class, ORSCharacter.class, ORSCharacter.class}, definition, source, target);
    }
    public static void main(String[] arguments) throws Exception {
        check(Paths.get(mudclient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
            .equals(Paths.get(arguments[0]).toRealPath()), "production code must come from the tested client JAR");
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        mudclient client = (mudclient) ((sun.misc.Unsafe) field.get(null)).allocateInstance(mudclient.class);
        check(client.getProjectileMaxRange() == 40, "existing 40-step projectile lifetime");
        SpriteDef drain = new SpriteDef("drain", 0, "", EntityHandler.PROJECTILE_TYPES.SUMMON_BAT_VAMPIRISM.id());
        int[] remaining = {40, 30, 20, 10, 0};
        int[][] source = {{100, 200, 300}, {-200, -300, -400}};
        int[][] target = {{500, 700, 900}, {200, 500, -800}};
        int[][][] returning = {
            {{500,700,900},{400,575,750},{300,450,600},{200,325,450},{100,200,300}},
            {{200,500,-800},{100,300,-700},{0,100,-600},{-100,-100,-500},{-200,-300,-400}}
        };
        for (int scene = 0; scene < source.length; scene++) {
            for (int step = 0; step < remaining.length; step++) {
                for (int axis = 0; axis < 3; axis++) {
                    check(coordinate(client, drain, source[scene][axis], target[scene][axis], remaining[step])
                        == returning[scene][step][axis], "drain starts on enemy and reaches bat on axis " + axis);
                    for (EntityHandler.PROJECTILE_TYPES type : EntityHandler.PROJECTILE_TYPES.values()) {
                        if (type == EntityHandler.PROJECTILE_TYPES.SUMMON_BAT_VAMPIRISM) continue;
                        SpriteDef ordinary = new SpriteDef(type.name(), 0, "", type.id());
                        check(coordinate(client, ordinary, source[scene][axis], target[scene][axis], remaining[step])
                            == returning[scene][4-step][axis], "ordinary projectile retains forward travel: " + type);
                    }
                    check(coordinate(client, null, source[scene][axis], target[scene][axis], remaining[step])
                        == returning[scene][4-step][axis], "unknown projectile retains ordinary fallback travel");
                }
            }
        }
        ORSCharacter bat = new ORSCharacter(), enemy = new ORSCharacter();
        bat.currentX = 100; bat.currentZ = 200; enemy.currentX = 500; enemy.currentZ = 700;
        SpriteDef ordinary = new SpriteDef("ordinary", 0, "", EntityHandler.PROJECTILE_TYPES.MAGIC.id());
        check(mirror(client, drain, bat, enemy), "returning sprite faces left toward the bat at camera zero");
        check(!mirror(client, ordinary, bat, enemy), "ordinary sprite still faces right toward the enemy");
        for (int camera = 0; camera < 256; camera += 16) {
            set(client, "cameraRotation", camera);
            check(mirror(client, drain, bat, enemy) == mirror(client, ordinary, enemy, bat),
                "drain facing follows its visual destination at camera " + camera);
        }
        check(!mirror(client, drain, bat, bat), "coincident return endpoints have no arbitrary mirror");
        check(!mirror(client, drain, null, enemy), "missing endpoint remains safe");
        int[] frameCounts = new int[256]; frameCounts[drain.id-7] = 8;
        set(client, "projectileEffectFrameCounts", frameCounts);
        int[] expectedFrames = {0, 2, 4, 6, 7};
        for (int step = 0; step < remaining.length; step++) {
            int sprite = (Integer) call(client, "getProjectileSceneSpriteIndex",
                new Class<?>[] {SpriteDef.class, int.class, boolean.class}, drain, remaining[step], false);
            check(sprite == mudclient.spriteProjectileEffectBase + (drain.id-7)*mudclient.PROJECTILE_EFFECT_FRAME_SLOTS + expectedFrames[step],
                "returning path does not reverse or retime authored animation frames");
        }
        bat.npcId = 43; bat.serverIndex = 1;
        enemy.npcId = SlayerMovementPreview.NAGA.npcId; enemy.serverIndex = 2;
        ORSCharacter[] registry = new ORSCharacter[3]; registry[1] = bat; registry[2] = enemy;
        set(client, "npcsServer", registry);
        EntityHandler.projectiles.clear();
        for (int id = 0; id <= drain.id; id++) EntityHandler.projectiles.add(new SpriteDef("projectile", 0, "", id));
        PacketHandler handler = new PacketHandler(client);
        handler.getPacketsIncoming().dataBuffer = new byte[] {0,1,0,2,3,0,(byte)drain.id,0,1};
        handler.getPacketsIncoming().packetEnd = 0;
        call(handler, "updateNPCAppearances", new Class<?>[0]);
        check(enemy.attackingNpcServerIndex == 1 && enemy.incomingProjectileSprite.id == drain.id,
            "existing packet keeps bat as attacker and enemy as combat target");
        check(enemy.projectileRange == 40 && bat.projectileRange == 0, "existing incoming-projectile lifetime is unchanged");
        check(enemy.npcProjectileAttackStartedMillis == 0,
            "draining a Naga does not falsely trigger that enemy's attack pose");
        ORSCharacter player = new ORSCharacter(); player.serverIndex = 2;
        ORSCharacter[] players = new ORSCharacter[3]; players[2] = player; set(client, "playerServer", players);
        handler.getPacketsIncoming().dataBuffer = new byte[] {0,1,0,2,3,0,(byte)drain.id,0,1};
        handler.getPacketsIncoming().packetEnd = 0;
        call(handler, "drawNearbyPlayers", new Class<?>[0]);
        check(player.attackingNpcServerIndex == 1 && player.incomingProjectileSprite.id == drain.id,
            "player-victim packets retain the same bat as logical shooter");
        check(player.projectileRange == 40, "player-victim drain uses the same original animation lifetime");
        System.out.println("PASS " + checks + " bat return trajectory, facing, frame-clock and packet ownership checks");
    }
}
