# Chapter 60 总结与进阶路线 - 项目任务

## 任务概述

**60 章收官**: 把博客项目 + 60 章笔记包装成 **可投递的求职资产**:

1. 博客项目 README + 一键启动 + 文档完整
2. 简历草稿 (含项目段落 + 技能矩阵)
3. STAR 答辩稿 + 5 道系统设计准备
4. 60 章自评 + 3 个月进阶路线
5. 至少 1 篇结业总结文章 (掘金 / 自己博客)
6. GitHub 仓库整理 + 打 tag v1.0

## 业务背景

学完 60 章只是开始, **能讲清** + **有数据** + **能被搜到** 才是真正的产出。
否则学了等于没学, 别人看你 GitHub 还是空的。

## 任务拆解

### Step 1: 博客项目最终整理 (60 分钟)

**(a) README.md 完整化**

参考 02-demo.md 的 Demo 1 写一个面向面试官的 README:
- 一句话 + 一张架构图
- 技术栈表格
- 核心能力 (3-5 bullet)
- 快速开始 (3 行命令)
- 文档导航 + 在线 demo URL

**(b) docs/ 目录组织**

```
docs/
├── architecture.md       架构图 + 模块职责 + 数据流
├── PERFORMANCE.md        Ch58 输出的性能报告
├── jvm-tuning.md         Ch54 输出的 JVM 调优记录
├── api-spec.md           API 定义 (或链接到 Swagger)
├── deployment.md         部署 + 运维 SOP
├── INCIDENT-SOP.md       Ch54 输出的故障应急
└── system-design/        Ch59 的 5 道设计稿
    ├── 01-tinyurl.md
    ├── 02-seckill.md
    ├── 03-feed.md
    ├── 04-leaderboard.md
    └── 05-login.md
```

**(c) 仓库整理**

- 删除 `target/` `.DS_Store` `*.iml` 等无用文件 (改 .gitignore)
- 检查 .env / 密钥 / token 没提交
- LICENSE 加 MIT
- 打 tag `v1.0`

### Step 2: 简历项目段落 (60 分钟)

`docs/RESUME.md`:

```markdown
# 项目: 博客 API (Spring Boot 3 + Java 21)
2026-01 ~ 2026-05 | 个人项目 | GitHub: <url>

## 项目简介
独立开发的中型博客后端, 覆盖认证 / 缓存 / 异步 / 文件 / 监控 / 部署
全链路, QPS 峰值 9200, P99 < 5ms, 测试覆盖率 65%。

## 技术栈
Java 21, Spring Boot 3.3, Spring Security, JWT, MySQL 8.4, Redis 7.4,
RabbitMQ, MinIO, Caffeine, Docker, GitHub Actions, Prometheus, Grafana

## 技术亮点 (5 条, 见 Demo 2)
1. 双层缓存 + N+1 优化, 详情接口 P99 612ms → 3ms (200x)
2. 线程池隔离 + 虚拟线程, P99 抖动 ±300ms → ±20ms
3. JVM + GC 调优, Full GC 每天 5 次 → 0
4. 完整测试体系 (单元 + 切片 + Testcontainers), 覆盖率 65%
5. Docker + CI/CD, 部署时间 20 分钟 → 90 秒

## 我的角色
独立完成需求拆解 / 架构设计 / 编码 / 测试 / 部署 / 文档化全流程。
```

**简历整体结构** (1 页):

```
姓名 | 学历 | 联系方式 | GitHub | 博客
----
求职意向 (1 句, 如 "Java 后端 P6 / 应届")
----
技能矩阵 (按熟练度分类, 不是堆术语)
- 精通: Java 21, Spring Boot 3, MySQL, Redis
- 熟悉: RabbitMQ, Docker, k8s 基础, Linux
- 了解: Kafka, ES, Spring Cloud
----
项目经验 1: 博客 API (上面的 5 条亮点)
项目经验 2: (你之前的另一个项目)
----
教育经历
工作经历 (如有)
----
其他: 开源贡献 / 技术文章 / 获奖
```

### Step 3: 写结业总结文章 (90 分钟)

发到 **掘金 / segmentfault / 自己博客**:

**题目示例**:
- "我花 4 个月学完 60 章 Java 后端: 详细路径 + 踩坑 + 思考"
- "从 0 到 1 做一个 Spring Boot 博客 API: 完整复盘"
- "Java 后端工程师自学路径 (60 章学完)"

**结构** (3000 字左右):

```
1. 我是谁 + 背景 + 为什么学 (200 字)
2. 60 章主线分布 + 每个模块的核心收获 (1000 字)
3. 项目: 博客 API 关键决策 + 数字 (1000 字)
4. 踩过的坑 (3-5 个具体的, 不是"挺难的") (500 字)
5. 接下来的计划 (300 字)
6. 资源附录: 开源仓库 + 完整 60 章列表
```

**关键**: 不要写"小白也能学会", 要写**有判断 / 有数字 / 有反思**。 否则被淹没。

### Step 4: 5 道系统设计快速复习 (45 分钟)

参考 Ch59:
- 短链 / 秒杀 / Feed / 排行榜 / 登录

每题做一次 90 秒电梯讲法演练, 录音回听。

### Step 5: 60 章自评 + 3 个月路线 (30 分钟)

`docs/SELF-ASSESS.md` 参考 02-demo.md Demo 4, 按真实情况打分 (不全是 ✅✅✅):

```
模块 → 掌握度 (✅/✅✅/✅✅✅) → 备注 (能讲什么 / 不能讲什么)
```

`docs/NEXT-3-MONTHS.md`:

```markdown
# 接下来 3 个月

## 6 月: 分布式深化
- [ ] 把博客拆 2 个微服务, Spring Cloud Gateway + Nacos
- [ ] 加 Seata 解决跨服务事务
- [ ] 写 1 篇文 "我的微服务拆分实践"

## 7 月: 大数据 + k8s
- [ ] 用 Kafka + Flink 出实时 PV / UV
- [ ] 把博客部署到 k8s (minikube), 写 Helm Chart
- [ ] 写 1 篇文 "k8s 入门: 从 Docker Compose 到 Helm"

## 8 月: 求职冲刺
- [ ] 简历 review (找 3 个朋友 / 内推人)
- [ ] 模拟面试 5 次 (Pramp / 朋友)
- [ ] 投递 10-30 家
- [ ] 算法刷题维持 (每周 5 道)
```

### Step 6: GitHub 仓库光环 (15 分钟)

- README 加 badge (build / coverage / license / star)
- 写 CONTRIBUTING.md
- 创建第一个 issue (next-step roadmap)
- 求几个朋友 star 起步 (从 0 → 5 起跑)

## 交付物

- [ ] `README.md` 面向面试官的项目介绍
- [ ] `docs/architecture.md` `PERFORMANCE.md` `jvm-tuning.md` `INCIDENT-SOP.md`
- [ ] `docs/system-design/` 5 道设计稿
- [ ] `docs/RESUME.md` 简历项目段落 + 完整简历草稿
- [ ] `docs/INTERVIEW-STAR.md` 90 秒答辩稿
- [ ] `docs/SELF-ASSESS.md` + `docs/NEXT-3-MONTHS.md`
- [ ] 1 篇公开博客文章 URL
- [ ] git tag `v1.0` + push
- [ ] GitHub 仓库整理完整
- [ ] git commit: `ch60: graduation - project packaging + resume + next steps`

## 验收清单

| 验收项 | 标准 |
|---|---|
| README 30 秒能看懂 | 朋友看完能复述项目做什么 |
| 简历亮点带数字 | 5 条全部有 "P99 X → Y" / "QPS 增 N x" 之类数字 |
| 答辩 90 秒讲完 | 录音回听不卡壳 |
| 自评诚实 | 不能全是 ✅✅✅, 至少 5 处标 ✅ 或 "需复习" |
| 进阶路线具体 | 不是"努力学习 Spring Cloud", 而是"6 月拆 2 个微服务" |
| 公开输出 | 至少 1 篇文章 + GitHub 仓库可见 |
| GitHub 没敏感信息 | 检查 .env / token / 私密 URL 都没提交 |

## 扩展挑战

1. **side project 2.0**: 在博客基础上加 1 个有意思的功能 (AI 总结 / 智能搜索 / 评论情感分析)
2. **开源贡献**: 给 Spring Boot / Redisson / 任何用过的库提 1 个 PR (typo 也行, 走流程)
3. **个人技术品牌**: 注册掘金 / segmentfault / 自己博客, 1 个月写 1 篇
4. **社群**: 加入 1 个 Java 技术社群, 主动回答 5 个问题
5. **mentor**: 找一个比你高 2-3 级的 mentor (公司同事 / Twitter / Discord)
6. **目标公司清单**: 列 10-20 家公司 + 内推渠道 + 时间表

---

## 写在最后

> 学完 60 章只是入场券。 真正决定你高度的不是知识储备, 而是 **能不能持续把学到的
> 东西落到项目里 + 用文字 / 代码 / 演讲分享出来**。
>
> 别把简历写成 "学过 X / 了解 Y", 写成 "我用 X 解决了 Y 问题, 收益是 Z"。
>
> 别把 GitHub 当代码备份, 当作 "陌生人 30 秒决定要不要约你面试" 的橱窗。
>
> 别把面试当考试, 当作 "讲一个你自己最骄傲的故事" — 故事够好, 自然有人想听。
>
> 祝顺利!
