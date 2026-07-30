#!/usr/bin/env python3
"""Verify exact runtime signatures for measured hot LWJGL method handles."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
LWJGL_BINDINGS_SOURCE = ROOT / "PC_Client/src/orsc/LwjglBindings.java"

FIXTURE = r"""
package orsc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class Renderer3DLwjglHotHandleFixture {
	private Renderer3DLwjglHotHandleFixture() {
	}

	public static void main(String[] args) throws Exception {
		Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
		Class<?> gl11 = Class.forName("org.lwjgl.opengl.GL11");
		Class<?> gl13 = Class.forName("org.lwjgl.opengl.GL13");
		Class<?> gl20 = Class.forName("org.lwjgl.opengl.GL20");

		assertVoidHandle(glfw, "glfwPollEvents");
		assertVoidHandle(
			gl11,
			"glClearColor",
			float.class,
			float.class,
			float.class,
			float.class);
		assertVoidHandle(gl11, "glClear", int.class);
		assertVoidHandle(gl11, "glPopAttrib");
		assertVoidHandle(gl11, "glPolygonOffset", float.class, float.class);
		assertVoidHandle(gl13, "glActiveTexture", int.class);
		assertVoidHandle(
			gl11,
			"glTexSubImage2D",
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			ByteBuffer.class);
		assertVoidHandle(gl11, "glAlphaFunc", int.class, float.class);
		assertVoidHandle(gl11, "glDepthMask", boolean.class);
		assertVoidHandle(gl11, "glMatrixMode", int.class);
		assertVoidHandle(gl11, "glLoadIdentity");
		assertVoidHandle(gl11, "glLoadMatrixf", FloatBuffer.class);
		assertVoidHandle(
			gl11,
			"glOrtho",
			double.class,
			double.class,
			double.class,
			double.class,
			double.class,
			double.class);
		assertVoidHandle(gl11, "glBegin", int.class);
		assertVoidHandle(gl11, "glEnd");
		assertVoidHandle(gl20, "glUniform1i", int.class, int.class);

		System.out.println(
			"PASS: renderer-v2 measured LWJGL calls use exact typed handles");
	}

	private static void assertVoidHandle(
		Class<?> owner,
		String name,
		Class<?>... parameterTypes)
		throws Exception {
		MethodHandle handle =
			MethodHandles.publicLookup().unreflect(owner.getMethod(name, parameterTypes));
		MethodType expected = MethodType.methodType(void.class, parameterTypes);
		if (!expected.equals(handle.type())) {
			throw new AssertionError(
				owner.getName()
					+ "."
					+ name
					+ ": expected "
					+ expected
					+ " but was "
					+ handle.type());
		}
	}
}
"""


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise AssertionError(f"missing {label}: {fragment}")


def forbid(source: str, fragment: str, label: str) -> None:
    if fragment in source:
        raise AssertionError(f"unexpected {label}: {fragment}")


def main() -> None:
    if not CLIENT_JAR.exists():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    source = LWJGL_BINDINGS_SOURCE.read_text(encoding="utf-8")
    calls = (
        ("glfwPollEvents", "glfwPollEvents.invokeExact();", "invoke(glfwPollEvents);"),
        (
            "glClearColor",
            "glClearColor.invokeExact(red, green, blue, alpha);",
            "invoke(glClearColor, red, green, blue, alpha);",
        ),
        ("glClear", "glClear.invokeExact(mask);", "invoke(glClear, mask);"),
        ("glPopAttrib", "glPopAttrib.invokeExact();", "invoke(glPopAttrib);"),
        (
            "glPolygonOffset",
            "glPolygonOffset.invokeExact(factor, units);",
            "invoke(glPolygonOffset, factor, units);",
        ),
        (
            "glActiveTexture",
            "glActiveTexture.invokeExact(textureUnit);",
            "invoke(glActiveTexture, textureUnit);",
        ),
        (
            "glTexSubImage2D",
            "glTexSubImage2D.invokeExact(",
            "invoke(glTexSubImage2D,",
        ),
        (
            "glAlphaFunc",
            "glAlphaFunc.invokeExact(function, reference);",
            "invoke(glAlphaFunc, function, reference);",
        ),
        ("glDepthMask", "glDepthMask.invokeExact(flag);", "invoke(glDepthMask, flag);"),
        ("glMatrixMode", "glMatrixMode.invokeExact(mode);", "invoke(glMatrixMode, mode);"),
        ("glLoadIdentity", "glLoadIdentity.invokeExact();", "invoke(glLoadIdentity);"),
        (
            "glLoadMatrixf",
            "glLoadMatrixf.invokeExact(matrix);",
            "invoke(glLoadMatrixf, matrix);",
        ),
        (
            "glOrtho",
            "glOrtho.invokeExact(left, right, bottom, top, near, far);",
            "invoke(glOrtho, left, right, bottom, top, near, far);",
        ),
        ("glBegin", "glBegin.invokeExact(mode);", "invoke(glBegin, mode);"),
        ("glEnd", "glEnd.invokeExact();", "invoke(glEnd);"),
        (
            "glUniform1i",
            "glUniform1i.invokeExact(location, value);",
            "invoke(glUniform1i, location, value);",
        ),
    )
    for name, exact_call, reflective_call in calls:
        require(source, f"private final MethodHandle {name};", f"{name} handle field")
        require(source, exact_call, f"{name} exact invocation")
        forbid(source, reflective_call, f"{name} reflective invocation")

    with tempfile.TemporaryDirectory(
        prefix="renderer-lwjgl-hot-handles-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DLwjglHotHandleFixture.java"
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")

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
                str(fixture),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if compile_result.returncode != 0:
            raise AssertionError(
                "LWJGL hot-handle fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.Renderer3DLwjglHotHandleFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "LWJGL hot-handle fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
