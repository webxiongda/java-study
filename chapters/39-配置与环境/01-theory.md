# Chapter 39 配置与环境 - 理论篇

## 一、学习定位

代码在 dev / test / prod 三套环境里跑，不能每换一个环境就改代码。Spring Boot 的配置体系帮你做到：**"代码一次，配置各环境不同"**。

- 优先级：L1
- 预计投入：2 小时
- 阶段产出：博客项目的 dev/prod 两套配置，所有敏感变量由环境变量注入

## 二、核心概念

### 1. 配置优先级（高→低）

```
1. 命令行参数：        --server.port=9090
2. Java 系统属性：     -Dserver.port=9090
3. 环境变量：          SERVER_PORT=9090
4. Profile YML：      application-prod.yml（外部）
5. Profile YML：      application-prod.yml（jar 内）
6. application.yml（外部，jar 同目录 /config/）
7. application.yml（jar 包内）
8. @PropertySource：  @PropertySource("custom.properties")
```

**生产实践**：

```bash
java -jar blog.jar \
  --spring.profiles.active=prod \
  --spring.datasource.password=${DB_PASSWORD}   # 密码从环境变量传入
```

### 2. Profile 的作用

```yaml
# application.yml（基础配置，所有环境共享）
spring:
  datasource:
    url: jdbc:mysql://localhost/blog
    username: root

---
# 多文档 YML（同一文件内分隔）
spring:
  config:
    activate:
      on-profile: dev
server:
  port: 8080
---
spring:
  config:
    activate:
      on-profile: prod
server:
  port: 8080
```

**环境间差异**：

| 配置项 | dev | prod |
|--------|-----|------|
| DB 地址 | localhost | prod-rds.xxx.com |
| 密码 | 明文（dev 密码） | 环境变量 $DB_PASSWORD |
| 日志级别 | DEBUG | INFO |
| SQL 日志 | 开（调试用）| 关 |
| Swagger | 开 | 关 |
| DataSource 池大小 | 10 | 50 |

### 3. @ConfigurationProperties 类型安全绑定

```java
@Component
@ConfigurationProperties(prefix = "blog.upload")
@Data
public class UploadProperties {
    private String baseDir = "/tmp";         // 默认值
    private long maxSize = 5 * 1024 * 1024L; // 5MB
    private List<String> allowedTypes = List.of("jpg", "png", "gif");
}
```

对应 YML：

```yaml
blog:
  upload:
    base-dir: /var/data/uploads    # relaxed binding：baseDir ↔ base-dir
    max-size: 10485760             # 10MB
    allowed-types:
      - jpg
      - png
      - gif
      - webp
```

**@Value 对比**：

| 维度 | `@Value("${xxx}")` | `@ConfigurationProperties` |
|------|-------------------|--------------------------|
| 绑定方式 | 逐个字段 | 整个 prefix 注入 |
| 校验 | 不支持 | 支持 `@Validated` |
| 复杂类型 | 手写 SpEL | 自动转 List/Map/Duration |
| 默认值 | `${xxx:default}` | 代码里字段默认值 |
| 推荐度 | 简单 key 用 | 模块配置用 |

### 4. 随机值

```yaml
blog:
  secret: ${random.uuid}
  retry-base: ${random.long(1000,10000)}
```

### 5. 加密配置（生产）

**绝对不要**把密码明文写入 YML 提交 Git。方案（由简到繁）：

| 方案 | 做法 | 复杂度 |
|------|------|--------|
| 环境变量 | `${DB_PASSWORD}` | 简单 |
| Spring Cloud Vault | 启动时从 HashiCorp Vault 拉 | 中 |
| Jasypt | `ENC(加密串)` start 解密 | 低 |
| k8s Secret | 挂载为环境变量 | 中 |

### 6. @Profile

```java
@Component
@Profile("dev")
public class DevMailSender implements MailSender {
    public void send(String to, String msg) {
        log.info("[DEV mail] would send to {}: {}", to, msg);
    }
}

@Component
@Profile("prod")
public class ProdMailSender implements MailSender {
    public void send(String to, String msg) {
        mailClient.send(to, msg);  // 真实发邮件
    }
}
```

也可以在 yml 里控制：

```yaml
spring:
  autoconfigure:
    exclude:          # prod 排除 DataSource 自动配置（仅演示）
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

### 7. 运行时刷新

```java
@RefreshScope
@Component
@ConfigurationProperties(prefix = "blog.feature")
@Data
public class FeatureFlags {
    private boolean newHomePage = false;
    private boolean newSearch = false;
}
```

启动 `spring-boot-starter-actuator` 后，`POST /actuator/refresh` 可触发配置热加载。

## 三、工作原理

| 维度 | 要点 |
|---|---|
| 入口 | `ConfigFileApplicationListener` 按优先级加载 `application*.yml` |
| 配置 | `spring.config.import` 可引入外部配置中心（Consul / Nacos / Config Server）|
| 执行 | `Environment` 持有所有 PropertySource，`@Value` / `@ConfigurationProperties` 从它读 |
| 边界 | 密码明文风险、配置键名拼错（会被静默忽略）、Profile 溢出 |
| 验证 | `curl /actuator/env` 看所有配置项和来源；`--debug` 启动看配置加载报告 |

## 四、在博客项目里的落点

- `application.yml`：通用配置（datasource 基础、mybatis、server）
- `application-dev.yml`：日志 DEBUG、Swagger 开、本地 MySQL
- `application-staging.yml`：与生产配置一致但 DB 密码不同
- `application-prod.yml`：日志 INFO、Swagger 关、连接池 50、密码从环境变量
- `@ConfigurationProperties`：UploadProperties / FeatureFlags

## 五、常见坑

| 现象 | 原因 | 修法 |
|------|------|------|
| `@Value("${xxx}")` 启动抛异常 | key 在 yml 里不存在 | 配默认值 `${xxx:default}` |
| 多环境配置没生效 | profile 没写对或没激活 | `spring.profiles.active=prod` 检查拼写 |
| `@ConfigurationProperties` 字段全 null | prefix 写错或没加 `@Component` | `@Component` 或 `@EnableConfigurationProperties(XxxProperties.class)` |
| 密码泄露到 Git | yml 里明文写密码 | 改用环境变量 + .gitignore |
| 配置热加载不生效 | 没加 `@RefreshScope` | 加注解 |
| yml 里写中文注释乱码 | yml 编码问题 | 用 `UTF-8` 保存 |

## 六、面试高频问题

1. Spring Boot 配置加载优先级？
2. `@Value` 和 `@ConfigurationProperties` 区别？怎么选？
3. Profile 怎么配？多环境配置怎么组织？
4. `@RefreshScope` 的作用？怎么触发刷新？
5. 生产密码怎么管理？
6. `application.yml` 里多文档（---）和多个文件怎么选？
