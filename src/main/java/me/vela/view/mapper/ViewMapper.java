/**
 * 
 */
package me.vela.view.mapper;

import java.util.Collection;
import java.util.List;

import me.vela.model.builder.context.BuildContext;

/**
 * @author w.vela
 */
public interface ViewMapper {

    /**
     * @param model
     * @param buildContext
     * @return
     */
    public <M, V, B extends BuildContext> V map(M model, B buildContext);

    /**
     * @param models
     * @param buildContext
     * @return
     */
    public <M, V, B extends BuildContext> List<V> map(Collection<M> models, B buildContext);

}
