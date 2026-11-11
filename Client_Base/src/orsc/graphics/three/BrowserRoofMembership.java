package orsc.graphics.three;

import java.util.Arrays;

/** Private render-thread-only membership retention, never roof visibility authority.
 * Current grid references are checked every frame, including in-place changes.
 * Dirty frames execute the unchanged literal remove/add sequence in mudclient.
 * No Scene model/camera/pick arrays escape through this helper.
 */
public final class BrowserRoofMembership {
	private static final int MAX_REFERENCES = 720;
	private final RSModel[] current = new RSModel[MAX_REFERENCES];
	private final RSModel[] retained = new RSModel[MAX_REFERENCES];
	private Scene scene;
	private Object epoch;
	private int revision;
	private int plane;
	private Renderer3DRoofVisibility visibility;
	private int count;
	private boolean valid;
	private boolean pending;
	private Scene currentScene;
	private int currentPlane;
	private Renderer3DRoofVisibility currentVisibility;
	private int currentCount;
	// Saturating diagnostic counters, read once/second by private Bootstrap.
	private final int[] work = new int[7];
	private void add(int index, int amount) {
		work[index] = work[index] > Integer.MAX_VALUE - amount
			? Integer.MAX_VALUE : work[index] + amount;
	}

	public boolean canReuse(Scene scene, RSModel[][] roofs, RSModel[][] walls,
		int plane, Renderer3DRoofVisibility visibility) {
		add(0, 1);
		pending = false;
		if (scene == null || visibility == null || !scene.canRetainBrowserRoofMembership()
			|| !collect(roofs, walls, plane)) {
			add(6, 1); invalidate(); return false;
		}
		currentScene = scene;
		currentPlane = plane;
		currentVisibility = visibility;
		pending = true;
		boolean same = valid && this.scene == scene && this.epoch == scene.modelMembershipEpoch()
			&& this.revision == scene.modelMembershipRevision() && this.plane == plane
			&& this.visibility == visibility && count == currentCount;
		for (int n = 0; n < currentCount; n++) {
			if (retained[n] != current[n]) same = false;
		}
		add(3, currentCount);
		if (!same) return false;
		pending = false;
		add(2, 1); add(4, currentCount);
		if (visibility.areRoofsVisible()) add(5, currentCount);
		return true;
	}

	/** Call only after the literal sequence completed successfully on this thread. */
	public void completedReconciliation() {
		if (!pending) return;
		pending = false;
		add(1, 1);
		if (!currentScene.canRetainBrowserRoofMembership()
			|| !currentScene.hasUniqueRoofMembership(current, currentCount)) {
			add(6, 1); invalidate(); return;
		}
		System.arraycopy(current, 0, retained, 0, currentCount);
		if (currentCount < count) Arrays.fill(retained, currentCount, count, null);
		scene = currentScene; epoch = scene.modelMembershipEpoch();
		revision = scene.modelMembershipRevision(); plane = currentPlane;
		visibility = currentVisibility; count = currentCount; valid = true;
	}

	private boolean collect(RSModel[][] roofs, RSModel[][] walls, int plane) {
		if (roofs == null || plane < 0 || plane >= roofs.length || roofs[plane] == null) return false;
		int length = roofs[plane].length;
		if (length > MAX_REFERENCES / (plane == 0 ? 5 : 1)) return false;
		if (plane == 0 && (roofs.length <= 2 || roofs[1] == null || roofs[2] == null
			|| roofs[1].length < length || roofs[2].length < length || walls == null || walls.length <= 2
			|| walls[1] == null || walls[2] == null || walls[1].length < length || walls[2].length < length)) return false;
		int n = 0;
		for (int index = 0; index < length; index++) {
			current[n++] = roofs[plane][index];
			if (plane == 0) {
				current[n++] = walls[1][index]; current[n++] = roofs[1][index];
				current[n++] = walls[2][index]; current[n++] = roofs[2][index];
			}
		}
		if (n < currentCount) Arrays.fill(current, n, currentCount, null);
		currentCount = n;
		return true;
	}

	private void invalidate() {
		Arrays.fill(current, 0, currentCount, null);
		Arrays.fill(retained, 0, count, null);
		currentCount = count = 0; valid = pending = false;
		scene = currentScene = null; epoch = null; visibility = currentVisibility = null;
	}

	public int[] work() { return work.clone(); }
}
