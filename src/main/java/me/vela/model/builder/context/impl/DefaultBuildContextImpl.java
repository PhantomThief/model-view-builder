/**
 * 
 */
package me.vela.model.builder.context.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import me.vela.model.builder.context.BuildContext;

/**
 * @author w.vela
 */
public class DefaultBuildContextImpl implements BuildContext {

    private ConcurrentMap<Class<?>, Set<?>> ids = new ConcurrentHashMap<>();

    private ConcurrentMap<Class<?>, Map<?, ?>> datas = new ConcurrentHashMap<>();

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#getIds(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Set<K> getIds(Class<V> type) {
        return (Set<K>) ids
                .computeIfAbsent(type, i -> Collections.synchronizedSet(new HashSet<>()));
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putId(java.lang.Class, java.lang.Object)
     */
    @Override
    public <K, V> void putId(Class<V> type, K id) {
        getIds(type).add(id);
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putIds(java.lang.Class, java.lang.Iterable)
     */
    @Override
    public <K, V> void putIds(Class<V> type, Iterable<K> ids) {
        Set<Object> set = getIds(type);
        if (ids instanceof Collection) {
            set.addAll((Collection<? extends Object>) ids);
        } else {
            ids.forEach(set::add);
        }
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#getData(java.lang.Class)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> getData(Class<V> type) {
        return (Map<K, V>) datas.computeIfAbsent(type, i -> new ConcurrentHashMap<>());
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putData(java.lang.Class, java.lang.Object, java.lang.Object)
     */
    @Override
    public <K, V> void putData(Class<V> type, K id, V value) {
        getData(type).put(id, value);
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putDatas(java.lang.Class, java.util.Map)
     */
    @Override
    public <K, V> void putDatas(Class<V> type, Map<K, V> values) {
        getData(type).putAll(values);
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#merge(me.vela.model.builder.context.BuildContext)
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void merge(BuildContext buildContext) {
        if (buildContext instanceof DefaultBuildContextImpl) {
            for (Entry<Class<?>, Set<?>> entry : ((DefaultBuildContextImpl) buildContext).ids
                    .entrySet()) {
                putIds(entry.getKey(), entry.getValue());
            }
            for (Entry entry : ((DefaultBuildContextImpl) buildContext).datas.entrySet()) {
                putDatas((Class) entry.getKey(), (Map) entry.getValue());
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#allValueTypes()
     */
    @Override
    public Set<Class<?>> allValueTypes() {
        return ids.keySet();
    }

    @Override
    public String toString() {
        return "DefaultBuildContextImpl [ids=" + ids + ", datas=" + datas + "]";
    }

}
