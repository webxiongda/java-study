# Chapter 21 Maven 工程化 - 实操 Demo

## Demo 目标

把 `mini-utils` 改造成标准 Maven 工程，并产出一份贯穿后续所有章节的「基线 pom」。所有从第 22 章开始的 demo，都将在这份 pom 上做「加依赖」的增量改动，不再重复给完整 pom。

## 前置条件

- JDK 21（`java -version` 输出 `21.x`）
- Maven 3.9+（`mvn -v`）
- IDEA / VS Code，UTF-8
- 已用 Git 管理

## 实操步骤

1. 新建分支 `chapter-21-maven` 或在工作目录建 `blog-api/` 子目录。
2. 用下面的 **基线 pom** 初始化 `pom.xml`，先不要加任何业务依赖。
3. 在 `src/main/java/com/example/blog/BlogApplication.java` 写一个最小 Spring Boot 启动类（仅做版本验证用），跑 `mvn spring-boot:run` 看启动日志。
4. 跑 `mvn clean package`，确认在 `target/` 下生成可执行 jar，且 `java -jar` 能起。
5. 跑 `mvn dependency:tree | head -50`，观察传递依赖；跑 `mvn help:effective-pom` 看父 POM 合并后的最终结果。
6. 阅读「生命周期 vs 阶段 vs 目标」一节，能口头说清 `clean / compile / test / package / install` 五个阶段做了什么。

## 基线 pom（后续所有章节复用）

> 版本基准：Spring Boot 3.3.x（兼容 JDK 21）。后续每一章 demo 只展示「需要新增的 `<dependency>`」，不再重复完整文件。

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>blog-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>blog-api</name>
    <description>Java 学习路线主项目（博客 API）</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- 子章节会逐步用到的版本，集中管理便于升级 -->
        <mybatis-spring-boot.version>3.0.3</mybatis-spring-boot.version>
        <mysql.version>8.4.0</mysql.version>
        <pagehelper.version>2.1.0</pagehelper.version>
        <springdoc.version>2.6.0</springdoc.version>
        <jjwt.version>0.12.6</jjwt.version>
        <redisson.version>3.34.1</redisson.version>
        <bucket4j.version>8.10.1</bucket4j.version>
        <assertj.version>3.26.3</assertj.version>
    </properties>

    <dependencies>
        <!-- Web（含 jackson、tomcat、jakarta.validation-api） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 参数校验（jakarta.validation，第 34 章） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- 测试：含 JUnit 5、Mockito、AssertJ、Spring Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 后续章节增量依赖速查（仅供参考，对应章节再加）

| 章节 | 新增依赖（`<dependency>` 关键 GAV） |
|---|---|
| 23 日志 | `org.springframework.boot:spring-boot-starter-logging`（已传递引入，仅需 `logback-spring.xml`） |
| 24 JDBC + 25/26 SQL | `org.springframework.boot:spring-boot-starter-jdbc` + `com.mysql:mysql-connector-j:${mysql.version}` |
| 28-29、36 MyBatis | `org.mybatis.spring.boot:mybatis-spring-boot-starter:${mybatis-spring-boot.version}` + `com.github.pagehelper:pagehelper-spring-boot-starter:${pagehelper.version}` |
| 37 JPA | `org.springframework.boot:spring-boot-starter-data-jpa`（与 MyBatis 二选一） |
| 38 OpenAPI | `org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdoc.version}` |
| 42 JWT | `io.jsonwebtoken:jjwt-api:${jjwt.version}` + `jjwt-impl`（runtime）+ `jjwt-jackson`（runtime） |
| 44 Security | `org.springframework.boot:spring-boot-starter-security` |
| 45 限流 | `com.bucket4j:bucket4j-core:${bucket4j.version}` |
| 46-47 Redis | `org.springframework.boot:spring-boot-starter-data-redis` + `org.redisson:redisson-spring-boot-starter:${redisson.version}` |
| 48 MQ | `org.springframework.boot:spring-boot-starter-amqp` |
| 49 文件上传 | 内置在 starter-web，无需新增 |

## 启动类示例

~~~java
package com.example.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
~~~

## 运行与验证

| 检查项 | 命令 / 期望 |
|---|---|
| 编译通过 | `mvn clean compile` 输出 `BUILD SUCCESS` |
| 测试通过 | `mvn test` 输出 `Tests run: 0`（暂无用例也算成功） |
| 打包成功 | `mvn package`，`target/blog-api-1.0.0-SNAPSHOT.jar` 存在 |
| 可执行运行 | `java -jar target/blog-api-1.0.0-SNAPSHOT.jar`，访问 `http://localhost:8080/actuator`（404 也算 Spring 起了） |
| 依赖树清晰 | `mvn dependency:tree` 能看到 `spring-boot-starter-web` 等树形结构 |
| 有效 pom | `mvn help:effective-pom` 能看到父 POM 合并后的版本号 |

## 常见坑

- `JAVA_HOME` 指向 JDK 17 但 Maven 用 21 → `mvn -v` 检查实际版本。
- 公司内网先在 `~/.m2/settings.xml` 配 mirror（阿里云）再拉依赖。
- IDEA 改完 pom 不生效 → 右键 `Reload Maven Project`。

## 建议提交

~~~bash
git add pom.xml src/
git commit -m "chapter 21: bootstrap baseline maven pom (spring-boot 3.3, jdk 21)"
~~~
