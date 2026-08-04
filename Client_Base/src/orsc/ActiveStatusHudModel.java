package orsc;

import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded client presentation state for mixed potion and Cleric statuses. */
public final class ActiveStatusHudModel {
	private List<StoredEntry> entries = Collections.emptyList();
	private int overflowCount;

	public synchronized void replace(ActiveStatusPacketDecoder.DecodedSnapshot decoded,
			ClericSpellbookCatalog catalog, long nowMillis) {
		if (decoded == null || catalog == null) {
			clear();
			return;
		}
		boolean useEnrichment = decoded.isEnriched()
			&& validateCatalogEnrichment(decoded.getEntries(), catalog);
		ArrayList<StoredEntry> next = new ArrayList<StoredEntry>(decoded.getEntries().size());
		for (ActiveStatusPacketDecoder.Entry entry : decoded.getEntries()) {
			long durationMillis = saturatingMultiply(entry.getRemainingSeconds(), 1_000L);
			long expiresAtMillis = saturatingAdd(nowMillis, durationMillis);
			if (expiresAtMillis <= nowMillis) {
				continue;
			}
			ClericSpellDef spell = useEnrichment && entry.getIdentityKind() == 1
				? catalog.get(entry.getStableIdentity()) : null;
			next.add(new StoredEntry(entry, expiresAtMillis, spell));
		}
		entries = Collections.unmodifiableList(next);
		overflowCount = Math.max(0, decoded.getOverflowCount());
	}

	private boolean validateCatalogEnrichment(List<ActiveStatusPacketDecoder.Entry> decoded,
			ClericSpellbookCatalog catalog) {
		for (ActiveStatusPacketDecoder.Entry entry : decoded) {
			if (entry.getIdentityKind() == 0) {
				continue;
			}
			ClericSpellDef spell = catalog.get(entry.getStableIdentity());
			ClericEffectRankDef rank = spell == null ? null : spell.getEffectRank(entry.getRank());
			if (spell == null || rank == null
					|| spell.getSpellbookIconItemId() != entry.getIconItemId()
					|| rank.getCounterKind() != entry.getCounterKind()
					|| entry.getRemainingCounter() > rank.getInitialCounter()) {
				return false;
			}
		}
		return true;
	}

	public synchronized Snapshot snapshot(long nowMillis) {
		compact(nowMillis);
		ArrayList<Row> rows = new ArrayList<Row>(entries.size());
		for (StoredEntry stored : entries) {
			long seconds = Math.max(0L,
				(stored.expiresAtMillis - nowMillis + 999L) / 1_000L);
			rows.add(new Row(stored.packet.getIconItemId(), seconds,
				stored.packet.getIdentityKind(), stored.packet.getStableIdentity(),
				stored.packet.getRank(), stored.packet.getCounterKind(),
				stored.packet.getRemainingCounter(), stored.spell));
		}
		return new Snapshot(rows, overflowCount);
	}

	private void compact(long nowMillis) {
		ArrayList<StoredEntry> retained = null;
		for (int index = 0; index < entries.size(); index++) {
			StoredEntry entry = entries.get(index);
			if (entry.expiresAtMillis <= nowMillis) {
				if (retained == null) {
					retained = new ArrayList<StoredEntry>(entries.subList(0, index));
				}
			} else if (retained != null) {
				retained.add(entry);
			}
		}
		if (retained != null) {
			entries = Collections.unmodifiableList(retained);
		}
	}

	public synchronized void clear() {
		entries = Collections.emptyList();
		overflowCount = 0;
	}

	private static long saturatingMultiply(long left, long right) {
		if (left > Long.MAX_VALUE / right) {
			return Long.MAX_VALUE;
		}
		return left * right;
	}

	private static long saturatingAdd(long left, long right) {
		if (right > Long.MAX_VALUE - left) {
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	private static String roman(int rank) {
		String[] thousands = {"", "M", "MM"};
		String[] hundreds = {"", "C", "CC"};
		String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
		String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
		return thousands[Math.min(2, rank / 1_000)]
			+ hundreds[rank / 100 % 10]
			+ tens[rank / 10 % 10]
			+ ones[rank % 10];
	}

	private static final class StoredEntry {
		private final ActiveStatusPacketDecoder.Entry packet;
		private final long expiresAtMillis;
		private final ClericSpellDef spell;

		private StoredEntry(ActiveStatusPacketDecoder.Entry packet,
				long expiresAtMillis, ClericSpellDef spell) {
			this.packet = packet;
			this.expiresAtMillis = expiresAtMillis;
			this.spell = spell;
		}
	}

	public static final class Snapshot {
		private final List<Row> rows;
		private final int overflowCount;

		private Snapshot(List<Row> rows, int overflowCount) {
			this.rows = Collections.unmodifiableList(rows);
			this.overflowCount = overflowCount;
		}

		public List<Row> getRows() { return rows; }
		public int getOverflowCount() { return overflowCount; }
	}

	public static final class Row {
		private final int iconItemId;
		private final long remainingSeconds;
		private final int identityKind;
		private final int stableIdentity;
		private final int rank;
		private final int counterKind;
		private final int remainingCounter;
		private final ClericSpellDef clericSpell;

		private Row(int iconItemId, long remainingSeconds, int identityKind,
				int stableIdentity, int rank, int counterKind, int remainingCounter,
				ClericSpellDef clericSpell) {
			this.iconItemId = iconItemId;
			this.remainingSeconds = remainingSeconds;
			this.identityKind = identityKind;
			this.stableIdentity = stableIdentity;
			this.rank = rank;
			this.counterKind = counterKind;
			this.remainingCounter = remainingCounter;
			this.clericSpell = clericSpell;
		}

		public int getIconItemId() { return iconItemId; }
		public long getRemainingSeconds() { return remainingSeconds; }
		public boolean isCleric() { return identityKind == 1 && clericSpell != null; }
		public int getStableIdentity() { return stableIdentity; }
		public int getRank() { return rank; }
		public int getRemainingCounter() { return remainingCounter; }
		public String getCounterBadge() {
			return counterKind == 1 ? remainingCounter + "H"
				: counterKind == 2 ? remainingCounter + "P" : "";
		}
		public String getClericHoverText() {
			if (!isCleric()) {
				return null;
			}
			ClericEffectRankDef effectRank = clericSpell.getEffectRank(rank);
			String prefix = clericSpell.getName() + " " + roman(rank) + " — ";
			String counter = counterKind == 1
				? " — " + remainingCounter + " protected hits remaining"
				: counterKind == 2
					? " — " + remainingCounter + " healing pulses remaining" : "";
			switch (effectRank.getPresentationKind()) {
				case 1:
					return prefix + effectRank.getPrimaryMagnitude() + " Hits per pulse" + counter;
				case 2:
					return prefix + effectRank.getPrimaryMagnitude()
						+ "% chance to raise offense roll by " + effectRank.getSecondaryMagnitude();
				case 3:
					return prefix + effectRank.getPrimaryMagnitude() + "% reduction" + counter;
				case 4:
					return prefix + effectRank.getPrimaryMagnitude() + "% direct damage";
				case 5:
					return prefix + effectRank.getPrimaryMagnitude() + "% direct damage reflected";
				case 6:
					return prefix + effectRank.getPrimaryMagnitude() + "% lifesteal until "
						+ effectRank.getSecondaryMagnitude() + "% Hits";
				case 7:
				default:
					return prefix + effectRank.getPrimaryMagnitude()
						+ "% faster passive regeneration";
			}
		}
		public String getClericIconAssetKey() {
			return isCleric() ? clericSpell.getIconAssetKey() : "";
		}
	}
}
