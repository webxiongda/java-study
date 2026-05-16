<p align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="120" alt="Java Logo" />
</p>

<h1 align="center">60 天学会 Java 后端开发</h1>

<p align="center">
  <strong>一套面向有编程基础学习者的 60 天 Java 后端就业面试路线</strong>
</p>

<p align="center">
  从 Java SE 到 Spring Boot、数据库、Redis、Docker、JVM、并发与项目实战，目标是能独立交付 REST API 并通过 Java 后端面试。
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> •
  <a href="#课程大纲">课程大纲</a> •
  <a href="#项目实战">项目实战</a> •
  <a href="./ROADMAP.md">完整路线图</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/天数-60天-brightgreen" alt="60 Days" />
  <img src="https://img.shields.io/badge/每天-3~4小时-blue" alt="3-4 hours/day" />
  <img src="https://img.shields.io/badge/JDK-21_LTS-orange" alt="JDK 21" />
  <img src="https://img.shields.io/badge/目标-Java后端就业-red" alt="Java Backend" />
</p>

---

## 项目简介

这个目录参考 `node-study` 的组织方式，提供一套 Java 后端 60 天学习路线。它不是只列知识点，而是把每天的学习目标、实践产出、里程碑项目、进度追踪和复习机制放在一起。

### 适合谁？

- 有编程基础，但 Java 学得不系统
- 想转 Java 后端或准备 Java 后端面试
- 希望通过项目掌握 Spring Boot、数据库、Redis 和部署

### 你会学到什么？

| 能力维度 | 具体技能 |
|---|---|
| Java 基础 | 语法、OOP、异常、泛型、集合、IO、Stream |
| 工程化 | Maven、JUnit、日志、分层结构、Git 提交 |
| 数据库 | SQL、事务、建模、JDBC、MyBatis、索引 |
| Web 后端 | Spring、Spring Boot、REST API、校验、异常、OpenAPI |
| 认证安全 | JWT、RBAC、Spring Security、Web 安全基础 |
| 中间件 | Redis 缓存、缓存问题、消息队列概念 |
| 进阶面试 | 并发、线程池、JVM、性能优化、系统设计 |

## 快速开始

### 前置要求

- JDK 21 LTS
- IntelliJ IDEA 或 VS Code
- Maven 3.9+
- Git
- Docker Desktop（Day 24 起建议安装，Day 56 必需）
- 每天 3-4 小时学习时间

### 开始学习

```bash
cd 60-days-java
cd days/day-01
# 阅读 README.md 开始学习
```

### 学习方式

1. 按天推进，不跳过里程碑。
2. 每天必须写代码、写笔记、提交 Git commit。
3. 每 3 天、7 天、30 天按 `../java-learning/review-plan.md` 做复习。
4. 每个里程碑项目都要有 README、运行方式、核心功能和总结。

## 课程大纲

### 🟢 阶段一：Java SE 核心基础（Day 1-15）

从环境、语法、OOP、异常、泛型、集合和 IO 打稳 Java 编码基础。

| 天数 | 主题 | 关键词 |
|---|---|---|
| Day 01 | [环境搭建与 Java 初印象](./days/day-01/) | JDK 21、IDEA、Maven、Git、首个 Java 程序 |
| Day 02 | [Java 基础语法](./days/day-02/) | 变量、基本类型、运算符、流程控制、输入输出 |
| Day 03 | [方法与调试](./days/day-03/) | 方法、参数、返回值、重载、断点调试 |
| Day 04 | [数组与字符串](./days/day-04/) | 数组、String、StringBuilder、常见字符串处理 |
| Day 05 | [面向对象入门](./days/day-05/) | 类、对象、构造器、封装、this、static |
| Day 06 | [继承与多态](./days/day-06/) | 继承、抽象类、接口、重写、向上转型 |
| Day 07 | [包、访问控制与常用类](./days/day-07/) | package、访问修饰符、Math、Objects、Date/Time |
| Day 08 | [异常处理](./days/day-08/) | checked/unchecked、try/catch/finally、自定义异常 |
| Day 09 | [枚举与泛型入门](./days/day-09/) | enum、泛型类、泛型方法、类型边界 |
| Day 10 | [里程碑：Java SE 小项目](./days/day-10/) | 代码整理、README、Git 提交、基础测验 |
| Day 11 | [集合框架上](./days/day-11/) | List、Set、Map、Iterator、集合选择 |
| Day 12 | [集合框架下](./days/day-12/) | HashMap 原理、equals/hashCode、Comparable/Comparator |
| Day 13 | [IO 基础](./days/day-13/) | File、InputStream、OutputStream、Reader/Writer |
| Day 14 | [NIO 与序列化](./days/day-14/) | Path、Files、Charset、JSON 序列化概念 |
| Day 15 | [阶段一总结测验](./days/day-15/) | Java SE 基础回顾、限时代码题、错题整理 |

### 🔵 阶段二：现代 Java 与工程化（Day 16-30）

补齐 Lambda、Stream、Maven、测试、日志、SQL、JDBC 和 MyBatis。

| 天数 | 主题 | 关键词 |
|---|---|---|
| Day 16 | [Lambda 与函数式接口](./days/day-16/) | Lambda、函数式接口、方法引用、Predicate/Function |
| Day 17 | [Stream API](./days/day-17/) | map/filter/reduce/collect/groupingBy、流式聚合 |
| Day 18 | [Optional 与时间 API](./days/day-18/) | Optional、LocalDateTime、Duration、格式化 |
| Day 19 | [注解与反射](./days/day-19/) | Annotation、Class、Field、Method、运行时元数据 |
| Day 20 | [里程碑：Java 工具库](./days/day-20/) | 集合、IO、Stream、泛型、反射综合 |
| Day 21 | [Maven 工程化](./days/day-21/) | pom、依赖、生命周期、插件、多模块概念 |
| Day 22 | [单元测试](./days/day-22/) | JUnit 5、断言、参数化测试、测试命名 |
| Day 23 | [日志体系](./days/day-23/) | SLF4J、Logback、日志级别、日志格式 |
| Day 24 | [JDBC 入门](./days/day-24/) | Driver、Connection、PreparedStatement、连接释放 |
| Day 25 | [SQL 基础](./days/day-25/) | 表设计、CRUD、约束、索引入门 |
| Day 26 | [SQL 进阶](./days/day-26/) | JOIN、聚合、分页、事务、执行计划入门 |
| Day 27 | [数据库建模](./days/day-27/) | 一对多、多对多、ER 图、范式、字段设计 |
| Day 28 | [MyBatis 入门](./days/day-28/) | Mapper、XML/注解、动态 SQL、结果映射 |
| Day 29 | [MyBatis 进阶](./days/day-29/) | 分页、事务、N+1、批量操作、SQL 日志 |
| Day 30 | [里程碑：博客 DAO 层](./days/day-30/) | JDBC/MyBatis/SQL 整合、README、测试 |

### 🟡 阶段三：Spring Boot 后端开发（Day 31-40）

用 Spring Boot、MyBatis 和 OpenAPI 构建规范博客 API。

| 天数 | 主题 | 关键词 |
|---|---|---|
| Day 31 | [Spring 基础](./days/day-31/) | IoC、DI、Bean、配置方式、生命周期 |
| Day 32 | [Spring Boot 入门](./days/day-32/) | Starter、自动配置、Controller、Service |
| Day 33 | [REST API 设计](./days/day-33/) | 资源命名、状态码、DTO、统一响应、版本化 |
| Day 34 | [参数校验与异常处理](./days/day-34/) | Validation、全局异常处理、错误码设计 |
| Day 35 | [分层架构与事务](./days/day-35/) | Controller/Service/Repository、事务边界、业务异常 |
| Day 36 | [Spring Boot + MyBatis](./days/day-36/) | 数据源、Mapper 扫描、分页、事务整合 |
| Day 37 | [Spring Data JPA 可选线](./days/day-37/) | Entity、Repository、关联映射、MyBatis/JPA 对比 |
| Day 38 | [OpenAPI 文档](./days/day-38/) | springdoc、接口分组、请求示例、调试 |
| Day 39 | [配置与环境](./days/day-39/) | profile、配置注入、敏感信息、环境隔离 |
| Day 40 | [里程碑：博客 API v1](./days/day-40/) | Spring Boot + DB + 文档 + 测试 + GitHub |

### 🟠 阶段四：认证、安全、缓存与生产能力（Day 41-50）

实现 JWT、RBAC、Spring Security、Redis、文件上传和安全加固。

| 天数 | 主题 | 关键词 |
|---|---|---|
| Day 41 | [认证基础](./days/day-41/) | Session、JWT、密码哈希、登录流程设计 |
| Day 42 | [JWT 实战](./days/day-42/) | Access Token、Refresh Token、拦截器、续签 |
| Day 43 | [权限控制](./days/day-43/) | RBAC、角色、权限、资源鉴权、管理员接口 |
| Day 44 | [Spring Security 入门](./days/day-44/) | Filter Chain、Authentication、Authorization |
| Day 45 | [Web 安全防护](./days/day-45/) | XSS、CSRF、SQL 注入、限流、敏感信息保护 |
| Day 46 | [Redis 基础与缓存](./days/day-46/) | 数据结构、过期时间、Cache-Aside、缓存命名 |
| Day 47 | [Redis 进阶](./days/day-47/) | 缓存穿透/击穿/雪崩、分布式锁、排行榜 |
| Day 48 | [消息队列概念](./days/day-48/) | 异步任务、重试、死信、RabbitMQ/Kafka 对比 |
| Day 49 | [文件上传](./days/day-49/) | Multipart、文件校验、对象存储概念、访问 URL |
| Day 50 | [里程碑：博客 API v2](./days/day-50/) | 认证、权限、缓存、安全、文件上传整合 |

### 🔴 阶段五：并发、JVM、部署与面试冲刺（Day 51-60）

完成并发、JVM、Docker、测试、性能优化、系统设计和简历包装。

| 天数 | 主题 | 关键词 |
|---|---|---|
| Day 51 | [并发基础](./days/day-51/) | Thread、Runnable、Callable、线程状态、join |
| Day 52 | [线程池与异步](./days/day-52/) | Executor、ThreadPoolExecutor、CompletableFuture |
| Day 53 | [并发安全](./days/day-53/) | synchronized、Lock、Atomic、volatile、可见性 |
| Day 54 | [JVM 基础](./days/day-54/) | 内存区域、类加载、GC 概念、常见参数 |
| Day 55 | [里程碑：并发 + JVM 测验](./days/day-55/) | 高频面试题、代码实验、错题复盘 |
| Day 56 | [Docker 部署](./days/day-56/) | Dockerfile、Compose、MySQL/Redis 编排、健康检查 |
| Day 57 | [测试策略](./days/day-57/) | 单元测试、集成测试、MockMvc、测试数据 |
| Day 58 | [性能优化](./days/day-58/) | SQL 索引、慢查询、接口压测、日志排查 |
| Day 59 | [系统设计与面试](./days/day-59/) | 限流、缓存、排行榜、短链、登录系统设计 |
| Day 60 | [总结与进阶路线](./days/day-60/) | 项目包装、简历描述、JDK 25 新特性、后续路线 |

## 项目实战

| 项目 | 时间 | 目标 |
|---|---|---|
| CLI 学生管理系统 | Day 1-10 | 验证 Java 基础、OOP、异常、文件组织 |
| mini-utils 工具库 | Day 16-20 | 验证集合、IO、Stream、泛型、反射 |
| 博客数据层 | Day 24-30 | 验证 SQL、数据库建模、JDBC/MyBatis |
| 博客 API v1 | Day 31-40 | 验证 Spring Boot、REST、校验、文档、测试 |
| 博客 API v2 | Day 41-50 | 验证认证、权限、Redis、安全和上传 |
| 面试冲刺包 | Day 51-60 | 验证并发、JVM、部署、性能和系统设计 |

## 推荐默认技术栈

- Java：JDK 21 LTS
- 后端框架：Spring Boot
- 构建工具：Maven
- 数据库：MySQL 或 PostgreSQL
- ORM/SQL 映射：MyBatis 为主，JPA 作为对比了解
- 缓存：Redis
- 部署：Docker + Docker Compose
