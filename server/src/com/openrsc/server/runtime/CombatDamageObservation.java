package com.openrsc.server.runtime;

import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Safe publication helper which cannot become damage authority. */
public final class CombatDamageObservation {
	private static final Logger LOGGER = LogManager.getLogger();

	private CombatDamageObservation() {
	}

	public static DamageRequest begin(final Mob source, final Mob target,
			final DamageRequest.SourceCategory category,
			final String effectKey, final int resolvedDamage,
			final DamageRequestCustomizer customizer) {
		try {
			final CombatDamageObserver observer = target.getWorld().getServer()
				.getCombatDamageObserver();
			if (!observer.isEnabled()) {
				return null;
			}
			final DamageRequest.Builder builder = DamageRequest.resolvedLegacy(
				source, target, category, effectKey, resolvedDamage);
			if (customizer != null) {
				customizer.customize(builder);
			}
			return builder.build();
		} catch (final RuntimeException observerFailure) {
			LOGGER.warn(
				"Combat damage observation setup failed for effect {}; gameplay result remains authoritative",
				effectKey, observerFailure);
			return null;
		}
	}

	public static void publish(final DamageRequest request,
			final int hitsBefore, final int hitsAfter) {
		if (request == null) {
			return;
		}
		try {
			request.getTarget().getWorld().getServer().getCombatDamageObserver()
				.onDamageObserved(DamageResult.observedCurrentPath(
					request, hitsBefore, hitsAfter));
		} catch (final RuntimeException observerFailure) {
			LOGGER.warn(
				"Combat damage observation failed for effect {}; gameplay result remains authoritative",
				request.getEffectKey(), observerFailure);
		}
	}

	@FunctionalInterface
	public interface DamageRequestCustomizer {
		void customize(DamageRequest.Builder builder);
	}
}
