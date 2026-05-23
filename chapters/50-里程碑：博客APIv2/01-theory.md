# Chapter 50 里程碑：博客APIv2 - 理论篇

## 一、学习定位

这是 **第二个里程碑**, 把 41-49 章 (认证 / Security / Web 安全 / Redis / MQ / 文件上传) 全部整合到博客 API。

- v1 (Ch40): 只有 CRUD + 基础参数校验
- **v2 (本章)**: 认证 + 权限 + 缓存 + 安全 + 文件上传 + 异步任务

输出: 一个 **简历级的博客 API 项目**, 不是玩具 demo。

- 优先级: L1 必须完成
- 投入: 8 小时 (整合 + 联调 + 文档)
- 产出: 可在简历里写 "独立交付完整 Spring Boot 博客后端"

## 二、整合的能力清单

| 来源 | 能力 | v2 落地位置 |
|---|---|---|
| Ch41 | 注册登录 | `/auth/register` `/auth/login` |
| Ch42 | JWT | 全局 JwtAuthenticationFilter |
| Ch43 | RBAC | `@PreAuthorize` 注解 |
| Ch44 | Spring Security 6 | SecurityConfig |
| Ch45 | Web 安全 | 安全响应头 / Jsoup 净化 / 限流 |
| Ch46 | Redis 缓存 | 文章详情 Cache-Aside |
| Ch46 | Redis ZSET | 热文章排行榜 |
| Ch47 | Redisson | 评论点赞防并发 |
| Ch47 | 多级缓存 | 文章详情 Caffeine+Redis |
| Ch48 | RabbitMQ | 评论通知 / 异步统计 |
| Ch49 | 文件上传 | 头像 + 文章插图 + MinIO |

## 三、v2 系统架构

```
                     ┌──── nginx ────┐
                     │ TLS / 限流 / CDN│
                     └────┬──────────┘
                          │
                   ┌──────▼──────┐
                   │ Spring Boot │
   ┌──────────┐    │   API       │   ┌────────┐
   │ JWT 过滤 ├───►│  + Security ├──►│ MySQL  │
   └──────────┘    │  + 限流     │   └────────┘
                   │  + 缓存     │   ┌────────┐
                   │  + MQ 发送  ├──►│ Redis  │
                   └──────┬──────┘   └────────┘
                          │          ┌────────┐
                          ├─────────►│RabbitMQ│
                          │          └───┬────┘
                          │          ┌───▼────┐
                          │          │Consumer│ (邮件/统计)
                          │          └────────┘
                          │          ┌────────┐
                          └─────────►│ MinIO  │ (头像/图片)
                                     └────────┘
```

## 四、模块拆分

```
backend/
├── auth/         (Ch41-43) AuthController / JwtService / UserDetailsService
├── post/         (Ch40) PostController / PostService / PostMapper
├── comment/      新增 CommentController / CommentService
├── upload/       (Ch49) UploadController / StorageService
├── cache/        (Ch46-47) CacheConfig / PostCacheService
├── security/     (Ch44-45) SecurityConfig / RateLimitFilter / Sanitizer
├── mq/           (Ch48) RabbitConfig / NotifyConsumer
├── common/       Result / GlobalExceptionHandler / BizException
└── config/       OpenAPI / Jackson / WebMvc
```

## 五、对外接口清单

| Endpoint | 描述 | 权限 |
|---|---|---|
| POST /auth/register | 注册 | 公开 |
| POST /auth/login | 登录返回 JWT | 公开 |
| POST /upload/avatar | 头像上传 | 登录 |
| POST /upload/image | 文章插图 | 登录 |
| GET /posts | 文章列表 (分页, 缓存) | 公开 |
| GET /posts/{id} | 文章详情 (多级缓存) | 公开 |
| POST /posts | 发布 | 登录 + Jsoup 净化 |
| PUT /posts/{id} | 编辑 | 作者本人 / ADMIN |
| DELETE /posts/{id} | 删除 | 作者本人 / ADMIN |
| POST /posts/{id}/comments | 评论 | 登录, 异步通知 |
| GET /posts/hot | 热门排行 | 公开 (ZSET) |
| GET /actuator/health | 健康检查 | 公开 |

## 六、关键非功能要求

| 维度 | 标准 |
|---|---|
| 安全 | 所有写接口必须认证; 富文本 Jsoup 净化; 全局响应头 (CSP/HSTS/X-Frame); 登录限流 5/min |
| 性能 | 文章详情 P99 < 50ms (缓存命中); 列表 P99 < 200ms |
| 可观测 | 日志结构化 (JSON), TraceId 贯穿; Prometheus 暴露 /actuator/prometheus |
| 可用性 | Redis 宕机降级到 DB; MQ 宕机 outbox 兜底 |
| 测试 | 集成测试覆盖核心路径; ≥ 30 个测试 |
| 文档 | OpenAPI (Swagger) + README + 部署文档 |

## 七、常见坑 (整合期)

| 坑 | 后果 | 处理 |
|---|---|---|
| Security 配置吃掉 OPTIONS 预检 | 前端 CORS 失败 | 显式 `cors().and()` + WebMvc CORS |
| @PreAuthorize 没生效 | 任何人可调管理接口 | `@EnableMethodSecurity` 没加 |
| 缓存和事务顺序 | 事务未提交先清缓存, 别人又读 | 改成 `@TransactionalEventListener(AFTER_COMMIT)` |
| MQ 消费方启动慢于生产方 | 消息丢失 | 队列必须持久化 + 用 outbox |
| 上传文件后, 缓存里仍是旧 URL | 用户改头像不生效 | 用户表更新时 evict 用户缓存 |
| 多 Module 循环依赖 | 启动失败 | 限制依赖方向: controller → service → mapper, 跨模块通过 event |

## 八、面试讲法 (3 分钟)

"我做了一个 Spring Boot 博客后端, 完整集成:
- 认证用 JWT + Spring Security 6, RBAC 用 @PreAuthorize
- Web 安全做了 XSS (Jsoup)、CSRF (无状态 JWT 不需要)、SQL (MyBatis #{}) 三类防护, 加全局安全响应头和 Bucket4j 限流
- 文章详情走 Caffeine + Redis 二级缓存, P99 50ms; 热门用 Redis ZSET
- 评论通知用 RabbitMQ 异步, outbox 表保证最终一致
- 头像/插图上传走 MinIO, Tika 校验真实类型, sha 命名去重
- 全链路日志 + Prometheus + Docker Compose 一键起
代码 30+ 个集成测试, P99 / 错误率 / QPS 都跑过基准。"

## 九、自检表

- [ ] 41-49 章每章的核心能力都落到了博客项目
- [ ] 没有死循环依赖, 模块清晰
- [ ] 安全清单 (Ch45) 全过
- [ ] 缓存 / MQ / 上传 都有失败降级
- [ ] README 有架构图 + 启动命令 + 接口列表 + 关键设计决策
- [ ] 简历可写一句话技术亮点
