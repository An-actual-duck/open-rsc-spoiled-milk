package orsc;

import com.openrsc.client.entityhandling.defs.ClericComponentDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** The spellbook names effects; the Worship guide explains their mechanics. */
public final class ClericSpellSummary {
  private ClericSpellSummary() {}

  public static String describe(ClericSpellDef definition) {
    if (definition.getEffectRanks().isEmpty()) return "Pulls nearby party allies closer.";
    LinkedHashSet<String> names = new LinkedHashSet<String>();
    for (ClericComponentDef component : definition.getEffectRanks().get(0).getComponents())
      names.add(component.getName());
    List<String> effects = new ArrayList<String>(names);
    StringBuilder text = new StringBuilder(definition.isHostile() ? "Afflicts" : "Grants");
    text.append(" up to Tier ").append(definition.getSpellTier()).append(" ");
    for (int i = 0; i < effects.size(); i++) {
      if (i > 0) text.append(i == effects.size() - 1 ? " and " : ", ");
      text.append(effects.get(i));
    }
    return text.append('.').toString();
  }
}
