/**
 * 
 */
package me.vela.model.builder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import me.vela.model.builder.annotation.ValueType;
import me.vela.model.builder.context.BuildContext;
import me.vela.model.builder.context.impl.DefaultBuildContextImpl;

import org.apache.commons.lang3.ClassUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * @author w.vela
 */
public class ModelBuilder {

    private final Multimap<Class<?>, Function<?, ?>> idExtractors = HashMultimap.create();

    private final Map<Function<?, ?>, Class<?>> functionValueMap = new HashMap<>();

    private final Multimap<Class<?>, Function<Collection<?>, Map<?, ?>>> dataBuilders = HashMultimap
            .create();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public BuildContext build(Collection<?> sources) {
        BuildContext finalBuildContext = new DefaultBuildContextImpl();

        while (!sources.isEmpty()) {
            BuildContext thisBuildContext = new DefaultBuildContextImpl();
            Set<?> newSources = new HashSet<>();
            for (Object t : sources) {
                Collection<Function<?, ?>> thisExtractors = getIdExtractors(t.getClass());

                // 提取数据
                for (Function idExtractor : thisExtractors) {
                    Object id = idExtractor.apply(t);
                    if (id == null) {
                        continue;
                    }
                    Class<?> valueMap = getValueMap(idExtractor);
                    if (finalBuildContext.getIds(valueMap).contains(id)) {
                        continue;
                    }

                    if (id instanceof Iterable) {
                        thisBuildContext.putIds(valueMap, (Iterable<?>) id);
                    } else {
                        thisBuildContext.putId(valueMap, id);
                    }
                }

                // 构建数据
                for (Class<?> valueType : thisBuildContext.allValueTypes()) {
                    for (Function<Collection<?>, Map<?, ?>> dataBuilder : dataBuilders
                            .get(valueType)) {
                        Map values = dataBuilder.apply(thisBuildContext.getIds(valueType));
                        thisBuildContext.putDatas(valueType, values);
                        newSources.addAll(values.values());
                    }
                }
            }

            finalBuildContext.merge(thisBuildContext);
            sources = newSources;
        }
        return finalBuildContext;
    }

    private final ConcurrentMap<Class<?>, Set<Function<?, ?>>> functionCache = new ConcurrentHashMap<>();

    private Collection<Function<?, ?>> getIdExtractors(Class<?> type) {
        return functionCache.computeIfAbsent(type, t -> {
            Set<Function<?, ?>> result = new HashSet<>();
            result.addAll(idExtractors.get(t));
            ClassUtils.getAllInterfaces(t).forEach(i -> result.addAll(idExtractors.get(i)));
            ClassUtils.getAllSuperclasses(t).forEach(i -> result.addAll(idExtractors.get(i)));
            return result;
        });
    }

    public BuildContext buildOne(Object one) {
        return build(Collections.singleton(one));
    }

    public <E> ModelBuilder addIdExtractor(Class<E> type, Function<E, ?> idExtractor) {
        idExtractors.put(type, idExtractor);
        return this;
    }

    public <E> ModelBuilder addIdExtractor(Class<E> type, Function<E, ?> idExtractor,
            Class<?> valueType) {
        addIdExtractor(type, idExtractor);
        functionValueMap.put(idExtractor, valueType);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E, K, V> ModelBuilder addDataBuilder(Class<E> type,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        ((Multimap) dataBuilders).put(type, dataBuilder);
        return this;
    }

    private Class<?> getValueMap(Function<?, ?> function) {
        Class<?> valueType = functionValueMap.get(function);
        if (valueType == null) {
            ValueType annotation = function.getClass().getAnnotation(ValueType.class);
            if (annotation != null && annotation.value() != null) {
                return annotation.value();
            }
        }
        return valueType;
    }

    @Override
    public String toString() {
        return "ModelBuilder [idExtractors=" + idExtractors + ", functionValueMap="
                + functionValueMap + ", dataBuilders=" + dataBuilders + ", functionCache="
                + functionCache + "]";
    }

}
