package com.openrsc.server.model.entity.death;

/** Monotonic state for one reward-eligible mob death incarnation. */
public enum DeathLifecycleState {
	ALIVE,
	DYING,
	DEAD,
	RESPAWNING
}
