# 🗺️ 60 天学会 Java 后端开发 — 完整路线图

> 每天 3-4 小时，60 天建立 Java 后端就业面试所需的核心能力。

## 总览

- Day 1-15：阶段一：Java SE 核心基础。建立 Java 语法、面向对象、异常、集合和 IO 的基本编码能力。
- Day 16-30：阶段二：现代 Java 与工程化。掌握 Lambda、Stream、Maven、测试、日志、SQL、JDBC 和 MyBatis。
- Day 31-40：阶段三：Spring Boot 后端开发。能独立设计并实现规范的 Spring Boot REST API。
- Day 41-50：阶段四：认证、安全、缓存与生产能力。补齐认证、权限、安全、Redis、异步任务和文件上传能力。
- Day 51-60：阶段五：并发、JVM、部署与面试冲刺。完成并发、JVM、Docker、测试、性能优化、系统设计和项目包装。

## 每日路线图

| Day | 主题 | 核心知识点 | 产出物 | 优先级 | 预计课时 |
|---|---|---|---|---|---|
| 01 | 环境搭建与 Java 初印象 | JDK 21、IDEA、Maven、Git、首个 Java 程序 | 开发环境就绪 + Hello Java | L1 必须掌握 | 3小时 |
| 02 | Java 基础语法 | 变量、基本类型、运算符、流程控制、输入输出 | 命令行计算器 | L1 必须掌握 | 3小时 |
| 03 | 方法与调试 | 方法、参数、返回值、重载、断点调试 | 小型工具函数库 | L1 必须掌握 | 3小时 |
| 04 | 数组与字符串 | 数组、String、StringBuilder、常见字符串处理 | 文本统计工具 | L1 必须掌握 | 3小时 |
| 05 | 面向对象入门 | 类、对象、构造器、封装、this、static | 用户信息管理小练习 | L1 必须掌握 | 3小时 |
| 06 | 继承与多态 | 继承、抽象类、接口、重写、向上转型 | 支付方式模拟器 | L1 必须掌握 | 4小时 |
| 07 | 包、访问控制与常用类 | package、访问修饰符、Math、Objects、Date/Time | 日期工具类 | L1 必须掌握 | 3小时 |
| 08 | 异常处理 | checked/unchecked、try/catch/finally、自定义异常 | 统一异常练习 | L1 必须掌握 | 3小时 |
| 09 | 枚举与泛型入门 | enum、泛型类、泛型方法、类型边界 | 通用响应对象 | L2 项目常用 | 3小时 |
| 10 | 里程碑：Java SE 小项目 | 代码整理、README、Git 提交、基础测验 | CLI 学生管理系统 | L1 必须掌握 | 4小时 |
| 11 | 集合框架上 | List、Set、Map、Iterator、集合选择 | 商品购物车模型 | L1 必须掌握 | 3小时 |
| 12 | 集合框架下 | HashMap 原理、equals/hashCode、Comparable/Comparator | 排行榜功能 | L3 面试高频 | 4小时 |
| 13 | IO 基础 | File、InputStream、OutputStream、Reader/Writer | 文件复制工具 | L1 必须掌握 | 3小时 |
| 14 | NIO 与序列化 | Path、Files、Charset、JSON 序列化概念 | 本地数据存储 | L2 项目常用 | 3小时 |
| 15 | 阶段一总结测验 | Java SE 基础回顾、限时代码题、错题整理 | 阶段一学习笔记 | L1 必须掌握 | 3小时 |
| 16 | Lambda 与函数式接口 | Lambda、函数式接口、方法引用、Predicate/Function | 集合数据处理 | L1 必须掌握 | 3小时 |
| 17 | Stream API | map/filter/reduce/collect/groupingBy、流式聚合 | 订单统计练习 | L1 必须掌握 | 4小时 |
| 18 | Optional 与时间 API | Optional、LocalDateTime、Duration、格式化 | 日期统计工具 | L2 项目常用 | 3小时 |
| 19 | 注解与反射 | Annotation、Class、Field、Method、运行时元数据 | 简易对象映射器 | L3 面试高频 | 4小时 |
| 20 | 里程碑：Java 工具库 | 集合、IO、Stream、泛型、反射综合 | mini-utils 项目 | L1 必须掌握 | 4小时 |
| 21 | Maven 工程化 | pom、依赖、生命周期、插件、多模块概念 | Maven 多模块雏形 | L1 必须掌握 | 3小时 |
| 22 | 单元测试 | JUnit 5、断言、参数化测试、测试命名 | 工具库测试覆盖 | L1 必须掌握 | 3小时 |
| 23 | 日志体系 | SLF4J、Logback、日志级别、日志格式 | 标准日志配置 | L2 项目常用 | 3小时 |
| 24 | JDBC 入门 | Driver、Connection、PreparedStatement、连接释放 | 原生 JDBC CRUD | L1 必须掌握 | 4小时 |
| 25 | SQL 基础 | 表设计、CRUD、约束、索引入门 | 用户/文章表 | L1 必须掌握 | 3小时 |
| 26 | SQL 进阶 | JOIN、聚合、分页、事务、执行计划入门 | 10 道 SQL 练习 | L3 面试高频 | 4小时 |
| 27 | 数据库建模 | 一对多、多对多、ER 图、范式、字段设计 | 博客系统 ER 图 | L2 项目常用 | 3小时 |
| 28 | MyBatis 入门 | Mapper、XML/注解、动态 SQL、结果映射 | 博客 CRUD | L1 必须掌握 | 4小时 |
| 29 | MyBatis 进阶 | 分页、事务、N+1、批量操作、SQL 日志 | 完整查询接口 | L3 面试高频 | 4小时 |
| 30 | 里程碑：博客 DAO 层 | JDBC/MyBatis/SQL 整合、README、测试 | 博客数据层 v1 | L1 必须掌握 | 4小时 |
| 31 | Spring 基础 | IoC、DI、Bean、配置方式、生命周期 | Spring Hello World | L1 必须掌握 | 4小时 |
| 32 | Spring Boot 入门 | Starter、自动配置、Controller、Service | 第一个 REST API | L1 必须掌握 | 4小时 |
| 33 | REST API 设计 | 资源命名、状态码、DTO、统一响应、版本化 | 博客 API 规范 | L3 面试高频 | 3小时 |
| 34 | 参数校验与异常处理 | Validation、全局异常处理、错误码设计 | 标准错误响应 | L1 必须掌握 | 4小时 |
| 35 | 分层架构与事务 | Controller/Service/Repository、事务边界、业务异常 | 博客 API 重构 | L1 必须掌握 | 4小时 |
| 36 | Spring Boot + MyBatis | 数据源、Mapper 扫描、分页、事务整合 | 博客后端 CRUD | L1 必须掌握 | 4小时 |
| 37 | Spring Data JPA 可选线 | Entity、Repository、关联映射、MyBatis/JPA 对比 | ORM 对比笔记 | L2 项目常用 | 3小时 |
| 38 | OpenAPI 文档 | springdoc、接口分组、请求示例、调试 | Swagger 文档 | L2 项目常用 | 3小时 |
| 39 | 配置与环境 | profile、配置注入、敏感信息、环境隔离 | dev/prod 配置 | L2 项目常用 | 3小时 |
| 40 | 里程碑：博客 API v1 | Spring Boot + DB + 文档 + 测试 + GitHub | 可演示后端项目 | L1 必须掌握 | 4小时 |
| 41 | 认证基础 | Session、JWT、密码哈希、登录流程设计 | 注册登录设计 | L3 面试高频 | 3小时 |
| 42 | JWT 实战 | Access Token、Refresh Token、拦截器、续签 | 登录注册接口 | L1 必须掌握 | 4小时 |
| 43 | 权限控制 | RBAC、角色、权限、资源鉴权、管理员接口 | 管理员权限 | L2 项目常用 | 4小时 |
| 44 | Spring Security 入门 | Filter Chain、Authentication、Authorization | Security 接入 | L1 必须掌握 | 4小时 |
| 45 | Web 安全防护 | XSS、CSRF、SQL 注入、限流、敏感信息保护 | 安全检查清单 | L3 面试高频 | 3小时 |
| 46 | Redis 基础与缓存 | 数据结构、过期时间、Cache-Aside、缓存命名 | 热门文章缓存 | L1 必须掌握 | 4小时 |
| 47 | Redis 进阶 | 缓存穿透/击穿/雪崩、分布式锁、排行榜 | 缓存保护方案 | L3 面试高频 | 4小时 |
| 48 | 消息队列概念 | 异步任务、重试、死信、RabbitMQ/Kafka 对比 | 评论通知设计 | L2 项目常用 | 3小时 |
| 49 | 文件上传 | Multipart、文件校验、对象存储概念、访问 URL | 头像/封面上传 | L2 项目常用 | 3小时 |
| 50 | 里程碑：博客 API v2 | 认证、权限、缓存、安全、文件上传整合 | 简历级后端项目 | L1 必须掌握 | 4小时 |
| 51 | 并发基础 | Thread、Runnable、Callable、线程状态、join | 多线程下载模拟 | L3 面试高频 | 4小时 |
| 52 | 线程池与异步 | Executor、ThreadPoolExecutor、CompletableFuture | 异步任务执行器 | L3 面试高频 | 4小时 |
| 53 | 并发安全 | synchronized、Lock、Atomic、volatile、可见性 | 并发计数器实验 | L3 面试高频 | 4小时 |
| 54 | JVM 基础 | 内存区域、类加载、GC 概念、常见参数 | JVM 笔记 | L3 面试高频 | 4小时 |
| 55 | 里程碑：并发 + JVM 测验 | 高频面试题、代码实验、错题复盘 | 面试题答案集 | L1 必须掌握 | 4小时 |
| 56 | Docker 部署 | Dockerfile、Compose、MySQL/Redis 编排、健康检查 | 一键启动项目 | L2 项目常用 | 4小时 |
| 57 | 测试策略 | 单元测试、集成测试、MockMvc、测试数据 | 核心接口测试 | L2 项目常用 | 4小时 |
| 58 | 性能优化 | SQL 索引、慢查询、接口压测、日志排查 | 优化报告 | L3 面试高频 | 4小时 |
| 59 | 系统设计与面试 | 限流、缓存、排行榜、短链、登录系统设计 | 3 道设计题笔记 | L3 面试高频 | 4小时 |
| 60 | 总结与进阶路线 | 项目包装、简历描述、JDK 25 新特性、后续路线 | 结业总结 + 简历项目说明 | L1 必须掌握 | 3小时 |

## 里程碑检查点

| 检查点 | 天数 | 产出 | 技能验证 |
|---|---|---|---|
| 🏆 Mile 1 | Day 10 | CLI 学生管理系统 | Java 基础、OOP、异常、文件组织 |
| 🎯 Review 1 | Day 15 | 阶段一测验 | Java SE 基础掌握 |
| 🏆 Mile 2 | Day 20 | mini-utils 工具库 | 集合、IO、Stream、泛型、反射 |
| 🏆 Mile 3 | Day 30 | 博客数据层 v1 | SQL、事务、建模、JDBC/MyBatis |
| 🏆 Mile 4 | Day 40 | 博客 API v1 | Spring Boot、REST、校验、文档 |
| 🏆 Mile 5 | Day 50 | 博客 API v2 | JWT、RBAC、Redis、安全、上传 |
| 🎯 Review 2 | Day 55 | 并发 + JVM 测验 | 高频面试题和代码实验 |
| 🎯 Final | Day 60 | 结业总结 | 简历项目、部署、系统设计、进阶路线 |

## 面试知识点清单

### Java 核心

- [ ] OOP、接口、抽象类、封装、继承、多态
- [ ] String、StringBuilder、包装类型、常用类
- [ ] 异常体系和自定义异常
- [ ] 泛型、枚举、注解、反射
- [ ] 集合框架、HashMap、equals/hashCode
- [ ] IO/NIO、序列化、文件处理
- [ ] Lambda、Stream、Optional、时间 API

### 后端工程

- [ ] Maven 依赖和生命周期
- [ ] JUnit 5 单元测试
- [ ] SLF4J/Logback 日志
- [ ] SQL、JOIN、索引、事务、建模
- [ ] JDBC 和 MyBatis
- [ ] Spring IoC/DI、Bean 生命周期
- [ ] Spring Boot 自动配置和 REST API
- [ ] 参数校验、全局异常、统一响应

### 生产与面试进阶

- [ ] JWT、RBAC、Spring Security
- [ ] Web 安全基础
- [ ] Redis 缓存策略和缓存问题
- [ ] 线程、线程池、锁、原子类、volatile
- [ ] JVM 内存区域、类加载、GC
- [ ] Docker、接口测试、性能优化
- [ ] 限流、缓存、排行榜、短链、登录系统设计
