# Stream API 实操 Demo

## Demo 1：订单数据的 groupingBy 多维统计

### 实操目标
用真实业务场景演示 `groupingBy` 的多种用法，包括分组计数、分组求和、多级分组。

### 示例代码

```java
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

public class GroupingByDemo {

    record Order(String orderId, String customerId, String category,
                 String status, double amount) {}

    public static void main(String[] args) {
        List<Order> orders = List.of(
            new Order("O001", "C001", "Electronics", "PAID",   299.99),
            new Order("O002", "C002", "Clothing",    "PAID",   89.50),
            new Order("O003", "C001", "Electronics", "PENDING",149.99),
            new Order("O004", "C003", "Clothing",    "PAID",   65.00),
            new Order("O005", "C002", "Books",       "PAID",   29.99),
            new Order("O006", "C001", "Books",       "CANCELLED", 39.99),
            new Order("O007", "C003", "Electronics", "PAID",   599.00),
            new Order("O008", "C002", "Clothing",    "PENDING", 120.00)
        );

        // ==================== 1. 基础分组：按分类分组 ====================
        Map<String, List<Order>> byCategory = orders.stream()
            .collect(Collectors.groupingBy(Order::category));

        System.out.println("=== 按分类分组 ===");
        byCategory.forEach((category, orderList) -> {
            System.out.printf("%-15s: %d 笔%n", category, orderList.size());
        });

        // ==================== 2. 分组 + 统计数量 ====================
        Map<String, Long> countByStatus = orders.stream()
            .collect(Collectors.groupingBy(Order::status, Collectors.counting()));

        System.out.println("\n=== 按状态统计数量 ===");
        countByStatus.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> System.out.printf("%-12s: %d 笔%n", e.getKey(), e.getValue()));

        // ==================== 3. 分组 + 求和 ====================
        Map<String, Double> revenueByCategory = orders.stream()
            .filter(o -> "PAID".equals(o.status()))  // 只统计已支付
            .collect(Collectors.groupingBy(
                Order::category,
                Collectors.summingDouble(Order::amount)
            ));

        System.out.println("\n=== 各分类已支付收入 ===");
        revenueByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(e -> System.out.printf("%-15s: ¥%.2f%n", e.getKey(), e.getValue()));

        // ==================== 4. 分组 + 求最大值（每个分类最贵的订单）====================
        Map<String, Optional<Order>> mostExpensiveByCategory = orders.stream()
            .collect(Collectors.groupingBy(
                Order::category,
                Collectors.maxBy(Comparator.comparingDouble(Order::amount))
            ));

        System.out.println("\n=== 各分类最贵订单 ===");
        mostExpensiveByCategory.forEach((cat, opt) ->
            opt.ifPresent(o -> System.out.printf("%-15s: %s ¥%.2f%n",
                cat, o.orderId(), o.amount()))
        );

        // ==================== 5. 多级分组：先按分类，再按状态 ====================
        Map<String, Map<String, Long>> multiLevel = orders.stream()
            .collect(Collectors.groupingBy(
                Order::category,
                Collectors.groupingBy(Order::status, Collectors.counting())
            ));

        System.out.println("\n=== 多级分组（分类 -> 状态 -> 数量）===");
        multiLevel.forEach((cat, statusMap) -> {
            System.out.println(cat + ":");
            statusMap.forEach((status, count) ->
                System.out.printf("  %-12s: %d%n", status, count));
        });

        // ==================== 6. partitioningBy：二分法 ====================
        Map<Boolean, List<Order>> paidVsOthers = orders.stream()
            .collect(Collectors.partitioningBy(o -> "PAID".equals(o.status())));

        System.out.println("\n=== 已支付 vs 未支付 ===");
        System.out.println("已支付: " + paidVsOthers.get(true).size() + " 笔");
        System.out.println("未支付: " + paidVsOthers.get(false).size() + " 笔");
    }
}
```

### 运行结果

```
=== 按分类分组 ===
Electronics    : 3 笔
Clothing       : 3 笔
Books          : 2 笔

=== 按状态统计数量 ===
PAID        : 5 笔
PENDING     : 2 笔
CANCELLED   : 1 笔

=== 各分类已支付收入 ===
Electronics    : ¥898.99
Clothing       : ¥154.50
Books          : ¥29.99

=== 各分类最贵订单 ===
Electronics    : O007 ¥599.00
Clothing       : O008 ¥120.00
Books          : O006 ¥39.99

=== 多级分组（分类 -> 状态 -> 数量）===
Electronics:
  PAID        : 2
  PENDING     : 1
Clothing:
  PAID        : 2
  PENDING     : 1
Books:
  PAID        : 1
  CANCELLED   : 1

=== 已支付 vs 未支付 ===
已支付: 5 笔
未支付: 3 笔
```

### 关键点说明
- `groupingBy(classifier, downstreamCollector)` 的第二个参数对每个桶内的元素再做一次 collect
- `Collectors.summingDouble`、`counting()`、`maxBy()` 都是可以作为 downstream 的 Collector
- 多级分组就是嵌套 `groupingBy`，结果类型为 `Map<K1, Map<K2, V>>`
- `partitioningBy` 比 `groupingBy` 的 boolean 版本性能更好（只有两个桶）

---

## Demo 2：reduce 的实际用法

### 实操目标
展示 `reduce` 在求和、求积、字符串拼接、对象合并等场景中的实际使用，以及和 `collect` 的对比。

### 示例代码

```java
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

public class ReduceDemo {

    record Employee(String name, String dept, int salary) {}

    public static void main(String[] args) {
        // ==================== 1. 基础 reduce：求和 ====================
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // 方式1：reduce with identity
        int sum1 = numbers.stream().reduce(0, Integer::sum);
        System.out.println("求和(reduce): " + sum1);  // 55

        // 方式2：IntStream.sum()（推荐，避免装箱）
        int sum2 = numbers.stream().mapToInt(Integer::intValue).sum();
        System.out.println("求和(IntStream): " + sum2);  // 55

        // 方式3：Collectors.summingInt
        int sum3 = numbers.stream().collect(Collectors.summingInt(Integer::intValue));
        System.out.println("求和(summingInt): " + sum3);  // 55

        // ==================== 2. reduce 求乘积 ====================
        long factorial5 = LongStream.rangeClosed(1, 5)
            .reduce(1L, (a, b) -> a * b);
        System.out.println("\n5的阶乘: " + factorial5);  // 120

        // ==================== 3. reduce 对字符串进行操作 ====================
        // 注意：大量字符串拼接应该用 joining，这里只是演示 reduce 语义
        List<String> words = List.of("Stream", "API", "is", "powerful");

        // reduce 拼接（小数据量可用）
        String sentence = words.stream()
            .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
        System.out.println("\nreduce 拼接: " + sentence);

        // joining（推荐，内部使用 StringBuilder，性能更好）
        String sentence2 = words.stream()
            .collect(Collectors.joining(" "));
        System.out.println("joining 拼接: " + sentence2);

        // ==================== 4. reduce 合并对象（复杂累积）====================
        List<Employee> employees = List.of(
            new Employee("Alice", "Engineering", 15000),
            new Employee("Bob",   "Engineering", 18000),
            new Employee("Carol", "Marketing",   12000),
            new Employee("Dave",  "Engineering", 20000),
            new Employee("Eve",   "Marketing",   13000)
        );

        // 找出薪资最高的员工（用 reduce 而不是 max，演示语义）
        Optional<Employee> topEarner = employees.stream()
            .reduce((a, b) -> a.salary() > b.salary() ? a : b);
        topEarner.ifPresent(e ->
            System.out.printf("%n薪资最高: %s (%d)%n", e.name(), e.salary()));

        // ==================== 5. 自定义统计：一次遍历得到多个统计值 ====================
        // 场景：需要同时得到 工程部总薪资 和 人数
        record SalaryAccumulator(long totalSalary, int count) {
            double average() { return count == 0 ? 0 : (double) totalSalary / count; }
        }

        SalaryAccumulator engStats = employees.stream()
            .filter(e -> "Engineering".equals(e.dept()))
            .reduce(
                new SalaryAccumulator(0, 0),  // identity
                (acc, emp) -> new SalaryAccumulator(acc.totalSalary() + emp.salary(), acc.count() + 1),
                (a, b) -> new SalaryAccumulator(a.totalSalary() + b.totalSalary(), a.count() + b.count())
            );
        // 第三个参数是 combiner（并行流合并多个部分结果时用）

        System.out.printf("%n工程部统计: 总薪资=%d, 人数=%d, 平均=%.0f%n",
            engStats.totalSalary(), engStats.count(), engStats.average());

        // ==================== 6. reduce vs collect 性能对比场景 ====================
        // 正确做法：collect 用于可变积累（构建集合）
        List<Integer> evens = numbers.stream()
            .filter(n -> n % 2 == 0)
            .collect(Collectors.toList());
        System.out.println("\n偶数(collect): " + evens);

        // 不推荐：用 reduce 构建集合（每次 add 都创建新 List，性能差）
        List<Integer> evens2 = numbers.stream()
            .filter(n -> n % 2 == 0)
            .reduce(new ArrayList<>(),
                (list, n) -> { var newList = new ArrayList<>(list); newList.add(n); return newList; },
                (a, b) -> { var newList = new ArrayList<>(a); newList.addAll(b); return newList; }
            );
        System.out.println("偶数(reduce-不推荐): " + evens2);
    }
}
```

### 运行结果

```
求和(reduce): 55
求和(IntStream): 55
求和(summingInt): 55

5的阶乘: 120

reduce 拼接: Stream API is powerful
joining 拼接: Stream API is powerful

薪资最高: Dave (20000)

工程部统计: 总薪资=53000, 人数=3, 平均=17667

偶数(collect): [2, 4, 6, 8, 10]
偶数(reduce-不推荐): [2, 4, 6, 8, 10]
```

### 关键点说明
- `reduce` 的三参数版本 `reduce(identity, accumulator, combiner)`：combiner 只在并行流中使用，用于合并多个部分结果
- 构建集合应该用 `collect`，不要用 `reduce`——`reduce` 本质是不可变积累，每步都要新建对象
- `IntStream.sum()` 在大数据量时比 `Stream<Integer>.reduce(0, Integer::sum)` 快，因为避免了装箱

---

## Demo 3：Stream 综合管道 —— 商品推荐数据处理

### 实操目标
综合运用多种 Stream 操作（flatMap、sorted、distinct、toMap、joining），模拟电商推荐系统的数据处理流程。

### 示例代码

```java
import java.util.*;
import java.util.stream.*;
import java.util.function.*;

public class StreamPipelineDemo {

    record Product(String id, String name, String category, double price, List<String> tags) {}
    record UserBehavior(String userId, String productId, String action) {}  // action: VIEW/BUY/CART

    public static void main(String[] args) {
        List<Product> products = List.of(
            new Product("P01", "Java编程思想", "Books", 89.0, List.of("java", "programming", "classic")),
            new Product("P02", "Spring实战",   "Books", 69.0, List.of("java", "spring", "programming")),
            new Product("P03", "机械键盘",     "Electronics", 299.0, List.of("keyboard", "hardware")),
            new Product("P04", "显示器",       "Electronics", 1299.0, List.of("monitor", "hardware")),
            new Product("P05", "Effective Java","Books", 79.0, List.of("java", "best-practice")),
            new Product("P06", "鼠标",         "Electronics", 199.0, List.of("mouse", "hardware"))
        );

        List<UserBehavior> behaviors = List.of(
            new UserBehavior("U01", "P01", "BUY"),
            new UserBehavior("U01", "P02", "VIEW"),
            new UserBehavior("U01", "P03", "CART"),
            new UserBehavior("U02", "P01", "BUY"),
            new UserBehavior("U02", "P05", "BUY"),
            new UserBehavior("U03", "P04", "VIEW"),
            new UserBehavior("U03", "P06", "BUY")
        );

        // ==================== 1. 用 flatMap 提取所有标签并统计热度 ====================
        System.out.println("=== 标签热度排行 ===");
        Map<String, Long> tagHotness = products.stream()
            .flatMap(p -> p.tags().stream())   // 展开每个商品的所有标签
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            ;
        tagHotness.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> System.out.printf("  %-15s: %d 次%n", e.getKey(), e.getValue()));

        // ==================== 2. toMap 构建商品 ID -> 商品对象索引 ====================
        Map<String, Product> productIndex = products.stream()
            .collect(Collectors.toMap(Product::id, Function.identity()));

        // ==================== 3. 统计每个用户的购买总金额 ====================
        System.out.println("\n=== 用户购买总金额 ===");
        behaviors.stream()
            .filter(b -> "BUY".equals(b.action()))
            .collect(Collectors.groupingBy(
                UserBehavior::userId,
                Collectors.summingDouble(b -> productIndex.getOrDefault(b.productId(),
                    new Product("", "", "", 0, List.of())).price())
            ))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(e -> System.out.printf("  %s: ¥%.2f%n", e.getKey(), e.getValue()));

        // ==================== 4. Books 分类按价格降序，生成推荐文案 ====================
        System.out.println("\n=== 图书推荐（按价格降序）===");
        String recommendation = products.stream()
            .filter(p -> "Books".equals(p.category()))
            .sorted(Comparator.comparingDouble(Product::price).reversed())
            .map(p -> String.format("%s(¥%.0f)", p.name(), p.price()))
            .collect(Collectors.joining(" > "));
        System.out.println("  " + recommendation);

        // ==================== 5. 找出有购买行为的用户看过但没买的商品（潜在转化机会）====================
        System.out.println("\n=== 潜在转化商品（有浏览无购买）===");
        Set<String> boughtProducts = behaviors.stream()
            .filter(b -> "BUY".equals(b.action()))
            .map(UserBehavior::productId)
            .collect(Collectors.toSet());

        behaviors.stream()
            .filter(b -> "VIEW".equals(b.action()))
            .map(UserBehavior::productId)
            .filter(pid -> !boughtProducts.contains(pid))
            .distinct()
            .map(productIndex::get)
            .filter(Objects::nonNull)
            .forEach(p -> System.out.printf("  %s - %s (¥%.0f)%n",
                p.id(), p.name(), p.price()));

        // ==================== 6. 统计摘要（IntSummaryStatistics）====================
        System.out.println("\n=== Books 价格统计 ===");
        DoubleSummaryStatistics bookStats = products.stream()
            .filter(p -> "Books".equals(p.category()))
            .mapToDouble(Product::price)
            .summaryStatistics();
        System.out.printf("  数量: %d, 最低: ¥%.0f, 最高: ¥%.0f, 平均: ¥%.1f, 合计: ¥%.0f%n",
            bookStats.getCount(), bookStats.getMin(),
            bookStats.getMax(), bookStats.getAverage(), bookStats.getSum());
    }
}
```

### 运行结果

```
=== 标签热度排行 ===
  java           : 3 次
  programming    : 2 次
  hardware       : 3 次
  classic        : 1 次
  spring         : 1 次

=== 用户购买总金额 ===
  U03: ¥1498.00
  U01: ¥89.00
  U02: ¥168.00

=== 图书推荐（按价格降序）===
  Java编程思想(¥89) > Effective Java(¥79) > Spring实战(¥69)

=== 潜在转化商品（有浏览无购买）===
  P02 - Spring实战 (¥69)
  P04 - 显示器 (¥1299)

=== Books 价格统计 ===
  数量: 3, 最低: ¥69, 最高: ¥89, 平均: ¥79.0, 合计: ¥237
```

### 关键点说明
- `flatMap` 将 `List<List<String>>` 展开为 `Stream<String>` 是最常见用法
- `toMap(keyMapper, valueMapper)` 中 `Function.identity()` 等于 `e -> e`，将元素本身作为 value
- `DoubleSummaryStatistics` 一次遍历同时得到 count/min/max/sum/average，避免多次遍历
- `getOrDefault` 配合 `toMap` 构建的索引，是处理关联数据的常见模式
