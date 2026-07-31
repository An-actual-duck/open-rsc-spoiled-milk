#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "server/src/com/openrsc/server/model/NpcMovementBoundary.java"
MOB_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
WALKING_SOURCE = ROOT / "server/src/com/openrsc/server/model/WalkingQueue.java"


SOURCES = {
    "com/openrsc/server/model/Point.java": r"""
        package com.openrsc.server.model;

        public final class Point {
            private final int x;
            private final int y;

            private Point(int x, int y) { this.x = x; this.y = y; }
            public static Point location(int x, int y) { return new Point(x, y); }
            public int getX() { return x; }
            public int getY() { return y; }
            public boolean inBounds(int minX, int minY, int maxX, int maxY) {
                return x >= minX && x <= maxX && y >= minY && y <= maxY;
            }
        }
    """,
    "com/openrsc/server/external/NPCLoc.java": r"""
        package com.openrsc.server.external;

        public final class NPCLoc {
            private final int minX;
            private final int maxX;
            private final int minY;
            private final int maxY;

            public NPCLoc(int minX, int maxX, int minY, int maxY) {
                this.minX = minX;
                this.maxX = maxX;
                this.minY = minY;
                this.maxY = maxY;
            }
            public int minX() { return minX; }
            public int maxX() { return maxX; }
            public int minY() { return minY; }
            public int maxY() { return maxY; }
        }
    """,
    "com/openrsc/server/model/entity/Mob.java": r"""
        package com.openrsc.server.model.entity;

        public class Mob {
            private final boolean npc;
            public Mob(boolean npc) { this.npc = npc; }
            public boolean isNpc() { return npc; }
        }
    """,
    "com/openrsc/server/model/entity/npc/Npc.java": r"""
        package com.openrsc.server.model.entity.npc;

        import com.openrsc.server.external.NPCLoc;
        import com.openrsc.server.model.Point;
        import com.openrsc.server.model.entity.Mob;

        public final class Npc extends Mob {
            private final NPCLoc loc;
            private final boolean combat;
            private final boolean chasing;
            private final boolean summoned;

            public Npc(NPCLoc loc, boolean combat, boolean chasing, boolean summoned) {
                super(true);
                this.loc = loc;
                this.combat = combat;
                this.chasing = chasing;
                this.summoned = summoned;
            }
            public NPCLoc getLoc() { return loc; }
            public boolean inCombat() { return combat; }
            public boolean isChasing() { return chasing; }
            public boolean isSummonedFixture() { return summoned; }
            public boolean inRoamBounds(Point point) {
                int minX = loc.maxX() - loc.minX() > 2 ? loc.minX() + 1 : loc.minX();
                int maxX = loc.maxX() - loc.minX() > 2 ? loc.maxX() - 1 : loc.maxX();
                int minY = loc.maxY() - loc.minY() > 2 ? loc.minY() + 1 : loc.minY();
                int maxY = loc.maxY() - loc.minY() > 2 ? loc.maxY() - 1 : loc.maxY();
                return point.inBounds(minX, minY, maxX, maxY);
            }
        }
    """,
    "com/openrsc/server/content/Summoning.java": r"""
        package com.openrsc.server.content;

        import com.openrsc.server.model.entity.Mob;
        import com.openrsc.server.model.entity.npc.Npc;

        public final class Summoning {
            public static boolean isSummon(Mob mob) {
                return mob instanceof Npc && ((Npc) mob).isSummonedFixture();
            }
        }
    """,
    "com/openrsc/server/model/NpcMovementBoundaryFixture.java": r"""
        package com.openrsc.server.model;

        import com.openrsc.server.external.NPCLoc;
        import com.openrsc.server.model.entity.Mob;
        import com.openrsc.server.model.entity.npc.Npc;

        public final class NpcMovementBoundaryFixture {
            public static void main(String[] args) {
                NPCLoc combatBounds = new NPCLoc(10, 12, 10, 12);
                Npc hostile = new Npc(combatBounds, true, true, false);

                check(!NpcMovementBoundary.allows(hostile, Point.location(9, 11)),
                    "hostile cannot select outside tile beside boundary player");
                check(NpcMovementBoundary.allows(hostile, Point.location(10, 10)),
                    "hostile can select legal adjacent tile inside combat bounds");

                Npc singleTileHostile = new Npc(
                    new NPCLoc(10, 10, 10, 10), true, true, false);
                int[][] adjacent = {
                    {0, -1}, {-1, 0}, {1, 0}, {0, 1},
                    {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
                };
                for (int[] offset : adjacent) {
                    check(!NpcMovementBoundary.allows(singleTileHostile,
                            Point.location(10 + offset[0], 10 + offset[1])),
                        "no adjacent destination may escape a single-tile boundary");
                }

                Mob player = new Mob(false);
                check(NpcMovementBoundary.allows(player, Point.location(-500, 9000)),
                    "players remain exempt");

                Npc summon = new Npc(combatBounds, true, true, true);
                check(NpcMovementBoundary.allows(summon, Point.location(9, 11)),
                    "summoned NPCs remain exempt");

                Npc roaming = new Npc(
                    new NPCLoc(10, 14, 10, 14), false, false, false);
                check(!NpcMovementBoundary.allows(roaming, Point.location(10, 11)),
                    "roaming NPC retains inset bounds");
                check(NpcMovementBoundary.allows(roaming, Point.location(11, 11)),
                    "roaming NPC can use inset interior");
            }

            private static void check(boolean condition, String label) {
                if (!condition) throw new AssertionError(label);
            }
        }
    """,
}


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="npc-movement-boundary-") as directory:
        temp = Path(directory)
        classes = temp / "classes"
        classes.mkdir()
        paths = []
        for relative, source in SOURCES.items():
            path = temp / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(textwrap.dedent(source).strip() + "\n", encoding="utf-8")
            paths.append(path)
        paths.append(POLICY)
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(classes),
                *(str(path) for path in paths),
            ],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.model.NpcMovementBoundaryFixture"],
            cwd=ROOT,
            check=True,
        )

    mob = MOB_SOURCE.read_text(encoding="utf-8")
    walking = WALKING_SOURCE.read_text(encoding="utf-8")
    if "NpcMovementBoundary.allows(this, candidate)" not in mob:
        raise AssertionError("melee-adjacent selection does not share boundary policy")
    if "NpcMovementBoundary.allows(npc, destinationPoint)" not in walking:
        raise AssertionError("movement execution does not share boundary policy")
    print("PASS: NPC selection and movement share executable boundary policy")


if __name__ == "__main__":
    main()
