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
            return computeIfAbsent(lazyDatas, namespace, ns -> lazyBuilder.apply(this));
        } else {
            return computeIfAbsent(datas, namespace, ns -> new ConcurrentHashMap<>());
        }
    }

    /**
     * Workaround to fix ConcurrentHashMap stuck bug when call {@link ConcurrentHashMap#computeIfAbsent} recursively.
     * see https://bugs.openjdk.java.net/browse/JDK-8062841.
     */
    private static <K, V> Map<K, V> computeIfAbsent(ConcurrentMap<Object, Map<Object, Object>> map, Object key,
                                                    Function<Object, Map<Object, Object>> function) {
        Map<Object, Object> value = map.get(key);
        if (value == null) {
            value = function.apply(key);
            map.put(key, value);
        }
        return (Map<K, V>) value;
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
