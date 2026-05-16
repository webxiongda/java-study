# Chapter 08 - 自测题

## Q1（概念）：以下代码输出什么？

**题目**：不运行代码，判断输出结果并解释原因。

```java
public class Test {
    public static int compute() {
        int x = 0;
        try {
            x = 1;
            int[] arr = new int[3];
            arr[5] = 10;  // 抛出 ArrayIndexOutOfBoundsException
            x = 2;
        } catch (ArrayIndexOutOfBoundsException e) {
            x = 3;
            return x;    // 准备返回 3
        } finally {
            x = 4;       // x 被修改为 4，但 catch 中已经保存了返回值 3
            System.out.println("finally 中 x = " + x);
        }
        return x;
    }

    public static void main(String[] args) {
        int result = compute();
        System.out.println("result = " + result);
    }
}
```

**参考答案**：

输出：
```
finally 中 x = 4
result = 3
```

**解释**：

1. `x = 1` 执行
2. `arr[5] = 10` 抛出 `ArrayIndexOutOfBoundsException`，`x = 2` 不执行
3. 进入 catch 块，`x = 3`，执行 `return x`：此时 JVM 记录下返回值 `3`，但不立即返回
4. 执行 finally 块：`x = 4`（局部变量 x 变为 4），打印 `"finally 中 x = 4"`
5. finally 执行完毕，真正返回之前记录的值 `3`

关键点：`return x` 中的 `x` 是基本类型，JVM 在执行 return 时已经复制了当时的值（3），后续 finally 对 `x` 的修改不影响返回值。如果是对象引用，情况会不同（修改引用指向的对象内容会影响返回结果，但替换引用本身不影响）。

---

## Q2（概念）：异常继承层次与 catch 顺序

**题目**：以下代码能编译通过吗？如果能，运行时输出什么？如果不能，说明原因并给出正确写法。

```java
import java.io.*;

public class CatchOrderTest {
    public static void main(String[] args) {
        try {
            InputStream is = new FileInputStream("/nonexistent.txt");
        } catch (Exception e) {
            System.out.println("捕获到 Exception");
        } catch (FileNotFoundException e) {
            System.out.println("捕获到 FileNotFoundException");
        }
    }
}
```

**参考答案**：

**不能编译通过**。

编译错误：`Exception 'java.io.FileNotFoundException' has already been caught`

原因：`FileNotFoundException` 是 `IOException` 的子类，`IOException` 是 `Exception` 的子类。catch 从上到下匹配，`catch (Exception e)` 已经能捕获所有异常，后面的 `catch (FileNotFoundException e)` 永远不会执行，Java 编译器检测到这种"不可达的 catch 块"会直接报错。

**正确写法**（子类在前，父类在后）：
```java
try {
    InputStream is = new FileInputStream("/nonexistent.txt");
} catch (FileNotFoundException e) {
    System.out.println("捕获到 FileNotFoundException");  // 优先匹配
} catch (IOException e) {
    System.out.println("捕获到 IOException");
} catch (Exception e) {
    System.out.println("捕获到 Exception");
}
```

运行输出：`捕获到 FileNotFoundException`

---

## Q3（实操）：完善异常处理代码

**题目**：以下代码存在多处异常处理问题，请找出所有问题并给出修复版本。

```java
public class UserService {
    public User findUser(int userId) {
        try {
            User user = database.findById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            return user;
        } catch (Exception e) {
            // 问题A
        }
        return null;
    }

    public void deleteUser(int userId) throws Exception {
        try {
            User user = findUser(userId);
            database.delete(user);
            System.out.println("删除成功");
        } catch (Exception e) {
            throw new Exception("删除失败");  // 问题B
        }
    }

    public String readConfig(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            return reader.readLine();
        } catch (IOException e) {
            return null;  // 问题C
        }
        // 问题D：reader 没有关闭
    }
}
```

**参考答案**：

**问题 A**：`catch (Exception e) {}` 静默吞掉所有异常，调用方无法知道发生了什么错误。

**问题 B**：`throw new Exception("删除失败")` 没有传入 `cause`，丢失了异常链；同时 `throws Exception` 过于宽泛，应该声明具体异常类型。

**问题 C**：`return null` 静默忽略了 IO 异常，调用方拿到 null 但不知道原因，后续可能引发 NPE 并且难以排查。

**问题 D**：`BufferedReader` 没有在 finally 中关闭，即使抛出异常也不会关闭，造成资源泄漏。

**修复版本**：

```java
public class UserService {

    // 修复A：记录日志并向上抛出，而不是静默吞掉
    public User findUser(int userId) {
        try {
            User user = database.findById(userId);
            if (user == null) {
                throw new UserNotFoundException("用户不存在，userId=" + userId);
            }
            return user;
        } catch (DatabaseException e) {
            log.error("查询用户失败，userId={}", userId, e);
            throw new ServiceException("查询用户失败", e);  // 包装后重新抛出
        }
    }

    // 修复B：保留异常链，声明具体异常类型
    public void deleteUser(int userId) {
        try {
            User user = findUser(userId);
            database.delete(user);
            System.out.println("删除成功");
        } catch (ServiceException e) {
            log.error("删除用户失败，userId={}", userId, e);
            throw new ServiceException("删除用户失败，userId=" + userId, e);  // 传入 cause
        }
    }

    // 修复C和D：使用 try-with-resources，异常时记录日志并抛出
    public String readConfig(String path) throws ConfigException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        } catch (FileNotFoundException e) {
            throw new ConfigException("配置文件不存在：" + path, e);
        } catch (IOException e) {
            log.error("读取配置文件失败：{}", path, e);
            throw new ConfigException("读取配置文件失败：" + path, e);
        }
    }
}
```

---

## Q4（概念）：自定义异常设计

**题目**：你需要为一个支付系统设计异常体系。以下是需求：
- 支付失败（通用）
- 余额不足（需携带当前余额和所需金额）
- 支付超时（需携带超时毫秒数）
- 账户被冻结

请设计异常类的继承结构，并实现其中两个。说明为什么选择继承 `RuntimeException` 而不是 `Exception`。

**参考答案**：

**异常继承结构**：

```
RuntimeException
└── PaymentException（支付异常基类）
    ├── InsufficientBalanceException（余额不足）
    ├── PaymentTimeoutException（支付超时）
    └── AccountFrozenException（账户被冻结）
```

**实现**：

```java
// 基类
public class PaymentException extends RuntimeException {
    private final String errorCode;

    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}

// 余额不足
public class InsufficientBalanceException extends PaymentException {
    private final double currentBalance;
    private final double requiredAmount;

    public InsufficientBalanceException(double currentBalance, double requiredAmount) {
        super("PAY_001",
              String.format("余额不足：当前 %.2f 元，需要 %.2f 元", currentBalance, requiredAmount));
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public double getCurrentBalance() { return currentBalance; }
    public double getRequiredAmount() { return requiredAmount; }
}

// 支付超时
public class PaymentTimeoutException extends PaymentException {
    private final long timeoutMs;

    public PaymentTimeoutException(long timeoutMs) {
        super("PAY_002", "支付超时：等待 " + timeoutMs + " ms 后超时");
        this.timeoutMs = timeoutMs;
    }

    public long getTimeoutMs() { return timeoutMs; }
}
```

**为什么选择 RuntimeException**：

1. 支付异常是业务异常，通常在 Controller 层统一捕获处理（如返回错误响应给前端），不需要每个调用方单独 catch。
2. 使用 checked 异常（继承 Exception）会强制所有调用链上的方法都声明 `throws PaymentException`，产生大量样板代码。
3. 调用方如果不想处理，用 checked 异常会被编译器强制要求声明或捕获，很容易导致 `catch (Exception e) {}` 静默吞掉，反而更危险。
4. 现代框架（Spring、Hibernate）均采用 unchecked 异常策略。

---

## Q5（项目应用）：异常处理全链路设计

**题目**：在一个 Web 应用中，Controller 调用 Service，Service 调用 Repository（数据库访问层）。请描述异常应该在哪一层处理，并写出一个完整的异常流转示例（Repository 抛 SQLException，逐层包装，最终在 Controller 返回友好错误信息）。

**参考答案**：

**各层职责**：

| 层次 | 职责 |
|------|------|
| Repository 层 | 捕获 `SQLException`，包装为 `DataAccessException`（技术异常） |
| Service 层 | 捕获 `DataAccessException`，包装为业务异常（如 `UserNotFoundException`）；或者不捕获，让异常自然传播 |
| Controller 层 | 捕获所有业务异常，转换为 HTTP 响应（错误码 + 错误信息） |

**完整代码示例**：

```java
// Repository 层：处理技术细节
public class UserRepository {
    public User findById(int id) {
        try {
            // 数据库查询
            ResultSet rs = statement.executeQuery("SELECT * FROM users WHERE id=" + id);
            if (!rs.next()) return null;
            return mapToUser(rs);
        } catch (SQLException e) {
            // 将技术异常包装，上层不需要知道 SQL 细节
            throw new DataAccessException("查询用户失败，id=" + id, e);
        }
    }
}

// Service 层：处理业务逻辑
public class UserService {
    private final UserRepository repo;

    public User getUser(int id) {
        User user = repo.findById(id);  // DataAccessException 自然传播
        if (user == null) {
            throw new UserNotFoundException("用户不存在，id=" + id);
        }
        return user;
    }
}

// Controller 层：统一处理，转为 HTTP 响应
public class UserController {
    private final UserService service;

    public Response getUser(int id) {
        try {
            User user = service.getUser(id);
            return Response.ok(user);
        } catch (UserNotFoundException e) {
            return Response.error(404, e.getMessage());
        } catch (DataAccessException e) {
            // 数据库错误：记录详细日志（包含堆栈），返回通用错误信息（不暴露内部细节）
            log.error("查询用户时数据库异常，id={}", id, e);
            return Response.error(500, "服务暂时不可用，请稍后重试");
        }
    }
}
```

**关键设计原则**：
1. 底层包装异常时保留 cause（异常链），方便排查
2. Controller 层给用户的错误信息不暴露内部实现细节（如 SQL 错误信息）
3. 数据库等系统异常一定要记录完整日志（含堆栈），业务异常可以只记录警告级别
4. 异常在有能力处理的地方捕获，不能处理就向上传播
