# 环境搭建与 Java 初印象 自测题。

> 先独立作答，再看参考答案。建议手写答案或在代码编辑器中验证。

## 题目

### Q1（概念）：请简述 JVM、JDK、JRE 三者的包含关系，并说明作为开发者应该安装哪个？

### Q2（概念）：下面这段代码中，`static` 关键字起到了什么作用？如果去掉 `static` 会发生什么？

```java
public class Demo {
    public static void main(String[] args) {
        System.out.println("test");
    }
}
```

### Q3（实操）：下面的代码有几处错误？请找出所有错误并说明原因。

```java
// 文件名：myapp.java
public class MyApp {
    public void main(String[] args) {
        system.out.println("Hello")
    }
}
```

### Q4（实操）：编写一个 Java 程序，要求：
1. 程序从命令行接收两个参数（用户名和年龄）
2. 打印格式为：`你好，{用户名}！你今年 {年龄} 岁。`
3. 如果没有传入参数，打印：`请传入用户名和年龄参数`

### Q5（项目应用）：你刚加入一个团队，需要从 GitHub 克隆一个已有的 Spring Boot 项目并在本地运行。项目使用 Maven 构建，JDK 要求是 21。请描述完整的操作步骤（从环境检查到看到程序输出），包括可能遇到的问题和解决方案。

---

## 参考答案

### A1：

三者是包含关系：**JDK ⊇ JRE ⊇ JVM**。

- JVM 是最核心的虚拟机，只负责执行字节码
- JRE = JVM + 标准类库，能运行 Java 程序但无法开发
- JDK = JRE + 开发工具（javac、jdb、javadoc 等），是完整的开发套件

作为开发者必须安装 **JDK**。注意：从 JDK 9 开始，JRE 不再作为独立包发布，安装 JDK 即同时拥有运行环境。

---

### A2：
，
`static` 让 `main` 方法成为**类方法**而非实例方法。

JVM 启动时需要找到程序入口并调用 `main`，此时还没有创建任何对象。如果 `main` 是静态方法，JVM 直接通过类名调用即可；如果去掉 `static`，JVM 就无法在不创建 `Demo` 对象的情况下调用 `main`，程序会因找不到入口而报错：

```
Error: Main method is not static in class Demo, please define the main method as:
   public static void main(String[] args)
```

---

### A3：

该代码有 **3 处错误**：

1. **文件名不一致**：文件名是 `myapp.java`（小写 m），但类名是 `MyApp`（大写 M）。Java 要求 `public class` 的类名必须与文件名完全一致（区分大小写）。应改为 `MyApp.java`。

2. **main 方法缺少 static**：`public void main(String[] args)` 应为 `public static void main(String[] args)`，否则 JVM 无法识别为程序入口。

3. **System 大小写错误**：`system.out.println` 应为 `System.out.println`（S 大写）。Java 严格区分大小写，`system` 和 `System` 是两个不同的标识符。

4. **（附加）缺少分号**：`System.out.println("Hello")` 末尾缺少分号 `;`。Java 语句必须以分号结尾。

---

### A4：

```java
public class WelcomeUser {
    public static void main(String[] args) {
        // 判断参数数量
        if (args.length < 2) {
            System.out.println("请传入用户名和年龄参数");
            System.out.println("用法：java WelcomeUser <用户名> <年龄>");
            return; // 提前结束程序
        }

        String username = args[0];
        String age = args[1];

        System.out.println("你好，" + username + "！你今年 " + age + " 岁。");
    }
}
```

运行示例：
```bash
# 正常运行
java WelcomeUser 小明 18
# 输出：你好，小明！你今年 18 岁。

# 缺少参数
java WelcomeUser
# 输出：请传入用户名和年龄参数
```

关键点：`args.length` 检查参数数量，`return` 可以在 `void` 方法中提前退出。

---

### A5：

**完整操作步骤**：

**第一步：检查本地环境**
```bash
java -version    # 确认显示 21.x.x
javac -version   # 确认有 javac（说明装的是 JDK 而非 JRE）
mvn -version     # 确认 Maven 已安装
git --version    # 确认 Git 已安装
```

**第二步：克隆项目**
```bash
git clone https://github.com/team/project-name.git
cd project-name
```

**第三步：检查项目 JDK 版本要求**
```bash
# 查看 pom.xml 中的 java.version 属性
cat pom.xml | grep -A2 "java.version"
```

**第四步：下载依赖并编译**
```bash
mvn clean install -DskipTests   # -DskipTests 跳过测试，加快速度
# 如果下载慢，先配置阿里云镜像到 ~/.m2/settings.xml
```

**第五步：运行项目**
```bash
# Spring Boot 项目通常有以下几种方式
mvn spring-boot:run
# 或者打包后运行
java -jar target/project-name-1.0.0.jar
```

**可能遇到的问题**：

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `JAVA_HOME` 未设置 | 环境变量缺失 | 设置 JAVA_HOME 指向 JDK 21 目录 |
| 依赖下载超时 | 国内访问 Maven Central 慢 | 配置阿里云镜像 |
| 编译报错 `--release 21` | 本地 JDK 版本低于 21 | 安装 JDK 21 并更新 JAVA_HOME |
| 端口被占用 | 默认 8080 端口冲突 | 修改 application.properties 中的 `server.port` |
