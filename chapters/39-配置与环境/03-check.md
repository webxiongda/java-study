# Chapter 39 配置与环境 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：`application.yml` 和 `application-prod.yml` 的关系？如果两个文件里有同一个 key，最终用哪个？

**参考答案：**

**关系**：`application.yml` 是基础配置，**所有 Profile 共享**。`application-prod.yml` 是 prod Profile 的**增量覆盖**，只当 `spring.profiles.active=prod` 时加载。

**最终值 = 基础 + Profile 覆盖 + 命令行/环境变量更高优先级覆盖**。

**举例**：

```yaml
# application.yml
server:
  port: 8080
logging:
  level:
    com.example: DEBUG

# application-prod.yml
server:
  port: 80
  # logging.level 不写 → 保留 DEBUG？还是变成默认 INFO？
```

**答案**：logging.level 保持 application.yml 里的 DEBUG（**Profile 只覆盖同层同名 key，不覆盖整棵树**）。

如果要 prod 覆盖 logging.level，得显式写：

```yaml
# application-prod.yml
logging:
  level:
    com.example: INFO    # 覆盖
```

**优先级总览**（从高到低）：

```
1. 命令行 --server.port=9090
2. application-prod.yml（外部文件）
3. application-prod.yml（jar 内）
4. application.yml（外部文件）
5. application.yml（jar 内）
```

**关键点**：Profile YML 是**增量覆盖**不是替换。不写的 key 继承基础配置，显式写了才覆盖。

---

## Q2（概念）：`@ConfigurationProperties` 和 `@Value` 怎么选？各自的优缺点？

**参考答案：**

| 维度 | `@Value("${xxx}")` | `@ConfigurationProperties` |
|------|-------------------|--------------------------|
| 绑定 | 单个 key | 整个 prefix → 对象 |
| 校验 | 不支持，null 了运行时才知道 | `@Validated` + Bean Validation |
| 复杂类型 | 手写 SpEL：`${list:''}.split(',')` | List / Map / Duration 自动解析 |
| 默认值 | `${xxx:default}` | 字段默认值 |
| 复用 | 每个使用处都要写一次 | 注入一次对象到处用 |
| 刷新 | 配合 `@RefreshScope` | 配合 `@RefreshScope` |
| 推荐场景 | 1-2 个分散 key | 一整个模块的配置组 |

**选型建议**：

- 1-2 个散落 key（如单个开关 `${feature.search}`）→ `@Value`
- 一整个模块的参数（上传配置 / 数据源 / 缓存）→ `@ConfigurationProperties`
- 超过 3 个 key 就应抽出 `@ConfigurationProperties` 类

```java
// ❌ 散落 @Value
@Service
public class FileService {
    @Value("${blog.upload.base-dir:/tmp}") String baseDir;
    @Value("${blog.upload.max-size:5242880}") long maxSize;
    @Value("${blog.upload.allowed-types}") String allowedTypes;
}

// ✅ ConfigurationProperties 集中管理
@Service @RequiredArgsConstructor
public class FileService {
    private final UploadProperties uploadProps;
}
```

---

## Q3（实操）：以下配置有 6 处问题，找出并改正。

```yaml
# application.yml
spring:
  datasource:
    password: 123456                               # ①

mybatis:
  mapper-locations: classpath:mapper/*.xml

---
spring:
  profile: dev                                     # ②
spring:
  datasource:
    username: root
    password: root:                                # ③ 冒号结尾

---
profiles:                                          # ④
  active: ${PROFILE:dev}
---
blog.upload.base-dir: /tmp                         # ⑤
```

```java
@Component
@ConfigurationProperties(prefix = "blog.upload")
public class UploadProperties {
    private String baseDir;
    public String getBaseDir() { return baseDir; }
    // 没有 setter                                 # ⑥
}
```

**参考答案：**

```yaml
# application.yml（基础配置，不含密码）
spring:
  datasource:
    password: ${DB_PASSWORD:}                      # ① 无密码提交 Git，环境变量注入

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml      # classpath* 支持多模块

---
spring:
  config:
    activate:
      on-profile: dev                              # ② Spring Boot 3.x 写法
  datasource:
    username: root
    password: root                                 # ③ 冒号结尾解析成 ""（空字符串），改正
  logging:
    level:
      com.example: DEBUG

---
# application-prod.yml（独立文件，不写在多文档里）
spring:
  config:
    activate:
      on-profile: prod                             # ④ 语法统一
  datasource:
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

```yaml
# application-prod.yml（独立文件）
# 正确方式
```

```java
@Component
@ConfigurationProperties(prefix = "blog.upload")
public class UploadProperties {
    private String baseDir = "/tmp";               // ⑤ 默认值在代码中，不在 yml
    public String getBaseDir() { return baseDir; }
    public void setBaseDir(String baseDir) { this.baseDir = baseDir; }  // ⑥ Spring Boot 需要 setter 绑定
}
```

**6 处问题**：

1. 密码明文在 yml 里提交 Git。
2. Spring Boot 3.x 多文档 `---` 语法：应是 `spring.config.activate.on-profile`，不是 `spring.profile`。
3. yml 值以冒号结尾 → 解析为空字符串。
4. `profiles.active` 拼写错 → 应是 `spring.profiles.active`，且 prod 应提为独立文件。
5. `blog.upload.base-dir` 在多文档段写，与配置类绑定混淆。默认值应写在 Java 字段上。
6. `@ConfigurationProperties` 需要 setter 方法（或 @Data/@Setter）才能绑定。

---

## Q4（实操）：写一个 `MailProperties` 配置类，用 `@ConfigurationProperties` 绑定以下 yml，并在 Service 中使用。支持 prod 时发真实邮件、dev 时走日志。

```yaml
mail:
  enabled: true
  host: smtp.xxx.com
  port: 587
  auth:
    user: ${MAIL_USER}
    pass: ${MAIL_PASS}
  admin-email:
    - admin1@x.com
    - admin2@x.com
  max-retries: 3
```

**参考答案：**

```java
@ConfigurationProperties(prefix = "mail")
@Component
@Data
@Validated
public class MailProperties {
    private boolean enabled = true;
    @NotEmpty private String host;
    @Min(1) @Max(65535) private int port = 587;
    private Auth auth = new Auth();
    @NotEmpty private List<String> adminEmails;
    @Min(1) private int maxRetries = 3;

    @Data
    public static class Auth {
        private String user;
        private String pass;
    }
}

@Service
@RequiredArgsConstructor
public class MailService {
    private final MailProperties mailProps;

    public void sendAdminAlert(String subject, String body) {
        if (!mailProps.isEnabled()) {
            log.info("[mail disabled] would send alert to {}", mailProps.getAdminEmails());
            return;
        }
        // 发真实邮件
        for (String email : mailProps.getAdminEmails()) {
            mailClient.send(email, subject, body);
        }
    }
}
```

**Profile 区分**：

```java
@Component
@Profile("dev")
public class DevMailService extends MailService {
    public void sendAdminAlert(String subject, String body) {
        log.info("[DEV] would send '{}' to {}: {}", subject, mailProps.getAdminEmails(), body);
    }
}

@Component
@Profile("prod")
public class ProdMailService extends MailService {
    public void sendAdminAlert(String subject, String body) {
        mailClient.send(mailProps.getAdminEmails(), subject, body);
    }
}
```

**或者 yml 控制**：

```yaml
# application-dev.yml
mail:
  enabled: false    # dev 不发真实邮件，Service 里判断 enabled

# application-prod.yml
mail:
  enabled: true
```

**关键点**：

1. 嵌套类 `Auth` 也是 `@Data`，自动绑定 `mail.auth.user`。
2. `List<String> adminEmails` 自动绑定 yml 里的列表。
3. `enabled: false` 通过 Service 内判断实现，不依赖 `@Profile`——更适合动态开关场景。
4. `@Validated` 让 `@NotEmpty host` 启动时就报错，不等到运行时才 NPE。

---

## Q5（综合）：你们公司应用有 dev / staging / prod 三套环境，配置差异如下：

| 配置项 | dev | staging | prod |
|--------|-----|---------|------|
| 数据库 | localhost | staging-rds | prod-rds |
| DB 密码 | dev123 | 环境变量 | 环境变量 |
| 日志级别 | DEBUG | INFO | INFO |
| Swagger | 开 | 内网开 | 关 |
| 上传目录 | /tmp/uploads | /data/uploads | /data/uploads |

请设计 yml 文件结构，并写出 `application-staging.yml` 的关键配置。

**参考答案：**

### 一、文件结构

```
resources/
├── application.yml              # 所有环境共享
├── application-dev.yml          # dev 特有
├── application-staging.yml      # staging 特有
└── application-prod.yml         # prod 特有
```

### 二、各文件内容

```yaml
# application.yml（基础，所有环境共享）
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost/blog
    username: root
    password: ${DB_PASSWORD:dev123}   # 默认值 dev123，可被环境变量覆盖
  servlet:
    multipart:
      max-file-size: 5MB

blog:
  upload:
    base-dir: /tmp/uploads            # dev 默认值
    allowed-types: jpg,png,gif,webp
  feature:
    new-home-page: true
```

```yaml
# application-dev.yml（本地开发）
spring:
  datasource:
    password: dev123                  # 覆盖默认，显式写方便本地
logging:
  level:
    com.example: DEBUG
    org.springframework.web: DEBUG
springdoc:
  swagger-ui:
    enabled: true
```

```yaml
# application-staging.yml（预发布，与 prod 接近但 Swagger 对内网开）
spring:
  datasource:
    url: jdbc:mysql://staging-rds.xxx.com:3306/blog
    password: ${DB_PASSWORD}          # 从环境变量
logging:
  level:
    com.example: INFO
springdoc:
  swagger-ui:
    enabled: true                     # 内网可查看
blog:
  upload:
    base-dir: /data/uploads
```

```yaml
# application-prod.yml（生产）
spring:
  datasource:
    url: jdbc:mysql://prod-rds.xxx.com:3306/blog
    password: ${DB_PASSWORD}
logging:
  level:
    com.example: WARN
springdoc:
  swagger-ui:
    enabled: false
  api-docs:
    enabled: false
blog:
  upload:
    base-dir: /data/uploads
```

### 三、启动方式

```bash
# dev
java -jar blog.jar --spring.profiles.active=dev

# staging
DB_PASSWORD=xxx java -jar blog.jar --spring.profiles.active=staging

# prod
DB_PASSWORD=yyy java -jar blog.jar --spring.profiles.active=prod
```

### 四、安全注意事项

1. `application.yml` 里的 `password` 用 `${DB_PASSWORD:dev123}`——prod 覆盖环境变量时就不透传默认值。
2. `application-prod.yml` 的密码**不**写默认值 → 不配密码启动直接报错，不偷偷用不安全的值。
3. `.gitignore` 加 `application-local.yml`（开发自定义密钥配置）。
4. CI/CD 在部署时自动注入 `DB_PASSWORD`（GitHub Secrets / 云平台 Secret）。

**关键点**：

1. **共享配置写在 `application.yml`**，差异写 Profile 文件，避免三份文件重复。
2. **staging 配置 ≈ prod 配置**，区别只是 DB 地址和 Swagger——同样作为产线验证环境，越接近 prod 越好。
3. **环境变量解决密码问题**，不写死在任何 yml 里；本地开发用 `application-dev.yml` 里的 dev 密码。
4. **配置文件名 = 环境名**，不是 `application-env-prod.yml` 等多余层级。
