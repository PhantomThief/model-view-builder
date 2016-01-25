/**
 * 
 */
package com.github.phantomthief.model.builder;

import static java.util.Collections.singleton;

import com.github.phantomthief.model.builder.context.BuildContext;

/**
 * 
 * @author w.vela
 */
public interface ModelBuilder<B extends BuildContext> {

    void buildMulti(Iterable<?> sources, B buildContext);

    default void buildSingle(Object one, B buildContext) {
        buildMulti(singleton(one), buildContext);
    }
}
