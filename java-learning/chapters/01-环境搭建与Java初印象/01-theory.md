# 环境搭建与 Java 初印象 理论文档

## 核心概念

### JVM / JDK / JRE 的区别

这是学 Java 第一天必须搞清楚的三个概念，很多初学者把它们混为一谈。

**JVM（Java Virtual Machine，Java 虚拟机）**

JVM 是 Java 能够"一次编写，到处运行"的核心。它是一个抽象的计算机规范，针对不同操作系统（Windows、macOS、Linux）有各自的实现。你写的 `.java` 文件被编译成 `.class` 字节码后，JVM 负责在具体操作系统上解释并执行这些字节码。JVM 本身不包含 Java 标准库，也不包含编译工具。

**JRE（Java Runtime Environment，Java 运行时环境）**

JRE = JVM + Java 标准类库（如 `java.util`、`java.io` 等）。如果你只是想运行别人编译好的 Java 程序，安装 JRE 就够了。

**JDK（Java Development Kit，Java 开发工具包）**

JDK = JRE + 开发工具（编译器 `javac`、调试器 `jdb`、文档生成器 `javadoc` 等）。作为开发者，你必须安装 JDK。

```
JDK
 └── JRE
      └── JVM
           └── 字节码解释/执行引擎
```

**JDK 21 的重要特性**

JDK 21 是 LTS（长期支持）版本，带来了几个关键改进：
- **虚拟线程（Virtual Threads）**：大幅降低高并发编程门槛
- **Record 类**：更简洁的不可变数据类
- **Pattern Matching**：`switch` 语句的模式匹配更加成熟
- **Sequenced Collections**：集合接口增强

作为初学者，暂时关注基础语法即可，但要知道自己用的是现代版本。

---

### Java 的执行流程

```
源代码(.java)
    ↓  javac 编译
字节码(.class)
    ↓  java 命令启动 JVM
类加载器加载 .class 文件
    ↓
字节码验证器检查合法性
    ↓
解释执行 或 JIT 即时编译（热点代码）
    ↓
操作系统 / 硬件执行
```

**关键点**：`.class` 文件是平台无关的字节码，不是机器码。JVM 才是与平台相关的部分。这就是 Java 跨平台的本质。

**JIT 编译器（Just-In-Time Compiler）**：JVM 会监测哪些代码被频繁执行（热点代码），然后将其编译为本地机器码缓存起来，后续直接执行机器码，速度接近原生程序。这是 Java 性能不差的原因之一。

---

### Maven 的作用

Maven 是 Java 项目的构建工具和依赖管理工具。类比 Node.js 的 npm：
- `pom.xml` 相当于 `package.json`，声明项目依赖
- 执行 `mvn package` 会自动下载依赖、编译源码、打包成 JAR
- Maven 仓库分为本地仓库（`~/.m2`）、中央仓库（Maven Central）、私服

```xml
<!-- pom.xml 核心结构示例 -->
<project>
    <groupId>com.example</groupId>       <!-- 组织标识，类似包名 -->
    <artifactId>my-app</artifactId>      <!-- 项目名 -->
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.2.0</version>
        </dependency>
    </dependencies>
</project>
```

---

### Hello World 解析

```java
public class HelloJava {           // 类名必须与文件名一致
    public static void main(String[] args) {  // 程序入口，固定签名
        System.out.println("Hello, Java!");   // 输出并换行
    }
}
```

- `public`：访问修饰符，表示对外可见
- `class`：声明这是一个类，Java 中一切皆类
- `static`：静态方法，JVM 启动时无需创建对象即可调用
- `void`：返回值为空
- `String[] args`：命令行参数数组，运行时通过 `java HelloJava arg1 arg2` 传入
- `System.out.println`：`System` 是类，`out` 是其静态字段（PrintStream 类型），`println` 是方法

---

## 使用场景

- **JDK 安装**：任何 Java 开发的第一步，本地开发、CI/CD 服务器都需要
- **IDEA**：企业级 Java 开发的首选 IDE，代码补全、重构、调试能力极强
- **Maven**：中大型项目必用，Spring Boot 项目标配
- **Git**：代码版本管理，与任何语言的开发都应该配合使用

---

## 工作原理

### 为什么 Java 是"编译+解释"混合型语言？

纯解释型语言（如早期 Python）：每次执行都逐行解释，慢。
纯编译型语言（如 C）：编译为特定平台的机器码，快但不跨平台。

Java 的方案：先编译为平台无关的字节码，再由 JVM 解释执行。同时引入 JIT 编译器，对热点代码做本地编译优化。这样既保证了跨平台，又兼顾了运行效率。

### 类加载机制（简版）

JVM 的类加载器采用"双亲委派模型"：
1. 当需要加载某个类时，先委托父加载器去加载
2. 父加载器找不到，才由当前加载器加载
3. 这样可以防止核心类库被用户自定义类覆盖（安全性）

---

## 常见坑与易错点

### 坑 1：文件名与类名不一致

```java
// 文件名：Hello.java
public class HelloJava {  // 错误！类名 HelloJava ≠ 文件名 Hello
    ...
}
```

**错误信息**：`class HelloJava is public, should be declared in a file named HelloJava.java`

**正确做法**：`public class` 的类名必须与文件名（不含 `.java` 后缀）完全一致，区分大小写。

---

### 坑 2：JAVA_HOME 配置错误导致命令找不到

在 macOS 上安装了多个 JDK 版本，或者 `JAVA_HOME` 指向了 JRE 而非 JDK 目录，会导致 `javac` 命令不可用。

**验证方式**：
```bash
java -version    # 查看 JVM 版本
javac -version   # 查看编译器版本（JDK 才有）
echo $JAVA_HOME  # 查看环境变量
```

**正确做法**：`JAVA_HOME` 应指向 JDK 根目录（包含 `bin/javac` 的那一层）。

---

### 坑 3：Maven 下载依赖超时（国内网络问题）

国内访问 Maven 中央仓库速度极慢，容易超时报错。

**解决方案**：在 `~/.m2/settings.xml` 中配置阿里云镜像：

```xml
<mirrors>
    <mirror>
        <id>aliyunmaven</id>
        <mirrorOf>*</mirrorOf>
        <name>阿里云公共仓库</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

---

### 坑 4：main 方法签名写错

```java
// 错误写法 1：少了 static
public void main(String[] args) { }

// 错误写法 2：参数类型错误
public static void main(String args) { }

// 错误写法 3：返回值写错
public static int main(String[] args) { return 0; }
```

JVM 查找的入口方法签名是固定的：`public static void main(String[] args)`，一个字都不能错。

---

## 面试高频问题

### Q1：JVM、JDK、JRE 的区别是什么？

**参考答案**：JVM 是 Java 虚拟机，负责执行字节码，是 Java 跨平台的核心，针对不同操作系统有不同实现。JRE 是运行时环境，包含 JVM 和 Java 标准类库，只能运行 Java 程序，不能开发。JDK 是开发工具包，包含 JRE 和开发工具（如 javac 编译器、javadoc 等），开发者必须安装 JDK。三者是包含关系：JDK ⊇ JRE ⊇ JVM。从 JDK 9 起，JRE 不再作为独立安装包发布，但逻辑上的分层关系依然存在。

### Q2：Java 为什么能跨平台？跨平台的是什么？

**参考答案**：Java 的跨平台指的是 Java 字节码（`.class` 文件）可以在任何安装了对应 JVM 的平台上运行。源代码 `.java` 被 `javac` 编译成字节码，字节码是平台无关的中间格式；然后由各个平台上的 JVM 负责将字节码解释或编译为本地机器码并执行。所以跨平台的是字节码，而 JVM 本身是平台相关的（不同操作系统安装不同版本的 JVM）。"一次编写，到处运行"（Write Once, Run Anywhere）正是基于这个机制。

### Q3：Java 是编译型语言还是解释型语言？

**参考答案**：Java 是"编译+解释"的混合型语言。编译阶段：`javac` 将 `.java` 源码编译为字节码（`.class`），这是静态编译。执行阶段：JVM 解释执行字节码，同时 JIT（即时编译器）会识别热点代码（被频繁执行的方法/循环），将其动态编译为本地机器码并缓存，后续直接执行机器码，性能接近原生编译语言。所以准确说 Java 是"先编译后解释，热点代码再 JIT 编译"的混合执行模式。

### Q4：Maven 的 groupId、artifactId、version 分别代表什么？

**参考答案**：这三者合称 GAV 坐标，是 Maven 中唯一标识一个依赖的方式。`groupId` 是组织或项目的唯一标识，通常是反向域名格式，如 `com.alibaba`；`artifactId` 是模块名，如 `fastjson`；`version` 是版本号，如 `2.0.0`。三者共同定位 Maven 仓库中的具体 JAR 文件。在实际项目中，SpringBoot 父项目会通过 BOM（Bill of Materials）统一管理常用依赖的版本，子模块只需声明 groupId 和 artifactId 即可，避免版本冲突。

### Q5：为什么 main 方法必须是 static 的？

**参考答案**：JVM 启动时，还没有创建任何对象，只加载了类。如果 `main` 方法是实例方法（非 static），JVM 就需要先创建该类的对象才能调用，而创建对象又需要调用构造方法——这形成了先有鸡还是先有蛋的问题。`static` 方法属于类本身而非某个实例，JVM 在加载完类之后就可以直接通过类名调用，无需先创建对象。因此 JVM 规范规定程序入口 `main` 必须是 `public static void main(String[] args)`。

### Q6：Git 中 `git init`、`git clone`、`git pull` 有什么区别？

**参考答案**：`git init` 在当前目录初始化一个新的本地仓库，创建 `.git` 目录，用于从零开始的项目。`git clone <url>` 从远程仓库完整复制整个项目（包含所有历史记录）到本地，适合加入已有项目。`git pull` 是 `git fetch` + `git merge` 的组合，从远程拉取最新提交并合并到当前本地分支，用于同步他人的更新。日常开发流程通常是：clone 一次，后续用 pull 同步，用 commit + push 提交。
