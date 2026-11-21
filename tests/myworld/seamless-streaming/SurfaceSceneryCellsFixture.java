package orsc;

import java.util.Collections;
import java.util.List;
import orsc.graphics.three.World;

public final class SurfaceSceneryCellsFixture {
	public static void main(String[] args) {
		SurfaceSceneryCells state = new SurfaceSceneryCells();
		state.begin(8, 2, 4, 5, 12, 1, 0);
		SurfaceSceneryCells.Cell cell = state.cell(8, 2, 4, 6, 12);
		require(cell != null, "expected receiving cell");
		require(state.cell(8, 2, 3, 6, 12) == null, "wrong travel lifetime");
		require(state.cell(7, 2, 4, 6, 12) == null, "wrong terrain prediction");
		require(state.cell(8, 3, 4, 6, 12) == null, "wrong installed context");
		require(state.cell(8, 2, 4, 5, 12) == null, "overlap is outside the incoming edge");
		List<World.SurfaceSceneryPlacement> placement = Collections.singletonList(
			new World.SurfaceSceneryPlacement(50, 305, 620, 0, 0));
		require(cell.startsRevision(1, 0), "first revision invalidates an older cell");
		require(cell.accept(1, 0, 2, placement) == null, "partial cell became complete");
		require(cell.accept(1, 0, 2, placement) == null, "duplicate first page was applied");
		require(cell.accept(2, 0, 1, Collections.emptyList()).isEmpty(), "replacement empty cell was lost");
		require(cell.accept(1, 1, 2, placement) == null, "old trailing page restored cleared contents");
		require(cell.accept(3, 0, 0, Collections.emptyList()) == null, "invalidated cell became ready");
		require(cell.accept(4, 1, 2, placement) == null, "trailing page created a revision");
		require(cell.accept(4, 0, 2, placement) == null, "first page alone became complete");
		require(cell.accept(4, 1, 2, placement).size() == 2, "ordered pages did not commit once");
		state.begin(9, 2, 4, 4, 11, 0, -1);
		require(state.cell(8, 2, 4, 6, 12) == null, "reversal retained an old prediction");
		require(state.cell(9, 2, 4, 3, 10) != null, "south edge start missing");
		require(state.cell(9, 2, 4, 5, 10) != null, "south edge end missing");
		require(state.cell(9, 2, 4, 4, 9) == null, "play-only subscription includes a draw cell");
		state.begin(10, 2, 4, 4, 11, 0, -1, true);
		require(state.cell(10, 2, 4, 2, 9).band == SurfaceSceneryCells.Band.ENTERING_DRAW, "south draw edge missing");
		require(state.cell(10, 2, 4, 4, 10).band == SurfaceSceneryCells.Band.ENTERING_PLAY, "south play edge missing");
		require(state.cell(10, 2, 4, 3, 13).band == SurfaceSceneryCells.Band.LEAVING_PLAY, "north play edge missing");
		require(state.cell(10, 2, 4, 2, 13) == null, "unchanged presentation crossed ownership");
		require(state.cell(10, 2, 4, 4, 11) == null, "overlapping gameplay was subscribed");
		state.reset();
		require(state.cell(10, 2, 4, 2, 9) == null, "scope reset retained a draw cell");
		System.out.println("PASS authoritative surface scenery revisions and scope");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
