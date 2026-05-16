# 环境搭建与 Java 初印象 实操 Demo

## Demo 1：Hello Java + 命令行参数读取

### 实操目标
验证 JDK 安装成功，理解 Java 程序的基本结构，学会通过命令行传递参数。

### 示例代码

```java
// 文件名：HelloJava.java
public class HelloJava {
    public static void main(String[] args) {
        // 基本输出
        System.out.println("Hello, Java 21!");
        System.out.println("欢迎来到 Java 世界！");

        // 读取命令行参数
        System.out.println("命令行参数数量：" + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.println("参数[" + i + "] = " + args[i]);
        }

        // 打印 JVM 信息
        System.out.println("Java 版本：" + System.getProperty("java.version"));
        System.out.println("操作系统：" + System.getProperty("os.name"));
        System.out.println("用户目录：" + System.getProperty("user.home"));
    }
}
```

### 运行结果

不带参数运行：`java HelloJava`
```
Hello, Java 21!
欢迎来到 Java 世界！
命令行参数数量：0
Java 版本：21.0.2
操作系统：Mac OS X
用户目录：/Users/yourname
```

带参数运行：`java HelloJava Alice 25`
```
Hello, Java 21!
欢迎来到 Java 世界！
命令行参数数量：2
参数[0] = Alice
参数[1] = 25
Java 版本：21.0.2
操作系统：Mac OS X
用户目录：/Users/yourname
```

### 关键点说明

- `System.out.println()` vs `System.out.print()`：前者输出后换行，后者不换行
- `args.length` 获取参数数量，`args[i]` 获取第 i 个参数（从 0 开始）
- `System.getProperty(key)` 可以获取 JVM 系统属性，常用属性键包括 `java.version`、`os.name`、`user.home`
- 字符串用 `+` 拼接时，数字会自动转换为字符串

---

## Demo 2：Maven 项目结构与 pom.xml 配置

### 实操目标
理解 Maven 标准目录结构，能创建一个 Maven 项目并成功编译运行。

### 示例代码

**项目结构**（使用 IDEA 创建 Maven 项目后的目录）：
```
my-first-app/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── example/
    │               └── App.java
    └── test/
        └── java/
            └── com/
                └── example/
                    └── AppTest.java
```

**pom.xml**：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <!-- GAV 坐标，唯一标识这个项目 -->
    <groupId>com.example</groupId>
    <artifactId>my-first-app</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <!-- 项目属性 -->
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <!-- 依赖声明（目前暂无外部依赖） -->
    <dependencies>
        <!-- 后续添加 Spring Boot 等依赖 -->
    </dependencies>
</project>
```

**src/main/java/com/example/App.java**：
```java
package com.example;

/**
 * Maven 项目入口类
 * 演示标准 Maven 项目结构下的 Hello World
 */
public class App {

    /**
     * 打印欢迎信息
     * @param name 用户姓名
     * @return 格式化的欢迎语
     */
    public static String greet(String name) {
        return "Hello, " + name + "! Welcome to Maven project.";
    }

    public static void main(String[] args) {
        // 调用方法
        String message = greet("Developer");
        System.out.println(message);

        // 打印项目信息
        System.out.println("GroupId: com.example");
        System.out.println("ArtifactId: my-first-app");
        System.out.println("Java Version: " + System.getProperty("java.version"));
    }
}
```

**src/test/java/com/example/AppTest.java**：
```java
package com.example;

// 简单测试类（不依赖 JUnit，后续章节会引入）
public class AppTest {
    public static void main(String[] args) {
        // 手动验证 greet 方法
        String result = App.greet("Alice");
        String expected = "Hello, Alice! Welcome to Maven project.";

        if (expected.equals(result)) {
            System.out.println("测试通过！");
        } else {
            System.out.println("测试失败！");
            System.out.println("期望: " + expected);
            System.out.println("实际: " + result);
        }
    }
}
```

### 运行结果

在 IDEA 中右键 `App.java` -> Run，或命令行：
```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.example.App"
```

输出：
```
Hello, Developer! Welcome to Maven project.
GroupId: com.example
ArtifactId: my-first-app
Java Version: 21.0.2
```

### 关键点说明

- `package com.example` 声明包名，对应目录结构 `src/main/java/com/example/`，包名和目录必须一致
- `SNAPSHOT` 版本表示开发中的快照版本，发布时会去掉 SNAPSHOT
- Maven 的 `src/main/java` 存放生产代码，`src/test/java` 存放测试代码，这是约定优于配置的思想
- `mvn compile` 只编译不打包，`mvn package` 编译并打包为 JAR

---

## Demo 3：Git 基本工作流演示

### 实操目标
掌握 Git 初始化、提交、查看日志的基本操作，建立版本控制习惯。

### 示例代码（Shell 命令 + Java 文件）

```bash
# 1. 初始化项目
mkdir java-hello && cd java-hello
git init
git config user.name "Your Name"
git config user.email "your@email.com"

# 2. 创建 .gitignore（告诉 Git 忽略哪些文件）
cat > .gitignore << 'EOF'
# 编译产物
target/
*.class

# IDEA 配置文件
.idea/
*.iml

# Maven 包装器
.mvn/
mvnw
mvnw.cmd
EOF

# 3. 创建第一个 Java 文件（假设已通过 Maven 创建项目）

# 4. 第一次提交
git add .
git status            # 查看暂存区状态
git commit -m "feat: 初始化 Java 项目，添加 HelloJava 入口类"

# 5. 修改文件后，查看差异
git diff              # 查看工作区与暂存区的差异
git diff --staged     # 查看暂存区与上次提交的差异

# 6. 再次提交
git add src/
git commit -m "feat: 添加 greet 方法并完善注释"

# 7. 查看提交历史
git log --oneline     # 简洁格式
git log --oneline --graph  # 图形化分支
```

**提交历史示例输出**：
```
a3f1b2c feat: 添加 greet 方法并完善注释
8e9d4a1 feat: 初始化 Java 项目，添加 HelloJava 入口类
```

### 对应的 Java 文件（修改前后对比）

**初始版本（第一次提交）**：
```java
// HelloJava.java - v1
public class HelloJava {
    public static void main(String[] args) {
        System.out.println("Hello, Java!");
    }
}
```

**修改版本（第二次提交）**：
```java
// HelloJava.java - v2
public class HelloJava {

    /**
     * 生成个性化问候语
     */
    public static String greet(String name) {
        return "Hello, " + name + "!";
    }

    public static void main(String[] args) {
        System.out.println(greet("Java 21"));
        System.out.println("项目已纳入版本管理");
    }
}
```

### 运行结果

```
Hello, Java 21!
项目已纳入版本管理
```

### 关键点说明

- `.gitignore` 中一定要排除 `target/` 和 `*.class`，这些是编译产物，不应提交到仓库
- `git add .` 把所有变更加入暂存区，`git commit -m` 提交并附说明
- 提交信息建议使用 `feat:` / `fix:` / `docs:` 等前缀（Conventional Commits 规范）
- `git log --oneline` 查看简洁历史，排查问题时非常实用
