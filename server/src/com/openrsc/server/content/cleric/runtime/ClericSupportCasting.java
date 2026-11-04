package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericCastTransaction;
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
import com.openrsc.server.util.rsc.TeleportRestrictions;

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
        if (caster.getSkills().getLevel(Skill.HITS.id())<=0 || !canUseInCombatContext(caster)
            || caster.getSkills().getMaxStat(Skill.PRAYER.id()) < definition.getWorshipLevel()) return new CastResult(Outcome.NO_USEFUL_RECIPIENT,0);
        if (!isImplemented(definition.getId())) {
			return CastResult.notImplemented();
		}

        if (isUnify(definition) && TeleportRestrictions.isBlocked(caster)) {
            return new CastResult(Outcome.TELEPORT_RESTRICTED, 0);
        }

		if (definition.isHostile()) return ClericHostileRuntime.cast(caster, definition);

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
            if (isUnify(definition)) {
                application = prepareUnify(target, caster, definition);
            } else {
                application = ClericTimedEffectRuntime.prepare(caster,target,definition,effectRank);
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

	/** MyWorld uses the same Cleric rules in PvE and Wilderness combat. */
	public static boolean canUseInCombatContext(final Player player) {
		return !player.getDuel().isDuelActive()
			&& (player.getConfig().WANT_MYWORLD || !isPvpContext(player));
	}

    private static boolean isImplemented(final ClericSpellId id) { return id != null && id.isActive(); }

    private static boolean isUnify(ClericSpellDefinition definition) {
        return definition.getId() == ClericSpellId.UNIFY
            || definition.getId() == ClericSpellId.GREATER_UNIFY;
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
			final Player recipient, final Player caster, final ClericSpellDefinition definition) {
        if (TeleportRestrictions.isBlocked(recipient)) {
            return IneffectiveApplication.INSTANCE;
        }
		final Point start = recipient.getLocation();
		final Point destination = caster.getLocation();
		final List<ClericUnifyStepPlanner.Step> planned = ClericUnifyStepPlanner.plan(
			start.getX(), start.getY(), destination.getX(), destination.getY(), definition.getRadius(), definition.getId() == ClericSpellId.GREATER_UNIFY ? 3 : 1,
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
        final ClericUnifyStepPlanner.Step last = planned.get(planned.size() - 1);
        final Point landing = Point.location(last.getX(), last.getY());
        if (TeleportRestrictions.isLocationBlocked(landing)) {
            return IneffectiveApplication.INSTANCE;
        }
        final WorldLocation destinationLocation = recipient.getWorld().getRegionManager()
            .fromRuntimeCompatibilityPoint(landing, recipient.getWorldLocation(), true);
        return new UnifyApplication(recipient, landing, destinationLocation);
	}

    private static Item[] createCost(ClericSpellDefinition definition) {
        List<Item> cost=new ArrayList<Item>();
        for(com.openrsc.server.content.cleric.ClericResourceCost resource:definition.getResourceCosts())cost.add(new Item(resource.getItemId(),resource.getCount(),false));
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
			return candidate.getSkills().getLevel(Skill.HITS.id()) > 0 && candidate.getParty() == party
				&& candidate.isLoggedIn()
				&& !candidate.isRemoved()
				&& !candidate.isUnregistering()
				&& canUseInCombatContext(candidate);
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
        private final Point landing;
        private final WorldLocation destination;

        private UnifyApplication(Player recipient, Point landing, WorldLocation destination) {
            this.recipient = recipient;
            this.landing = landing;
            this.destination = destination;
        }

		@Override
		public boolean isUseful() {
			return true;
		}

		@Override
		public void commit() {
            recipient.face(landing);
            if (recipient.getConfig().WANT_LAYERED_PLAYER_LOCATION_AUTHORITY)
                recipient.teleportLayered(destination, false);
            else recipient.teleport(landing.getX(), landing.getY(), false);
		}
	}


	public enum Outcome {
		SUCCESS,
		NO_USEFUL_RECIPIENT,
		INSUFFICIENT_SIGILS,
		TELEPORT_RESTRICTED,
		NOT_IMPLEMENTED
	}

	public static final class CastResult {
		private static final CastResult NOT_IMPLEMENTED =
			new CastResult(Outcome.NOT_IMPLEMENTED, 0);

		private final Outcome outcome;
		private final int affectedRecipientCount;

		CastResult(Outcome outcome, int affectedRecipientCount) {
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
