package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericResourceCost;
import com.openrsc.server.content.cleric.ClericSpellCatalog;
import com.openrsc.server.content.cleric.ClericSpellDefinition;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Real accepted/refused casts and appearance encoders, offline without server startup. */
public final class ClericBuffAnimationFixture {
  static int checks;
  static void check(boolean ok,String why) { checks++; if(!ok)throw new AssertionError(why); }
  @SuppressWarnings("unchecked") static List<Packet> packets(Player p)throws Exception {
    Field f=Player.class.getDeclaredField("outgoingPackets"); f.setAccessible(true);return (List<Packet>)f.get(p);
  }
  static void appearances(CurrentCombatHarness h,Player viewer,int... expected)throws Exception {
    for (Player other : viewer.getLocalPlayers()) viewer.requiresAppearanceUpdateFor(other);
    packets(viewer).clear();h.server().getGameUpdater().updatePlayerAppearances(viewer);
    List<Integer> actual=new ArrayList<Integer>();
    for(Packet p:packets(viewer)) if(p.getID()==234) {
      io.netty.buffer.ByteBuf b=p.getBuffer().duplicate();int count=b.readUnsignedShort();
      for(int i=0;i<count;i++) {
        b.readUnsignedShort();check(b.readUnsignedByte()==10,"only effect records after flag reset");actual.add((int)b.readUnsignedByte());
      }
      check(b.readableBytes()==0,"appearance count matches exact body");
    }
    List<Integer>want=new ArrayList<Integer>();for(int id:expected)want.add(id);
    check(actual.equals(want),"recipient/observer appearance IDs "+actual+" wanted "+want);
  }
  public static void main(String[] args)throws Exception {
    try(CurrentCombatHarness h=new CurrentCombatHarness()) {
      h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;
      h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
      Player caster=h.player("vfxcaster",450,450),recipient=h.player("vfxrecipient",451,450),observer=h.player("vfxobserver",452,450),ally=h.player("vfxally",451,451);
      for(Player p:new Player[]{caster,recipient,observer,ally}) {
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(0,0,0,0,1,2));
        p.setClientVersion(ClericBlessingContract.CLIENT_VERSION);p.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(p);
        ClericBlessingsFixture.skill(p,Skill.HITS,100,1000);ClericBlessingsFixture.skill(p,Skill.PRAYER,160,99);
      }
      ClericBlessingsFixture.party(h,caster,recipient,ally);
      observer.getLocalPlayers().add(recipient);observer.getLocalPlayers().add(ally);
      caster.getLocalPlayers().add(recipient);caster.getLocalPlayers().add(ally);
      recipient.getLocalPlayers().add(ally);ally.getLocalPlayers().add(recipient);
      int[] ids={86,95,96,89,97,98,92,99,100};
      for(int code=12;code<=20;code++) {
        ClericBlessingsFixture.clear(caster);ClericBlessingsFixture.clear(recipient);ClericBlessingsFixture.clear(ally);
        for(Player p:new Player[]{caster,recipient,observer,ally})p.resetAfterUpdate();
        ClericSpellDefinition spell=ClericSpellCatalog.fromCode(code);ClericBlessingsFixture.fund(caster,spell);ClericBlessingsFixture.packet(caster,code);
        check(ally.getUpdateFlags().getCombatEffects().size()==1,"second party recipient has its own sequence");
        check(recipient.getUpdateFlags().getCombatEffects().size()==1,"one sequence per accepted composite blessing");
        check(recipient.getUpdateFlags().getCombatEffects().get(0).getEffectType()==ids[code-12],"component mapping");
        check(!caster.getUpdateFlags().hasCombatEffect()&&!observer.getUpdateFlags().hasCombatEffect(),"caster/uninvolved not recipient");
        for(ClericResourceCost cost:spell.getResourceCosts())check(ClericBlessingsFixture.count(caster,cost.getItemId())==100-cost.getCount(),"resources once");
        appearances(h,ally,ids[code-12]);appearances(h,recipient,ids[code-12]);appearances(h,observer);appearances(h,caster);
        recipient.getUpdateFlags().setCombatEffect(new CombatEffect(recipient,34));
        appearances(h,recipient,ids[code-12],34);appearances(h,observer,34);appearances(h,caster,34);
        if(spell.getSpellTier()==3) {
          recipient.resetAfterUpdate();ally.resetAfterUpdate();caster.resetAfterUpdate();
          ClericSpellDefinition lower=ClericSpellCatalog.fromCode(code-2);ClericBlessingsFixture.fund(caster,lower);ClericBlessingsFixture.packet(caster,code-2);
          check(!recipient.getUpdateFlags().hasCombatEffect(),"refused tier downgrade stays silent");
          for(ClericResourceCost cost:lower.getResourceCosts())check(ClericBlessingsFixture.count(caster,cost.getItemId())==100,"no-op consumes no sigils");
        }
      }
      ClericBlessingsFixture.clear(recipient);recipient.resetAfterUpdate();ally.resetAfterUpdate();caster.resetAfterUpdate();
      caster.getCarriedItems().getInventory().getItems().clear();ClericBlessingsFixture.packet(caster,12);
      check(!recipient.getUpdateFlags().hasCombatEffect(),"unfunded cast stays silent");
      for(ClericSpellDefinition d:ClericSpellCatalog.getAll())check(d.getPresentation().hasOnEntityAnimation()==d.getId().isBlessing(),"curses and Unify stay animationless");
      Npc npc=h.npc(3,451,451);npc.resetAfterUpdate();observer.resetAfterUpdate();observer.getLocalNpcs().add(npc);
      Method npcs=h.server().getGameUpdater().getClass().getDeclaredMethod("updateNpcAppearances",Player.class);npcs.setAccessible(true);
      npcs.invoke(h.server().getGameUpdater(),observer); // Initial independent phase-state receipt.
      npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc,96));
      packets(observer).clear();npcs.invoke(h.server().getGameUpdater(),observer);
      check(packets(observer).stream().noneMatch(p->p.getID()==104),"NPC cannot broadcast recipient-only cosmetics");
      npc.getUpdateFlags().setCombatEffect(new CombatEffect(npc,34));packets(observer).clear();npcs.invoke(h.server().getGameUpdater(),observer);
      Packet ordinary=packets(observer).stream().filter(p->p.getID()==104).findFirst().orElseThrow(()->new AssertionError("missing ordinary NPC effect"));
      io.netty.buffer.ByteBuf b=ordinary.getBuffer().duplicate();check(b.readUnsignedShort()==1&&b.readUnsignedShort()==npc.getIndex()&&b.readUnsignedByte()==10&&b.readUnsignedByte()==34&&b.readableBytes()==0,"NPC count excludes private ID, ordinary effect broadcast retained");
      System.out.println("PASS "+checks+" actual Cleric buff cast/resource/recipient-only player+NPC packet checks");
    }
  }
}
