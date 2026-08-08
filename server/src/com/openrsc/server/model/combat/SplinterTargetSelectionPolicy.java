package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Current random-single NPC selection policy for Splinter.
 *
 * <p>The caller continues to own the proc roll before selection. A successful
 * proc takes the player's current view, excludes the primary and current
 * invalid candidates, and consumes exactly one index draw only when at least
 * one candidate remains. View membership owns world/layer isolation; the
 * current radius policy intentionally performs no line-of-effect check.</p>
 */
public final class SplinterTargetSelectionPolicy {
	public static final int RADIUS = 2;

	private SplinterTargetSelectionPolicy() {
	}

	public static Npc select(final Player owner, final Mob primaryTarget,
			final GameRandom random) {
		if (!primaryTarget.isNpc()) {
			return null;
		}
		final List<Npc> candidates = new ArrayList<>();
		for (Npc npc : owner.getViewArea().getNpcsInView()) {
			if (npc != null && npc != primaryTarget && !npc.isRemoved()
					&& !Summoning.isSummon(npc)
					&& npc.getDef().isAttackable()
					&& npc.getSkills().getLevel(Skill.HITS.id()) > 0
					&& npc.getLocation().withinRange(
						primaryTarget.getLocation(), RADIUS)) {
				candidates.add(npc);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		return candidates.get(Objects.requireNonNull(random, "random")
			.nextInt(candidates.size()));
	}
}
