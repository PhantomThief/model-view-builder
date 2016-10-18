/**
 * 
 */
package com.github.phantomthief.model.builder;

import java.util.concurrent.ConcurrentHashMap;

import com.github.phantomthief.model.builder.context.impl.SimpleBuildContext;
import com.github.phantomthief.model.builder.util.ToStringUtils;

/**
 * @author w.vela
 */
public class TestBuildContext extends SimpleBuildContext {

    private final int visitorId;

    TestBuildContext(int visitorId) {
        super(new ConcurrentHashMap<>(1, 0.75F, 2));
        this.visitorId = visitorId;
    }

    public int getVisitorId() {
        return visitorId;
    }

    @Override
    public String toString() {
        return ToStringUtils.toString(this);
    }
}
