package com.github.phantomthief.view.mapper.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import org.apache.commons.lang3.ClassUtils;

import com.github.phantomthief.view.mapper.ViewMapper;

/**
 * @author w.vela
 */
public class OverrideViewMapper extends ForwardingViewMapper {

    private final Map<Class<?>, BiFunction<?, ?, ?>> overrideMappers = new HashMap<>();
    private final ConcurrentMap<Class<?>, BiFunction<?, ?, ?>> modelTypeCache = new ConcurrentHashMap<>();

    public OverrideViewMapper(ViewMapper delegate) {
        super(delegate);
    }

    public <E, B, V> OverrideViewMapper addMapper(Class<E> type, BiFunction<E, B, V> mapper) {
        overrideMappers.put(type, mapper);
        return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <M, V, B> V map(M model, B buildContext) {
        BiFunction mapper = getMapper(model.getClass());
        if (mapper != null) {
            return (V) mapper.apply(model, buildContext);
        } else {
            return super.map(model, buildContext);
        }
    }

    @SuppressWarnings("rawtypes")
    private BiFunction getMapper(Class<?> modelType) {
        return modelTypeCache.computeIfAbsent(modelType, t -> {
            BiFunction<?, ?, ?> result = overrideMappers.get(t);
            if (result == null) {
                for (Class<?> c : ClassUtils.getAllInterfaces(t)) {
                    result = overrideMappers.get(c);
                    if (result != null) {
                        return result;
                    }
                }
                for (Class<?> c : ClassUtils.getAllSuperclasses(t)) {
                    result = overrideMappers.get(c);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return result;
        });
    }

}
