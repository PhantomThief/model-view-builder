/**
 * 
 */
package com.github.phantomthief.model.builder;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.github.phantomthief.model.builder.impl.SimpleModelBuilder;
import com.github.phantomthief.model.builder.model.Comment;
import com.github.phantomthief.model.builder.model.HasUser;
import com.github.phantomthief.model.builder.model.Post;
import com.github.phantomthief.model.builder.model.User;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @author w.vela
 */
public class ModelBuilderTest {

    private static org.slf4j.Logger logger = getLogger(ModelBuilderTest.class);

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

        {
            followingMap.put(1, 5);
            followingMap.put(1, 2);
        }

        private Set<Integer> retreievedUserIds;
        private Set<Long> retreievedPostIds;
        private Set<Long> retreievedCommentIds;
        private Set<Integer> retrievedFollowUserIds;

        public Map<Integer, User> getUsers(Collection<Integer> ids) {
            if (retreievedUserIds != null) {
                logger.info("try to get users:{}", ids);
                for (Integer id : ids) {
                    assertTrue(retreievedUserIds.add(id));
                }
            }
            return ids.stream().filter(i -> i <= USER_MAX).collect(toMap(identity(), User::new));
        }

        public Map<Long, Post> getPosts(Collection<Long> ids) {
            if (retreievedPostIds != null) {
                logger.info("try to get posts:{}", ids);
                for (Long id : ids) {
                    assertTrue(retreievedPostIds.add(id));
                }
            }
            return Maps.filterKeys(posts, ids::contains);
        }

        public Map<Long, Comment> getComments(Collection<Long> ids) {
            if (ids == null) {
                return Collections.emptyMap();
            }
            if (retreievedCommentIds != null) {
                logger.info("try to get cmts:{}", ids);
                for (Long id : ids) {
                    assertTrue(retreievedCommentIds.add(id));
                }
            }
            return Maps.filterKeys(cmts, ids::contains);
        }

        public Map<Integer, Boolean> isFollowing(int fromUserId, Collection<Integer> ids) {
            if (retrievedFollowUserIds != null) {
                logger.info("try to get followings:{}->{}", fromUserId, ids);
                for (Integer id : ids) {
                    assertTrue(retrievedFollowUserIds.add(id));
                }
            }
            Collection<Integer> followings = followingMap.get(fromUserId);
            return ids.stream().collect(toMap(identity(), followings::contains));
        }

        void assertOn() {
            logger.info("assert on.");
            retreievedUserIds = new HashSet<>();
            retreievedPostIds = new HashSet<>();
            retreievedCommentIds = new HashSet<>();
            retrievedFollowUserIds = new HashSet<>();
        }
    }

    private TestDAO testDAO;
    private ModelBuilder<TestBuildContext> builder;

    @Before
    public void setup() {
        testDAO = new TestDAO();
        builder = new SimpleModelBuilder<TestBuildContext>() //
                .on(User.class).self(User::getId) //
                .on(Post.class).self(Post::getId) //
                .on(Comment.class).self(Comment::getId) //
                .on(Comment.class).extractId(Comment::getAtUserIds).to(User.class) //
                .on(HasUser.class).extractId(HasUser::getUserId).to(User.class) //
                .on(Post.class).extractValue(Post::comments).<Comment> id(Comment::getId)
                .to(Comment.class) //
                .build(User.class).toSelf(testDAO::getUsers) //
                .build(Post.class).toSelf(testDAO::getPosts) //
                .build(Comment.class).toSelf(testDAO::getComments) //
                .build(User.class)
                .<Integer> using((context, ids) -> testDAO.isFollowing(context.getVisitorId(), ids))
                .to("isFollowing");
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
        sources.addAll(testDAO.getComments(Arrays.asList(3L)).values());
        logger.info("sources===>");
        sources.forEach(o -> logger.info("{}", o));
        testDAO.assertOn();
        builder.build(sources, buildContext);
        logger.info("buildContext===>");
        logger.info("{}", buildContext);

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
        }
        logger.info("fin.");
        assertTrue(true);
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
}
