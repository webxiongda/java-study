# Chapter 17 Stream API - 自测题

## Q1（概念）：Stream 的惰性求值是什么意思？中间操作和终端操作的区别？

**参考答案：**

**惰性求值（Lazy Evaluation）：**
Stream 的中间操作不会立即执行，只有在终端操作被调用时，整条 Pipeline 才会真正执行。这意味着如果没有终端操作，所有中间操作的代码都不会运行。

```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie", "David");

// 以下代码不会打印任何内容，因为没有终端操作
Stream<String> stream = names.stream()
    .filter(name -> {
        System.out.println("filtering: " + name); // 不会执行
        return name.startsWith("A");
    })
    .map(String::toUpperCase); // 不会执行

// 加上终端操作后才真正执行
stream.forEach(System.out::println); // 此时 filter 和 map 才执行
```

**中间操作（Intermediate Operations）：**
- 返回值仍然是 Stream
- 具有惰性，不会立即执行
- 可以链式调用多个
- 常见：filter、map、flatMap、distinct、sorted、limit、skip、peek

**终端操作（Terminal Operations）：**
- 返回非 Stream 类型（void、集合、Optional、基本类型等）
- 触发整条 Pipeline 的执行
- 一个 Stream 只能有一个终端操作，调用后 Stream 消费完毕不可再用
- 常见：forEach、collect、reduce、count、findFirst、anyMatch、toArray

**惰性求值的好处：**
- 短路优化：`findFirst()` 找到第一个后立即停止，不需要遍历全部元素
- 融合优化：JVM 可将多个中间操作合并为一次遍历

---

## Q2（概念）：collect(Collectors.groupingBy()) 的工作原理？

**参考答案：**

`Collectors.groupingBy()` 是一个下游收集器，核心思路是：**遍历 Stream 中的每个元素，按指定的分类函数（classifier）将元素分到不同的"桶"（bucket）中，最终生成 `Map<K, List<V>>`**。

**基本形式：**
```java
// 单参数：分类函数 → 返回 Map<K, List<T>>
Map<String, List<Order>> byStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::getStatus));

// 双参数：分类函数 + 下游收集器
Map<String, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,          // 分类函数
        Collectors.counting()      // 下游收集器：统计每组数量
    ));

// 三参数：分类函数 + Map工厂 + 下游收集器
Map<String, Long> sortedCountByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        TreeMap::new,              // 使用 TreeMap 保证 key 有序
        Collectors.counting()
    ));
```

**工作流程：**
1. Stream 中每个元素调用 classifier 函数得到 key
2. 以 key 为桶，将元素放入对应桶（默认 `ArrayList`）
3. 对每个桶应用下游收集器（默认是 `toList()`）
4. 返回最终 `Map<K, 下游收集结果>`

**常用下游收集器组合：**
```java
// 每组只保留金额字段
Map<String, List<Double>> amountsByStatus = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getStatus,
        Collectors.mapping(Order::getAmount, Collectors.toList())
    ));

// 每组求和
Map<Long, Double> totalByUser = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getUserId,
        Collectors.summingDouble(Order::getAmount)
    ));
```

---

## Q3（实操）：给定 List<Order>（含 userId、amount、status 字段），用 Stream 写出：统计每个用户的总消费金额（Map<Long, Double>）

**参考答案：**

```java
import java.util.*;
import java.util.stream.*;

public class OrderStats {

    static class Order {
        private Long userId;
        private Double amount;
        private String status;

        public Order(Long userId, Double amount, String status) {
            this.userId = userId;
            this.amount = amount;
            this.status = status;
        }

        public Long getUserId() { return userId; }
        public Double getAmount() { return amount; }
        public String getStatus() { return status; }
    }

    public static void main(String[] args) {
        List<Order> orders = Arrays.asList(
            new Order(1L, 100.0, "PAID"),
            new Order(1L, 200.0, "PAID"),
            new Order(2L, 150.0, "PAID"),
            new Order(2L, 50.0,  "REFUNDED"),
            new Order(3L, 300.0, "PAID")
        );

        // 方式1：groupingBy + summingDouble（推荐）
        Map<Long, Double> totalByUser = orders.stream()
            .collect(Collectors.groupingBy(
                Order::getUserId,
                Collectors.summingDouble(Order::getAmount)
            ));

        System.out.println(totalByUser);
        // {1=300.0, 2=200.0, 3=300.0}

        // 方式2：toMap + merge（适合理解原理）
        Map<Long, Double> totalByUser2 = orders.stream()
            .collect(Collectors.toMap(
                Order::getUserId,
                Order::getAmount,
                Double::sum           // mergeFunction：key 冲突时求和
            ));

        System.out.println(totalByUser2);

        // 扩展：只统计 PAID 状态的消费
        Map<Long, Double> paidTotalByUser = orders.stream()
            .filter(o -> "PAID".equals(o.getStatus()))
            .collect(Collectors.groupingBy(
                Order::getUserId,
                Collectors.summingDouble(Order::getAmount)
            ));

        System.out.println("PAID only: " + paidTotalByUser);
    }
}
```

**关键点：**
- `summingDouble` 比先 `toList` 再手动求和更简洁
- `toMap` 的第三个参数 `mergeFunction` 处理 key 冲突，不能省略（否则重复 key 会抛异常）

---

## Q4（实操）：找出以下 Stream 代码的问题并改正

**有问题的代码：**
```java
List<String> words = Arrays.asList("hello", "world", "java", "stream");

// 问题代码 1
Stream<String> s = words.stream();
s.filter(w -> w.length() > 4).forEach(System.out::println);
s.map(String::toUpperCase).forEach(System.out::println); // 第二次使用同一个 Stream

// 问题代码 2
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
int sum = 0;
numbers.stream().forEach(n -> sum += n); // 在 lambda 中修改外部变量

// 问题代码 3
List<List<Integer>> nested = Arrays.asList(
    Arrays.asList(1, 2, 3),
    Arrays.asList(4, 5, 6)
);
List<Integer> flat = nested.stream()
    .map(list -> list.stream())          // 应该用 flatMap
    .collect(Collectors.toList());
```

**参考答案（问题分析与改正）：**

**问题1：Stream 被重复消费**
```java
// 错误：Stream 一旦终端操作执行后就关闭，不能再次使用
// 改正：每次从 List 创建新的 Stream
words.stream()
    .filter(w -> w.length() > 4)
    .forEach(System.out::println);

words.stream()
    .map(String::toUpperCase)
    .forEach(System.out::println);
```

**问题2：Lambda 中修改外部局部变量（编译报错）**
```java
// 错误：lambda 中使用的外部变量必须是 effectively final，不能被修改
// 改正：使用 reduce 或 mapToInt().sum()
int sum = numbers.stream()
    .reduce(0, Integer::sum);
// 或
int sum2 = numbers.stream()
    .mapToInt(Integer::intValue)
    .sum();
```

**问题3：map 返回 Stream<Stream<Integer>> 而不是扁平化结果**
```java
// 错误：map 把每个 List 转成了 Stream，结果类型是 Stream<Stream<Integer>>
// 改正：使用 flatMap 进行扁平化
List<Integer> flat = nested.stream()
    .flatMap(List::stream)               // flatMap 自动展开内层 Stream
    .collect(Collectors.toList());
// 结果：[1, 2, 3, 4, 5, 6]
```

---

## Q5（项目应用）：用 Stream 实现对商品列表按类别分组，每组按价格排序，取前3名

**参考答案：**

```java
import java.util.*;
import java.util.stream.*;

public class ProductRanking {

    static class Product {
        private String name;
        private String category;
        private double price;

        public Product(String name, String category, double price) {
            this.name = name;
            this.category = category;
            this.price = price;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPrice() { return price; }

        @Override
        public String toString() {
            return name + "(" + price + ")";
        }
    }

    public static void main(String[] args) {
        List<Product> products = Arrays.asList(
            new Product("iPhone 15",   "手机", 5999.0),
            new Product("小米14",      "手机", 3999.0),
            new Product("华为Mate60",  "手机", 6999.0),
            new Product("OPPO Find X", "手机", 4999.0),
            new Product("MacBook Pro", "电脑", 14999.0),
            new Product("联想ThinkPad","电脑", 8999.0),
            new Product("华为MateBook","电脑", 7999.0),
            new Product("戴尔XPS",     "电脑", 12999.0),
            new Product("AirPods Pro", "耳机", 1999.0),
            new Product("索尼WH-1000", "耳机", 2499.0),
            new Product("华为FreeBuds","耳机", 999.0)
        );

        // 核心实现：按类别分组 → 每组按价格降序排序 → 取前3名
        Map<String, List<Product>> top3ByCategory = products.stream()
            .collect(Collectors.groupingBy(
                Product::getCategory,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> list.stream()
                        .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
                        .limit(3)
                        .collect(Collectors.toList())
                )
            ));

        // 输出结果
        top3ByCategory.forEach((category, topProducts) -> {
            System.out.println("【" + category + "】TOP3：");
            topProducts.forEach(p ->
                System.out.printf("  %s - %.0f元%n", p.getName(), p.getPrice())
            );
        });

        // 替代写法：先排序再 groupingBy（利用 LinkedHashMap 保持顺序）
        System.out.println("\n--- 替代写法 ---");
        Map<String, List<Product>> top3Alt = products.stream()
            .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
            .collect(Collectors.groupingBy(Product::getCategory))
            .entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().stream().limit(3).collect(Collectors.toList()),
                (a, b) -> a,
                LinkedHashMap::new
            ));

        top3Alt.forEach((cat, prods) ->
            System.out.println(cat + ": " + prods)
        );
    }
}
```

**输出示例：**
```
【手机】TOP3：
  华为Mate60 - 6999元
  iPhone 15 - 5999元
  OPPO Find X - 4999元
【电脑】TOP3：
  MacBook Pro - 14999元
  戴尔XPS - 12999元
  联想ThinkPad - 8999元
【耳机】TOP3：
  索尼WH-1000 - 2499元
  AirPods Pro - 1999元
  华为FreeBuds - 999元
```

**关键点：**
- `collectingAndThen`：先用一个收集器收集，再对结果做后处理
- `Comparator.comparingDouble(...).reversed()`：按价格降序
- 耳机只有3个，`limit(3)` 不会报错，会返回所有元素
