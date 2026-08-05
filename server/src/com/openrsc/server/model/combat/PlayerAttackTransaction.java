package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Spells;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;

/**
 * Per-player serialization point for validate/approach/commit attack starts.
 *
 * <p>The 100-tick lease bounds orphaned callbacks without changing any
 * approach radius, pathing rule, or scheduler cadence. Replacing the bound
 * walk action normally cancels an intent much sooner.</p>
 */
public final class PlayerAttackTransaction {
	public static final long MAX_PENDING_TICKS = 100L;

	public interface CommitAction {
		boolean commit();
	}

	private final Player owner;
	private long nextIntentId;
	private AttackIntent pending;
	private WalkToAction pendingApproach;
	private boolean commitInProgress;

	public PlayerAttackTransaction(final Player owner) {
		if (owner == null) {
			throw new IllegalArgumentException("attack transaction owner is required");
		}
		this.owner = owner;
	}

	public synchronized AttackIntent issue(final Mob target,
			final CombatStyle style, final AttackIntent.Channel channel,
			final AttackIntent.Source source, final Spells spell) {
		expirePendingIfNecessary();
		if (source == AttackIntent.Source.RETALIATION && pending != null
			&& pending.getSource() == AttackIntent.Source.MANUAL) {
			return null;
		}
		pendingApproach = null;
		final long commandTick = owner.getWorld().getServer().getCurrentTick();
		pending = new AttackIntent(++nextIntentId, owner, target, style,
			channel, source, commandTick,
			Math.addExact(commandTick, MAX_PENDING_TICKS), spell);
		return pending;
	}

	public synchronized void bindApproach(final AttackIntent intent,
			final WalkToAction approach) {
		if (intent != null && intent == pending && approach != null) {
			pendingApproach = approach;
		}
	}

	public synchronized AttackTransactionResult prepare(
			final AttackIntent intent) {
		if (intent == null || intent != pending) {
			return result(AttackTransactionResult.Status.CONFLICT,
				AttackTransactionResult.Reason.INTENT_NOT_CURRENT, intent, null);
		}
		if (intent.isExpired(owner.getWorld().getServer().getCurrentTick())) {
			clearIfCurrent(intent);
			return result(AttackTransactionResult.Status.REJECTED,
				AttackTransactionResult.Reason.EXPIRED, intent, null);
		}
		final CombatEligibilityDecision decision = CombatEligibility.evaluate(
			CombatEligibilityRequest.builder(owner, intent.getTarget(),
				CombatEligibilityPhase.APPROACH, intent.getStyle())
				.snapshots(intent.getActorSnapshot(), intent.getTargetSnapshot())
				.currentParticipants(true)
				.registration(true)
				.sameSpatialDomain(true)
				.build());
		if (!decision.isAllowed()) {
			clearIfCurrent(intent);
			return result(AttackTransactionResult.Status.REJECTED,
				decision.getReason() == CombatEligibilityReason.SOURCE_LIFECYCLE_CHANGED
					|| decision.getReason() == CombatEligibilityReason.TARGET_LIFECYCLE_CHANGED
					? AttackTransactionResult.Reason.PARTICIPANT_CHANGED
					: AttackTransactionResult.Reason.ELIGIBILITY_REJECTED,
				intent, decision.getReason());
		}
		if (!intent.hasCurrentLoadout()) {
			clearIfCurrent(intent);
			return result(AttackTransactionResult.Status.REJECTED,
				AttackTransactionResult.Reason.LOADOUT_CHANGED, intent, null);
		}
		return result(AttackTransactionResult.Status.READY_TO_COMMIT,
			AttackTransactionResult.Reason.ACCEPTED, intent, null);
	}

	public synchronized AttackTransactionResult commit(
			final AttackIntent intent, final CommitAction action) {
		if (action == null) {
			throw new IllegalArgumentException("attack commit action is required");
		}
		final AttackTransactionResult prepared = prepare(intent);
		if (!prepared.isReadyToCommit()) return prepared;
		boolean committed = false;
		commitInProgress = true;
		try {
			committed = action.commit();
		} catch (RuntimeException failure) {
			clearIfCurrent(intent);
			throw failure;
		} finally {
			commitInProgress = false;
		}
		if (!committed) {
			clearIfCurrent(intent);
			return result(AttackTransactionResult.Status.REJECTED,
				AttackTransactionResult.Reason.COMMIT_FAILED, intent, null);
		}
		pending = null;
		pendingApproach = null;
		return result(AttackTransactionResult.Status.COMMITTED,
			AttackTransactionResult.Reason.ACCEPTED, intent, null);
	}

	public synchronized AttackTransactionResult cancel(
			final AttackIntent intent,
			final AttackTransactionResult.Reason reason) {
		if (intent == null || intent != pending) {
			return result(AttackTransactionResult.Status.CONFLICT,
				AttackTransactionResult.Reason.INTENT_NOT_CURRENT, intent, null);
		}
		clearIfCurrent(intent);
		return result(AttackTransactionResult.Status.REJECTED,
			reason == null ? AttackTransactionResult.Reason.SUPERSEDED : reason,
			intent, null);
	}

	public synchronized void cancelCurrent(
			final AttackTransactionResult.Reason reason) {
		if (pending != null) cancel(pending, reason);
	}

	public synchronized void onWalkToActionChanged(
			final WalkToAction previous, final WalkToAction replacement) {
		if (commitInProgress || pending == null || pendingApproach == null
			|| previous != pendingApproach || replacement == previous) {
			return;
		}
		pending = null;
		pendingApproach = null;
	}

	public synchronized AttackIntent getPending() {
		expirePendingIfNecessary();
		return pending;
	}

	public synchronized AttackIntent getPending(final Mob target,
			final AttackIntent.Channel channel) {
		expirePendingIfNecessary();
		return pending != null && pending.getTarget() == target
			&& pending.getChannel() == channel ? pending : null;
	}

	private void clearIfCurrent(final AttackIntent intent) {
		if (pending == intent) {
			pending = null;
			pendingApproach = null;
		}
	}

	private void expirePendingIfNecessary() {
		if (pending != null && pending.isExpired(
			owner.getWorld().getServer().getCurrentTick())) {
			pending = null;
			pendingApproach = null;
		}
	}

	private static AttackTransactionResult result(
			final AttackTransactionResult.Status status,
			final AttackTransactionResult.Reason reason,
			final AttackIntent intent,
			final CombatEligibilityReason eligibilityReason) {
		return new AttackTransactionResult(status, reason, intent, eligibilityReason);
	}
}
