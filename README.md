model-view-builder
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
    <version>1.0.0</version>
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
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	 // 这里使用流式定义modelBuilder的依赖以及构建器之类的……
	 .build(); // 最终使用build()方法完成定义
```

完成声明后，ModelBuilder对象就可以使用了（这个对象建议复用）。

每次构建对象时，用这样的调用：
```Java
BuildContext buildContext = new BuildContext();  // 声明一个构建上下文，所有构建的结果都会存入这个上下文对象中
modelBuilder.build(postList, buildContext); // 执行构建操作
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
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	.addIdExtractor(Post.class, Post::getAuthorUserId, User.class) //post.getAuthoUserId()返回值放到User.class的id命名空间中
 	.build();
```

##### 从已有的value抽取value和id

```Java
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	.addValueExtractor(Post.class, i -> Collections.singletonMap(i.getId(), i), Post.class) // post对象放到value为Post.class的命名空间，同时Post.getId()
 	.build();
```

如果遇到没有完成构建的Post对象，会直接把Post对象放到Post.class的value命名空间中，并把Post.getId()放到Post.class的id命名空间中

##### 从id命名空间构建数据到value命名空间

```Java
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	.addDataBuilder(User.class, userService::getUserByIds) // 把id命名空间User.class用userService.getUserByIds()方法构建数据，并回存到value命名空间User.class
	.addDataBuilder(Post.class, postService::getPostCommentCount, "postComments") // 把id命名空间Post.class的数据用postService.getPostCommentCount()方法构建，构建结果存入postComments的value命名空间
	.build();
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
viewMapper.addMapper(Post.class, (buildContext, post) -> new PostView(post, buildContext));
```

最终调用：

```Java
List<PostView> postViews = viewMapper.map(postList, bulidContext);
```

## 高级技巧

### 自定义BuildContext

很多使用，希望把一些初始参数放入BuildContext中，这时候可以考虑使用自定义的BuildContext。以需要知道访问者身份的构建器为例：
```Java
public class MyBuildContext extends BuildContext {
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
ModelBuilder<MyBuildContext> modelBuilder = new DefaultModelBuilderImpl<MyBuildContext>()
	.addDataBuilderEx(Post.class, (MyBuildContext buildContext, postIds) -> postService.isUserFavoritedPosts(buildContext.getVisitor(), postIds), "userFavoritesPosts");
	.build();
```

使用构建器时：
```Java
int visitor = 999;
MyBuildContext myBuildContext = new MyBuildContext();
myBuildContext.setVisitor(visitor);

modelBuilder.build(posts, myBuildContext);
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
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	.addValueExtractor(Post.class, post -> {
		Map<Integer, User> userMap = new HashMap<>();
		userMap.put(post.getAuthor().getId(), post.getAuthor());
		post.getAtUsers().forEach(user -> userMap.put(user.getId(), user));
		return userMap;
	}, User.class)
	.build();
```

### 基于反射的ViewMapper声明

TODO

### 使用merge方式简化ModelBuilder的维护

TODO

### 基于注解的ModelBuilder声明

TODO

### 使用OverrideViewMapper进行View映射的剪裁和定制

TODO

## 注意事项

### 对象不会被重复构建

如果一个对象（比如id=1的Post对象）如果已经被构建完（它的所有依赖都已经构建完成），在构建过程中，如果再次遇到相同的对象，将不会重复构建。

所以在声明构建依赖关系时，不用担心出现环状声明造成实际构建过程的死循环。

事实上，你完全可以把所有的构建依赖都声明到一个ModelBuilder里，然后工程全局使用这唯一一个ModelBuilder。因为这个ModelBuilder里已经定义了最齐全的依赖关系。

### ModelBuilder依赖声明支持接口

假如有一组对象，都实现了如下接口：
```Java
public interface HasAuthor {
	public int getAuthorUserId();
}
```

那么在ModelBuilder声明依赖关系时，可以直接声明这个接口依赖：
```Java
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
	.addIdExtractor(HasAuthor.class, HasAuthor::getAuthorUserId, User.class)
 	.build();
```

那么所有实现了HasAuthor接口的Model就不用重复声明这个依赖了。抽象类或者父类上的声明关系也遵循这个规则。

### ModelBuilder的定义顺序与构建顺序无关

ModelBuilder在声明时只定义构建过程中各个元素的依赖关系，声明顺序不会影响到构建顺序。而构建过程是一个查找-构建的过程。每次循环会把当前未完成构建的对象，依次执行﹝抽出id﹞、﹝抽出value﹞和﹝构建value﹞三部操作。

每次产生的新的value会在下一轮构建时重复进行。直到没有新的对象被构建出来为止。