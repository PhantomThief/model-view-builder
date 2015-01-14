/**
 * 
 */
package com.github.phantomthief.model.builder;

import java.util.Collection;
import java.util.Collections;

import com.github.phantomthief.model.builder.context.BuildContext;

/**
 * <p>
 * DefaultModelBuilderImpl class.
 * </p>
 *
 * @author w.vela
 * @version $Id: $Id
 */
public interface ModelBuilder<B extends BuildContext> {

    /**
     * <p>
     * build.
     * </p>
     *
     * @param sources a {@link java.util.Collection} object.
     * @param buildContext a B object.
     */
    public void build(Collection<?> sources, B buildContext);

    /**
     * <p>
     * buildOne.
     * </p>
     *
     * @param one a {@link java.lang.Object} object.
     * @param buildContext a B object.
     */
    public default void buildOne(Object one, B buildContext) {
        build(Collections.singleton(one), buildContext);
    }

}
