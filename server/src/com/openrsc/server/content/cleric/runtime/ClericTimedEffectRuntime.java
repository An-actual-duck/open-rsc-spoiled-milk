package com.openrsc.server.content.cleric.runtime;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericCastTransaction;
import com.openrsc.server.content.cleric.ClericDirectCombatEffects;
import com.openrsc.server.content.cleric.ClericHealingPulseEffect;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.effect.ClericEffectCatalog;
import com.openrsc.server.content.cleric.effect.ClericEffectClock;
import com.openrsc.server.content.cleric.effect.ClericEffectCounterKind;
import com.openrsc.server.content.cleric.effect.ClericEffectEntry;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.effect.ClericEffectMagnitudes;
import com.openrsc.server.content.cleric.effect.ClericEffectMagnitude;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigin;
import com.openrsc.server.content.cleric.effect.ClericEffectOriginValidator;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRankDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.TransientEffectState;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.Optional;

/** Content-owned adapter between C07 casts and the C08 transient registry. */
public final class ClericTimedEffectRuntime {
	private ClericTimedEffectRuntime() {
	}

	public static ClericCastTransaction.PreparedApplication prepare(
			final Player caster, final Player recipient,
			final ClericSpellDefinition spell, final int effectRank) {
		if (caster == null || recipient == null || spell == null
				|| !isImplementedTimedSpell(spell.getId())) {
			throw new IllegalArgumentException("Unsupported timed Cleric application");
		}
		if (spell.getId() == ClericSpellId.RALLY
				&& !ClericDirectCombatEffects.isBelowPercent(
					recipient.getSkills().getLevel(Skill.HITS.id()),
					recipient.getHealingMaximumHits(), 50)) {
			return IneffectiveApplication.INSTANCE;
		}
		final RegistryAttachment attachment = registryForPreparation(recipient);
		final ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition =
			ClericEffectCatalog.get(spell.getId(), effectRank);
		final ClericEffectOrigin origin = ClericEffectOrigins.current(caster, recipient);
		final ClericEffectOriginValidator validator =
			ClericEffectOrigins.validatorFor(recipient);
		final ClericEffectRegistry.ApplyResult preview = attachment.registry.preview(
			definition, origin, validator);
		return new TimedApplication(recipient, attachment, definition, origin,
			validator, preview.isUseful());
	}

	public static double getRespiteSpeedBonus(final Player recipient) {
		final ClericEffectRegistry registry = installedRegistry(recipient);
		if (registry == null) {
			return 0.0D;
		}
		final Optional<ClericEffectEntry> entry = registry.get(
			ClericEffectFamily.PASSIVE_REGENERATION,
			ClericEffectOrigins.validatorFor(recipient));
		if (!entry.isPresent()
				|| entry.get().getDefinition().getSpellId() != ClericSpellId.RESPITE) {
			return 0.0D;
		}
		final ClericEffectMagnitudes.Regeneration magnitude =
			(ClericEffectMagnitudes.Regeneration) entry.get().getDefinition().getMagnitude();
		return magnitude.getSpeedIncreasePercent() / 100.0D;
	}

	static PulseResult pulseHealing(final Player recipient) {
		final ClericEffectRegistry registry = installedRegistry(recipient);
		if (registry == null) {
			return PulseResult.MISSING_OR_INVALID;
		}
		final ClericEffectOriginValidator validator =
			ClericEffectOrigins.validatorFor(recipient);
		final Optional<ClericEffectEntry> current = registry.get(
			ClericEffectFamily.HEALING_PULSES, validator);
		if (!current.isPresent()) {
			ActionSender.sendActivePotionEffects(recipient);
			return PulseResult.MISSING_OR_INVALID;
		}
		final ClericEffectEntry entry = current.get();
		final ClericSpellId spellId = entry.getDefinition().getSpellId();
		if (spellId != ClericSpellId.MEND && spellId != ClericSpellId.GREATER_MEND) {
			throw new IllegalStateException("Healing-pulse family contains a non-Mend spell");
		}
		final ClericEffectRegistry.CounterResult consumed = registry.consumeCounter(
			ClericEffectFamily.HEALING_PULSES, ClericEffectCounterKind.PULSES, validator);
		if (consumed != ClericEffectRegistry.CounterResult.CONSUMED
				&& consumed != ClericEffectRegistry.CounterResult.EXHAUSTED) {
			ActionSender.sendActivePotionEffects(recipient);
			return PulseResult.MISSING_OR_INVALID;
		}
		if (validator.isCurrent(entry.getOrigin())) {
			final ClericEffectMagnitudes.HealingPulse magnitude =
				(ClericEffectMagnitudes.HealingPulse) entry.getDefinition().getMagnitude();
			heal(recipient, magnitude.getHitsPerPulse());
		}
		ActionSender.sendActivePotionEffects(recipient);
		return consumed == ClericEffectRegistry.CounterResult.CONSUMED
			? PulseResult.CONTINUES : PulseResult.COMPLETED;
	}

	private static void heal(final Player recipient, final int hitsPerPulse) {
		final int currentHits = recipient.getSkills().getLevel(Skill.HITS.id());
		final int healed = ClericHealingPulseEffect.healedHits(
			currentHits, recipient.getHealingMaximumHits(), hitsPerPulse);
		if (healed <= 0) {
			return;
		}
		recipient.getSkills().setLevel(Skill.HITS.id(), currentHits + healed);
		recipient.getUpdateFlags().addHitSplat(
			new HitSplat(recipient, HitSplat.TYPE_HEAL, healed));
		ActionSender.sendStat(recipient, Skill.HITS.id());
		if (recipient.getConfig().WANT_PARTIES && recipient.getParty() != null) {
			recipient.getParty().sendParty();
		}
	}

	private static boolean isImplementedTimedSpell(final ClericSpellId spellId) {
		return spellId == ClericSpellId.MEND
			|| spellId == ClericSpellId.FERVOR
			|| spellId == ClericSpellId.WARD
			|| spellId == ClericSpellId.GREATER_MEND
			|| spellId == ClericSpellId.ZEAL
			|| spellId == ClericSpellId.THORNS
			|| spellId == ClericSpellId.AEGIS
			|| spellId == ClericSpellId.RALLY
			|| spellId == ClericSpellId.RESPITE;
	}

	private static RegistryAttachment registryForPreparation(final Player recipient) {
		final TransientEffectState state = recipient.getTransientEffectState();
		if (state == TransientEffectState.empty()) {
			return new RegistryAttachment(new ClericEffectRegistry(
				ClericEffectClock.game(
					recipient.getWorld().getServer().getGameClock(),
					recipient.getConfig().GAME_TICK)), true);
		}
		if (!(state instanceof ClericEffectRegistry)) {
			throw new IllegalStateException("Unsupported installed transient-effect state");
		}
		return new RegistryAttachment((ClericEffectRegistry) state, false);
	}

	private static ClericEffectRegistry installedRegistry(final Player recipient) {
		if (recipient == null
				|| !(recipient.getTransientEffectState() instanceof ClericEffectRegistry)) {
			return null;
		}
		return (ClericEffectRegistry) recipient.getTransientEffectState();
	}

	public enum PulseResult {
		MISSING_OR_INVALID,
		CONTINUES,
		COMPLETED
	}

	private enum IneffectiveApplication
			implements ClericCastTransaction.PreparedApplication {
		INSTANCE;

		@Override
		public boolean isUseful() {
			return false;
		}

		@Override
		public void commit() {
			throw new IllegalStateException("Ineffective Rally application committed");
		}
	}

	private static final class RegistryAttachment {
		private final ClericEffectRegistry registry;
		private final boolean installOnCommit;

		private RegistryAttachment(final ClericEffectRegistry registry,
				final boolean installOnCommit) {
			this.registry = registry;
			this.installOnCommit = installOnCommit;
		}
	}

	private static final class TimedApplication
			implements ClericCastTransaction.PreparedApplication {
		private final Player recipient;
		private final RegistryAttachment attachment;
		private final ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition;
		private final ClericEffectOrigin origin;
		private final ClericEffectOriginValidator validator;
		private final boolean useful;

		private TimedApplication(final Player recipient,
				final RegistryAttachment attachment,
				final ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
				final ClericEffectOrigin origin,
				final ClericEffectOriginValidator validator, final boolean useful) {
			this.recipient = recipient;
			this.attachment = attachment;
			this.definition = definition;
			this.origin = origin;
			this.validator = validator;
			this.useful = useful;
		}

		@Override
		public boolean isUseful() {
			return useful;
		}

		@Override
		public void commit() {
			if (!useful) {
				throw new IllegalStateException("Ineffective timed Cleric application committed");
			}
			if (attachment.installOnCommit) {
				recipient.installTransientEffectState(attachment.registry);
			}
			final ClericEffectRegistry.ApplyResult applied = attachment.registry.apply(
				definition, origin, validator);
			if (!applied.isUseful()) {
				throw new IllegalStateException("Prepared Cleric effect changed before commit");
			}
			if (definition.getFamily() == ClericEffectFamily.HEALING_PULSES) {
				if (pulseHealing(recipient) != PulseResult.CONTINUES) {
					throw new IllegalStateException("Fresh Mend effect did not retain delayed pulses");
				}
				ClericHealingPulseEvent.restart(recipient);
			} else {
				ActionSender.sendActivePotionEffects(recipient);
			}
		}
	}
}
