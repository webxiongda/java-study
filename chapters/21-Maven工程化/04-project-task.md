# Chapter 21 Maven 工程化 - 项目任务

## 任务概述

把第 20 章产出的 `mini-utils` 单文件工具集，改造成一个**标准 Maven 工程 `blog-api`**，并产出一份贯穿后续 39 章的 **baseline pom**。

## 业务背景

到第 20 章为止，所有 Demo 都是 `javac SomeClass.java && java SomeClass`。从本章开始，要写真实的后端项目——意味着：

- 项目要分 `src/main/java` 和 `src/test/java`。
- 依赖（Spring Boot、MyBatis、JWT、Redis…）由 Maven 管理。
- 一条 `mvn package` 就能产出可部署的 jar，第 56 章会被 Docker 打进镜像。

## 任务拆解

### Step 1：环境核对（5 分钟）

```bash
java -version       # 期望: openjdk 21.x
mvn -v              # 期望: Apache Maven 3.9+，且 Java version: 21
```

如果版本不对，先去把 `JAVA_HOME` 改对，否则后面 39 章全报错。

### Step 2：建工程骨架（10 分钟）

```bash
cd ~/Desktop/xiong_projects/java-study
mkdir -p blog-api/src/main/java/com/example/blog
mkdir -p blog-api/src/main/resources
mkdir -p blog-api/src/test/java/com/example/blog
cd blog-api
```

把第 21 章 `02-demo.md` 里的 **baseline pom** 完整复制为 `pom.xml`。

### Step 3：写最小启动类

新建 `src/main/java/com/example/blog/BlogApplication.java`：

```java
package com.example.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

### Step 4：验证构建链路

| 命令 | 期望结果 |
|------|---------|
| `mvn clean compile` | `BUILD SUCCESS`，`target/classes/` 出现 `.class` |
| `mvn test` | `BUILD SUCCESS`，`Tests run: 0`（没用例不算错） |
| `mvn package` | `target/blog-api-1.0.0-SNAPSHOT.jar` 存在，体积约 18-20 MB |
| `java -jar target/blog-api-1.0.0-SNAPSHOT.jar` | 看到 Spring Boot 启动 banner，端口 8080 监听成功 |
| `curl http://localhost:8080/` | 返回 `{"status":404,"error":"Not Found"...}`——说明 Spring 已起，只是没写 controller |

### Step 5：把工具搬过来

把第 20 章 `mini-utils` 里的工具类（`StringUtils`、`DateUtils` 等）放进 `src/main/java/com/example/blog/util/`。

写一个简单的单元测试，例如 `src/test/java/com/example/blog/util/StringUtilsTest.java`：

```java
package com.example.blog.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {
    @Test
    void isBlank_should_return_true_for_whitespace() {
        assertTrue(StringUtils.isBlank("   "));
        assertTrue(StringUtils.isBlank(null));
        assertFalse(StringUtils.isBlank("hello"));
    }
}
```

跑 `mvn test`，期望看到 `Tests run: 1, Failures: 0`。

### Step 6：故意制造一次失败，记录排查过程

挑一个做：

1. **改坏 pom**：把 `<artifactId>spring-boot-starter-web</artifactId>` 改成 `spring-boot-starter-webx`。跑 `mvn compile`，记录报错信息（`Could not resolve dependencies for project...`），再改回。
2. **依赖冲突**：手动加 `<dependency>` 引一个老版本 `jackson-databind`，跑 `mvn dependency:tree -Dverbose | grep jackson`，看冲突日志。
3. **JDK 版本不匹配**：临时 `export JAVA_HOME=<JDK 17 路径>`，跑 `mvn compile` 看 `release version 21 not supported` 错误。

## 交付物

- [ ] `blog-api/pom.xml`（baseline pom，与 `02-demo.md` 一致或更完善）
- [ ] `blog-api/src/main/java/com/example/blog/BlogApplication.java`
- [ ] `blog-api/src/main/java/com/example/blog/util/` 下至少 2 个从 mini-utils 搬过来的工具类
- [ ] `blog-api/src/test/java/.../*Test.java` 至少 1 个能通过的测试
- [ ] `mvn package` 成功，`target/` 下产出 fat jar
- [ ] 一段 README（可写在项目根 `README.md` 末尾）：包含运行命令、`mvn dependency:tree` 截图、一次失败排查记录

## 验收清单

| 验收项 | 检查命令 / 标准 |
|-------|----------------|
| 工程结构正确 | `tree -L 4 blog-api` 看到 `src/main/java` 和 `src/test/java` 分离 |
| 编译通过 | `mvn -q clean compile` 退出码 0 |
| 测试通过 | `mvn -q test` 至少有 1 个 ✅ |
| 可执行 jar | `java -jar target/blog-api-*.jar` 看到 Tomcat started |
| 能讲清依赖来源 | 随便挑 1 个依赖（如 `jackson-databind`），能用 `mvn dependency:tree -Dincludes=...` 说清谁引入的 |
| 有失败记录 | README 里能复述一次错误现象、原因、修法 |

## 扩展挑战

1. **多 profile**：加 `<profiles>` 区分 `dev` / `prod`，`mvn package -Pprod` 时跳过测试并加 `-O` 优化。
2. **代码风格强制**：引入 `spotless-maven-plugin`，配置 google-java-format，在 `verify` 阶段执行 `spotless:check`，写错风格会让 CI 红。
3. **Git Hook**：在 `.git/hooks/pre-commit` 调 `mvn -q test`，防止把红色测试推上去。

完成本章后，**所有后续章节都会复用这个 `blog-api` 工程**——不再每章新建工程，而是 `git checkout -b chapter-22-junit` 持续在同一个仓库迭代。
