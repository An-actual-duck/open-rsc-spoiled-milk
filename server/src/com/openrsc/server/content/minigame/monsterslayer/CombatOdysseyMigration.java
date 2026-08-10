package com.openrsc.server.content.minigame.monsterslayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Contact;

/** Pure, non-writing proposal for preserving bounded Combat Odyssey accomplishments. */
public final class CombatOdysseyMigration {
	public static final long LEGACY_TOTAL_KILLS = 40_906L;

	private CombatOdysseyMigration() {
	}

	public static Result propose(LegacySnapshot legacy, LegacyData legacyData,
			MonsterSlayerData monsterSlayerData, MonsterSlayerState.Snapshot current) {
		if (legacy == null || legacyData == null || monsterSlayerData == null || current == null) {
			return Result.failure(Failure.INVALID_INPUT, "Migration inputs are incomplete");
		}
		try {
			MonsterSlayerState.validate(current, monsterSlayerData);
			if (current.getMigrationVersion() == MonsterSlayerState.MIGRATION_VERSION) {
				return Result.success(Classification.ALREADY_MIGRATED, 0L, current);
			}
			validateEmptyDestination(current, monsterSlayerData);
			ParsedLegacy parsed = parseLegacy(legacy, legacyData);
			Classification classification = classify(parsed);
			long creditedKills = parsed.active == null ? 0L : creditedKills(parsed, legacyData);
			MonsterSlayerRank rank = classification == Classification.COMPLETED_CLAIMED
				|| classification == Classification.COMPLETED_UNCLAIMED
				? MonsterSlayerRank.LEGEND
				: classification == Classification.PARTIAL
					? MonsterSlayerRank.FLEDGLING : MonsterSlayerRank.UNSTAMPED;
			int introStage = rank == MonsterSlayerRank.UNSTAMPED ? 0 : 2;
			Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
			for (Contact contact : monsterSlayerData.getContactsInChallengeOrder()) {
				cursors.put(contact.getKey(), rank == MonsterSlayerRank.LEGEND
					? contact.getMandatoryTasks().size() : 0);
			}
			MonsterSlayerState.LegacyStatus status = legacyStatus(classification);
			MonsterSlayerState.Snapshot proposal = MonsterSlayerState.create(
				introStage, rank, MonsterSlayerBalances.zero(), cursors, null, 0, 0L, 0,
				MonsterSlayerState.MIGRATION_VERSION, status, parsed.prestige, monsterSlayerData);
			return Result.success(classification, creditedKills, proposal);
		} catch (MigrationValidationException ex) {
			return Result.failure(ex.failure, ex.getMessage());
		} catch (MonsterSlayerState.ValidationException ex) {
			return Result.failure(Failure.NEW_STATE_INVALID, ex.getMessage());
		} catch (IllegalArgumentException ex) {
			return Result.failure(Failure.ARITHMETIC_OR_STATE_BOUNDS,
				"Migration proposal exceeded a typed state bound");
		}
	}

	private static void validateEmptyDestination(MonsterSlayerState.Snapshot current,
			MonsterSlayerData data) {
		if (current.getIntroStage() != 0 || current.getRank() != MonsterSlayerRank.UNSTAMPED
			|| current.getActiveTaskKey() != null || current.getActiveKills() != 0
			|| current.getTasksCompleted() != 0L
			|| current.getLegacyStatus() != MonsterSlayerState.LegacyStatus.NONE
			|| current.getLegacyPrestige() != 0) {
			throw invalid(Failure.NEW_STATE_CONFLICT,
				"Unmigrated Monster Slayer destination already contains progression");
		}
		for (MonsterSlayerChallenge challenge : MonsterSlayerChallenge.values()) {
			if (current.getBalances().get(challenge) != 0L) {
				throw invalid(Failure.NEW_STATE_CONFLICT,
					"Unmigrated Monster Slayer destination already contains balances");
			}
		}
		for (Contact contact : data.getContactsInChallengeOrder()) {
			if (current.getMandatoryCursors().get(contact.getKey()) != 0) {
				throw invalid(Failure.NEW_STATE_CONFLICT,
					"Unmigrated Monster Slayer destination already contains task progress");
			}
		}
	}

	private static ParsedLegacy parseLegacy(LegacySnapshot snapshot, LegacyData data) {
		int prestige = typedInteger(snapshot.prestige, "co_prestige", 0);
		if (prestige < 0) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, "co_prestige is negative");
		}
		Long progress = typedLong(snapshot.tierProgress, "co_tier_progress");
		if (progress != null && progress < 0L) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, "co_tier_progress is negative");
		}
		String state = typedString(snapshot.odysseyState, "combat_odyssey");
		if (state == null || "0".equals(state)) {
			if (progress != null && progress != 0L) {
				throw invalid(Failure.LEGACY_STATE_CONTRADICTION,
					"co_tier_progress exists without an active combat_odyssey record");
			}
			return new ParsedLegacy(prestige, Intro.NONE, null);
		}
		if ("1".equals(state) || "2".equals(state)) {
			if (progress != null && progress != 0L) {
				throw invalid(Failure.LEGACY_STATE_CONTRADICTION,
					"co_tier_progress contradicts the combat_odyssey introduction stage");
			}
			return new ParsedLegacy(prestige, "1".equals(state) ? Intro.ACCEPTED : Intro.MET_BIGGUM, null);
		}

		String[] parts = state.split(":", -1);
		if (parts.length != 3) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, "combat_odyssey has an invalid active record");
		}
		int tierId = parseInt(parts[0], "combat_odyssey tier");
		int taskId = parseInt(parts[1], "combat_odyssey task");
		int kills = parseInt(parts[2], "combat_odyssey kills");
		LegacyTier tier = data.getTier(tierId);
		if (tier == null) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, "combat_odyssey has an unknown tier");
		}
		if (progress == null) {
			throw invalid(Failure.LEGACY_STATE_CONTRADICTION,
				"Active combat_odyssey state is missing co_tier_progress");
		}
		long validMask = (1L << tier.tasks.size()) - 1L;
		if ((progress & ~validMask) != 0L) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE,
				"co_tier_progress has impossible task bits");
		}
		if (taskId == -1) {
			if (kills != 0) {
				throw invalid(Failure.LEGACY_STATE_CONTRADICTION,
					"Unassigned combat_odyssey task has kill progress");
			}
		} else if (taskId < 0 || taskId >= tier.tasks.size()) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, "combat_odyssey has an unknown task");
		} else if (kills < 0 || kills > tier.tasks.get(taskId)) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE,
				"combat_odyssey kills are outside the current task bound");
		}
		return new ParsedLegacy(prestige, Intro.NONE,
			new ActiveLegacy(tierId, taskId, kills, progress));
	}

	private static Classification classify(ParsedLegacy parsed) {
		if (parsed.prestige > 0) {
			return Classification.COMPLETED_CLAIMED;
		}
		if (parsed.active != null && parsed.active.tierId == 13 && parsed.active.taskId == 0
			&& ((parsed.active.progress & 1L) != 0L || parsed.active.kills >= 1)) {
			return Classification.COMPLETED_UNCLAIMED;
		}
		if (parsed.intro != Intro.NONE || parsed.active != null) {
			return Classification.PARTIAL;
		}
		return Classification.NONE;
	}

	private static long creditedKills(ParsedLegacy parsed, LegacyData data) {
		ActiveLegacy active = parsed.active;
		long credited = 0L;
		for (int tierId = 0; tierId < active.tierId; tierId++) {
			credited = Math.addExact(credited, data.getTier(tierId).totalKills);
		}
		LegacyTier tier = data.getTier(active.tierId);
		for (int taskId = 0; taskId < tier.tasks.size(); taskId++) {
			if ((active.progress & (1L << taskId)) != 0L) {
				credited = Math.addExact(credited, tier.tasks.get(taskId));
			}
		}
		if (active.taskId >= 0 && (active.progress & (1L << active.taskId)) == 0L) {
			credited = Math.addExact(credited, active.kills);
		}
		return Math.min(LEGACY_TOTAL_KILLS, credited);
	}

	private static MonsterSlayerState.LegacyStatus legacyStatus(Classification classification) {
		switch (classification) {
			case PARTIAL: return MonsterSlayerState.LegacyStatus.PARTIAL;
			case COMPLETED_UNCLAIMED: return MonsterSlayerState.LegacyStatus.COMPLETED_UNCLAIMED;
			case COMPLETED_CLAIMED: return MonsterSlayerState.LegacyStatus.COMPLETED_CLAIMED;
			default: return MonsterSlayerState.LegacyStatus.NONE;
		}
	}

	private static int typedInteger(Object value, String key, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof Integer)) {
			throw invalid(Failure.LEGACY_TYPE_MISMATCH, key + " has the wrong cache type");
		}
		return (Integer) value;
	}

	private static Long typedLong(Object value, String key) {
		if (value == null) {
			return null;
		}
		if (!(value instanceof Long)) {
			throw invalid(Failure.LEGACY_TYPE_MISMATCH, key + " has the wrong cache type");
		}
		return (Long) value;
	}

	private static String typedString(Object value, String key) {
		if (value == null) {
			return null;
		}
		if (!(value instanceof String)) {
			throw invalid(Failure.LEGACY_TYPE_MISMATCH, key + " has the wrong cache type");
		}
		return (String) value;
	}

	private static int parseInt(String value, String owner) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw invalid(Failure.MALFORMED_LEGACY_STATE, owner + " is not an integer");
		}
	}

	private static MigrationValidationException invalid(Failure failure, String message) {
		return new MigrationValidationException(failure, message);
	}

	public enum Classification {
		NONE,
		PARTIAL,
		COMPLETED_UNCLAIMED,
		COMPLETED_CLAIMED,
		ALREADY_MIGRATED
	}

	public enum Failure {
		INVALID_INPUT,
		LEGACY_TYPE_MISMATCH,
		MALFORMED_LEGACY_STATE,
		LEGACY_STATE_CONTRADICTION,
		NEW_STATE_CONFLICT,
		NEW_STATE_INVALID,
		ARITHMETIC_OR_STATE_BOUNDS
	}

	public static final class LegacySnapshot {
		private final Object odysseyState;
		private final Object tierProgress;
		private final Object prestige;

		private LegacySnapshot(Object odysseyState, Object tierProgress, Object prestige) {
			this.odysseyState = odysseyState;
			this.tierProgress = tierProgress;
			this.prestige = prestige;
		}

		public static LegacySnapshot of(Object odysseyState, Object tierProgress, Object prestige) {
			return new LegacySnapshot(odysseyState, tierProgress, prestige);
		}
	}

	public static final class LegacyData {
		private final List<LegacyTier> tiers;

		private LegacyData(List<LegacyTier> tiers) {
			this.tiers = Collections.unmodifiableList(new ArrayList<LegacyTier>(tiers));
		}

		public static LegacyData load(Path path) {
			try {
				JSONObject root = new JSONObject(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
				JSONArray tierArray = root.getJSONArray("tiers");
				List<LegacyTier> tiers = new ArrayList<LegacyTier>();
				long total = 0L;
				for (int tierIndex = 0; tierIndex < tierArray.length(); tierIndex++) {
					JSONObject tierObject = tierArray.getJSONObject(tierIndex);
					if (tierObject.getInt("tierId") != tierIndex) {
						throw new IllegalArgumentException("Combat Odyssey tier IDs no longer match legacy positions");
					}
					JSONArray taskArray = tierObject.getJSONArray("tasks");
					if (taskArray.length() < 1 || taskArray.length() > 62) {
						throw new IllegalArgumentException("Combat Odyssey tier task count is unsafe");
					}
					List<Integer> taskKills = new ArrayList<Integer>();
					long tierTotal = 0L;
					for (int taskIndex = 0; taskIndex < taskArray.length(); taskIndex++) {
						int kills = taskArray.getJSONObject(taskIndex).getInt("kills");
						if (kills <= 0) {
							throw new IllegalArgumentException("Combat Odyssey task has nonpositive kills");
						}
						taskKills.add(kills);
						tierTotal = Math.addExact(tierTotal, kills);
					}
					tiers.add(new LegacyTier(tierIndex, taskKills, tierTotal));
					total = Math.addExact(total, tierTotal);
				}
				if (tiers.size() != 14 || total != LEGACY_TOTAL_KILLS
					|| tiers.get(13).tasks.size() != 1 || tiers.get(13).tasks.get(0) != 1) {
					throw new IllegalArgumentException("Combat Odyssey migration inventory no longer matches audited data");
				}
				return new LegacyData(tiers);
			} catch (RuntimeException ex) {
				throw ex;
			} catch (Exception ex) {
				throw new IllegalArgumentException("Unable to load Combat Odyssey migration data", ex);
			}
		}

		private LegacyTier getTier(int tierId) {
			return tierId < 0 || tierId >= tiers.size() ? null : tiers.get(tierId);
		}
	}

	public static final class Result {
		private final boolean successful;
		private final Classification classification;
		private final Failure failure;
		private final String diagnostic;
		private final long creditedKills;
		private final MonsterSlayerState.Snapshot proposal;

		private Result(boolean successful, Classification classification, Failure failure,
				String diagnostic, long creditedKills, MonsterSlayerState.Snapshot proposal) {
			this.successful = successful;
			this.classification = classification;
			this.failure = failure;
			this.diagnostic = diagnostic;
			this.creditedKills = creditedKills;
			this.proposal = proposal;
		}

		private static Result success(Classification classification, long creditedKills,
				MonsterSlayerState.Snapshot proposal) {
			return new Result(true, classification, null, null, creditedKills, proposal);
		}

		private static Result failure(Failure failure, String diagnostic) {
			return new Result(false, null, failure, diagnostic, 0L, null);
		}

		public boolean isSuccessful() { return successful; }
		public Classification getClassification() { return classification; }
		public Failure getFailure() { return failure; }
		public String getDiagnostic() { return diagnostic; }
		public long getCreditedKills() { return creditedKills; }
		public MonsterSlayerState.Snapshot getProposal() { return proposal; }
	}

	private enum Intro { NONE, ACCEPTED, MET_BIGGUM }

	private static final class ParsedLegacy {
		private final int prestige;
		private final Intro intro;
		private final ActiveLegacy active;

		private ParsedLegacy(int prestige, Intro intro, ActiveLegacy active) {
			this.prestige = prestige;
			this.intro = intro;
			this.active = active;
		}
	}

	private static final class ActiveLegacy {
		private final int tierId;
		private final int taskId;
		private final int kills;
		private final long progress;

		private ActiveLegacy(int tierId, int taskId, int kills, long progress) {
			this.tierId = tierId;
			this.taskId = taskId;
			this.kills = kills;
			this.progress = progress;
		}
	}

	private static final class LegacyTier {
		private final int tierId;
		private final List<Integer> tasks;
		private final long totalKills;

		private LegacyTier(int tierId, List<Integer> tasks, long totalKills) {
			this.tierId = tierId;
			this.tasks = Collections.unmodifiableList(new ArrayList<Integer>(tasks));
			this.totalKills = totalKills;
		}
	}

	private static final class MigrationValidationException extends IllegalArgumentException {
		private final Failure failure;

		private MigrationValidationException(Failure failure, String message) {
			super(message);
			this.failure = failure;
		}
	}
}
