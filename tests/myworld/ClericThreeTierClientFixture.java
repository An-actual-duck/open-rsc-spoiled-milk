package orsc;

import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.interfaces.misc.ClericSpellGuideCatalog;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;

/** Actual decoder/model and PacketHandler tests; no sockets, login or game simulation. */
public final class ClericThreeTierClientFixture {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    interface Action{void run()throws Exception;}
    static void reject(Action action,String why)throws Exception{checks++;try{action.run();throw new AssertionError("Accepted "+why);}catch(IllegalArgumentException expected){}}
    static byte[] packet;static List<ClericSpellDef> definitions;
    static ClericSpellDef spell(int code){for(ClericSpellDef d:definitions)if(d.getStableCode()==code)return d;throw new AssertionError("Missing "+code);}
    static byte[] change(byte[] in,int index,int value){byte[] out=in.clone();out[index]=(byte)value;return out;}
    static void text(DataOutputStream out,String s)throws Exception{out.write(s.getBytes("ISO-8859-1"));out.writeByte(10);}
    static byte[] synthetic(int code,int costCount,int rankCount,int componentCount,int componentKind,int primary,int caster)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeByte(3);out.writeByte(1);out.writeShort(code);
        for(String s:new String[]{"cleric.synthetic","Synthetic","Synthetic metadata","saradomin"})text(out,s);
        out.writeByte(1);out.writeByte(1);out.writeByte(2);out.writeByte(caster);out.writeShort(3300);out.writeByte(costCount);
        for(int c=0;c<costCount;c++){out.writeShort(3300);out.writeByte(1);}out.writeInt(-1);out.writeInt(-1);text(out,"cleric-mend");out.writeByte(rankCount);
        for(int r=1;r<=rankCount;r++){out.writeByte(r);out.writeInt(60000);out.writeByte(8);out.writeByte(0);out.writeShort(0);out.writeShort(primary==0?r:primary);out.writeShort(1);out.writeByte(componentCount);for(int c=0;c<componentCount;c++){out.writeByte(componentKind);out.writeShort(200);out.writeShort(0);out.writeShort(0);}}
        return bytes.toByteArray();
    }
    static void presentation()throws Exception{
        check(packet.length==2677,"authoritative source payload length");check(definitions.size()==11,"active11");ClericSpellbookCatalog catalog=new ClericSpellbookCatalog();catalog.replace(3,definitions);
        int[] order={12,15,18,1,13,16,19,21,14,17,20};for(int i=0;i<order.length;i++){check(catalog.codeAtIndex(i)==order[i],"sparse sorted position "+i);check(catalog.get(order[i])==catalog.atIndex(i),"stable lookup");}
        check(catalog.codeAtIndex(-1)==-1&&catalog.codeAtIndex(11)==-1&&catalog.get(0)==null,"unknown identity");
        for(ClericSpellDef d:definitions){Map<Integer,Integer> inventory=new HashMap<Integer,Integer>();for(ClericCostDef c:d.getCosts())inventory.put(c.getItemId(),c.getCount());check(d.hasResources(id->inventory.getOrDefault(id,0)),"all costs");for(ClericCostDef c:d.getCosts()){inventory.put(c.getItemId(),c.getCount()-1);check(!d.hasResources(id->inventory.getOrDefault(id,0)),"missing resource greys out "+d.getName());inventory.put(c.getItemId(),c.getCount());}check(d.getEffectRanks().isEmpty()||d.getEffectRanks().size()==d.getSpellTier()*2,"tier cap");}
        check(spell(1).getCosts().get(0).getCount()==2&&spell(21).getCosts().get(0).getCount()==6,"flat Unify costs");check(spell(1).getRadius()==3&&spell(21).getRadius()==5,"Unify radii");
        for(int length=0;length<packet.length;length++){final byte[] cut=Arrays.copyOf(packet,length);reject(()->ClericSpellbookPacketDecoder.decode(cut),"truncation "+length);}
        reject(()->ClericSpellbookPacketDecoder.decode(change(packet,0,2)),"schema2");reject(()->ClericSpellbookPacketDecoder.decode(change(packet,1,33)),"too many definitions");reject(()->ClericSpellbookPacketDecoder.decode(Arrays.copyOf(packet,packet.length+1)),"trailing byte");
        for(int retired:new int[]{0,2,3,4,5,6,7,8,9,10,11})reject(()->ClericSpellbookPacketDecoder.decode(synthetic(retired,1,2,1,1,0,0)),"retired code");
        reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,2,2,1,1,0,0)),"duplicate costs");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,5,2,1,1,0,0)),"cost bound");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,7,1,1,0,0)),"rank bound");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,2,10,1,0,0)),"component bound");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,2,2,1,0,0)),"duplicate components");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,2,1,10,0,0)),"unknown component");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,2,1,1,4,0)),"rank identity mismatch");reject(()->ClericSpellbookPacketDecoder.decode(synthetic(12,1,2,1,1,0,2)),"bad bool");
        reject(()->new ClericComponentDef(8,5,0,0),"lifesteal units");reject(()->new ClericCostDef(1,0),"zero cost");reject(()->new ClericComponentDef(5,101,0,0),"percentage bounds");
        reject(()->catalog.replace(2,definitions),"old catalog");reject(()->catalog.replace(3,Arrays.asList(spell(12),spell(12))),"duplicate identities");
        int[] thresholds={0,20,45,75,115,160};for(int rank=1;rank<=6;rank++){check(ClericPowerPresentation.rank(thresholds[rank-1],6)==rank,"threshold");if(rank>1)check(ClericPowerPresentation.rank(thresholds[rank-1]-1,6)==rank-1,"threshold lower boundary");}check(ClericPowerPresentation.rank(163,2)==2&&ClericPowerPresentation.rank(163,4)==4&&ClericPowerPresentation.rank(72,6)==3,"caps/current combined value");check(ClericPowerPresentation.rank(-1,6)==0,"unknown power");
        check(spell(14).getEffectRank(6).getComponents().get(0).getMagnitude()==450,"regen total percent not additive");check(spell(14).getEffectRank(6).getComponents().get(1).getMagnitude()==6,"authoritative healing");
    }
    static byte[] statuses(int... codes)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeByte(codes.length);for(int code:codes){out.writeShort(spell(code).getSpellbookIconItemId());out.writeInt(60);}out.writeShort(0);out.writeByte(1);out.writeByte(codes.length);for(int code:codes){out.writeByte(1);out.writeShort(code);out.writeByte(6);out.writeByte(0);out.writeShort(0);}return bytes.toByteArray();}
    static void hud()throws Exception{ClericSpellbookCatalog catalog=new ClericSpellbookCatalog();catalog.replace(3,definitions);ActiveStatusHudModel hud=new ActiveStatusHudModel();hud.replace(ActiveStatusPacketDecoder.decode(statuses(14,17,20)),catalog,1000);List<ActiveStatusHudModel.Row> rows=hud.snapshot(1000).getRows();check(rows.size()==3,"one status per god");for(ActiveStatusHudModel.Row row:rows){check(row.isCleric(),"typed status");check(row.getCounterBadge().equals("P6"),"power badge");check(row.getRemainingSeconds()==60,"shared timer");check(!row.getClericIconAssetKey().isEmpty(),"approved icon");check(row.getClericHoverText().contains(spell(row.getStableIdentity()).getName()+" P6"),"name and power");}check(rows.get(0).getClericHoverText().contains("450% of normal")&&rows.get(0).getClericHoverText().contains("6 Hits every 6 sec, 10 pulses"),"Saradomin details");check(rows.get(1).getClericHoverText().contains("7 poison")&&rows.get(1).getClericHoverText().contains("each drained non-Hits stat every 5 sec")&&rows.get(1).getClericHoverText().contains("15% melee"),"Guthix details");check(rows.get(2).getClericHoverText().contains("strictly below 50% Hits"),"lifesteal boundary");check(hud.snapshot(61000).getRows().isEmpty(),"expiry");hud.replace(ActiveStatusPacketDecoder.decode(statuses(14)),catalog,1000);hud.replace(ActiveStatusPacketDecoder.decode(new byte[]{0}),catalog,1001);check(hud.snapshot(1001).getRows().isEmpty(),"authoritative removal");}
    static void guide(){List<ClericSpellGuideCatalog.Entry> entries=ClericSpellGuideCatalog.build(definitions,id->"Sigil "+id);check(entries.size()==11,"guide roster");for(ClericSpellGuideCatalog.Entry e:entries){check(e.getLines().size()<=12,"bounded guide row");for(String line:e.getLines())check(line.length()<=70,"bounded guide line");if(e.getStableCode()==14)check(e.getLines().toString().contains("0/20/45/75/115/160")&&e.getLines().toString().contains("200/250/300/350/400/450"),"all power ranks");if(e.getStableCode()==21)check(e.getLines().toString().contains("6 Sigil 3300")&&e.getArea().contains("5 tiles")&&!e.getLines().toString().contains("tier cap"),"Greater Unify fixed");}for(String row:ClericTextLayout.wrap("Long blessing name and three detailed components displayed correctly",100,s->s.length()*6))check(row.length()*6<=100,"pixel word wrap");}
    static void set(Object target,String name,Object value)throws Exception{Field field=mudclient.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    static Object get(Object target,String name)throws Exception{Field field=mudclient.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    static sun.misc.Unsafe unsafe()throws Exception{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return(sun.misc.Unsafe)f.get(null);}
    static void transport()throws Exception{
        mudclient mc=(mudclient)unsafe().allocateInstance(mudclient.class);set(mc,"clericSpellbook",new ClericSpellbookCatalog());set(mc,"clericIconSprites",new HashMap<Integer,com.openrsc.client.model.Sprite>());set(mc,"externalAssetLoader",new ClientExternalAssetLoader());set(mc,"playerStatEquipment",new int[8]);
        PacketHandler handler=new PacketHandler(mc);handler.getPacketsIncoming().dataBuffer=packet.clone();handler.getPacketsIncoming().packetEnd=0;handler.handlePacket(158,packet.length);check(mc.getClericSpellbookDefinitions().size()==11,"actual opcode158 dispatch");
        handler.getPacketsIncoming().dataBuffer=change(packet,0,2);handler.getPacketsIncoming().packetEnd=0;handler.handlePacket(158,packet.length);check(mc.getClericSpellbookDefinitions().isEmpty(),"actual handler fail closed");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);for(int i=0;i<5;i++)out.writeByte(0);for(int i=0;i<8;i++)out.writeInt(i==7?160:0);
        Method equipment=PacketHandler.class.getDeclaredMethod("updateEquipmentStats",int.class);equipment.setAccessible(true);handler.getPacketsIncoming().dataBuffer=bytes.toByteArray();handler.getPacketsIncoming().packetEnd=0;equipment.invoke(handler,bytes.size());check(mc.playerStatEquipment[7]==160&&(Boolean)get(mc,"clericHolyPowerKnown"),"authoritative combined Holy Power stat7");
        handler.getPacketsIncoming().dataBuffer=new byte[5];handler.getPacketsIncoming().packetEnd=0;equipment.invoke(handler,5);check(mc.playerStatEquipment[7]==0&&!(Boolean)get(mc,"clericHolyPowerKnown"),"old stats stay unknown");
    }
    public static void main(String[]args)throws Exception{packet=Files.readAllBytes(Paths.get(args[0]));definitions=ClericSpellbookPacketDecoder.decode(packet);String scope=args.length>1?args[1]:"all";if(scope.equals("all")||scope.equals("presentation")){presentation();transport();}if(scope.equals("all")||scope.equals("hud"))hud();if(scope.equals("all")||scope.equals("guide"))guide();System.out.println("PASS Cleric schema3 client "+scope+": "+checks+" checks; actual server bytes and no networking");}
}
