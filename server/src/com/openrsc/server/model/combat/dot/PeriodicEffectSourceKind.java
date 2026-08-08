package com.openrsc.server.model.combat.dot;

/**
 * The durable category of an actor or non-actor that applied a periodic
 * effect. This is provenance only; it is not a reward or live-entity handle.
 */
public enum PeriodicEffectSourceKind {
	PLAYER,
	NPC,
	ENVIRONMENT,
	SCRIPT
}
