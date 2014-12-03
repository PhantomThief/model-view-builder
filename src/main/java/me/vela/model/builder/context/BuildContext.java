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

    public <K, V> Set<K> getIds(Class<V> type);

    public <K, V> void putId(Class<V> type, K id);

    public <K, V> void putIds(Class<V> type, Iterable<K> ids);

    public Set<Class<?>> allValueTypes();

    public <K, V> Map<K, V> getData(Class<V> type);

    public <K, V> void putData(Class<V> type, K id, V value);

    public <K, V> void putDatas(Class<V> type, Map<K, V> values);

    public void merge(BuildContext buildContext);

}
