package com.openrsc.server.event.rsc.handler;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class GameTickEventStore {
    private static final Logger LOGGER = LogManager.getLogger(GameTickEventStore.class);

    private final Object LOCK = new Object();

    /**
     * Tracks whether the event should be added using the criteria determined by the key
     */
    private final Map<GameTickKey, GameTickEvent> events = new LinkedHashMap<>();

    /**
     * Process-local identity for one accepted stay in this store. This is not
     * the event UUID or store key and is removed with the registration.
     */
    private final Map<GameTickEvent, Long> registrationSequences =
        new IdentityHashMap<>();
    private long nextRegistrationSequence;

    /**
     * Indexes events by username for fast-lookup during individual player tick processing
     */
    private final Multimap<Long, GameTickEvent> byUsernameHash = ArrayListMultimap.create();

    /**
     * Keep the non player events ready
     */
    private final Map<GameTickKey, GameTickEvent> nonPlayerEvents = new LinkedHashMap<>();

    /**
     * Index by event type to quickly know if a certain event type exists (i.e. instanceof)
     */
    private final Multimap<Key<? extends GameTickEvent>, GameTickEvent> byType = LinkedHashMultimap.create();

    public boolean add(GameTickEvent event) {
        synchronized (LOCK) {
            final GameTickKey eventKey = getKey(event);

            if (events.containsKey(eventKey)) {
                // We already have an instance of this event
                // LOGGER.warn("Tried to add duplicate event: {}", eventKey);
                return false;
            }

            registerAccepted(eventKey, event);
            return true;
        }
    }

	public boolean addOrUpdate(GameTickEvent event) {
		{
			synchronized (LOCK) {
				final GameTickKey eventKey = getKey(event);
	
				if (events.containsKey(eventKey)) {
					GameTickEvent existingEvent = events.get(eventKey);
					if (existingEvent.isRunning()) {
						// We already have an instance of this event that is running
						// LOGGER.warn("Tried to add duplicate event: {}", eventKey);
						return false;
					}
					// Remove existing stopped event
					// LOGGER.warn("Replaced stopped event: {}", eventKey);
					remove(existingEvent);
				}
	
				registerAccepted(eventKey, event);
				return true;
			}
		}
	}

    public boolean eventIsContained(GameTickEvent event) {
		final GameTickKey eventKey = getKey(event);
		return events.containsKey(eventKey);
	}

    public void remove(GameTickEvent event) {
        synchronized (LOCK) {
            final GameTickKey eventKey = getKey(event);

            if(!events.containsKey(eventKey)) {
                // Event does not exist
                LOGGER.warn("Failed to remove event: {}", eventKey);
                return;
            }

            GameTickEvent registeredEvent = events.get(eventKey);
            events.remove(eventKey);
            byType.remove(
                Key.get(registeredEvent.getClass()), registeredEvent);
            if(isPlayerOwner(registeredEvent)) {
                byUsernameHash.remove(
                    ((Player) registeredEvent.getOwner()).getUsernameHash(),
                    registeredEvent);
            } else {
                nonPlayerEvents.remove(eventKey);
            }
            registrationSequences.remove(registeredEvent);
        }
    }

    public Collection<GameTickEvent> getPlayerEvents(Player player) {
        synchronized (LOCK) {
            return new ArrayList<>(byUsernameHash.get(player.getUsernameHash()));
        }
    }

    public Collection<GameTickEvent> getPlayerEvents(Long usernameHash) {
        synchronized (LOCK) {
            return new ArrayList<>(byUsernameHash.get(usernameHash));
        }
    }

    public Collection<GameTickEvent> getNonPlayerEvents() {
        synchronized (LOCK) {
            return new ArrayList<>(nonPlayerEvents.values());
        }
    }

    public Collection<GameTickEvent> getEvents(Class<? extends GameTickEvent> type) {
        synchronized (LOCK) {
            return new ArrayList<>(byType.get(Key.get(type)));
        }
    }

    public boolean hasEvent(Class<? extends GameTickEvent> eventType) {
        synchronized (LOCK) {
            return byType.containsKey(Key.get(eventType));
        }
    }

    public Collection<GameTickEvent> getTrackedEvents() {
        synchronized (LOCK) {
            return new ArrayList<>(events.values());
        }
    }

    /** One atomic scheduler-local order/identity view for read-only capture. */
    List<RegisteredEvent> getTrackedEventRegistrations() {
        synchronized (LOCK) {
            List<RegisteredEvent> registrations =
                new ArrayList<>(events.size());
            for (GameTickEvent event : events.values()) {
                Long sequence = registrationSequences.get(event);
                if (sequence == null) {
                    throw new IllegalStateException(
                        "Tracked event has no registration identity");
                }
                registrations.add(new RegisteredEvent(
                    event, sequence.longValue()));
            }
            return Collections.unmodifiableList(registrations);
        }
    }

    private void registerAccepted(
        final GameTickKey eventKey,
        final GameTickEvent event) {
        if (nextRegistrationSequence == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "Event registration identity exhausted");
        }
        long registrationSequence = nextRegistrationSequence + 1L;
        events.put(eventKey, event);
        byType.put(Key.get(event.getClass()), event);
        if (isPlayerOwner(event)) {
            byUsernameHash.put(
                ((Player) event.getOwner()).getUsernameHash(), event);
        } else {
            nonPlayerEvents.put(eventKey, event);
        }
        registrationSequences.put(event, Long.valueOf(registrationSequence));
        nextRegistrationSequence = registrationSequence;
    }

    /** Internal handle/identity pair; no reference crosses diagnostics. */
    static final class RegisteredEvent {
        private final GameTickEvent event;
        private final long registrationSequence;

        private RegisteredEvent(
            final GameTickEvent event,
            final long registrationSequence) {
            this.event = event;
            this.registrationSequence = registrationSequence;
        }

        GameTickEvent getEvent() {
            return event;
        }

        long getRegistrationSequence() {
            return registrationSequence;
        }
    }

    private boolean isPlayerOwner(GameTickEvent event) {
        return event.hasOwner() && event.getOwner() instanceof Player;
    }

    private GameTickKey getKey(GameTickEvent event) {
        if(event instanceof PluginTickEvent) {
            return new GameTickKey((PluginTickEvent) event);
        }
        return new GameTickKey(event);
    }

    class GameTickKey {
        private final String name;
        private final Boolean isPlayerEvent;
        private final UUID ownerUUID;

        private GameTickKey(GameTickEvent event) {
            this.name = resolveName(event);
            this.isPlayerEvent = isPlayerOwner(event);
            this.ownerUUID = resolveUUID(event);
        }

        private String resolveName(GameTickEvent event) {
            if(event instanceof PluginTickEvent) {
                return ((PluginTickEvent) event).getPluginName();
            }

            return String.valueOf(event.getClass());
        }

        private UUID resolveUUID(GameTickEvent event) {
            DuplicationStrategy strategy = event.getDuplicationStrategy();
            if(strategy == DuplicationStrategy.ALLOW_MULTIPLE) {
                return event.getUUID();
            } else if(strategy == DuplicationStrategy.ONE_PER_SERVER) {
                return UUID.nameUUIDFromBytes(resolveName(event).getBytes());
            } else if(strategy == DuplicationStrategy.ONE_PER_MOB) {
                return Optional.ofNullable(event.getOwner())
                        .map(Mob::getUUID)
                        .orElse(event.getUUID());
            }

            throw new IllegalArgumentException(
                    MessageFormat.format("Unknown duplication strategy {0}", strategy)
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GameTickKey that = (GameTickKey) o;
            return new EqualsBuilder()
                    .append(name, that.name)
                    .append(isPlayerEvent, that.isPlayerEvent)
                    .append(ownerUUID, that.ownerUUID).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(name)
                    .append(isPlayerEvent)
                    .append(ownerUUID)
                    .toHashCode();
        }
    }
}
