/**
 * 
 */
package me.vela.view.mapper.impl;

import java.util.Collection;
import java.util.List;

import me.vela.model.builder.context.BuildContext;
import me.vela.view.mapper.ViewMapper;

/**
 * @author w.vela
 */
public abstract class ForwardingViewMapper implements ViewMapper {

    private final ViewMapper delegate;

    /**
     * @param delegate
     */
    protected ForwardingViewMapper(ViewMapper delegate) {
        this.delegate = delegate;
    }

    public <M, V, B extends BuildContext> V map(M model, B buildContext) {
        return delegate.map(model, buildContext);
    }

    public <M, V, B extends BuildContext> List<V> map(Collection<M> models, B buildContext) {
        return delegate.map(models, buildContext);
    }

}
