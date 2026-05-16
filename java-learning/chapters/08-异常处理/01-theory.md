# Chapter 08 - 异常处理：核心理论

## 1. Throwable 体系

Java 中所有可抛出的对象都必须继承自 `java.lang.Throwable`，体系如下：

```
Throwable
├── Error（错误，程序一般无法处理）
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception（异常，程序可以处理）
    ├── RuntimeException（非受检异常 / unchecked）
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── IndexOutOfBoundsException
    │   └── ...
    └── 其他 Exception（受检异常 / checked）
        ├── IOException
        ├── SQLException
        └── ...
```

**Error**：JVM 级别的严重错误，如内存溢出、栈溢出。程序通常无法从 Error 中恢复，不应该被 catch（极少数场景如 OOM 监控除外）。

**Exception**：程序逻辑范围内可预期、可处理的异常，分为两类：

---

## 2. Checked vs Unchecked 异常

### Checked 异常（受检异常）
- 继承自 `Exception`（但不继承 `RuntimeException`）
- 编译器强制要求：调用方必须 `try-catch` 或在方法签名用 `throws` 声明
- 设计哲学：表示"合理的、可预期的异常情况"，调用方有责任处理
- 例：`IOException`、`SQLException`、`ClassNotFoundException`

### Unchecked 异常（非受检异常）
- 继承自 `RuntimeException`
- 编译器不强制处理，可以捕获也可以不捕获
- 设计哲学：表示"编程错误"，如 null 解引用、数组越界，这类错误应该修复代码而非捕获异常
- 例：`NullPointerException`、`IllegalArgumentException`、`ClassCastException`

### 如何选择？

| 场景 | 推荐类型 | 原因 |
|------|----------|------|
| 文件不存在、网络超时 | Checked | 调用方可以恢复，如重试或提示用户 |
| 参数为 null / 非法 | Unchecked | 是调用方代码错误，应该修 bug |
| 数据库连接失败 | 两者均有争议 | 现代框架（Spring）倾向 Unchecked |
| 业务规则违反（余额不足） | Unchecked（自定义） | 业务异常通常传播到上层统一处理 |

**现代趋势**：Spring 等主流框架大量使用 Unchecked 异常，因为强制 checked 会导致大量样板代码和"异常吞噬"问题。

---

## 3. try / catch / finally 执行规则

### 基本结构

```java
try {
    // 可能抛出异常的代码
} catch (ExceptionType1 e) {
    // 处理 ExceptionType1
} catch (ExceptionType2 e) {
    // 处理 ExceptionType2
} finally {
    // 无论如何都会执行的代码（资源释放）
}
```

### 执行顺序规则

1. `try` 块正常执行 → 执行 `finally` → 继续后续代码
2. `try` 块抛出异常 → 匹配 `catch` → 执行 `finally` → 继续后续代码
3. `try` 块抛出异常 → 无匹配 `catch` → 执行 `finally` → 异常继续向上传播

### finally 一定会执行吗？

**99% 的情况下，finally 一定执行**，包括：
- `try` 或 `catch` 中有 `return` 语句
- `try` 或 `catch` 中抛出了新异常

**不执行的极端情况**（了解即可）：
- 调用了 `System.exit()` 终止 JVM
- JVM 崩溃（如 OOM 导致 finally 本身无法执行）
- 守护线程被 JVM 强制终止

### finally 里有 return 的危险

```java
public int test() {
    try {
        return 1;
    } finally {
        return 2;  // 警告：这会覆盖 try 中的 return 1！
    }
}
// 调用 test() 返回 2，try 中的 return 1 被吞掉
```

**结论**：永远不要在 `finally` 中写 `return` 或抛出新异常，这会掩盖原始异常或返回值。

---

## 4. 多 catch 顺序规则

**规则：子类异常必须放在父类异常之前。**

```java
// 错误写法：Exception 放在前面，后面的 catch 永远不会执行
try {
    // ...
} catch (Exception e) {       // 编译错误！IOException 永远匹配不到
    // ...
} catch (IOException e) {
    // ...
}

// 正确写法：从具体到通用
try {
    // ...
} catch (FileNotFoundException e) {  // 先捕获更具体的
    // ...
} catch (IOException e) {            // 再捕获更通用的
    // ...
} catch (Exception e) {              // 最后兜底
    // ...
}
```

### 多异常合并捕获（Java 7+）

```java
// 当多个异常处理逻辑相同时，可以合并
try {
    // ...
} catch (IOException | SQLException e) {
    log.error("操作失败", e);
}
```

---

## 5. try-with-resources（Java 7+）

用于自动关闭实现了 `AutoCloseable` 接口的资源，替代手动在 `finally` 中关闭。

```java
// 传统写法（繁琐且容易忘关）
BufferedReader br = null;
try {
    br = new BufferedReader(new FileReader("file.txt"));
    // 使用 br
} catch (IOException e) {
    e.printStackTrace();
} finally {
    if (br != null) {
        try {
            br.close();   // close 本身也可能抛异常
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// try-with-resources 写法（简洁，自动关闭）
try (BufferedReader br = new BufferedReader(new FileReader("file.txt"))) {
    // 使用 br，退出 try 块后自动调用 br.close()
} catch (IOException e) {
    e.printStackTrace();
}
```

**执行顺序**：资源关闭发生在 `catch/finally` 之前，如果关闭时也抛出异常，该异常会被"抑制"（suppressed），原始异常优先。

---

## 6. 自定义异常

### 基本写法

```java
// 继承 RuntimeException（推荐，不强制调用方 catch）
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 支持异常链
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

### 使用

```java
public void transfer(double amount) {
    if (amount > balance) {
        throw new BusinessException("INSUFFICIENT_BALANCE", "余额不足，当前余额：" + balance);
    }
}
```

---

## 7. throw vs throws

| | `throw` | `throws` |
|--|---------|----------|
| 位置 | 方法体内 | 方法签名上 |
| 作用 | 实际抛出一个异常实例 | 声明该方法可能抛出的异常类型 |
| 后接 | 异常对象（`throw new XxxException(...)`） | 异常类名（`throws IOException, SQLException`） |

```java
// throws：声明可能抛出
public void readFile(String path) throws IOException {
    // throw：实际抛出
    if (path == null) {
        throw new IllegalArgumentException("path 不能为 null");
    }
    // ...
}
```

---

## 8. 异常链（Exception Chaining）

捕获底层异常后，包装成更有业务含义的高层异常，同时保留原始异常信息，便于排查。

```java
// 方式一：构造器传入 cause
try {
    // 数据库操作
} catch (SQLException e) {
    throw new DataAccessException("查询用户失败", e);  // e 作为 cause
}

// 方式二：initCause()（不推荐，是老写法）
BusinessException ex = new BusinessException("DB_ERROR", "数据库错误");
ex.initCause(originalException);
throw ex;

// 获取原始异常
try {
    // ...
} catch (DataAccessException e) {
    Throwable cause = e.getCause();  // 获取被包装的原始异常
    cause.printStackTrace();
}
```

**重要**：永远不要丢失异常链！以下写法会丢失原始异常：
```java
// 错误：丢失了原始异常 e
throw new ServiceException("操作失败");  // 没传 cause

// 正确：保留异常链
throw new ServiceException("操作失败", e);
```

---

## 9. 异常设计原则

1. **不要吞掉异常**：`catch (Exception e) {}` 空实现是最危险的代码之一，问题会在生产环境悄悄发生。
2. **尽早抛出，尽晚捕获**：在检测到问题的地方立即抛出，在有能力处理的地方再捕获。
3. **异常不做流程控制**：不要用 try-catch 控制正常业务流程，异常有性能开销（创建调用栈）。
4. **记录异常时要包含完整信息**：`log.error("操作失败", e)` 而不是 `log.error(e.getMessage())`。
5. **自定义异常要有意义**：名称能体现业务场景，携带足够的上下文信息（错误码、字段名等）。

---

## 常见坑

**坑 1：catch (Exception e) {} 静默吞掉异常**

```java
// 错误！异常被吞掉，问题无法排查
try {
    riskyOperation();
} catch (Exception e) {
    // 什么都不做
}

// 正确：至少记录日志，或者重新抛出
try {
    riskyOperation();
} catch (Exception e) {
    log.error("riskyOperation 执行失败", e);
    throw e;  // 如果不能在这里处理，就重新抛出
}
```

**坑 2：finally 里的 return 覆盖原始返回值**

```java
// 危险！finally 里的 return 2 会覆盖 try 里的 return 1
public int dangerous() {
    try {
        return 1;
    } finally {
        return 2;  // 实际返回 2，逻辑混乱
    }
}
// 同理，finally 里抛出新异常会覆盖原始异常
```

**坑 3：catch 顺序写错，子类永远匹配不到**

```java
// 编译错误：IOException 是 Exception 的子类，被前面的 catch(Exception) 拦截了
try {
    // ...
} catch (Exception e) {
    System.out.println("通用异常");
} catch (IOException e) {  // 编译报错：Exception 'java.io.IOException' has already been caught
    System.out.println("IO异常");
}
```

---

## 面试高频问题

**Q1：finally 一定会执行吗？**

答：几乎一定，但有两种例外：1）调用 `System.exit()` 强制终止 JVM；2）JVM 本身崩溃。在正常程序流程中，即使 `try` 或 `catch` 里有 `return`，`finally` 也会在 `return` 之前执行。但要注意：如果 `finally` 里有 `return`，它会覆盖 `try/catch` 里的 `return`，这是非常危险的写法，应该避免。

**Q2：checked 和 unchecked 异常的设计哲学是什么？**

答：checked 异常（如 `IOException`）的哲学是"编译器强制调用方处理可预期的失败情况"，适合调用方有能力恢复的场景（如文件不存在时让用户重新输入路径）。unchecked 异常（如 `NullPointerException`）的哲学是"编程错误，应该修复代码而非捕获异常"。现代框架（如 Spring）更倾向使用 unchecked，因为 checked 容易导致大量样板代码和异常被静默吞掉（`catch (Exception e) {}`）。

**Q3：如何正确记录异常日志？**

答：应该用 `log.error("描述信息", e)` 而不是 `log.error(e.getMessage())`。原因：
1. `e.getMessage()` 只包含消息，丢失了堆栈信息，无法定位问题。
2. 第二个参数传 `Throwable` 时，SLF4J/Log4j 会自动打印完整堆栈。
3. 描述信息应包含上下文，如 `log.error("查询用户失败，userId={}", userId, e)`。

**Q4：try-with-resources 和 try-finally 有什么区别？**

答：`try-with-resources`（Java 7+）会自动调用 `AutoCloseable.close()` 方法，代码更简洁。关键区别是异常处理：如果 `try` 块和 `close()` 都抛出异常，`try-finally` 中 `finally` 的异常会覆盖原始异常（原始异常丢失），而 `try-with-resources` 会将 `close()` 的异常作为"抑制异常"（suppressed exception）附加到原始异常上，可以通过 `e.getSuppressed()` 获取，原始异常不丢失。

**Q5：什么是异常链，为什么要保留它？**

答：异常链是指在捕获底层异常后，创建一个高层异常并将底层异常作为 `cause` 传入（如 `throw new ServiceException("msg", e)`）。保留异常链的原因：1）高层异常提供业务含义，方便上层理解问题；2）底层异常提供技术细节，方便定位根源。如果不保留 cause（`throw new ServiceException("msg")`），生产环境出问题时可能永远找不到根本原因。可以通过 `e.getCause()` 或查看日志中的 `Caused by:` 段落查看完整链路。
