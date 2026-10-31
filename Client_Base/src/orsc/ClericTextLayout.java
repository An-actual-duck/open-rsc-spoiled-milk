package orsc;
import java.util.*;
import java.util.function.ToIntFunction;
/** Shared bounded word wrapping for compact book/HUD and detailed guide text. */
public final class ClericTextLayout {
    private ClericTextLayout(){}
    public static List<String> wrap(String text,int width,ToIntFunction<String> measure){
        if(text==null||width<1)throw new IllegalArgumentException("Invalid text layout");
        ArrayList<String> rows=new ArrayList<String>();String line="";
        for(String word:text.trim().split("\\s+")){
            if(word.isEmpty())continue;
            if(!line.isEmpty()&&measure.applyAsInt(line+" "+word)>width){rows.add(line);line="";}
            while(measure.applyAsInt(word)>width&&word.length()>1){int end=word.length()-1;while(end>1&&measure.applyAsInt(word.substring(0,end))>width)end--;rows.add(word.substring(0,end));word=word.substring(end);}
            line=line.isEmpty()?word:line+" "+word;
        }
        if(!line.isEmpty())rows.add(line);return Collections.unmodifiableList(rows);
    }
}
