package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.HpUpdate;
import com.openrsc.server.model.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hit restoration event is independent of the other stats
 * The restoration tick may be re-synced if player has no stats to restore and new
 * trigger occurs such as drinking a potion
 */
public class StatRestorationEvent extends GameTickEvent {

	private HashMap<Integer, Integer> restoringStats = new HashMap<Integer, Integer>();
	private AtomicReference<Boolean> restoringHits = new AtomicReference<Boolean>(false);
	private long lastStatRestoration = System.currentTimeMillis();
	private long lastHitRestoration = System.currentTimeMillis();
	private long numberSkills;

	public StatRestorationEvent(World world, Mob mob) {
		super(world, mob, 1, "Stat Restoration Event", DuplicationStrategy.ALLOW_MULTIPLE);
		numberSkills = mob.isPlayer() ? world.getServer().getConstants().getSkills().getSkillsCount() : 4;
	}

	/**
	 * Player stat restoration follows durable player/session state rather than
	 * a terrain source. NPC restoration deliberately retains the default owner
	 * position hint so NPC source-preservation correlation remains mandatory.
	 */
	@Override
	public GameTickEventSpatialAffinity getSpatialAffinity() {
		return getOwner() != null && getOwner().isPlayer()
			? GameTickEventSpatialAffinity.nonSpatialGlobal()
			: super.getSpatialAffinity();
	}

	@Override
	public void run() {
		boolean isPlayerAbsent = getOwner().isPlayer() && (getPlayerOwner() == null || getPlayerOwner().isRemoved());
		boolean isNpcAbsent = getOwner().isNpc() && (getNpcOwner() == null || (getNpcOwner().isRemoved() && getNpcOwner().isUnregistering()));
		if (getOwner() == null || isPlayerAbsent || isNpcAbsent) {
			stop();
			return;
		}
		if (Summoning.isSummon(getOwner())) {
			stop();
			return;
		}

		boolean restoredStats = false;
		boolean restoredHits = false;
		long deltaCycles;

		if (getOwner().isPlayer()) {
			Player player = (Player) getOwner();
			player.tickBodyRobeWeaponPowerDecay();
			player.tickDeathRingChargeDecay();
		}

		// Add new skills to the restoration cycle
		for (int skillIndex = 0; skillIndex < numberSkills; skillIndex++) {
			if (skillIndex == Skill.PRAYER.id()
				&& !getOwner().getConfig().LACKS_PRAYERS
				&& !getOwner().getConfig().WANT_MYWORLD) {
				continue;
			}
			checkAndStartRestoration(skillIndex);
		}

		boolean sendUpdate = !getOwner().isPlayer() || ((Player)getOwner()).getClientLimitations().supportsSkillUpdate;

		// Check for Hits
		if (restoringHits.get()) {
			long delay = 100 * getWorld().getServer().getConfig().GAME_TICK; // 64 seconds in authentic rate
			if (getOwner().isPlayer()) {
				delay = getNaturalHitsInterval((Player) getOwner(), 0.0D);
			}
			deltaCycles = (System.currentTimeMillis() - this.lastHitRestoration) / delay;
			if (System.currentTimeMillis() - this.lastHitRestoration > delay && getOwner().isPlayer()) {
				normalizeLevel(Skill.HITS.id(), sendUpdate);
				restoredHits = true;
				if (((Player) getOwner()).getParty() != null) {
					getOwner().getUpdateFlags().setHpUpdate(new HpUpdate(getOwner(), 0));
					if (getWorld().getServer().getConfig().WANT_PARTIES) {
						if (((Player) getOwner()).getParty() != null) {
							((Player) getOwner()).getParty().sendParty();
						}
					}
				}
			} else if (!getOwner().isPlayer() &&
				(System.currentTimeMillis() - (this.lastHitRestoration + deltaCycles * delay)) / (delay / 100) == 1) {
				// npc only gets heal cycle sync on (re)spawn
				normalizeLevel(Skill.HITS.id(), true);
			}
		}

		// Every other skill
		Iterator<Entry<Integer, Integer>> it = restoringStats.entrySet().iterator();
		while (it.hasNext()) {

			Entry<Integer, Integer> set = it.next();
			int stat = set.getKey();

			long delay = 100 * getWorld().getServer().getConfig().GAME_TICK; // 64 seconds in authentic rate
			if (getOwner().isPlayer()) {
				Player player = (Player) getOwner();
				if (!player.getConfig().WANT_MYWORLD && player.getPrayers().isPrayerActivated(Prayers.RAPID_RESTORE)) {
					delay = 50 * getWorld().getServer().getConfig().GAME_TICK;
				}
			}
			if (System.currentTimeMillis() - this.lastStatRestoration > delay) {
				normalizeLevel(stat, sendUpdate);
				restoredStats = true;
				if (restoringStats.get(stat) == 0) {
					it.remove();
					if (getOwner().isPlayer()) {
						Player player = (Player) getOwner();
						player.message("Your " + getOwner().getWorld().getServer().getConstants().getSkills().getSkillDisplayName(stat).toLowerCase()
							+ " ability has returned to normal.");
					}
				}
			}
		}
		if (restoredHits) {
			this.lastHitRestoration = System.currentTimeMillis();
		}
		if (restoredStats) {
			this.lastStatRestoration = System.currentTimeMillis();
		}
		if (!sendUpdate && (restoredHits || restoredStats)) {
			getOwner().getSkills().sendUpdateAll();
		}
	}

	/**
	 * Resolves independently owned speed factors without mutating the existing
	 * natural-healing clock. C07 passes no active Respite bonus because status
	 * representation belongs to C08; the later Respite effect supplies only its
	 * snapshotted bonus here and must not reschedule or directly heal.
	 */
	long getNaturalHitsInterval(final Player player, final double respiteSpeedBonus) {
		final long minimumInterval = getWorld().getServer().getConfig().GAME_TICK;
		long interval = 100L * minimumInterval;
		if (!player.getConfig().WANT_MYWORLD
			&& player.getPrayers().isPrayerActivated(Prayers.RAPID_HEAL)) {
			interval = 50L * minimumInterval;
		}
		interval = NaturalHitsRegeneration.applySpeedBonus(
			interval, minimumInterval, player.getSoulRobeHealthRegenerationBonus());
		interval = NaturalHitsRegeneration.applySpeedBonus(
			interval, minimumInterval,
			Math.max(0, player.getPotionOfRegenerationMultiplier() - 1));
		interval = NaturalHitsRegeneration.applySpeedBonus(
			interval, minimumInterval,
			player.getCarriedItems().getEquipment().getBodyAmuletRegenSpeedBonus());
		return NaturalHitsRegeneration.applySpeedBonus(
			interval, minimumInterval, respiteSpeedBonus);
	}

	/**
	 * Normalises level to max level by 1.
	 *
	 * @param skill
	 * @return true if action done, false if skill is already normal
	 */
	private void normalizeLevel(int skill, boolean sendUpdate) {
		int cur = getOwner().getSkills().getLevel(skill);
		int norm = getOwner().isPlayer()
			? ((Player) getOwner()).getEquipmentAdjustedNormalLevel(skill)
			: getOwner().getSkills().getMaxStat(skill);
		int diff = 0;

		if (cur > norm) {
			getOwner().getSkills().setLevel(skill, cur - 1, sendUpdate, true);
			diff = -1;
		} else if (cur < norm) {
			getOwner().getSkills().setLevel(skill, cur + 1, sendUpdate, true);
			diff = 1;
		}

		cur += diff;
		if (cur == norm) {
			if (skill == Skill.HITS.id()) {
				restoringHits.set(false);
			} else {
				restoringStats.put(skill, 0);
			}
		}
	}

	private boolean needsRestore(int id) {
		int curStat = getOwner().getSkills().getLevel(id);
		int maxStat = getOwner().isPlayer()
			? ((Player) getOwner()).getEquipmentAdjustedNormalLevel(id)
			: getOwner().getSkills().getMaxStat(id);
		return curStat > maxStat || curStat < maxStat;
	}

	private void checkAndStartRestoration(int id) {
		boolean toRestore = needsRestore(id);

		if (id == Skill.HITS.id()) {
			if (restoringHits.get()) {
				return;
			}
			if (toRestore) {
				restoringHits.set(true);
			}
		} else {
			if (restoringStats.containsKey(id)) {
				return;
			}
			if (toRestore) {
				restoringStats.put(id, 1);
			}
		}
	}

	public void tryResyncStat() {
		if (restoringStats.size() == 0) {
			this.lastStatRestoration = System.currentTimeMillis();
		}
	}

	public void tryResyncHit() {
		boolean toRestore = needsRestore(Skill.HITS.id());

		if (!toRestore) {
			this.lastHitRestoration = System.currentTimeMillis();
			restoringHits.set(false);
		}
	}
}
