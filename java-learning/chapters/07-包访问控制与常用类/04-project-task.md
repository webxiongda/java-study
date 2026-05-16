# Chapter 07 - 项目任务：日志工具类 LogUtils

## 业务背景

你正在参与一个企业级项目的开发，团队需要一个统一的日志工具类 `LogUtils`，供整个项目各模块调用。这个工具类需要处理时间格式化、手机号脱敏、文本截断等常见需求。由于多人共用，接口设计和访问控制必须规范，避免误用。

---

## 任务要求

实现 `LogUtils` 工具类，放在包 `com.example.util` 下，要求如下：

### 基本约束
- 类不可被实例化（使用 `private` 构造器）
- 所有方法均为静态方法
- 类上加注释说明用途和作者

### 需实现的方法

#### 1. `formatTimestamp()`
```
public static String formatTimestamp()
```
- 返回当前系统时间，格式为 `"yyyy-MM-dd HH:mm:ss"`
- 示例输出：`"2025-07-20 14:30:05"`

#### 2. `maskPhone(String phone)`
```
public static String maskPhone(String phone)
```
- 将手机号中间4位替换为 `****`
- 输入：`"13812345678"` → 输出：`"138****5678"`
- 边界处理：
  - `phone` 为 null 或空白 → 返回 `""`
  - 长度不足 11 位 → 返回原字符串（不做脱敏）

#### 3. `truncate(String text, int maxLen)`
```
public static String truncate(String text, int maxLen)
```
- 截断超长文本，超出 `maxLen` 个字符的部分替换为 `"..."`
- `text` 为 null → 返回 `""`
- `maxLen <= 0` → 抛出 `IllegalArgumentException`
- 未超出长度 → 原样返回

#### 4. `isBlank(String s)`
```
public static boolean isBlank(String s)
```
- `s` 为 null、空串、或仅含空白字符（空格、\t、\n 等）时返回 `true`
- 否则返回 `false`

---

## 参考结构

```
src/
└── com/
    └── example/
        └── util/
            └── LogUtils.java
```

测试代码（放在 `main` 方法或单独测试类中）：
```java
public class Main {
    public static void main(String[] args) {
        // 时间格式化
        System.out.println(LogUtils.formatTimestamp());
        // 输出示例：2025-07-20 14:30:05

        // 手机号脱敏
        System.out.println(LogUtils.maskPhone("13812345678"));
        // 输出：138****5678
        System.out.println(LogUtils.maskPhone(null));
        // 输出：（空字符串）
        System.out.println(LogUtils.maskPhone("1381234"));
        // 输出：1381234（不足11位，不脱敏）

        // 文本截断
        System.out.println(LogUtils.truncate("这是一段很长的日志消息内容", 6));
        // 输出：这是一段很长...
        System.out.println(LogUtils.truncate(null, 10));
        // 输出：（空字符串）

        // 空白判断
        System.out.println(LogUtils.isBlank("  "));  // true
        System.out.println(LogUtils.isBlank("log")); // false
    }
}
```

---

## 验收标准

1. **不可实例化**：`new LogUtils()` 应抛出 `UnsupportedOperationException`，而不是编译通过并成功创建对象。
2. **方法行为正确**：四个方法均通过上方所有边界用例，包括 null 输入、不足长度、超出长度等场景。
3. **访问控制规范**：工具方法为 `public static`，任何只在类内部使用的辅助逻辑声明为 `private static`，没有暴露不必要的实现细节。
4. **格式化器复用**：`DateTimeFormatter` 声明为 `private static final` 常量，不在每次方法调用时重复创建。

---

## 常见坑

**坑 1：忘记 null 检查导致 NullPointerException**

`maskPhone(null)` 若直接调用 `phone.length()` 会抛 NPE。应先用 `isBlank()` 或显式 null 检查处理边界。

```java
// 错误写法
public static String maskPhone(String phone) {
    return phone.substring(0, 3) + "****" + phone.substring(7); // NPE!
}

// 正确写法
public static String maskPhone(String phone) {
    if (isBlank(phone)) return "";
    if (phone.length() < 11) return phone;
    return phone.substring(0, 3) + "****" + phone.substring(7);
}
```

**坑 2：DateTimeFormatter 每次 new 造成性能浪费**

`DateTimeFormatter.ofPattern(...)` 是相对重量级的操作，应声明为常量：

```java
// 错误写法（每次调用都重新创建）
public static String formatTimestamp() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
}

// 正确写法
private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

public static String formatTimestamp() {
    return LocalDateTime.now().format(FORMATTER);
}
```

**坑 3：truncate 的 "..." 计入长度与否的歧义**

题目要求超出 `maxLen` 的部分替换为 `"..."`，即最终输出为 `前maxLen个字符 + "..."`，总长度是 `maxLen + 3`。不要把 `"..."` 的3个字符也计入 `maxLen` 中（那样会输出更短的内容）。确认需求后在代码注释中写清楚约定。

```java
// 示例：maxLen=6，输入10个字符的字符串
// 输出：前6个字符 + "..."，总共9个字符
```
