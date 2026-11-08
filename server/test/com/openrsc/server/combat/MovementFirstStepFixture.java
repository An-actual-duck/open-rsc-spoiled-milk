package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.monsterslayer.CockatriceCombat;
import com.openrsc.server.model.Point;
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
            Path phases = output.resolveSibling(output.getFileName().toString().replaceFirst("\\.json$", "") + "-phases.json");
            if (Files.exists(phases)) throw new IllegalArgumentException("Never overwrite phase evidence");
            Files.createDirectories(phases.toAbsolutePath().getParent());
            Files.write(phases, (result.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            cadenceAndReclicks(h);
            recentStopAndReadiness(h);
            collisionAndHolds(h);
            npcBoundary(h);
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
        for (boolean npc : new boolean[]{false, true}) {
            h.clock().reset(EPOCH);
            Mob mob = npc ? h.npc(NpcId.MOSS_GIANT.id(), 441, 440) : player(h);
            int startX = mob.getX(), y = mob.getY();
            walk(mob, startX + 1, y);poll(mob);
            check(mob.getX() == startX + 1, "initial authorized step; npc=" + npc);
            int x = startX + 1, steps = 1;
            int intervals = 4;
            for (int t = 1; t <= intervals * 430; t++) {
                h.clock().advanceMillis(1);
                walk(mob, npc ? (x == startX ? startX + 1 : startX) : x + 1, y);poll(mob);
                if (mob.getX() != x) {
                    check(t % 430 == 0, "reclicks never earn an early step; npc=" + npc);
                    check(Math.abs(mob.getX() - x) == 1 && mob.getY() == y,
                        "each committed trajectory is exactly one adjacent tile");
                    x = mob.getX();steps++;
                }
            }
            check(steps == intervals + 1 && x == startX + (npc ? 1 : intervals + 1),
                "elapsed intervals produce exactly one additional tile apiece; npc=" + npc);
        }
    }
    private static void collisionAndHolds(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);
        Player p = player(h);walk(p, 451, 450);
        // Add a real live collision owner after path admission. Native terrain is immutable;
        // mutating a legacy tile view would not test the authoritative collision route.
        Player blocker = h.player("block" + serial++, 451, 450);
        h.server().getConfig().PLAYER_BLOCKING = 2;
        try {
            p.updatePosition();check(p.getX() == 450, "live collision checked at movement commitment");
        } finally {
            h.logout(blocker);
            h.server().getConfig().PLAYER_BLOCKING = 0;
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
    private static void recentStopAndReadiness(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);Player p = player(h);
        walk(p, 451, 450);p.updatePosition();check(p.getX() == 451, "precondition: previous successful step");
        h.clock().advanceMillis(430);p.updatePosition(); // finish old path without another successful step
        h.clock().advanceMillis(1);walk(p, 452, 450);p.updatePosition();
        check(candidate ? p.getX() == 452 : p.getX() == 451, "idle endpoint cleanup does not restart candidate deadline");
        h.clock().reset(EPOCH);Player held = player(h);
        boolean readiness = h.server().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS;
        h.server().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS = true;
        held.setAttribute("native_terrain_self_appearance_pending", Boolean.TRUE);
        walk(held, 451, 450);held.updatePosition();
        check(held.getX() == 450 && !held.getWalkingQueue().finished(), "terrain readiness preserves queued movement");
        held.removeAttribute("native_terrain_self_appearance_pending");
        h.clock().advanceMillis(10);held.updatePosition();
        check(candidate ? held.getX() == 451 : held.getX() == 450, "candidate resumes when actual readiness hold releases");
        h.server().getConfig().WANT_LAYERED_NATIVE_TERRAIN_READINESS = readiness;
        h.clock().reset(EPOCH);Player stationary = player(h);
        com.openrsc.server.model.Path noDisplacement = new com.openrsc.server.model.Path(
            stationary, com.openrsc.server.model.Path.PathType.WALK_TO_POINT);
        noDisplacement.addDirect(stationary.getX(), stationary.getY());
        stationary.getWalkingQueue().setPath(noDisplacement);stationary.updatePosition();
        check(stationary.getX() == 450, "same-tile queued waypoint has no displacement");
        h.clock().advanceMillis(10);walk(stationary, 451, 450);stationary.updatePosition();
        check(candidate ? stationary.getX() == 451 : stationary.getX() == 450,
            "same-tile waypoint does not consume a successful-step interval");
        h.clock().reset(EPOCH);Player staggered = player(h);
        check(staggered.applyStagger(1), "attack stagger precondition");
        walk(staggered, 451, 450);staggered.updatePosition();
        check(staggered.getX() == 451, "attack stagger is not turned into a movement lock");
    }
    private static void npcBoundary(CurrentCombatHarness h) throws Exception {
        h.clock().reset(EPOCH);
        Npc bounded = new Npc(h.world(), NpcId.MOSS_GIANT.id(), 441, 440,
            441, 441, 440, 440);
        h.world().registerNpc(bounded);
        try {
            check(com.openrsc.server.model.PathValidation.checkAdjacent(bounded, 441, 440, 442, 440),
                "boundary control destination passes ordinary terrain collision");
            walk(bounded, 442, 440);poll(bounded);
            check(bounded.getX() == 441 && bounded.getY() == 440
                && bounded.getWalkingQueue().finished(), "NPC roam boundary rejects queued destination");
        } finally { h.world().unregisterNpc(bounded); }
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
        h.clock().reset(EPOCH);nanos(h, 0L);Player p = player(h);
        walk(p, 451, 450);p.updatePosition();walk(p, 452, 450);
        Field millis = MutableGameClock.class.getDeclaredField("millis");millis.setAccessible(true);
        millis.setLong(h.clock(), EPOCH + 86_400_000L);nanos(h, 1L);p.updatePosition();
        check(p.getX() == 451, "wall-clock forward jump cannot grant an early step");
        millis.setLong(h.clock(), EPOCH - 86_400_000L);nanos(h, 430_000_000L);p.updatePosition();
        check(p.getX() == 452, "wall-clock rollback cannot freeze monotonic movement");
    }
}
