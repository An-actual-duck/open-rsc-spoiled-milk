package com.openrsc.server.content.cleric;

import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.util.*;

/** Active authoritative book; retired stable codes remain reserved and fail closed. */
public final class ClericSpellCatalog {
    public static final int SCHEMA_VERSION = ClericBlessingContract.SCHEMA_VERSION;
    public static final int MAX_LAUNCH_HOLY_POWER = 163;
    private static final List<ClericSpellDefinition> DEFINITIONS;
    private static final Map<Integer,ClericSpellDefinition> BY_CODE = new HashMap<Integer,ClericSpellDefinition>();
    static {
        List<ClericSpellDefinition> defs = new ArrayList<ClericSpellDefinition>();
        defs.add(make(ClericSpellId.UNIFY,"Unify","Draws nearby party allies closer.",ClericAlignment.NEUTRAL,3,1,3,"unify",-1));
        String[][] names={{"Saradomin's Warmth","Saradomin's Aura","Saradomin's Resplendence"},{"Guthix's Respite","Guthix's Protection","Guthix's Vengeance"},{"Zamorak's Fervor","Zamorak's Fury","Zamorak's Wrath"}};
        String[] descriptions={"Speeds allies' natural regeneration.","Cleanses allies' poison and restores drained stats.","Raises allies' maximum direct damage."};
        ClericAlignment[] gods={ClericAlignment.SARADOMIN,ClericAlignment.GUTHIX,ClericAlignment.ZAMORAK};
        String[][] icons={{"mend","greater-mend","aegis"},{"purify","restore","thorns"},{"fervor","zeal","rally"}};
        int[][] animations={{67,72,75},{69,70,74},{68,73,76}};
        for(int tier=1;tier<=3;tier++)for(int god=0;god<3;god++) {
            ClericSpellId id=ClericSpellId.fromCode(12+god*3+tier-1);
            defs.add(make(id,names[god][tier-1],descriptions[god]+(tier>1?" Includes earlier blessing components.":""),gods[god],tier==1?1:tier==2?20:40,tier,tier+1,icons[god][tier-1],animations[god][tier-1]));
        }
        defs.add(make(ClericSpellId.GREATER_UNIFY,"Greater Unify","Draws a wider group of party allies closer.",ClericAlignment.NEUTRAL,30,2,5,"unify",-1));
        for(ClericSpellDefinition d:defs)if(BY_CODE.put(d.getStableCode(),d)!=null)throw new IllegalStateException("Duplicate spell code");
        DEFINITIONS=Collections.unmodifiableList(defs);
    }
    private static ClericSpellDefinition make(ClericSpellId id,String name,String description,ClericAlignment god,int level,int tier,int radius,String icon,int animation) {
        int[] thresholds=id.isBlessing()?Arrays.copyOf(new int[]{0,20,45,75,115,160},tier*2):new int[]{0};
        return new ClericSpellDefinition(id,name,description,god,level,tier,radius,false,ClericSigilCost.forLaunchTier(tier),new ClericSpellPresentation(ClericSigilItemId.get(ClericSigilMaterial.STONE,god,true).getItemId(),icon,-1,animation),thresholds);
    }
    private ClericSpellCatalog(){}
    public static List<ClericSpellDefinition> getAll(){return DEFINITIONS;}
    public static ClericSpellDefinition get(ClericSpellId id){if(id==null)throw new IllegalArgumentException("Missing spell");return fromCode(id.getCode());}
    public static ClericSpellDefinition fromCode(int code){ClericSpellDefinition d=BY_CODE.get(code);if(d==null)throw new IllegalArgumentException("Retired or unknown Cleric spell: "+code);return d;}
    public static ClericSpellDefinition fromKey(String key){for(ClericSpellDefinition d:DEFINITIONS)if(d.getStableKey().equals(key))return d;throw new IllegalArgumentException("Retired or unknown Cleric key");}
}
