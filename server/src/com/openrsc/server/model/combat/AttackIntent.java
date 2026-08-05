package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Spells;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;

/** Immutable identity retained between an attack command and its commit. */
public final class AttackIntent {
	public enum Source {
		MANUAL,
		RETALIATION,
		COMPATIBILITY
	}

	public enum Channel {
		MELEE,
		RANGED,
		THROWING,
		MAGIC,
		AUTOCAST
	}

	private final long intentId;
	private final Player actor;
	private final Mob target;
	private final CombatParticipantSnapshot actorSnapshot;
	private final CombatParticipantSnapshot targetSnapshot;
	private final CombatStyle style;
	private final Channel channel;
	private final Source source;
	private final long commandTick;
	private final long expiresAfterTick;
	private final int rangeEquipmentId;
	private final int throwingEquipmentId;
	private final Spells spell;

	AttackIntent(final long intentId, final Player actor, final Mob target,
			final CombatStyle style, final Channel channel,
			final Source source, final long commandTick,
			final long expiresAfterTick, final Spells spell) {
		if (intentId <= 0L || actor == null || target == null || actor == target
			|| style == null || channel == null || source == null
			|| expiresAfterTick < commandTick) {
			throw new IllegalArgumentException("complete attack intent data is required");
		}
		this.intentId = intentId;
		this.actor = actor;
		this.target = target;
		this.actorSnapshot = CombatParticipantSnapshot.capture(actor);
		this.targetSnapshot = CombatParticipantSnapshot.capture(target);
		this.style = style;
		this.channel = channel;
		this.source = source;
		this.commandTick = commandTick;
		this.expiresAfterTick = expiresAfterTick;
		this.rangeEquipmentId = actor.getRangeEquip();
		this.throwingEquipmentId = actor.getThrowingEquip();
		this.spell = spell;
	}

	public long getIntentId() { return intentId; }
	public Player getActor() { return actor; }
	public Mob getTarget() { return target; }
	public CombatParticipantSnapshot getActorSnapshot() { return actorSnapshot; }
	public CombatParticipantSnapshot getTargetSnapshot() { return targetSnapshot; }
	public CombatStyle getStyle() { return style; }
	public Channel getChannel() { return channel; }
	public Source getSource() { return source; }
	public long getCommandTick() { return commandTick; }
	public long getExpiresAfterTick() { return expiresAfterTick; }
	public Spells getSpell() { return spell; }

	public boolean isExpired(final long currentTick) {
		return currentTick > expiresAfterTick;
	}

	public boolean hasCurrentLoadout() {
		if (rangeEquipmentId != actor.getRangeEquip()
			|| throwingEquipmentId != actor.getThrowingEquip()) {
			return false;
		}
		return channel != Channel.AUTOCAST || actor.getAutoCastSpell() == spell;
	}
}
