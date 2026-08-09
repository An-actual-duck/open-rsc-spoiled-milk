package com.openrsc.server.plugins.authentic.minigames.mage_arena;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.MiniGameInterface;
import com.openrsc.server.plugins.RuneScript;
import com.openrsc.server.plugins.triggers.*;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.openrsc.server.plugins.Functions.*;

public class MageArena implements MiniGameInterface, TalkNpcTrigger, KillNpcTrigger, OpLocTrigger, TakeObjTrigger, SpellNpcTrigger, AttackNpcTrigger, PlayerDeathTrigger {

	public static final int ELEMENTAL_STONE = 1152;
	public static final int POWER_STONE = 1153;
	public static final int ENLIGHTENMENT_STONE = 1154;
	private static final String MAGE_ARENA_STAFF_REWARD_CACHE = "mage_arena_staff_reward";
	private static final WorldLocation ARENA_ENTRY = location(229, 130, 0);
	private static final WorldLocation KOLODION_COMBAT_SPAWN = location(227, 130, 0);
	private static final WorldLocation ARENA_EXIT = location(228, 118, 0);
	private static final WorldLocation ARENA_REENTRY = location(228, 120, 0);
	private static final WorldLocation KOLODION_CAVE_RETURN = location(446, 538, -1);
	private static final int[] MAGE_ARENA_REWARD_STAFFS = {
		ItemId.STAFF_OF_ELEMENTS.id(),
		ItemId.STAFF_OF_POWER.id(),
		ItemId.STAFF_OF_ENLIGHTENMENT.id()
	};

	@Override
	public int getMiniGameId() {
		return Minigames.MAGE_ARENA;
	}

	@Override
	public String getMiniGameName() {
		return "Mage Arena (members)";
	}

	@Override
	public boolean isMembers() {
		return true;
	}

	@Override
	public void handleReward(Player player) {
		//mini-quest complete handled already
	}

	@Override
	public void onTalkNpc(final Player player, final Npc n) {
		if (getMaxLevel(player, Skill.MAGIC.id()) < 60) { // TODO: Enter the arena game.
			say(player, n, "hello there", "what is this place?");
			npcsay(player, n, "do not waste my time with trivial questions!",
				"i am the great kolodion, master of battle magic", "i have an arena to run");
			say(player, n, "can i enter?");
			npcsay(player, n, "hah, a wizard of your level..don't be absurd");
		} else if (player.getCache().hasKey("mage_arena")) {
			int stage = player.getCache().getInt("mage_arena");
			/* Started but failed. */
			if (stage == 1) {
				say(player, n, "hi");
				npcsay(player, n, "you return young conjurer..", "..you obviously have a taste for the darkside of magic",
					"let us continue with the battle...now");
				if (cantGo(player)) {
					cantGoMessage(player);
					return;
				}
				teleport(player, ARENA_ENTRY);
				delay();
				clampMageArenaCombatStats(player);
				spawnKolodion(player, player.getCache().getInt("kolodion_stage"), true);

			} else if (stage == 2) {
				say(player, n, "hello kolodion");
				npcsay(player, n, "hello  young mage.. you're a tough one you");
				say(player, n, "what now?");
				npcsay(player, n, "step into the magic pool, it will take you to the chamber",
					"there you must decide what type of mage you want to be");
				say(player, n, "ok .. thanks kolodion");
				npcsay(player, n, "that's what i'm here for");
			} else if (stage >= 3) {
				say(player, n, "hello kolodion");
				npcsay(player, n, "hey there, how are you?, enjoying the bloodshed?");
				say(player, n, "it's not bad, i've seen worse");
				int menu = multi(player, n,
					"i think i've had enough for now",
					"what is the arena for now?");
				if (menu == 0) {
					npcsay(player, n, "shame , you're a good battle mage",
						"hope to see you soon");
				} else if (menu == 1) {
					npcsay(player, n, "this arena is a proving ground for high magic",
						"the old relics are no longer mine to hand out",
						"but battle mages may still return here for challenge and rewards");
					say(player, n, "good stuff");
					npcsay(player, n, "bring your strongest magic when you return");
				}
			}
		} else {
			say(player, n, "hello there",
				"what is this place?");
			npcsay(player, n, "i am the great kolodion, master of battle magic ...",
				"... and this is my battle arena",
				"top wizards travel from all over to fight here");
			int choice = multi(player, n, "can i fight here?", "what's the point of that?", "that's barbaric");
			if (choice == 0) {
				canifight(player, n);
			} else if (choice == 1) {
				whatsthepoint(player, n);
			} else if (choice == 2) {
				barbaric(player, n);
			}
		}
	}

	public void canifight(Player player, Npc n) {
		npcsay(player, n, "my arena is open to any high level wizard",
				"but this is no game traveller, wizards fall in this arena..",
				"..never to rise again, the strongest of mage's have been destroyed",
				"but if you're sure you want in?");
		int choice = multi(player, n, "yes indeedy", "no, i don't");
		if (choice == 0) {
			joinfight(player, n);
		} else if (choice == 1) {
			npcsay(player, n, "your loss");
		}
	}

	public void whatsthepoint(Player player, Npc n) {
		npcsay(player, n, "we learn how to use our magic to it fullest...",
			"..,how to channel forces of the cosmos into our world..",
			"..,but mainly I just like blasting people into dust");
		int choice = multi(player, n, "can i fight here?", "that's barbaric");
		if (choice == 0) {
			canifight(player, n);
		} else if (choice == 1) {
			barbaric(player, n);
		}
	}

	public void barbaric(Player player, Npc n) {
		npcsay(player, n, "nope, it's magic, but I know what you mean",
				"so do you want to join us?");
		int choice = multi(player, n, "yes indeedy", "no, i don't");
		if (choice == 0) {
			joinfight(player, n);
		} else if (choice == 1) {
			npcsay(player, n, "your loss");
		}
	}

	public void joinfight(Player player, Npc n) {
		npcsay(player, n, "good..good, you have a healthy sense of competition",
			"remember traveller in my arena hand to hand combat is useless",
			"your strength will diminish as you enter the arena",
			"but the spells you can learn are amongst the most powerful in runescape",
			"before i can accept you in, we must duel",
			"you may not take armour or weapons into the arena");
		if (cantGo(player)) {
			cantGoMessage(player);
		}
		else {
			int choice = multi(player, n, "ok let's fight", "no thanks");
			if (choice == 0) {
				npcsay(player, n, "I must check that you're up to scratch");
				say(player, n, "you don't need to worry about that");
				npcsay(player, n, "not just any magician can enter traveller",
						"only the most powerful, the most feared",
						"before you use the power of this arena",
						"you must prove yourself against me",
						"now!");
				if (!player.getCache().hasKey("mage_arena")) {
					player.getCache().set("mage_arena", 1);
				}
				teleport(player, ARENA_ENTRY);
				delay();
				clampMageArenaCombatStats(player);

				// first time
				spawnKolodion(player, NpcId.KOLODION_HUMAN.id(), false);
			} else if (choice == 1) {
				npcsay(player, n, "your loss");
			}
		}
	}

	private void startKolodionEvent(Player player) {
		DelayedEvent kolE = player.getAttribute("kolodionEvent", null);
		DelayedEvent kolodionEvent = new DelayedEvent(player.getWorld(), player, config().GAME_TICK, "Mage Arena Kolodion Event") {
			@Override
			public void run() {
				Npc npc = getOwner().getAttribute("spawned_kolodion");
				if (npc == null) {
					stop();
					return;
				}
				/* Player logged out. */
				if (!getOwner().isLoggedIn() || getOwner().isRemoved()) {
					npc.remove();
					stop();
					return;
				}
				/* Npc has been removed from the world. */
				if (!player.getWorld().hasNpc(npc)) {
					stop();
					return;
				}
				/* Player has left the area */
				if (!npc.withinRange(getOwner())) {
					npc.remove();
					stop();
					return;
				}
				if (!npc.withinRange(getOwner(), 8)) {
					return;
				}
				clampMageArenaCombatStats(getOwner());
			}
		};
		if (kolE != null) {
			if (kolE.shouldRemove()) {
				player.setAttribute("kolodionEvent", kolodionEvent);
				player.getWorld().getServer().getGameEventHandler().add(kolodionEvent);
			}
		} else {
			player.setAttribute("kolodionEvent", kolodionEvent);
			player.getWorld().getServer().getGameEventHandler().add(kolodionEvent);
		}
	}

	// new kolodion stage
	public void spawnKolodion(Player player, int id) {
		this.spawnKolodion(player, id, false);
	}

	// kolodion from new attempt
	public void spawnKolodion(Player player, int id, boolean isContinue) {
		Npc kolodion = addnpc(id, KOLODION_COMBAT_SPAWN,
			(int)TimeUnit.SECONDS.toMillis(516), player);
		player.setAttribute("spawned_kolodion", kolodion);
		if (!isContinue) {
			player.getCache().set("kolodion_stage", id);
			if (isIntroductoryKolodionForm(kolodion)) {
				player.message("kolodion raises his staff and begins casting elemental magic");
			} else if (isKolodionOgreForm(kolodion)) {
				player.message("kolodion grows larger and channels earth and air magic");
			} else if (isKolodionSpiderForm(kolodion)) {
				player.message("kolodion skitters forward as acid gathers around its fangs");
			} else if (isKolodionSoulessForm(kolodion)) {
				player.message("kolodion's form twists into a forest spirit of violent magic");
			} else if (isKolodionDemonForm(kolodion)) {
				player.message("kolodion erupts into a demon and lashes out with burning claws");
			} else {
				player.message("kolodion blasts you " + (id == NpcId.KOLODION_OGRE.id() ? "with his staff" : "again"));
				player.damage(random(7, 15));
			}
			ActionSender.sendTeleBubble(player, player.getX(), player.getY(), true);
		}
		startKolodionEvent(player);
	}

	private boolean isIntroductoryKolodionForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_HUMAN.id();
	}

	private boolean isKolodionOgreForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_OGRE.id();
	}

	private boolean isKolodionSpiderForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_SPIDER.id();
	}

	private boolean isKolodionSoulessForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_SOULESS.id();
	}

	private boolean isKolodionDemonForm(final Npc npc) {
		return npc != null && npc.getID() == NpcId.KOLODION_DEMON.id();
	}

	private static int getOwnedMageArenaCombinationStaffId(final Player player) {
		for (int staffId : MAGE_ARENA_REWARD_STAFFS) {
			if (player.getCarriedItems().hasCatalogID(staffId, Optional.empty()) || player.getBank().hasItemId(staffId)) {
				return staffId;
			}
		}
		return -1;
	}

	private static boolean awardMageArenaStaff(final Player player, final int staffId) {
		final Item staff = new Item(staffId);
		if (!canReceive(player, staff)) {
			player.message("Your client could not receive this staff.");
			return false;
		}
		if (player.getCarriedItems().getInventory().canHold(staff)
			&& player.getCarriedItems().getInventory().add(staff)) {
			player.message("Kolodion gives you a " + staff.getDef(player.getWorld()).getName() + ".");
			return true;
		}
		if (player.getBank().canHold(staff) && player.getBank().add(staff, false)) {
			player.message("Kolodion places your " + staff.getDef(player.getWorld()).getName() + " in the bank.");
			return true;
		}
		player.message("You need inventory or bank space to receive the staff.");
		return false;
	}

	private final int[] statBoostingPotions = {
		ItemId.FULL_ATTACK_POTION.id(),
		ItemId.TWO_ATTACK_POTION.id(),
		ItemId.ONE_ATTACK_POTION.id(),
		ItemId.FULL_STRENGTH_POTION.id(),
		ItemId.THREE_STRENGTH_POTION.id(),
		ItemId.TWO_STRENGTH_POTION.id(),
		ItemId.ONE_STRENGTH_POTION.id(),
		ItemId.FULL_SUPER_ATTACK_POTION.id(),
		ItemId.TWO_SUPER_ATTACK_POTION.id(),
		ItemId.ONE_SUPER_ATTACK_POTION.id(),
		ItemId.FULL_SUPER_STRENGTH_POTION.id(),
		ItemId.TWO_SUPER_STRENGTH_POTION.id(),
		ItemId.ONE_SUPER_STRENGTH_POTION.id(),
		ItemId.FULL_STAT_RESTORATION_POTION.id(),
		ItemId.TWO_STAT_RESTORATION_POTION.id(),
		ItemId.ONE_STAT_RESTORATION_POTION.id()
	};

	private boolean isNotAllowed(Player player, Item item) {
		ItemDefinition def = item.getDef(player.getWorld());
		if (def.isWieldable()) {
			return !isAllowedMageArenaGear(player, item);
		} else {
			// non wearables are ok
			// Unless we're on Cabbage
			if (config().WANT_COMBAT_ODYSSEY) {
				// Disallow stat boosting potions
				return inArray(item.getCatalogId(), statBoostingPotions);
			}
		}
		return false;
	}

	private boolean isAllowedMageArenaGear(Player player, Item item) {
		final int itemId = item.getCatalogId();
		final ItemDefinition def = item.getDef(player.getWorld());
		final int slot = def.getWieldPosition();
		if (slot == Equipment.EquipmentSlot.SLOT_NECK.getIndex()
			|| slot == Equipment.EquipmentSlot.SLOT_RING.getIndex()
			|| slot == Equipment.EquipmentSlot.SLOT_WRIST.getIndex()
			|| slot == Equipment.EquipmentSlot.SLOT_CAPE.getIndex()) {
			return true;
		}
		return isAllowedMageArenaStaff(itemId) || isAllowedMageArenaRobePiece(itemId);
	}

	private boolean isAllowedMageArenaStaff(final int itemId) {
		return EnchantingItemEffects.isBaseStaff(itemId)
			|| EnchantingItemEffects.isEnchantedStaff(itemId)
			|| isTierElevenStaff(itemId);
	}

	private boolean isTierElevenStaff(final int itemId) {
		return itemId == ItemId.STAFF_OF_SARADOMIN.id()
			|| itemId == ItemId.STAFF_OF_ZAMORAK.id()
			|| itemId == ItemId.STAFF_OF_GUTHIX.id()
			|| itemId == ItemId.STAFF_OF_ELEMENTS.id()
			|| itemId == ItemId.STAFF_OF_POWER.id()
			|| itemId == ItemId.STAFF_OF_ENLIGHTENMENT.id();
	}

	private boolean isAllowedMageArenaRobePiece(final int itemId) {
		return EnchantingItemEffects.isBaseWoolRobePiece(itemId)
			|| EnchantingItemEffects.isEnchantedWoolRobePiece(itemId);
	}

	private void clampMageArenaCombatStats(final Player player) {
		final boolean sendUpdate = player.getClientLimitations().supportsSkillUpdate;
		boolean changed = setCurrentLevelIfPresent(player, Skill.MELEE.id(), 0, sendUpdate);
		changed |= setCurrentLevelIfPresent(player, Skill.RANGED.id(), 0, sendUpdate);
		changed |= setCurrentLevelIfPresent(player, Skill.STRENGTH.id(), 0, sendUpdate);
		if (changed && !sendUpdate) {
			player.getSkills().sendUpdateAll();
		}
	}

	private boolean setCurrentLevelIfPresent(final Player player, final int skill, final int level, final boolean sendUpdate) {
		if (skill < 0 || player.getSkills().getLevel(skill) == level) {
			return false;
		}
		player.getSkills().setLevel(skill, level, sendUpdate);
		return true;
	}

	private void cantGoMessage(Player player) {
		if (!config().WANT_COMBAT_ODYSSEY) {
			// Authentic
			mes("You cannot enter the arena...");
			delay(3);
			mes("...while carrying weapons or armour");
			delay(3);
		} else {
			// Cabbage
			mes("You cannot enter the arena...");
			delay(3);
			mes("...while carrying weapons, armour, or melee potions");
			delay(3);
		}
	}

	private boolean cantGo(Player player) {
		synchronized(player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (isNotAllowed(player, item)) return true;
			}

			if (config().WANT_EQUIPMENT_TAB) {
				Item item;
				for (int i = 0; i < Equipment.SLOT_COUNT; i++) {
					item = player.getCarriedItems().getEquipment().get(i);
					if (item == null) continue;
					if (isNotAllowed(player, item)) return true;
				}
			}
			return false;
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.KOLODION.id();
	}

	@Override
	public boolean blockKillNpc(Player player, Npc n) {
		return inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
				NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id());
	}

	@Override
	public void onKillNpc(Player player, Npc n) {
		if (inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
				NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id())) {
			n.remove();

			if (n.getID() == NpcId.KOLODION_HUMAN.id()) {
				mes("kolodion slumps to the floor..");
				delay(3);
				mes("..his body begins to grow and he changes form");
				delay(3);
				mes("He becomes an intimidating ogre");
				delay(3);
				spawnKolodion(player, NpcId.KOLODION_OGRE.id());
			} else if (n.getID() == NpcId.KOLODION_OGRE.id()) {
				mes("kolodion slumps to the floor once more..");
				delay(3);
				mes("..but again his body begins to grow and he changes form");
				delay(3);
				mes("He becomes an enormous spider");
				delay(3);
				spawnKolodion(player, NpcId.KOLODION_SPIDER.id());
			} else if (n.getID() == NpcId.KOLODION_SPIDER.id()) {
				mes("kolodion again slumps to the floor..");
				delay(3);
				mes("..but again his body begins to grow as he changes form");
				delay(3);
				mes("He becomes an ethereal being");
				delay(3);
				spawnKolodion(player, NpcId.KOLODION_SOULESS.id());
			} else if (n.getID() == NpcId.KOLODION_SOULESS.id()) {
				mes("kolodion again slumps to the floor..motionless");
				delay(3);
				mes("..but again his body begins to grow as he changes form");
				delay(3);
				mes("...larger this time");
				delay(3);
				mes("He becomes a vicious demon");
				delay(3);
				spawnKolodion(player, NpcId.KOLODION_DEMON.id());
			} else if (n.getID() == NpcId.KOLODION_DEMON.id()) {
				mes("kolodion again slumps to the floor..motionless");
				delay(3);
				mes("..he slowly rises to his feet in his true form");
				delay(3);
				mes("@yel@Kolodion: \"well done young adventurer\"");
				delay(3);
				mes("@yel@Kolodion: \"you truly are a worthy battle mage\"");
				delay(3);
				player.message("kolodion teleports you to his cave");
				teleport(player, KOLODION_CAVE_RETURN);
				Npc kolodion = ifnearvisnpc(player, NpcId.KOLODION.id(), 8);
				if (kolodion == null) {
					player.message("kolodion is currently busy, but his reward waits for you");
				} else {
					say(player, kolodion, "what now kolodion?");
					npcsay(player, kolodion, "you have earned a true battle mage staff",
						"step into the magic pool and choose the stone that matches your path");
				}
				player.getCache().set("mage_arena", 2);
				player.getCache().remove("kolodion_stage");
			}
		}
	}

	@Override
	public void onSpellNpc(Player player, Npc n) {
		if (inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
			NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id())) {
			if (!n.getAttribute("spawnedFor", null).equals(player)) {
				player.message("that mage is busy.");
			}
		} else if (inArray(n.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())
			&& (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") < 2)) {
			player.message("you are not yet ready to fight the battle mages");
		}
	}

	@Override
	public boolean blockSpellNpc(final Player player, final Npc n) {
		if (inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
				NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id())) {
			if (!n.getAttribute("spawnedFor", null).equals(player)) {
				return true;
			}
		} else if (inArray(n.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())
			&& (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") < 2)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == 1019 || obj.getID() == 1020 || obj.getID() == 1027
			|| isMageArenaStaffStone(obj.getID());
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() == 1019 || obj.getID() == 1020) {
			player.message("you open the gate ...");
			player.message("... and walk through");
			doGate(player, obj);
			if (player.getCache().hasKey("mage_arena") && player.getCache().getInt("mage_arena") == 4) {
				clampMageArenaCombatStats(player);
			}
		} else if (obj.getID() == 1027) {
			if (player.getY() >= 120) {
				player.message("you pass through the mystical barrier");
				teleport(player, ARENA_EXIT);
				Npc kolodion = player.getAttribute("spawned_kolodion", null);
				if (kolodion != null) {
					kolodion.remove();
				}
			} else {
				if (player.getCache().hasKey("mage_arena") && player.getCache().getInt("mage_arena") >= 4) {
					mes("the barrier is checking your person for weapons");
					delay(3);
					if (!cantGo(player)) {
						clampMageArenaCombatStats(player);
						teleport(player, ARENA_REENTRY);
					} else {
						cantGoMessage(player);
					}
				} else {
					player.message("you cannot enter without the permission of kolodion");
				}
			}
		} else if (isMageArenaStaffStone(obj.getID())) {
			claimMageArenaStoneStaff(player, obj.getID());
		}
	}

	private boolean isMageArenaStaffStone(final int objectId) {
		return objectId == ELEMENTAL_STONE || objectId == POWER_STONE || objectId == ENLIGHTENMENT_STONE;
	}

	private void claimMageArenaStoneStaff(final Player player, final int objectId) {
		if (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") < 2) {
			mes("the stone is silent");
			delay(3);
			mes("you are not ready to choose a mage path");
			delay(3);
			return;
		}
		if (player.getCache().getInt("mage_arena") >= 4) {
			mes("you have already chosen your mage path");
			delay(3);
			mes("the chamber guardian can sell you the other staves");
			delay(3);
			return;
		}

		final int existingStaffId = getOwnedMageArenaCombinationStaffId(player);
		if (existingStaffId > -1) {
			player.getCache().set("mage_arena", 4);
			player.getCache().set(MAGE_ARENA_STAFF_REWARD_CACHE, existingStaffId);
			player.getCache().remove("kolodion_stage");
			player.message("You have already claimed a Mage Arena staff.");
			player.sendMiniGameComplete(this.getMiniGameId(), Optional.empty());
			return;
		}

		final int staffId = getMageArenaStaffForStone(objectId);
		final String pathName = getMageArenaStonePathName(objectId);
		mes("you place your hands on the " + pathName + " stone");
		delay(3);
		mes("you feel the path of " + pathName.toLowerCase() + " magic open before you");
		delay(3);
		if (!awardMageArenaStaff(player, staffId)) {
			return;
		}
		player.getCache().set("mage_arena", 4);
		player.getCache().set(MAGE_ARENA_STAFF_REWARD_CACHE, staffId);
		player.getCache().remove("kolodion_stage");
		player.sendMiniGameComplete(this.getMiniGameId(), Optional.empty());
	}

	private int getMageArenaStaffForStone(final int objectId) {
		if (objectId == ELEMENTAL_STONE) {
			return ItemId.STAFF_OF_ELEMENTS.id();
		}
		if (objectId == POWER_STONE) {
			return ItemId.STAFF_OF_POWER.id();
		}
		return ItemId.STAFF_OF_ENLIGHTENMENT.id();
	}

	private String getMageArenaStonePathName(final int objectId) {
		if (objectId == ELEMENTAL_STONE) {
			return "Elemental";
		}
		if (objectId == POWER_STONE) {
			return "Power";
		}
		return "Enlightenment";
	}

	@Override
	public void onAttackNpc(Player player, Npc affectedmob) {
		if (inArray(affectedmob.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
			NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id())) {
			if (!affectedmob.getAttribute("spawnedFor", null).equals(player)) {
				player.message("that mage is busy.");
			}
		} else if (inArray(affectedmob.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())) {
			if (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") <= 2) {
				player.message("you are not yet ready to fight the battle mages");
			} else {
				// Cabbage only
				if (config().WANT_COMBAT_ODYSSEY) {
					clampMageArenaCombatStats(player);
					RuneScript.npcattack();
				}
			}
		}
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc n) {
		if (inArray(n.getID(), NpcId.KOLODION_HUMAN.id(), NpcId.KOLODION_OGRE.id(), NpcId.KOLODION_SPIDER.id(),
				NpcId.KOLODION_SOULESS.id(), NpcId.KOLODION_DEMON.id())) {
			if(!n.getAttribute("spawnedFor", null).equals(player)) {
				return true;
			}
		} else if (inArray(n.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())
			&& (player.getConfig().WANT_COMBAT_ODYSSEY || !player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") <= 2)) {
			return true;
		}

		return false;
	}

	@Override
	public void onPlayerDeath(Player player) {
		if (player.getAttribute("spawned_kolodion", null) != null) {
			player.setAttribute("spawned_kolodion", null);
		}
	}

	@Override
	public boolean blockPlayerDeath(Player player) {
		return false;
	}

	@Override
	public boolean blockTakeObj(Player player, GroundItem i) {
		return false;
	}

	@Override
	public void onTakeObj(Player player, GroundItem i) {
	}

	private static WorldLocation location(final int x, final int y, final int level) {
		return WorldLocation.global(new WorldCoordinate(x, y, level));
	}

	/**
	 * Uses Player's explicit-level compatibility overload, keeping the named
	 * layered destination correct in both native and legacy map runtimes.
	 */
	private static void teleport(final Player player, final WorldLocation destination) {
		WorldCoordinate coordinate = destination.getCoordinate();
		player.teleport(coordinate.getX(), coordinate.getY(), coordinate.getLevel(), false);
	}
}
