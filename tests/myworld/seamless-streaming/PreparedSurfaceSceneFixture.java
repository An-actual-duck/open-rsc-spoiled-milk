package orsc.graphics.three;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import orsc.graphics.two.GraphicsController;

/** Checks the staged scene order and the stale-membership fallback. */
public final class PreparedSurfaceSceneFixture {
	private static final Field MODELS = field("models");
	private static final Field COUNT = field("modelCount");
	private static final Field METADATA = field("m_jb");

	public static void main(String[] args) throws Exception {
		RSModel keep = new RSModel(1, 1);
		RSModel departing = new RSModel(1, 1);
		RSModel oldRoof = new RSModel(1, 1);
		RSModel newWall = new RSModel(1, 1);
		RSModel newRoof = new RSModel(1, 1);
		RSModel[] initial = {keep, departing, oldRoof, keep, departing};
		Scene literal = scene(initial);
		Scene staged = scene(initial);
		IdentityHashMap<RSModel, Boolean> removals = new IdentityHashMap<>();
		removals.put(departing, Boolean.TRUE);
		removals.put(oldRoof, Boolean.TRUE);
		RSModel[] walls = {null, newWall};
		RSModel[] roofs = {newRoof, null};

		Scene.PreparedModels proposal = staged.prepareModelReplacement(removals, walls, roofs);
		check(!literal.installPreparedModels(proposal), "another scene accepted the proposal");
		check(COUNT.getInt(staged) == initial.length, "preparation mutated the live scene");
		literal.removeModels(new RSModel[]{departing}, 1);
		literal.removeModels(new RSModel[]{oldRoof}, 1);
		for (RSModel wall : walls) literal.addModel(wall);
		for (RSModel roof : roofs) literal.addModel(roof);
		check(staged.installPreparedModels(proposal), "matching scene rejected the proposal");
		check(!staged.installPreparedModels(proposal), "same proposal installed twice");
		assertSameActiveOrder(literal, staged);

		proposal = staged.prepareModelReplacement(removals, walls, null);
		staged.addModel(departing);
		staged.removeModels(new RSModel[]{departing}, 1);
		check(!staged.installPreparedModels(proposal), "same-sized scene with newer membership accepted the proposal");
		assertSameActiveOrder(literal, staged);
		System.out.println("PASS staged scenery and terrain preserve model order and metadata and reject stale membership");
	}

	private static Scene scene(RSModel[] initial) throws Exception {
		Constructor<GraphicsController> graphics = GraphicsController.class
			.getDeclaredConstructor(int.class, int.class, int.class);
		graphics.setAccessible(true);
		Scene scene = new Scene(graphics.newInstance(32, 32, 1), 2, 8, 2);
		for (RSModel model : initial) scene.addModel(model);
		int[] metadata = (int[]) METADATA.get(scene);
		for (int i = 0; i < initial.length; i++) metadata[i] = i + 1;
		return scene;
	}

	private static void assertSameActiveOrder(Scene literal, Scene staged) throws IllegalAccessException {
		int count = COUNT.getInt(literal);
		check(COUNT.getInt(staged) == count, "model count changed");
		RSModel[] expected = (RSModel[]) MODELS.get(literal);
		RSModel[] actual = (RSModel[]) MODELS.get(staged);
		int[] expectedMetadata = (int[]) METADATA.get(literal);
		int[] actualMetadata = (int[]) METADATA.get(staged);
		for (int i = 0; i < count; i++) {
			check(actual[i] == expected[i], "model order changed at " + i);
			check(actualMetadata[i] == expectedMetadata[i], "metadata changed at " + i);
		}
	}

	private static Field field(String name) {
		try {
			Field field = Scene.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
