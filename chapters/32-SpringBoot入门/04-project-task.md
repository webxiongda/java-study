# Chapter 32 Spring Boot 入门 - 项目任务

## 任务概述

用 Spring Boot 3.3.5 把 30 章的 `blog-dao` 模块接上 Web 层，跑出第一个可 curl 的 REST API，并把 Actuator 接起来让运维能探活。

## 业务背景

DAO 层已经就绪，缺的是 HTTP 入口。这一章重点是「把 Boot 的自动配置用起来，少写配置多写业务」。

## 任务拆解

### Step 1：引入 Boot Parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### Step 2：主类 + application.yml

主类放到 `com.example.blog.BlogApplication`（确保所有子包都在主类包下）。

`application.yml` 最小配置：server.port / datasource / mybatis / actuator 端点。

### Step 3：第一个 Controller

```
GET  /api/posts            列表（keyset 分页）
POST /api/posts            发布文章
GET  /api/posts/{id}       详情
```

curl 验证全部 3 个接口，用 `Content-Type: application/json`。

### Step 4：自定义 HealthIndicator

写 `DbHealthIndicator`（按 03-check Q4 写法），访问 `/actuator/health` 看到 `components.db.status=UP`。

### Step 5：自动配置报告

```bash
java -jar blog.jar --debug 2>&1 | grep -E "(Matched|Did not match)" | head -30
```

截图贴到 `docs/autoconfig-report.md`，标注：

- `DataSourceAutoConfiguration` 为何匹配
- 哪个配置类帮你配了 Jackson
- `MyBatisAutoConfiguration` 激活条件

### Step 6：关掉一个自动配置再验证

临时排除 `DataSourceAutoConfiguration`，启动报错截图 → 恢复 → 启动正常。

写到 `docs/disable-autoconfig.md`。

## 交付物

- [ ] `BlogApplication.java`
- [ ] `application.yml` + `application-dev.yml`
- [ ] `controller/PostController.java`（3 个接口）
- [ ] `DbHealthIndicator.java` + 单测
- [ ] `docs/autoconfig-report.md`
- [ ] `docs/disable-autoconfig.md`
- [ ] curl 验证截图（3 接口 + `/actuator/health`）

## 验收清单

| 项 | 标准 |
|----|------|
| 3 个接口可 curl | 返回正确 JSON，无 500 |
| `/actuator/health` | `{"status":"UP","components":{"db":{...}}}` |
| 主类包路径 | grep `@SpringBootApplication` 在最顶层包 |
| 无多余 @Bean DataSource | 由 Boot 自动配置 HikariCP |
| Fat JAR 可启动 | `java -jar target/blog-*.jar` 正常起 |

## 扩展挑战

1. **分层打包**：开 `spring-boot-maven-plugin` 的 `layers`，让 Docker 镜像增量构建。
2. **GraalVM Native**：试着 `mvn -Pnative native:compile`，对比 JVM 启动时间。
3. **Micrometer + Prometheus**：加 `spring-boot-starter-actuator` + Prometheus 依赖，用 `docker run prom/prometheus` 拉 metrics 画图。
4. **优雅停机**：配 `server.shutdown=graceful + spring.lifecycle.timeout-per-shutdown-phase=30s`，kill -15 观察是否等待请求完成。
