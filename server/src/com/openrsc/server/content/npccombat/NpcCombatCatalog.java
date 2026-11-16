package com.openrsc.server.content.npccombat;

import com.openrsc.server.Server;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Server-scoped gameplay catalog; deliberately outside editor/source-discovery loading. */
public final class NpcCombatCatalog {
  private final Map<Integer,NpcCombatDefinition> definitions;
  private final Map<Integer,String> compatibility;
  private NpcCombatCatalog(Map<Integer,NpcCombatDefinition> d,Map<Integer,String> c){
    definitions=Collections.unmodifiableMap(new HashMap<>(d));compatibility=Collections.unmodifiableMap(new HashMap<>(c));
  }
  public static NpcCombatCatalog empty(){return new NpcCombatCatalog(Collections.emptyMap(),Collections.emptyMap());}
  public NpcCombatDefinition get(int id){return definitions.get(id);}
  public String compatibilityPolicy(int id){return compatibility.get(id);}
  public int canonicalCount(){return definitions.size();}
  private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException("Canonical NPC combat: "+message);}
  public static int integer(JSONObject o,String key){
    Object raw=o.get(key);require(raw instanceof Integer||raw instanceof Long,"integer required: "+key);
    long n=((Number)raw).longValue();require(n>=0&&n<Integer.MAX_VALUE,"invalid/reserved value: "+key);return(int)n;
  }
  private static boolean bool(JSONObject o,String key){Object v=o.get(key);require(v instanceof Boolean,"boolean required: "+key);return(Boolean)v;}
  private static CombatStyle style(String s){
    require(s.equals("melee")||s.equals("ranged")||s.equals("magic"),"unknown style "+s);
    return CombatStyle.valueOf(s.toUpperCase(Locale.ROOT));
  }
  private static int[] values(JSONObject o){
    require(o.keySet().equals(new HashSet<>(Arrays.asList("melee","ranged","magic"))),"complete three-style numeric block required");
    return new int[]{integer(o,"melee"),integer(o,"ranged"),integer(o,"magic")};
  }
  private static Set<String> tags(JSONArray a){
    Set<String> out=new HashSet<>();for(int i=0;i<a.length();i++){
      String s=a.getString(i);require(s.matches("[a-z][a-z0-9_.-]*")&&out.add(s),"invalid/duplicate tag "+s);
    }return out;
  }
  public static NpcCombatCatalog load(Server server){
    if(!server.getConfig().WANT_MYWORLD)return empty();
    Path file=Paths.get(server.getConfig().CONFIG_DIR,"defs","NpcCombatMyWorld.json");
    try{return parse(server,new JSONObject(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)));}
    catch(IOException|RuntimeException e){throw new IllegalStateException("Cannot load canonical NPC combat from "+file,e);}
  }
  /** Public for offline schema/real-loader fixtures, never an inference/fallback API. */
  public static NpcCombatCatalog parse(Server server,JSONObject root){
    require(integer(root,"schemaVersion")==1,"unsupported schema");JSONArray rows=root.getJSONArray("npcs");
    Map<Integer,NpcCombatDefinition> defs=new HashMap<>();Map<Integer,String> compat=new HashMap<>();Set<Integer> seen=new HashSet<>();
    for(int x=0;x<rows.length();x++){
      JSONObject r=rows.getJSONObject(x);int id=integer(r,"id");NPCDef original=server.getEntityHandler().getNpcDef(id);
      require(seen.add(id)&&original!=null,"duplicate/unknown id "+id);
      require(original.getName().equals(r.getString("name")),"identity changed at "+id);
      String policy=r.getString("policy");
      if(!policy.equals("canonical")){
        require(Arrays.asList("noncombat_compatibility","protected_no_change","role_review_hold").contains(policy),"unknown compatibility policy");
        require(!r.getString("reason").trim().isEmpty(),"compatibility reason required");
        require(!policy.equals("noncombat_compatibility")||!Boolean.TRUE.equals(original.attackable),"attackable definition cannot silently use noncombat compatibility");
        compat.put(id,policy);continue;
      }
      require(Boolean.TRUE.equals(original.attackable),"canonical combat on nonattackable "+id);
      int[] power=values(r.getJSONObject("power")),defense=values(r.getJSONObject("defense"));
      int holy=integer(r,"holyPower");require(holy==0,"Holy Power placeholder must remain inert/zero");
      boolean boss=bool(r,"boss");CombatStyle primary=style(r.getString("primaryStyle"));
      JSONArray enabled=r.getJSONArray("styles");Set<CombatStyle> styles=EnumSet.noneOf(CombatStyle.class);
      for(int j=0;j<enabled.length();j++)require(styles.add(style(enabled.getString(j))),"duplicate enabled style");
      require(!styles.isEmpty()&&styles.contains(primary),"missing enabled primary");
      CombatStyle[] order={CombatStyle.MELEE,CombatStyle.RANGED,CombatStyle.MAGIC};
      for(int j=0;j<3;j++)require(styles.contains(order[j])?power[j]>0:power[j]==0,"disabled/enabled power mismatch at "+id);
      String dispatch=r.getString("dispatch");require(dispatch.equals("generic")||dispatch.equals("special_preserved"),"invalid dispatch");
      boolean generic=dispatch.equals("generic");int[] weights={0,0,0};
      if(generic){
        require(styles.size()<3&&(styles.size()==1||styles.contains(CombatStyle.MELEE)),"unsupported generic style combination");
        JSONObject w=r.getJSONObject("weights");Set<String> keys=new HashSet<>();long total=0;
        for(int j=0;j<3;j++)if(styles.contains(order[j])){String k=order[j].name().toLowerCase(Locale.ROOT);keys.add(k);weights[j]=integer(w,k);require(weights[j]>0,"enabled style needs weight");total+=weights[j];}
        require(w.keySet().equals(keys)&&total==100,"invalid/missing selection weights");
      }else require(r.has("weights")&&r.isNull("weights"),"scripted selection must stay owned by encounter");
      int range=integer(r,"projectileRange");require(range>=1&&range<=15,"projectile range out of bounds");
      List<NpcMagicElement> elements=new ArrayList<>();JSONArray el=r.getJSONArray("magicElements");
      for(int j=0;j<el.length();j++){String s=el.getString(j);NpcMagicElement e=NpcMagicElement.valueOf(s.equals("neutral")?"NONE":s.toUpperCase(Locale.ROOT));require(!elements.contains(e),"duplicate element");elements.add(e);}
      require(styles.contains(CombatStyle.MAGIC)?!elements.isEmpty():elements.isEmpty(),"element/style mismatch");
      PrayerCatalog.GodLine god=r.isNull("magicGod")?null:PrayerCatalog.GodLine.valueOf(r.getString("magicGod").toUpperCase(Locale.ROOT));
      require(god==null||styles.contains(CombatStyle.MAGIC),"god alignment without Magic");
      Set<String> creature=tags(r.getJSONArray("creatureTags"));JSONObject attacks=r.getJSONObject("attackTags");
      Set<String> attackKeys=new HashSet<>();for(CombatStyle s:styles)attackKeys.add(s.name().toLowerCase(Locale.ROOT));
      require(attacks.keySet().equals(attackKeys),"incomplete attack-tag block");
      for(String key:attackKeys){Set<String> t=tags(attacks.getJSONArray(key));require(t.contains("style."+key),"missing style tag");
        if(key.equals("magic")&&god!=null)require(t.contains("holy")&&t.contains("god."+god.name().toLowerCase(Locale.ROOT)),"missing aligned Magic tags");}
      String visual=r.getString("rangedVisual");require(Arrays.asList("arrow","bolt","dart","throwing_knife","naga_scimitar","acid_drop").contains(visual),"unknown projectile visual");
      r.getJSONArray("creatureAffinities");r.getJSONArray("specialAttacks");
      defs.put(id,new NpcCombatDefinition(id,original.getName(),power,defense,holy,boss,primary,styles,weights,range,generic,elements,god,visual,creature,r.toString()));
    }
    int count=0;while(server.getEntityHandler().getNpcDef(count)!=null){require(seen.contains(count),"missing canonical/explicit compatibility record for id "+count);count++;}
    require(seen.size()==count,"definition coverage mismatch");return new NpcCombatCatalog(defs,compat);
  }
}
