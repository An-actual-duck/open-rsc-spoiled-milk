package com.openrsc.server.model.states;

import com.openrsc.server.model.entity.Mob;

/**
 * Simplified hostility state system replacing complex combat states
 */
public class HostileState {
    private boolean isHostile;
    private Mob target;
    private long hostilityTimer;
    private HostilityType hostilityType;

    public enum HostilityType {
        ATTACKED,        // Player was attacked by this mob
        NATURAL,        // Naturally aggressive NPC
        LEVEL_RANGE,    // Hostile due to level range (wilderness, etc.)
        PROVOKED,       // Player attacked this mob first
        RETALIATION     // Auto-retaliation after being attacked
    }

    public HostileState() {
        this.isHostile = false;
        this.target = null;
        this.hostilityTimer = 0;
        this.hostilityType = null;
    }

    /**
     * Set this mob as hostile toward a target
     */
    public void setHostile(Mob target, HostilityType type) {
        this.isHostile = true;
        this.target = target;
        this.hostilityType = type;
        this.hostilityTimer = System.currentTimeMillis();
    }

    /**
     * Clear hostility - mob is no longer hostile
     */
    public void clearHostility() {
        this.isHostile = false;
        this.target = null;
        this.hostilityType = null;
        this.hostilityTimer = 0;
    }

    /**
     * Check if this mob is currently hostile toward anyone
     */
    public boolean isHostile() {
        return isHostile && target != null;
    }

    /**
     * Check if this mob is hostile toward a specific target
     */
    public boolean isHostileToward(Mob potentialTarget) {
        return isHostile && target != null && target.equals(potentialTarget);
    }

    /**
     * Get the current hostility target
     */
    public Mob getTarget() {
        return target;
    }

    /**
     * Get the type of hostility
     */
    public HostilityType getHostilityType() {
        return hostilityType;
    }

    /**
     * Get when hostility started
     */
    public long getHostilityTimer() {
        return hostilityTimer;
    }

    /**
     * Check if hostility has expired (for timeout-based hostility)
     */
    public boolean hasHostilityExpired(long timeoutMs) {
        return !isHostile || (System.currentTimeMillis() - hostilityTimer) > timeoutMs;
    }

    /**
     * Update hostility target (for switching targets)
     */
    public void updateTarget(Mob newTarget, HostilityType newType) {
        this.target = newTarget;
        this.hostilityType = newType;
        this.hostilityTimer = System.currentTimeMillis();
    }
}
