/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/reflection/Rs2Reflection.java.
 * Mobile: only the ground-item actions read is ported (verbatim). Upstream's invokeMenu and setItemId inject
 * menu actions through the client's internals, outside Microbot Mobile's controlled input path, so they are
 * not part of this file. BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;

/**
 * As upstream: reads an item's ground ("floor") menu actions, which the RuneLite API does not expose, by
 * discovering the client's field layout through reflection (an object field holding a list of strings, or of
 * beans with one string field), caching it per item class. Falls back to upstream's default
 * {@code [null, null, "Take", null, null]} when the layout is not found.
 */
@Slf4j
public class Rs2Reflection {
    private Rs2Reflection() {
    }

    private static volatile Field cachedOuterField;
    private static volatile Field cachedListField;
    private static volatile Field cachedStringField;
    private static volatile Class<?> cachedGroundItemClass;

    /** As upstream: the ground actions of {@code item}, or upstream's default when they cannot be read. */
    public static String[] getGroundItemActions(ItemComposition item) {
        return getGroundItemActionsFromObject(item);
    }

    @SneakyThrows
    static String[] getGroundItemActionsFromObject(Object item) {
        if (item == null) return new String[]{};
        Class<?> itemClass = item.getClass();
        if (cachedGroundItemClass != itemClass) {
            resetGroundItemActionCache();
            cachedGroundItemClass = itemClass;
        }

        if (cachedOuterField != null && cachedListField != null) {
            try {
                return extractWithCache(item);
            } catch (Exception e) {
                log.warn("Ground item action cache invalidated, re-discovering");
                resetGroundItemActionCache();
            }
        }

        for (Class<?> clazz = item.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field outerField : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(outerField.getModifiers()) || outerField.isSynthetic()) continue;

                Class<?> type = outerField.getType();
                if (type.isPrimitive() || type == String.class || type.isArray()
                        || type.getName().startsWith("java.") || type.getName().startsWith("net.runelite.")) continue;

                outerField.setAccessible(true);
                Object outerValue = outerField.get(item);
                outerField.setAccessible(false);
                if (outerValue == null) continue;

                for (Field listField : outerValue.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(listField.getModifiers()) || listField.isSynthetic()) continue;
                    if (!List.class.isAssignableFrom(listField.getType())) continue;

                    listField.setAccessible(true);
                    Object listObj = listField.get(outerValue);
                    listField.setAccessible(false);
                    if (!(listObj instanceof List)) continue;

                    List<?> list = (List<?>) listObj;
                    if (list.isEmpty()) continue;

                    Object first = null;
                    for (Object el : list) {
                        if (el != null) { first = el; break; }
                    }
                    if (first == null) continue;

                    if (first instanceof String) {
                        cachedOuterField = outerField;
                        cachedListField = listField;
                        cachedStringField = null;
                        return groundItemActionsOrDefault(toStringArray(list));
                    }

                    Field stringField = null;
                    for (Field f : first.getClass().getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && f.getType() == String.class) {
                            stringField = f;
                            break;
                        }
                    }
                    if (stringField == null) continue;

                    cachedOuterField = outerField;
                    cachedListField = listField;
                    cachedStringField = stringField;
                    return groundItemActionsOrDefault(extractFromBeans(list, stringField));
                }
            }
        }

        return defaultGroundItemActions();
    }

    static void resetGroundItemActionCache() {
        cachedGroundItemClass = null;
        cachedOuterField = null;
        cachedListField = null;
        cachedStringField = null;
    }

    private static String[] extractWithCache(Object item) throws Exception {
        cachedOuterField.setAccessible(true);
        Object outer = cachedOuterField.get(item);
        cachedOuterField.setAccessible(false);
        if (outer == null) return defaultGroundItemActions();

        cachedListField.setAccessible(true);
        Object listObj = cachedListField.get(outer);
        cachedListField.setAccessible(false);
        if (!(listObj instanceof List)) return defaultGroundItemActions();

        List<?> list = (List<?>) listObj;
        if (cachedStringField == null) return groundItemActionsOrDefault(toStringArray(list));
        return groundItemActionsOrDefault(extractFromBeans(list, cachedStringField));
    }

    private static String[] groundItemActionsOrDefault(String[] actions) {
        if (actions == null || actions.length == 0) return defaultGroundItemActions();
        for (String action : actions) {
            if (action != null && !action.isBlank()) return actions;
        }
        return defaultGroundItemActions();
    }

    private static String[] defaultGroundItemActions() {
        return new String[]{null, null, "Take", null, null};
    }

    private static String[] toStringArray(List<?> list) {
        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object el = list.get(i);
            result[i] = el instanceof String ? (String) el : null;
        }
        return result;
    }

    private static String[] extractFromBeans(List<?> list, Field stringField) throws Exception {
        String[] result = new String[list.size()];
        stringField.setAccessible(true);
        for (int i = 0; i < list.size(); i++) {
            Object bean = list.get(i);
            if (bean != null) {
                Object val = stringField.get(bean);
                result[i] = val instanceof String ? (String) val : null;
            }
        }
        stringField.setAccessible(false);
        return result;
    }

}
