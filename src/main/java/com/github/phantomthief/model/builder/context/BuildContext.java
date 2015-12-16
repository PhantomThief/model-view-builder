/**
 * 
 */
package com.github.phantomthief.model.builder.context;

import java.util.Map;

/**
 * @author w.vela
 */
public interface BuildContext {

    default <K, V> Map<K, V> getData(Class<V> type) {
        return getData((Object) type);
    }

    <K, V> Map<K, V> getData(Object namespace);

    <K, V> Map<K, V> getLazyNodeData(Object namespace);

    void merge(BuildContext buildContext);
}
