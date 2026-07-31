package com.openrsc.server.model;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

import java.util.Objects;

/** Shared destination policy for NPC movement selection and execution. */
public final class NpcMovementBoundary {
	private NpcMovementBoundary() {
	}

	/**
	 * Returns whether a mob may select or execute the destination under the
	 * movement bounds currently enforced for that mob.
	 *
	 * <p>Players and summoned NPCs are intentionally exempt. Roaming NPCs use
	 * their inset roam bounds, while chasing or fighting NPCs use the complete
	 * configured {@link NPCLoc} combat bounds.</p>
	 */
	public static boolean allows(final Mob mob, final Point destination) {
		Objects.requireNonNull(mob, "mob");
		Objects.requireNonNull(destination, "destination");
		if (!mob.isNpc()) {
			return true;
		}

		Npc npc = (Npc) mob;
		if (Summoning.isSummon(npc)) {
			return true;
		}
		if (!npc.inCombat() && !npc.isChasing()) {
			return npc.inRoamBounds(destination);
		}

		NPCLoc loc = npc.getLoc();
		return destination.inBounds(
			loc.minX(), loc.minY(), loc.maxX(), loc.maxY());
	}
}
