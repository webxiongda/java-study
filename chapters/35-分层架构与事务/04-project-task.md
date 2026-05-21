# Chapter 35 分层架构与事务 - 项目任务

## 任务概述

为博客 API 补上事务正确性：`publish` 接口原子保证，审计日志独立提交，长事务拆分，集成测试验证回滚行为。

## 业务背景

33-34 章做了接口规范 + 校验，但 Service 层的事务边界还没认真设计。"发文章"涉及 3 张表的写入，"注册"涉及写 user + 发验证码，任何一步失败都需要精确回滚。这一章做"事务大扫除"。

## 任务拆解

### Step 1：梳理所有 Service 方法的事务边界

建 `docs/tx-matrix.md`，表格形式：

| 方法 | 涉及表 | 传播 | rollbackFor | 有无外部 IO |
|------|-------|------|------------|------------|
| PostService.publish | post, post_tag | REQUIRED | Exception | 否 |
| UserService.register | user | REQUIRED | Exception | 发验证码（要提到事务外）|
| AuditLogService.log | audit_log | REQUIRES_NEW | Exception | 否 |

### Step 2：修复长事务

找出 Service 中调用外部 IO（SMS / Mail / 文件上传）的方法，把 IO 移到事务外。

验证：用 P6Spy / SQL 日志看事务开始到提交之间没有 `Thread.sleep > 1s`。

### Step 3：self call 修复

全局 grep `self.xxx()` vs 类内直接调用，确保所有 `@Transactional` 方法都通过代理调用。

单测：故意在 `inner()` 里 throw → 验证 `outer()` 的事务是否正确回滚。

### Step 4：注册流程事务设计

```
register() = [INSERT user] + [INSERT email_verify_code]  → 主事务
           + [sendSms()]                                 → 事务外
```

单测：SMS 服务 mock 失败 → user 已提交（注册成功）+ SMS 失败报错（但不回滚）。

### Step 5：集成测试

写 `PostServiceIT.java`：

- `publish_rolls_back_on_bad_tag()`：传不存在的 tagId，post + tag 全部回滚。
- `publish_audit_log_survives_rollback()`：主事务 rollback，审计日志留下（验证 REQUIRES_NEW）。
- `concurrent_increment_post_count()`：10 个线程同时发文章，最终 post_count = 10（乐观锁 / 原子 UPDATE）。

### Step 6：慢事务日志

写一个 `@Aspect`，拦截 `@Transactional` 方法，记录事务从开到提交的时间，超 500ms 打 WARN。

## 交付物

- [ ] `docs/tx-matrix.md`：所有 Service 的事务矩阵
- [ ] 所有长事务已拆分（IO 移出）
- [ ] self call 全部通过代理
- [ ] `PostServiceIT.java`：≥ 5 个集成测试
- [ ] `TxTimingAspect.java`：慢事务日志切面
- [ ] 单测覆盖率 ≥ 70%（含新增 Service）

## 验收清单

| 项 | 标准 |
|----|------|
| 发文章原子 | post + tag 要么全有要么全无 |
| 审计日志独立 | 主事务 rollback，audit_log 记录仍存在 |
| 无长事务 | 事务内无 Thread.sleep / HTTP 调用 |
| self call 修复 | grep `this.` 后面跟 `@Transactional` 方法应为空 |
| 慢事务告警 | 超 500ms 出 WARN，单测可触发 |

## 扩展挑战

1. **消息发件箱（Outbox Pattern）**：发文章后不直接发 MQ，写一条 outbox 记录（同主事务），定时任务轮询发送，证明即使发送失败也最终能发出去。
2. **两阶段提交感受**：不实现，但写一篇 `docs/2pc-vs-saga.md`，对比 2PC / SAGA / 本地消息表的适用场景和取舍。
3. **并发更新 post_count**：对比 `UPDATE SET count = count + 1`（DB 原子）vs 先 SELECT 再 UPDATE（竞争条件），压测结果写到 `docs/concurrent-update.md`。
4. **事务超时**：配 `@Transactional(timeout = 5)`，用 `Thread.sleep(6000)` 在事务内验证超时回滚行为。
