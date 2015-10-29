/**
 * 
 */
package com.github.phantomthief.model.builder;

import java.util.Collections;

import com.github.phantomthief.model.builder.context.BuildContext;

/**
 * 
 * @author w.vela
 */
public interface ModelBuilder<B extends BuildContext> {

    public void buildMulti(Iterable<?> sources, B buildContext);

    public default void buildSingle(Object one, B buildContext) {
        buildMulti(Collections.singleton(one), buildContext);
    }
}
