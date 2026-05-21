# Chapter 39 配置与环境 - 项目任务

## 任务概述

为博客项目搭好完整的多环境配置体系：dev / staging / prod 三套 Profile，`@ConfigurationProperties` 绑定上传 / 特性开关等模块配置，所有敏感信息通过环境变量注入。

## 业务背景

现在博客只有一套配置，换到团队协作就会遇到："为什么你改了我的 DB 密码？" "为什么这个配置在 prod 就是 dev 的值？" "为什么上传目录在 dev 写了 /data/uploads 磁盘满了？"

这一章解决这些问题。

## 任务拆解

### Step 1：拆分三套 Profile 文件

`src/main/resources/` 下：

```yaml
application.yml              # 基础：server.port, spring.datasource 基础配置
application-dev.yml          # 本地开发
application-staging.yml      # 与 prod 尽量一致
application-prod.yml         # 生产配置
```

所有密码在 Profile YML 里用 `${DB_PASSWORD}` 环境变量形式。

### Step 2：@ConfigurationProperties 类

- `UploadProperties`：上传目录（dev=/tmp，staging/prod=/data/uploads）、允许类型、大小限制
- `FeatureFlags`：newHomePage / newSearch 等开关

对应 yml 的 `blog.upload.*` 和 `blog.feature.*`。

### Step 3：修改 Service 读取配置

`FileService` / `MailService` 改为从 ConfigurationProperties 获取参数，不再写 @Value。

### Step 4：application-staging.yml 创建

staging 与 prod 尽量一致（DB 地址不同、Swagger 对内网开），保证 staging 测试的接口行为与 prod 一致。

### Step 5：验证加载优先级

```bash
# 默认 dev 启动
mvn spring-boot:run -Dspring.profiles.active=dev

# 命令行覆盖端口
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9000

# 环境变量覆盖密码
DB_PASSWORD=override mvn spring-boot:run -Dspring.profiles.active=prod
```

验证：

```bash
curl http://localhost:9000/actuator/env/server.port
# 来源显示 commandLineArgs

curl http://localhost:9000/actuator/env/spring.datasource.password
# 来源显示 prod: ENC
```

### Step 6：.gitignore 加 application-local.yml

```gitignore
# 本地开发自定义配置，不提交
application-local.yml
```

开发可以建 `application-local.yml` 自己配密码，`spring.profiles.include: local` 引入。

## 交付物

- [ ] `application.yml`（基础配置，不含密码）
- [ ] `application-dev.yml`
- [ ] `application-staging.yml`
- [ ] `application-prod.yml`（不含明文密码）
- [ ] `UploadProperties.java`（`@ConfigurationProperties prefix=blog.upload`）
- [ ] `FeatureFlags.java`
- [ ] 修改后的 `FileService.java` 使用 UploadProperties
- [ ] `.gitignore` 含 `application-local.yml`

## 验收清单

| 项 | 标准 |
|----|------|
| 0 个明文密码 | `grep "password:\s" application*.yml` 只能命中 `${xxx}` 形式 |
| 3 套配置可启动 | `--spring.profiles.active=dev|staging|prod` 全部正常启动 |
| 上传目录正确 | dev 是 `/tmp/`，staging/prod 是 `/data/` |
| Swagger 只在 dev 开 | prod profile 下 `/swagger-ui/` 返回 404 |
| @ConfigurationProperties | FileService 里注入 UploadProperties，不是 `@Value` |

## 扩展挑战

1. **配置中心**：引入 Nacos Config / Spring Cloud Config → 运行时改配置，对比本地 yml 的差距。
2. **Vault 集成**：用 Spring Vault 从 HashiCorp Vault 拉数据库密码和 API Key，证明密码不落盘。
3. **外部化配置**：测试 jar 外部的 `./config/application.yml` 覆盖 jar 内部配置的效果。
4. **配置校验 CI**：写一个单元测试，启动时 `@SpringBootTest` 自动校验 `MailProperties` 的 `@NotEmpty` 字段是否都已绑定。
