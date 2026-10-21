package com.openrsc.server.model.action;

import com.openrsc.server.io.AgilityShortcut;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;
import java.util.function.IntSupplier;

/** The maintained gameplay transaction; adapters schedule it, tests exercise the same path. */
public final class ConfiguredAgilityAction {
	public static final String PENDING_ATTRIBUTE = "configured_agility_pending";
	public interface Actor {
		boolean available();
		boolean busy();
		void busy(boolean value);
		int effectiveAgility();
		WorldLocation location();
		Object pending();
		void pending(Object token);
		boolean placementCurrent();
		void requireSafe(WorldLocation location);
		void schedule(Runnable completion);
		void message(String message);
		void land(WorldLocation location);
	}
	private ConfiguredAgilityAction() {}
	public static void begin(final Actor actor, final AgilityShortcut shortcut, final IntSupplier roll) {
		Objects.requireNonNull(shortcut, "shortcut");
		if (!actor.available() || actor.busy() || actor.pending() != null || !actor.placementCurrent()) return;
		if (shortcut.chanceBasisPoints(actor.effectiveAgility()) < 0) {
			actor.message("You need an Agility level of " + shortcut.getRequiredLevel() + " to use this shortcut."); return;
		}
		try { requireSafe(actor, shortcut); }
		catch (IllegalStateException unsafe) { actor.message("This shortcut does not have a safe landing right now."); return; }
		final WorldLocation start = actor.location();
		final Object token = new Object();
		actor.pending(token); actor.busy(true);
		actor.message(shortcut.getTemplate().preparation);
		try {
			actor.schedule(() -> {
				if (actor.pending() != token) return;
				try {
					if (!actor.available() || !actor.busy() || !start.equals(actor.location()) || !actor.placementCurrent()) return;
					int chance = shortcut.chanceBasisPoints(actor.effectiveAgility());
					if (chance < 0) return;
					requireSafe(actor, shortcut);
					int random = roll.getAsInt();
					if (random < 0 || random >= 10000) throw new IllegalStateException("Shortcut random roll is outside 0..9999");
					boolean success = random < chance;
					actor.land(success ? shortcut.getSuccessDestination() : shortcut.getFailureDestination());
					actor.message(success ? shortcut.getTemplate().success : shortcut.getTemplate().failure);
				} catch (IllegalStateException unsafe) {
					actor.message("This shortcut does not have a safe landing right now.");
				} finally { release(actor, token); }
			});
		} catch (RuntimeException exception) { release(actor, token); throw exception; }
	}
	private static void requireSafe(Actor actor, AgilityShortcut shortcut) {
		actor.requireSafe(actor.location());
		actor.requireSafe(shortcut.getSuccessDestination()); actor.requireSafe(shortcut.getFailureDestination());
	}
	private static void release(Actor actor, Object token) {
		if (actor.pending() == token) { actor.pending(null); actor.busy(false); }
	}
}
