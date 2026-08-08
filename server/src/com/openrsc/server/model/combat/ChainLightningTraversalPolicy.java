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
 * Current target-enumeration policy for one chain-lightning hop.
 *
 * <p>Each hop takes a fresh owner-view snapshot, excludes only its current
 * anchor, and then draws one uniformly random candidate. Earlier living nodes
 * may therefore be selected again. View membership remains the spatial-domain
 * boundary, and the legacy radius check intentionally adds no line-of-effect
 * test. Proc chance, damage, projectiles, hop execution, and death remain with
 * the event owner.</p>
 */
public final class ChainLightningTraversalPolicy {
	public static final int MAX_HOPS = 3;
	public static final int RADIUS = 4;

	private ChainLightningTraversalPolicy() {
	}

	public static Npc selectNext(final Player owner, final Mob anchor,
			final GameRandom random) {
		if (anchor == null || !anchor.isNpc()) {
			return null;
		}
		final List<Npc> candidates = new ArrayList<>();
		for (Npc npc : owner.getViewArea().getNpcsInView()) {
			if (npc != null && npc != anchor && !npc.isRemoved()
					&& npc.getSkills().getLevel(Skill.HITS.id()) > 0
					&& npc.getDef().isAttackable()
					&& !Summoning.isSummon(npc)
					&& npc.withinRange(anchor.getLocation(), RADIUS)) {
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
