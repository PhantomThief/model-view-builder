/**
 * 
 */
package me.vela.view.mapper;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author w.vela
 */
public interface ViewMapper {

    /**
     * @param model
     * @param buildContext
     * @return
     */
    public <M, V, B> V map(M model, B buildContext);

    /**
     * @param models
     * @param buildContext
     * @return
     */
    public default <M, V, B> List<V> map(Collection<M> models, B buildContext) {
        return models.stream().map(i -> this.<M, V, B> map(i, buildContext))
                .collect(Collectors.toList());
    }

}
