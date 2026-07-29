package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Immutable directed projection of an object-command telepoint. */
public final class WorldObjectTransition {
	private final WorldLocation source;
	private final WorldLocation destination;
	private final String command;

	public WorldObjectTransition(
		WorldLocation source,
		WorldLocation destination,
		String command) {
		this.source = Objects.requireNonNull(source, "source");
		this.destination = Objects.requireNonNull(destination, "destination");
		this.command = Objects.requireNonNull(command, "command");
	}

	public static WorldObjectTransition fromLegacyPoints(
		Point source,
		Point destination,
		String command) {
		return new WorldObjectTransition(
			LegacyPackedPointAdapter.fromLegacyPoint(source),
			LegacyPackedPointAdapter.fromLegacyPoint(destination),
			command);
	}

	public WorldLocation getSource() {
		return source;
	}

	public WorldLocation getDestination() {
		return destination;
	}

	public String getCommand() {
		return command;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldObjectTransition)) {
			return false;
		}
		WorldObjectTransition transition = (WorldObjectTransition) other;
		return source.equals(transition.source)
			&& destination.equals(transition.destination)
			&& command.equals(transition.command);
	}

	@Override
	public int hashCode() {
		int result = source.hashCode();
		result = 31 * result + destination.hashCode();
		result = 31 * result + command.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "WorldObjectTransition{source=" + source + ", destination=" + destination
			+ ", command='" + command + "'}";
	}
}
