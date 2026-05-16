import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const studyRoot = path.resolve(root, "..", "java-learning");

const phases = [
  {
    name: "阶段一：Java SE 核心基础",
    range: "Day 1-15",
    goal: "建立 Java 语法、面向对象、异常、集合和 IO 的基本编码能力。",
  },
  {
    name: "阶段二：现代 Java 与工程化",
    range: "Day 16-30",
    goal: "掌握 Lambda、Stream、Maven、测试、日志、SQL、JDBC 和 MyBatis。",
  },
  {
    name: "阶段三：Spring Boot 后端开发",
    range: "Day 31-40",
    goal: "能独立设计并实现规范的 Spring Boot REST API。",
  },
  {
    name: "阶段四：认证、安全、缓存与生产能力",
    range: "Day 41-50",
    goal: "补齐认证、权限、安全、Redis、异步任务和文件上传能力。",
  },
  {
    name: "阶段五：并发、JVM、部署与面试冲刺",
    range: "Day 51-60",
    goal: "完成并发、JVM、Docker、测试、性能优化、系统设计和项目包装。",
  },
];

const days = [
  ["01", "环境搭建与 Java 初印象", "JDK 21、IDEA、Maven、Git、首个 Java 程序", "开发环境就绪 + Hello Java", "L1 必须掌握", "3小时"],
  ["02", "Java 基础语法", "变量、基本类型、运算符、流程控制、输入输出", "命令行计算器", "L1 必须掌握", "3小时"],
  ["03", "方法与调试", "方法、参数、返回值、重载、断点调试", "小型工具函数库", "L1 必须掌握", "3小时"],
  ["04", "数组与字符串", "数组、String、StringBuilder、常见字符串处理", "文本统计工具", "L1 必须掌握", "3小时"],
  ["05", "面向对象入门", "类、对象、构造器、封装、this、static", "用户信息管理小练习", "L1 必须掌握", "3小时"],
  ["06", "继承与多态", "继承、抽象类、接口、重写、向上转型", "支付方式模拟器", "L1 必须掌握", "4小时"],
  ["07", "包、访问控制与常用类", "package、访问修饰符、Math、Objects、Date/Time", "日期工具类", "L1 必须掌握", "3小时"],
  ["08", "异常处理", "checked/unchecked、try/catch/finally、自定义异常", "统一异常练习", "L1 必须掌握", "3小时"],
  ["09", "枚举与泛型入门", "enum、泛型类、泛型方法、类型边界", "通用响应对象", "L2 项目常用", "3小时"],
  ["10", "里程碑：Java SE 小项目", "代码整理、README、Git 提交、基础测验", "CLI 学生管理系统", "L1 必须掌握", "4小时"],
  ["11", "集合框架上", "List、Set、Map、Iterator、集合选择", "商品购物车模型", "L1 必须掌握", "3小时"],
  ["12", "集合框架下", "HashMap 原理、equals/hashCode、Comparable/Comparator", "排行榜功能", "L3 面试高频", "4小时"],
  ["13", "IO 基础", "File、InputStream、OutputStream、Reader/Writer", "文件复制工具", "L1 必须掌握", "3小时"],
  ["14", "NIO 与序列化", "Path、Files、Charset、JSON 序列化概念", "本地数据存储", "L2 项目常用", "3小时"],
  ["15", "阶段一总结测验", "Java SE 基础回顾、限时代码题、错题整理", "阶段一学习笔记", "L1 必须掌握", "3小时"],
  ["16", "Lambda 与函数式接口", "Lambda、函数式接口、方法引用、Predicate/Function", "集合数据处理", "L1 必须掌握", "3小时"],
  ["17", "Stream API", "map/filter/reduce/collect/groupingBy、流式聚合", "订单统计练习", "L1 必须掌握", "4小时"],
  ["18", "Optional 与时间 API", "Optional、LocalDateTime、Duration、格式化", "日期统计工具", "L2 项目常用", "3小时"],
  ["19", "注解与反射", "Annotation、Class、Field、Method、运行时元数据", "简易对象映射器", "L3 面试高频", "4小时"],
  ["20", "里程碑：Java 工具库", "集合、IO、Stream、泛型、反射综合", "mini-utils 项目", "L1 必须掌握", "4小时"],
  ["21", "Maven 工程化", "pom、依赖、生命周期、插件、多模块概念", "Maven 多模块雏形", "L1 必须掌握", "3小时"],
  ["22", "单元测试", "JUnit 5、断言、参数化测试、测试命名", "工具库测试覆盖", "L1 必须掌握", "3小时"],
  ["23", "日志体系", "SLF4J、Logback、日志级别、日志格式", "标准日志配置", "L2 项目常用", "3小时"],
  ["24", "JDBC 入门", "Driver、Connection、PreparedStatement、连接释放", "原生 JDBC CRUD", "L1 必须掌握", "4小时"],
  ["25", "SQL 基础", "表设计、CRUD、约束、索引入门", "用户/文章表", "L1 必须掌握", "3小时"],
  ["26", "SQL 进阶", "JOIN、聚合、分页、事务、执行计划入门", "10 道 SQL 练习", "L3 面试高频", "4小时"],
  ["27", "数据库建模", "一对多、多对多、ER 图、范式、字段设计", "博客系统 ER 图", "L2 项目常用", "3小时"],
  ["28", "MyBatis 入门", "Mapper、XML/注解、动态 SQL、结果映射", "博客 CRUD", "L1 必须掌握", "4小时"],
  ["29", "MyBatis 进阶", "分页、事务、N+1、批量操作、SQL 日志", "完整查询接口", "L3 面试高频", "4小时"],
  ["30", "里程碑：博客 DAO 层", "JDBC/MyBatis/SQL 整合、README、测试", "博客数据层 v1", "L1 必须掌握", "4小时"],
  ["31", "Spring 基础", "IoC、DI、Bean、配置方式、生命周期", "Spring Hello World", "L1 必须掌握", "4小时"],
  ["32", "Spring Boot 入门", "Starter、自动配置、Controller、Service", "第一个 REST API", "L1 必须掌握", "4小时"],
  ["33", "REST API 设计", "资源命名、状态码、DTO、统一响应、版本化", "博客 API 规范", "L3 面试高频", "3小时"],
  ["34", "参数校验与异常处理", "Validation、全局异常处理、错误码设计", "标准错误响应", "L1 必须掌握", "4小时"],
  ["35", "分层架构与事务", "Controller/Service/Repository、事务边界、业务异常", "博客 API 重构", "L1 必须掌握", "4小时"],
  ["36", "Spring Boot + MyBatis", "数据源、Mapper 扫描、分页、事务整合", "博客后端 CRUD", "L1 必须掌握", "4小时"],
  ["37", "Spring Data JPA 可选线", "Entity、Repository、关联映射、MyBatis/JPA 对比", "ORM 对比笔记", "L2 项目常用", "3小时"],
  ["38", "OpenAPI 文档", "springdoc、接口分组、请求示例、调试", "Swagger 文档", "L2 项目常用", "3小时"],
  ["39", "配置与环境", "profile、配置注入、敏感信息、环境隔离", "dev/prod 配置", "L2 项目常用", "3小时"],
  ["40", "里程碑：博客 API v1", "Spring Boot + DB + 文档 + 测试 + GitHub", "可演示后端项目", "L1 必须掌握", "4小时"],
  ["41", "认证基础", "Session、JWT、密码哈希、登录流程设计", "注册登录设计", "L3 面试高频", "3小时"],
  ["42", "JWT 实战", "Access Token、Refresh Token、拦截器、续签", "登录注册接口", "L1 必须掌握", "4小时"],
  ["43", "权限控制", "RBAC、角色、权限、资源鉴权、管理员接口", "管理员权限", "L2 项目常用", "4小时"],
  ["44", "Spring Security 入门", "Filter Chain、Authentication、Authorization", "Security 接入", "L1 必须掌握", "4小时"],
  ["45", "Web 安全防护", "XSS、CSRF、SQL 注入、限流、敏感信息保护", "安全检查清单", "L3 面试高频", "3小时"],
  ["46", "Redis 基础与缓存", "数据结构、过期时间、Cache-Aside、缓存命名", "热门文章缓存", "L1 必须掌握", "4小时"],
  ["47", "Redis 进阶", "缓存穿透/击穿/雪崩、分布式锁、排行榜", "缓存保护方案", "L3 面试高频", "4小时"],
  ["48", "消息队列概念", "异步任务、重试、死信、RabbitMQ/Kafka 对比", "评论通知设计", "L2 项目常用", "3小时"],
  ["49", "文件上传", "Multipart、文件校验、对象存储概念、访问 URL", "头像/封面上传", "L2 项目常用", "3小时"],
  ["50", "里程碑：博客 API v2", "认证、权限、缓存、安全、文件上传整合", "简历级后端项目", "L1 必须掌握", "4小时"],
  ["51", "并发基础", "Thread、Runnable、Callable、线程状态、join", "多线程下载模拟", "L3 面试高频", "4小时"],
  ["52", "线程池与异步", "Executor、ThreadPoolExecutor、CompletableFuture", "异步任务执行器", "L3 面试高频", "4小时"],
  ["53", "并发安全", "synchronized、Lock、Atomic、volatile、可见性", "并发计数器实验", "L3 面试高频", "4小时"],
  ["54", "JVM 基础", "内存区域、类加载、GC 概念、常见参数", "JVM 笔记", "L3 面试高频", "4小时"],
  ["55", "里程碑：并发 + JVM 测验", "高频面试题、代码实验、错题复盘", "面试题答案集", "L1 必须掌握", "4小时"],
  ["56", "Docker 部署", "Dockerfile、Compose、MySQL/Redis 编排、健康检查", "一键启动项目", "L2 项目常用", "4小时"],
  ["57", "测试策略", "单元测试、集成测试、MockMvc、测试数据", "核心接口测试", "L2 项目常用", "4小时"],
  ["58", "性能优化", "SQL 索引、慢查询、接口压测、日志排查", "优化报告", "L3 面试高频", "4小时"],
  ["59", "系统设计与面试", "限流、缓存、排行榜、短链、登录系统设计", "3 道设计题笔记", "L3 面试高频", "4小时"],
  ["60", "总结与进阶路线", "项目包装、简历描述、JDK 25 新特性、后续路线", "结业总结 + 简历项目说明", "L1 必须掌握", "3小时"],
];

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function write(file, content) {
  ensureDir(path.dirname(file));
  fs.writeFileSync(file, `${content.trimEnd()}\n`, "utf8");
}

function dayLink(n) {
  return `./days/day-${n}/`;
}

function prevNext(day) {
  const n = Number(day);
  const prev = n > 1 ? `[⬅️ Day ${String(n - 1).padStart(2, "0")}](../day-${String(n - 1).padStart(2, "0")}/)` : "";
  const next = n < 60 ? `[➡️ Day ${String(n + 1).padStart(2, "0")}](../day-${String(n + 1).padStart(2, "0")}/)` : "";
  return [prev, next].filter(Boolean).join(" | ");
}

function milestoneNote(day) {
  const milestones = {
    "10": "今天重点不是继续堆功能，而是把前 9 天的代码整理成一个可以展示、可以运行、可以复盘的完整小项目。",
    "20": "今天要把现代 Java 语法和工具型代码组织能力收束成一个小型工具库，为后续工程化做准备。",
    "30": "今天完成博客系统的数据访问层，重点验证 SQL、建模、事务和 MyBatis 的项目化能力。",
    "40": "今天交付博客 API v1，要求接口可运行、文档可查看、异常和校验一致。",
    "50": "今天交付简历级博客 API v2，重点是认证、权限、Redis、安全和文件上传整合。",
    "55": "今天用面试题和代码实验收束并发与 JVM，不追求大而全，追求讲得清、写得出、能验证。",
    "60": "今天完成 60 天总结，把项目、知识体系、面试表达和后续路线整理成可持续推进的资产。",
  };
  return milestones[day] ?? "今天要完成理论学习、编码练习、笔记复盘和一次 Git 提交，保持可追踪的学习闭环。";
}

function exercisesFor(day, title, output) {
  if (day === "10") {
    return [
      "整理 `student-cli/` 项目结构，至少包含 `model`、`service`、`repository`、`app` 四类职责。",
      "实现学生新增、列表、搜索、更新、删除、按分数排序、文件持久化。",
      "补充 README：功能、运行方式、核心类说明、学到什么、后续改进。",
      "写 10 道阶段复盘题，覆盖 OOP、异常、泛型、集合选择和文件组织。",
    ];
  }
  if (day === "40") {
    return [
      "完成博客文章、分类、评论的核心 REST API。",
      "接入参数校验、统一异常、统一响应和 OpenAPI 文档。",
      "至少为 3 个核心接口补充单元或集成测试。",
      "整理 README 和接口截图，形成可演示版本。",
    ];
  }
  if (day === "50") {
    return [
      "为博客 API 接入注册、登录、JWT、角色权限和管理员接口。",
      "为文章详情、热门文章等场景接入 Redis 缓存。",
      "完成基础安全检查：密码哈希、输入校验、权限边界、敏感信息不出响应。",
      "补充项目亮点描述，准备简历项目版本。",
    ];
  }
  return [
    `围绕“${title}”完成 1 个可运行 Demo。`,
    `把 Demo 重构到清晰的类、方法或模块边界中。`,
    `在笔记中解释核心概念、常见坑和你自己的理解。`,
    `提交代码，commit 信息包含 Day ${day} 和今日主题。`,
  ];
}

function questionsFor(day) {
  const common = [
    "今天最核心的 3 个概念是什么？",
    "哪个知识点最容易在面试或项目中出错？",
    "今天的代码还能如何重构得更清晰？",
  ];
  const special = {
    "12": ["为什么重写 `equals` 后通常也必须重写 `hashCode`？", "HashMap 的 key 为什么要求稳定？", "ArrayList 和 LinkedList 的典型取舍是什么？"],
    "19": ["反射为什么会破坏一部分编译期安全？", "注解本身保存了什么信息？", "框架为什么大量使用反射和注解？"],
    "26": ["INNER JOIN 和 LEFT JOIN 的结果差异是什么？", "索引为什么可能失效？", "事务 ACID 分别解决什么问题？"],
    "34": ["参数校验应该放在哪一层？", "业务异常和系统异常如何区分？", "错误码设计要避免什么问题？"],
    "44": ["Spring Security 的过滤器链解决什么问题？", "认证和授权的区别是什么？", "为什么权限判断不能只放前端？"],
    "47": ["缓存穿透、击穿、雪崩分别是什么？", "分布式锁适合解决什么问题？", "Redis key 命名为什么要规范？"],
    "53": ["`volatile` 能保证原子性吗？", "`synchronized` 和 `Lock` 的常见区别是什么？", "线程安全问题如何复现和验证？"],
    "54": ["JVM 内存区域如何划分？", "类加载过程有哪些阶段？", "GC 日志能帮助定位什么问题？"],
  };
  return special[day] ?? common;
}

function renderDay(day) {
  const [n, title, topics, output, priority, hours] = day;
  const exercises = exercisesFor(n, title, output);
  const questions = questionsFor(n);
  return `# Day ${n} — ${title}

## 📋 今日目标

- 掌握：${topics}
- 完成：${output}
- 优先级：${priority}
- 预计投入：${hours}

## 📖 核心知识点

今天围绕 **${title}** 建立可编码、可复述、可面试的理解。学习时不要只记 API 名称，要把“为什么这样设计、什么时候使用、常见错误是什么”写进笔记。

建议学习顺序：

1. 阅读官方文档或权威教程，先建立概念框架。
2. 跟写一个最小 Demo，确认环境和基础 API 可用。
3. 独立扩展 1-2 个小功能，避免只停留在复制代码。
4. 用自己的话整理概念、代码结构和易错点。

## 💻 实践练习

${exercises.map((item, index) => `${index + 1}. ${item}`).join("\n")}

## ✅ 今日产出

- [ ] 完成核心知识点学习
- [ ] 完成 Demo 或项目任务：${output}
- [ ] 整理学习笔记和易错点
- [ ] 完成今日复盘题
- [ ] 提交 Git commit

## 🧠 复盘问题

${questions.map((item, index) => `${index + 1}. ${item}`).join("\n")}

## 🏁 通过标准

${milestoneNote(n)}

---

${prevNext(n)}
`;
}

function renderReadme() {
  const phaseSections = [
    ["🟢", "阶段一：Java SE 核心基础（Day 1-15）", "从环境、语法、OOP、异常、泛型、集合和 IO 打稳 Java 编码基础。"],
    ["🔵", "阶段二：现代 Java 与工程化（Day 16-30）", "补齐 Lambda、Stream、Maven、测试、日志、SQL、JDBC 和 MyBatis。"],
    ["🟡", "阶段三：Spring Boot 后端开发（Day 31-40）", "用 Spring Boot、MyBatis 和 OpenAPI 构建规范博客 API。"],
    ["🟠", "阶段四：认证、安全、缓存与生产能力（Day 41-50）", "实现 JWT、RBAC、Spring Security、Redis、文件上传和安全加固。"],
    ["🔴", "阶段五：并发、JVM、部署与面试冲刺（Day 51-60）", "完成并发、JVM、Docker、测试、性能优化、系统设计和简历包装。"],
  ];
  const tables = phaseSections.map(([icon, heading, desc], idx) => {
    const start = [1, 16, 31, 41, 51][idx];
    const end = [15, 30, 40, 50, 60][idx];
    const rows = days.slice(start - 1, end).map(([n, title, topics]) => `| Day ${n} | [${title}](${dayLink(n)}) | ${topics} |`).join("\n");
    return `### ${icon} ${heading}

${desc}

| 天数 | 主题 | 关键词 |
|---|---|---|
${rows}`;
  }).join("\n\n");

  return `<p align="center">
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

这个目录参考 \`node-study\` 的组织方式，提供一套 Java 后端 60 天学习路线。它不是只列知识点，而是把每天的学习目标、实践产出、里程碑项目、进度追踪和复习机制放在一起。

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

\`\`\`bash
cd 60-days-java
cd days/day-01
# 阅读 README.md 开始学习
\`\`\`

### 学习方式

1. 按天推进，不跳过里程碑。
2. 每天必须写代码、写笔记、提交 Git commit。
3. 每 3 天、7 天、30 天按 \`../java-learning/review-plan.md\` 做复习。
4. 每个里程碑项目都要有 README、运行方式、核心功能和总结。

## 课程大纲

${tables}

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
`;
}

function renderRoadmap() {
  const phaseText = phases.map((phase) => `- ${phase.range}：${phase.name}。${phase.goal}`).join("\n");
  const rows = days.map(([n, title, topics, output, priority, hours]) => `| ${n} | ${title} | ${topics} | ${output} | ${priority} | ${hours} |`).join("\n");
  return `# 🗺️ 60 天学会 Java 后端开发 — 完整路线图

> 每天 3-4 小时，60 天建立 Java 后端就业面试所需的核心能力。

## 总览

${phaseText}

## 每日路线图

| Day | 主题 | 核心知识点 | 产出物 | 优先级 | 预计课时 |
|---|---|---|---|---|---|
${rows}

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
`;
}

function renderLearningGoal() {
  return `# 学习目标

## 学习动机

面试导向：目标是通过 Java 后端岗位面试，并能独立交付 Spring Boot REST API。

## 当前基础

非零基础：有编程经验，但 Java 学习不系统。

## 可投入时间

每天约 3-4 小时。

## 验收标准

1. 能独立编写 REST API（含认证、数据库、缓存、接口文档和基础测试）。
2. 能讲清 Java 核心、集合、并发、JVM、Spring Boot、数据库、Redis 高频面试题。
3. 能拿出一个可运行、可部署、可写进简历的博客 API 项目。

## 推荐重点

### 必须深度掌握

- Java OOP、异常、泛型、集合、IO、Stream
- HashMap、equals/hashCode、集合选择
- SQL、事务、索引、数据库建模
- Maven、JUnit、日志和分层架构
- Spring IoC/DI、Spring Boot、REST API、全局异常、参数校验
- JWT、RBAC、Spring Security、Redis
- Java 并发、线程池、锁、JVM 基础

### 项目实战

- CLI 学生管理系统（Day 1-10）
- mini-utils 工具库（Day 16-20）
- 博客数据层（Day 24-30）
- 博客 API v1/v2（Day 31-50）
- 面试冲刺包和部署版本（Day 51-60）

### 可适当快过

- IDE 安装细节
- JPA 深入源码
- 消息队列生产级集群
- 微服务全家桶
- JDK 25 新特性深挖
`;
}

function renderProgress() {
  const rows = days.map(([n, title, , , priority]) => `| ${n} | ${title} | ${priority.split(" ")[0]} | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | - |`).join("\n");
  return `# Java 学习进度

## 当前状态

- 当前章节：01-环境搭建与 Java 初印象
- 开始日期：-
- 完成章节数：0 / 60

## 章节进度

| # | 章节 | 优先级 | 理论 | Demo | 验收 | 项目任务 | 复习 | 完成日期 |
|---|---|---|---|---|---|---|---|---|
${rows}
`;
}

function renderReviewPlan() {
  const rows = days.map(([n, title]) => `| ${n} ${title} | - | - | - | - | ⬜ 未开始 |`).join("\n");
  return `# 复习计划

复习节奏参考间隔重复：首次复习 +3 天，二次复习 +7 天，三次复习 +30 天。每次复习只做三件事：重跑代码、回答复盘题、更新错题。

| 章节 | 完成日期 | 首次复习（+3天） | 二次复习（+7天） | 三次复习（+30天） | 状态 |
|---|---|---|---|---|---|
${rows}
`;
}

function renderLearningReadme() {
  const rows = days.map(([n, title, topics, , priority, hours]) => `| ${n} | ${title} | ${topics} | ${priority} | ${hours} | ⬜ 未开始 |`).join("\n");
  return `# Java 后端学习路线（面试向）

> 目标：通过 Java 后端岗位面试 + 能独立编写 Spring Boot REST API  
> 基础：非零基础，不系统  
> 节奏：每天 3-4 小时

## 章节列表

| # | 章节 | 核心内容 | 优先级 | 预计课时 | 状态 |
|---|---|---|---|---|---|
${rows}

## 优先级说明

- **L1 必须掌握**：面试 + 项目都要，不能跳过
- **L2 项目常用**：工作中经常用，建议掌握
- **L3 面试高频**：面试容易问，要重点准备
- **L4 进阶拓展**：有余力时学，可暂缓
`;
}

write(path.join(root, "README.md"), renderReadme());
write(path.join(root, "ROADMAP.md"), renderRoadmap());
write(path.join(studyRoot, "learning-goal.md"), renderLearningGoal());
write(path.join(studyRoot, "README.md"), renderLearningReadme());
write(path.join(studyRoot, "progress.md"), renderProgress());
write(path.join(studyRoot, "review-plan.md"), renderReviewPlan());
write(path.join(studyRoot, "mistakes.md"), "# 错题与易错点\n\n| 日期 | 章节 | 问题 | 正确理解 | 下次复习 |\n|---|---|---|---|---|\n");

for (const day of days) {
  const [n] = day;
  write(path.join(root, "days", `day-${n}`, "README.md"), renderDay(day));
}

console.log(`Generated ${days.length} day files and study tracking documents.`);
