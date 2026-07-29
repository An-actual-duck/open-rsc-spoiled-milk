#!/usr/bin/env python3
"""Exercise bounded, mutation-safe remastered animation-key caching."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"

FIXTURE = r"""
package orsc.remastered;

import com.openrsc.client.entityhandling.defs.extras.AnimationDef;

public final class RemasteredAnimationKeyCacheFixture {
	private RemasteredAnimationKeyCacheFixture() {
	}

	public static void main(String[] args) {
		RemasteredAnimationKeyCache cache = new RemasteredAnimationKeyCache();
		AnimationDef animation =
			new AnimationDef(" Demon ", " NPC ", 0, 0, 0, false, false, 100);

		String first = cache.key(animation, 3);
		assertEquals("sprite/npc/demon/3", first, "normalized animation key");
		assertSame(first, cache.key(animation, 3), "repeated frame key identity");
		assertEquals(1, cache.definitionCount(), "definition count");
		assertEquals(1, cache.cachedSlotCount(), "initial cached slot count");

		assertEquals("sprite/npc/demon/0", cache.key(animation, 0), "frame zero");
		assertEquals("sprite/npc/demon/17", cache.key(animation, 17), "initial capacity edge");
		assertEquals("sprite/npc/demon/18", cache.key(animation, 18), "grown capacity");
		assertEquals("sprite/npc/demon/255", cache.key(animation, 255), "cache limit");
		assertEquals(5, cache.cachedSlotCount(), "grown cached slot count");

		int beforeUncachedFrame = cache.cachedSlotCount();
		assertEquals("sprite/npc/demon/256", cache.key(animation, 256),
			"oversized frame fallback");
		assertEquals(beforeUncachedFrame, cache.cachedSlotCount(),
			"oversized frame must not grow retained storage");

		animation.name = "Imp";
		String changedName = cache.key(animation, 3);
		assertEquals("sprite/npc/imp/3", changedName, "name mutation invalidation");
		assertNotSame(first, changedName, "mutated definition must replace cached key");
		assertEquals(1, cache.definitionCount(), "mutation retains identity entry");
		assertEquals(1, cache.cachedSlotCount(), "mutation drops stale frame slots");

		animation.category = "bad category";
		assertNull(cache.key(animation, 3), "invalid category fallback key");
		assertNull(cache.key(animation, 3), "invalid key result is cached");
		assertEquals(1, cache.cachedSlotCount(), "invalid result consumes one stable slot");

		AnimationDef equalButDistinct =
			new AnimationDef("Imp", "bad category", 0, 0, 0, false, false, 200);
		assertNull(cache.key(equalButDistinct, 3), "distinct invalid definition");
		assertEquals(2, cache.definitionCount(), "identity-keyed definitions");
		assertEquals(2, cache.cachedSlotCount(), "distinct definition slot");

		assertNull(cache.key(null, 0), "null definition");
		assertNull(cache.key(animation, -1), "negative frame");
		assertEquals(2, cache.definitionCount(), "invalid inputs do not change cache");
		assertEquals(2, cache.cachedSlotCount(), "invalid inputs do not add slots");

		System.out.println("PASS: remastered animation key cache");
	}

	private static void assertEquals(String expected, String actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label);
		}
	}

	private static void assertNotSame(Object first, Object second, String label) {
		if (first == second) {
			throw new AssertionError(label);
		}
	}

	private static void assertNull(Object value, String label) {
		if (value != null) {
			throw new AssertionError(label + ": expected null but was " + value);
		}
	}
}
"""


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise AssertionError(f"{label} is missing {fragment!r}")


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    cache_source = (
        ROOT
        / "Client_Base/src/orsc/remastered/RemasteredAnimationKeyCache.java"
    ).read_text(encoding="utf-8")
    require(
        cache_source,
        "IdentityHashMap<AnimationDef, Entry>",
        "RemasteredAnimationKeyCache.java",
    )
    require(
        cache_source,
        "frame > MAX_CACHED_FRAME",
        "RemasteredAnimationKeyCache.java",
    )
    require(
        cache_source,
        "!entry.matches(category, name)",
        "RemasteredAnimationKeyCache.java",
    )

    resolver_source = (
        ROOT / "Client_Base/src/orsc/remastered/RemasteredSpriteResolver.java"
    ).read_text(encoding="utf-8")
    require(
        resolver_source,
        "animationKeyCache.key(animation, frame)",
        "RemasteredSpriteResolver.java",
    )

    graphics_source = (
        ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
    ).read_text(encoding="utf-8")
    require(
        graphics_source,
        "remasteredSpriteResolver.resolve(animation, offset, canonical)",
        "GraphicsController.java",
    )
    if "resolve(RemasteredSpriteKey.forAnimation(animation, offset)" in graphics_source:
        raise AssertionError(
            "GraphicsController still composes animation keys on each sprite selection"
        )

    with tempfile.TemporaryDirectory(prefix="remastered-animation-key-cache-") as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc/remastered"
        source_dir.mkdir(parents=True)
        source = source_dir / "RemasteredAnimationKeyCacheFixture.java"
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")

        compile_result = subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(CLIENT_JAR),
                "-d",
                str(temp),
                str(source),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if compile_result.returncode != 0:
            raise AssertionError(
                "remastered animation key fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.remastered.RemasteredAnimationKeyCacheFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "remastered animation key fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
