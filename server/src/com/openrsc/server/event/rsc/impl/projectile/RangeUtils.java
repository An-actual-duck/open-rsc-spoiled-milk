package com.openrsc.server.event.rsc.impl.projectile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.combat.OSRSCombatFormula;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.openrsc.server.plugins.Functions.getCurrentLevel;

public class RangeUtils {
    public static final int WEARABLE_ARROWS_ID = 1000;
    public static final int WEARABLE_BOLTS_ID = 1001;
	public static final int PLAYER_COMBAT_RANGE_BONUS = 2;
	private static final int PLAYER_POSITIONING_RANGE_REDUCTION = 1;
	private static final int DEFAULT_BOW_RANGE = 5;
	private static final int SHORT_BOW_RANGE = 4;
	private static final int DEFAULT_THROWING_RANGE = 3;
	private static final int THROWING_DART_RANGE = 4;

    private final static Set<Integer> BOWS = ImmutableSet.of(
            ItemId.LONGBOW.id(), ItemId.SHORTBOW.id(),
            ItemId.PINE_LONGBOW.id(), ItemId.PINE_SHORTBOW.id(),
            ItemId.OAK_LONGBOW.id(), ItemId.OAK_SHORTBOW.id(),
            ItemId.PALM_LONGBOW.id(), ItemId.PALM_SHORTBOW.id(),
            ItemId.WILLOW_LONGBOW.id(), ItemId.WILLOW_SHORTBOW.id(),
            ItemId.MAPLE_LONGBOW.id(), ItemId.MAPLE_SHORTBOW.id(),
            ItemId.EBONY_LONGBOW.id(), ItemId.EBONY_SHORTBOW.id(),
            ItemId.YEW_LONGBOW.id(), ItemId.YEW_SHORTBOW.id(),
            ItemId.BLOOD_LONGBOW.id(), ItemId.BLOOD_SHORTBOW.id(),
            ItemId.MAGIC_LONGBOW.id(), ItemId.MAGIC_SHORTBOW.id(),
            ItemId.DRAGON_LONGBOW.id()
    );
    private final static Set<Integer> CROSSBOWS = ImmutableSet.of(
            ItemId.CROSSBOW.id(), ItemId.PHOENIX_CROSSBOW.id(), ItemId.OAK_CROSSBOW.id(),
            ItemId.WILLOW_CROSSBOW.id(), ItemId.PALM_CROSSBOW.id(), ItemId.MAPLE_CROSSBOW.id(),
            ItemId.YEW_CROSSBOW.id(), ItemId.EBONY_CROSSBOW.id(), ItemId.MAGIC_CROSSBOW.id(),
            ItemId.BLOOD_CROSSBOW.id(), ItemId.DRAGON_CROSSBOW.id()
    );
	private final static Set<Integer> SHORT_BOWS = ImmutableSet.of(
		ItemId.SHORTBOW.id(), ItemId.PINE_SHORTBOW.id(), ItemId.OAK_SHORTBOW.id(), ItemId.WILLOW_SHORTBOW.id(),
		ItemId.PALM_SHORTBOW.id(), ItemId.MAPLE_SHORTBOW.id(), ItemId.YEW_SHORTBOW.id(), ItemId.EBONY_SHORTBOW.id(),
		ItemId.MAGIC_SHORTBOW.id(), ItemId.BLOOD_SHORTBOW.id()
	);

    private final static Set<Integer> TIN_ARROWS = ImmutableSet.of(ItemId.TIN_ARROWS.id());
    private final static Set<Integer> COPPER_ARROWS = ImmutableSet.of(ItemId.COPPER_ARROWS.id());
    private final static Set<Integer> BRONZE_ARROWS = ImmutableSet.of(ItemId.BRONZE_ARROWS.id(), ItemId.POISON_BRONZE_ARROWS.id());
    private final static Set<Integer> IRON_ARROWS = ImmutableSet.of(ItemId.IRON_ARROWS.id(), ItemId.POISON_IRON_ARROWS.id());
    private final static Set<Integer> STEEL_ARROWS = ImmutableSet.of(ItemId.STEEL_ARROWS.id(), ItemId.POISON_STEEL_ARROWS.id());
    private final static Set<Integer> MITHRIL_ARROWS = ImmutableSet.of(ItemId.MITHRIL_ARROWS.id(), ItemId.POISON_MITHRIL_ARROWS.id());
    private final static Set<Integer> TITAN_ARROWS = ImmutableSet.of(ItemId.TITAN_STEEL_ARROWS.id());
    private final static Set<Integer> ADDY_ARROWS = ImmutableSet.of(ItemId.ADAMANTITE_ARROWS.id(), ItemId.POISON_ADAMANTITE_ARROWS.id());
    private final static Set<Integer> ORICHALCUM_ARROWS = ImmutableSet.of(ItemId.ORICHALCUM_ARROWS.id());
    private final static Set<Integer> ICE_ARROWS = ImmutableSet.of(ItemId.ICE_ARROWS.id());
    private final static Set<Integer> RUNE_ARROWS = ImmutableSet.of(ItemId.RUNE_ARROWS.id(), ItemId.POISON_RUNE_ARROWS.id());
    private final static Set<Integer> DRAGON_ARROWS = ImmutableSet.of(ItemId.DRAGON_ARROWS.id(), ItemId.POISON_DRAGON_ARROWS.id());

    private final static Set<Integer> TIN_BOLTS = ImmutableSet.of(ItemId.CROSSBOW_BOLTS.id(), ItemId.POISON_CROSSBOW_BOLTS.id());
    private final static Set<Integer> COPPER_BOLTS = ImmutableSet.of(ItemId.COPPER_BOLTS.id(), ItemId.POISON_COPPER_BOLTS.id());
    private final static Set<Integer> BRONZE_BOLTS = ImmutableSet.of(ItemId.BRONZE_BOLTS.id(), ItemId.POISON_BRONZE_BOLTS.id(), ItemId.OYSTER_PEARL_BOLTS.id());
    private final static Set<Integer> IRON_BOLTS = ImmutableSet.of(ItemId.IRON_BOLTS.id(), ItemId.POISON_IRON_BOLTS.id());
    private final static Set<Integer> STEEL_BOLTS = ImmutableSet.of(ItemId.STEEL_BOLTS.id(), ItemId.POISON_STEEL_BOLTS.id());
    private final static Set<Integer> MITHRIL_BOLTS = ImmutableSet.of(ItemId.MITHRIL_BOLTS.id(), ItemId.POISON_MITHRIL_BOLTS.id());
    private final static Set<Integer> TITAN_BOLTS = ImmutableSet.of(ItemId.TITAN_STEEL_BOLTS.id(), ItemId.POISON_TITAN_STEEL_BOLTS.id());
    private final static Set<Integer> ADDY_BOLTS = ImmutableSet.of(ItemId.ADAMANTITE_BOLTS.id(), ItemId.POISON_ADAMANTITE_BOLTS.id());
    private final static Set<Integer> ORICHALCUM_BOLTS = ImmutableSet.of(ItemId.ORICHALCUM_BOLTS.id(), ItemId.POISON_ORICHALCUM_BOLTS.id());
    private final static Set<Integer> RUNE_BOLTS = ImmutableSet.of(ItemId.RUNE_BOLTS.id(), ItemId.POISON_RUNE_BOLTS.id());
    private static final Set<Integer> DRAGON_BOLTS = ImmutableSet.of(ItemId.DRAGON_BOLTS.id(), ItemId.POISON_DRAGON_BOLTS.id());

    private static final Map<Integer, Set<Integer>> ALLOWED_PROJECTILES;

    @SafeVarargs
    private static <T> Set<T> combine(Set<T>... sets) {
        return Stream.of(sets)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    static {
        Map<Integer, Set<Integer>> allowedProjectilesMap = new HashMap<>();

        // Arrows
        allowedProjectilesMap.put(ItemId.SHORTBOW.id(), TIN_ARROWS);
        allowedProjectilesMap.put(ItemId.LONGBOW.id(), TIN_ARROWS);
        allowedProjectilesMap.put(ItemId.PINE_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS));
        allowedProjectilesMap.put(ItemId.PINE_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS));
        allowedProjectilesMap.put(ItemId.OAK_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS));
        allowedProjectilesMap.put(ItemId.OAK_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS));
        allowedProjectilesMap.put(ItemId.WILLOW_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS));
        allowedProjectilesMap.put(ItemId.WILLOW_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS));
        allowedProjectilesMap.put(ItemId.PALM_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS));
        allowedProjectilesMap.put(ItemId.PALM_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS));
        allowedProjectilesMap.put(ItemId.MAPLE_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS));
        allowedProjectilesMap.put(ItemId.MAPLE_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS));
        allowedProjectilesMap.put(ItemId.YEW_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.YEW_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.EBONY_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.EBONY_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.MAGIC_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ORICHALCUM_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.MAGIC_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ORICHALCUM_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.BLOOD_SHORTBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ORICHALCUM_ARROWS, RUNE_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.BLOOD_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ORICHALCUM_ARROWS, RUNE_ARROWS, ICE_ARROWS));
        allowedProjectilesMap.put(ItemId.DRAGON_LONGBOW.id(), combine(TIN_ARROWS, COPPER_ARROWS, BRONZE_ARROWS, IRON_ARROWS, STEEL_ARROWS, MITHRIL_ARROWS, TITAN_ARROWS, ADDY_ARROWS, ORICHALCUM_ARROWS, RUNE_ARROWS, DRAGON_ARROWS, ICE_ARROWS));

        // Crossbow
        allowedProjectilesMap.put(ItemId.CROSSBOW.id(), TIN_BOLTS);
        allowedProjectilesMap.put(ItemId.PHOENIX_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS));
        allowedProjectilesMap.put(ItemId.OAK_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS));
        allowedProjectilesMap.put(ItemId.WILLOW_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS));
        allowedProjectilesMap.put(ItemId.PALM_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS));
        allowedProjectilesMap.put(ItemId.MAPLE_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS));
        allowedProjectilesMap.put(ItemId.YEW_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS, TITAN_BOLTS));
        allowedProjectilesMap.put(ItemId.EBONY_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS, TITAN_BOLTS, ADDY_BOLTS));
        allowedProjectilesMap.put(ItemId.MAGIC_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS, TITAN_BOLTS, ADDY_BOLTS, ORICHALCUM_BOLTS));
        allowedProjectilesMap.put(ItemId.BLOOD_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS, TITAN_BOLTS, ADDY_BOLTS, ORICHALCUM_BOLTS, RUNE_BOLTS));
        allowedProjectilesMap.put(ItemId.DRAGON_CROSSBOW.id(), combine(TIN_BOLTS, COPPER_BOLTS, BRONZE_BOLTS, IRON_BOLTS, STEEL_BOLTS, MITHRIL_BOLTS, TITAN_BOLTS, ADDY_BOLTS, ORICHALCUM_BOLTS, RUNE_BOLTS, DRAGON_BOLTS));

        ALLOWED_PROJECTILES = ImmutableMap.copyOf(allowedProjectilesMap);
    }

    public static final ImmutableSet<Integer> THROWING_DARTS = ImmutableSet.of(
            ItemId.TIN_THROWING_DART.id(),
            ItemId.COPPER_THROWING_DART.id(),
            ItemId.BRONZE_THROWING_DART.id(),
            ItemId.IRON_THROWING_DART.id(),
            ItemId.STEEL_THROWING_DART.id(),
            ItemId.MITHRIL_THROWING_DART.id(),
            ItemId.TITAN_STEEL_THROWING_DART.id(),
            ItemId.ADAMANTITE_THROWING_DART.id(),
            ItemId.ORICHALCUM_THROWING_DART.id(),
            ItemId.RUNE_THROWING_DART.id(),
            ItemId.POISONED_TIN_THROWING_DART.id(),
            ItemId.POISONED_COPPER_THROWING_DART.id(),
            ItemId.POISONED_BRONZE_THROWING_DART.id(),
            ItemId.POISONED_IRON_THROWING_DART.id(),
            ItemId.POISONED_STEEL_THROWING_DART.id(),
            ItemId.POISONED_MITHRIL_THROWING_DART.id(),
            ItemId.POISONED_TITAN_STEEL_THROWING_DART.id(),
            ItemId.POISONED_ADAMANTITE_THROWING_DART.id(),
            ItemId.POISONED_ORICHALCUM_THROWING_DART.id(),
            ItemId.POISONED_RUNE_THROWING_DART.id()
    );

    public static final ImmutableSet<Integer> THROWING_KNIVES = ImmutableSet.of(
            ItemId.TIN_THROWING_KNIFE.id(),
            ItemId.COPPER_THROWING_KNIFE.id(),
            ItemId.BRONZE_THROWING_KNIFE.id(),
            ItemId.IRON_THROWING_KNIFE.id(),
            ItemId.STEEL_THROWING_KNIFE.id(),
            ItemId.BLACK_THROWING_KNIFE.id(),
            ItemId.MITHRIL_THROWING_KNIFE.id(),
            ItemId.TITAN_STEEL_THROWING_KNIFE.id(),
            ItemId.ADAMANTITE_THROWING_KNIFE.id(),
            ItemId.ORICHALCUM_THROWING_KNIFE.id(),
            ItemId.RUNE_THROWING_KNIFE.id(),
            ItemId.POISONED_TIN_THROWING_KNIFE.id(),
            ItemId.POISONED_COPPER_THROWING_KNIFE.id(),
            ItemId.POISONED_BRONZE_THROWING_KNIFE.id(),
            ItemId.POISONED_IRON_THROWING_KNIFE.id(),
            ItemId.POISONED_STEEL_THROWING_KNIFE.id(),
            ItemId.POISONED_BLACK_THROWING_KNIFE.id(),
            ItemId.POISONED_MITHRIL_THROWING_KNIFE.id(),
            ItemId.POISONED_TITAN_STEEL_THROWING_KNIFE.id(),
            ItemId.POISONED_ADAMANTITE_THROWING_KNIFE.id(),
            ItemId.POISONED_ORICHALCUM_THROWING_KNIFE.id(),
            ItemId.POISONED_RUNE_THROWING_KNIFE.id()
    );

	public static int getBowAttackRadius(final int weaponId) {
		final int baseRadius = isCrossbow(weaponId) || isShortBow(weaponId) ? SHORT_BOW_RANGE : DEFAULT_BOW_RANGE;
		return baseRadius + PLAYER_COMBAT_RANGE_BONUS;
	}

	public static int getThrowingAttackRadius(final int throwingEquip) {
		final int baseRadius = THROWING_DARTS.contains(throwingEquip) ? THROWING_DART_RANGE : DEFAULT_THROWING_RANGE;
		return baseRadius + PLAYER_COMBAT_RANGE_BONUS;
	}

	public static int getApproachRadius(final int attackRadius) {
		return Math.max(1, attackRadius - PLAYER_POSITIONING_RANGE_REDUCTION);
	}

    protected static final ImmutableSet<Integer> POISONED_ITEMS = ImmutableSet.of(
            ItemId.POISONED_TIN_THROWING_DART.id(),
            ItemId.POISONED_TIN_THROWING_KNIFE.id(),
            ItemId.POISONED_TIN_SPEAR.id(),

            ItemId.POISONED_COPPER_THROWING_DART.id(),
            ItemId.POISONED_COPPER_THROWING_KNIFE.id(),
            ItemId.POISONED_COPPER_SPEAR.id(),

            ItemId.POISONED_BRONZE_THROWING_DART.id(),
            ItemId.POISONED_BRONZE_SPEAR.id(),
            ItemId.POISONED_BRONZE_DAGGER.id(),
            ItemId.POISONED_BRONZE_THROWING_KNIFE.id(),

            ItemId.POISONED_IRON_THROWING_DART.id(),
            ItemId.POISONED_IRON_SPEAR.id(),
            ItemId.POISONED_IRON_DAGGER.id(),
            ItemId.POISONED_IRON_THROWING_KNIFE.id(),

            ItemId.POISONED_STEEL_THROWING_DART.id(),
            ItemId.POISONED_STEEL_SPEAR.id(),
            ItemId.POISONED_STEEL_DAGGER.id(),
            ItemId.POISONED_STEEL_THROWING_KNIFE.id(),

            ItemId.POISONED_MITHRIL_THROWING_DART.id(),
            ItemId.POISONED_MITHRIL_SPEAR.id(),
            ItemId.POISONED_MITHRIL_DAGGER.id(),
            ItemId.POISONED_MITHRIL_THROWING_KNIFE.id(),

            ItemId.POISONED_TITAN_STEEL_THROWING_DART.id(),
            ItemId.POISONED_TITAN_STEEL_THROWING_KNIFE.id(),
            ItemId.POISONED_TITAN_STEEL_SPEAR.id(),

            ItemId.POISONED_ADAMANTITE_THROWING_DART.id(),
            ItemId.POISONED_ADAMANTITE_SPEAR.id(),
            ItemId.POISONED_ADAMANTITE_DAGGER.id(),
            ItemId.POISONED_ADAMANTITE_THROWING_KNIFE.id(),

            ItemId.POISONED_ORICHALCUM_THROWING_DART.id(),
            ItemId.POISONED_ORICHALCUM_THROWING_KNIFE.id(),
            ItemId.POISONED_ORICHALCUM_SPEAR.id(),

            ItemId.POISONED_RUNE_THROWING_DART.id(),
            ItemId.POISONED_RUNE_SPEAR.id(),
            ItemId.POISONED_RUNE_DAGGER.id(),
            ItemId.POISONED_RUNE_THROWING_KNIFE.id(),

            ItemId.POISON_CROSSBOW_BOLTS.id(),
            ItemId.POISON_COPPER_BOLTS.id(),
            ItemId.POISON_BRONZE_BOLTS.id(),
            ItemId.POISON_IRON_BOLTS.id(),
            ItemId.POISON_STEEL_BOLTS.id(),
            ItemId.POISON_MITHRIL_BOLTS.id(),
            ItemId.POISON_TITAN_STEEL_BOLTS.id(),
            ItemId.POISON_ADAMANTITE_BOLTS.id(),
            ItemId.POISON_ORICHALCUM_BOLTS.id(),
            ItemId.POISON_RUNE_BOLTS.id(),
            ItemId.POISON_DRAGON_ARROWS.id(),
            ItemId.POISON_DRAGON_BOLTS.id(),
            ItemId.POISONED_DRAGON_DAGGER.id()
    );

    public static void poisonTarget(Mob aggressor, Mob target, int poisonDamage) {
        target.applyPoison(poisonDamage);
        if(aggressor instanceof Player
                && target instanceof Npc
                && aggressor.getConfig().WANT_POISON_NPCS
        ) {
            Player player = (Player) aggressor;
            Npc npc = (Npc) target;
            player.message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + npc.getDef().name + "!");
        }
    }

    public static void applyDragonFireBreath(Player player, Mob target, boolean deliveredFirstProjectile) {
        if (target.isNpc()) {
            Npc npc = (Npc) target;
            if (!deliveredFirstProjectile && (npc.getID() == NpcId.DRAGON.id() || npc.getID() == NpcId.KING_BLACK_DRAGON.id())) {
                player.playerServerMessage(MessageType.QUEST, "The dragon breathes fire at you");
                int percentage = 20;
                int fireDamage;
                if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.ANTI_DRAGON_BREATH_SHIELD.id())) {
                    if (npc.getID() == NpcId.DRAGON.id()) {
                        percentage = 10;
                    } else if (npc.getID() == NpcId.KING_BLACK_DRAGON.id()) {
                        percentage = 4;
                    } else {
                        percentage = 0;
                    }
                    player.playerServerMessage(MessageType.QUEST, "Your shield prevents some of the damage from the flames");
                }
                fireDamage = (int) Math.floor(getCurrentLevel(player, Skill.HITS.id()) * percentage / 100.0);
                player.damage(fireDamage);

                //reduce ranged level (case for KBD)
                if (npc.getID() == NpcId.KING_BLACK_DRAGON.id()) {
                    int newLevel = getCurrentLevel(player, Skill.RANGED.id()) - Formulae.getLevelsToReduceAttackKBD(player);
                    player.getSkills().setLevel(Skill.RANGED.id(), newLevel);
                }
            }
        }
    }

    public static void handleArrowLossAndDrop(World world, Player player, Mob target, int damage, int arrowId) {
        if (Formulae.loseArrow(damage)) {
            GroundItem arrows = getArrows(arrowId, target, player);
            if (!DropTable.handleRingOfAvarice(player, new Item(arrowId, 1))) {
                if (arrows == null) {
                    world.registerItem(
                            new GroundItem(
                                    player.getWorld(),
                                    arrowId,
                                    target.getX(),
                                    target.getY(),
                                    1,
                                    player
                            )
                    );
                } else {
                    arrows.setAmount(arrows.getAmount() + 1);
                }
            }
        }
    }

    private static GroundItem getArrows(int id, Mob target, Player player) {
        return target.getViewArea().getVisibleGroundItem(id, target.getLocation(), player);
    }

    public static void applyPoison(Player player, Mob target, int arrowId) {
        final boolean isWeaponPoisoned = RangeUtils.POISONED_ITEMS.contains(arrowId);
        if (isWeaponPoisoned && target.isPlayer()) {
            if (DataConversions.random(1, 8) == 1) {
                poisonTarget(player, target, 20);
            }
        }
        // Poison Arrows/Bolts Ability to Poison an NPC
        if (player.getConfig().WANT_POISON_NPCS) {
            if (isWeaponPoisoned && target.isNpc()) {
                if (DataConversions.random(1, 50) == 1) {
                    poisonTarget(player, target, 60);
                }
            }
        }
    }

    public static boolean canFire(int weaponId, int arrowId) {
        return ALLOWED_PROJECTILES.containsKey(weaponId)
                && ALLOWED_PROJECTILES.get(weaponId).contains(arrowId);
    }

    public static int doRangedDamage(final Mob attacker, final int bowId, final int arrowId, final Mob defender, final boolean skillCape) {
		if (attacker.getWorld().getServer().getConfig().OSRS_COMBAT_RANGED) {
			return OSRSCombatFormula.Ranged.doRangedDamage(attacker, bowId, arrowId, defender, skillCape);
		} else {
			return CombatFormula.doRangedDamage(attacker, bowId, arrowId, defender, skillCape);
		}
	}

    public static boolean isCrossbow(int weaponId) {
        return CROSSBOWS.contains(weaponId);
    }

	public static boolean isShortBow(final int weaponId) {
		return SHORT_BOWS.contains(weaponId);
	}

    public static boolean isBow(int weaponId) {
        return BOWS.contains(weaponId);
    }

    public static int getAdjustedRangeDelayTicks(final Mob attacker, final int baseDelayTicks) {
        if (attacker == null) {
            return baseDelayTicks;
        }
        final double multiplier = getRangeSpeedMultiplier(attacker);
        if (multiplier == 1.0D) {
            return baseDelayTicks;
        }
        return Math.max(1, (int) Math.floor(baseDelayTicks / multiplier));
    }

    private static double getRangeSpeedMultiplier(final Mob attacker) {
        double multiplier = 1.0D;
        if (attacker.isPlayer()) {
            final Player player = (Player) attacker;
            final int weaponSpeed = player.getCarriedItems().getEquipment().getWeaponSpeed();
            switch (weaponSpeed) {
                case 1:
                    multiplier = 0.8D;
                    break;
                case 2:
                    multiplier = 0.9D;
                    break;
                case 4:
                    multiplier = 1.1D;
                    break;
                case 5:
                    multiplier = 1.2D;
                    break;
                case 3:
                default:
                    multiplier = 1.0D;
                    break;
            }
            multiplier *= player.getPotionAttackSpeedMultiplier();
        }
        if (attacker.getEarthAttackSpeedDebuffPercent() > 0) {
            multiplier *= Math.max(0.10D, 1.0D - (attacker.getEarthAttackSpeedDebuffPercent() / 100.0D));
        }
        return multiplier;
    }
}
