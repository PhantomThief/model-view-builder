/**
 * 
 */
package me.vela.model.builder.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import me.vela.model.builder.ModelBuilder;
import me.vela.model.builder.context.BuildContext;
import me.vela.model.builder.context.impl.DefaultBuildContextImpl;

import org.apache.commons.lang3.ClassUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * <p>
 * DefaultModelBuilderImpl class.
 * </p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public class DefaultModelBuilderImpl<B extends BuildContext> implements ModelBuilder<B> {

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

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
     * key:valueExtractor, value:idName
     */
    private final Map<Function<?, ?>, String> valueExtractorIdNameMap = new HashMap<>();

    /**
     * key:valueExtractor, value:valueName
     */
    private final Map<Function<?, ?>, String> valueExtractorValueNameMap = new HashMap<>();

    /**
     * <p>
     * build.
     * </p>
     *
     * @param sources a {@link java.util.Collection} object.
     * @param buildContext a B object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void build(Collection<?> sources, B buildContext) {
        sources = new HashSet<>(sources);
        int i = 0;

        while (!sources.isEmpty()) {
            i++;

            BuildContext thisBuildContext = new DefaultBuildContextImpl();
            Set<?> newSources = new HashSet<>();

            Set<Object> valueExtracted = new HashSet<>();

            // 尝试提取所有values
            for (Object t : sources) {
                Collection<Function<?, Map<?, ?>>> thisValueExtractors = getValueExtractors(t
                        .getClass());
                for (Function valueExtractor : thisValueExtractors) {
                    Map<?, ?> valueMap = (Map<?, ?>) valueExtractor.apply(t);
                    if (valueMap != null) {
                        String idName = valueExtractorIdNameMap.get(valueExtractor);
                        String valueName = valueExtractorValueNameMap.get(valueExtractor);

                        for (Entry<?, ?> entry : valueMap.entrySet()) {
                            if (buildContext.getIds(idName).contains(entry.getKey())) {
                                logger.trace("第[{}]次构建，直接抽出value，忽略id[{}]:{}", i, idName,
                                        entry.getKey());
                                continue;
                            }
                            if (buildContext.getData(valueName).containsKey(entry.getKey())) {
                                logger.trace("第[{}]次构建，直接抽出value，忽略value[{}]:{}", i, valueName,
                                        entry.getKey());
                                continue;
                            }
                            logger.trace("第[{}]次构建，直接抽出value，直接抽出[{}]->[{}]:{}", i, idName,
                                    valueName, entry);

                            thisBuildContext.putData(valueName, entry.getKey(), entry.getValue());
                            thisBuildContext.putId(idName, entry.getKey());
                            valueExtracted.add(entry.getValue());
                        }
                    }
                }
            }

            logger.trace("第[{}]次构建，直接抽出value:{}", i, valueExtracted);

            sources.addAll((Collection) valueExtracted);

            for (Object t : sources) {
                Collection<Function<?, ?>> thisIdExtractors = getIdExtractors(t.getClass());
                // 提取数据
                for (Function idExtractor : thisIdExtractors) {
                    Object id = idExtractor.apply(t);
                    if (id == null) {
                        continue;
                    }
                    String valueMap = functionValueMap.get((Function<?, ?>) idExtractor);

                    if (id instanceof Iterable) {
                        for (Object thisId : (Iterable<?>) id) {
                            if (buildContext.getIds(valueMap).contains(thisId)) {
                                logger.trace("第[{}]次构建，抽出id，忽略id[{}]:{}", i, valueMap, thisId);
                                continue;
                            }
                            thisBuildContext.putId(valueMap, thisId);
                        }
                    } else {
                        if (buildContext.getIds(valueMap).contains(id)) {
                            logger.trace("第[{}]次构建，抽出id，忽略id[{}]:{}", i, valueMap, id);
                            continue;
                        }
                        thisBuildContext.putId(valueMap, id);
                    }
                }

            }

            // 构建数据
            for (String valueType : thisBuildContext.allValueTypes()) {
                for (Function<Collection<?>, Map<?, ?>> dataBuilder : dataBuilders.get(valueType)) {

                    Set<Object> thisIds = thisBuildContext.getIds(valueType);
                    String toValueType = buildToMap.get(dataBuilder);
                    if (toValueType == null) {
                        toValueType = valueType;
                    }
                    if (thisIds.removeAll(thisBuildContext.getData(toValueType).keySet())) {
                        logger.trace("第[{}]次构建，构建数据，忽略value[{}]，剩余id:{}", i, toValueType, thisIds);
                    }
                    if (thisIds.isEmpty()) {
                        continue;
                    }

                    Map values = dataBuilder.apply(thisIds);
                    if (values == null) {
                        continue;
                    }
                    thisBuildContext.putDatas(toValueType, values);
                    newSources.addAll(values.values());
                    logger.trace("第[{}]次构建，产生数据[{}]:{}", i, toValueType, values);
                }

                for (BiFunction<B, Collection<?>, Map<?, ?>> dataBuilder : dataBuildersEx
                        .get(valueType)) {

                    Set<Object> thisIds = thisBuildContext.getIds(valueType);
                    String toValueType = buildToMapEx.get(dataBuilder);
                    if (toValueType == null) {
                        toValueType = valueType;
                    }
                    if (thisIds.removeAll(thisBuildContext.getData(toValueType).keySet())) {
                        logger.trace("第[{}]次构建，构建数据EX，忽略value[{}]，剩余id:{}", i, toValueType, thisIds);
                    }
                    if (thisIds.isEmpty()) {
                        continue;
                    }

                    Map values = dataBuilder.apply(buildContext, thisIds);
                    if (values == null) {
                        continue;
                    }
                    thisBuildContext.putDatas(toValueType, values);
                    newSources.addAll(values.values());
                    logger.trace("第[{}]次构建，产生数据EX[{}]:{}", i, toValueType, values);
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
    public <E> DefaultModelBuilderImpl<B> addIdExtractor(Class<E> modelType,
            Function<E, ?> modelIdExtractor, Class<?> valueType) {
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
    public <E> DefaultModelBuilderImpl<B> addIdExtractorWithName(Class<E> modelType,
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
    public <E> DefaultModelBuilderImpl<B> addValueExtractor(Class<E> modelType,
            Function<E, Map<?, ?>> valueExtractor, String idName, String valueName) {
        valueExtractors.put(modelType, valueExtractor);
        valueExtractorIdNameMap.put(valueExtractor, idName);
        valueExtractorValueNameMap.put(valueExtractor, valueName);
        return this;
    }

    public <E> DefaultModelBuilderImpl<B> addValueExtractor(Class<E> modelType,
            Function<E, Map<?, ?>> valueExtractor, Class<?> idValueType) {
        valueExtractors.put(modelType, valueExtractor);
        valueExtractorIdNameMap.put(valueExtractor, idValueType.getName());
        valueExtractorValueNameMap.put(valueExtractor, idValueType.getName());
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
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilder(Class<E> valueType,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        return addDataBuilder(valueType.getName(), dataBuilder);
    }

    /**
     * <p>
     * addDataBuilderEx.
     * </p>
     *
     * @param valueType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilderEx(Class<E> valueType,
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
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilder(String valueIdName,
            Function<Collection<K>, Map<K, V>> dataBuilder) {
        ((Multimap) dataBuilders).put(valueIdName, dataBuilder);
        return this;
    }

    /**
     * <p>
     * addDataBuilderEx.
     * </p>
     *
     * @param valueIdName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilderEx(String valueIdName,
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
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilderWithValueName(Class<E> modelType,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        return addDataBuilderWithValueName(modelType.getName(), dataBuilder, buildToValueName);
    }

    /**
     * <p>
     * addDataBuilderWithValueNameEx.
     * </p>
     *
     * @param modelType a {@link java.lang.Class} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <E> a E object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    public <E, K, V> DefaultModelBuilderImpl<B> addDataBuilderWithValueNameEx(Class<E> modelType,
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
    public <K, V> DefaultModelBuilderImpl<B> addDataBuilderWithValueName(String idValueName,
            Function<Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        ((Multimap) dataBuilders).put(idValueName, dataBuilder);
        buildToMap.put(dataBuilder, buildToValueName);
        return this;
    }

    /**
     * <p>
     * addDataBuilderWithValueNameEx.
     * </p>
     *
     * @param idValueName a {@link java.lang.String} object.
     * @param dataBuilder a {@link java.util.function.BiFunction} object.
     * @param buildToValueName a {@link java.lang.String} object.
     * @param <K> a K object.
     * @param <V> a V object.
     * @return a {@link me.vela.model.builder.ModelBuilder} object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <K, V> DefaultModelBuilderImpl<B> addDataBuilderWithValueNameEx(String idValueName,
            BiFunction<B, Collection<K>, Map<K, V>> dataBuilder, String buildToValueName) {
        ((Multimap) dataBuildersEx).put(idValueName, dataBuilder);
        buildToMapEx.put(dataBuilder, buildToValueName);
        return this;
    }

    public static <B extends BuildContext> DefaultModelBuilderImpl<B> merge(
            @SuppressWarnings("unchecked") DefaultModelBuilderImpl<B>... other) {
        DefaultModelBuilderImpl<B> result = new DefaultModelBuilderImpl<>();
        if (other != null) {
            for (DefaultModelBuilderImpl<B> builder : other) {
                for (Entry<Class<?>, Function<?, ?>> entry : builder.idExtractors.entries()) {
                    result.idExtractors.put(entry.getKey(), entry.getValue());
                }
                for (Entry<Function<?, ?>, String> entry : builder.functionValueMap.entrySet()) {
                    result.functionValueMap.put(entry.getKey(), entry.getValue());
                }
                for (Entry<String, Function<Collection<?>, Map<?, ?>>> entry : builder.dataBuilders
                        .entries()) {
                    result.dataBuilders.put(entry.getKey(), entry.getValue());
                }
                for (Entry<Function<?, ?>, String> entry : builder.buildToMap.entrySet()) {
                    result.buildToMap.put(entry.getKey(), entry.getValue());
                }
                for (Entry<String, BiFunction<B, Collection<?>, Map<?, ?>>> entry : builder.dataBuildersEx
                        .entries()) {
                    result.dataBuildersEx.put(entry.getKey(), entry.getValue());
                }
                for (Entry<Class<?>, Function<?, Map<?, ?>>> entry : builder.valueExtractors
                        .entries()) {
                    result.valueExtractors.put(entry.getKey(), entry.getValue());
                }
                for (Entry<Function<?, ?>, String> entry : builder.valueExtractorIdNameMap
                        .entrySet()) {
                    result.valueExtractorIdNameMap.put(entry.getKey(), entry.getValue());
                }
                for (Entry<Function<?, ?>, String> entry : builder.valueExtractorValueNameMap
                        .entrySet()) {
                    result.valueExtractorValueNameMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "DefaultModelBuilderImpl [idExtractors=" + idExtractors + ", functionValueMap="
                + functionValueMap + ", dataBuilders=" + dataBuilders + ", buildToMap="
                + buildToMap + ", valueExtractors=" + valueExtractors + ", idExtractorsCache="
                + idExtractorsCache + ", valueExtractorsCache=" + valueExtractorsCache + "]";
    }

}
