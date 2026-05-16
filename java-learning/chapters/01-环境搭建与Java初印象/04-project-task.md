# 环境搭建与 Java 初印象 项目任务

## 业务背景

你入职了一家做 SaaS 产品的小公司，被分配到后端团队。团队使用 Java 21 + Spring Boot 3 技术栈。在正式开始开发任务之前，技术 Lead 要求你完成一个"环境验证程序"：搭建标准项目结构，并实现一个带交互欢迎界面的命令行工具，用于未来演示和内部测试。

这个任务听起来简单，但需要你真正理解项目结构、编译流程和版本管理——因为你的代码需要能被其他同事 `git clone` 后直接运行。

---

## 技术要求

1. 使用 Maven 标准项目结构（`src/main/java/` 目录层级）
2. 程序必须通过命令行参数接收输入，并做参数校验
3. 必须配置正确的 `pom.xml`，指定 JDK 21 编译目标
4. 项目必须纳入 Git 版本管理，包含合理的 `.gitignore`
5. 至少提交 2 次 commit，提交信息符合 `feat:` / `fix:` 规范

---

## 任务说明

实现一个名为 `WelcomeApp` 的命令行程序，功能如下：

**功能 1：基础欢迎**
- 运行 `java WelcomeApp` （无参数）时，打印系统信息和使用说明

**功能 2：个性化欢迎**
- 运行 `java WelcomeApp <姓名> <角色>` 时，打印个性化欢迎语

**输入/输出示例**：

无参数运行：
```
==============================
   欢迎使用 Java 学习环境验证工具
==============================
Java 版本：21.0.2
操作系统：Mac OS X
当前时间：2024-01-15 10:30:00

用法：java WelcomeApp <姓名> <角色>
示例：java WelcomeApp 张三 后端工程师
==============================
```

带参数运行（`java WelcomeApp 张三 后端工程师`）：
```
==============================
  你好，张三！
  欢迎加入团队，你的角色是：后端工程师
  今天是你加入的第一天，加油！
==============================
```

参数不足（`java WelcomeApp 张三`）：
```
[错误] 参数不足！需要提供姓名和角色两个参数。
用法：java WelcomeApp <姓名> <角色>
```

---

## 验收标准

- [ ] Maven 项目结构正确，`pom.xml` 中指定了 `<maven.compiler.source>21</maven.compiler.source>`
- [ ] 程序能正确处理 3 种情况：无参数、2个参数、参数不足
- [ ] 输出中包含当前 Java 版本（通过 `System.getProperty("java.version")` 获取）
- [ ] 项目包含 `.gitignore`（正确排除 `target/` 和 `*.class`），且有至少 2 条 Git 提交记录
- [ ] 代码可以通过 `mvn compile && mvn exec:java -Dexec.mainClass="com.example.WelcomeApp"` 成功运行

---

## 提示（不是答案）

1. 获取当前时间可以用 `new java.util.Date().toString()`，或者更规范的 `java.time.LocalDateTime.now()`——先用简单的，后面章节会系统学习时间 API
2. 打印分隔线可以用一个字符串常量，比如 `String SEPARATOR = "=============================="`，然后多次复用，体会"避免重复"的编程思想

---

## 常见坑

1. **包名和目录不对应**：如果在 `pom.xml` 里定义了 `groupId` 为 `com.example`，那么 Java 文件应该放在 `src/main/java/com/example/` 目录下，且文件头要写 `package com.example;`，否则编译可能报找不到类的错误

2. **`args.length` 与下标越界**：直接访问 `args[0]` 而不先检查 `args.length`，当无参数运行时会抛出 `ArrayIndexOutOfBoundsException`，程序崩溃而不是输出友好提示

3. **Git 提交了 `target/` 目录**：如果忘记配置 `.gitignore`，Maven 编译产物会被提交进仓库，导致仓库体积膨胀，其他人 clone 后还可能因为二进制文件冲突而出问题
