# Chapter 17 Stream API - 项目任务

## 业务背景

你是某电商平台的后端开发工程师，产品经理要求你开发一个**数据报表生成器**，对平台订单数据进行多维度统计分析，辅助运营团队做决策。

数据量：模拟 1000+ 条订单记录，需用 Stream API 高效处理，禁止使用手写 for 循环。

---

## 数据模型

```java
import java.time.LocalDate;

public class Order {
    private Long orderId;
    private Long userId;
    private String productName;
    private Double amount;
    private String status;       // PAID / PENDING / REFUNDED / CANCELLED
    private LocalDate createDate;

    // 构造器、getter、setter、toString 省略（自行补全）

    public Order(Long orderId, Long userId, String productName,
                 Double amount, String status, LocalDate createDate) {
        this.orderId = orderId;
        this.userId = userId;
        this.productName = productName;
        this.amount = amount;
        this.status = status;
        this.createDate = createDate;
    }

    // getters
    public Long getOrderId()        { return orderId; }
    public Long getUserId()         { return userId; }
    public String getProductName()  { return productName; }
    public Double getAmount()       { return amount; }
    public String getStatus()       { return status; }
    public LocalDate getCreateDate(){ return createDate; }
}
```

---

## 测试数据准备

在 `main` 方法中初始化以下测试数据（至少 15 条）：

```java
List<Order> orders = Arrays.asList(
    new Order(1L,  101L, "iPhone 15",    5999.0, "PAID",      LocalDate.of(2024, 1, 15)),
    new Order(2L,  101L, "AirPods",      1299.0, "PAID",      LocalDate.of(2024, 1, 28)),
    new Order(3L,  102L, "MacBook Pro",  14999.0,"PAID",      LocalDate.of(2024, 2, 3)),
    new Order(4L,  103L, "小米14",       3999.0, "REFUNDED",  LocalDate.of(2024, 2, 14)),
    new Order(5L,  102L, "iPad",         4599.0, "PAID",      LocalDate.of(2024, 2, 20)),
    new Order(6L,  104L, "华为Mate60",   6999.0, "PAID",      LocalDate.of(2024, 3, 5)),
    new Order(7L,  101L, "键盘",         899.0,  "PAID",      LocalDate.of(2024, 3, 12)),
    new Order(8L,  105L, "联想笔记本",   7999.0, "CANCELLED", LocalDate.of(2024, 3, 18)),
    new Order(9L,  103L, "戴尔显示器",   2999.0, "PAID",      LocalDate.of(2024, 4, 1)),
    new Order(10L, 104L, "机械键盘",     599.0,  "PAID",      LocalDate.of(2024, 4, 8)),
    new Order(11L, 105L, "索尼耳机",     2499.0, "PAID",      LocalDate.of(2024, 4, 15)),
    new Order(12L, 102L, "充电宝",       199.0,  "REFUNDED",  LocalDate.of(2024, 5, 2)),
    new Order(13L, 106L, "手机壳",       59.0,   "PAID",      LocalDate.of(2024, 5, 10)),
    new Order(14L, 106L, "钢化膜",       29.0,   "PAID",      LocalDate.of(2024, 5, 16)),
    new Order(15L, 107L, "Switch",       2399.0, "PAID",      LocalDate.of(2024, 6, 1)),
    new Order(16L, 107L, "游戏手柄",     399.0,  "CANCELLED", LocalDate.of(2024, 6, 5)),
    new Order(17L, 101L, "显卡",         3999.0, "PAID",      LocalDate.of(2024, 6, 20)),
    new Order(18L, 103L, "路由器",       399.0,  "REFUNDED",  LocalDate.of(2024, 7, 3))
);
```

---

## 任务要求

### 任务1：统计各状态的订单数量 `Map<String, Long>`

实现方法：
```java
public static Map<String, Long> countByStatus(List<Order> orders) {
    // TODO: 使用 groupingBy + counting
}
```

期望输出格式：
```
PAID=12, REFUNDED=3, CANCELLED=2, PENDING=0
```

### 任务2：找出消费最高的前5名用户 `List<Long>`（userId 列表）

实现方法：
```java
public static List<Long> top5SpenderUserIds(List<Order> orders) {
    // TODO:
    // 1. 按 userId 分组，统计每人总消费（只统计 PAID 状态）
    // 2. 按总消费降序排序
    // 3. 取前5名的 userId
}
```

期望输出格式：
```
TOP5消费用户（userId）: [102, 101, 104, 103, 107]
```

### 任务3：按月汇总销售额 `Map<String, Double>`

月份格式为 `"yyyy-MM"`，例如 `"2024-01"`。只统计 PAID 状态的订单。

实现方法：
```java
public static Map<String, Double> monthlySales(List<Order> orders) {
    // TODO:
    // 1. 过滤 PAID 状态
    // 2. 按月份字符串分组（使用 DateTimeFormatter）
    // 3. 对每组金额求和
    // 注意：返回的 Map 按月份排序（使用 TreeMap）
}
```

期望输出格式：
```
2024-01: 7298.0
2024-02: 19598.0
...
```

### 任务4：找出从未有退款的用户列表 `List<Long>`

"从未有退款"：该用户在所有订单中，没有任何一条 status = "REFUNDED"。

实现方法：
```java
public static List<Long> usersWithNoRefund(List<Order> orders) {
    // TODO:
    // 思路：先找出有退款的 userId 集合，再从所有用户中过滤掉
}
```

---

## 完整代码骨架

```java
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

public class OrderReportGenerator {

    // Order 类（如上定义）

    public static Map<String, Long> countByStatus(List<Order> orders) {
        // TODO
        return null;
    }

    public static List<Long> top5SpenderUserIds(List<Order> orders) {
        // TODO
        return null;
    }

    public static Map<String, Double> monthlySales(List<Order> orders) {
        // TODO
        return null;
    }

    public static List<Long> usersWithNoRefund(List<Order> orders) {
        // TODO
        return null;
    }

    public static void main(String[] args) {
        List<Order> orders = /* 初始化测试数据 */;

        System.out.println("=== 任务1：各状态订单数量 ===");
        System.out.println(countByStatus(orders));

        System.out.println("\n=== 任务2：消费TOP5用户 ===");
        System.out.println(top5SpenderUserIds(orders));

        System.out.println("\n=== 任务3：按月销售额 ===");
        monthlySales(orders).forEach((month, sales) ->
            System.out.printf("%s: %.1f%n", month, sales));

        System.out.println("\n=== 任务4：从未退款的用户 ===");
        System.out.println(usersWithNoRefund(orders));
    }
}
```

---

## 参考答案

<details>
<summary>点击展开参考答案（建议先自行实现）</summary>

```java
// 任务1
public static Map<String, Long> countByStatus(List<Order> orders) {
    return orders.stream()
        .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
}

// 任务2
public static List<Long> top5SpenderUserIds(List<Order> orders) {
    return orders.stream()
        .filter(o -> "PAID".equals(o.getStatus()))
        .collect(Collectors.groupingBy(
            Order::getUserId,
            Collectors.summingDouble(Order::getAmount)
        ))
        .entrySet().stream()
        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
        .limit(5)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
}

// 任务3
public static Map<String, Double> monthlySales(List<Order> orders) {
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
    return orders.stream()
        .filter(o -> "PAID".equals(o.getStatus()))
        .collect(Collectors.groupingBy(
            o -> o.getCreateDate().format(fmt),
            TreeMap::new,
            Collectors.summingDouble(Order::getAmount)
        ));
}

// 任务4
public static List<Long> usersWithNoRefund(List<Order> orders) {
    Set<Long> refundedUsers = orders.stream()
        .filter(o -> "REFUNDED".equals(o.getStatus()))
        .map(Order::getUserId)
        .collect(Collectors.toSet());

    return orders.stream()
        .map(Order::getUserId)
        .distinct()
        .filter(uid -> !refundedUsers.contains(uid))
        .sorted()
        .collect(Collectors.toList());
}
```

</details>

---

## 验收标准

1. **任务1**：`countByStatus` 返回正确的 `Map<String, Long>`，所有出现过的状态都有对应的 key 和数量。
2. **任务2**：`top5SpenderUserIds` 只统计 PAID 状态订单，按总金额降序，最多返回5个 userId；数据不足5人时返回全部。
3. **任务3**：`monthlySales` 返回 `TreeMap`（按月份字符串自然排序），月份格式为 `"yyyy-MM"`，金额精确。
4. **任务4**：`usersWithNoRefund` 正确排除所有有过退款记录的用户，包括该用户同时有 PAID 订单的情况。

---

## 常见坑

**坑1：toMap 遇到重复 key 会抛 IllegalStateException**
```java
// 错误：如果同一 userId 有多条订单，toMap 会抛异常
Map<Long, Double> wrong = orders.stream()
    .collect(Collectors.toMap(Order::getUserId, Order::getAmount)); // 异常！

// 正确：提供 mergeFunction
Map<Long, Double> correct = orders.stream()
    .collect(Collectors.toMap(
        Order::getUserId,
        Order::getAmount,
        Double::sum            // 重复 key 时合并
    ));
```

**坑2：entrySet().stream() 排序后要重新收集**
```java
// 错误：对 Map 直接排序，结果没有保持顺序（HashMap 无序）
orders.stream()
    .collect(Collectors.groupingBy(...))
    .entrySet()
    .stream()
    .sorted(...)
    // 直接调用 forEach 是可以的，但如果 collect 成 HashMap 又会无序

// 正确：排序后 collect 到 LinkedHashMap 或 TreeMap
.collect(Collectors.toMap(
    Map.Entry::getKey,
    Map.Entry::getValue,
    (a, b) -> a,
    LinkedHashMap::new  // 保持插入顺序
));
```

**坑3：Stream 操作后忘记终端操作，结果为 null 或未执行**
```java
// 错误：忘记 collect，返回的是 Stream 引用
Stream<Long> userIds = orders.stream().map(Order::getUserId); // 没有执行！

// 正确：
List<Long> userIdList = orders.stream()
    .map(Order::getUserId)
    .collect(Collectors.toList()); // 终端操作触发执行
```
