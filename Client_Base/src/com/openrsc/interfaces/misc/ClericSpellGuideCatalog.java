package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.*;
import java.util.*;
import java.util.function.IntFunction;
import orsc.ClericTextLayout;

/** Detailed numeric guide, derived from the same checked metadata as the HUD. */
public final class ClericSpellGuideCatalog {
    public static final int MAX_LINE_CHARACTERS=70;
    private ClericSpellGuideCatalog(){}
    public static List<Entry> build(List<ClericSpellDef> definitions){return build(definitions,id->"item #"+id);}
    public static List<Entry> build(List<ClericSpellDef> definitions,IntFunction<String> resourceName){
        if(definitions==null||definitions.isEmpty())return Collections.emptyList();
        ArrayList<ClericSpellDef> ordered=new ArrayList<ClericSpellDef>(definitions);
        Collections.sort(ordered,(a,b)->{int level=Integer.compare(a.getWorshipLevel(),b.getWorshipLevel());return level!=0?level:Integer.compare(a.getStableCode(),b.getStableCode());});
        ArrayList<Entry> entries=new ArrayList<Entry>();for(ClericSpellDef d:ordered)entries.add(format(d,resourceName));return Collections.unmodifiableList(entries);
    }
    private static Entry format(ClericSpellDef d,IntFunction<String> names){
        String header="Lvl "+d.getWorshipLevel()+"  "+d.getName()+" (Tier "+d.getSpellTier()+")";
        String area="Area: party allies within "+d.getRadius()+" tiles; "+(d.affectsCaster()?"includes caster":"caster excluded");
        ArrayList<String> lines=new ArrayList<String>();StringBuilder cost=new StringBuilder("Per cast: ");for(ClericCostDef c:d.getCosts()){if(cost.length()>10)cost.append(" + ");cost.append(c.getCount()).append(" ").append(names.apply(c.getItemId()));}add(lines,cost.toString());add(lines,area);
        if(d.getEffectRanks().isEmpty()){
            add(lines,d.getDescription());add(lines,"Fixed effect: no Holy Power scaling. Stops within 2 tiles.");
        }else{
            int cap=d.getEffectRanks().size();add(lines,"Holy Power = current Worship + strongest applicable holy staff.");
            add(lines,"P1-P"+cap+" thresholds: "+orsc.ClericPowerPresentation.thresholds(cap)+" Holy Power; tier cap P"+cap+".");
            add(lines,"Duration: "+(d.getEffectRanks().get(0).getDurationMilliseconds()/1000)+" sec. Values below follow P1-P"+cap+".");
            for(int component=0;component<d.getEffectRanks().get(0).getComponents().size();component++){
                ClericComponentDef first=d.getEffectRanks().get(0).getComponents().get(component);StringBuilder values=new StringBuilder();StringBuilder intervals=new StringBuilder();
                for(ClericEffectRankDef rank:d.getEffectRanks()){ClericComponentDef c=rank.getComponents().get(component);if(values.length()>0){values.append('/');intervals.append('/');}values.append(c.getMagnitude());intervals.append(c.getParameter1()/1000);}
                String v=values.toString(),text;switch(first.getKind()){
                    case 1:text="Natural Hits regeneration (% of normal): "+v;break;
                    case 2:text="Hits per pulse: "+v+"; "+first.getParameter2()+" pulses every "+(first.getParameter1()/1000)+" sec";break;
                    case 3:text="Extra poison cleansing per poison pulse: "+v;break;
                    case 4:text="Restore "+v+" point per drained non-Hits stat every "+intervals+" sec";break;
                    case 5:text="Melee/ranged/Magic defense (%): +"+v;break;
                    case 6:text="Maximum direct damage (%): +"+v;break;
                    case 7:text="Total direct damage dealt (%): +"+v;break;
                    case 8:text="Lifesteal (%): "+v+"; strictly below "+first.getParameter1()+"% Hits";break;
                    case 9:text="Direct damage reflected (%): "+v;break;
                    default:throw new IllegalArgumentException("Unsupported guide component");
                }add(lines,text);
            }
        }
        return new Entry(d.getStableCode(),header,area,d.getDescription(),d.getEffectRanks().isEmpty()?"No Holy Power scaling":"P1-P"+d.getEffectRanks().size(),lines);
    }
    private static void add(List<String> lines,String text){lines.addAll(ClericTextLayout.wrap(text,MAX_LINE_CHARACTERS,String::length));}
    public static final class Entry{
        private final int code;private final String header,area,mechanics,power;private final List<String> lines;
        Entry(int code,String header,String area,String mechanics,String power,List<String> lines){if(lines.size()>12)throw new IllegalArgumentException("Cleric guide exceeds bounded row");this.code=code;this.header=header;this.area=area;this.mechanics=mechanics;this.power=power;this.lines=Collections.unmodifiableList(new ArrayList<String>(lines));}
        public int getStableCode(){return code;}public String getHeader(){return header;}public String getArea(){return area;}public String getMechanics(){return mechanics;}public String getHolyPower(){return power;}public List<String> getLines(){return lines;}
    }
}
