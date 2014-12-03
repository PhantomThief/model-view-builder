/**
 * 
 */
package me.vela.view.mapper.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import me.vela.model.builder.context.BuildContext;
import me.vela.view.mapper.ViewMapper;

import org.apache.commons.lang3.ClassUtils;

/**
 * @author w.vela
 */
public class DefaultViewMapperImpl implements ViewMapper {

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    private final Map<Class<?>, BiFunction<BuildContext, ?, ?>> mappers = new HashMap<>();

    @SuppressWarnings({ "unchecked" })
    @Override
    public <M, V, B extends BuildContext> V map(M model, B buildContext) {
        V view = (V) getMapper(model.getClass()).apply(buildContext, model);
        return view;
    }

    @Override
    public <M, V, B extends BuildContext> List<V> map(Collection<M> models, B buildContext) {
        return models.stream().map(i -> this.<M, V, B> map(i, buildContext))
                .collect(Collectors.toList());
    }

    private final ConcurrentMap<Class<?>, BiFunction<BuildContext, ?, ?>> modelTypeCache = new ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes")
    private BiFunction getMapper(Class<?> modelType) {
        return modelTypeCache.computeIfAbsent(modelType, t -> {
            BiFunction<BuildContext, ?, ?> result = mappers.get(t);
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

    public <M, V> DefaultViewMapperImpl addMapper(Class<M> modelType,
            BiFunction<BuildContext, M, V> viewFactory) {
        mappers.put(modelType, viewFactory);
        return this;
    }

}
