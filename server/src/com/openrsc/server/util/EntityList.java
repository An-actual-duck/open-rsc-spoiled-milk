package com.openrsc.server.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.openrsc.server.model.entity.Entity;

import java.text.MessageFormat;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("ConstantConditions")
public class EntityList<T extends Entity> extends AbstractCollection<T> {

    public static final int DEFAULT_CAPACITY = 2000;
    private Queue<Integer> priorityIdPool;
    private ConcurrentHashMap<Integer, Object> occupiedIndices;
    private final int capacity;
    private Object[] entities;
    private volatile int scanUpperBound;
    private final Map<T, Integer> registrations = new IdentityHashMap<>();

    public EntityList() {
        this(DEFAULT_CAPACITY);
    }

    public EntityList(final int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
		this.capacity = capacity;
		initialize();
    }

	private void initialize() {
		this.priorityIdPool = IntStream.range(0, capacity)
			.boxed()
			.collect(Collectors.toCollection(() -> new PriorityQueue<>(capacity)));
		this.occupiedIndices = new ConcurrentHashMap<>();
		this.entities = new Object[capacity];
        this.scanUpperBound = 0;
	}

    public synchronized boolean add(final T entity) {
        Objects.requireNonNull(entity, "entity");
        if (registrations.containsKey(entity)) return false;
        if (size() >= capacity) {
            throw new IllegalStateException(
                    MessageFormat.format(
                            "Attempt to add entity would exceed capacity of {0}",
                            capacity
                    )
            );
        }

        int nextId = priorityIdPool.poll();
        entities[nextId] = entity;
        entity.setIndex(nextId);
        occupiedIndices.put(nextId, new Object());
        registrations.put(entity, nextId);
        if (nextId >= scanUpperBound) scanUpperBound = nextId + 1;
        return true;
    }

    @Override
    public synchronized boolean contains(final Object entity) {
        return registrations.containsKey(entity);
    }

    public int count() {
        return getOccupiedIndices().size();
    }

    public int size() {
        return getOccupiedIndices().size();
    }

    /** Read-only registration budget, including any indices absent from the pool. */
    public synchronized int getAvailableCapacity() {
        return Math.max(0, Math.min(capacity - size(), priorityIdPool.size()));
    }

    @SuppressWarnings("unchecked")
    public synchronized T get(final int index) {
        if (index < 0 || index >= capacity) return null;
        if(entities[index] != null) {
            return (T) entities[index];
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public synchronized Iterator<T> iterator() {
        final ImmutableList.Builder<T> snapshot = ImmutableList.builderWithExpectedSize(size());
        for (int index = 0; index < scanUpperBound; index++) {
            if (entities[index] != null) snapshot.add((T) entities[index]);
        }
        return snapshot.build().iterator();
    }

    /**
     * Iterates the live backing array directly without creating a snapshot.
     * Ascending index order. Removals before visitation are skipped; additions to
     * higher unvisited indexes are visible in this pass. Lower indexes wait for
     * the next pass. clear() retains the captured array for this pass. Like the
     * original traversal, callers own tick-thread confinement; this method does
     * not promise an atomic concurrent snapshot. iterator() supplies a snapshot.
     */
    @SuppressWarnings("unchecked")
    public void forEachLive(final Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        final Object[] liveEntities = entities;
        // Retain the captured array's initial range across clear(), as before.
        // Higher-slot registrations during a callback remain visible this pass.
        int limit = scanUpperBound;
        int index = 0;
        while (true) {
            for (; index < limit; index++) {
                final Object entity = liveEntities[index];
                if (entity != null) action.accept((T) entity);
            }
            // A callback can add above the original bound. Read it once per range,
            // rather than paying for a volatile load on every occupied slot.
            final int updatedLimit = scanUpperBound;
            if (updatedLimit <= limit) break;
            limit = updatedLimit;
        }
    }

    public synchronized void clear() {
        for (T entity : registrations.keySet()) entity.setIndex(-1);
        registrations.clear();
        initialize();
    }

    @SuppressWarnings("unchecked")
    public synchronized T remove(final int index) {
        if (index < 0 || index >= capacity) return null;
        T entity = (T) entities[index];
        if (entity == null) return null;
        entities[index] = null;
        occupiedIndices.remove(index);
        registrations.remove(entity);
        if (index + 1 == scanUpperBound) {
            int limit = index;
            while (limit > 0 && entities[limit - 1] == null) limit--;
            scanUpperBound = limit;
        }
        entity.setIndex(-1);
        priorityIdPool.offer(index);
        return entity;
    }

    public synchronized void remove(final T entity) {
        // Entity equality (notably player username equality) is not registration ownership.
        final Integer index = registrations.get(entity);
        if (index != null && entities[index] == entity) remove(index.intValue());
    }

    private ConcurrentHashMap.KeySetView<Integer, Object> getOccupiedIndices() {
        return occupiedIndices.keySet();
    }

    public Set<Integer> indices() {
        return ImmutableSet.copyOf(getOccupiedIndices());
    }
}
