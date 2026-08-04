package com.openrsc.server.content.cleric;

import java.util.ArrayList;
import java.util.List;

/**
 * One cast-level commit boundary shared by Cleric support applications.
 *
 * <p>Application preparation must be side-effect free. Equal-strength refreshes
 * are represented as useful prepared applications; weaker replacements and
 * other no-ops are not. The resource boundary must either spend one complete
 * cost vector and invoke the supplied non-throwing commit exactly once, or do
 * neither. This keeps partial recipient success while prohibiting partial
 * resource vectors and wholly ineffective spending.</p>
 */
public final class ClericCastTransaction {
	private ClericCastTransaction() {
	}

	public static Result execute(final Iterable<? extends PreparedApplication> prepared,
			final ResourceCommitBoundary resourceBoundary) {
		if (prepared == null || resourceBoundary == null) {
			throw new IllegalArgumentException(
				"Cleric cast transaction requires applications and a resource boundary");
		}
		final List<PreparedApplication> useful = new ArrayList<PreparedApplication>();
		for (PreparedApplication application : prepared) {
			if (application == null) {
				throw new IllegalArgumentException("Prepared Cleric application cannot be null");
			}
			if (application.isUseful()) {
				useful.add(application);
			}
		}
		if (useful.isEmpty()) {
			return Result.noUsefulApplication();
		}

		final int[] commitCount = {0};
		final boolean committed = resourceBoundary.commit(new Runnable() {
			@Override
			public void run() {
				if (commitCount[0] != 0) {
					throw new IllegalStateException("Cleric application commit invoked more than once");
				}
				commitCount[0]++;
				for (PreparedApplication application : useful) {
					application.commit();
				}
			}
		});
		if (committed != (commitCount[0] == 1)) {
			throw new IllegalStateException(
				"Cleric resource boundary violated its all-or-nothing commit contract");
		}
		return committed
			? Result.success(useful.size())
			: Result.insufficientResources();
	}

	/** A fully preflighted application whose commit must not reject or throw. */
	public interface PreparedApplication {
		boolean isUseful();

		void commit();
	}

	/**
	 * Owns serialized resource preflight and removal. A false result must not
	 * invoke {@code applicationCommit}; a true result must invoke it once while
	 * still inside the same serialized mutation boundary.
	 */
	public interface ResourceCommitBoundary {
		boolean commit(Runnable applicationCommit);
	}

	public enum Outcome {
		SUCCESS,
		NO_USEFUL_APPLICATION,
		INSUFFICIENT_RESOURCES
	}

	public static final class Result {
		private static final Result NO_USEFUL =
			new Result(Outcome.NO_USEFUL_APPLICATION, 0);
		private static final Result INSUFFICIENT =
			new Result(Outcome.INSUFFICIENT_RESOURCES, 0);

		private final Outcome outcome;
		private final int appliedRecipientCount;

		private Result(Outcome outcome, int appliedRecipientCount) {
			this.outcome = outcome;
			this.appliedRecipientCount = appliedRecipientCount;
		}

		private static Result success(int appliedRecipientCount) {
			return new Result(Outcome.SUCCESS, appliedRecipientCount);
		}

		private static Result noUsefulApplication() {
			return NO_USEFUL;
		}

		private static Result insufficientResources() {
			return INSUFFICIENT;
		}

		public Outcome getOutcome() {
			return outcome;
		}

		public int getAppliedRecipientCount() {
			return appliedRecipientCount;
		}
	}
}
