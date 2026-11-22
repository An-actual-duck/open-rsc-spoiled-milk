package orsc;

import orsc.graphics.three.RSModel;

/** Scene storage owns invalidation of the render-only placement publication. */
public final class ScenePlacementRevisionFixture {
	public static void main(String[] args) {
		ClientSceneInstanceStore store = new ClientSceneInstanceStore(2, 2);
		changed(store, () -> store.setGameObjectCount(2));
		changed(store, () -> store.setGameObjectX(1, 14));
		changed(store, () -> store.setGameObjectZ(1, 29));
		changed(store, () -> store.setGameObjectId(1, 10));
		changed(store, () -> store.setGameObjectDirection(1, 2));
		changed(store, () -> store.setGameObjectModel(1, new RSModel(3, 1)));
		changed(store, () -> store.setGameObjectMaterialized(1, true));
		changed(store, () -> store.moveGameObject(1, 0, 14, 29));
		changed(store, () -> store.setGameObjectCount(1));
		long still = store.getResidentPlacementRevision();
		store.getGameObjectModel(0).addRotation(1, 0, 0);
		store.setGameObjectDirection(0, 2);
		if (store.getResidentPlacementRevision() != still) throw new AssertionError("A pose invalidated the placement list");
		changed(store, () -> store.setWallObjectCount(2));
		changed(store, () -> store.setWallObjectX(1, 22));
		changed(store, () -> store.setWallObjectZ(1, 60));
		changed(store, () -> store.setWallObjectId(1, 12));
		changed(store, () -> store.setWallObjectDirection(1, 3));
		changed(store, () -> store.setWallObjectModel(1, new RSModel(3, 1)));
		changed(store, () -> store.setWallObjectMaterialized(1, true));
		changed(store, () -> store.moveWallObject(1, 0, 22, 60));
		changed(store, () -> store.setWallObjectModel(0, null));
		changed(store, () -> store.setWallObjectCount(1));
		changed(store, () -> store.publishWorldOrigin(48, -48));
		if (store.getGameObjectX(0) != -34 || store.getGameObjectZ(0) != 77
			|| store.getWallObjectX(0) != -26 || store.getWallObjectZ(0) != 108)
			throw new AssertionError("Published origin changed a world tile");
		store.setWallObjectX(0, -12);
		store.setWallObjectZ(0, 4);
		changed(store, () -> store.publishWorldOrigin(0, 0));
		if (store.getGameObjectX(0) != 14 || store.getGameObjectZ(0) != 29
			|| store.getWallObjectX(0) != 36 || store.getWallObjectZ(0) != -44)
			throw new AssertionError("Return origin changed a retained or edited tile");
		System.out.println("PASS game-object and wall placement publication follows actual edits and compaction");
	}

	private static void changed(ClientSceneInstanceStore store, Runnable edit) {
		long before = store.getResidentPlacementRevision();
		edit.run();
		if (store.getResidentPlacementRevision() == before) throw new AssertionError("A placement edit was missed");
	}
}
