package orsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Defensive decoder for opcode 152's legacy prefix and optional v1 trailer. */
public final class ActiveStatusPacketDecoder {
	public static final int MAX_VISIBLE = 32;
	public static final int EXTENSION_VERSION = 1;
	private static final int PREFIX_ENTRY_BYTES = 6;
	private static final int EXTENSION_ENTRY_BYTES = 7;

	private ActiveStatusPacketDecoder() {
	}

	public static DecodedSnapshot decode(byte[] payload) {
		if (payload == null || payload.length < 1) {
			return DecodedSnapshot.empty();
		}
		Cursor cursor = new Cursor(payload);
		int count = cursor.readUnsignedByte();
		if (count < 0 || count > MAX_VISIBLE
				|| payload.length < 1 + count * PREFIX_ENTRY_BYTES) {
			return DecodedSnapshot.empty();
		}
		ArrayList<Entry> prefix = new ArrayList<Entry>(count);
		for (int index = 0; index < count; index++) {
			int iconItemId = cursor.readUnsignedShort();
			int remainingSeconds = cursor.readInt();
			if (iconItemId < 0 || remainingSeconds <= 0) {
				return DecodedSnapshot.empty();
			}
			prefix.add(Entry.prefix(iconItemId, remainingSeconds));
		}
		if (cursor.remaining() == 0) {
			return new DecodedSnapshot(prefix, 0, false);
		}
		if (cursor.remaining() < 2) {
			return new DecodedSnapshot(prefix, 0, false);
		}
		int overflow = cursor.readUnsignedShort();
		if (cursor.remaining() == 0) {
			return new DecodedSnapshot(prefix, overflow, false);
		}
		int requiredTrailerBytes = 2 + count * EXTENSION_ENTRY_BYTES;
		if (cursor.remaining() != requiredTrailerBytes) {
			return new DecodedSnapshot(prefix, overflow, false);
		}
		int version = cursor.readUnsignedByte();
		int trailerCount = cursor.readUnsignedByte();
		if (version != EXTENSION_VERSION || trailerCount != count) {
			return new DecodedSnapshot(prefix, overflow, false);
		}
		ArrayList<Entry> enriched = new ArrayList<Entry>(count);
		for (int index = 0; index < count; index++) {
			int identityKind = cursor.readUnsignedByte();
			int stableIdentity = cursor.readUnsignedShort();
			int rank = cursor.readUnsignedByte();
			int counterKind = cursor.readUnsignedByte();
			int remainingCounter = cursor.readUnsignedShort();
			Entry base = prefix.get(index);
			if (!validTrailerRecord(base.iconItemId, identityKind, stableIdentity,
					rank, counterKind, remainingCounter)) {
				return new DecodedSnapshot(prefix, overflow, false);
			}
			enriched.add(new Entry(base.iconItemId, base.remainingSeconds,
				identityKind, stableIdentity, rank, counterKind, remainingCounter));
		}
		return new DecodedSnapshot(enriched, overflow, true);
	}

	public static DecodedSnapshot legacySnapshot(int[] itemIds,
			int[] remainingSeconds, int overflowCount) {
		if (itemIds == null || remainingSeconds == null) {
			return DecodedSnapshot.empty();
		}
		int count = Math.min(MAX_VISIBLE, Math.min(itemIds.length, remainingSeconds.length));
		ArrayList<Entry> entries = new ArrayList<Entry>(count);
		for (int index = 0; index < count; index++) {
			if (itemIds[index] >= 0 && itemIds[index] <= 65_535
					&& remainingSeconds[index] > 0) {
				entries.add(Entry.prefix(itemIds[index], remainingSeconds[index]));
			}
		}
		return new DecodedSnapshot(entries, Math.max(0, overflowCount), false);
	}

	private static boolean validTrailerRecord(int iconItemId, int identityKind,
			int stableIdentity, int rank, int counterKind, int remainingCounter) {
		if (identityKind == 0) {
			return stableIdentity == iconItemId && rank == 0
				&& counterKind == 0 && remainingCounter == 0;
		}
		if (identityKind != 1 || rank <= 0 || counterKind < 0 || counterKind > 2) {
			return false;
		}
		return counterKind == 0 ? remainingCounter == 0 : remainingCounter > 0;
	}

	public static final class DecodedSnapshot {
		private final List<Entry> entries;
		private final int overflowCount;
		private final boolean enriched;

		private DecodedSnapshot(List<Entry> entries, int overflowCount, boolean enriched) {
			this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
			this.overflowCount = overflowCount;
			this.enriched = enriched;
		}

		public static DecodedSnapshot empty() {
			return new DecodedSnapshot(Collections.<Entry>emptyList(), 0, false);
		}
		public List<Entry> getEntries() { return entries; }
		public int getOverflowCount() { return overflowCount; }
		public boolean isEnriched() { return enriched; }
	}

	public static final class Entry {
		private final int iconItemId;
		private final int remainingSeconds;
		private final int identityKind;
		private final int stableIdentity;
		private final int rank;
		private final int counterKind;
		private final int remainingCounter;

		private Entry(int iconItemId, int remainingSeconds, int identityKind,
				int stableIdentity, int rank, int counterKind, int remainingCounter) {
			this.iconItemId = iconItemId;
			this.remainingSeconds = remainingSeconds;
			this.identityKind = identityKind;
			this.stableIdentity = stableIdentity;
			this.rank = rank;
			this.counterKind = counterKind;
			this.remainingCounter = remainingCounter;
		}

		private static Entry prefix(int itemId, int remainingSeconds) {
			return new Entry(itemId, remainingSeconds, 0, itemId, 0, 0, 0);
		}

		public int getIconItemId() { return iconItemId; }
		public int getRemainingSeconds() { return remainingSeconds; }
		public int getIdentityKind() { return identityKind; }
		public int getStableIdentity() { return stableIdentity; }
		public int getRank() { return rank; }
		public int getCounterKind() { return counterKind; }
		public int getRemainingCounter() { return remainingCounter; }
	}

	private static final class Cursor {
		private final byte[] bytes;
		private int offset;

		private Cursor(byte[] bytes) { this.bytes = bytes; }
		private int remaining() { return bytes.length - offset; }
		private int readUnsignedByte() { return bytes[offset++] & 0xff; }
		private int readUnsignedShort() {
			return readUnsignedByte() << 8 | readUnsignedByte();
		}
		private int readInt() {
			return readUnsignedByte() << 24 | readUnsignedByte() << 16
				| readUnsignedByte() << 8 | readUnsignedByte();
		}
	}
}
