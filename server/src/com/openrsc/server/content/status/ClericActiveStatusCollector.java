package com.openrsc.server.content.status;

import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.effect.ClericEffectCounterKind;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRankDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.content.cleric.effect.ClericEffectStatusSnapshot;
import com.openrsc.server.model.entity.player.Player;

import java.util.List;

/** Content-side bridge from an installed Cleric registry to generic HUD state. */
public final class ClericActiveStatusCollector {
	private ClericActiveStatusCollector() {
	}

	public static void append(Player player, List<ActiveStatusEntry> statuses) {
		if (player == null || statuses == null) {
			throw new IllegalArgumentException("Player and active-status destination are required");
		}
		if (!(player.getTransientEffectState() instanceof ClericEffectRegistry)) {
			return;
		}
		ClericEffectRegistry registry =
			(ClericEffectRegistry) player.getTransientEffectState();
		for (ClericEffectStatusSnapshot snapshot :
				registry.statusSnapshot(ClericEffectOrigins.validatorFor(player))) {
			ClericEffectRankDefinition<?> definition = snapshot.getDefinition();
			statuses.add(ActiveStatusEntry.cleric(
				priorityKey(definition), definition.getSpellId().getCode(),
				ClericSpellCatalog.get(definition.getSpellId()).getPresentation()
					.getSpellbookIconItemId(),
				snapshot.getRemainingSeconds(), definition.getRank(),
				toPresentationCounter(definition.getCounterKind()),
				snapshot.getRemainingCounter()));
		}
	}

	private static String priorityKey(ClericEffectRankDefinition<?> definition) {
		switch (definition.getFamily()) {
			case HEALING_PULSES:
			case PROTECTION:
				return definition.getFamily().getKey();
			default:
				return "cleric:" + definition.getSpellId().getKey();
		}
	}

	private static ActiveStatusEntry.CounterKind toPresentationCounter(
			ClericEffectCounterKind counterKind) {
		switch (counterKind) {
			case CHARGES:
				return ActiveStatusEntry.CounterKind.CHARGES;
			case PULSES:
				return ActiveStatusEntry.CounterKind.PULSES;
			case NONE:
			default:
				return ActiveStatusEntry.CounterKind.NONE;
		}
	}
}
