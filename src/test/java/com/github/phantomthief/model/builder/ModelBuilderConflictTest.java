package com.github.phantomthief.model.builder;

import com.github.phantomthief.model.builder.impl.SimpleModelBuilder;
import com.github.phantomthief.model.builder.model.User;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.github.phantomthief.model.builder.impl.LazyBuilder.on;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author w.vela
 */
class ModelBuilderConflictTest {

    private SimpleModelBuilder<TestBuildContext> builder;

    @Test
    void testBuild1() {
        TestBuildContext buildContext = new TestBuildContext(1);
        boolean[] conflict = {false};
        builder = new SimpleModelBuilder<TestBuildContext>()
                .onConflictCheckListener(() -> conflict[0] = true)
                .lazy(on(User.class,
                        (TestBuildContext context, Collection<Integer> ids) -> Collections.emptyMap(),
                        "isFans"));
        List<Object> sources = new ArrayList<>();
        builder.buildSingle(null, buildContext);
        builder.lazy(on(User.class,
                (TestBuildContext context, Collection<Integer> ids) -> Collections.emptyMap(),
                "isFans2"));
        assertTrue(conflict[0]);
    }

    @Test
    void testBuild2() {
        TestBuildContext buildContext = new TestBuildContext(1);
        boolean[] conflict = {false};
        builder = new SimpleModelBuilder<TestBuildContext>()
                .onConflictCheckListener(() -> conflict[0] = true)
                .extractId(Object.class, it -> it, String.class);
        List<Object> sources = new ArrayList<>();
        builder.buildSingle(null, buildContext);
        builder.extractId(Object.class, it -> it, String.class);
        assertTrue(conflict[0]);
    }

    @Test
    void testBuild3() {
        TestBuildContext buildContext = new TestBuildContext(1);
        boolean[] conflict = {false};
        builder = new SimpleModelBuilder<TestBuildContext>()
                .onConflictCheckListener(() -> conflict[0] = true)
                .extractValue(Object.class, it -> it, it -> it, String.class);
        List<Object> sources = new ArrayList<>();
        builder.buildSingle(null, buildContext);
        builder.extractValue(Object.class, it -> it, it -> it, String.class);
        assertTrue(conflict[0]);
    }
}
