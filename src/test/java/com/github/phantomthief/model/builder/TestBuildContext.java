/**
 * 
 */
package com.github.phantomthief.model.builder;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.github.phantomthief.model.builder.context.impl.SimpleBuildContext;
import com.github.phantomthief.model.builder.util.ToStringUtils;

/**
 * @author w.vela
 */
public class TestBuildContext extends SimpleBuildContext {

    private final int visitorId;

    public TestBuildContext(int visitorId) {
        this.visitorId = visitorId;
    }

    public int getVisitorId() {
        return visitorId;
    }

    @Override
    public ConcurrentMap<Object, Map<Object, Object>> getDatas() {
        return super.getDatas();
    }

    @Override
    public String toString() {
        return ToStringUtils.toString(this);
    }
}
