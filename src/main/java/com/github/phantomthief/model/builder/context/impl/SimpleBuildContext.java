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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * 
 * @author w.vela
 */
@SuppressWarnings("unchecked")
public class SimpleBuildContext implements BuildContext {

    private final ConcurrentMap<Object, Map<Object, Object>> datas = new ConcurrentHashMap<>();
    // key->target namespace, value->result holder
    private final ConcurrentMap<Object, Supplier<Object>> lazyDatas = new ConcurrentHashMap<>();

    @Override
    public <K, V> Map<K, V> getData(Object namespace) {
        return (Map<K, V>) datas.computeIfAbsent(namespace, i -> new ConcurrentHashMap<>());
    }

    @Override
    public <T> T getLazyNodeData(Object namespace) {
        Supplier<Object> supplier = lazyDatas.get(namespace);
        if (supplier == null) {
            throw new IllegalArgumentException("invalid lazy namespace:" + namespace);
        }
        return (T) supplier.get();
    }

    public void setupLazyNodeData(Object namespace,
            Function<BuildContext, Object> lazyBuildFunction) {
        lazyDatas.put(namespace, Suppliers.memoize(() -> lazyBuildFunction.apply(this)));
    }

    protected ConcurrentMap<Object, Map<Object, Object>> getDatas() {
        return datas;
    }

    @Override
    public void merge(BuildContext buildContext) {
        if (buildContext instanceof SimpleBuildContext) {
            SimpleBuildContext other = (SimpleBuildContext) buildContext;
            other.datas.forEach(
                    (namespace, values) -> datas.merge(namespace, values, MergeUtils::merge));
            other.lazyDatas.forEach((targetNamespace, valueHolder) -> lazyDatas
                    .putIfAbsent(targetNamespace, valueHolder));
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public String toString() {
        return reflectionToString(this, SHORT_PREFIX_STYLE);
    }
}
