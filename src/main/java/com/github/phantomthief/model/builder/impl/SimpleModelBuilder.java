package com.github.phantomthief.model.builder.impl;

import static com.google.common.collect.HashMultimap.create;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.ClassUtils.getAllInterfaces;
import static org.apache.commons.lang3.ClassUtils.getAllSuperclasses;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.slf4j.LoggerFactory.getLogger;

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

import org.slf4j.Logger;

import com.github.phantomthief.model.builder.ModelBuilder;
import com.github.phantomthief.model.builder.context.BuildContext;
import com.github.phantomthief.model.builder.context.impl.SimpleBuildContext;
import com.github.phantomthief.model.builder.util.MergeUtils;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * @author w.vela
 */
@SuppressWarnings("unchecked")
public class SimpleModelBuilder<B extends BuildContext> implements ModelBuilder<B> {

    private static final Logger logger = getLogger(SimpleModelBuilder.class);

    // obj.class=>obj->(namespace,ids)
    private final SetMultimap<Class<?>, Function<Object, KeyPair<Set<Object>>>> idExtractors = create();
    // obj.class=>obj->(namespace,values)
    private final SetMultimap<Class<?>, Function<Object, KeyPair<Map<Object, Object>>>> valueExtractors = create();
    // idNamespace=>(valueNamespace, ids->values)
    private final SetMultimap<Object, KeyPair<BiFunction<B, Collection<Object>, Map<Object, Object>>>> valueBuilders = create();
    // targetNamespace=>Function<BuildContext, Object>
    private final Map<Object, Function<BuildContext, Map<Object, Object>>> lazyBuilders = new HashMap<>();

    private final ConcurrentMap<Class<?>, Set<Function<Object, KeyPair<Set<Object>>>>> cachedIdExtractors = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Set<Function<Object, KeyPair<Map<Object, Object>>>>> cachedValueExtractors = new ConcurrentHashMap<>();

    private volatile boolean alreadyBuilt = false;
    private Runnable onConflictListener;

    @Override
    public void buildMulti(Iterable<?> sources, B buildContext) {
        alreadyBuilt = true;
        if (sources == null) {
            return;
        }
        if (buildContext instanceof SimpleBuildContext) {
            SimpleBuildContext simpleBuildContext = (SimpleBuildContext) buildContext;
            lazyBuilders.forEach(simpleBuildContext::setupLazyNodeData);
        }

        Set<Object> pendingForBuilding = Sets.newHashSet(sources);

        while (!pendingForBuilding.isEmpty()) {
            // namespace->ids
            Map<Object, Set<Object>> idsMap = new HashMap<>();
            // namespace->values
            Map<Object, Map<Object, Object>> valuesMap = new HashMap<>();

            for (Object object : pendingForBuilding) {
                extract(object, buildContext, idsMap, valuesMap);
            }

            valueBuild(idsMap, valuesMap, buildContext);
            mergeToBuildContext(valuesMap, buildContext);


            Set<Object> newPendingForBuilding = new HashSet<>();
            for (Map<Object, Object> pending : valuesMap.values()) {
                newPendingForBuilding.addAll(pending.values());
            }
            pendingForBuilding = newPendingForBuilding;
        }
    }

    public SimpleModelBuilder<B> onConflictCheckListener(Runnable listener) {
        onConflictListener = listener;
        return this;
    }

    private void mergeToBuildContext(Map<Object, Map<Object, Object>> valuesMap, B buildContext) {
        for (Entry<Object, Map<Object, Object>> entry : valuesMap.entrySet()) {
            buildContext.getData(entry.getKey()).putAll(entry.getValue());
        }
    }

    private void valueBuild(Map<Object, Set<Object>> idsMap,
            Map<Object, Map<Object, Object>> valuesMap, B buildContext) {
        for (Entry<Object, Set<Object>> entry : idsMap.entrySet()) {
            for (KeyPair<BiFunction<B, Collection<Object>, Map<Object, Object>>> valueBuilderWrapper
                    : valueBuilders.get(entry.getKey())) {
                Object valueNamespace = valueBuilderWrapper.getKey();
                BiFunction<B, Collection<Object>, Map<Object, Object>> valueBuilder = valueBuilderWrapper
                        .getValue();
                Set<Object> needToBuildIds = filterIdSetOnBuild(entry.getValue(), buildContext, valuesMap,
                        valueNamespace);
                Map<Object, Object> values = valueBuilder.apply(buildContext, needToBuildIds);
                if (values != null) {
                    valuesMap.merge(valueNamespace, values, MergeUtils::merge);
                }
            }
        }
    }

    private Set<Object> filterIdSetOnBuild(Set<Object> original, B buildContext,
            Map<Object, Map<Object, Object>> valuesMap, Object valueNamespace) {
        Set<Object> buildContextExistIds = buildContext.getData(valueNamespace).keySet();
        Set<Object> valueMapExistIds = computeIfAbsent(valuesMap, valueNamespace, i -> new HashMap<>()).keySet();
        if (buildContextExistIds.isEmpty() && valueMapExistIds.isEmpty()) {
            return original;
        }

        Set<Object> filteredIds = new HashSet<>(original.size());
        for (Object value : original) {
            if (!buildContextExistIds.contains(value) && !valueMapExistIds.contains(value)) {
                filteredIds.add(value);
            }
        }
        return filteredIds;
    }

    private static <K, V> V computeIfAbsent(Map<K, V> map, K key, Function<Object, V> function) {
        V value = map.get(key);
        if (value == null) {
            value = function.apply(key);
            map.put(key, value);
        }
        return value;
    }

    // return new found data.
    private void extract(Object obj, B buildContext, Map<Object, Set<Object>> idsMap,
            Map<Object, Map<Object, Object>> valuesMap) {
        if (obj == null) {
            return;
        }
        Set<Function<Object, KeyPair<Map<Object, Object>>>> localValueExtractors =
                computeIfAbsent(cachedValueExtractors, obj.getClass(),
                        t -> getAllSuperTypes((Class<?>) t).stream()
                                .flatMap(i -> this.valueExtractors.get(i).stream()).collect(toSet()));
        for (Function<Object, KeyPair<Map<Object, Object>>> valueExtractor : localValueExtractors) {
            KeyPair<Map<Object, Object>> values = valueExtractor.apply(obj);
            Map<Object, Object> filtered = filterValueMap(values, buildContext);
            idsMap.merge(values.getKey(), new HashSet<>(filtered.keySet()),
                    MergeUtils::merge);
            valuesMap.merge(values.getKey(), filtered, MergeUtils::merge);
        }

        Set<Function<Object, KeyPair<Set<Object>>>> localIdExtractors = computeIfAbsent(
                cachedIdExtractors, obj.getClass(),
                t -> getAllSuperTypes((Class<?>) t).stream()
                        .flatMap(i -> idExtractors.get(i).stream()).collect(toSet()));

        for (Function<Object, KeyPair<Set<Object>>> idExtractor : localIdExtractors) {
            KeyPair<Set<Object>> ids = idExtractor.apply(obj);
            idsMap.merge(ids.getKey(), filterIdSet(ids, buildContext, valuesMap),
                    MergeUtils::merge);
        }
    }

    private Set<Object> filterIdSet(KeyPair<Set<Object>> keyPair, B buildContext,
            Map<Object, Map<Object, Object>> valuesMap) {
        Set<Object> buildContextExistIds = buildContext.getData(keyPair.getKey()).keySet();
        Set<Object> valueMapExistIds = computeIfAbsent(valuesMap, keyPair.getKey(), i -> new HashMap<>()).keySet();

        if (buildContextExistIds.isEmpty() && valueMapExistIds.isEmpty()) {
            return new HashSet<>(keyPair.getValue());
        }

        Set<Object> filteredIds = new HashSet<>(keyPair.getValue().size());
        for (Object value : keyPair.getValue()) {
            if (!buildContextExistIds.contains(value) && !valueMapExistIds.contains(value)) {
                filteredIds.add(value);
            }
        }
        return filteredIds;
    }

    private Map<Object, Object> filterValueMap(KeyPair<Map<Object, Object>> keyPair,
            B buildContext) {
        Map<Object, Object> buildContextData = buildContext.getData(keyPair.getKey());
        if (buildContextData.isEmpty()) {
            return new HashMap<>(keyPair.getValue());
        }

        Map<Object, Object> filteredValueMap = new HashMap<>(keyPair.getValue().size());
        for (Entry<Object, Object> entry: keyPair.value.entrySet()) {
            if (!buildContextData.containsKey(entry.getKey())) {
                filteredValueMap.put(entry.getKey(), entry.getValue());
            }
        }
        return filteredValueMap;
    }

    /**
     * use {@link #extractId} or {@link #extractValue}
     */
    @Deprecated
    public <E> OnBuilder<E> on(Class<E> type) {
        return new OnBuilder<>(type);
    }

    /**
     * use {@link #valueFromSelf}
     */
    @Deprecated
    public <E> SimpleModelBuilder<B> self(Class<E> type, Function<E, Object> idExtractor) {
        SimpleModelBuilder<B>.OnBuilder<E> onBuilder = new OnBuilder<>(type);
        return onBuilder.new ExtractingValue(i -> i).id(idExtractor).to(type);
    }

    /**
     * use {@link #buildValue} or {@link #buildValueTo}
     */
    @Deprecated
    public BuildingBuilder build(Object idNamespace) {
        return new BuildingBuilder(idNamespace);
    }

    /**
     * use {@link #buildValue} or {@link #buildValueTo}
     */
    @Deprecated
    public <K> SimpleModelBuilder<B> build(Object idNamespace,
            BiFunction<B, Collection<K>, Map<K, ?>> valueBuilder) {
        return build(idNamespace).by(valueBuilder).to(idNamespace);
    }

    /**
     * use {@link #buildValue} or {@link #buildValueTo}
     */
    @Deprecated
    public <K> SimpleModelBuilder<B> build(Object idNamespace,
            Function<Collection<K>, Map<K, ?>> valueBuilder) {
        return build(idNamespace).by(valueBuilder).to(idNamespace);
    }

    /**
     * use {@link #lazyBuild}
     */
    @SuppressWarnings("rawtypes")
    @Deprecated
    public SimpleModelBuilder<B> lazy(Lazy lazy) {
        tryCheckConflict();
        lazyBuilders.put(lazy.targetNamespace(), buildContext -> (Map) ((BiFunction) lazy.builder())
                .apply(buildContext, buildContext.getData(lazy.sourceNamespace()).keySet()));
        return this;
    }

    private void tryCheckConflict() {
        if (alreadyBuilt && onConflictListener != null) {
            onConflictListener.run();
        }
    }

    public <E> SimpleModelBuilder<B> valueFromSelf(Class<E> type, Function<E, Object> idExtractor) {
        self(type, idExtractor);
        return this;
    }

    public <E> SimpleModelBuilder<B> extractId(Class<E> type, Function<E, Object> idExtractor,
            Object toIdNamespace) {
        on(type).id(idExtractor).to(toIdNamespace);
        return this;
    }

    public <E, V> SimpleModelBuilder<B> extractValue(Class<E> type,
            Function<E, Object> valueExtractor, Function<V, Object> idExtractor,
            Object toValueNamespace) {
        on(type).value(valueExtractor).id(idExtractor).to(toValueNamespace);
        return this;
    }

    public <K> SimpleModelBuilder<B> buildValue(Object idNamespace,
            Function<Collection<K>, Map<K, ?>> valueBuilder) {
        build(idNamespace, valueBuilder);
        return this;
    }

    public <K> SimpleModelBuilder<B> buildValue(Object idNamespace,
            BiFunction<B, Collection<K>, Map<K, ?>> valueBuilder) {
        build(idNamespace, valueBuilder);
        return this;
    }

    public <K> SimpleModelBuilder<B> buildValueTo(Object idNamespace,
            Function<Collection<K>, Map<K, ?>> valueBuilder, Object toValueNamespace) {
        build(idNamespace).by(valueBuilder).to(toValueNamespace);
        return this;
    }

    public <K> SimpleModelBuilder<B> buildValueTo(Object idNamespace,
            BiFunction<B, Collection<K>, Map<K, ?>> valueBuilder, Object toValueNamespace) {
        build(idNamespace).by(valueBuilder).to(toValueNamespace);
        return this;
    }

    public <K> SimpleModelBuilder<B> lazyBuild(Object sourceNamespace,
            Function<Collection<K>, Map<K, ?>> builder, Object targetNamespace) {
        lazy(LazyBuilder.on(sourceNamespace, builder, targetNamespace));
        return this;
    }

    public <K> SimpleModelBuilder<B> lazyBuild(Object sourceNamespace,
            BiFunction<B, Collection<K>, Map<K, ?>> builder, Object targetNamespace) {
        lazy(LazyBuilder.on(sourceNamespace, builder, targetNamespace));
        return this;
    }

    private Set<Class<?>> getAllSuperTypes(Class<?> iface) {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(iface);
        classes.addAll(getAllInterfaces(iface));
        classes.addAll(getAllSuperclasses(iface));
        return classes;
    }

    @Override
    public String toString() {
        return reflectionToString(this, SHORT_PREFIX_STYLE);
    }

    interface Lazy {

        Object sourceNamespace();

        Object targetNamespace();

        BiFunction<?, ?, ?> builder();
    }

    private static final class KeyPair<V> implements Entry<Object, V> {

        private final Object key;
        private final V value;

        private KeyPair(Object key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }

    @Deprecated
    public final class OnBuilder<E> {

        private final Class<E> objType;

        private OnBuilder(Class<E> objType) {
            this.objType = objType;
        }

        public ExtractingId id(Function<E, Object> idExtractor) {
            return new ExtractingId(idExtractor);
        }

        public ExtractingValue value(Function<E, Object> valueExtractor) {
            return new ExtractingValue(valueExtractor);
        }

        public <V> ExtractingValue value(Function<E, Iterable<V>> valueExtractor,
                Function<V, Object> idExtractor) {
            return new ExtractingValue(valueExtractor).id(idExtractor);
        }

        public final class ExtractingValue {

            private final Function<E, Object> valueExtractor;
            private Function<Object, Object> idExtractor;

            private ExtractingValue(Function<E, ?> valueExtractor) {
                this.valueExtractor = (Function<E, Object>) valueExtractor;
            }

            public <K> ExtractingValue id(Function<K, Object> idExtractor) {
                this.idExtractor = (Function<Object, Object>) idExtractor;
                return this;
            }

            public SimpleModelBuilder<B> to(Object valueNamespace) {
                tryCheckConflict();
                valueExtractors.put(objType, obj -> {
                    Object rawValue = valueExtractor.apply((E) obj);
                    Map<Object, Object> value;
                    if (rawValue == null) {
                        value = emptyMap();
                    } else {
                        if (idExtractor != null) {
                            if (rawValue instanceof Iterable) {
                                if (rawValue instanceof Collection) {
                                    value = new HashMap<>(((Collection) rawValue).size());
                                } else {
                                    value = new HashMap<>();
                                }
                                for (E e : ((Iterable<E>) rawValue)) {
                                    value.put(idExtractor.apply(e), e);
                                }
                            } else {
                                value = singletonMap(idExtractor.apply(rawValue), rawValue);
                            }
                        } else {
                            if (rawValue instanceof Map) {
                                value = (Map<Object, Object>) rawValue;
                            } else {
                                logger.warn("invalid value extractor for:{}->{}", obj, rawValue);
                                value = emptyMap();
                            }
                        }
                    }
                    return new KeyPair<>(valueNamespace, value);
                });
                cachedValueExtractors.clear();
                return SimpleModelBuilder.this;
            }
        }

        public final class ExtractingId {

            private final Function<E, Object> idExtractor;

            private ExtractingId(Function<E, Object> idExtractor) {
                this.idExtractor = idExtractor;
            }

            public SimpleModelBuilder<B> to(Object idNamespace) {
                tryCheckConflict();
                idExtractors.put(objType, obj -> {
                    Object rawId = idExtractor.apply((E) obj);
                    Set<Object> ids;
                    if (rawId == null) {
                        ids = emptySet();
                    } else {
                        if (rawId instanceof Iterable) {
                            ids = Sets.newHashSet((Iterable) rawId);
                        } else {
                            ids = singleton(rawId);
                        }
                    }
                    return new KeyPair<>(idNamespace, ids);
                });
                cachedIdExtractors.clear();
                return SimpleModelBuilder.this;
            }
        }
    }

    @Deprecated
    public final class BuildingBuilder {

        private final Object idNamespace;

        private BuildingBuilder(Object idNamespace) {
            this.idNamespace = idNamespace;
        }

        @SuppressWarnings("rawtypes")
        public <K> BuildingValue<K> by(Function<Collection<K>, Map<K, ?>> valueBuilder) {
            return new BuildingValue<>((c, ids) -> (Map) valueBuilder.apply(ids));
        }

        @SuppressWarnings("rawtypes")
        public <K> BuildingValue<K> by(BiFunction<B, Collection<K>, Map<K, ?>> valueBuilder) {
            return new BuildingValue<>((BiFunction) valueBuilder);
        }

        public final class BuildingValue<K> {

            private final BiFunction<B, Collection<K>, Map<K, Object>> valueBuilderFunction;

            private BuildingValue(
                    BiFunction<B, Collection<K>, Map<K, Object>> valueBuilderFunction) {
                this.valueBuilderFunction = valueBuilderFunction;
            }

            @SuppressWarnings("rawtypes")
            public SimpleModelBuilder<B> to(Object valueNamespace) {
                tryCheckConflict();
                valueBuilders.put(idNamespace, new KeyPair(valueNamespace, valueBuilderFunction));
                return SimpleModelBuilder.this;
            }
        }
    }
}
