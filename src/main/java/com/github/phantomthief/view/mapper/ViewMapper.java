/**
 * 
 */
package com.github.phantomthief.view.mapper;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

/**
 * @author w.vela
 */
public interface ViewMapper {

    <M, V, B> V map(M model, B buildContext);

    default <M, V, B> List<V> map(Collection<M> models, B buildContext) {
        return models.stream().map(i -> this.<M, V, B> map(i, buildContext)).collect(toList());
    }

}
