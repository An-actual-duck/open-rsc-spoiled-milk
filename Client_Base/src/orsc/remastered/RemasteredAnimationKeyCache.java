package orsc.remastered;

import com.openrsc.client.entityhandling.defs.extras.AnimationDef;

import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * Resolver-owned cache for the stable remastered key of an animation frame.
 *
 * <p>Animation definitions are shared runtime objects, so identity is the
 * correct cache boundary. Their name and category remain public legacy
 * fields, however, so an entry is replaced if either source value changes.
 * Frame storage grows only as needed and unusual offsets above the cache
 * limit retain the original uncached behavior.</p>
 *
 * <p>This class is intentionally not synchronized. Its owner,
 * {@link RemasteredSpriteResolver}, accesses it under the resolver monitor.</p>
 */
final class RemasteredAnimationKeyCache {
	private static final int INITIAL_FRAME_CAPACITY = 18;
	private static final int MAX_CACHED_FRAME = 255;

	private final IdentityHashMap<AnimationDef, Entry> entries =
		new IdentityHashMap<AnimationDef, Entry>();
	private int cachedSlotCount;

	String key(AnimationDef animation, int frame) {
		if (animation == null || frame < 0) {
			return null;
		}
		if (frame > MAX_CACHED_FRAME) {
			return RemasteredSpriteKey.forAnimation(animation, frame);
		}

		String category = animation.category;
		String name = animation.getName();
		Entry entry = entries.get(animation);
		if (entry == null || !entry.matches(category, name)) {
			if (entry != null) {
				cachedSlotCount -= entry.cachedSlotCount;
			}
			entry = new Entry(category, name);
			entries.put(animation, entry);
		}
		entry.ensureCapacity(frame + 1);
		if (!entry.computed[frame]) {
			entry.keys[frame] = RemasteredSpriteKey.forAnimation(animation, frame);
			entry.computed[frame] = true;
			entry.cachedSlotCount++;
			cachedSlotCount++;
		}
		return entry.keys[frame];
	}

	int definitionCount() {
		return entries.size();
	}

	int cachedSlotCount() {
		return cachedSlotCount;
	}

	private static boolean same(String first, String second) {
		return first == null ? second == null : first.equals(second);
	}

	private static final class Entry {
		private final String category;
		private final String name;
		private String[] keys = new String[INITIAL_FRAME_CAPACITY];
		private boolean[] computed = new boolean[INITIAL_FRAME_CAPACITY];
		private int cachedSlotCount;

		private Entry(String category, String name) {
			this.category = category;
			this.name = name;
		}

		private boolean matches(String currentCategory, String currentName) {
			return same(category, currentCategory) && same(name, currentName);
		}

		private void ensureCapacity(int requiredCapacity) {
			if (requiredCapacity <= keys.length) {
				return;
			}
			int capacity = keys.length;
			while (capacity < requiredCapacity) {
				capacity = Math.min(MAX_CACHED_FRAME + 1, capacity * 2);
			}
			keys = Arrays.copyOf(keys, capacity);
			computed = Arrays.copyOf(computed, capacity);
		}
	}
}
