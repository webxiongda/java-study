# 继承与多态 项目任务

## 业务背景

你在为一个在线教育平台开发课程内容管理模块。平台有三种类型的课程内容：

- **视频课（VideoLesson）**：有时长（分钟），可以播放/暂停
- **文档课（DocumentLesson）**：有页数，可以阅读/标注
- **测验课（QuizLesson）**：有题目数量和通过分数，可以开始答题/提交

所有内容类型都有：标题、作者、预估完成时间（分钟），以及"开始学习"和"获取学习摘要"两个操作。

---

## 技术要求

1. 使用**抽象类**定义 `LessonContent` 基类，包含公共字段和公共方法
2. 使用**方法重写（@Override）**让不同类型的课程返回不同的"学习摘要"
3. 使用**接口**定义额外能力，例如 `Bookmarkable`（可收藏，含 `bookmark()` 和 `getBookmarkCount()` 方法），并让 VideoLesson 和 DocumentLesson 实现它
4. 使用**多态**：写一个 `CoursePlayer` 类，其 `playAll(List<LessonContent> lessons)` 方法统一处理所有类型的课程
5. 使用 `instanceof`（Java 16+ pattern matching 写法）在 `playAll` 中针对 VideoLesson 额外打印时长信息

---

## 任务说明

### 需要实现的类结构

```
LessonContent（抽象类）
├── VideoLesson（具体类，实现 Bookmarkable）
├── DocumentLesson（具体类，实现 Bookmarkable）
└── QuizLesson（具体类）

Bookmarkable（接口）
CoursePlayer（工具类）
Main（入口，构造测试数据并调用 CoursePlayer）
```

### 输入/输出示例

构造如下课程列表，调用 `CoursePlayer.playAll(lessons)` 后，期望输出类似：

```
[Course Player] Starting lesson 1/3
Title: Java 基础语法
Type: Video | Duration: 45 mins
Author: 张老师 | Est. Time: 45 mins
Summary: 视频课「Java 基础语法」，时长 45 分钟，已播放 0 次
---
[Course Player] Starting lesson 2/3
Title: Java 设计模式文档
Type: Document | Pages: 30
Author: 李老师 | Est. Time: 60 mins
Summary: 文档课「Java 设计模式文档」，共 30 页，已收藏 0 次
---
[Course Player] Starting lesson 3/3
Title: Java 基础测验
Type: Quiz | Questions: 20 | Pass Score: 60
Author: 王老师 | Est. Time: 30 mins
Summary: 测验课「Java 基础测验」，共 20 题，通过分数线 60 分
---
```

---

## 验收标准

- [ ] `LessonContent` 是抽象类，包含 `title`、`author`、`estimatedMinutes` 字段和抽象方法 `getSummary()`、`getType()`
- [ ] 三个子类各自实现 `getSummary()` 和 `getType()`，内容不同
- [ ] `Bookmarkable` 接口包含 `bookmark()` 和 `getBookmarkCount()` 方法，`VideoLesson` 和 `DocumentLesson` 实现它
- [ ] `CoursePlayer.playAll()` 接受 `List<LessonContent>` 参数，方法内用多态统一调用，不做类型判断（除了额外信息打印）
- [ ] 主方法中创建至少 3 个不同类型的课程对象，存入 `List<LessonContent>`，调用 `playAll` 输出结果

---

## 提示（不是答案）

1. `LessonContent` 中可以写一个非抽象的 `printInfo()` 方法，调用 `getType()`（子类实现）和 `getSummary()`（子类实现），这样 `CoursePlayer` 只需调用 `printInfo()` 就能得到完整输出——这就是模板方法模式的思路
2. `Bookmarkable` 可以用一个 `private int bookmarkCount` 字段 + 实现方法来完成，但接口字段只能是常量，所以这个计数字段只能放在实现类里

---

## 常见坑

1. **忘记 `super(title, author, estimatedMinutes)`**：子类构造器如果父类没有无参构造器，必须显式调用 `super()`，否则编译失败

2. **接口方法签名写错导致没有真正重写**：例如把 `bookmark()` 写成 `addBookmark()`，实现类编译通过了（因为接口方法变成了未实现的抽象方法），运行时才发现行为不对。建议实现类的对应方法都加上 `@Override`

3. **多态丢失子类特有方法**：将 `VideoLesson` 存入 `List<LessonContent>` 后，无法直接调用 `bookmark()`，因为 `LessonContent` 类型没有这个方法。需要先用 `instanceof VideoLesson v` 判断再调用，或者将变量声明为 `List<LessonContent & Bookmarkable>`（Java 不支持这种写法，需要另想设计）
