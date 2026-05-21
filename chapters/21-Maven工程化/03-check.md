# Chapter 21 Maven 工程化 - 自测题

## Q1（概念）：`compile` / `provided` / `runtime` / `test` 四种 scope 的区别？各举一个真实例子。

**参考答案：**

| scope | 编译期可见 | 测试期可见 | 运行期可见 | 打进最终 jar | 典型示例 |
|-------|---------|---------|---------|----------|---------|
| `compile`（默认） | ✅ | ✅ | ✅ | ✅ | `spring-boot-starter-web` |
| `provided` | ✅ | ✅ | ❌ | ❌ | `jakarta.servlet-api`（外部 Tomcat 容器提供） |
| `runtime` | ❌ | ✅ | ✅ | ✅ | `com.mysql:mysql-connector-j`（业务代码用 JDBC API，运行才需驱动） |
| `test` | ❌ | ✅ | ❌ | ❌ | `junit-jupiter`、`mockito-core` |

```xml
<!-- compile：业务直接 import -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- provided：编译需要 servlet API，但部署在 Tomcat 时由容器提供 -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- runtime：代码里写的是 JDBC 标准接口，不需要 import 驱动类 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- test：只在 src/test/java 下用 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**结论**：scope 写错的常见后果——
- 把 `test` 写成 `compile`：JUnit 被打进生产 jar，体积膨胀且可能引入安全漏洞。
- 把 `runtime` 写成 `compile`：你能 `import com.mysql.cj.jdbc.Driver`，但应该面向 `java.sql.Driver` 接口，绑死了具体驱动。
- 把 `provided` 写成 `compile`：jar 里多一份 servlet-api，跟 Tomcat 自带的冲突，启动报 `LinkageError`。

---

## Q2（概念）：Maven 依赖调解的两条原则是什么？请用一个具体冲突场景说明。

**参考答案：**

两条规则按顺序应用：

1. **路径最短优先（Nearest Wins）**：依赖树中**离根 pom 路径最短**的那个版本胜出。
2. **声明顺序优先（First Wins）**：路径长度相同时，pom 里**先声明**的依赖中的版本胜出。

```
你的 pom
├── lib-A (1.0)
│   └── jackson-databind (2.13.0)         ← 路径长度 2
└── lib-B (1.0)
    └── lib-C (1.0)
        └── jackson-databind (2.16.0)     ← 路径长度 3
```

**结果**：Maven 选 `jackson-databind 2.13.0`（路径短）。

但是这可能导致运行时报错——如果 lib-B → lib-C 是按 2.16 API 写的，会抛 `NoSuchMethodError`。

**排查命令**：
```bash
mvn dependency:tree -Dverbose -Dincludes=com.fasterxml.jackson.core:jackson-databind
```
输出会标注 `(omitted for conflict with 2.13.0)`。

**解决方案 3 选 1**：

```xml
<!-- 方案 1：在 pom 顶层显式声明，强制版本（推荐） -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.0</version>
</dependency>

<!-- 方案 2：从 lib-A 排除老版本 -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>lib-A</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<!-- 方案 3：在父 pom 的 dependencyManagement 里锁版本（多模块项目推荐） -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.16.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**口诀**：**显式声明 > 排除 > 听天由命**。生产项目一定要在父 pom 锁版本。

---

## Q3（实操）：下面这段 pom 有 4 个问题，找出并修正。

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>blog-api</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.3.5</version>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
        </dependency>
    </dependencies>
</project>
```

**参考答案：**

**问题 1：缺少 `project.build.sourceEncoding`，源码会用平台默认编码**

Windows 上默认 GBK，跑到 Linux 上 UTF-8，中文注释/资源乱码。

```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>   <!-- 加这行 -->
</properties>
```

**更现代的写法**：用 `<maven.compiler.release>21</maven.compiler.release>` 替代 source/target 一对，效果更严格（保证只用 JDK 21 内的 API）。

**问题 2：`mysql-connector-j` 缺少 `<version>`，且没有 `<scope>runtime</scope>`**

没有版本号又没有 parent / BOM，Maven 会报 `'dependencies.dependency.version' for com.mysql:mysql-connector-j:jar is missing`。即使能解析，业务代码也不应该 `import com.mysql.cj...`，应该用 `java.sql.*` 接口。

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
    <scope>runtime</scope>                <!-- 加这两行 -->
</dependency>
```

**问题 3：`junit-jupiter` 缺少 `<scope>test</scope>`**

会被打进生产 jar，且让生产代码能 `import org.junit.jupiter.api.Test`，破坏分层。

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>                   <!-- 加这行 -->
</dependency>
```

**问题 4：缺少 `spring-boot-maven-plugin`，打不出可执行 fat jar**

`mvn package` 出来的 jar 直接 `java -jar` 会报 `no main manifest attribute`。

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>3.3.5</version>
        </plugin>
    </plugins>
</build>
```

**加分项**：根本上推荐继承 `spring-boot-starter-parent`，4 个问题除 1 之外都能自动解决（版本、scope、plugin 都有默认值）。

---

## Q4（实操）：用 Maven 命令完成下列任务，每一步写出命令。

1. 看完整依赖树。
2. 看为什么会引入 `org.apache.tomcat.embed:tomcat-embed-core` 这个包。
3. 看父 POM 合并后的最终 pom 长什么样。
4. 强制更新所有 SNAPSHOT 依赖到最新。
5. 跳过测试打包。
6. 只跑名字包含 `UserService` 的测试用例。
7. 不联网，仅用本地仓库构建（适合 CI 缓存场景）。
8. 把当前项目装进本地仓库 `~/.m2/repository`，供同一台机器上的其他项目引用。

**参考答案：**

```bash
# 1. 完整依赖树（树形输出）
mvn dependency:tree

# 2. 反向定位：tomcat-embed-core 是谁引入的
mvn dependency:tree -Dverbose -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
# 输出会显示：spring-boot-starter-web -> spring-boot-starter-tomcat -> tomcat-embed-core

# 3. 看 effective pom（合并 parent、profile、properties 后的最终结果）
mvn help:effective-pom
# 嫌输出长可重定向：mvn help:effective-pom > effective.xml

# 4. -U：force update SNAPSHOT
mvn clean install -U

# 5. 跳过测试。两种写法效果不同：
mvn package -DskipTests          # 编译测试代码，但不执行
mvn package -Dmaven.test.skip    # 连编译都跳过，更快但风险更大

# 6. 精确跑指定测试
mvn test -Dtest=UserServiceTest                    # 跑一个类
mvn test -Dtest='UserService*'                     # 通配
mvn test -Dtest='UserServiceTest#testCreate'       # 跑某个方法

# 7. 离线模式
mvn -o package
# 前提：依赖已经在 ~/.m2 里，否则会失败

# 8. install 到本地仓库
mvn clean install
# 之后同机器其他项目就能 <dependency> 引用 com.example:blog-api:1.0.0-SNAPSHOT
```

**实战 tip**：
- CI 流水线常见组合：`mvn -B -U -ntp clean verify`（`-B` batch 无交互、`-ntp` no transfer progress 日志清爽）。
- 调试报错时加 `-X` 看完整 trace；加 `-e` 看异常栈。

---

## Q5（综合）：把现有的单模块 blog-api 拆成多模块项目

**题目**：现有 `blog-api` 项目结构如下：

```
blog-api/
├── pom.xml
└── src/main/java/com/example/blog/
    ├── BlogApplication.java
    ├── controller/UserController.java
    ├── service/UserService.java
    ├── dao/UserMapper.java
    └── common/Result.java
```

要求拆成 4 个模块：`blog-common`（工具/DTO）、`blog-dao`（Mapper）、`blog-service`（业务）、`blog-web`（Controller + 启动类）。给出**父 pom** 和 **blog-web 子 pom** 的完整内容，并说明依赖顺序和编译顺序。

**参考答案：**

**目标目录结构：**

```
blog-parent/
├── pom.xml                       ← packaging=pom，聚合 + 公共配置
├── blog-common/
│   ├── pom.xml
│   └── src/main/java/.../common/Result.java
├── blog-dao/
│   ├── pom.xml
│   └── src/main/java/.../dao/UserMapper.java
├── blog-service/
│   ├── pom.xml
│   └── src/main/java/.../service/UserService.java
└── blog-web/
    ├── pom.xml
    └── src/main/java/.../
        ├── BlogApplication.java
        └── controller/UserController.java
```

**依赖关系**（自下而上）：
```
blog-common  ← 被所有模块依赖
blog-dao     ← depends on blog-common
blog-service ← depends on blog-dao + blog-common
blog-web     ← depends on blog-service + blog-common
```

**父 POM（blog-parent/pom.xml）：**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>blog-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>            <!-- 关键：父模块 packaging=pom -->

    <modules>                             <!-- 聚合：mvn 在此目录下会构建这 4 个 -->
        <module>blog-common</module>
        <module>blog-dao</module>
        <module>blog-service</module>
        <module>blog-web</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis.version>3.0.3</mybatis.version>
        <mysql.version>8.4.0</mysql.version>
    </properties>

    <!-- 统一管理版本号，子模块声明依赖时不写 version -->
    <dependencyManagement>
        <dependencies>
            <!-- 自家模块也统一在这里管理版本 -->
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>blog-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>blog-dao</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>blog-service</artifactId>
                <version>${project.version}</version>
            </dependency>

            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>${mybatis.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

**blog-web 子 POM：**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example</groupId>
        <artifactId>blog-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>blog-web</artifactId>             <!-- 不写 groupId、version，从 parent 继承 -->

    <dependencies>
        <!-- 内部模块 -->
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>blog-service</artifactId>     <!-- 不写 version，由 dependencyManagement 决定 -->
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>blog-common</artifactId>
        </dependency>

        <!-- 外部依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 只在「能跑」的最终模块打 fat jar -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**编译顺序**：Maven 通过 `<modules>` 列出的依赖关系**自动计算反应器（reactor）顺序**——你不用关心 `<modules>` 里的声明顺序。在 `blog-parent` 目录执行：

```bash
mvn clean install
```

实际执行顺序：
```
[INFO] Reactor Build Order:
[INFO]   blog-parent
[INFO]   blog-common         ← 没有内部依赖，最先
[INFO]   blog-dao            ← 依赖 common
[INFO]   blog-service        ← 依赖 dao
[INFO]   blog-web            ← 依赖 service，最后
```

**两个常见踩坑：**

1. **循环依赖**：如果 `blog-common` 又反过来 `import` `blog-service` 的类，Maven 会报 `The projects in the reactor contain a cyclical reference`。**唯一的修法**是把循环引用的代码挪到下层（如把 `Result.java` 放到真正不依赖任何模块的 `common`）。

2. **只想 build 改动过的模块**：用 `-pl`（projects list）+ `-am`（also make，连同依赖）：
   ```bash
   # 只构建 blog-web 和它依赖的模块
   mvn install -pl blog-web -am
   # 构建 blog-dao 和依赖它的所有模块（用于改了底层后跑上游测试）
   mvn install -pl blog-dao -amd
   ```

**面试加分点**：当被问"你怎么管理多模块项目的版本号？"——答 **BOM + dependencyManagement**：所有外部依赖在父 pom 锁版本，子模块只写 GA、不写 V；自家模块用 `${project.version}` 统一跟版本号走。
