/**
 * 
 */
package com.github.phantomthief.model.builder.context.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.phantomthief.model.builder.context.BuildContext;

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
     * @see com.github.phantomthief.model.builder.context.BuildContext#getIds(java.lang.String)
     */
    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <K> Set<K> getIds(String type) {
        return (Set<K>) ids.computeIfAbsent(type,
                i -> Collections.synchronizedSet(new HashSet<>()));
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#putId(java.lang.String, java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putId(String type, K id) {
        getIds(type).add(id);
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#putIds(java.lang.String, java.lang.Iterable)
     */
    /** {@inheritDoc} */
    @Override
    public <K> void putIds(String type, Iterable<K> ids) {
        ids.forEach(id -> putId(type, id));
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#getData(java.lang.String)
     */
    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <K, V> Map<K, V> getData(String type) {
        return (Map<K, V>) datas.computeIfAbsent(type, i -> new ConcurrentHashMap<>());
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#putData(java.lang.String, java.lang.Object, java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putData(String type, K id, V value) {
        if (value != null) {
            getData(type).put(id, value);
        }
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#putDatas(java.lang.String, java.util.Map)
     */
    /** {@inheritDoc} */
    @Override
    public <K, V> void putDatas(String type, Map<K, V> values) {
        Map<Object, Object> dataMap = getData(type);
        values.entrySet().stream() //
                .filter(entry -> entry.getValue() != null) //
                .forEach(entry -> dataMap.put(entry.getKey(), entry.getValue()));
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.context.BuildContext#merge(com.github.phantomthief.model.builder.context.BuildContext)
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
     * @see com.github.phantomthief.model.builder.context.BuildContext#allValueTypes()
     */
    /** {@inheritDoc} */
    @Override
    public Set<String> allValueTypes() {
        return Stream.of(ids.keySet(), datas.keySet()).flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "DefaultBuildContextImpl [ids=" + ids + ", datas=" + datas + "]";
    }

}
