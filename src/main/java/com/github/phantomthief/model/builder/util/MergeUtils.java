package com.github.phantomthief.model.builder.util;

import java.util.Map;
import java.util.Set;

/**
 * @author w.vela
 */
public final class MergeUtils {

    private MergeUtils() {
        throw new UnsupportedOperationException();
    }

    public static Map<Object, Object> merge(Map<Object, Object> map1, Map<Object, Object> map2) {
        map1.putAll(map2);
        return map1;
    }

    public static Set<Object> merge(Set<Object> set1, Set<Object> set2) {
        set1.addAll(set2);
        return set1;
    }
}
