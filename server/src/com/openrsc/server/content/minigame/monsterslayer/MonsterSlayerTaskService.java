package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;
import com.openrsc.server.net.rsc.ActionSender;
import java.util.ArrayList;
import java.util.List;

/** Narrow server API for future dialogue and NPC-death integration. */
public final class MonsterSlayerTaskService {
	private final MonsterSlayerData data;
	private final AssignmentItemGrant assignmentItems;

	/** Only the detached inventory may be changed before assignment commits. */
	public interface AssignmentItemGrant { boolean grant(Inventory staged, int itemId); }

	public MonsterSlayerTaskService(MonsterSlayerData data) {
		this(data, new AssignmentItemGrant() {
			@Override public boolean grant(Inventory staged, int itemId) { return staged.add(new Item(itemId), false); }
		});
	}
	public MonsterSlayerTaskService(MonsterSlayerData data, AssignmentItemGrant assignmentItems) {
		if (data == null) throw new IllegalArgumentException("Monster Slayer data is required");
		if (assignmentItems == null) throw new IllegalArgumentException("Slayer assignment supplies are required");
		this.data = data;
		this.assignmentItems = assignmentItems;
	}

	public MonsterSlayerState.TaskResult assignMandatory(Player player, String contactKey) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			return applyAssignment(player, current, MonsterSlayerState.assignMandatory(current, data, contactKey));
		}
	}

	public MonsterSlayerState.TaskResult assignRepeatable(Player player, String contactKey, String taskKey) {
		player = requirePlayer(player);
		synchronized (player) {
			if (!MonsterSlayerBossTasks.eligible(player, taskKey)) throw new IllegalArgumentException("Boss task requires opt-in and access");
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			return applyAssignment(player, current, MonsterSlayerState.assignRepeatable(current, data, contactKey, taskKey));
		}
	}

	/** Invoked only after NPC lifecycle code has established player eligibility. */
	public MonsterSlayerState.TaskResult creditEligibleKill(Player player, int npcId) {
		player = requirePlayer(player);
		synchronized (player) { return apply(player, MonsterSlayerState.recordEligibleKill(
			MonsterSlayerState.read(player.getCache(), data), data, npcId)); }
	}

	/**
	 * Read-only selection predicate for the NPC death lifecycle. It deliberately
	 * checks the same typed transition that credit would use, but never writes a
	 * player cache or emits progression.
	 */
	public boolean hasActiveTaskAcceptingNpc(Player player, int npcId) {
		try {
			player = requirePlayer(player);
			synchronized (player) {
				MonsterSlayerState.TaskResult candidate = MonsterSlayerState.recordEligibleKill(
					MonsterSlayerState.read(player.getCache(), data), data, npcId);
				return candidate.getReason() == MonsterSlayerState.TaskResult.Reason.PROGRESSED
					|| candidate.getReason() == MonsterSlayerState.TaskResult.Reason.COMPLETED;
			}
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/**
	 * Development-only completion seam. The command caller is responsible for
	 * privilege gating; this service deliberately replays the normal typed kill
	 * transition so rewards, mandatory cursors, rank changes, and exact-once
	 * completion retain their production semantics.
	 */
	public MonsterSlayerState.TaskResult completeActiveTaskForDevelopment(Player player) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.Snapshot current = MonsterSlayerState.read(player.getCache(), data);
			MonsterSlayerState.TaskResult result = MonsterSlayerState.completeActiveTaskForDevelopment(current, data);
			return apply(player, result);
		}
	}

	public MonsterSlayerState.DevelopmentResult advanceOneRankForDevelopment(Player player) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.DevelopmentResult result = MonsterSlayerState.advanceOneRankForDevelopment(
				MonsterSlayerState.read(player.getCache(), data), data);
			if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
			return result;
		}
	}

	public MonsterSlayerState.DevelopmentResult setBalanceForDevelopment(Player player,
			MonsterSlayerChallenge challenge, long amount) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.DevelopmentResult result = MonsterSlayerState.setBalanceForDevelopment(
				MonsterSlayerState.read(player.getCache(), data), data, challenge, amount);
			if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
			return result;
		}
	}

	/** Prepares the current rank's final mandatory task without granting rewards or rank. */
	public MonsterSlayerState.DevelopmentPreparation prepareCurrentMandatoryContactForDevelopment(Player player) {
		player = requirePlayer(player);
		synchronized (player) {
			MonsterSlayerState.DevelopmentPreparation result = MonsterSlayerState.prepareCurrentMandatoryContactForDevelopment(
				MonsterSlayerState.read(player.getCache(), data), data);
			if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
			return result;
		}
	}

	/**
	 * Optional progression must not be allowed to escape into the authoritative
	 * NPC death lifecycle. The caller still owns diagnostics and continues with
	 * other contributors after a failed credit.
	 */
	public CreditResult tryCreditEligibleKill(Player player, int npcId) {
		try {
			return CreditResult.success(creditEligibleKill(player, npcId));
		} catch (MonsterSlayerState.ValidationException ex) {
			return CreditResult.failure("invalid-state");
		} catch (ArithmeticException ex) {
			return CreditResult.failure("arithmetic-overflow");
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(" balance outside")) {
				return CreditResult.failure("balance-cap");
			}
			return CreditResult.failure("invalid-transition");
		} catch (RuntimeException ex) {
			// Slayer is optional at this lifecycle boundary. Never let an
			// unexpected cache/runtime failure abort an otherwise valid death.
			return CreditResult.failure("runtime-failure");
		}
	}

	/**
	 * Returns the player-facing remaining-target message only for a persisted,
	 * non-final task credit. Completion keeps its existing presentation path.
	 */
	public String progressMessage(MonsterSlayerState.TaskResult result) {
		if (result == null || result.getReason() != MonsterSlayerState.TaskResult.Reason.PROGRESSED) {
			return null;
		}
		MonsterSlayerState.Snapshot snapshot = result.getSnapshot();
		MonsterSlayerDefinitions.Task task = snapshot == null
			? null : data.getTask(snapshot.getActiveTaskKey());
		if (task == null) return null;
		MonsterSlayerDefinitions.Family family = data.getFamily(task.getFamilyKey());
		int remaining = task.getRequiredKills() - snapshot.getActiveKills();
		if (family == null || remaining <= 0) return null;
		return "You have " + remaining + " "
			+ displayNameForRemaining(task.getDisplayName(family.getDisplayName()), remaining) + " left to kill.";
	}

	private static String displayNameForRemaining(String displayName, int remaining) {
		if (remaining != 1 || displayName == null || !displayName.endsWith("s")) {
			return displayName;
		}
		if (displayName.endsWith("ves")) {
			return displayName.substring(0, displayName.length() - 3) + "f";
		}
		if (displayName.endsWith("ies")) {
			return displayName.substring(0, displayName.length() - 3) + "y";
		}
		return displayName.substring(0, displayName.length() - 1);
	}

	private MonsterSlayerState.TaskResult apply(Player player, MonsterSlayerState.TaskResult result) {
		if (result.isAccepted()) MonsterSlayerState.write(player.getCache(), data, result.getSnapshot());
		return result;
	}

	/** Task state and starter supplies commit together; reminders and failed assignments grant nothing. */
	private MonsterSlayerState.TaskResult applyAssignment(Player player, MonsterSlayerState.Snapshot current,
			MonsterSlayerState.TaskResult result) {
		if (result.getReason() != MonsterSlayerState.TaskResult.Reason.ASSIGNED) return result;
		List<Integer> supplies = new ArrayList<Integer>(MonsterSlayerTaskSupplies.itemIds(
			data.getTask(result.getSnapshot().getActiveTaskKey())));
		supplies.removeIf(id -> MonsterSlayerState.hasStarterClaim(player.getCache(), id));
		if (supplies.isEmpty()) return apply(player, result);
		Inventory inventory = player.getCarriedItems().getInventory();
		List<Item> live = inventory.getItems();
		synchronized (live) {
			for (int id : supplies) {
				if (player.getWorld().getServer().getEntityHandler().getItemDef(id) == null
					|| player.getClientLimitations().maxItemId < id
					|| (player.getConfig().RESTRICT_ITEM_ID >= 0 && player.getConfig().RESTRICT_ITEM_ID < id))
					return MonsterSlayerState.TaskResult.rejected(current, MonsterSlayerState.TaskResult.Reason.SUPPLIES_UNAVAILABLE);
			}
			int slots = 0;
			for (int id : supplies) slots += inventory.getRequiredSlots(new Item(id));
			if (inventory.getCapacity() - inventory.size() < slots)
				return MonsterSlayerState.TaskResult.rejected(current, MonsterSlayerState.TaskResult.Reason.INVENTORY_FULL);
			List<Item> original = new ArrayList<Item>(live);
			List<ItemStatus> statuses = new ArrayList<ItemStatus>();
			for (Item item : original) statuses.add(item.getItemStatus());
			boolean committed = false;
			try {
				Inventory staged = inventory.copyForTransaction();
				for (int id : supplies) {
					int before = usableSupplyCount(staged, id);
					if (!assignmentItems.grant(staged, id) || usableSupplyCount(staged, id) != Math.addExact(before, 1))
						return MonsterSlayerState.TaskResult.rejected(current, MonsterSlayerState.TaskResult.Reason.SUPPLIES_FAILED);
				}
				inventory.publishTransaction(staged);
				// write validates first and restores its owned cache keys on any partial write failure.
				MonsterSlayerState.writeWithStarterClaims(player.getCache(), data, result.getSnapshot(), supplies);
				committed = true;
			} catch (RuntimeException failure) {
				return MonsterSlayerState.TaskResult.rejected(current, MonsterSlayerState.TaskResult.Reason.SUPPLIES_FAILED);
			} finally {
				if (!committed) {
					live.clear();
					for (int i = 0; i < original.size(); i++) original.get(i).setItemStatus(statuses.get(i));
					live.addAll(original);
				}
			}
		}
		ActionSender.sendInventory(player);
		return result.withStarterItems(supplies);
	}
	private static int usableSupplyCount(Inventory inventory, int id) {
		int count = 0;
		for (Item item : inventory.getItems()) if (item.getCatalogId() == id && !item.getNoted())
			count = Math.addExact(count, item.getAmount());
		return count;
	}

	private static Player requirePlayer(Player player) {
		if (player == null || player.isRemoved()) throw new IllegalArgumentException("Active player is required");
		return player;
	}

	public static final class CreditResult {
		private final MonsterSlayerState.TaskResult taskResult;
		private final String failureReason;
		private CreditResult(MonsterSlayerState.TaskResult taskResult, String failureReason) {
			this.taskResult = taskResult;
			this.failureReason = failureReason;
		}
		private static CreditResult success(MonsterSlayerState.TaskResult result) {
			return new CreditResult(result, null);
		}
		private static CreditResult failure(String reason) { return new CreditResult(null, reason); }
		public boolean isSuccessful() { return failureReason == null; }
		public MonsterSlayerState.TaskResult getTaskResult() { return taskResult; }
		public String getFailureReason() { return failureReason; }
	}
}
