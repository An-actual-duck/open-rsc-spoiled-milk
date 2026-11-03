package com.openrsc.server.combat;

import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.ItemVisualAppearances;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Real definition loading, indexing and visual-only override isolation. */
public final class ItemVisualConsistencyFixture {
    public static void main(String[] args) throws Exception {
        try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
            com.openrsc.server.external.EntityHandler handler = harness.server().getEntityHandler();
            Method apply = handler.getClass().getDeclaredMethod("applyRecoveredEquipmentAppearances");
            apply.setAccessible(true);
            Field appearance = ItemDefinition.class.getDeclaredField("appearanceId");
            appearance.setAccessible(true);
            Map<Integer, Map<String, Object>> metadata = new HashMap<>();
            Map<Integer, Integer> appearances = new HashMap<>();
            for (int id = 0; id < handler.items.size(); id++) {
                ItemDefinition item = handler.getItemDef(id);
                if (item == null) continue;
                metadata.put(id, fields(item));
                appearances.put(id, item.getAppearanceId());
            }
            for (ItemVisualAppearances.Mapping mapping : ItemVisualAppearances.MAPPINGS) {
                ItemDefinition item = handler.getItemDef(mapping.itemId);
                check(item.getAppearanceId() == mapping.appearanceId, "effective visual " + mapping.itemId);
                appearance.setInt(item, mapping.legacyAppearanceId);
            }
            harness.server().getConfig().WANT_MYWORLD = false;
            apply.invoke(handler);
            assertLegacy(handler);
            harness.server().getConfig().WANT_MYWORLD = true;
            harness.server().getConfig().WANT_CUSTOM_SPRITES = false;
            apply.invoke(handler);
            assertLegacy(handler);
            harness.server().getConfig().WANT_CUSTOM_SPRITES = true;
            apply.invoke(handler);
            for (Integer id : metadata.keySet()) {
                check(metadata.get(id).equals(fields(handler.getItemDef(id))), "gameplay metadata " + id);
                check(appearances.get(id) == handler.getItemDef(id).getAppearanceId(), "unrelated appearance " + id);
            }
            for (int i = 0; i < 15; i++)
                check(handler.getItemDef(3411 + i).getAppearanceId() == 1098 + i, "coating index " + i);
            System.out.println("PASS 328 repaired appearances, all-item gameplay invariance, mode isolation and 15 coated weapon indices");
        }
    }

    private static void assertLegacy(com.openrsc.server.external.EntityHandler handler) {
        for (ItemVisualAppearances.Mapping mapping : ItemVisualAppearances.MAPPINGS)
            check(handler.getItemDef(mapping.itemId).getAppearanceId() == mapping.legacyAppearanceId,
                "profile isolation " + mapping.itemId);
    }

    private static Map<String, Object> fields(ItemDefinition item) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        for (Class<?> type = item.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("appearanceId")) continue;
                field.setAccessible(true);
                result.put(field.getName(), field.get(item));
            }
        }
        return result;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
