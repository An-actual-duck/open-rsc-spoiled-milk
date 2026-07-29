package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.RSModel;

import java.util.Arrays;

/**
 * Owns the parallel storage for client game-object and wall-object instances.
 * Packet ordering, model preparation, scene/world side effects, and rendering
 * policy remain with their existing owners.
 */
final class ClientSceneInstanceStore {
	static final int WALL_OBJECT_KEY_BASE = 20000;
	static final int GAME_OBJECT_INITIAL_CAPACITY = WALL_OBJECT_KEY_BASE;
	static final int WALL_OBJECT_INITIAL_CAPACITY = 5000;

	private boolean[] gameObjectFrameMarks;
	private int[] gameObjectDirections;
	private int[] gameObjectIds;
	private boolean[] gameObjectMaterialized;
	private boolean[] gameObjectPendingAreaLoad;
	private RSModel[] gameObjectModels;
	private int[] gameObjectX;
	private int[] gameObjectZ;
	private int gameObjectCount;
	private long gameObjectRevision;

	private boolean[] wallObjectFrameMarks;
	private int[] wallObjectDirections;
	private int[] wallObjectIds;
	private boolean[] wallObjectMaterialized;
	private boolean[] wallObjectPendingAreaLoad;
	private RSModel[] wallObjectModels;
	private int[] wallObjectX;
	private int[] wallObjectZ;
	private int wallObjectCount;

	ClientSceneInstanceStore() {
		this(GAME_OBJECT_INITIAL_CAPACITY, WALL_OBJECT_INITIAL_CAPACITY);
	}

	ClientSceneInstanceStore(int gameObjectCapacity, int wallObjectCapacity) {
		if (gameObjectCapacity <= 0 || wallObjectCapacity <= 0) {
			throw new IllegalArgumentException("Scene instance capacities must be positive");
		}
		gameObjectFrameMarks = new boolean[gameObjectCapacity];
		gameObjectDirections = new int[gameObjectCapacity];
		gameObjectIds = new int[gameObjectCapacity];
		gameObjectMaterialized = new boolean[gameObjectCapacity];
		gameObjectPendingAreaLoad = new boolean[gameObjectCapacity];
		gameObjectModels = new RSModel[gameObjectCapacity];
		gameObjectX = new int[gameObjectCapacity];
		gameObjectZ = new int[gameObjectCapacity];

		wallObjectFrameMarks = new boolean[wallObjectCapacity];
		wallObjectDirections = new int[wallObjectCapacity];
		wallObjectIds = new int[wallObjectCapacity];
		wallObjectMaterialized = new boolean[wallObjectCapacity];
		wallObjectPendingAreaLoad = new boolean[wallObjectCapacity];
		wallObjectModels = new RSModel[wallObjectCapacity];
		wallObjectX = new int[wallObjectCapacity];
		wallObjectZ = new int[wallObjectCapacity];
	}

	int getGameObjectCount() {
		return gameObjectCount;
	}

	void setGameObjectCount(int count) {
		ensureGameObjectCapacity(count);
		for (int index = Math.max(0, count); index < gameObjectCount; index++) {
			gameObjectMaterialized[index] = false;
			gameObjectPendingAreaLoad[index] = false;
			gameObjectModels[index] = null;
		}
		if (gameObjectCount != count) {
			gameObjectRevision++;
		}
		gameObjectCount = count;
	}

	long getGameObjectRevision() {
		return gameObjectRevision;
	}

	int getWallObjectCount() {
		return wallObjectCount;
	}

	void setWallObjectCount(int count) {
		ensureWallObjectCapacity(count);
		for (int index = Math.max(0, count); index < wallObjectCount; index++) {
			wallObjectMaterialized[index] = false;
			wallObjectPendingAreaLoad[index] = false;
			wallObjectModels[index] = null;
		}
		wallObjectCount = count;
	}

	void ensureGameObjectCapacity(int requiredCapacity) {
		if (requiredCapacity <= gameObjectModels.length) {
			return;
		}
		int capacity = growCapacity(gameObjectModels.length, requiredCapacity);
		gameObjectFrameMarks = Arrays.copyOf(gameObjectFrameMarks, capacity);
		gameObjectDirections = Arrays.copyOf(gameObjectDirections, capacity);
		gameObjectIds = Arrays.copyOf(gameObjectIds, capacity);
		gameObjectMaterialized = Arrays.copyOf(gameObjectMaterialized, capacity);
		gameObjectPendingAreaLoad = Arrays.copyOf(gameObjectPendingAreaLoad, capacity);
		gameObjectModels = Arrays.copyOf(gameObjectModels, capacity);
		gameObjectX = Arrays.copyOf(gameObjectX, capacity);
		gameObjectZ = Arrays.copyOf(gameObjectZ, capacity);
	}

	void ensureWallObjectCapacity(int requiredCapacity) {
		if (requiredCapacity <= wallObjectModels.length) {
			return;
		}
		int capacity = growCapacity(wallObjectModels.length, requiredCapacity);
		wallObjectFrameMarks = Arrays.copyOf(wallObjectFrameMarks, capacity);
		wallObjectDirections = Arrays.copyOf(wallObjectDirections, capacity);
		wallObjectIds = Arrays.copyOf(wallObjectIds, capacity);
		wallObjectMaterialized = Arrays.copyOf(wallObjectMaterialized, capacity);
		wallObjectPendingAreaLoad = Arrays.copyOf(wallObjectPendingAreaLoad, capacity);
		wallObjectModels = Arrays.copyOf(wallObjectModels, capacity);
		wallObjectX = Arrays.copyOf(wallObjectX, capacity);
		wallObjectZ = Arrays.copyOf(wallObjectZ, capacity);
	}

	static int growCapacity(int currentCapacity, int requiredCapacity) {
		int capacity = Math.max(1, currentCapacity);
		while (capacity < requiredCapacity) {
			int increment = Math.max(128, capacity / 2);
			capacity += increment;
			if (capacity < 0) {
				return requiredCapacity;
			}
		}
		return capacity;
	}

	int getGameObjectCapacity() {
		return gameObjectModels.length;
	}

	int getWallObjectCapacity() {
		return wallObjectModels.length;
	}

	int getGameObjectX(int index) {
		return gameObjectX[index];
	}

	void setGameObjectX(int index, int value) {
		ensureGameObjectCapacity(index + 1);
		if (gameObjectX[index] != value) {
			gameObjectRevision++;
		}
		gameObjectX[index] = value;
	}

	int getGameObjectZ(int index) {
		return gameObjectZ[index];
	}

	void setGameObjectZ(int index, int value) {
		ensureGameObjectCapacity(index + 1);
		if (gameObjectZ[index] != value) {
			gameObjectRevision++;
		}
		gameObjectZ[index] = value;
	}

	int getGameObjectId(int index) {
		return gameObjectIds[index];
	}

	void setGameObjectId(int index, int value) {
		ensureGameObjectCapacity(index + 1);
		if (gameObjectIds[index] != value) {
			gameObjectRevision++;
		}
		gameObjectIds[index] = value;
	}

	int getGameObjectDirection(int index) {
		return gameObjectDirections[index];
	}

	void setGameObjectDirection(int index, int value) {
		ensureGameObjectCapacity(index + 1);
		gameObjectDirections[index] = value;
	}

	RSModel getGameObjectModel(int index) {
		return gameObjectModels[index];
	}

	void setGameObjectModel(int index, RSModel model) {
		ensureGameObjectCapacity(index + 1);
		gameObjectModels[index] = model;
		if (model != null) {
			model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
			model.key = index;
		}
	}

	boolean isGameObjectMaterialized(int index) {
		return gameObjectMaterialized[index];
	}

	void setGameObjectMaterialized(int index, boolean materialized) {
		ensureGameObjectCapacity(index + 1);
		gameObjectMaterialized[index] = materialized;
	}

	boolean isGameObjectPendingAreaLoad(int index) {
		return gameObjectPendingAreaLoad[index];
	}

	void setGameObjectPendingAreaLoad(int index, boolean pending) {
		ensureGameObjectCapacity(index + 1);
		gameObjectPendingAreaLoad[index] = pending;
	}

	int getWallObjectX(int index) {
		return wallObjectX[index];
	}

	void setWallObjectX(int index, int value) {
		ensureWallObjectCapacity(index + 1);
		wallObjectX[index] = value;
	}

	int getWallObjectZ(int index) {
		return wallObjectZ[index];
	}

	void setWallObjectZ(int index, int value) {
		ensureWallObjectCapacity(index + 1);
		wallObjectZ[index] = value;
	}

	int getWallObjectId(int index) {
		return wallObjectIds[index];
	}

	void setWallObjectId(int index, int value) {
		ensureWallObjectCapacity(index + 1);
		wallObjectIds[index] = value;
	}

	int getWallObjectDirection(int index) {
		return wallObjectDirections[index];
	}

	void setWallObjectDirection(int index, int value) {
		ensureWallObjectCapacity(index + 1);
		wallObjectDirections[index] = value;
	}

	RSModel getWallObjectModel(int index) {
		return wallObjectModels[index];
	}

	void setWallObjectModel(int index, RSModel model) {
		ensureWallObjectCapacity(index + 1);
		wallObjectModels[index] = model;
		if (model != null) {
			model.setRenderer3DModelKind(Renderer3DModelKind.WALL_OBJECT);
			model.key = index + WALL_OBJECT_KEY_BASE;
		}
	}

	boolean isWallObjectMaterialized(int index) {
		return wallObjectMaterialized[index];
	}

	void setWallObjectMaterialized(int index, boolean materialized) {
		ensureWallObjectCapacity(index + 1);
		wallObjectMaterialized[index] = materialized;
	}

	boolean isWallObjectPendingAreaLoad(int index) {
		return wallObjectPendingAreaLoad[index];
	}

	void setWallObjectPendingAreaLoad(int index, boolean pending) {
		ensureWallObjectCapacity(index + 1);
		wallObjectPendingAreaLoad[index] = pending;
	}

	void clearPendingAreaLoadMarks() {
		Arrays.fill(gameObjectPendingAreaLoad, 0, gameObjectCount, false);
		Arrays.fill(wallObjectPendingAreaLoad, 0, wallObjectCount, false);
	}

	void clearFrameMarks() {
		Arrays.fill(gameObjectFrameMarks, 0, gameObjectCount, false);
		Arrays.fill(wallObjectFrameMarks, 0, wallObjectCount, false);
	}

	boolean isGameObjectFrameMarked(int index) {
		return gameObjectFrameMarks[index];
	}

	void markGameObjectForFrame(int index) {
		gameObjectFrameMarks[index] = true;
	}

	boolean isWallObjectFrameMarked(int index) {
		return wallObjectFrameMarks[index];
	}

	void markWallObjectForFrame(int index) {
		wallObjectFrameMarks[index] = true;
	}

	void retainPendingAreaLoadInstances() {
		int retainedGameObjects = 0;
		for (int readIndex = 0; readIndex < gameObjectCount; readIndex++) {
			if (!gameObjectPendingAreaLoad[readIndex]) {
				continue;
			}
			if (retainedGameObjects != readIndex) {
				gameObjectX[retainedGameObjects] = gameObjectX[readIndex];
				gameObjectZ[retainedGameObjects] = gameObjectZ[readIndex];
				gameObjectIds[retainedGameObjects] = gameObjectIds[readIndex];
				gameObjectDirections[retainedGameObjects] = gameObjectDirections[readIndex];
				gameObjectModels[retainedGameObjects] = gameObjectModels[readIndex];
				if (gameObjectModels[retainedGameObjects] != null) {
					gameObjectModels[retainedGameObjects].key = retainedGameObjects;
				}
				gameObjectMaterialized[retainedGameObjects] = false;
			}
			gameObjectPendingAreaLoad[retainedGameObjects] = false;
			retainedGameObjects++;
		}
		setGameObjectCount(retainedGameObjects);

		int retainedWalls = 0;
		for (int readIndex = 0; readIndex < wallObjectCount; readIndex++) {
			if (!wallObjectPendingAreaLoad[readIndex]) {
				continue;
			}
			if (retainedWalls != readIndex) {
				wallObjectX[retainedWalls] = wallObjectX[readIndex];
				wallObjectZ[retainedWalls] = wallObjectZ[readIndex];
				wallObjectIds[retainedWalls] = wallObjectIds[readIndex];
				wallObjectDirections[retainedWalls] = wallObjectDirections[readIndex];
				wallObjectModels[retainedWalls] = null;
				wallObjectMaterialized[retainedWalls] = false;
			}
			wallObjectPendingAreaLoad[retainedWalls] = false;
			retainedWalls++;
		}
		setWallObjectCount(retainedWalls);
	}
}
