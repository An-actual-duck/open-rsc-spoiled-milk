package orsc;

import com.openrsc.client.entityhandling.defs.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Atomic, bounds-checked schema-3 decoder. No partial catalog is exposed. */
public final class ClericSpellbookPacketDecoder {
    private ClericSpellbookPacketDecoder() {}
    public static List<ClericSpellDef> decode(byte[] payload) {
        Cursor in=new Cursor(payload);
        int schema=in.u8(),count=in.u8();
        if(schema!=ClericBlessingContract.SCHEMA_VERSION||count>ClericBlessingContract.MAX_DEFINITIONS)
            throw new IllegalArgumentException("Unsupported Cleric catalog schema or count");
        ArrayList<ClericSpellDef> definitions=new ArrayList<ClericSpellDef>(count);
        for(int n=0;n<count;n++) {
            int code=in.u16();String key=in.text(128),name=in.text(128),description=in.text(2048),alignment=in.text(32);
            int worship=in.u8(),tier=in.u8(),radius=in.u8(),caster=in.u8();
            if(caster>1)throw new IllegalArgumentException("Invalid Cleric caster flag");
            int icon=in.u16(),costCount=in.u8();
            if(costCount<1||costCount>ClericBlessingContract.MAX_COSTS)throw new IllegalArgumentException("Invalid Cleric cost count");
            ArrayList<ClericCostDef> costs=new ArrayList<ClericCostDef>(costCount);
            for(int c=0;c<costCount;c++)costs.add(new ClericCostDef(in.u16(),in.u8()));
            int casterIcon=in.i32(),animation=in.i32();String statusIcon=in.text(128);int rankCount=in.u8();
            if(rankCount>6)throw new IllegalArgumentException("Invalid Cleric rank count");
            ArrayList<ClericEffectRankDef> ranks=new ArrayList<ClericEffectRankDef>(rankCount);
            for(int r=0;r<rankCount;r++) {
                int rank=in.u8(),duration=in.i32(),kind=in.u8(),counter=in.u8(),initial=in.u16(),primary=in.u16(),secondary=in.u16(),components=in.u8();
                if(kind!=ClericBlessingContract.BLESSING_PRESENTATION||components<1||components>ClericBlessingContract.MAX_COMPONENTS||duration>3600000)
                    throw new IllegalArgumentException("Invalid Cleric composite rank");
                ArrayList<ClericComponentDef> parts=new ArrayList<ClericComponentDef>(components);
                for(int c=0;c<components;c++)parts.add(new ClericComponentDef(in.u8(),in.u16(),in.u16(),in.u16()));
                ranks.add(new ClericEffectRankDef(rank,duration,kind,counter,initial,primary,secondary,parts));
            }
            if(!ranks.isEmpty()&&(ranks.size()!=tier*2||ranks.get(0).getSecondaryMagnitude()!=tier))throw new IllegalArgumentException("Invalid Cleric tier cap");
            for(ClericEffectRankDef rank:ranks)if(rank.getSecondaryMagnitude()!=tier)throw new IllegalArgumentException("Mismatched Cleric tier");
            definitions.add(new ClericSpellDef(code,key,name,description,alignment,worship,tier,radius,caster==1,icon,costs,casterIcon,animation,statusIcon,ranks));
        }
        if(in.remaining()!=0)throw new IllegalArgumentException("Trailing Cleric catalog bytes");
        ClericSpellbookCatalog checked=new ClericSpellbookCatalog();checked.replace(schema,definitions);return checked.snapshot();
    }
    private static final class Cursor {
        private final byte[] data;private int offset;
        Cursor(byte[] bytes){if(bytes==null||bytes.length>65535)throw new IllegalArgumentException("Invalid Cleric packet size");data=bytes;}
        int remaining(){return data.length-offset;}
        int u8(){if(remaining()<1)throw new IllegalArgumentException("Truncated Cleric catalog");return data[offset++]&255;}
        int u16(){return u8()<<8|u8();}
        int i32(){return u8()<<24|u8()<<16|u8()<<8|u8();}
        String text(int max){int start=offset;while(true){int c=u8();if(c==10)break;if(c<32||offset-start>max)throw new IllegalArgumentException("Invalid Cleric text");}return new String(data,start,offset-start-1,StandardCharsets.ISO_8859_1);}
    }
}
