package com.openrsc.server.model.world.region;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Correlates a bounded runtime authored-object observation with the disposable
 * transactional reconstruction baseline for the same selected-source recipe.
 *
 * <p>Only a complete exact final-live authored set with constructor-matched
 * registration receipts is eligible for fingerprint equality. Recognized
 * transient or missing expected state remains explicitly unresolved pending
 * scheduler correlation; it is not mislabeled as collision corruption.
 * Identity conflicts and invalid registration provenance fail separately.</p>
 *
 * <p>This value consumes count/fingerprint-only detached parents and retains
 * only a new count/fingerprint summary. It has no runtime handle, shared tile
 * comparison, mutation, reconstruction, teardown, registry, cache, visibility,
 * arrival, or lifecycle authority.</p>
 */
public final class
	LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long recipeObservedAtTick;
	private final long runtimeObservedAtTick;
	private final long residencyMirrorVersion;
	private final List<SourceComparison> sources;
	private final int exactBaselineMatchSourceCount;
	private final int nonFinalAuthoredStateSourceCount;
	private final int identityConflictSourceCount;
	private final int registrationProvenanceInvalidSourceCount;
	private final int stableBaselineMismatchSourceCount;
	private final long expectedAuthoredObjectCount;
	private final long identitylessDynamicObjectCount;
	private final String runtimeObservationFingerprintSha256;
	private final String transactionalBaselineFingerprintSha256;
	private final String fingerprintSha256;

	private
		LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison(
			final LayeredPackedRegionRuntimeAuthoredObjectObservation
				observation,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					baseline) {
		LayeredPackedRegionRuntimeAuthoredObjectObservation runtime =
			Objects.requireNonNull(observation, "observation");
		LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
			transactional = Objects.requireNonNull(baseline, "baseline");
		if (runtime.getGeneration() != transactional.getGeneration()
			|| runtime.getRequirementsObservedAtTick()
				!= transactional.getRequirementsObservedAtTick()
			|| runtime.getRecipeObservedAtTick()
				!= transactional.getObservedAtTick()
			|| runtime.getResidencyMirrorVersion()
				!= transactional.getResidencyMirrorVersion()
			|| runtime.getSourceCount() != transactional.getSourceCount()
			|| runtime.getExpectedAuthoredObjectCount()
				!= transactional.getAuthoredObjectFootprintCount()
			|| runtime.isRuntimeHandleRetained()
			|| runtime.isLifecycleAuthority()
			|| !transactional.isDetachedSummaryOnly()
			|| !transactional.isAllSourcesVerified()
			|| !transactional
				.isCollisionRegistrationAttachedToDisposableObjects()
			|| transactional.isRuntimeHandleRetained()
			|| transactional.isRuntimeCollisionApplied()
			|| transactional.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Runtime authored-object comparison parents do not align");
		}
		this.generation = runtime.getGeneration();
		this.requirementsObservedAtTick =
			runtime.getRequirementsObservedAtTick();
		this.recipeObservedAtTick = runtime.getRecipeObservedAtTick();
		this.runtimeObservedAtTick = runtime.getRuntimeObservedAtTick();
		this.residencyMirrorVersion = runtime.getResidencyMirrorVersion();
		this.runtimeObservationFingerprintSha256 =
			runtime.getFingerprintSha256();
		this.transactionalBaselineFingerprintSha256 =
			transactional.getFingerprintSha256();

		List<SourceComparison> compared =
			new ArrayList<SourceComparison>(runtime.getSourceCount());
		int exact = 0;
		int nonFinal = 0;
		int identityConflict = 0;
		int invalidRegistration = 0;
		int stableMismatch = 0;
		long expectedObjects = 0L;
		long dynamicObjects = 0L;
		for (int index = 0; index < runtime.getSourceCount(); index++) {
			SourceComparison source = new SourceComparison(
				runtime.getSources().get(index),
				transactional.getSources().get(index), index);
			compared.add(source);
			expectedObjects = Math.addExact(
				expectedObjects,
				(long) source.getExpectedAuthoredObjectCount());
			dynamicObjects = Math.addExact(
				dynamicObjects,
				(long) source.getIdentitylessDynamicObjectCount());
			switch (source.getOutcome()) {
				case EXACT_BASELINE_MATCH:
					exact = Math.incrementExact(exact);
					break;
				case NON_FINAL_AUTHORED_STATE:
					nonFinal = Math.incrementExact(nonFinal);
					break;
				case IDENTITY_CONFLICT:
					identityConflict = Math.incrementExact(identityConflict);
					break;
				case REGISTRATION_PROVENANCE_INVALID:
					invalidRegistration = Math.incrementExact(
						invalidRegistration);
					break;
				case STABLE_BASELINE_MISMATCH:
					stableMismatch = Math.incrementExact(stableMismatch);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported runtime authored baseline outcome");
			}
		}
		if (expectedObjects != runtime.getExpectedAuthoredObjectCount()
			|| dynamicObjects
				!= runtime.getIdentitylessDynamicObjectCount()
			|| compared.size() != exact + nonFinal + identityConflict
				+ invalidRegistration + stableMismatch) {
			throw new IllegalArgumentException(
				"Runtime authored-object comparison arithmetic drifted");
		}
		this.sources = Collections.unmodifiableList(compared);
		this.exactBaselineMatchSourceCount = exact;
		this.nonFinalAuthoredStateSourceCount = nonFinal;
		this.identityConflictSourceCount = identityConflict;
		this.registrationProvenanceInvalidSourceCount =
			invalidRegistration;
		this.stableBaselineMismatchSourceCount = stableMismatch;
		this.expectedAuthoredObjectCount = expectedObjects;
		this.identitylessDynamicObjectCount = dynamicObjects;
		this.fingerprintSha256 = fingerprint(
			runtimeObservationFingerprintSha256,
			transactionalBaselineFingerprintSha256, compared);
	}

	public static
		LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison compare(
			final LayeredPackedRegionRuntimeAuthoredObjectObservation
				observation,
			final
				LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
					baseline) {
		return new
			LayeredPackedRegionRuntimeAuthoredObjectBaselineComparison(
				observation, baseline);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getRecipeObservedAtTick() { return recipeObservedAtTick; }
	public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}
	public List<SourceComparison> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getExactBaselineMatchSourceCount() {
		return exactBaselineMatchSourceCount;
	}
	public int getNonFinalAuthoredStateSourceCount() {
		return nonFinalAuthoredStateSourceCount;
	}
	public int getIdentityConflictSourceCount() {
		return identityConflictSourceCount;
	}
	public int getRegistrationProvenanceInvalidSourceCount() {
		return registrationProvenanceInvalidSourceCount;
	}
	public int getStableBaselineMismatchSourceCount() {
		return stableBaselineMismatchSourceCount;
	}
	public long getExpectedAuthoredObjectCount() {
		return expectedAuthoredObjectCount;
	}
	public long getIdentitylessDynamicObjectCount() {
		return identitylessDynamicObjectCount;
	}
	public String getRuntimeObservationFingerprintSha256() {
		return runtimeObservationFingerprintSha256;
	}
	public String getTransactionalBaselineFingerprintSha256() {
		return transactionalBaselineFingerprintSha256;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }
	public boolean areAllSourcesExactBaselineMatches() {
		return exactBaselineMatchSourceCount == getSourceCount();
	}
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isSharedCollisionTileComparisonPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeMutationAuthorized() { return false; }
	public boolean isRuntimeMutationPerformed() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isSchedulerCorrelationPerformed() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		EXACT_BASELINE_MATCH,
		NON_FINAL_AUTHORED_STATE,
		IDENTITY_CONFLICT,
		REGISTRATION_PROVENANCE_INVALID,
		STABLE_BASELINE_MISMATCH
	}

	/** Closed comparison for one exact source ordinal. */
	public static final class SourceComparison {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int expectedAuthoredObjectCount;
		private final int identitylessDynamicObjectCount;
		private final int exactFinalLiveInstanceCount;
		private final int authoredTransientInstanceCount;
		private final int missingExpectedIdentityCount;
		private final int duplicateRecognizedIdentityInstanceCount;
		private final int unrecognizedAuthoredInstanceCount;
		private final int collisionRegistrationPresentCount;
		private final int collisionRegistrationMissingCount;
		private final int collisionRegistrationConstructorMismatchCount;
		private final int collisionRegistrationContributionCount;
		private final int collisionRegistrationRegionReferenceCount;
		private final String runtimeRegistrationFingerprintSha256;
		private final String baselineRegistrationFingerprintSha256;
		private final boolean registrationFingerprintMatched;
		private final Outcome outcome;

		private SourceComparison(
			final LayeredPackedRegionRuntimeAuthoredObjectObservation
				.SourceObservation runtime,
			final LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				.SourceVerification baseline,
			final int expectedOrdinal) {
			LayeredPackedRegionRuntimeAuthoredObjectObservation.SourceObservation
				observed = Objects.requireNonNull(runtime, "runtimeSource");
			LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				.SourceVerification transactional =
					Objects.requireNonNull(baseline, "baselineSource");
			if (expectedOrdinal < 0
				|| observed.getSourceOrdinal() != expectedOrdinal
				|| transactional.getSourceOrdinal() != expectedOrdinal
				|| observed.getPackedRegionX()
					!= transactional.getPackedRegionX()
				|| observed.getPackedRegionY()
					!= transactional.getPackedRegionY()
				|| observed.getExpectedAuthoredObjectCount()
					!= transactional.getAuthoredObjectFootprintCount()
				|| !observed.isObjectBoundaryHeldDuringCapture()) {
				throw new IllegalArgumentException(
					"Runtime authored-object source baseline is misaligned");
			}
			this.sourceOrdinal = expectedOrdinal;
			this.packedRegionX = observed.getPackedRegionX();
			this.packedRegionY = observed.getPackedRegionY();
			this.expectedAuthoredObjectCount =
				observed.getExpectedAuthoredObjectCount();
			this.identitylessDynamicObjectCount =
				observed.getIdentitylessDynamicObjectCount();
			this.exactFinalLiveInstanceCount =
				observed.getExactFinalLiveInstanceCount();
			this.authoredTransientInstanceCount =
				observed.getAuthoredTransientInstanceCount();
			this.missingExpectedIdentityCount =
				observed.getMissingExpectedIdentityCount();
			this.duplicateRecognizedIdentityInstanceCount =
				observed.getDuplicateRecognizedIdentityInstanceCount();
			this.unrecognizedAuthoredInstanceCount =
				observed.getUnrecognizedAuthoredInstanceCount();
			this.collisionRegistrationPresentCount =
				observed.getCollisionRegistrationPresentCount();
			this.collisionRegistrationMissingCount =
				observed.getCollisionRegistrationMissingCount();
			this.collisionRegistrationConstructorMismatchCount =
				observed
					.getCollisionRegistrationConstructorMismatchCount();
			this.collisionRegistrationContributionCount =
				observed.getCollisionRegistrationContributionCount();
			this.collisionRegistrationRegionReferenceCount =
				observed.getCollisionRegistrationRegionReferenceCount();
			this.runtimeRegistrationFingerprintSha256 =
				observed.getCollisionRegistrationFingerprintSha256();
			this.baselineRegistrationFingerprintSha256 =
				transactional.getCollisionRegistrationFingerprintSha256();
			this.registrationFingerprintMatched =
				runtimeRegistrationFingerprintSha256.equals(
					baselineRegistrationFingerprintSha256);
			this.outcome = classify(observed, transactional);
		}

		private Outcome classify(
			final LayeredPackedRegionRuntimeAuthoredObjectObservation
				.SourceObservation runtime,
			final LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
				.SourceVerification baseline) {
			if (runtime.getDuplicateRecognizedIdentityInstanceCount() > 0
				|| runtime.getUnrecognizedAuthoredInstanceCount() > 0) {
				return Outcome.IDENTITY_CONFLICT;
			}
			if (runtime.getCollisionRegistrationMissingCount() > 0
				|| runtime
					.getCollisionRegistrationConstructorMismatchCount() > 0) {
				return Outcome.REGISTRATION_PROVENANCE_INVALID;
			}
			if (runtime.getAuthoredTransientInstanceCount() > 0
				|| runtime.getMissingExpectedIdentityCount() > 0) {
				return Outcome.NON_FINAL_AUTHORED_STATE;
			}
			if (!runtime.isFinalLiveAuthoredSetPresent()
				|| !runtime.areRecognizedRegistrationsConstructorMatched()
				|| runtime.getCollisionRegistrationPresentCount()
					!= baseline.getCollisionRegistrationCount()
				|| runtime.getCollisionRegistrationContributionCount()
					!= baseline
						.getCollisionRegistrationContributionCount()
				|| runtime.getCollisionRegistrationRegionReferenceCount()
					!= baseline
						.getCollisionRegistrationRegionReferenceCount()
				|| !registrationFingerprintMatched) {
				return Outcome.STABLE_BASELINE_MISMATCH;
			}
			return Outcome.EXACT_BASELINE_MATCH;
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getExpectedAuthoredObjectCount() {
			return expectedAuthoredObjectCount;
		}
		public int getIdentitylessDynamicObjectCount() {
			return identitylessDynamicObjectCount;
		}
		public int getExactFinalLiveInstanceCount() {
			return exactFinalLiveInstanceCount;
		}
		public int getAuthoredTransientInstanceCount() {
			return authoredTransientInstanceCount;
		}
		public int getMissingExpectedIdentityCount() {
			return missingExpectedIdentityCount;
		}
		public int getDuplicateRecognizedIdentityInstanceCount() {
			return duplicateRecognizedIdentityInstanceCount;
		}
		public int getUnrecognizedAuthoredInstanceCount() {
			return unrecognizedAuthoredInstanceCount;
		}
		public int getCollisionRegistrationPresentCount() {
			return collisionRegistrationPresentCount;
		}
		public int getCollisionRegistrationMissingCount() {
			return collisionRegistrationMissingCount;
		}
		public int getCollisionRegistrationConstructorMismatchCount() {
			return collisionRegistrationConstructorMismatchCount;
		}
		public int getCollisionRegistrationContributionCount() {
			return collisionRegistrationContributionCount;
		}
		public int getCollisionRegistrationRegionReferenceCount() {
			return collisionRegistrationRegionReferenceCount;
		}
		public String getRuntimeRegistrationFingerprintSha256() {
			return runtimeRegistrationFingerprintSha256;
		}
		public String getBaselineRegistrationFingerprintSha256() {
			return baselineRegistrationFingerprintSha256;
		}
		public boolean isRegistrationFingerprintMatched() {
			return registrationFingerprintMatched;
		}
		public Outcome getOutcome() { return outcome; }
	}

	private static String fingerprint(
		final String runtimeFingerprint,
		final String baselineFingerprint,
		final List<SourceComparison> sources) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateString(digest, runtimeFingerprint);
			updateString(digest, baselineFingerprint);
			updateInt(digest, sources.size());
			for (SourceComparison source : sources) {
				updateInt(digest, source.getSourceOrdinal());
				updateInt(digest, source.getPackedRegionX());
				updateInt(digest, source.getPackedRegionY());
				updateString(digest, source.getOutcome().name());
				updateString(
					digest,
					source.getRuntimeRegistrationFingerprintSha256());
				updateString(
					digest,
					source.getBaselineRegistrationFingerprintSha256());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for runtime authored-object comparison",
				unavailable);
		}
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update(Integer.toString(value).getBytes(
			StandardCharsets.UTF_8));
		digest.update((byte) 0);
	}

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		digest.update(Objects.requireNonNull(value, "fingerprint value")
			.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
	}
}
