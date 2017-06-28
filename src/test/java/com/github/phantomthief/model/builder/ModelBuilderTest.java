/**
 * 
 */
package com.github.phantomthief.model.builder;

import static com.github.phantomthief.model.builder.impl.LazyBuilder.on;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.github.phantomthief.model.builder.context.impl.SimpleBuildContext;
import com.github.phantomthief.model.builder.impl.SimpleModelBuilder;
import com.github.phantomthief.model.builder.model.Comment;
import com.github.phantomthief.model.builder.model.Fake;
import com.github.phantomthief.model.builder.model.HasUser;
import com.github.phantomthief.model.builder.model.Post;
import com.github.phantomthief.model.builder.model.SubUser;
import com.github.phantomthief.model.builder.model.User;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @author w.vela
 */
public class ModelBuilderTest {

    private static Logger logger = getLogger(ModelBuilderTest.class);
    private TestDAO testDAO;
    private ModelBuilder<TestBuildContext> builder;

    @Before
    public void setup() {
        testDAO = new TestDAO();
        builder = new SimpleModelBuilder<TestBuildContext>() //
                .self(User.class, User::getId) //
                .self(Post.class, Post::getId) //
                .self(Comment.class, Comment::getId) //
                .on(Comment.class).id(Comment::getAtUserIds).to(User.class) //
                .on(HasUser.class).id(HasUser::getUserId).to(User.class) //
                .on(Post.class).value(Post::comments, Comment::getId).to(Comment.class) //
                .build(User.class, testDAO::getUsers).build(Post.class, testDAO::getPosts) //
                .build(Comment.class, testDAO::getComments).build(User.class) //
                .by((TestBuildContext context, Collection<Integer> ids) -> testDAO
                        .isFollowing(context.getVisitorId(), ids))
                .to("isFollowing") //
                .lazy(on(User.class,
                        (TestBuildContext context, Collection<Integer> ids) -> testDAO
                                .isFans(context.getVisitorId(), ids),
                        "isFans")) //
                .lazyBuild(User.class, (TestBuildContext context, Collection<Integer> ids) -> {
                    Map<Integer, Boolean> fans = testDAO.isFans(context.getVisitorId(), ids);
                    logger.debug("build fans for:{}->{}, result:{}", context.getVisitorId(), ids,
                            fans);
                    return fans;
                }, "isFans3") //
                .lazy(on(Fake.class,
                        (TestBuildContext context, Collection<Integer> ids) -> testDAO
                                .isFans(context.getVisitorId(), ids),
                        "unreachedLazy")) //
                .lazy(on(Fake.class, (TestBuildContext context, Collection<Integer> ids) -> {
                    context.getData("unreachedLazy");
                    return testDAO.isFans(context.getVisitorId(), ids);
                }, "unreachedLazy2")) //
        ;
        System.out.println("builder===>");
        System.out.println(builder);
    }

    @Test
    public void testBuild() throws Exception {
        TestBuildContext buildContext = new TestBuildContext(1);
        List<Object> sources = new ArrayList<>();
        Collection<Post> posts = testDAO.getPosts(Arrays.asList(1L, 2L, 3L)).values();
        posts.forEach(post -> post.setComments(
                testDAO.getComments(post.getCommentIds()).values().stream().collect(toList())));
        sources.addAll(posts);
        sources.addAll(testDAO.getComments(singletonList(3L)).values());
        sources.add(new SubUser(98));
        logger.info("sources===>");
        sources.forEach(o -> logger.info("{}", o));
        testDAO.assertOn();
        builder.buildMulti(sources, buildContext);
        logger.info("buildContext===>");
        logger.info("{}", buildContext);

        assertTrue(testDAO.retrievedFansUserIds.isEmpty());

        Map<Integer, Boolean> isFans = buildContext.getData("isFans");
        logger.info("isFans:{}", isFans);
        isFans.forEach((userId, value) -> assertEquals(
                testDAO.fansMap.get(buildContext.getVisitorId()).contains(userId), value));
        assertFalse(testDAO.retrievedFansUserIds.isEmpty());
        logger.info("retry fans");
        buildContext.getData("isFans");
        logger.info("doing merge");
        buildContext.merge(new SimpleBuildContext());
        testDAO.retrievedFansUserIds.clear();
        logger.info("isFans:{}", buildContext.getData("isFans"));

        // try assert
        for (Object obj : sources) {
            if (obj instanceof Post) {
                Post post = (Post) obj;
                assertEquals(buildContext.getData(Post.class).get(post.getId()), obj);
                assertEquals(post.getUserId(),
                        buildContext.getData(User.class).get(post.getUserId()).getId());
                for (Comment cmt : post.comments()) {
                    assertCmt(buildContext, cmt);
                }
            }
            if (obj instanceof Comment) {
                Comment cmt = (Comment) obj;
                assertCmt(buildContext, cmt);
            }
            if (obj instanceof User) {
                User user = (User) obj;
                assertUser(buildContext, user);
            }
        }

        Map<Long, Boolean> unreachedLazy = buildContext.getData("unreachedLazy2");
        assertTrue(unreachedLazy.isEmpty());
        assertFalse(unreachedLazy.getOrDefault(1L, false));

        logger.info("checking nodes.");
        buildContext.getData(User.class).values().forEach(user -> assertUser(buildContext, user));
        logger.info("fin.");
    }

    @Test
    public void testNullBuild() throws Exception {
        TestBuildContext buildContext = new TestBuildContext(1);
        builder.buildSingle(null, buildContext);
        buildContext.getData("t").put("a", "c");
        System.out.println("checking...");
        Map<Integer, Boolean> isFans = buildContext.getData("isFans3");
        assertFalse(isFans.getOrDefault(1, false));
        System.out.println("fin.");
    }

    @Test
    public void testMerge() throws Exception {
        TestBuildContext buildContext = new TestBuildContext(1);
        List<User> users = new ArrayList<>(testDAO.getUsers(ImmutableList.of(1, 2, 3)).values());
        builder.buildMulti(users, buildContext);
        Map<Integer, Boolean> isFans = buildContext.getData("isFans3");
        System.out.println("isFans:" + isFans);
        users.forEach(user -> assertTrue(isFans.get(user.getId()) != null));

        TestBuildContext other = new TestBuildContext(1);
        List<User> users2 = new ArrayList<>(testDAO.getUsers(ImmutableList.of(3, 4, 5)).values());
        builder.buildMulti(users2, other);
        Map<Integer, Boolean> isFans2 = other.getData("isFans3");
        System.out.println("isFans2:" + isFans2);
        users2.forEach(user -> assertTrue(isFans2.get(user.getId()) != null));

        buildContext.merge(other);
        System.out.println("after merged.");
        System.out.println("users:" + buildContext.getData(User.class));

        Map<Integer, Boolean> merged = buildContext.getData("isFans3");
        System.out.println("merged:" + merged);
        for (int i = 1; i <= 5; i++) {
            assertTrue(merged.get(i) != null);
        }
        System.out.println("fin.");
    }

    @Test
    public void testDuplicateMerge() throws Exception {
        TestBuildContext mainBuildContext = new TestBuildContext(1);

        TestBuildContext buildContext = new TestBuildContext(1);
        builder.buildMulti(emptyMap().values(), buildContext);
        mainBuildContext.merge(buildContext);

        TestBuildContext buildContext2 = new TestBuildContext(1);
        Map<Integer, User> byIdsFailFast = testDAO.getUsers(ImmutableList.of(1, 2));
        builder.buildMulti(byIdsFailFast.values(), buildContext2);
        Map<Integer, Boolean> isFans3 = buildContext2.getData("isFans3");
        System.out.println("[test] " + isFans3);
        assertTrue(!isFans3.isEmpty());

        mainBuildContext.merge(buildContext2);

        isFans3 = mainBuildContext.getData("isFans3");
        System.out.println("[test] " + isFans3);
        assertTrue(!isFans3.isEmpty());
    }

    private void assertUser(TestBuildContext buildContext, User user) {
        assertNotNull(buildContext.getData("isFollowing").get(user.getId()));
    }

    private void assertCmt(TestBuildContext buildContext, Comment cmt) {
        assertEquals(buildContext.getData(Comment.class).get(cmt.getId()), cmt);
        assertEquals(cmt.getUserId(),
                buildContext.getData(User.class).get(cmt.getUserId()).getId());
        if (cmt.getAtUserIds() != null) {
            for (Integer atUserId : cmt.getAtUserIds()) {
                assertEquals(atUserId, buildContext.getData(User.class).get(atUserId).getId());
            }
        }
    }

    private class TestDAO {

        private static final int USER_MAX = 100;
        private final Map<Long, Post> posts = ImmutableList
                .of(new Post(1, 1, null), //
                        new Post(2, 1, Arrays.asList(1L, 2L, 3L)), //
                        new Post(3, 2, Arrays.asList(4L, 5L)))
                .stream().collect(toMap(Post::getId, identity()));

        private final Map<Long, Comment> cmts = ImmutableList
                .of(new Comment(1, 1, null), new Comment(2, 2, null), new Comment(3, 1, null), //
                        new Comment(4, 2, Arrays.asList(2, 3)), //
                        new Comment(5, 11, Arrays.asList(2, 99)))
                .stream().collect(toMap(Comment::getId, identity()));

        private final Multimap<Integer, Integer> followingMap = HashMultimap.create();
        private final Multimap<Integer, Integer> fansMap = HashMultimap.create();
        private Set<Integer> retreievedUserIds;
        private Set<Long> retreievedPostIds;
        private Set<Long> retreievedCommentIds;
        private Set<Integer> retrievedFollowUserIds;
        private Set<Integer> retrievedFansUserIds;

        {
            followingMap.put(1, 5);
            followingMap.put(1, 2);
        }

        {
            fansMap.put(1, 5);
            fansMap.put(1, 99);
        }

        Map<Integer, User> getUsers(Collection<Integer> ids) {
            if (retreievedUserIds != null) {
                logger.info("try to get users:{}", ids);
                for (Integer id : ids) {
                    assertTrue(retreievedUserIds.add(id));
                }
            }
            return ids.stream().filter(i -> i <= USER_MAX).collect(toMap(identity(), User::new));
        }

        Map<Long, Post> getPosts(Collection<Long> ids) {
            if (retreievedPostIds != null) {
                logger.info("try to get posts:{}", ids);
                for (Long id : ids) {
                    assertTrue(retreievedPostIds.add(id));
                }
            }
            return Maps.filterKeys(posts, ids::contains);
        }

        Map<Long, Comment> getComments(Collection<Long> ids) {
            if (ids == null) {
                return emptyMap();
            }
            if (retreievedCommentIds != null) {
                logger.info("try to get cmts:{}", ids);
                for (Long id : ids) {
                    assertTrue(retreievedCommentIds.add(id));
                }
            }
            return Maps.filterKeys(cmts, ids::contains);
        }

        Map<Integer, Boolean> isFollowing(int fromUserId, Collection<Integer> ids) {
            if (retrievedFollowUserIds != null) {
                logger.info("try to get followings:{}->{}", fromUserId, ids);
                for (Integer id : ids) {
                    assertTrue(retrievedFollowUserIds.add(id));
                }
            }
            Collection<Integer> followings = followingMap.get(fromUserId);
            return ids.stream().collect(toMap(identity(), followings::contains));
        }

        Map<Integer, Boolean> isFans(int fromUserId, Collection<Integer> ids) {
            if (retrievedFansUserIds != null) {
                logger.info("try to get fans:{}->{}", fromUserId, ids);
                for (Integer id : ids) {
                    assertTrue(retrievedFansUserIds.add(id));
                }
            }
            Collection<Integer> fans = fansMap.get(fromUserId);
            return ids.stream().collect(toMap(identity(), fans::contains));
        }

        void assertOn() {
            logger.info("assert on.");
            retreievedUserIds = new HashSet<>();
            retreievedPostIds = new HashSet<>();
            retreievedCommentIds = new HashSet<>();
            retrievedFollowUserIds = new HashSet<>();
            retrievedFansUserIds = new HashSet<>();
        }
    }
}
