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
import java.util.function.Supplier;

import com.github.phantomthief.model.builder.context.BuildContext;
import com.github.phantomthief.model.builder.util.MergeUtils;

/**
 * 
 * @author w.vela
 */
@SuppressWarnings("unchecked")
public class SimpleBuildContext implements BuildContext {

    private final ConcurrentMap<Object, Map<Object, Object>> datas = new ConcurrentHashMap<>();

    private final ConcurrentMap<Object, Supplier<Map<Object, Object>>> lazyBuilders = new ConcurrentHashMap<>();

    @Override
    public <K, V> Map<K, V> getData(Object namespace) {
        return (Map<K, V>) datas.computeIfAbsent(namespace, ns -> {
            Supplier<Map<Object, Object>> supplier = lazyBuilders.get(namespace);
            if (supplier != null) {
                return supplier.get();
            } else {
                return new ConcurrentHashMap<>();
            }
        });
    }

    public void setupLazyNodeData(Object namespace,
            Function<BuildContext, Map<Object, Object>> lazyBuildFunction) {
        lazyBuilders.put(namespace, () -> lazyBuildFunction.apply(this));
    }

    @Override
    public void merge(BuildContext buildContext) {
        if (buildContext instanceof SimpleBuildContext) {
            SimpleBuildContext other = (SimpleBuildContext) buildContext;
            other.datas.forEach((namespace, values) -> datas.merge(namespace, values,
                    MergeUtils::merge));
            other.lazyBuilders.forEach((targetNamespace, builder) -> lazyBuilders.putIfAbsent(
                    targetNamespace, builder));
            lazyBuilders.keySet().forEach(datas::remove);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        return reflectionToString(this, SHORT_PREFIX_STYLE);
    }
}
