package com.openrsc.server.event.rsc.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Disconnected composition of one exact source lifecycle and one consumer of
 * the NPC-owner preservation fact established for that completed lifecycle.
 *
 * <p>Both operations run inside a Slice 169 scope. The preservation evidence
 * is thread-confined, invalidated before this method returns, and cannot
 * authorize later work.</p>
 */
final class GameTickEventNpcOwnerPreservationLifecycle {
	private GameTickEventNpcOwnerPreservationLifecycle() {
	}

	static Result execute(
		final GameTickEventNpcOwnerPreservationScope scope,
		final SourceLifecycleOperation sourceLifecycle,
		final PreservedScopeConsumer consumer) {
		GameTickEventNpcOwnerPreservationScope checkedScope =
			Objects.requireNonNull(scope, "scope");
		SourceLifecycleOperation checkedLifecycle =
			Objects.requireNonNull(sourceLifecycle, "sourceLifecycle");
		PreservedScopeConsumer checkedConsumer =
			Objects.requireNonNull(consumer, "consumer");

		SourceLifecycleRequest request =
			SourceLifecycleRequest.from(checkedScope);
		try {
			SourceLifecycleCompletion completion =
				Objects.requireNonNull(
					checkedLifecycle.execute(request),
					"sourceLifecycle completion");
			request.requireActiveScope(checkedScope);

			Reason refusal = classify(request, completion);
			if (refusal != null) {
				return Result.refused(request, completion, refusal);
			}

			PreservationEvidence evidence =
				PreservationEvidence.open(request, completion);
			try {
				checkedConsumer.consume(evidence);
				request.requireActiveScope(checkedScope);
				return Result.consumed(request, completion);
			} finally {
				evidence.invalidate();
			}
		} finally {
			request.invalidateIfActive();
		}
	}

	private static Reason classify(
		final SourceLifecycleRequest request,
		final SourceLifecycleCompletion completion) {
		if (completion.request != request) {
			return Reason.COMPLETION_SCOPE_MISMATCH;
		}
		if (completion.refusalReason != null) {
			return Reason.SOURCE_LIFECYCLE_REFUSED;
		}
		if (completion.absentSourceCount != request.selectedSources.size()) {
			return Reason.SOURCE_ABSENCE_INCOMPLETE;
		}
		if (completion.reconstructedSourceCount
				!= request.selectedSources.size()) {
			return Reason.SOURCE_RECONSTRUCTION_INCOMPLETE;
		}
		if (!completion.allSourcesRestoredBeforeReturn) {
			return Reason.SOURCES_NOT_RESTORED_BEFORE_RETURN;
		}
		if (!completion.firstVisibilityWithheld) {
			return Reason.FIRST_VISIBILITY_NOT_WITHHELD;
		}
		return null;
	}

	enum Reason {
		COMPLETION_SCOPE_MISMATCH,
		SOURCE_LIFECYCLE_REFUSED,
		SOURCE_ABSENCE_INCOMPLETE,
		SOURCE_RECONSTRUCTION_INCOMPLETE,
		SOURCES_NOT_RESTORED_BEFORE_RETURN,
		FIRST_VISIBILITY_NOT_WITHHELD,
		PRESERVED_CONSUMER_COMPLETED
	}

	enum LifecycleRefusalReason {
		SOURCE_SET_UNAVAILABLE,
		ABSENCE_OPERATION_REFUSED,
		RECONSTRUCTION_OPERATION_REFUSED,
		RESTORATION_VERIFICATION_REFUSED
	}

	@FunctionalInterface
	interface SourceLifecycleOperation {
		SourceLifecycleCompletion execute(SourceLifecycleRequest request);
	}

	@FunctionalInterface
	interface PreservedScopeConsumer {
		void consume(PreservationEvidence evidence);
	}

	/**
	 * Exact, active-scope input for one source lifecycle. Completion values can
	 * only be created by this request and remain bound to its object identity.
	 */
	static final class SourceLifecycleRequest {
		private final long generation;
		private final long requirementsObservedAtTick;
		private final String schedulerInstanceIdentity;
		private final int requiredEventLinkCount;
		private final int requiredOwnerCount;
		private final List<PackedSource> selectedSources;
		private final Thread boundaryThread;
		private boolean active = true;

		private SourceLifecycleRequest(
			final GameTickEventNpcOwnerPreservationScope scope) {
			this.generation = scope.getGeneration();
			this.requirementsObservedAtTick =
				scope.getRequirementsObservedAtTick();
			this.schedulerInstanceIdentity =
				scope.getSchedulerInstanceIdentity();
			this.requiredEventLinkCount =
				scope.getRequiredEventLinkCount();
			this.requiredOwnerCount = scope.getRequiredOwnerCount();
			List<PackedSource> sources = new ArrayList<PackedSource>();
			for (GameTickEventNpcOwnerPreservationScope.PackedSource source
				: scope.getSelectedSources()) {
				sources.add(new PackedSource(
					source.getPackedRegionX(),
					source.getPackedRegionY()));
			}
			this.selectedSources = Collections.unmodifiableList(sources);
			this.boundaryThread = Thread.currentThread();
		}

		private static SourceLifecycleRequest from(
			final GameTickEventNpcOwnerPreservationScope scope) {
			return new SourceLifecycleRequest(scope);
		}

		long getGeneration() {
			requireActive();
			return generation;
		}

		long getRequirementsObservedAtTick() {
			requireActive();
			return requirementsObservedAtTick;
		}

		String getSchedulerInstanceIdentity() {
			requireActive();
			return schedulerInstanceIdentity;
		}

		int getRequiredEventLinkCount() {
			requireActive();
			return requiredEventLinkCount;
		}

		int getRequiredOwnerCount() {
			requireActive();
			return requiredOwnerCount;
		}

		List<PackedSource> getSelectedSources() {
			requireActive();
			return selectedSources;
		}

		SourceLifecycleCompletion completed(
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean allSourcesRestoredBeforeReturn,
			final boolean firstVisibilityWithheld) {
			requireActive();
			return SourceLifecycleCompletion.completed(
				this, absentSourceCount, reconstructedSourceCount,
				allSourcesRestoredBeforeReturn, firstVisibilityWithheld);
		}

		SourceLifecycleCompletion refused(
			final LifecycleRefusalReason reason) {
			requireActive();
			return SourceLifecycleCompletion.refused(
				this, Objects.requireNonNull(reason, "reason"));
		}

		private void requireActiveScope(
			final GameTickEventNpcOwnerPreservationScope scope) {
			requireActive();
			if (generation != scope.getGeneration()
				|| requirementsObservedAtTick
					!= scope.getRequirementsObservedAtTick()
				|| !schedulerInstanceIdentity.equals(
					scope.getSchedulerInstanceIdentity())
				|| requiredEventLinkCount
					!= scope.getRequiredEventLinkCount()
				|| requiredOwnerCount != scope.getRequiredOwnerCount()
				|| !sameSources(selectedSources, scope.getSelectedSources())) {
				throw new IllegalStateException(
					"NPC preservation scope changed during source lifecycle");
			}
		}

		private static boolean sameSources(
			final List<PackedSource> expected,
			final List<GameTickEventNpcOwnerPreservationScope.PackedSource>
				observed) {
			if (expected.size() != observed.size()) {
				return false;
			}
			for (int index = 0; index < expected.size(); index++) {
				PackedSource left = expected.get(index);
				GameTickEventNpcOwnerPreservationScope.PackedSource right =
					observed.get(index);
				if (left.getPackedRegionX() != right.getPackedRegionX()
					|| left.getPackedRegionY()
						!= right.getPackedRegionY()) {
					return false;
				}
			}
			return true;
		}

		private void invalidateIfActive() {
			requireBoundaryThread();
			if (active) {
				active = false;
			}
		}

		private void requireActive() {
			requireBoundaryThread();
			if (!active) {
				throw new IllegalStateException(
					"NPC preservation source request is no longer active");
			}
		}

		private void requireBoundaryThread() {
			if (Thread.currentThread() != boundaryThread) {
				throw new IllegalStateException(
					"NPC preservation source request is thread-confined");
			}
		}
	}

	/** Typed source-lifecycle outcome bound to exactly one request instance. */
	static final class SourceLifecycleCompletion {
		private final SourceLifecycleRequest request;
		private final int absentSourceCount;
		private final int reconstructedSourceCount;
		private final boolean allSourcesRestoredBeforeReturn;
		private final boolean firstVisibilityWithheld;
		private final LifecycleRefusalReason refusalReason;

		private SourceLifecycleCompletion(
			final SourceLifecycleRequest request,
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean allSourcesRestoredBeforeReturn,
			final boolean firstVisibilityWithheld,
			final LifecycleRefusalReason refusalReason) {
			this.request = Objects.requireNonNull(request, "request");
			this.absentSourceCount = absentSourceCount;
			this.reconstructedSourceCount = reconstructedSourceCount;
			this.allSourcesRestoredBeforeReturn =
				allSourcesRestoredBeforeReturn;
			this.firstVisibilityWithheld = firstVisibilityWithheld;
			this.refusalReason = refusalReason;
			if (absentSourceCount < 0 || reconstructedSourceCount < 0
				|| (refusalReason != null
					&& (absentSourceCount != 0
						|| reconstructedSourceCount != 0
						|| allSourcesRestoredBeforeReturn
						|| firstVisibilityWithheld))) {
				throw new IllegalArgumentException(
					"Source lifecycle completion is inconsistent");
			}
		}

		private static SourceLifecycleCompletion completed(
			final SourceLifecycleRequest request,
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean allSourcesRestoredBeforeReturn,
			final boolean firstVisibilityWithheld) {
			return new SourceLifecycleCompletion(
				request, absentSourceCount, reconstructedSourceCount,
				allSourcesRestoredBeforeReturn, firstVisibilityWithheld,
				null);
		}

		private static SourceLifecycleCompletion refused(
			final SourceLifecycleRequest request,
			final LifecycleRefusalReason reason) {
			return new SourceLifecycleCompletion(
				request, 0, 0, false, false, reason);
		}
	}

	/**
	 * Ephemeral fact available only to the one consumer following a complete
	 * source lifecycle and while the outer owner scope remains held.
	 */
	static final class PreservationEvidence {
		private final long generation;
		private final int selectedSourceCount;
		private final int requiredEventLinkCount;
		private final int requiredOwnerCount;
		private final Thread boundaryThread;
		private boolean active = true;

		private PreservationEvidence(
			final SourceLifecycleRequest request,
			final SourceLifecycleCompletion completion) {
			this.generation = request.generation;
			this.selectedSourceCount = request.selectedSources.size();
			this.requiredEventLinkCount = request.requiredEventLinkCount;
			this.requiredOwnerCount = request.requiredOwnerCount;
			this.boundaryThread = Thread.currentThread();
			if (completion.request != request
				|| completion.absentSourceCount != selectedSourceCount
				|| completion.reconstructedSourceCount
					!= selectedSourceCount
				|| !completion.allSourcesRestoredBeforeReturn
				|| !completion.firstVisibilityWithheld
				|| completion.refusalReason != null) {
				throw new IllegalArgumentException(
					"NPC owner preservation evidence is incomplete");
			}
		}

		private static PreservationEvidence open(
			final SourceLifecycleRequest request,
			final SourceLifecycleCompletion completion) {
			return new PreservationEvidence(request, completion);
		}

		long getGeneration() {
			requireActive();
			return generation;
		}

		int getSelectedSourceCount() {
			requireActive();
			return selectedSourceCount;
		}

		int getRequiredEventLinkCount() {
			requireActive();
			return requiredEventLinkCount;
		}

		int getRequiredOwnerCount() {
			requireActive();
			return requiredOwnerCount;
		}

		boolean isPreservationEstablishedForActiveScope() {
			requireActive();
			return true;
		}

		boolean isRuntimeHandleRetained() {
			requireActive();
			return false;
		}

		boolean isReusablePreservationFact() {
			requireActive();
			return false;
		}

		private void invalidate() {
			requireBoundaryThread();
			active = false;
		}

		private void requireActive() {
			requireBoundaryThread();
			if (!active) {
				throw new IllegalStateException(
					"NPC owner preservation evidence is no longer active");
			}
		}

		private void requireBoundaryThread() {
			if (Thread.currentThread() != boundaryThread) {
				throw new IllegalStateException(
					"NPC owner preservation evidence is thread-confined");
			}
		}
	}

	/** Detached historical result; never authority for a later operation. */
	static final class Result {
		private final long generation;
		private final int selectedSourceCount;
		private final int requiredEventLinkCount;
		private final int requiredOwnerCount;
		private final int absentSourceCount;
		private final int reconstructedSourceCount;
		private final boolean allSourcesRestoredBeforeReturn;
		private final boolean firstVisibilityWithheld;
		private final LifecycleRefusalReason lifecycleRefusalReason;
		private final Reason reason;

		private Result(
			final SourceLifecycleRequest request,
			final SourceLifecycleCompletion completion,
			final Reason reason) {
			this.generation = request.generation;
			this.selectedSourceCount = request.selectedSources.size();
			this.requiredEventLinkCount = request.requiredEventLinkCount;
			this.requiredOwnerCount = request.requiredOwnerCount;
			this.absentSourceCount = completion.absentSourceCount;
			this.reconstructedSourceCount =
				completion.reconstructedSourceCount;
			this.allSourcesRestoredBeforeReturn =
				completion.allSourcesRestoredBeforeReturn;
			this.firstVisibilityWithheld =
				completion.firstVisibilityWithheld;
			this.lifecycleRefusalReason = completion.refusalReason;
			this.reason = Objects.requireNonNull(reason, "reason");
		}

		private static Result refused(
			final SourceLifecycleRequest request,
			final SourceLifecycleCompletion completion,
			final Reason reason) {
			if (reason == Reason.PRESERVED_CONSUMER_COMPLETED) {
				throw new IllegalArgumentException(
					"Refused preservation result cannot be complete");
			}
			return new Result(request, completion, reason);
		}

		private static Result consumed(
			final SourceLifecycleRequest request,
			final SourceLifecycleCompletion completion) {
			return new Result(
				request, completion, Reason.PRESERVED_CONSUMER_COMPLETED);
		}

		long getGeneration() { return generation; }
		int getSelectedSourceCount() { return selectedSourceCount; }
		int getRequiredEventLinkCount() { return requiredEventLinkCount; }
		int getRequiredOwnerCount() { return requiredOwnerCount; }
		int getAbsentSourceCount() { return absentSourceCount; }
		int getReconstructedSourceCount() {
			return reconstructedSourceCount;
		}
		boolean areAllSourcesRestoredBeforeReturn() {
			return allSourcesRestoredBeforeReturn;
		}
		boolean isFirstVisibilityWithheld() {
			return firstVisibilityWithheld;
		}
		LifecycleRefusalReason getLifecycleRefusalReason() {
			return lifecycleRefusalReason;
		}
		Reason getReason() { return reason; }
		boolean isPreservedConsumerInvoked() {
			return reason == Reason.PRESERVED_CONSUMER_COMPLETED;
		}
		boolean isPreservationEstablishedForConsumedWork() {
			return isPreservedConsumerInvoked();
		}
		boolean isReusablePreservationFact() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
	}

	/** Detached packed-source identity, copied in exact scope order. */
	static final class PackedSource {
		private final int packedRegionX;
		private final int packedRegionY;

		private PackedSource(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		int getPackedRegionX() { return packedRegionX; }
		int getPackedRegionY() { return packedRegionY; }
	}
}
