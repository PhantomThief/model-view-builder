package com.github.phantomthief.model.builder;

import com.github.phantomthief.model.builder.context.impl.SimpleBuildContext;
import com.github.phantomthief.model.builder.impl.SimpleModelBuilder;
import com.github.phantomthief.model.builder.model.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.*;

import static com.github.phantomthief.model.builder.impl.LazyBuilder.on;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author w.vela
 */
class ModelBuilderConflictTest {

    private static Logger logger = getLogger(ModelBuilderConflictTest.class);
    private SimpleModelBuilder<TestBuildContext> builder;

    @Test
    void testBuild() {
        TestBuildContext buildContext = new TestBuildContext(1);
        builder = new SimpleModelBuilder<TestBuildContext>()
                .lazy(on(User.class,
                        (TestBuildContext context, Collection<Integer> ids) -> Collections.emptyMap(),
                        "isFans"));
        List<Object> sources = new ArrayList<>();
        builder.buildSingle(null, buildContext);
        logger.info("after build.");
        builder.lazy(on(User.class,
                (TestBuildContext context, Collection<Integer> ids) -> Collections.emptyMap(),
                "isFans2"));
    }
}
