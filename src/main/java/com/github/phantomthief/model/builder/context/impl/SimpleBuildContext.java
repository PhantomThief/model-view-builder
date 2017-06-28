/**
 * 
 */
package com.github.phantomthief.model.builder.context.impl;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.github.phantomthief.model.builder.context.BuildContext;
import com.github.phantomthief.model.builder.util.MergeUtils;

/**
 * 
 * @author w.vela
 */
@SuppressWarnings("unchecked")
public class SimpleBuildContext implements BuildContext {

    private final ConcurrentMap<Object, Map<Object, Object>> datas;
    private final ConcurrentMap<Object, Map<Object, Object>> lazyDatas = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, Function<BuildContext, Map<Object, Object>>> lazyBuilders = new ConcurrentHashMap<>();

    public SimpleBuildContext() {
        this(new ConcurrentHashMap<>());
    }

    // for test case
    public SimpleBuildContext(ConcurrentMap<Object, Map<Object, Object>> datas) {
        this.datas = datas;
    }

    @Override
    public <K, V> Map<K, V> getData(Object namespace) {
        Function<BuildContext, Map<Object, Object>> lazyBuilder = lazyBuilders.get(namespace);
        if (lazyBuilder != null) {
            return (Map<K, V>) lazyDatas.computeIfAbsent(namespace, ns -> lazyBuilder.apply(this));
        } else {
            return (Map<K, V>) datas.computeIfAbsent(namespace, ns -> new ConcurrentHashMap<>());
        }
    }

    public void setupLazyNodeData(Object namespace,
            Function<BuildContext, Map<Object, Object>> lazyBuildFunction) {
        lazyBuilders.put(namespace, lazyBuildFunction);
    }

    @Override
    public void merge(BuildContext buildContext) {
        if (buildContext instanceof SimpleBuildContext) {
            SimpleBuildContext other = (SimpleBuildContext) buildContext;
            other.datas.forEach(
                    (namespace, values) -> datas.merge(namespace, values, MergeUtils::merge));
            other.lazyBuilders.forEach(lazyBuilders::putIfAbsent);
            lazyBuilders.keySet().forEach(key -> {
                datas.remove(key);
                lazyDatas.remove(key);
            });
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        return reflectionToString(this, SHORT_PREFIX_STYLE);
    }
}
