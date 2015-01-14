/**
 * 
 */
package com.github.phantomthief.view.mapper.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

import org.apache.commons.lang3.ClassUtils;

import com.github.phantomthief.model.builder.context.BuildContext;
import com.github.phantomthief.view.mapper.ViewMapper;

/**
 * <p>DefaultViewMapperImpl class.</p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public class DefaultViewMapperImpl implements ViewMapper {

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    private final Map<Class<?>, BiFunction<?, ?, ?>> mappers = new HashMap<>();

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked" })
    @Override
    public <M, V, B> V map(M model, B buildContext) {
        V view = (V) getMapper(model.getClass()).apply(buildContext, model);
        return view;
    }

    private final ConcurrentMap<Class<?>, BiFunction<?, ?, ?>> modelTypeCache = new ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes")
    private BiFunction getMapper(Class<?> modelType) {
        return modelTypeCache.computeIfAbsent(modelType, t -> {
            BiFunction<?, ?, ?> result = mappers.get(t);
            if (result == null) {
                for (Class<?> c : ClassUtils.getAllInterfaces(t)) {
                    result = mappers.get(c);
                    if (result != null) {
                        return result;
                    }
                }
                for (Class<?> c : ClassUtils.getAllSuperclasses(t)) {
                    result = mappers.get(c);
                    if (result != null) {
                        return result;
                    }
                }
            }
            if (result == null) {
                logger.warn("cannot found model's view:{}", modelType);
            }
            return result;
        });
    }

    /**
     * <p>addMapper.</p>
     *
     * @param modelType a {@link java.lang.Class} object.
     * @param viewFactory a {@link java.util.function.BiFunction} object.
     * @param <M> a M object.
     * @param <V> a V object.
     * @return a {@link com.github.phantomthief.view.mapper.impl.DefaultViewMapperImpl} object.
     */
    public <M, V> DefaultViewMapperImpl addMapper(Class<M> modelType,
            BiFunction<BuildContext, M, V> viewFactory) {
        mappers.put(modelType, viewFactory);
        return this;
    }

}
