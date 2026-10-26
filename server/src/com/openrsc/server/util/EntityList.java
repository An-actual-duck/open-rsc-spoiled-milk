package com.openrsc.server.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.openrsc.server.model.entity.Entity;

import java.text.MessageFormat;
import java.util.AbstractCollection;
import java.util.Arrays;
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
        return Arrays.stream(entities)
                .filter(Objects::nonNull)
                .map(entity -> (T) entity)
                .collect(ImmutableList.toImmutableList())
                .iterator();
    }

    /**
     * Iterates the live backing array directly without creating a snapshot.
     * Use this only in hot paths where same-tick visibility of list mutations is acceptable.
     */
    @SuppressWarnings("unchecked")
    public void forEachLive(final Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        final Object[] liveEntities = entities;
        for (int index = 0; index < liveEntities.length; index++) {
            final Object entity = liveEntities[index];
            if (entity != null) {
                action.accept((T) entity);
            }
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
