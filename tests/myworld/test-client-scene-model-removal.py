#!/usr/bin/env python3
"""Correctness and deterministic cost probe for batched Scene removals."""

from pathlib import Path
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"


FIXTURE = r"""
package orsc.graphics.three;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SceneModelRemovalFixture {
	private static final Field MODELS = field("models");
	private static final Field MODEL_COUNT = field("modelCount");
	private static final Field MODEL_METADATA = field("m_jb");

	public static void main(String[] args) throws Exception {
		final int modelCount = 12000;
		final int removalCount = 3000;
		RSModel[] models = models(modelCount);
		RSModel[] removals = new RSModel[removalCount];
		for (int index = 0; index < removalCount; index++) {
			removals[index] = models[index * 3 + 1];
		}

		Scene sequential = scene(models);
		long sequentialStarted = System.nanoTime();
		for (RSModel removal : removals) {
			sequential.removeModel(removal);
		}
		long sequentialNanos = System.nanoTime() - sequentialStarted;

		Method bulk;
		try {
			bulk = Scene.class.getMethod(
				"removeModels", RSModel[].class, Integer.TYPE);
		} catch (NoSuchMethodException missing) {
			System.out.println(
				"scene-removal baseline sequentialNanos=" + sequentialNanos);
			return;
		}
		Scene batched = scene(models);
		long batchedStarted = System.nanoTime();
		bulk.invoke(batched, removals, Integer.valueOf(removalCount));
		long batchedNanos = System.nanoTime() - batchedStarted;

		assertEquivalent(sequential, batched);
		System.out.println(
			"scene-removal sequentialNanos=" + sequentialNanos
				+ " batchedNanos=" + batchedNanos
				+ " speedup="
				+ String.format("%.2f", sequentialNanos / (double) batchedNanos));
	}

	private static Scene scene(RSModel[] source) throws Exception {
		Scene scene = allocateWithoutConstructor();
		RSModel[] models = source.clone();
		int[] metadata = new int[models.length];
		for (int index = 0; index < metadata.length; index++) {
			metadata[index] = index * 17 + 3;
		}
		MODELS.set(scene, models);
		MODEL_METADATA.set(scene, metadata);
		MODEL_COUNT.setInt(scene, models.length);
		return scene;
	}

	private static Scene allocateWithoutConstructor() throws Exception {
		Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
		Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Object unsafe = unsafeField.get(null);
		return (Scene) unsafeClass.getMethod("allocateInstance", Class.class)
			.invoke(unsafe, Scene.class);
	}

	private static RSModel[] models(int count) {
		RSModel[] models = new RSModel[count];
		for (int index = 0; index < count; index++) {
			models[index] = new RSModel(1, 1);
		}
		return models;
	}

	private static void assertEquivalent(Scene expected, Scene actual)
			throws Exception {
		int expectedCount = MODEL_COUNT.getInt(expected);
		int actualCount = MODEL_COUNT.getInt(actual);
		check(expectedCount == actualCount, "retained count");
		RSModel[] expectedModels = (RSModel[]) MODELS.get(expected);
		RSModel[] actualModels = (RSModel[]) MODELS.get(actual);
		int[] expectedMetadata = (int[]) MODEL_METADATA.get(expected);
		int[] actualMetadata = (int[]) MODEL_METADATA.get(actual);
		for (int index = 0; index < expectedCount; index++) {
			check(expectedModels[index] == actualModels[index],
				"model order at " + index);
			check(expectedMetadata[index] == actualMetadata[index],
				"parallel metadata at " + index);
		}
		for (int index = expectedCount; index < actualModels.length; index++) {
			check(actualModels[index] == null, "released model slot " + index);
		}
	}

	private static Field field(String name) {
		try {
			Field field = Scene.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
"""


def main() -> None:
    source = SCENE.read_text(encoding="utf-8")
    required = (
        "public final void removeModels(RSModel[] removals, int removalCount)",
        "IdentityHashMap<RSModel, Boolean>",
        "Arrays.fill(this.models, retained, this.modelCount, null)",
    )
    for fragment in required:
        if fragment not in source:
            raise AssertionError(f"batched Scene removal missing: {fragment}")

    with tempfile.TemporaryDirectory(prefix="scene-model-removal-") as raw:
        temp = Path(raw)
        fixture = temp / "orsc/graphics/three/SceneModelRemovalFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-cp",
                str(CLIENT_JAR),
                "-d",
                str(temp),
                str(SCENE),
                str(fixture),
            ],
            check=True,
            cwd=ROOT,
        )
        result = subprocess.run(
            ["java", "-cp", f"{temp}:{CLIENT_JAR}",
             "orsc.graphics.three.SceneModelRemovalFixture"],
            check=True,
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
        )
        print(result.stdout.strip())

    print("PASS: Scene model removals compact once with exact order and metadata")


if __name__ == "__main__":
    main()
