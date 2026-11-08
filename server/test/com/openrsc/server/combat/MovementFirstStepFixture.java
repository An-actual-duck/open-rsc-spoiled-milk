package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.content.monsterslayer.CockatriceCombat;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.WalkRequest;
import com.openrsc.server.net.rsc.struct.incoming.WalkStruct;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual authoritative movement with an injected clock; no sockets, accounts or native client. */
public final class MovementFirstStepFixture {
    private static final long EPOCH = 1_700_000_000_000L;
    private static int checks, serial;
    private static boolean candidate;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("baseline") || args[0].equals("candidate")))
            throw new IllegalArgumentException("Pass baseline|candidate and a new result JSON path");
        candidate = args[0].equals("candidate");
        Path output = Paths.get(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Never overwrite a prior result");
        JSONObject result = new JSONObject();
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            h.server().getConfig().WANT_MYWORLD = true;
            h.server().getConfig().WANT_CUSTOM_WALK_SPEED = true;
            h.server().getConfig().WALKING_TICK = 430;
            h.server().getConfig().PLAYER_BLOCKING = 0;
            h.server().getConfig().MAX_WALKING_SPEED = 1;
            h.openCombatProjectileRectangle(440, 470, 440, 455);
            result.put("players", phaseSweep(h, false));
            result.put("npcs", phaseSweep(h, true));
            Path phases = output.resolveSibling(args[0] + "-phases.json");
            if (Files.exists(phases)) throw new IllegalArgumentException("Never overwrite phase evidence");
            Files.createDirectories(phases.toAbsolutePath().getParent());
            Files.write(phases, (result.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            cadenceAndReclicks(h);
            collisionAndHolds(h);
            teleportOutcome(h);
            legacyControl(h);
            if (candidate) monotonicEpochs(h);
        }
        result.put("mode", args[0]).put("checks", checks).put("gameTickMillis", 640)
            .put("walkingIntervalMillis", 430).put("pollMillis", 10)
            .put("scope", "Deterministic path-assignment-to-first-authoritative-step; not packet admission, network or visible-client latency");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, (result.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS authoritative first-step fixture: " + checks + " checks; " + result.toString());
    }
    private static Player player(CurrentCombatHarness h) {
        return h.player("move" + serial++, 450, 450);
    }
    private static void walk(Mob mob, int x, int y) throws Exception {
        if (mob.isPlayer()) {
            WalkStruct request = new WalkStruct();
            request.setOpcode(OpcodeIn.WALK_TO_POINT);
            request.firstStep = Point.location(x, y);
            new WalkRequest().process(request, (Player) mob);
        } else mob.walk(x, y);
        check(!mob.getWalkingQueue().finished(), "accepted route contains a step; serial=" + serial
            + " actor=" + mob.getClass().getSimpleName() + " from=" + mob.getX() + "," + mob.getY()
            + " to=" + x + "," + y);
    }
    private static JSONObject phaseSweep(CurrentCombatHarness h, boolean npc) throws Exception {
        List<Long> delays = new ArrayList<Long>();
        JSONArray samples = new JSONArray();
        int stride = npc ? 10 : 1;
        int sx = npc ? 441 : 450, sy = npc ? 440 : 450;
        for (int phase = 0; phase < 430; phase += stride) {
            h.clock().reset(EPOCH);
            Mob mob = npc ? h.npc(NpcId.MOSS_GIANT.id(), sx, sy) : player(h);
            poll(mob); // Empty eligible poll: the original code charges this as movement.
            h.clock().advanceMillis(phase);
            walk(mob, sx + 1, sy);
            long firstPoll = phase == 0 ? 10 : ((phase + 9) / 10) * 10;
            for (long t = firstPoll; t <= 860; t += 10) {
                h.clock().advanceMillis(EPOCH + t - h.clock().currentTimeMillis());
                poll(mob);
                if (mob.getX() != sx) {
                    check(mob.getX() == sx + 1 && mob.getY() == sy, "exact first adjacent destination; npc=" + npc
                        + " phase=" + phase + " actual=" + mob.getX() + "," + mob.getY());
                    long delay = t - phase;
                    check(candidate ? delay <= 10 : t == 430, "first step follows selected eligibility policy");
                    delays.add(delay);
                    samples.put(new JSONArray().put(phase).put(delay));
                    break;
                }
            }
        }
        check(delays.size() == (npc ? 43 : 430), "every phase has a real displacement");
        Collections.sort(delays);
        return new JSONObject().put("samples", samples).put("count", delays.size())
            .put("p50Millis", percentile(delays, .50)).put("p95Millis", percentile(delays, .95))
            .put("maxMillis", delays.get(delays.size() - 1));
    }
    private static void poll(Mob mob) {
        // Match Server's intertick path: NPC behavior decisions are a separate owner.
        if (mob instanceof Npc) ((Npc) mob).updateMovementOnly();
        else mob.updatePosition();
    }
    private static long percentile(List<Long> values, double p) {
        return values.get(Math.max(0, (int) Math.ceil(values.size() * p) - 1));
    }
    private static void cadenceAndReclicks(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);
        Player p = player(h);
        walk(p, 451, 450);p.updatePosition();
        check(p.getX() == 451, "initial authorized step");
        int x = 451, steps = 1;
        for (int t = 1; t <= 1720; t++) {
            h.clock().advanceMillis(1);
            walk(p, x + 1, 450);p.updatePosition();
            if (p.getX() != x) {
                check(t % 430 == 0, "reclicks never earn an early step");
                x = p.getX();steps++;
            }
        }
        check(steps == 5 && x == 455, "four full intervals produce exactly four more tiles");
    }
    private static void collisionAndHolds(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);
        Player p = player(h);walk(p, 451, 450);
        GameObject blocker = new GameObject(h.world(), Point.location(451, 450),
            SceneryId.DOOR_BANK_CLOSED.id(), 0, 0);
        h.world().registerGameObject(blocker);
        try {
            p.updatePosition();check(p.getX() == 450, "collision checked at movement commitment");
        } finally {
            h.world().unregisterGameObject(blocker);
        }
        h.openCombatProjectileRectangle(450, 452, 450, 450);
        h.clock().advanceMillis(10);walk(p, 451, 450);p.updatePosition();
        check(candidate ? p.getX() == 451 : p.getX() == 450, "blocked attempt does not consume candidate cadence");
        h.clock().reset(EPOCH);
        Player held = player(h);Npc cockatrice = h.npc(CockatriceCombat.NPC_ID, 449, 450);
        CockatriceCombat.onMeleeSwing(cockatrice, held, false);
        check(CockatriceCombat.movementBlocked(held), "actual Cockatrice effect blocks movement");
        walk(held, 451, 450);held.updatePosition();
        check(held.getX() == 450, "effect hold remains authoritative");
        for (int tick = 0; tick < CockatriceCombat.LEG_FREEZE_TICKS; tick++) h.advanceOneCombatTick();
        check(!CockatriceCombat.movementBlocked(held), "effect naturally releases");
        held.updatePosition();check(held.getX() == 451, "eligible path resumes after hold");
        held.updatePosition();check(held.getX() == 451, "release cannot create a catch-up burst");
    }
    private static void teleportOutcome(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);Player p = player(h);
        p.setAttribute("blink", true);walk(p, 453, 450);p.updatePosition();
        check(p.getX() == 453, "queued blink is an actual displacement");
        p.removeAttribute("blink");h.clock().advanceMillis(10);walk(p, 454, 450);p.updatePosition();
        check(p.getX() == 453, "blink outcome consumes movement cadence");
    }
    private static void legacyControl(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);h.server().getConfig().WANT_MYWORLD = false;
        Player p = player(h);p.updatePosition();h.clock().advanceMillis(1);walk(p, 451, 450);p.updatePosition();
        check(p.getX() == 450, "legacy profile retains original empty-poll timing");
        h.clock().advanceMillis(429);p.updatePosition();check(p.getX() == 451, "legacy still steps at configured deadline");
        h.server().getConfig().WANT_MYWORLD = true;
    }
    private static void nanos(CurrentCombatHarness h, long value) throws Exception {
        Field f = MutableGameClock.class.getDeclaredField("nanos");f.setAccessible(true);f.setLong(h.clock(), value);
    }
    private static void monotonicEpochs(CurrentCombatHarness h) throws Exception {
        for (long origin : new long[]{0L, -1_000_000_000L, Long.MAX_VALUE - 100_000_000L}) {
            h.clock().reset(EPOCH);nanos(h, origin);Player p = player(h);
            walk(p, 451, 450);p.updatePosition();check(p.getX() == 451, "arbitrary nanoTime origin admits first step");
            walk(p, 452, 450);nanos(h, origin + 429_999_999L);p.updatePosition();
            check(p.getX() == 451, "nanoTime wrap and negative epoch retain deadline");
            nanos(h, origin + 430_000_000L);p.updatePosition();check(p.getX() == 452, "nanoTime elapsed interval is wrap safe");
        }
    }
}
