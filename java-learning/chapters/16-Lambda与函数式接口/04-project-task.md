# Lambda 与函数式接口 项目任务

## 业务背景

你正在开发一个电商平台的**订单处理服务**。每天需要处理大量订单数据，包括：按条件筛选订单、计算折扣价格、将订单数据转换为不同格式、记录处理日志。

当前代码中充斥着大量 if-else 和匿名内部类，维护困难。你的任务是用 Lambda 和函数式接口对核心处理逻辑进行重构。

---

## 技术要求

1. **四大函数式接口**：`Predicate`（订单过滤）、`Function`（价格/格式转换）、`Consumer`（日志记录）、`Supplier`（默认值提供）至少各用一次
2. **方法引用**：代码中至少使用 3 处方法引用（不同种类）
3. **自定义函数式接口**：设计一个 `@FunctionalInterface` 接口 `OrderProcessor`，支持 `andThen` 链式组合
4. **策略模式**：用 Lambda + Map 替代 if-else 实现折扣策略的选择

---

## 任务说明

### 数据模型

```java
public class Order {
    private String orderId;
    private String customerId;
    private String customerType;  // "REGULAR", "VIP", "SUPER_VIP"
    private double amount;
    private String status;        // "PENDING", "PAID", "CANCELLED"
    private boolean hasCoupon;

    // 构造器、getter、setter、toString 省略（自行补充）
}
```

### 功能 1：订单过滤器

实现 `OrderFilter` 工具类，使用 `Predicate` 组合多种过滤条件：

```java
// 期望用法
List<Order> pendingVipOrders = OrderFilter.filter(
    orders,
    OrderFilter.isPending().and(OrderFilter.isVip()).and(OrderFilter.amountGreaterThan(100))
);
```

### 功能 2：折扣计算策略

用 `Map<String, Function<Double, Double>>` 注册折扣策略，根据 `customerType` 选择对应策略：

```java
// 输入：Order{customerId="C001", customerType="VIP", amount=300.0}
// 输出：VIP 折后价 = 270.0（9折）
```

策略表：
| customerType | 折扣规则 |
|---|---|
| REGULAR | 原价 |
| VIP | 9折 |
| SUPER_VIP | 8折，且满300减50 |
| hasCoupon=true | 再减20元（叠加） |

### 功能 3：订单处理管道

实现自定义 `@FunctionalInterface OrderProcessor`，支持链式处理：

```java
// 期望用法
OrderProcessor pipeline = OrderProcessor.validate()       // 校验订单不为null
    .then(OrderProcessor.applyDiscount(discountStrategies))  // 应用折扣
    .then(OrderProcessor.log(System.out::println));          // 记录日志

pipeline.process(order);
```

### 功能 4：批量格式转换

用 `Function` 将 `Order` 对象转换为两种格式：
- 转换为 `Map<String, Object>`（用于 JSON 序列化）
- 转换为 CSV 行字符串 `"orderId,customerId,amount,status"`

---

## 验收标准

- [ ] `OrderFilter` 类中所有过滤方法返回 `Predicate<Order>`，支持 `.and()/.or()/.negate()` 组合
- [ ] 折扣策略通过 `Map<String, Function<Double, Double>>` 注册，没有任何 if-else 或 switch
- [ ] `OrderProcessor` 是 `@FunctionalInterface`，有 `then(OrderProcessor)` 默认方法
- [ ] 代码中至少有 3 处方法引用，且类型各不相同
- [ ] 有 `main` 方法或单元测试，能实际运行并打印出处理结果

---

## 提示（不是答案）

- `Predicate<Order>` 的工厂方法可以定义在 `OrderFilter` 的静态方法中，如 `static Predicate<Order> isPending() { return order -> "PENDING".equals(order.getStatus()); }`
- 折扣策略 Map 在应用层级，先查 customerType 的基础策略，再判断 hasCoupon 叠加——可以考虑 `Function.andThen()`
- `OrderProcessor` 的 `then` 方法：`default OrderProcessor then(OrderProcessor next) { return order -> next.process(this.process(order)); }`
- 记录日志可以用 `Consumer<Order>`，但 `OrderProcessor` 需要有返回值（返回处理后的 order），思考两者怎么结合

---

## 常见坑

### 坑 1：Predicate 组合时忘记每次都新建
```java
// 错误：这样写 p 被复用，andThen 产生新对象没有被接收
Predicate<Order> p = OrderFilter.isPending();
p.and(OrderFilter.isVip());  // 这行没有意义！and() 返回新 Predicate 但没有赋值
p.test(order);  // p 还是原来的 isPending，没有 isVip 的过滤

// 正确：
Predicate<Order> p = OrderFilter.isPending().and(OrderFilter.isVip());
```

### 坑 2：Function<Double, Double> 用基本类型时的装箱开销
```java
// 数据量大时，Function<Double, Double> 有自动装箱开销
// 如果是性能敏感的批处理，考虑使用 DoubleUnaryOperator
DoubleUnaryOperator vipDiscount = price -> price * 0.9;
double result = vipDiscount.applyAsDouble(300.0);  // 无装箱
```

### 坑 3：Lambda 中修改 Order 字段时线程安全问题
```java
// 在并行流或多线程场景下，Lambda 中修改共享 Order 对象不是线程安全的
// 建议 OrderProcessor 返回新对象，而不是修改原对象（不可变对象设计）
```
