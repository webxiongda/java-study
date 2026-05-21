# Chapter 40 里程碑：博客APIv1 - 理论篇

## 一、学习定位

本章是阶段二（21-40）的 **产出验收**。前 19 章分别学了 Maven / 测试 / 日志 / JDBC / SQL / MyBatis / Spring / Boot / REST / 校验 / 事务 / OpenAPI / 配置，但每一章都是碎片。这章就是把这些碎片**拼成一个可运行、可演示的博客 API 后端**。

- 优先级：L1 必须掌握
- 预计投入：4 小时
- 阶段产出：可演示的博客 API v1（Git 仓库 tag v1.0.0，Swagger 可打开，CRUD 可跑通）

## 二、核心概念

### 1. 整合交付（Integration Delivery）

"学完了"和"能交付"是两回事。整合交付就是：你学了 19 章的东西，今天要跑起来给别人看。

| 维度 | 碎片的 | 整合的 |
|------|--------|--------|
| 数据库 | 知道 MyBatis 映射 | 建表 SQL + 迁移脚本 + Flyway 版本管理 |
| REST | 知道 @GetMapping | 统一 Result / 分页 / 错误码 / 异常处理 全链路 |
| 文档 | 知道 @Operation | Swagger UI 可打开、可 Try it out |
| 测试 | 知道 @Test | Service 单测 + 集成测试覆盖核心路径 |
| Git | 知道 git commit | tag v1.0.0，commit 按章清晰 |

**核心原则**：

1. **可运行优先**：先确保 `mvn spring-boot:run` 一次成功，再谈优化
2. **可演示优先**：Swagger UI 能打开、能点、能看到数据，比代码结构完美更重要
3. **可扩展优先**：v1 不追求全功能，但要为 v2（认证、缓存、MQ）留好接口

### 2. 验收标准（Acceptance Criteria）

做之前先知道什么叫"做完"。博客 API v1 的验收标准可以分层：

```
L0 - 能启动
  mvn spring-boot:run → 控制台无异常
  curl localhost:8080/actuator/health → UP

L1 - 能演示核心流程
  发布文章 → 查询文章列表 → 查看文章详情 → 更新文章 → 删除文章
  每个接口用 Swagger UI 或 curl 验证

L2 - 有体面的错误处理
  参数校验失败 → 400 + 明确错误信息
  文章不存在 → 404
  未预期的异常 → 500 + 统一 JSON

L3 - 有文档
  Swagger UI 分组（client / admin）
  每个 DTO 有 @Schema(example)
  每个接口有 @Operation(summary)

L4 - 有测试
  Service 层 ≥ 5 个单测
  Controller 层 ≥ 2 个 @SpringBootTest + MockMvc
```

不要 L4 还没做到就开始搞 L0。按层验收。

### 3. README（项目说明书）

一个后端项目的 README 应该回答 4 个问题：

```
1. 这是什么项目？           → 一句话：博客后端 API
2. 怎么启动？              → 环境要求 + mvn spring-boot:run
3. 启动后有什么？           → API 列表 + 文档地址
4. 项目结构是什么？         → 包结构图
```

**最小 README 模板**：

```markdown
# Blog API v1

博客后端，Spring Boot 3.3 + MyBatis + MySQL。

## 启动

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE blog DEFAULT CHARSET utf8mb4"

# 2. 初始化表结构
mysql -u root -p blog < src/main/resources/sql/init.sql

# 3. 启动
mvn spring-boot:run

# 4. 访问
curl http://localhost:8080/actuator/health
```

## 文档

- Swagger UI：http://localhost:8080/swagger-ui.html

## 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/posts | 发布文章 |
| GET | /api/v1/posts | 文章列表（分页） |
| GET | /api/v1/posts/{id} | 文章详情 |
| PUT | /api/v1/posts/{id} | 更新文章 |
| DELETE | /api/v1/posts/{id} | 删除文章 |
| GET | /api/v1/tags | 标签列表 |
| GET | /actuator/health | 健康检查 |

## 技术栈

- JDK 21 / Spring Boot 3.3.5 / MyBatis 3.0 / MySQL 8
```

### 4. 测试策略（Test Strategy）

不是所有代码都要测。对 v1 版本，有的放矢：

| 测试类型 | 覆盖目标 | 数量要求 |
|----------|----------|----------|
| 单元测试（JUnit 5 + Mockito） | Service 层核心逻辑 | ≥ 5 个 |
| 集成测试（@SpringBootTest + MockMvc） | Controller 端到端 | ≥ 2 个 |
| 手动测试（curl / Swagger） | 所有接口 | 每个接口至少 1 次 |

**单元测试关注什么**：

```java
// ✅ 测业务逻辑
void publish_shouldCreatePostAndBindTags() {
    // given
    PostCreateReq req = new PostCreateReq("title", "content", List.of(1L));
    when(tagMapper.selectById(1L)).thenReturn(new TagDO(1L, "Java"));

    // when
    postService.publish(req, 1L);

    // then
    verify(postMapper).insert(any(PostDO.class));
    verify(postTagMapper).batchInsert(anyList());
}

// ✅ 测边界
void publish_shouldThrow_whenTagNotExist() {
    // given
    PostCreateReq req = new PostCreateReq("title", "content", List.of(999L));
    when(tagMapper.selectById(999L)).thenReturn(null);

    // then
    assertThatThrownBy(() -> postService.publish(req, 1L))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("标签不存在");
}
```

**不测什么**（依赖框架的代码，相信框架）：
- Mapper XML 映射（MyBatis 自身测试覆盖）
- Spring 容器启动（@SpringBootTest 只写 2 个通路验证就行）

### 5. 复盘（Retrospective）

里程碑最关键的一步：做完后回头想。

**复盘 checklist**：

```
□ 哪个环节耗时最长？为什么？
  → 最常见：数据库连接失败 / MyBatis 配置问题 / 依赖版本冲突
  → 下一次怎么做？用 P6Spy / 先跑通 actuator/health 再写业务代码

□ 哪个接口最容易出 Bug？
  → 最常见：查询列表（分页+排序+条件组合）
  → 下一次怎么做？写集成测试时优先覆盖列表查询

□ 代码结构有什么不满意？
  → 最常见：Service 方法太长、缺乏 VO 封装
  → 下一次怎么做？重构时优先拆分长方法

□ 文档和实际行为一致吗？
  → 最常见：加了接口忘了加 OpenAPI 注解
  → 下一次怎么做？CI 中对比 /v3/api-docs diff

□ 如果现在要加认证（JWT），需要在哪些地方动手？
  → Filter + SecurityConfig + @AuthenticationPrincipal
```

## 三、工作原理

| 维度 | 要点 |
|------|------|
| 入口 | `mvn spring-boot:run` → SpringApplication → 自动配置 → DispatcherServlet |
| 配置 | `application.yml`（基础）+ `application-dev.yml`（开发）→ Environment 绑定 |
| 执行 | Controller 接收请求 → Service 业务逻辑 → Mapper 数据库操作 → 返回统一 Result |
| 边界 | 参数校验失败 @Valid → MethodArgumentNotValidException → RestControllerAdvice 统一返回 400 |
| 验证 | 单元测试 + MockMvc 集成测试 + Swagger UI 手动验证 + curl 自动化脚本 |

**完整的请求链路**：

```
curl POST /api/v1/posts
  → Tomcat 接受连接
  → DispatcherServlet 路由到 PostController.create()
  → @Valid 校验 PostCreateReq → 失败则异常处理器返回 400
  → PostService.publish()
    → @Transactional 开启事务
    → postMapper.insert() 插入文章
    → tagMapper.selectById() 验证标签存在
    → postTagMapper.batchInsert() 绑定标签
    → 返回 PostVO
  → PostController 包装为 Result<PostVO> 返回
  → Jackson 序列化 JSON → 响应客户端
```

## 四、在博客项目里的落点

```
blog-api/
├── pom.xml                          # parent: spring-boot-starter-parent 3.3.5
├── src/main/java/com/example/blog/
│   ├── BlogApplication.java         # @SpringBootApplication
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java          # 统一响应
│   │   │   └── PageResult.java      # 分页响应
│   │   ├── exception/
│   │   │   ├── BusinessException.java
│   │   │   ├── ErrorCode.java       # 错误码枚举
│   │   │   └── GlobalExceptionHandler.java
│   │   └── config/
│   │       └── OpenApiConfig.java   # Swagger 配置 + JWT SecurityScheme
│   ├── post/
│   │   ├── PostController.java
│   │   ├── PostService.java
│   │   ├── PostMapper.java
│   │   ├── PostDO.java
│   │   ├── PostVO.java
│   │   └── PostCreateReq.java
│   └── tag/
│       ├── TagController.java
│       ├── TagService.java
│       ├── TagMapper.java
│       └── TagDO.java
└── src/main/resources/
    ├── application.yml
    ├── mapper/
    │   ├── PostMapper.xml
    │   └── TagMapper.xml
    └── sql/
        └── init.sql                  # 建表 + 种子数据
```

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| `mvn spring-boot:run` 报 `Failed to configure DataSource` | 没配置 datasource 或 MySQL 没启动 | 检查 application.yml + MySQL 是否运行 |
| Swagger UI 打开 404 | 没加 springdoc 依赖或配置 | 检查 pom.xml + `springdoc.swagger-ui.enabled=true` |
| @Valid 不生效 | 没加 `@Validated` / 方法参数没加 `@Valid` | Controller 方法参数上必须加 `@Valid` |
| 事务不回滚 | 内部自调用 / 非 public 方法 | 注入代理对象 self，用 self.method() 调用 |
| 分页查不到数据 | PageHelper 线程安全问题 / 页码从 0 开始 | 确保 PageHelper.startPage() 后紧跟第一个查询 |
| /actuator/health 返回 DOWN | 依赖服务不可用（DB 连不上） | 检查 MySQL 连接 + `spring.datasource.url` |

## 六、面试高频问题

1. 你完整的博客 API 项目包含哪些模块？为什么这样分？
2. 如果现在要加一个评论功能，你需要在哪些地方修改代码？
3. 你的项目如何保证接口数据的一致性（事务）？
4. 你的项目如何做参数校验？全局异常处理怎么设计的？
5. 你的项目如何组织多环境配置？生产密码怎么管理的？
6. 如果接口 QPS 到 1000，你觉得哪个地方会先撑不住？为什么？
7. v1 和 v2 的差距是什么？哪些是你要改进的？
