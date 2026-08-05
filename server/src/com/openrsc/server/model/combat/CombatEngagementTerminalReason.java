package com.openrsc.server.model.combat;

/** Stable reason why an offensive direction stopped being active. */
public enum CombatEngagementTerminalReason {
	EVENT_ENDED,
	RETARGETED,
	MANUAL_DISENGAGE,
	LEASH,
	DEATH,
	LOGOUT,
	TELEPORT,
	DOMAIN_TRANSFER,
	DESPAWN,
	RESPAWN,
	SESSION_CHANGED,
	LIFECYCLE_CHANGED,
	STALE_CALLBACK,
	CONTENT_REJECTED,
	LEGACY_RESET,
	AUDIT_REPAIR
}
