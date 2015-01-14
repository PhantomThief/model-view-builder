model-view-builder
=======================

支持树形结构的model构建，并可把model转换成view

* 树形迭代构建整个model结构
* 层级构建依赖
* 对Model零侵入

## 使用

```xml
<dependency>
    <groupId>com.github.phantomthief</groupId>
	<artifactId>model-view-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

```Java
// 初始化model builder
ModelBuilder<BuildContext> modelBuilder = new DefaultModelBuilderImpl<BuildContext>()
        //
        // 定义id依赖关系
        .addIdExtractor(User.class, User::getAvatarImageId, Image.class)
        .addIdExtractor(HasPost.class, HasPost::getPostId, Post.class)
        .addIdExtractor(HasAuthor.class, HasAuthor::getAuthorId, User.class)
        .addIdExtractor(HasComment.class, HasComment::getCommentId, Comment.class)
        .addIdExtractor(NewFriendMessage.class, NewFriendMessage::getUserIds, User.class)
        .addIdExtractor(PlainTextMessage.class, PlainTextMessage::getAvatarId, Image.class)
        .addIdExtractor(Emotion.class, Emotion::getImageId, Image.class)
        .addIdExtractor(EmotionCommentModel.class, EmotionCommentModel::getEmotionId,
                Emotion.class)

        // 定义值直接抽出的关系
        .addValueExtractor(Post.class,
                model -> Collections.singletonMap(model.getId(), model), Post.class)
        .addValueExtractor(User.class,
                model -> Collections.singletonMap(model.getId(), model), User.class)
        .addValueExtractor(Comment.class,
                model -> Collections.singletonMap(model.getId(), model), Comment.class)
        .addValueExtractor(Image.class,
                model -> Collections.singletonMap(model.getId(), model), Image.class)

        // 定义数据构建器
        .addDataBuilder(User.class, userService::getByIds) //
        .addDataBuilder(Post.class, postService::getByIds) //
        .addDataBuilder(Comment.class, commentService::getByIds) //
        .addDataBuilder(Image.class, imageService::getByIds) //
        .addDataBuilder(Emotion.class, emotionService::getByIds) //
;

// 构建对象

List<Message> messages = getFromRepository(ownerUserId); // 获取原始对象
BuildContext buildContext = new BuildContext(); // 初始化构建上下文
modelBuilder.build(messages, buildContext); // 构建，会把所有依赖对象都构建完成，并存入buildContext中

// mapper使用
// 定义mapper
ViewMapper viewMapper = new DefaultViewMapperImpl();
viewMapper.addMapper(Message.class, (buildContext, message) -> new MessageView(buildContext, message));
// ...

// 映射
List<MessageView> messageViews = viewMapper.map(messages, buildContext);
```