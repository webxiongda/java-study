# Chapter 23 日志体系 - 项目任务

## 任务概述

给 `blog-api` 接入**结构化日志 + traceId + 分级文件归档**，为后续所有章节的可观测性奠基。

## 任务拆解

### Step 1：添加 `logback-spring.xml`

放到 `src/main/resources/logback-spring.xml`，要点：
- 控制台 + 文件双 appender
- 文件按日期 + 大小滚动，保留 30 天
- ERROR 单独写一份 `error.log`
- pattern 含 `%X{traceId:-}`
- 用 `<springProfile name="prod">` / `dev` 区分级别

### Step 2：写 `TraceIdFilter`

参考 `03-check.md` Q4。注册成 `@Component` + `@Order(1)`，header 名 `X-Trace-Id`。

### Step 3：写一个示例 Service 演示日志输出

```java
@Slf4j
@RestController
public class PingController {
    @GetMapping("/ping")
    public Map<String, String> ping() {
        log.info("ping called");
        log.debug("debug detail: {}", System.currentTimeMillis());
        return Map.of("pong", "ok");
    }
}
```

### Step 4：本地验证

```bash
mvn spring-boot:run
curl -i http://localhost:8080/ping            # 响应头有 X-Trace-Id
tail -f logs/app.log                          # 看到带 traceId 的行
curl -H "X-Trace-Id: my-custom-id" http://localhost:8080/ping
# 日志里 traceId=my-custom-id（沿用上游）
```

### Step 5：故意触发一次 ERROR

加一个 `/boom` 接口 `throw new RuntimeException("oops")`。访问后：
- `logs/error.log` 出现 stack trace
- `logs/app.log` 同时有该行（root 配置）

## 交付物

- [ ] `logback-spring.xml`（含 console、file、error_file 三个 appender）
- [ ] `TraceIdFilter.java`
- [ ] `PingController.java` + `/boom` 示例
- [ ] 截图：`logs/app.log` 和 `logs/error.log` 各一段
- [ ] 在 `docs/observability.md` 记录：traceId 流转路径、日志保留策略、生产改 DEBUG 的方法

## 验收清单

| 项 | 标准 |
|----|------|
| 输出格式 | 每行含 `level / traceId / logger / msg` |
| 同请求关联 | 一次请求所有日志 traceId 相同 |
| 文件滚动 | 跑压测产生 > 100MB 后自动滚动 + gzip 历史 |
| ERROR 分流 | 只有 ERROR 进 `error.log` |
| 异步透传 | `@Async` 方法里 traceId 依然存在（用 `TaskDecorator`） |

## 扩展挑战

1. **接 Loki / ELK**：用 logback-encoder 把日志编码成 JSON，方便机器抓取。
2. **动态调级**：开 `spring-boot-starter-actuator`，`POST /actuator/loggers/com.example` 临时改 DEBUG，排查完调回 INFO。
3. **敏感字段脱敏**：写一个 `MaskingPatternLayout`，自动把 `password=xxx`、`token=...` 替换为 `***`。
