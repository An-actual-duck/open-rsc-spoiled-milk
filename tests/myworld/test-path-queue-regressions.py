#!/usr/bin/env python3
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE_JAR = ROOT / "server" / "core.jar"

JAVA_SOURCE = r"""
package com.openrsc.server.model;

import java.util.Iterator;

public final class PathQueueRegressionTest {
	private static void fail(String message) {
		throw new AssertionError(message);
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			fail(label + " expected " + expected + " but got " + actual);
		}
	}

	private static void assertPoint(Point point, int expectedX, int expectedY, String label) {
		if (point == null) {
			fail(label + " expected point " + expectedX + "," + expectedY + " but got null");
		}
		assertEquals(expectedX, point.getX(), label + " x");
		assertEquals(expectedY, point.getY(), label + " y");
	}

	private static void testDirectPathOrder() {
		Path path = new Path(null, Path.PathType.WALK_TO_POINT);

		path.addDirect(1, 1);
		path.addDirect(2, 2);
		path.addDirect(3, 3);

		assertEquals(3, path.size(), "direct path size");
		assertPoint(path.poll(), 3, 3, "first direct poll");
		assertPoint(path.poll(), 2, 2, "second direct poll");
		assertPoint(path.poll(), 1, 1, "third direct poll");
		assertEquals(0, path.size(), "direct path drained size");
	}

	private static void testFinishRemovesHead() {
		Path path = new Path(null, Path.PathType.WALK_TO_POINT);

		path.addDirect(10, 10);
		path.addDirect(20, 20);
		path.finish();

		assertEquals(1, path.size(), "finish size");
		assertPoint(path.poll(), 10, 10, "finish remaining point");
	}

	private static void testIteratorOrder() {
		Path path = new Path(null, Path.PathType.WALK_TO_POINT);

		path.addDirect(4, 4);
		path.addDirect(5, 5);

		Iterator<Point> iterator = path.iterator();
		if (!iterator.hasNext()) {
			fail("iterator missing first point");
		}
		assertPoint(iterator.next(), 5, 5, "iterator first point");
		if (!iterator.hasNext()) {
			fail("iterator missing second point");
		}
		assertPoint(iterator.next(), 4, 4, "iterator second point");
		if (iterator.hasNext()) {
			fail("iterator has unexpected extra point");
		}
	}

	private static void testLegacyDirectPathLimit() {
		Path path = new Path(null, Path.PathType.WALK_TO_POINT);

		for (int i = 0; i < 60; i++) {
			path.addDirect(i, i);
		}

		assertEquals(51, path.size(), "legacy addDirect maximum size");
		assertPoint(path.poll(), 50, 50, "legacy addDirect head");
	}

	private static void testAdjacentDistanceWrapperParityForSameTile() {
		Point point = new Point(42, 84);
		boolean objectResult = PathValidation.checkAdjacentDistance(null, point, point, true, false);
		boolean primitiveResult = PathValidation.checkAdjacentDistance(null, 42, 84, 42, 84, true, false);
		if (objectResult != primitiveResult) {
			fail("same-tile adjacent-distance wrapper result diverged from primitive result");
		}
	}

	public static void main(String[] args) {
		testDirectPathOrder();
		testFinishRemovesHead();
		testIteratorOrder();
		testLegacyDirectPathLimit();
		testAdjacentDistanceWrapperParityForSameTile();
		System.out.println("PASS: Path queue behavior validated");
	}
}
"""


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def run_command(args: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        capture_output=True,
        text=True,
    )


def main() -> None:
    if not CORE_JAR.exists():
        fail("Missing server/core.jar; run ./scripts/build-server.sh first")

    with tempfile.TemporaryDirectory(prefix="myworld-path-queue-") as tmp_dir_name:
        tmp_dir = Path(tmp_dir_name)
        src_dir = tmp_dir / "com" / "openrsc" / "server" / "model"
        classes_dir = tmp_dir / "classes"
        src_dir.mkdir(parents=True)
        classes_dir.mkdir()

        source_file = src_dir / "PathQueueRegressionTest.java"
        source_file.write_text(JAVA_SOURCE, encoding="utf-8")

        compile_result = run_command(
            [
                "javac",
                "-source",
                "1.8",
                "-target",
                "1.8",
                "-cp",
                str(CORE_JAR),
                "-d",
                str(classes_dir),
                str(source_file),
            ],
            ROOT,
        )
        if compile_result.returncode != 0:
            fail("Path queue regression compile failed:\n" + compile_result.stdout + compile_result.stderr)

        run_result = run_command(
            [
                "java",
                "-cp",
                f"{classes_dir}:{CORE_JAR}",
                "com.openrsc.server.model.PathQueueRegressionTest",
            ],
            ROOT,
        )
        if run_result.returncode != 0:
            fail("Path queue regression run failed:\n" + run_result.stdout + run_result.stderr)

        output = run_result.stdout + run_result.stderr
        if "PASS: Path queue behavior validated" not in output:
            fail("Path queue regression run did not report success:\n" + output)

    print("PASS: Path queue behavior validated")


if __name__ == "__main__":
    main()
