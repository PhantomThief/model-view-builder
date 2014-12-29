/**
 * 
 */
package me.vela.model.builder.context.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import me.vela.model.builder.context.BuildContext;

import com.google.common.collect.Iterables;

/**
 * <p>
 * DefaultBuildContextImpl class.
 * </p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public class DefaultBuildContextImpl implements BuildContext {

    private ConcurrentMap<String, Set<?>> ids = new ConcurrentHashMap<>();

    private ConcurrentMap<String, Map<?, ?>> datas = new ConcurrentHashMap<>();

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#getIds(java.lang.String)
     */
    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <K> Set<K> getIds(String type) {
        return (Set<K>) ids
                .computeIfAbsent(type, i -> Collections.synchronizedSet(new HashSet<>()));
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putId(java.lang.String, java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putId(String type, K id) {
        getIds(type).add(id);
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putIds(java.lang.String, java.lang.Iterable)
     */
    /** {@inheritDoc} */
    @Override
    public <K> void putIds(String type, Iterable<K> ids) {
        ids.forEach(id -> putId(type, id));
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#getData(java.lang.String)
     */
    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> getData(String type) {
        return (Map<K, V>) datas.computeIfAbsent(type, i -> new ConcurrentHashMap<>());
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putData(java.lang.String, java.lang.Object, java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putData(String type, K id, V value) {
        getData(type).put(id, value);

    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#putDatas(java.lang.String, java.util.Map)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putDatas(String type, Map<K, V> values) {
        getData(type).putAll(values);

    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#merge(me.vela.model.builder.context.BuildContext)
     */
    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void merge(BuildContext buildContext) {
        if (buildContext instanceof DefaultBuildContextImpl) {
            for (Entry<String, Set<?>> entry : ((DefaultBuildContextImpl) buildContext).ids
                    .entrySet()) {
                putIds(entry.getKey(), entry.getValue());
            }
            for (Entry entry : ((DefaultBuildContextImpl) buildContext).datas.entrySet()) {
                putDatas((String) entry.getKey(), (Map) entry.getValue());
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    /* (non-Javadoc)
     * @see me.vela.model.builder.context.BuildContext#allValueTypes()
     */
    /** {@inheritDoc} */
    @Override
    public Iterable<String> allValueTypes() {
        return Iterables.concat(ids.keySet(), datas.keySet());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "DefaultBuildContextImpl [ids=" + ids + ", datas=" + datas + "]";
    }

}
