# Chapter 39 配置与环境 - 实操 Demo

## Demo 目标

在博客项目里搭好 3 套配置 + 用 `@ConfigurationProperties` 绑定模块配置 + 演示配置加载优先级。

## 前置

- 32 章的 Boot 应用可运行

## 一、3 套配置

`src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: blog-api
server:
  port: 8080

blog:
  upload:
    base-dir: ${UPLOAD_DIR:/tmp/uploads}
    max-size: 5MB
    allowed-types: jpg,png,gif
  feature:
    new-home-page: true
    new-search: false
```

`application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost/blog?useSSL=false
    username: root
    password: root123
logging:
  level:
    com.example: DEBUG
    org.springframework.web: DEBUG
springdoc:
  swagger-ui.enabled: true
```

`application-prod.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://prod-cluster/blog
    username: ${DB_USER}
    password: ${DB_PASSWORD}
logging:
  level:
    com.example: INFO
springdoc:
  swagger-ui.enabled: false
```

## 二、@ConfigurationProperties

```java
@Component
@ConfigurationProperties(prefix = "blog.upload")
@Validated
public record UploadProperties(
    @NotEmpty String baseDir,
    @Positive long maxSize,
    List<String> allowedTypes
) {}

@Component
@ConfigurationProperties(prefix = "blog.feature")
public record FeatureFlags(
    boolean newHomePage,
    boolean newSearch
) {}
```

## 三、Service 使用

```java
@Service @RequiredArgsConstructor
public class FileService {
    private final UploadProperties uploadProps;
    private final FeatureFlags featureFlags;

    public String save(MultipartFile file) {
        // 检查文件类型
        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        if (!uploadProps.allowedTypes().contains(ext)) {
            throw new BusinessException("不允许的文件类型: " + ext);
        }
        // 保存文件到配置目录
        Path target = Path.of(uploadProps.baseDir(), UUID.randomUUID() + "." + ext);
        file.transferTo(target);
        return target.toString();
    }
}
```

## 四、加载优先级验证

1. 启动 `application-dev.yml` 配置
2. 用 `--server.port=9090` 覆盖端口
3. 访问 `/actuator/env/server.port` 看来源

```bash
# 步骤 1
mvn spring-boot:run

# 步骤 2：停止后重新启动覆盖
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090

# 步骤 3
curl http://localhost:9090/actuator/env/server.port
# "propertySources": [
#   {name:"commandLineArgs", value:"9090"},
#   {name:"application.yml", value:"8080"}
# ]
```

## 五、@RefreshScope 热加载

```java
@RefreshScope
@Component
@ConfigurationProperties(prefix = "blog.feature")
public class FeatureFlags { ... }
```

```bash
# 改 yml 里的 blog.feature.new-home-page = false
# 触发刷新
curl -X POST http://localhost:8080/actuator/refresh

# 验证
curl http://localhost:8080/api/v1/features
# {"newHomePage":false,"newSearch":true}
```

## 六、失败场景：密码泄露

```yaml
# ❌ 错误：明文密码提交 Git
spring.datasource.password: mypassword123

# ✅ 正确：环境变量
spring.datasource.password: ${DB_PASSWORD}
```

确定 .gitignore 包含 `application-local.yml`，开发自己的本地配置不提交。

## 提交建议

```bash
git add src/main/resources/application{,-dev,-prod}.yml
git add UploadProperties.java FeatureFlags.java
git commit -m "chapter 39: 3-profile config + @ConfigurationProperties"
```
