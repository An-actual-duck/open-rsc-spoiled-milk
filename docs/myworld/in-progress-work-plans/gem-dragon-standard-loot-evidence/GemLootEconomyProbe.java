package com.openrsc.server.combat;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.external.ItemDefinition;
import java.lang.reflect.*;
import java.util.*;
public final class GemLootEconomyProbe {
 static Field drops,weight,type,amount,id,table;
 static double ev(CurrentCombatHarness h,DropTable t)throws Exception {
  int total=0;double sum=0;
  for(Object d:(List<?>)drops.get(t)) {
   int w=weight.getInt(d);if(w<=0)continue;total+=w;
   String kind=type.get(d).toString();
   if(kind.equals("ITEM")) {ItemDefinition def=h.server().getEntityHandler().getItemDef(id.getInt(d));if(def!=null)sum+=w*(double)amount.getInt(d)*def.getDefaultPrice();}
   else if(kind.equals("TABLE"))sum+=w*ev(h,(DropTable)table.get(d));
  }
  return total==0?0:sum/total;
 }
 public static void main(String[] a)throws Exception {
  drops=DropTable.class.getDeclaredField("drops");drops.setAccessible(true);
  Class<?> dc=Class.forName("com.openrsc.server.content.DropTable$Drop");
  weight=dc.getDeclaredField("weight");type=dc.getDeclaredField("type");amount=dc.getDeclaredField("amount");id=dc.getDeclaredField("id");table=dc.getDeclaredField("table");
  for(Field f:new Field[]{weight,type,amount,id,table})f.setAccessible(true);
  try(CurrentCombatHarness h=new CurrentCombatHarness()) {
   for(int n:new int[]{862,202,201,291,877,878,879,880,881})System.out.println("ECONOMY "+n+" normalBaseEV="+ev(h,h.world().getNpcDrops().getDropTable(n)));
  }
 }
}
