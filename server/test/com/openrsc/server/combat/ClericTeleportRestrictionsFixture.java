package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectFamily;
import com.openrsc.server.content.cleric.runtime.ClericSupportCasting;
import com.openrsc.server.content.cleric.runtime.ClericTimedEffectRuntime;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.ItemActionHandler;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.ItemCommandStruct;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.util.rsc.TeleportRestrictions;
import java.lang.reflect.Method;
import java.util.Map;

/** Real cast and item packets: no deep-Wilderness pulls, duel escape or blocked-cast costs. */
public final class ClericTeleportRestrictionsFixture {
    private static int checks;

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static void place(Player player, int x, int y) {
        player.resetAll();
        player.getCarriedItems().getInventory().getItems().clear();
        player.teleport(x, y, 0, false);
        player.setTeleporting(false);
    }

    private static void cost(Player caster, ClericSpellDefinition spell, boolean consumed) {
        for (ClericResourceCost resource : spell.getResourceCosts()) {
            check(ClericBlessingsFixture.count(caster, resource.getItemId())
                == 100 - (consumed ? resource.getCount() : 0), "exact once-only cost or no blocked-cast cost");
        }
    }

    public static void main(String[] args) throws Exception {
        try (CurrentCombatHarness h = new CurrentCombatHarness()) {
            h.world().getRegionManager().preflightNativeLayeredPlacements();
            h.world().getRegionManager().populateNativeLayeredPlacements();
            Player caster = h.player("pull caster", 200, 314);
            Player ally = h.player("pull ally", 203, 314);
            Player blocked = h.player("pull blocked", 203, 313);
            ClericBlessingsFixture.skill(caster, Skill.PRAYER, 99, 99);
            for (Player player : new Player[]{caster, ally, blocked}) {
                player.getClientLimitations().maxItemId = 65535;
                h.recordOutgoingPackets(player);
            }
            Party party = new Party(h.world());
            party.addPlayer(caster); party.addPlayer(ally);
            check(Point.location(200, 314).wildernessLevel() == 19, "allowed boundary is level 19");
            check(Point.location(200, 313).wildernessLevel() == 20, "blocked boundary is level 20");
            check(!TeleportRestrictions.isLocationBlocked(Point.location(200, 314)), "shared ordinary-spell boundary allows 19");
            check(TeleportRestrictions.isLocationBlocked(Point.location(200, 313)), "shared ordinary-spell boundary blocks 20");
            check(TeleportRestrictions.isLocationBlocked(Point.location(400, 20)), "Fisher King teleport exclusion");
            check(TeleportRestrictions.isLocationBlocked(Point.location(415, 160)), "Grand Tree teleport exclusion");

            for (int code : new int[]{1, 21}) {
                ClericSpellDefinition spell = ClericSpellCatalog.fromCode(code);
                int x = code == 1 ? 203 : 205;
                place(caster, 200, 314); place(ally, x, 314);
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(ally.getX() == 202 && ally.getY() == 314 && ally.isTeleporting(), "level 19 still teleports");
                cost(caster, spell, true);

                place(caster, 200, 313); place(ally, x, 314);
                ClericBlessingsFixture.fund(caster, spell);
                check(ClericSupportCasting.cast(caster, spell).getOutcome()
                    == ClericSupportCasting.Outcome.TELEPORT_RESTRICTED, "deep-Wilderness caster cannot pull shallower ally");
                check(!ally.isTeleporting() && ally.getX() == x, "blocked caster causes no movement");
                cost(caster, spell, false);

                place(caster, 200, 314); place(ally, x, 313);
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(!ally.isTeleporting() && ally.getX() == x, "shallower caster cannot extract level-20 ally");
                cost(caster, spell, false);

                place(caster, 200, 314); place(ally, x, 314);
                caster.getDuel().setDuelActive(true);
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(!ally.isTeleporting() && caster.getDuel().isDuelActive(), "dueling caster cannot use Unify");
                cost(caster, spell, false);

                place(caster, 200, 314); place(ally, x, 314);
                ally.getDuel().setDuelActive(true);
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(!ally.isTeleporting() && ally.getDuel().isDuelActive(), "cannot pull an ally out of a duel");
                cost(caster, spell, false);

                place(caster, 200, 314); place(ally, x, 314);
                ClericBlessingsFixture.fund(caster, spell);
                caster.getCarriedItems().getInventory().add(new Item(ItemId.ANA_IN_A_BARREL.id()));
                check(ClericSupportCasting.cast(caster, spell).getOutcome()
                    == ClericSupportCasting.Outcome.TELEPORT_RESTRICTED, "Unify respects spell quest-item restriction");
                cost(caster, spell, false);
                place(caster, 200, 314); place(ally, x, 314);
                ally.getCarriedItems().getInventory().add(new Item(ItemId.ANA_IN_A_BARREL.id()));
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(!ally.isTeleporting(), "carrying ally cannot bypass quest-item restriction");
                cost(caster, spell, false);
            }

            party.addPlayer(blocked);
            for (int code : new int[]{1, 21}) {
                ClericSpellDefinition spell = ClericSpellCatalog.fromCode(code);
                place(caster, 200, 314); place(ally, 203, 314); place(blocked, 203, 313);
                ClericBlessingsFixture.fund(caster, spell);
                ClericBlessingsFixture.packet(caster, code);
                check(ally.isTeleporting() && ally.getX() == 202, "eligible member moves in a mixed party");
                check(!blocked.isTeleporting() && blocked.getX() == 203, "restricted member remains in place");
                cost(caster, spell, true);
            }
            place(caster, 200, 313); place(ally, 201, 313);
            ClericBlessingsFixture.fund(caster, ClericSpellCatalog.fromCode(12));
            ClericBlessingsFixture.packet(caster, 12);
            check(ClericTimedEffectRuntime.blessing(ally, ClericEffectFamily.SARADOMIN_BLESSING) != null,
                "ordinary party blessings remain available in deep Wilderness");

            checkItemTeleports(h, caster);
            checkMagicTeleport(h, caster);
        }
        System.out.println("PASS " + checks + " Unify Wilderness/duel/quest-item and ordinary teleport restrictions");
    }

    private static void checkItemTeleports(CurrentCombatHarness h, Player player) throws Exception {
        int[] ids = {ItemId.AGILITY_CAPE.id(), ItemId.CRAFTING_CAPE.id(), ItemId.FISHING_CAPE.id(), 1709};
        String[] plugins = {"custom.misc.AgilityCape", "custom.misc.CraftingCape", "custom.misc.FishingCape", "custom.myworld.skills.enchanting.LawJewelry"};
        for (int i = 0; i < ids.length; i++) {
            place(player, 450, 450);
            Item item = new Item(ids[i]); item.getItemStatus().setDurability(5);
            player.getCarriedItems().getInventory().add(item);
            String[] commands = h.server().getEntityHandler().getItemDef(ids[i]).getCommand();
            check(commands != null && commands.length > 0, "teleport fixture has real item commands: " + ids[i]);
            int commandIndex = 0;
            for (int c = 0; c < commands.length; c++) if ("teleport".equalsIgnoreCase(commands[c])) commandIndex = c;
            player.getDuel().setDuelActive(true);
            ItemCommandStruct request = new ItemCommandStruct();
            request.index = 0; request.commandIndex = commandIndex;
            new ItemActionHandler().process(request, player);
            check(player.getDuel().isDuelActive() && !player.isTeleporting(), "item packet cannot clear duel state before teleport check");
            check(player.getX() == 450 && player.getY() == 450, "item packet cannot escape duel");
            ((OpInvTrigger) Class.forName("com.openrsc.server.plugins." + plugins[i]).newInstance())
                .onOpInv(player, 0, item, commands[commandIndex]);
            check(player.getDuel().isDuelActive() && !player.isTeleporting(), "direct item plugin also guards duel");
            check(item.getItemStatus().getDurability() == 5, "blocked item teleport spends no charges");
        }
    }

    private static void checkMagicTeleport(CurrentCombatHarness h, Player player) throws Exception {
        place(player, 450, 450);
        ClericBlessingsFixture.skill(player, Skill.MAGIC, 99, 99);
        for (Map.Entry<Integer, Integer> rune : h.server().getEntityHandler()
                .getSpellDef(Spells.VARROCK_TELEPORT).getRunesRequired()) {
            player.getCarriedItems().getInventory().add(new Item(rune.getKey(), 100));
        }
        player.setCastTimer(0);
        player.getDuel().setDuelActive(true);
        SpellStruct spell = new SpellStruct(); spell.setOpcode(OpcodeIn.CAST_ON_SELF); spell.spell = Spells.VARROCK_TELEPORT;
        new SpellHandler().process(spell, player);
        check(!player.isTeleporting() && player.getDuel().isDuelActive(), "ordinary teleport packet blocked during duel");
        for (Map.Entry<Integer, Integer> rune : h.server().getEntityHandler()
                .getSpellDef(Spells.VARROCK_TELEPORT).getRunesRequired()) {
            check(player.getCarriedItems().getInventory().countId(rune.getKey()) == 100, "blocked ordinary teleport spends no runes");
        }
        Method complete = SpellHandler.class.getDeclaredMethod("completeTeleport", Player.class,
            com.openrsc.server.external.SpellDef.class, Spells.class);
        complete.setAccessible(true);
        complete.invoke(new SpellHandler(), player, h.server().getEntityHandler().getSpellDef(Spells.VARROCK_TELEPORT), Spells.VARROCK_TELEPORT);
        check(!player.isTeleporting() && player.getX() == 450, "charged teleport also rechecks duel before movement");
        place(player, 200, 313);
        complete.invoke(new SpellHandler(), player, h.server().getEntityHandler().getSpellDef(Spells.VARROCK_TELEPORT), Spells.VARROCK_TELEPORT);
        check(!player.isTeleporting() && player.getX() == 200, "charged teleport cannot finish after crossing level-20 boundary");
    }
}
