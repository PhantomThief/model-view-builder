/**
 * 
 */
package me.vela.model.builder.context;

import java.util.Map;
import java.util.Set;

/**
 * @author w.vela
 */
public interface BuildContext {

    public <K> Set<K> getIds(String type);

    public default <K, V> Set<K> getIds(Class<V> type) {
        return getIds(type.getName());
    }

    public <K, V> void putId(String type, K id);

    public default <K, V> void putId(Class<V> type, K id) {
        putId(type.getName(), id);
    }

    public <K> void putIds(String type, Iterable<K> ids);

    public default <K, V> void putIds(Class<V> type, Iterable<K> ids) {
        putIds(type.getName(), ids);
    }

    public Set<String> allValueTypes();

    public <K, V> Map<K, V> getData(String type);

    public default <K, V> Map<K, V> getData(Class<V> type) {
        return getData(type.getName());
    }

    public <K, V> void putData(String type, K id, V value);

    public default <K, V> void putData(Class<V> type, K id, V value) {
        putData(type.getName(), id, value);
    }

    public <K, V> void putDatas(String type, Map<K, V> values);

    public default <K, V> void putDatas(Class<V> type, Map<K, V> values) {
        putDatas(type.getName(), values);
    }

    public void merge(BuildContext buildContext);

}
