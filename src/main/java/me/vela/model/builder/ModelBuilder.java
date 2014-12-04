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
import java.util.function.BiFunction;
import java.util.function.Function;

import me.vela.model.builder.context.BuildContext;
import me.vela.model.builder.context.impl.DefaultBuildContextImpl;

import org.apache.commons.lang3.ClassUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * <p>
 * ModelBuilder class.
 * </p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public class ModelBuilder<B extends BuildContext> {

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
     * key:idName, values: dataBuilder
     */
    private final Multimap<String, BiFunction<B, Collection<?>, Map<?, ?>>> dataBuildersEx = HashMultimap
            .create();

    /**
     * key:dataBuilder, value: overrided valueName
     */
    private final Map<BiFunction<B, ?, ?>, String> buildToMapEx = new HashMap<>();

    /**
     * key: modelClass, values: valueExtractors
     */
    private final Multimap<Class<?>, Function<?, Map<?, ?>>> valueExtractors = HashMultimap
            .create();

    /**
     * <p>build.</p>
     *
     * @param sources a {@link java.util.Collection} object.
     * @param buildContext a B object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void build(Collection<?> sources, B buildContext) {

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
                        newSources.addAll((Collection) valueMap.values());
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
                    if (buildContext.getIds(valueMap).contains(id)) {
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
                    if (values == null) {
                        continue;
                    }
                    String toValueType = buildToMap.get(dataBuilder);
                    if (toValueType == null) {
                        toValueType = valueType;
                    }
                    thisBuildContext.putDatas(toValueType, values);
                    newSources.addAll(values.values());
                }

                for (BiFunction<B, Collection<?>, Map<?, ?>> dataBuilder : dataBuildersEx
                        .get(valueType)) {

                    Set<Object> thisIds = thisBuildContext.getIds(valueType);
                    Map values = dataBuilder.apply(buildContext, thisIds);
                    if (values == null) {
                        continue;
                    }
                    String toValueType = buildToMapEx.get(dataBuilder);
                    if (toValueType == null) {
                        toValueType = valueType;
                    }
                    thisBuildContext.putDatas(toValueType, values);
                    newSources.addAll(values.values());
                }
            }

            buildContext.merge(thisBuildContext);
            sources = newSources;
        }
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
     * <p>
     * buildOne.
     * </p>
     *
     * @param one a {@link java.lang.Object} object.
     * @param buildContext a B object.
     */
    public void buildOne(Object one, B buildContext) {
        build(Collections.singleton(one), buildContext);
    }

    /**
     * <p>
     * 添加id extractor
     * </p>
     *
     * @param modelType a {@link java.lang.Class} model的class.
     * @param modelIdExtractor a {@link java.util.function.Function}
     *        从model抽出valueId的方法
     * @param valueType a {@link java.lang.Class} 抽出的value的类型.
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder<B> addIdExtractor(Class<E> modelType, Function<E, ?> modelIdExtractor,
            Class<?> valueType) {
        return addIdExtractorWithName(modelType, modelIdExtractor, valueType.getName());
    }

    /**
     * <p>
     * 添加id extractor
     * </p>
     *
     * @param modelType a {@link java.lang.Class} model的class.
     * @param modelIdExtractor a {@link java.util.function.Function}
     *        从model抽出valueId的方法
     * @param valueIdName a {@link java.lang.String} 对应id的名字
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder<B> addIdExtractorWithName(Class<E> modelType,
            Function<E, ?> modelIdExtractor, String valueIdName) {
        idExtractors.put(modelType, modelIdExtractor);
        functionValueMap.put(modelIdExtractor, valueIdName);
        return this;
    }

    /**
     * <p>
     * addValueExtractor.
     * </p>
     *
     * @param modelType a {@link java.lang.Class} object.
     * @param valueExtractor a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E> ModelBuilder<B> addValueExtractor(Class<E> modelType,
            Function<E, Map<?, ?>> valueExtractor) {
        valueExtractors.put(modelType, valueExtractor);
        return this;
    }

    /**
     * <p>
     * addDataBuilder.
     * </p>
     *
     * @param valueType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder<B> addDataBuilder(Class<E> valueType,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        return addDataBuilder(valueType.getName(), dataBuilder);
    }

    /**
     * <p>addDataBuilderEx.</p>
     *
     * @param valueType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder<B> addDataBuilderEx(Class<E> valueType,
            BiFunction<B, Collection<K>, Map<K, V>> dataBuilder) {
        return addDataBuilderEx(valueType.getName(), dataBuilder);
    }

    /**
     * <p>
     * addDataBuilder.
     * </p>
     *
     * @param valueIdName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E, K, V> ModelBuilder<B> addDataBuilder(String valueIdName,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        ((Multimap) dataBuilders).put(valueIdName, dataBuilder);
        return this;
    }

    /**
     * <p>addDataBuilderEx.</p>
     *
     * @param valueIdName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E, K, V> ModelBuilder<B> addDataBuilderEx(String valueIdName,
            BiFunction<B, Collection<K>, Map<K, V>> dataBuilder) {
        ((Multimap) dataBuildersEx).put(valueIdName, dataBuilder);
        return this;
    }

    /**
     * <p>
     * addDataBuilder.
     * </p>
     *
     * @param modelType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder<B> addDataBuilderWithValueName(Class<E> modelType,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        return addDataBuilderWithValueName(modelType.getName(), dataBuilder, buildToValueName);
    }

    /**
     * <p>addDataBuilderWithValueNameEx.</p>
     *
     * @param modelType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> ModelBuilder<B> addDataBuilderWithValueNameEx(Class<E> modelType,
            BiFunction<B, Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        return addDataBuilderWithValueNameEx(modelType.getName(), dataBuilder, buildToValueName);
    }

    /**
     * <p>
     * addDataBuilder.
     * </p>
     *
     * @param idValueName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.Function} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <K, V> ModelBuilder<B> addDataBuilderWithValueName(String idValueName,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        ((Multimap) dataBuilders).put(idValueName, dataBuilder);
        buildToMap.put(dataBuilder, buildToValueName);
        return this;
    }

    /**
     * <p>addDataBuilderWithValueNameEx.</p>
     *
     * @param idValueName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <K, V> ModelBuilder<B> addDataBuilderWithValueNameEx(String idValueName,
            BiFunction<B, Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        ((Multimap) dataBuildersEx).put(idValueName, dataBuilder);
        buildToMapEx.put(dataBuilder, buildToValueName);
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
