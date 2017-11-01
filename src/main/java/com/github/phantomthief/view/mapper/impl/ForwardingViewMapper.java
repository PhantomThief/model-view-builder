package com.github.phantomthief.view.mapper.impl;

import com.github.phantomthief.view.mapper.ViewMapper;

/**
 * <p>Abstract ForwardingViewMapper class.</p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public abstract class ForwardingViewMapper implements ViewMapper {

    private final ViewMapper delegate;

    /**
     * <p>Constructor for ForwardingViewMapper.</p>
     *
     * @param delegate a {@link com.github.phantomthief.view.mapper.ViewMapper} object.
     */
    protected ForwardingViewMapper(ViewMapper delegate) {
        this.delegate = delegate;
    }

    /**
     * <p>map.</p>
     *
     * @param model a M object.
     * @param buildContext a B object.
     * @param <M> a M object.
     * @param <V> a V object.
     * @param <B> a B object.
     * @return a V object.
     */
    public <M, V, B> V map(M model, B buildContext) {
        return delegate.map(model, buildContext);
    }

}
