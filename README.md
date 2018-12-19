model-view-builder [![Build Status](https://travis-ci.org/PhantomThief/model-view-builder.svg)](https://travis-ci.org/PhantomThief/model-view-builder) [![Coverage Status](https://coveralls.io/repos/PhantomThief/model-view-builder/badge.svg?branch=master&service=github)](https://coveralls.io/github/PhantomThief/model-view-builder?branch=master)
=======================

对象依赖构建以及model到view的映射

* 树形迭代构建整个model结构
* 层级构建依赖
* 对Model零侵入
* 使用构建数据上下文进行View映射

## 为什么构建这个项目

在web场景中，经常会遇到，构建一个列表数据（渲染jsp或者输出json给客户端使用），但是列表用到的数据会依赖别的数据。

举个具体例子：

底层接口返回一个帖子列表：

```Java
List<Post> postList = postService.getList();
```

Post的定义可能是：
```Java
public class Post {
	private int id;
	private int authorUserId;
	private String content;
	// ...other properties...
	// ...getter and setter
	public int getAuthorUserId() {
		return this.authorUserId;
	}
}
```

而最终输出的结果里，可能还需要帖子的作者（authorUserId）的用户信息，帖子的一些状态（比如帖子有多少个评论，当前访问者是否喜欢了帖子），以及作者的一些状态（比如当前用户是否关注了作者之类的）。

最终，构建过程会是一个树形的递归结构：拿到Post后，根据authorUserId，搜集所有用户id，然后再获取用户的数据。可能还需要根据帖子的id构建一些其它数据。

下面就是这些可能的接口定义：
```Java
// 获取用户数据
public Map<Integer, User> getUserByIds(Collection<Integer> userIds);

// 获取帖子的评论数
public Map<Integer, Integer> getPostCommentCount(Collection<Integer> postIds);

// 获取一个用户是否喜欢过这些帖子的状态
public Map<Integer, Boolean> isUserFavoritedPosts(int userId, Collection<Integer> postIds);

// 获取一个用户是否关注另外一些用户的状态
public Map<Integer, Boolean> isUserFollowingUsers(int userId, Collection<Integer> toCheckedUserIds); 
```

这样构建代码可能就会是一些硬编码的foreach循环搜集id，然后再调用接口构建，而且可复用程度不高。

本项目就是提供一个解决这样场景的方案。

## 基本使用

```xml
<dependency>
    <groupId>com.github.phantomthief</groupId>
	<artifactId>model-view-builder</artifactId>
    <version>1.1.1</version>
</dependency>
```

### ModelBuilder的使用：

#### 概念

ModelBuilder构建器分成两组命名空间：id命名空间和value命名空间。命名空间可以是一个Class<?>也可以是一个字符串。

id命名空间存储的是构建过程中用到的id（比如上面提到的场景中，Post的id，User的id就会存入这里）。

value命名空间存储的是构建过程中的具体实体数据（比如上面例子中，Post对象，User对象，以及﹝帖子评论数﹞，﹝是否喜欢过﹞之类的）。

#### 使用方法

构建器ModelBuilder声明时使用下面的方式：
```Java
SimpleModelBuilder<SimpleBuildContext> modelBuilder = new SimpleModelBuilder<SimpleBuildContext>()
	 // 这里使用流式定义modelBuilder的依赖以及构建器之类的……
```

完成声明后，ModelBuilder对象就可以使用了（这个对象建议复用）。

每次构建对象时，用这样的调用：
```Java
SimpleBuildContext buildContext = new SimpleBuildContext();  // 声明一个构建上下文，所有构建的结果都会存入这个上下文对象中
modelBuilder.buildMulti(postList, buildContext); // 执行构建操作
```

构建完成后，所有构建结果都会在上下文buildContext对象中，可以使用这样的语法获得数据：
```Java
int specifyUserId = 23;
User user = buildContext.getDatas(User.class).get(specifyUserId); // 从User.class的value命名空间获得数据

int specifyPostId = 56;
Map<Integer, Integer> postCommentMap = buildContext.getDatas("postComments"); // 从postComments的value命名空间获得数据
int postComment = postCommentMap.getOrDefault(specifyPostId, 0);
```

#### 构建依赖的声明

声明依赖包含三种情况：

##### 从原始对象抽取数据到id命名空间

上面例子中的使用场景：把Post.getAuthorUserId()返回的数据放到id命名空间User.class

```Java
SimpleModelBuilder<BuildContext> modelBuilder = new SimpleModelBuilder<BuildContext>()
	.on(Post.class).id(Post::getAuthorUserId).to(User.class) //post.getAuthoUserId()返回值放到User.class的id命名空间中
```

##### 从已有的value抽取value和id

```Java
SimpleModelBuilder<BuildContext> modelBuilder = new SimpleModelBuilder<BuildContext>()
	.self(Post.class, Post::getId) // post对象放到value为Post.class的命名空间，同时Post.getId()
```

如果遇到没有完成构建的Post对象，会直接把Post对象放到Post.class的value命名空间中，并把Post.getId()放到Post.class的id命名空间中

##### 从id命名空间构建数据到value命名空间

```Java
SimpleModelBuilder<BuildContext> modelBuilder = new SimpleModelBuilder<BuildContext>()
	.build(User.class, userService::getUserByIds) // 把id命名空间User.class用userService.getUserByIds()方法构建数据，并回存到value命名空间User.class
	.build(Post.class).<Integer> by(postService::getPostCommentCount).to("postComments") // 把id命名空间Post.class的数据用postService.getPostCommentCount()方法构建，构建结果存入postComments的value命名空间
```

### ViewMapper的使用

#### 概念

ViewMapper负责把model对象转换为view对象。例如，一个Post对象（如上面定义）可能会和具体的Post对象存储结构耦合。

而最终输出到页面上时，可能并不是Post对象一一对应（比如本例子中，可能有一些字段不会输出，另外一些字段可能并不存在于Post对象中，比如作者的信息，或者一些和访问者相关的状态）。

所以会定义一个PostView，如下：

```Java
public class PostView {
	private Post post;
	private BuildContext buildContext;
	public PostView(Post post, BuildContext buildContext){
		this.post = Post;
		this.buildContext = buildContext;
	}
	public int getId() {
		return post.getId();
	}
	public UserView getAuthor() {
		User author = buildContext.getDatas(User.class).get(post.getAuthorUserId());
		if (author!=null) {
			return new UserView(author, buildContext);
		} else {
			return null;
		}
	}
	public int getCommentCount() {
		Map<Integer, Integer> commentCountMap = buildContext.getDatas("postComments");
		return commentCountMap.getOrDefault(post.getId(), 0);
	}
	// ...other fields...
}
```

在使用时，需要ViewMapper知道Model类到View类的映射，可以使用如下代码进行声明：

```Java
ViewMapper viewMapper = new DefaultViewMapperImpl();
((DefaultViewMapperImpl) viewMapper).addMapper(Post.class, (buildContext, post) -> new PostView(post, buildContext));
```

最终调用：

```Java
List<PostView> postViews = viewMapper.map(postList, bulidContext);
```

## 高级技巧

### 自定义BuildContext

很多使用，希望把一些初始参数放入BuildContext中，这时候可以考虑使用自定义的BuildContext。以需要知道访问者身份的构建器为例：
```Java
public class MyBuildContext extends SimpleBuildContext {
	private int visitor;
	public int getVisitor() {
		return this.visitor;
	}
	public void setVisitor(int visitor){
		this.visitor = visitor;
	}
}
```

然后在声明ModelBuilder时，可以使用MyBuildContext代替默认的BuildContext：
```Java
ModelBuilder<MyBuildContext> modelBuilder = new SimpleModelBuilder<MyBuildContext>()
	.build(Post.class).<Integer> by((buildContext, postIds) -> postService.isUserFavoritedPosts(buildContext.getVisitor(), postIds)).to("userFavoritesPosts");
```

使用构建器时：
```Java
int visitor = 999;
MyBuildContext myBuildContext = new MyBuildContext();
myBuildContext.setVisitor(visitor);

modelBuilder.buildMulti(posts, myBuildContext);
```

### Model中可以直接抽出其它Model的情况

如果一个model里可以获得另外别的model，就可以使用这种方法来抽出元素。举例：
```Java
public class Post {
	private User author;
	public User getAuthor() {
		return this.author;
	}
	private List<User> atUsers;
	public List<User> getAtUsers() {
		return this.atUsers;
	}
}
```

那么依赖声明时可以这样：
```Java
SimpleModelBuilder<BuildContext> modelBuilder = new SimpleModelBuilder<BuildContext>()
	.on(Post.class).value(Post::getAuthor).id(User::getId).to(User.class);
```

### 基于反射的ViewMapper声明

如果View可以按照某些约定去编写（例如放在特定包下，或者使用特定注解作为工厂方法/构建方法之类的），那么可以利用反射去完成构建。这也是ViewMapper声明的推荐做法。

由于View的实现各式各样，这里就不提供统一的工具方法，只是提供一个简单的例子：
```Java
public static final ViewMapper scan(String pkg, Set<Class<?>> ignoreViews) {
    DefaultViewMapperImpl viewMapper = new DefaultViewMapperImpl();
    try {
        ImmutableSet<ClassInfo> topLevelClasses = ClassPath.from(
                ViewerScanner.class.getClassLoader()).getTopLevelClassesRecursive(pkg);
        for (ClassInfo classInfo : topLevelClasses) {
            Class<?> type = classInfo.load();
            if (ignoreViews.contains(type)) {
                continue;
            }
            Constructor<?>[] constructors = type.getConstructors();
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 2 && parameterTypes[1] == BuildContext.class) {
                    logger.info("register view [{}] for model [{}], with buildContext.",
                            type.getSimpleName(), parameterTypes[0].getSimpleName());
                    viewMapper.addMapper(parameterTypes[0], (buildContext, i) -> {
                        try {
                            return constructor.newInstance(i, buildContext);
                        } catch (Exception e) {
                            logger.error("fail to construct model:{}", i, e);
                            return null;
                        }
                    });
                }
                if (parameterTypes.length == 1) {
                    logger.info("register view [{}] for model [{}]", type.getSimpleName(),
                            parameterTypes[0].getSimpleName());
                    viewMapper.addMapper(parameterTypes[0], (buildContext, i) -> {
                        try {
                            return constructor.newInstance(i);
                        } catch (Exception e) {
                            logger.error("fail to construct model:{}", i, e);
                            return null;
                        }
                    });
                }
            }
        }
    } catch (IOException e) {
        logger.error("Ops.", e);
    }
    return viewMapper;
}
```

### 使用OverrideViewMapper进行View映射的剪裁和定制

特定场景下，可能强制覆盖某些Model到View的映射关系，比如正常场景下，User对象会映射成UserView，但是在某个场景下，User对象需要映射到UserCustomizeView，这时候可以使用临时的View映射定制：
```Java
ViewMapper defaultViewMapper = getDefaultViewMapper();
OverrideViewMapper overrideViewMapper = new OverrideViewMapper<>(defaultViewMapper) //
	.addMapper(User.class, (user, buildContext) -> new UserCustmoizeView(user));

List<View> views = overrideViewMapper.map(userList);
```

## 注意事项

### 对象不会被重复构建

如果一个对象（比如id=1的Post对象）如果已经被构建完（它的所有依赖都已经构建完成），在构建过程中，如果再次遇到相同的对象，将不会重复构建。

所以在声明构建依赖关系时，不用担心出现环状声明造成实际构建过程的死循环。

事实上，你完全可以把所有的构建依赖都声明到一个ModelBuilder里，然后工程全局使用这唯一一个ModelBuilder。因为这个ModelBuilder里已经定义了最齐全的依赖关系。

### ModelBuilder依赖声明支持接口

假如有一组对象，都实现了如下接口：
```Java
public interface HasAuthor {
	int getAuthorUserId();
}
```

那么在ModelBuilder声明依赖关系时，可以直接声明这个接口依赖：
```Java
SimpleModelBuilder<BuildContext> modelBuilder = new SimpleModelBuilder<BuildContext>()
	.on(HasAuthor.class).id(HasAuthor::getAuthorUserId).to(User.class);
```

那么所有实现了HasAuthor接口的Model就不用重复声明这个依赖了。抽象类或者父类上的声明关系也遵循这个规则。

### ModelBuilder的定义顺序与构建顺序无关

ModelBuilder在声明时只定义构建过程中各个元素的依赖关系，声明顺序不会影响到构建顺序。而构建过程是一个查找-构建的过程。每次循环会把当前未完成构建的对象，依次执行﹝抽出id﹞、﹝抽出value﹞和﹝构建value﹞三部操作。

每次产生的新的value会在下一轮构建时重复进行。直到没有新的对象被构建出来为止。

### 为什么不提供构建对象的回填机制

考虑最开始的例子：Post有一个getAuthorUserId()方法，返回作者的id。如果需要对象回填的话，就需要额外提供如下方法：
```Java
public User getAuthor();
public void setAuthor(User user);
```
这样，Post就可能存在两个状态：回填前，getAuthor()方法是无效的，而回填后，getAuthor()才可用。这会给后续使用带来很多问题。

另外，回填操作其实是一个相当消耗资源的事情，使用上下文查找其实是把回填操作lazy化（在需要的使用，调用getter时才会查找）。

当然，其实回填操作也可以自己去实现。所以本组件就没有提供这样的机制。

### 为什么使用编程式而不是声明式？

因为我讨厌写配置文件，越复杂的事情，配置文件往往比编程要复杂的多。如果你喜欢配置文件，可以帮我实现一个，也不复杂：）
