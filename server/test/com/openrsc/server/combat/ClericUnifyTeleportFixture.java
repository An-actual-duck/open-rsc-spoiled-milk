package com.openrsc.server.combat;

import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

/** Real Unify packets preserve signed floors and mark a single teleport destination. */
public final class ClericUnifyTeleportFixture {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.world().getRegionManager().preflightNativeLayeredPlacements();
            h.world().getRegionManager().populateNativeLayeredPlacements();
            Player caster=h.player("unify caster",450,450),recipient=h.player("unify target",453,450);
            ClericBlessingsFixture.skill(caster,Skill.PRAYER,99,99);
            caster.getClientLimitations().maxItemId=65535;recipient.getClientLimitations().maxItemId=65535;
            h.recordOutgoingPackets(caster);h.recordOutgoingPackets(recipient);
            Party party=new Party(h.world());party.addPlayer(caster);party.addPlayer(recipient);
            for(int level:new int[]{0,2,-3})for(int spell:new int[]{1,21}) {
                caster.teleportLayered(WorldLocation.global(new WorldCoordinate(450,450,level)),false);
                recipient.teleportLayered(WorldLocation.global(new WorldCoordinate(spell==1?453:455,450,level)),false);
                recipient.setTeleporting(false);
                ClericBlessingsFixture.fund(caster,ClericSpellCatalog.fromCode(spell));
                ClericBlessingsFixture.packet(caster,spell);
                WorldLocation actual=recipient.getWorldLocation();
                check(actual.getCoordinate().getX()==452 && actual.getCoordinate().getY()==450,"safe pull distance is unchanged: spell="+spell+" level="+level+" actual="+actual+" sigils="+ClericBlessingsFixture.count(caster,3300));
                check(actual.getCoordinate().getLevel()==level && actual.getWorldSpace().equals(caster.getWorldLocation().getWorldSpace()),"teleport preserves world and signed floor");
                check(recipient.isTeleporting(),"movement packet must use teleport, not walking");
                check(recipient.finishedPath(),"Unify leaves no forced walking route");
                check(recipient.getOutgoingCombatTarget()==null,"teleport uses normal outgoing combat cleanup");
            }
        }
        System.out.println("PASS "+checks+" Unify/Greater Unify teleport checks across surface,upper and underground floors");
    }
}
