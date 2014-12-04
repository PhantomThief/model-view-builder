/**
 * 
 */
package me.vela.model.builder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import me.vela.model.builder.context.BuildContext;
import me.vela.model.builder.context.impl.DefaultBuildContextImpl;

import org.apache.commons.lang3.ClassUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * <p>ModelBuilder class.</p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public class ModelBuilder {

    /**
     * key: modelClass, values: idExtractors
     */
    private final Multimap<Class<?>, Function<?, ?>> idExtractors = HashMultimap.create();

    /**
     * key:idExtractor, value: idName
     */
    private final Map<Function<?, ?>, String> functionValueMap = new HashMap<>();

    /**
     * key:idName, values: dataBuilder
     */
    private final Multimap<String, Function<Collection<?>, Map<?, ?>>> dataBuilders = HashMultimap
            .create();

    /**
     * key:dataBuilder, value: overrided valueName
     */
    private final Map<Function<?, ?>, String> buildToMap = new HashMap<>();

    /**
     * key: modelClass, values: valueExtractors
     */
    private final Multimap<Class<?>, Function<?, Map<?, ?>>> valueExtractors = HashMultimap
            .create();

    /**
     * <p>build.</p>
     *
     * @param sources a {@link java.util.Collection} object.
     * @return a {@link me.vela.model.builder.context.BuildContext} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public BuildContext build(Collection<?> sources) {
        BuildContext finalBuildContext = new DefaultBuildContextImpl();

        while (!sources.isEmpty()) {
            BuildContext thisBuildContext = new DefaultBuildContextImpl();
            Set<?> newSources = new HashSet<>();

            // 尝试提取所有values
            for (Object t : sources) {
                Collection<Function<?, Map<?, ?>>> thisValueExtractors = getValueExtractors(t
                        .getClass());
                for (Function valueExtractor : thisValueExtractors) {
                    Map<?, ?> valueMap = (Map<?, ?>) valueExtractor.apply(t);
                    if (valueMap != null) {
                        for (Entry<?, ?> entry : valueMap.entrySet()) {
                            thisBuildContext.putData((Class) entry.getValue().getClass(),
                                    entry.getKey(), entry.getValue());
                        }
                    }
                }

                Collection<Function<?, ?>> thisIdExtractors = getIdExtractors(t.getClass());
                // 提取数据
                for (Function idExtractor : thisIdExtractors) {
                    Object id = idExtractor.apply(t);
                    if (id == null) {
                        continue;
                    }
                    String valueMap = functionValueMap.get((Function<?, ?>) idExtractor);
                    if (valueMap != null && finalBuildContext.getIds(valueMap).contains(id)) {
                        continue;
                    }

                    if (id instanceof Iterable) {
                        thisBuildContext.putIds(valueMap, (Iterable<?>) id);
                    } else {
                        thisBuildContext.putId(valueMap, id);
                    }
                }

            }

            // 构建数据
            for (String valueType : thisBuildContext.allValueTypes()) {
                for (Function<Collection<?>, Map<?, ?>> dataBuilder : dataBuilders.get(valueType)) {

                    Set<Object> thisIds = thisBuildContext.getIds(valueType);
                    Map values = dataBuilder.apply(thisIds);
                    String toValueType = buildToMap.get(dataBuilder);
                    if (toValueType == null) {
                        toValueType = valueType;
                    }
                    thisBuildContext.putDatas(toValueType, values);
                    newSources.addAll(values.values());
                }
            }

            finalBuildContext.merge(thisBuildContext);
            sources = newSources;
        }
        return finalBuildContext;
    }

    private final ConcurrentMap<Class<?>, Set<Function<?, ?>>> idExtractorsCache = new ConcurrentHashMap<>();

    private Collection<Function<?, ?>> getIdExtractors(Class<?> type) {
        return idExtractorsCache.computeIfAbsent(type, t -> {
            Set<Function<?, ?>> result = new HashSet<>();
            result.addAll(idExtractors.get(t));
            ClassUtils.getAllInterfaces(t).forEach(i -> result.addAll(idExtractors.get(i)));
            ClassUtils.getAllSuperclasses(t).forEach(i -> result.addAll(idExtractors.get(i)));
            return result;
        });
    }

    private final ConcurrentMap<Class<?>, Set<Function<?, Map<?, ?>>>> valueExtractorsCache = new ConcurrentHashMap<>();

    private Collection<Function<?, Map<?, ?>>> getValueExtractors(Class<?> type) {
        return valueExtractorsCache.computeIfAbsent(type, t -> {
            Set<Function<?, Map<?, ?>>> result = new HashSet<>();
            result.addAll(valueExtractors.get(t));
            ClassUtils.getAllInterfaces(t).forEach(i -> result.addAll(valueExtractors.get(i)));
            ClassUtils.getAllSuperclasses(t).forEach(i -> result.addAll(valueExtractors.get(i)));
            return result;
        });
    }

    /**
     * <p>buildOne.</p>
     *
     * @param one a {@link java.lang.Object} object.
     * @return a {@link me.vela.model.builder.context.BuildContext} object.
     */
    public BuildContext buildOne(Object one) {
        return build(Collections.singleton(one));
    }

    /**
     * <p>addIdExtractor.</p>
     *
     * @param type a {@link java.lang.Class} object.
     * @param idExtractor a {@link java.util.function.Function} object.
     * @param valueType a {@link java.lang.Class} object.
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder addIdExtractor(Class<E> type, Function<E, ?> idExtractor,
            Class<?> valueType) {
        return addIdExtractor(type, idExtractor, valueType.getName());
    }

    /**
     * <p>addIdExtractor.</p>
     *
     * @param type a {@link java.lang.Class} object.
     * @param idExtractor a {@link java.util.function.Function} object.
     * @param valueType a {@link java.lang.String} object.
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder addIdExtractor(Class<E> type, Function<E, ?> idExtractor,
            String valueType) {
        idExtractors.put(type, idExtractor);
        functionValueMap.put(idExtractor, valueType);
        return this;
    }

    /**
     * <p>addValueExtractor.</p>
     *
     * @param type a {@link java.lang.Class} object.
     * @param valueExtractor a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder addValueExtractor(Class<E> type, Function<E, Map<?, ?>> valueExtractor) {
        valueExtractors.put(type, valueExtractor);
        return this;
    }

    /**
     * <p>addDataBuilder.</p>
     *
     * @param type a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder addDataBuilder(Class<E> type,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        return addDataBuilder(type.getName(), dataBuilder);
    }

    /**
     * <p>addDataBuilder.</p>
     *
     * @param type a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E, K, V> ModelBuilder addDataBuilder(String type,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        ((Multimap) dataBuilders).put(type, dataBuilder);
        return this;
    }

    /**
     * <p>addDataBuilder.</p>
     *
     * @param type a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param buildToType a {@link java.lang.String} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder addDataBuilder(Class<E> type,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToType) {
        return addDataBuilder(type.getName(), dataBuilder, buildToType);
    }

    /**
     * <p>addDataBuilder.</p>
     *
     * @param type a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param buildToType a {@link java.lang.String} object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <K, V> ModelBuilder addDataBuilder(String type,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToType) {
        ((Multimap) dataBuilders).put(type, dataBuilder);
        buildToMap.put(dataBuilder, buildToType);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ModelBuilder [idExtractors=" + idExtractors + ", functionValueMap="
                + functionValueMap + ", dataBuilders=" + dataBuilders + ", buildToMap="
                + buildToMap + ", valueExtractors=" + valueExtractors + ", idExtractorsCache="
                + idExtractorsCache + ", valueExtractorsCache=" + valueExtractorsCache + "]";
    }

}
