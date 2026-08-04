package com.openrsc.server.content.cleric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, bounded step selection for Unify; it never mutates or teleports a mob. */
public final class ClericUnifyStepPlanner {
	public static final int SETTLED_RADIUS = 4;
	public static final int SETTLED_SAFE_RADIUS = 2;
	public static final int MAXIMUM_STEPS = 2;

	private ClericUnifyStepPlanner() {
	}

	public static List<Step> plan(final int startX, final int startY,
			final int casterX, final int casterY, final Traversability traversability) {
		if (traversability == null) {
			throw new IllegalArgumentException("Unify traversability is required");
		}
		final List<Step> steps = new ArrayList<Step>(MAXIMUM_STEPS);
		int currentX = startX;
		int currentY = startY;
		if (chebyshevDistance(currentX, currentY, casterX, casterY) > SETTLED_RADIUS) {
			return Collections.emptyList();
		}

		while (steps.size() < MAXIMUM_STEPS
			&& chebyshevDistance(currentX, currentY, casterX, casterY) > SETTLED_SAFE_RADIUS) {
			final int stepX = Integer.compare(casterX, currentX);
			final int stepY = Integer.compare(casterY, currentY);
			final int[][] candidates = {
				{currentX + stepX, currentY + stepY},
				{currentX + stepX, currentY},
				{currentX, currentY + stepY}
			};
			final long currentManhattan = manhattanDistance(currentX, currentY, casterX, casterY);
			Step selected = null;
			for (int[] candidate : candidates) {
				if ((candidate[0] == currentX && candidate[1] == currentY)
					|| manhattanDistance(candidate[0], candidate[1], casterX, casterY)
						>= currentManhattan
					|| contains(steps, candidate[0], candidate[1])) {
					continue;
				}
				if (traversability.canStep(currentX, currentY, candidate[0], candidate[1])) {
					selected = new Step(candidate[0], candidate[1]);
					break;
				}
			}
			if (selected == null) {
				break;
			}
			steps.add(selected);
			currentX = selected.getX();
			currentY = selected.getY();
		}
		return Collections.unmodifiableList(steps);
	}

	private static boolean contains(List<Step> steps, int x, int y) {
		for (Step step : steps) {
			if (step.x == x && step.y == y) {
				return true;
			}
		}
		return false;
	}

	private static long chebyshevDistance(int firstX, int firstY, int secondX, int secondY) {
		return Math.max(Math.abs((long) firstX - secondX), Math.abs((long) firstY - secondY));
	}

	private static long manhattanDistance(int firstX, int firstY, int secondX, int secondY) {
		return Math.abs((long) firstX - secondX) + Math.abs((long) firstY - secondY);
	}

	public interface Traversability {
		boolean canStep(int startX, int startY, int destinationX, int destinationY);
	}

	public static final class Step {
		private final int x;
		private final int y;

		private Step(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}
	}
}
