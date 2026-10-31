
package test;

import com.openrsc.server.content.status.ActiveStatusEntry;
import com.openrsc.server.content.status.ActiveStatusInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ActiveStatusInventoryFixture {
	private interface Action { void run(); }
	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}
	private static void reject(Action action, String message) {
		try { action.run(); throw new AssertionError(message); }
		catch (IllegalArgumentException expected) { }
	}
	private static ActiveStatusEntry item(String key, int id, int seconds) {
		return ActiveStatusEntry.item(key, id, seconds);
	}
	public static void main(String[] args) {
		List<String> expected = Arrays.asList(
			"cleric:saradomin_blessing", "cleric:guthix_blessing", "cleric:zamorak_blessing",
            "cleric:healing_pulses", "cleric:protection", "potion:brawn",
			"potion:deftness", "cleric:fervor", "cleric:rally",
			"potion:stat_reduction_protection", "cleric:thorns", "cleric:zeal",
			"potion:magic_resistance", "potion:melee_resistance",
			"potion:poison_protection", "potion:antidote_cleanse", "slayer:slimy_spit", "slayer:slime_solvent", "potion:ranged_resistance",
			"potion:regeneration", "cleric:respite", "potion:insight",
			"potion:insight_skills", "potion:luck", "potion:notation",
			"potion:skiller", "potion:speed", "potion:warrior");
		check(ActiveStatusInventory.getAuthoredOrder().equals(expected),
			"authored mixed priority drift");

		ArrayList<ActiveStatusEntry> reversed = new ArrayList<ActiveStatusEntry>();
		for (int index = expected.size() - 1; index >= 0; index--) {
			reversed.add(item(expected.get(index), 100 + index, 10 + index));
		}
		ActiveStatusInventory ordered = ActiveStatusInventory.select(reversed);
		for (int index = 0; index < expected.size(); index++) {
			check(ordered.getVisible().get(index).getStableKey().equals(expected.get(index)),
				"priority mismatch at " + index);
		}

		for (final int size : new int[] {31, 32, 33, 64}) {
			ArrayList<ActiveStatusEntry> entries = new ArrayList<ActiveStatusEntry>();
			for (int index = 0; index < size; index++) {
				entries.add(item(String.format("future:%02d", index), index, index + 1));
			}
			ActiveStatusInventory inventory = ActiveStatusInventory.select(entries);
			check(inventory.getVisible().size() == Math.min(32, size),
				"visible bound drift at " + size);
			check(inventory.getOverflowCount() == Math.max(0, size - 32),
				"overflow drift at " + size);
			check(entries.size() == size, "selection removed gameplay state");
		}
		ArrayList<ActiveStatusEntry> sixtyFive = new ArrayList<ActiveStatusEntry>();
		for (int index = 0; index < 65; index++) {
			sixtyFive.add(item("future:" + index, index, 10));
		}
		reject(new Action() { public void run() {
			ActiveStatusInventory.select(sixtyFive);
		} }, "65-entry collection must be rejected");

		List<ActiveStatusEntry> timersA = Arrays.asList(
			item("potion:warrior", 1, 1), item("potion:brawn", 2, 999));
		List<ActiveStatusEntry> timersB = Arrays.asList(
			item("potion:warrior", 1, 999), item("potion:brawn", 2, 1));
		check(ActiveStatusInventory.select(timersA).getVisible().get(0).getStableKey()
			.equals(ActiveStatusInventory.select(timersB).getVisible().get(0).getStableKey()),
			"timer changes reordered statuses");

		ActiveStatusEntry cleric = ActiveStatusEntry.cleric("cleric:protection", 5,
			3300, 30, 3, ActiveStatusEntry.CounterKind.CHARGES, 6);
		check(cleric.getRank() == 3 && cleric.getRemainingCounter() == 6,
			"Cleric counter snapshot drift");
		reject(new Action() { public void run() {
			ActiveStatusEntry.cleric("cleric:bad", 5, 3300, 30, 3,
				ActiveStatusEntry.CounterKind.NONE, 1);
		} }, "NONE counter accepted a value");
	}
}
