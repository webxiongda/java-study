# Chapter 21 Maven 工程化 - 理论篇

## 一、学习定位

本章把前 20 章「单文件 Demo」搬进**工程化结构**：用 Maven 管理依赖、生命周期、插件和多模块。它是 Spring Boot / MyBatis / JUnit 等所有后续章节赖以存在的「地基」——后面每一章新增依赖、跑测试、打包，都依赖 Maven 的约定。

- 优先级：L1 必须掌握
- 预计投入：3 小时
- 阶段产出：把 mini-utils 改造成 Maven 工程，得到一份贯穿全书的 baseline pom

## 二、核心概念

### 1. POM 与坐标（Group / Artifact / Version）

POM（Project Object Model）就是 `pom.xml`：Maven 把每个项目当成一个对象，POM 是它的元数据。坐标三元组 `groupId:artifactId:version`（简称 GAV）唯一标识一个工件。

```xml
<groupId>com.example</groupId>      <!-- 组织/模块的命名空间，反向域名 -->
<artifactId>blog-api</artifactId>   <!-- 工件名，对应 jar 名 -->
<version>1.0.0-SNAPSHOT</version>   <!-- 版本号，SNAPSHOT 表示开发版 -->
<packaging>jar</packaging>          <!-- 默认 jar，可选 war / pom -->
```

`SNAPSHOT` 与 release 的区别：
- `1.0.0-SNAPSHOT`：每次 `mvn install` 会覆盖本地仓库；CI 拉取时强制更新。
- `1.0.0`：release 版本，一旦发布到中央仓库**不可覆盖**——所以发布前必须把 SNAPSHOT 去掉。

### 2. 依赖管理（dependencies / scope / 传递依赖）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <scope>compile</scope>     <!-- 默认 scope -->
</dependency>
```

| scope | 编译期 | 测试期 | 运行期 | 打包进 jar | 典型例子 |
|-------|-------|-------|-------|-----------|---------|
| `compile` | ✅ | ✅ | ✅ | ✅ | 业务代码依赖 |
| `provided` | ✅ | ✅ | ❌ | ❌ | servlet-api（容器提供） |
| `runtime` | ❌ | ✅ | ✅ | ✅ | mysql-connector-j |
| `test` | ❌ | ✅ | ❌ | ❌ | junit-jupiter |
| `system` | ✅ | ✅ | ❌ | ❌ | 本地 jar（**不推荐**） |
| `import` | — | — | — | — | 只能用在 `<dependencyManagement>`，导入 BOM |

**传递依赖**：依赖 A 又依赖 B，Maven 自动把 B 也加进来。但 scope 会"降级"：A 的 compile 依赖 B 是 runtime，则你拿到的 B 是 runtime。

**调解原则**（出现版本冲突时）：
1. **路径最短优先**：A→B→C(1.0) vs A→D→E→C(2.0) → 选 1.0
2. **声明顺序优先**：路径长度相同时，pom 里先声明的赢

排查命令：`mvn dependency:tree` 看完整树；`mvn dependency:tree -Dverbose -Dincludes=jackson-databind` 看某个包为何被引入。

### 3. 生命周期 / 阶段 / 目标（Lifecycle / Phase / Goal）

Maven 有 3 套独立生命周期：

| 生命周期 | 关键阶段 | 用途 |
|---------|---------|-----|
| `clean` | pre-clean → **clean** → post-clean | 删除 `target/` |
| `default` | validate → compile → test → package → verify → install → deploy | 构建主流程 |
| `site` | pre-site → site → post-site → site-deploy | 生成项目文档 |

**关键规则**：执行某阶段会**自动执行该生命周期内之前的所有阶段**。所以 `mvn package` 会先 compile → test → package。

```bash
mvn clean compile         # 清理再编译
mvn clean test            # 跑测试（自动先 compile）
mvn clean package -DskipTests   # 打包但跳过测试
mvn clean install         # 安装到本地仓库 ~/.m2/repository
mvn deploy                # 发布到远程仓库（需在 pom 配 distributionManagement）
```

**阶段 vs 目标**：阶段是 Maven 定义的"步骤名"；目标（Goal）是插件提供的具体动作，格式 `plugin:goal`。比如 `compile` 阶段默认绑定了 `maven-compiler-plugin:compile` 这个目标。

直接调目标也行：`mvn compiler:compile`、`mvn dependency:tree`、`mvn spring-boot:run`。

### 4. 插件机制（build / plugins）

Maven 本身只是骨架，所有实际工作都由**插件**完成。每个插件提供若干 Goal，绑定到不同 Phase。

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <argLine>-Xmx512m</argLine>
                <includes>
                    <include>**/*Test.java</include>
                </includes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

后端常用插件备忘：

| 插件 | 作用 | 默认绑定阶段 |
|------|-----|------------|
| `maven-compiler-plugin` | 编译 Java | compile |
| `maven-surefire-plugin` | 单元测试（`*Test.java`） | test |
| `maven-failsafe-plugin` | 集成测试（`*IT.java`） | integration-test |
| `spring-boot-maven-plugin` | 打可执行 fat jar、`mvn spring-boot:run` | package |
| `maven-jar-plugin` | 打普通 jar | package |
| `maven-shade-plugin` | 打包 uber-jar，可重定位包名 | package |
| `versions-maven-plugin` | 批量升级依赖版本 | 手动调 |

### 5. 多模块与 parent / BOM

随着项目变大，会拆成多个模块共享配置：

```
blog-parent/                  ← packaging=pom，聚合 + 父配置
├── pom.xml
├── blog-common/              ← 工具类、DTO
├── blog-dao/                 ← MyBatis Mapper
├── blog-service/             ← 业务逻辑
└── blog-web/                 ← Controller，依赖前 3 个
```

父 pom：
```xml
<packaging>pom</packaging>
<modules>
    <module>blog-common</module>
    <module>blog-dao</module>
    <module>blog-service</module>
    <module>blog-web</module>
</modules>

<!-- 统一管理版本号，子模块声明依赖时不写 version -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>3.0.3</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

子模块：
```xml
<parent>
    <groupId>com.example</groupId>
    <artifactId>blog-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>blog-dao</artifactId>
<!-- 不写 version、groupId，从 parent 继承 -->
```

**BOM（Bill Of Materials）**：一个只含 `<dependencyManagement>` 的 pom，用 `<scope>import</scope>` 引入，相当于"借用别人的版本表"。Spring Boot 的 `spring-boot-dependencies` 就是 BOM。

## 三、工作原理

| 维度 | 要点 | 你需要能说清 |
|---|---|---|
| 入口 | `mvn <phase>` 或 IDE 触发 | Maven 解析 pom → 构建依赖树 → 按生命周期执行绑定的 Goal |
| 配置 | `pom.xml`、`~/.m2/settings.xml`、`-Dxxx` 命令行参数 | 优先级：命令行 > pom properties > settings.xml |
| 执行 | Surefire fork JVM 跑测试，Compiler 调 javac | 每个 Goal 是独立的 Mojo Java 类 |
| 边界 | 网络不通、版本冲突、scope 写错、JDK 版本不匹配 | 看 `[ERROR]` 行 + `mvn -X` 全量日志 |
| 验证 | `mvn -v`、`mvn help:effective-pom`、`mvn dependency:tree` | 在执行前先看「合并后的 pom 长什么样」 |

## 四、项目使用场景

博客 API 项目中：

- **第 22-29 章**：Maven 加 JUnit / Logback / JDBC / MyBatis 依赖，全靠本章 baseline pom。
- **第 32 章后**：Spring Boot 项目本质上就是一份特殊的 Maven 工程（parent 是 `spring-boot-starter-parent`）。
- **第 40、50 章里程碑**：用 `mvn package` 打成 fat jar，第 56 章再用 `Dockerfile` 把它 `COPY` 进镜像。
- **CI / CD**：GitHub Actions / Jenkins 调的是 `mvn clean verify`、`mvn deploy`。
- **依赖升级**：CVE 漏洞预警时，用 `mvn dependency:tree -Dincludes=<group:artifact>` 定位间接依赖，再用 `<dependencyManagement>` 强制升级。

## 五、常见问题与坑

| 问题 | 后果 | 处理方式 |
|---|---|---|
| `JAVA_HOME` 指向 JDK 17 但项目要求 21 | 编译报 `release version 21 not supported` | `mvn -v` 确认实际 JDK；在 IDEA 里 Settings → Build Tools → Maven → JDK for importer |
| 依赖冲突却没察觉 | NoSuchMethodError 在运行时才爆 | `mvn dependency:tree -Dverbose`；用 `<exclusions>` 排除老版本 |
| `mvn test` 不跑 / 漏跑用例 | 测试覆盖率假象 | 文件名必须匹配 `*Test.java` 或 `Test*.java`；类必须 public；方法必须 `@Test` 注解 |
| `${project.basedir}` 在 IDE 里是绝对路径，打包后失效 | 容器内找不到文件 | 用 classpath 资源（`getResourceAsStream`），把文件放 `src/main/resources` |
| pom 改了但不生效 | IDE 用了缓存的 effective-pom | IDEA：右键 pom → Maven → Reload Project；命令行加 `-U` 强制更新 SNAPSHOT |
| 中央仓库慢 | 拉依赖卡死 | `~/.m2/settings.xml` 加阿里云镜像 |
| 公司私服需要鉴权 | 401 / 403 | settings.xml 里配 `<server>` 节点，id 和 pom 里的 repository id 一致 |

## 六、面试高频问题

1. Maven 的依赖调解原则是什么？两条规则的优先级如何？
2. `compile`、`provided`、`runtime`、`test` 这四种 scope 有什么区别？分别举一个真实例子。
3. 解释 Maven 的 3 套生命周期。`mvn install` 实际会按顺序执行哪些 phase？
4. `<dependencyManagement>` 和 `<dependencies>` 有什么本质区别？什么是 BOM？
5. SNAPSHOT 和 release 版本的区别？为什么不能把 SNAPSHOT 发到中央仓库？
6. 多模块项目里子模块怎么继承父 pom 的配置？`<modules>` 和 `<parent>` 各起什么作用？
7. 你遇到过最棘手的 Maven 依赖冲突是什么？怎么排查、怎么解决？
