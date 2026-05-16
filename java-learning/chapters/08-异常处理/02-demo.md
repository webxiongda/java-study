# Chapter 08 - 代码演示

## Demo 1：try-catch-finally 执行顺序演示

重点演示两种特殊情况：正常带 return 时 finally 的执行时机，以及 finally 里有 return 时的危险覆盖行为。

```java
public class FinallyDemo {

    public static void main(String[] args) {
        System.out.println("=== 场景1：正常执行，try 中有 return ===");
        int result1 = scenario1();
        System.out.println("返回值：" + result1);
        // 输出：
        // try 块执行
        // finally 块执行（finally 在 return 之前执行）
        // 返回值：1

        System.out.println("\n=== 场景2：catch 中有 return ===");
        int result2 = scenario2();
        System.out.println("返回值：" + result2);
        // 输出：
        // try 块抛出异常
        // catch 块执行
        // finally 块执行
        // 返回值：2

        System.out.println("\n=== 场景3：finally 里有 return（危险！）===");
        int result3 = scenario3();
        System.out.println("返回值：" + result3);
        // 输出：
        // try 块执行，准备返回 1
        // finally 块执行，覆盖返回值为 99
        // 返回值：99   ← try 里的 return 1 被吞掉了！

        System.out.println("\n=== 场景4：finally 里抛出异常（危险！）===");
        try {
            scenario4();
        } catch (Exception e) {
            System.out.println("捕获到异常：" + e.getMessage());
            // 输出：捕获到异常：finally 中的异常
            // 原始的 "try 中的异常" 被覆盖，永远消失了！
        }
    }

    // 场景1：try 中有 return，finally 依然执行
    public static int scenario1() {
        try {
            System.out.println("try 块执行");
            return 1;  // ← 这里会先保存返回值 1，然后执行 finally，最后才真正返回
        } finally {
            System.out.println("finally 块执行（finally 在 return 之前执行）");
            // 注意：这里没有 return，所以 try 中的 return 1 生效
        }
    }

    // 场景2：抛出异常，catch 中有 return
    public static int scenario2() {
        try {
            System.out.println("try 块抛出异常");
            throw new RuntimeException("测试异常");
        } catch (RuntimeException e) {
            System.out.println("catch 块执行");
            return 2;
        } finally {
            System.out.println("finally 块执行");
            // 没有 return，catch 中的 return 2 生效
        }
    }

    // 场景3：finally 里有 return，覆盖 try 的返回值（不要这样写！）
    public static int scenario3() {
        try {
            System.out.println("try 块执行，准备返回 1");
            return 1;
        } finally {
            System.out.println("finally 块执行，覆盖返回值为 99");
            return 99;  // ← 危险！覆盖了 try 中的 return 1
        }
    }

    // 场景4：finally 里抛出新异常，覆盖原始异常（不要这样写！）
    public static void scenario4() {
        try {
            throw new RuntimeException("try 中的异常");
        } finally {
            // 这个新异常会覆盖上面的原始异常，原始信息永远丢失！
            throw new RuntimeException("finally 中的异常");
        }
    }
}
```

**核心结论**：
- finally 几乎一定执行（System.exit 除外）
- finally 在 return 之前执行，但不影响 try/catch 的返回值（除非 finally 里也有 return）
- 永远不要在 finally 里写 return 或抛出新异常

---

## Demo 2：自定义业务异常

模拟电商场景：用户转账时余额不足，抛出自定义业务异常 `InsufficientBalanceException`。

```java
// ===== 自定义异常类 =====

/**
 * 业务异常基类，所有业务异常继承此类
 * 继承 RuntimeException，无需调用方强制 catch
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 支持异常链：保留原始异常 cause
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "BusinessException{errorCode='" + errorCode + "', message='" + getMessage() + "'}";
    }
}

/**
 * 余额不足异常
 */
public class InsufficientBalanceException extends BusinessException {

    private final double currentBalance;
    private final double requiredAmount;

    public InsufficientBalanceException(double currentBalance, double requiredAmount) {
        super(
            "INSUFFICIENT_BALANCE",
            String.format("余额不足：当前余额 %.2f 元，需要 %.2f 元", currentBalance, requiredAmount)
        );
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public double getCurrentBalance() { return currentBalance; }
    public double getRequiredAmount() { return requiredAmount; }
}

// ===== 账户服务类 =====

public class AccountService {

    private double balance;
    private final String userId;

    public AccountService(String userId, double initialBalance) {
        this.userId = userId;
        this.balance = initialBalance;
    }

    /**
     * 转账操作
     * @param amount 转账金额
     * @throws InsufficientBalanceException 余额不足时抛出
     * @throws IllegalArgumentException     金额非正数时抛出
     */
    public void transfer(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("转账金额必须大于0，实际值：" + amount);
        }
        if (amount > balance) {
            throw new InsufficientBalanceException(balance, amount);
        }
        balance -= amount;
        System.out.println(String.format("转账成功：%.2f 元，剩余余额：%.2f 元", amount, balance));
    }

    public double getBalance() { return balance; }
}

// ===== 主程序演示 =====

public class BusinessExceptionDemo {

    public static void main(String[] args) {
        AccountService account = new AccountService("user001", 500.00);

        // 场景1：正常转账
        System.out.println("=== 场景1：正常转账 ===");
        try {
            account.transfer(200.00);
        } catch (BusinessException e) {
            System.out.println("业务异常：" + e.getMessage());
        }

        // 场景2：余额不足
        System.out.println("\n=== 场景2：余额不足 ===");
        try {
            account.transfer(400.00);  // 当前余额只有 300，不够
        } catch (InsufficientBalanceException e) {
            // 可以获取具体的余额信息，提供更友好的提示
            System.out.println("转账失败：" + e.getMessage());
            System.out.println("错误码：" + e.getErrorCode());
            System.out.println("当前余额：" + e.getCurrentBalance());
            System.out.println("需要金额：" + e.getRequiredAmount());
        } catch (BusinessException e) {
            // 兜底：处理其他业务异常
            System.out.println("业务异常：" + e.getMessage());
        }

        // 场景3：非法参数
        System.out.println("\n=== 场景3：非法参数 ===");
        try {
            account.transfer(-100);
        } catch (IllegalArgumentException e) {
            System.out.println("参数错误：" + e.getMessage());
        }
    }
}
```

**输出**：
```
=== 场景1：正常转账 ===
转账成功：200.00 元，剩余余额：300.00 元

=== 场景2：余额不足 ===
转账失败：余额不足：当前余额 300.00 元，需要 400.00 元
错误码：INSUFFICIENT_BALANCE
当前余额：300.0
需要金额：400.0

=== 场景3：非法参数 ===
参数错误：转账金额必须大于0，实际值：-100.0
```

---

## Demo 3：try-with-resources 读取文件

对比传统 finally 关闭流和 try-with-resources 的写法差异。

```java
import java.io.*;
import java.nio.file.*;

public class FileReadDemo {

    // ===== 传统写法（Java 6 及以前） =====
    public static String readFileTraditional(String filePath) throws IOException {
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();

        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (FileNotFoundException e) {
            throw new IOException("文件不存在：" + filePath, e);  // 保留异常链
        } catch (IOException e) {
            throw new IOException("读取文件失败：" + filePath, e);
        } finally {
            // 关闭资源：close() 本身也可能抛出 IOException，必须再套一层 try
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("关闭文件流失败：" + e.getMessage());
                    // 注意：这里如果再抛出异常，会覆盖上面 catch 中的异常！
                }
            }
        }
        return content.toString();
    }

    // ===== try-with-resources 写法（Java 7+，推荐） =====
    public static String readFileModern(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();

        // try() 括号内声明资源，退出 try 块后自动调用 close()
        // 多个资源时用分号分隔，关闭顺序与声明顺序相反
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (FileNotFoundException e) {
            throw new IOException("文件不存在：" + filePath, e);
        }
        // 不需要 finally！reader 自动关闭
        // 如果 try 块和 close() 都抛异常，close() 的异常作为"suppressed异常"附加到原始异常

        return content.toString();
    }

    // ===== 多资源 try-with-resources =====
    public static void copyFile(String sourcePath, String destPath) throws IOException {
        // 声明多个资源，用分号分隔
        // 关闭顺序：writer 先关，reader 后关（与声明顺序相反）
        try (
            BufferedReader reader = new BufferedReader(new FileReader(sourcePath));
            BufferedWriter writer = new BufferedWriter(new FileWriter(destPath))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
            System.out.println("文件复制成功：" + sourcePath + " -> " + destPath);
        }
    }

    // ===== 主程序 =====
    public static void main(String[] args) {
        // 创建临时测试文件
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("demo", ".txt");
            Files.writeString(tempFile, "第一行\n第二行\n第三行\n");

            System.out.println("=== 传统写法读取文件 ===");
            String content1 = readFileTraditional(tempFile.toString());
            System.out.println(content1);

            System.out.println("=== try-with-resources 读取文件 ===");
            String content2 = readFileModern(tempFile.toString());
            System.out.println(content2);

            System.out.println("=== 读取不存在的文件 ===");
            readFileModern("/nonexistent/path/file.txt");

        } catch (IOException e) {
            // 正确的异常日志方式：包含描述信息 + 完整异常对象
            System.err.println("操作失败：" + e.getMessage());
            // 在真实项目中应用 log.error("操作失败", e)

            // 演示异常链：查看 cause
            if (e.getCause() != null) {
                System.err.println("根本原因：" + e.getCause().getMessage());
            }
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    System.err.println("临时文件清理失败");
                }
            }
        }
    }
}
```

**三种写法对比**：

| 维度 | 传统 finally | try-with-resources |
|------|-------------|-------------------|
| 代码量 | 多（嵌套 try-catch） | 少（声明即管理） |
| 忘记关闭风险 | 高 | 无（自动关闭） |
| 两个异常时 | finally 异常覆盖原始异常 | close 异常作为 suppressed，原始异常不丢失 |
| 适用版本 | Java 所有版本 | Java 7+ |
| 推荐 | 不推荐（除非兼容老版本） | 强烈推荐 |
