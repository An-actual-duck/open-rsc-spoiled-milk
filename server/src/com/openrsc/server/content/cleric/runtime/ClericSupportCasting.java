package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericCastTransaction;
import com.openrsc.server.content.cleric.ClericPurifyEffect;
import com.openrsc.server.content.cleric.ClericRestoreEffect;
import com.openrsc.server.content.cleric.ClericSigilItemId;
import com.openrsc.server.content.cleric.ClericSigilMaterial;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.ClericSupportTargeting;
import com.openrsc.server.content.cleric.ClericUnifyStepPlanner;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server adapters for the shared Cleric targeting and cast-transaction contract. */
public final class ClericSupportCasting {
	private ClericSupportCasting() {
	}

	public static CastResult cast(final Player caster,
			final ClericSpellDefinition definition) {
		if (caster == null || definition == null) {
			throw new IllegalArgumentException("Cleric casting requires a player and definition");
		}
		if (!isImplemented(definition.getId())) {
			return CastResult.notImplemented();
		}

		final Party party = caster.getParty();
		if (party == null) {
			return new CastResult(Outcome.NO_USEFUL_RECIPIENT, 0);
		}
		final List<Player> partySnapshot = snapshotPartyPlayers(party);
		final List<Player> targets = ClericSupportTargeting.resolve(
			caster, partySnapshot, definition.getRadius(), new PlayerCandidateView(caster, party));
		final List<ClericCastTransaction.PreparedApplication> applications =
			new ArrayList<ClericCastTransaction.PreparedApplication>(targets.size());
		final int effectRank = definition.getId() != ClericSpellId.UNIFY
			? definition.resolveEffectRank(
				caster.getCarriedItems().getEquipment().getHolyPower()) : 0;
		for (Player target : targets) {
			final ClericCastTransaction.PreparedApplication application;
			switch (definition.getId()) {
				case UNIFY:
					application = prepareUnify(target, caster);
					break;
				case PURIFY:
					application = preparePurify(target, effectRank);
					break;
				case RESTORE:
					application = prepareRestore(target, effectRank);
					break;
				case MEND:
				case FERVOR:
				case WARD:
				case GREATER_MEND:
				case ZEAL:
				case THORNS:
				case AEGIS:
				case RALLY:
				case RESPITE:
					application = ClericTimedEffectRuntime.prepare(
						caster, target, definition, effectRank);
					break;
				default:
					throw new IllegalStateException("Unprepared implemented Cleric spell");
			}
			applications.add(withOnEntityAnimation(target, definition, application));
		}

		final ClericCastTransaction.Result transaction = ClericCastTransaction.execute(
			applications, new ClericCastTransaction.ResourceCommitBoundary() {
				@Override
				public boolean commit(Runnable applicationCommit) {
					synchronized (caster) {
						final Item[] cost = createCost(definition);
						final boolean committed = caster.getCarriedItems().removeWithStateChange(
							cost, false, () -> {
								applicationCommit.run();
								return true;
							});
						if (committed) {
							ActionSender.sendInventory(caster);
						}
						return committed;
					}
				}
			});
		return CastResult.fromTransaction(transaction);
	}

	private static ClericCastTransaction.PreparedApplication withOnEntityAnimation(
			final Player recipient, final ClericSpellDefinition definition,
			final ClericCastTransaction.PreparedApplication application) {
		if (!definition.getPresentation().hasOnEntityAnimation()) {
			return application;
		}
		return new OnEntityAnimationApplication(recipient,
			definition.getPresentation().getOnEntityAnimationId(), application);
	}

	public static boolean isPvpContext(final Player player) {
		return player.getConfig().USES_PK_MODE
			|| player.getLocation().inWilderness()
			|| player.getDuel().isDuelActive();
	}

	private static boolean isImplemented(final ClericSpellId spellId) {
		return spellId == ClericSpellId.UNIFY
			|| spellId == ClericSpellId.PURIFY
			|| spellId == ClericSpellId.RESTORE
			|| spellId == ClericSpellId.MEND
			|| spellId == ClericSpellId.FERVOR
			|| spellId == ClericSpellId.WARD
			|| spellId == ClericSpellId.GREATER_MEND
			|| spellId == ClericSpellId.ZEAL
			|| spellId == ClericSpellId.THORNS
			|| spellId == ClericSpellId.AEGIS
			|| spellId == ClericSpellId.RALLY
			|| spellId == ClericSpellId.RESPITE;
	}

	private static List<Player> snapshotPartyPlayers(Party party) {
		final List<PartyPlayer> members = new ArrayList<PartyPlayer>(party.getPlayers());
		final List<Player> players = new ArrayList<Player>(members.size());
		for (PartyPlayer member : members) {
			if (member != null && member.isOnline()) {
				players.add(member.getPlayerReference());
			}
		}
		return Collections.unmodifiableList(players);
	}

	private static ClericCastTransaction.PreparedApplication prepareUnify(
			final Player recipient, final Player caster) {
		final Point start = recipient.getLocation();
		final Point destination = caster.getLocation();
		final List<ClericUnifyStepPlanner.Step> planned = ClericUnifyStepPlanner.plan(
			start.getX(), start.getY(), destination.getX(), destination.getY(),
			new ClericUnifyStepPlanner.Traversability() {
				@Override
				public boolean canStep(int startX, int startY, int destinationX, int destinationY) {
					if (!PathValidation.checkAdjacent(
						recipient, startX, startY, destinationX, destinationY)) {
						return false;
					}
					return recipient.getConfig().PLAYER_BLOCKING != 1
						|| recipient.getWorld().getRegionManager().findInteractionPlayer(
							destinationX, destinationY, recipient, false) == null;
				}
			});
		if (planned.isEmpty()) {
			return IneffectiveApplication.INSTANCE;
		}
		final List<Point> steps = new ArrayList<Point>(planned.size());
		for (ClericUnifyStepPlanner.Step step : planned) {
			steps.add(Point.location(step.getX(), step.getY()));
		}
		return new UnifyApplication(recipient, steps);
	}

	private static ClericCastTransaction.PreparedApplication preparePurify(
			final Player recipient, final int effectRank) {
		final ClericPurifyEffect.Plan plan = ClericPurifyEffect.plan(
			recipient.getCurrentPoisonPower(), effectRank);
		return plan.isUseful()
			? new PurifyApplication(recipient, plan.getReduction())
			: IneffectiveApplication.INSTANCE;
	}

	private static ClericCastTransaction.PreparedApplication prepareRestore(
			final Player recipient, final int effectRank) {
		final int skillCount = recipient.getWorld().getServer().getConstants()
			.getSkills().getSkillsCount();
		final int[] currentLevels = new int[skillCount];
		final int[] validMaximums = new int[skillCount];
		for (int skill = 0; skill < skillCount; skill++) {
			currentLevels[skill] = recipient.getSkills().getLevel(skill);
			validMaximums[skill] = recipient.getEquipmentAdjustedNormalLevel(skill);
		}
		final ClericRestoreEffect.Plan plan = ClericRestoreEffect.plan(
			currentLevels, validMaximums, Skill.HITS.id(), effectRank);
		return plan.isUseful()
			? new RestoreApplication(recipient, plan.getRestoredLevels())
			: IneffectiveApplication.INSTANCE;
	}

	private static Item[] createCost(ClericSpellDefinition definition) {
		final List<Item> cost = new ArrayList<Item>(ClericSigilMaterial.values().length);
		for (ClericSigilMaterial material : ClericSigilMaterial.values()) {
			final int count = definition.getPrimarySigilCost().getCount(material);
			if (count > 0) {
				cost.add(new Item(ClericSigilItemId.get(
					material, definition.getAlignment(), true).getItemId(), count));
			}
		}
		return cost.toArray(new Item[cost.size()]);
	}

	private static final class PlayerCandidateView
			implements ClericSupportTargeting.CandidateView<Player> {
		private final Player caster;
		private final Party party;

		private PlayerCandidateView(Player caster, Party party) {
			this.caster = caster;
			this.party = party;
		}

		@Override
		public boolean isEligibleRecipient(Player candidate) {
			return candidate.getParty() == party
				&& candidate.isLoggedIn()
				&& !candidate.isRemoved()
				&& !candidate.isUnregistering()
				&& !isPvpContext(candidate);
		}

		@Override
		public Object getWorldSpace(Player candidate) {
			return candidate.getWorldLocation().getWorldSpace();
		}

		@Override
		public int getSignedLevel(Player candidate) {
			return candidate.getWorldLocation().getCoordinate().getLevel();
		}

		@Override
		public int getX(Player candidate) {
			return candidate.getWorldLocation().getCoordinate().getX();
		}

		@Override
		public int getY(Player candidate) {
			return candidate.getWorldLocation().getCoordinate().getY();
		}

		@Override
		public boolean hasLineOfEffect(Player ignoredCaster, Player candidate) {
			final WorldLocation casterLocation = caster.getWorldLocation();
			final WorldLocation candidateLocation = candidate.getWorldLocation();
			return PathValidation.checkPath(
				caster.getWorld(), casterLocation, candidateLocation, false);
		}
	}

	private enum IneffectiveApplication implements ClericCastTransaction.PreparedApplication {
		INSTANCE;

		@Override
		public boolean isUseful() {
			return false;
		}

		@Override
		public void commit() {
			throw new IllegalStateException("An ineffective Cleric application cannot commit");
		}
	}

	private static final class OnEntityAnimationApplication
			implements ClericCastTransaction.PreparedApplication {
		private final Player recipient;
		private final int animationId;
		private final ClericCastTransaction.PreparedApplication application;

		private OnEntityAnimationApplication(final Player recipient, final int animationId,
				final ClericCastTransaction.PreparedApplication application) {
			this.recipient = recipient;
			this.animationId = animationId;
			this.application = application;
		}

		@Override
		public boolean isUseful() {
			return application.isUseful();
		}

		@Override
		public void commit() {
			application.commit();
			recipient.getUpdateFlags().setCombatEffect(
				new CombatEffect(recipient, animationId));
		}
	}

	private static final class UnifyApplication
			implements ClericCastTransaction.PreparedApplication {
		private final Player recipient;
		private final List<Point> steps;

		private UnifyApplication(Player recipient, List<Point> steps) {
			this.recipient = recipient;
			this.steps = Collections.unmodifiableList(new ArrayList<Point>(steps));
		}

		@Override
		public boolean isUseful() {
			return true;
		}

		@Override
		public void commit() {
			recipient.resetPath();
			for (Point step : steps) {
				recipient.face(step);
				recipient.setLocation(step, false);
				recipient.stepIncrementActivity();
			}
		}
	}

	private static final class PurifyApplication
			implements ClericCastTransaction.PreparedApplication {
		private final Player recipient;
		private final int reduction;

		private PurifyApplication(final Player recipient, final int reduction) {
			this.recipient = recipient;
			this.reduction = reduction;
		}

		@Override
		public boolean isUseful() {
			return true;
		}

		@Override
		public void commit() {
			recipient.reduceCurrentPoisonPower(reduction);
		}
	}

	private static final class RestoreApplication
			implements ClericCastTransaction.PreparedApplication {
		private final Player recipient;
		private final int[] restoredLevels;

		private RestoreApplication(final Player recipient, final int[] restoredLevels) {
			this.recipient = recipient;
			this.restoredLevels = restoredLevels.clone();
		}

		@Override
		public boolean isUseful() {
			return true;
		}

		@Override
		public void commit() {
			for (int skill = 0; skill < restoredLevels.length; skill++) {
				final int current = recipient.getSkills().getLevel(skill);
				final int validMaximum = recipient.getEquipmentAdjustedNormalLevel(skill);
				final int restored = Math.min(restoredLevels[skill], validMaximum);
				if (current < restored) {
					recipient.getSkills().setLevel(skill, restored, true, true);
				}
			}
		}
	}

	public enum Outcome {
		SUCCESS,
		NO_USEFUL_RECIPIENT,
		INSUFFICIENT_SIGILS,
		NOT_IMPLEMENTED
	}

	public static final class CastResult {
		private static final CastResult NOT_IMPLEMENTED =
			new CastResult(Outcome.NOT_IMPLEMENTED, 0);

		private final Outcome outcome;
		private final int affectedRecipientCount;

		private CastResult(Outcome outcome, int affectedRecipientCount) {
			this.outcome = outcome;
			this.affectedRecipientCount = affectedRecipientCount;
		}

		private static CastResult notImplemented() {
			return NOT_IMPLEMENTED;
		}

		private static CastResult fromTransaction(ClericCastTransaction.Result result) {
			switch (result.getOutcome()) {
				case SUCCESS:
					return new CastResult(Outcome.SUCCESS, result.getAppliedRecipientCount());
				case NO_USEFUL_APPLICATION:
					return new CastResult(Outcome.NO_USEFUL_RECIPIENT, 0);
				case INSUFFICIENT_RESOURCES:
					return new CastResult(Outcome.INSUFFICIENT_SIGILS, 0);
				default:
					throw new IllegalStateException("Unsupported Cleric transaction outcome");
			}
		}

		public Outcome getOutcome() {
			return outcome;
		}

		public int getAffectedRecipientCount() {
			return affectedRecipientCount;
		}
	}
}
