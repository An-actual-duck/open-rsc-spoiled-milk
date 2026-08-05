#!/usr/bin/env python3
"""Validate that NPC poison cannot cross a death/respawn lifecycle."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
MOB = SERVER / "src/com/openrsc/server/model/entity/Mob.java"
NPC = SERVER / "src/com/openrsc/server/model/entity/npc/Npc.java"
POISON_EVENT = SERVER / "src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.model.entity.npc;

import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.EntityType;
import com.openrsc.server.model.entity.Mob;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcPoisonDeathLifecycleFixture {
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static Field field(Class<?> owner, String name) throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Npc bareNpc() throws Exception {
		Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Unsafe unsafe = (Unsafe) unsafeField.get(null);
		Npc npc = (Npc) unsafe.allocateInstance(Npc.class);
		field(Entity.class, "attributes").set(npc, new ConcurrentHashMap<String, Object>());
		field(Entity.class, "entityType").set(npc, EntityType.NPC);
		return npc;
	}

	private static void applicationGateChecks() throws Exception {
		Npc npc = bareNpc();
		check(npc.canReceivePoison(), "a live NPC must accept poison");

		npc.killed = true;
		check(!npc.canReceivePoison(), "a killed NPC must reject poison");
		npc.applyPoison(20, 40);
		check(npc.getPoisonDamage() == 0 && npc.getPoisonMaxPower() == 0,
			"late poison changed a killed NPC");

		npc.killed = false;
		field(Entity.class, "removed").setBoolean(npc, true);
		check(!npc.canReceivePoison(), "a removed NPC must reject poison");
		npc.applyPoison(20, 40);
		check(npc.getPoisonDamage() == 0 && npc.getPoisonMaxPower() == 0,
			"late poison changed a removed NPC");

		field(Entity.class, "removed").setBoolean(npc, false);
		field(Npc.class, "isRespawning").setBoolean(npc, true);
		check(!npc.canReceivePoison(), "a respawning NPC must reject poison");
		npc.applyPoison(20, 40);
		check(npc.getPoisonDamage() == 0 && npc.getPoisonMaxPower() == 0,
			"late poison changed a respawning NPC");
	}

	private static void cleanupChecks() throws Exception {
		Npc npc = bareNpc();
		field(Mob.class, "poisonDamage").setInt(npc, 37);
		field(Mob.class, "poisonMaxPower").setInt(npc, 80);
		field(Mob.class, "poisonOwnerId").set(npc, UUID.randomUUID());

		npc.curePoison();
		check(npc.getPoisonDamage() == 0, "death cleanup retained poison power");
		check(npc.getPoisonMaxPower() == 0, "death cleanup retained poison ceiling");
		check(field(Mob.class, "poisonOwnerId").get(npc) == null,
			"death cleanup retained poison attribution");
	}

	public static void main(String[] args) throws Exception {
		applicationGateChecks();
		cleanupChecks();
	}
}
"""


def validate_lifecycle_wiring() -> None:
    mob = MOB.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")
    poison_event = POISON_EVENT.read_text(encoding="utf-8")

    apply_poison = mob.split(
        "public void applyPoison(final int appliedPoisonPower, final int maxPoisonPower, final Mob poisonSource)",
        1,
    )[1].split("public void applyPoison(final int poisonPower)", 1)[0]
    require("!((Npc) this).canReceivePoison()" in apply_poison,
            "shared poison application does not reject dead NPCs")

    predicate = npc.split("public boolean canReceivePoison()", 1)[1].split(
        "private void setRespawning", 1
    )[0]
    require("!killed && !isRemoved() && !isRespawning()" in predicate,
            "NPC poison acceptance omits a lifecycle state")

    removal = npc.split("private void removeWithinLayeredOwnerLifecycle()", 1)[1].split(
        "public boolean isInvisibleTo", 1
    )[0]
    require(removal.index("curePoison();") < removal.index("this.killed = true;"),
            "NPC removal does not clear poison before ending the lifetime")
    require(removal.index("n.curePoison();") < removal.index("n.killed = false;"),
            "NPC respawn exposes the new lifetime before defensive poison cleanup")

    already_killed = npc.split("if (this.killed) {", 1)[1].split("}", 1)[0]
    require("this.curePoison();" in already_killed,
            "repeated NPC death handling can retain late poison")

    poison_run = poison_event.split("public void run()", 1)[1].split(
        "private void applyLeach", 1
    )[0]
    require("!((Npc) mob).canReceivePoison()" in poison_run
            and "mob.curePoison();" in poison_run,
            "a scheduled poison tick does not self-clear for a dead NPC")


def run_compiled_fixture() -> None:
    require(CORE_JAR.is_file(), "server/core.jar is missing; build the server first")
    with tempfile.TemporaryDirectory(prefix="npc-poison-death-") as temporary:
        temp = Path(temporary)
        fixture = temp / (
            "com/openrsc/server/model/entity/npc/NpcPoisonDeathLifecycleFixture.java"
        )
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            [
                "javac",
                "-XDignore.symbol.file",
                "-cp",
                str(CORE_JAR),
                "-d",
                str(classes),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            [
                "java",
                "-cp",
                f"{classes}:{CORE_JAR}",
                "com.openrsc.server.model.entity.npc.NpcPoisonDeathLifecycleFixture",
            ],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_lifecycle_wiring()
    run_compiled_fixture()
    print("PASS: NPC poison is bounded to one live death/respawn lifecycle")


if __name__ == "__main__":
    main()
