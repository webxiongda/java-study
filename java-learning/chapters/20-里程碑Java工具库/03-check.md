# Chapter 20 里程碑 - 综合自测题

> 本章自测题覆盖 Chapter 11-19 全部核心知识，难度较高，需要综合运用多个技术点。

---

## Q1（综合：泛型 + Stream + 集合）

**题目**：实现一个通用的 `TopN` 方法，接收 `List<T>`、比较器 `Comparator<T>` 和数量 `n`，返回按比较器排序后的前 N 个元素。要求：
1. 用泛型方法定义
2. 内部使用 Stream API 实现
3. n 超出列表长度时返回全部
4. 列表为 null 或空时返回空 List

用以下场景测试：找出价格最高的3个商品、找出年龄最小的2个用户。

**参考答案：**

```java
import java.util.*;
import java.util.stream.*;

public class TopNUtil {

    /**
     * 通用 TopN 方法
     */
    public static <T> List<T> topN(List<T> list, Comparator<T> comparator, int n) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        if (n <= 0) return Collections.emptyList();

        return list.stream()
            .sorted(comparator)          // 按比较器排序（调用方决定升降序）
            .limit(n)                    // 取前 N 个
            .collect(Collectors.toList());
    }

    // 数据类
    static class Product {
        String name; double price;
        Product(String name, double price) { this.name = name; this.price = price; }
        public String toString() { return name + "(" + price + ")"; }
    }

    static class User {
        String name; int age;
        User(String name, int age) { this.name = name; this.age = age; }
        public String toString() { return name + "(" + age + ")"; }
    }

    public static void main(String[] args) {
        List<Product> products = Arrays.asList(
            new Product("A", 5999), new Product("B", 14999),
            new Product("C", 1999), new Product("D", 3999),
            new Product("E", 9999)
        );

        // 价格最高的3个（降序取前3）
        List<Product> top3 = topN(products,
            Comparator.comparingDouble((Product p) -> p.price).reversed(), 3);
        System.out.println("价格最高3个：" + top3);
        // [B(14999.0), E(9999.0), A(5999.0)]

        List<User> users = Arrays.asList(
            new User("Alice", 25), new User("Bob", 30), new User("Carol", 22)
        );

        // 年龄最小的2个（升序取前2）
        List<User> youngest2 = topN(users, Comparator.comparingInt(u -> u.age), 2);
        System.out.println("年龄最小2个：" + youngest2);
        // [Carol(22), Alice(25)]

        // 边界：n 超过列表长度
        List<Product> all = topN(products, Comparator.comparingDouble(p -> p.price), 100);
        System.out.println("n 超过长度（期望5个）：" + all.size()); // 5
    }
}
```

**考察点：** 泛型方法、`Comparator.comparingDouble/Int`、`reversed()`、Stream `sorted + limit`、边界条件处理。

---

## Q2（综合：IO + Stream + Optional）

**题目**：读取 CSV 文件（使用 `CsvUtils.readCsv`），将数据转换为对象列表，再用 Stream 统计分析。

要求：
1. 读取 `products.csv`，将每行 Map 转换为 `Product` 对象（id/name/price/category）
2. 用 Stream 统计每个类别的商品数量和平均价格
3. 找出价格最高的商品，用 `Optional` 包裹返回
4. 若 CSV 文件不存在，用 `Optional.empty()` 处理，不崩溃

**参考答案：**

```java
import java.util.*;
import java.util.stream.*;
import com.miniutils.csv.CsvUtils;

public class CsvAnalysis {

    record Product(long id, String name, double price, String category) {}

    // 安全读取 CSV（文件不存在时返回 empty）
    static Optional<List<Map<String, String>>> safeReadCsv(String path) {
        try {
            return Optional.of(CsvUtils.readCsv(path));
        } catch (RuntimeException e) {
            System.err.println("CSV 读取失败：" + e.getMessage());
            return Optional.empty();
        }
    }

    // Map -> Product 转换
    static Product mapToProduct(Map<String, String> row) {
        return new Product(
            Long.parseLong(row.get("id")),
            row.get("name"),
            Double.parseDouble(row.get("price")),
            row.get("category")
        );
    }

    public static void main(String[] args) {
        // 1. 安全读取（Optional 处理文件不存在）
        List<Product> products = safeReadCsv("data/products.csv")
            .map(rows -> rows.stream().map(CsvAnalysis::mapToProduct).collect(Collectors.toList()))
            .orElseGet(() -> {
                System.out.println("使用默认空列表");
                return Collections.emptyList();
            });

        if (products.isEmpty()) {
            System.out.println("无数据");
            return;
        }

        // 2. 按类别统计数量和平均价格
        System.out.println("=== 按类别统计 ===");
        products.stream()
            .collect(Collectors.groupingBy(
                Product::category,
                Collectors.summarizingDouble(Product::price)
            ))
            .forEach((cat, stats) ->
                System.out.printf("%s：%d件，平均价格 %.0f 元%n",
                    cat, stats.getCount(), stats.getAverage()));

        // 3. 价格最高商品（Optional）
        Optional<Product> mostExpensive = products.stream()
            .max(Comparator.comparingDouble(Product::price));

        mostExpensive.ifPresentOrElse(
            p -> System.out.println("最贵商品：" + p.name() + "（" + p.price() + "元）"),
            () -> System.out.println("没有商品数据")
        );
    }
}
```

**考察点：** IO 异常处理、`Optional.map + orElseGet`、Stream `groupingBy + summarizingDouble`、`max + Optional`。

---

## Q3（综合：反射 + 注解 + Stream）

**题目**：扩展 Chapter 19 的 `SimpleJsonSerializer`，添加以下功能：
1. 支持 `@JsonField(ignore = true)` 跳过字段
2. 序列化 `List` 类型字段（输出为 JSON 数组 `[...]`）
3. 用 Stream 处理字段遍历（替代 for 循环）

**参考答案：**

```java
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@interface JsonField {
    String name() default "";
    boolean ignore() default false;
}

public class EnhancedSerializer {

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof List) return listToJson((List<?>) obj);  // 顶层 List

        return Arrays.stream(obj.getClass().getDeclaredFields())
            .filter(f -> !f.isSynthetic())
            .filter(f -> {
                // ignore=true 的字段过滤掉
                if (!f.isAnnotationPresent(JsonField.class)) return true;
                return !f.getAnnotation(JsonField.class).ignore();
            })
            .map(f -> {
                f.setAccessible(true);
                String key = Optional.of(f)
                    .filter(field -> field.isAnnotationPresent(JsonField.class))
                    .map(field -> field.getAnnotation(JsonField.class).name())
                    .filter(name -> !name.isEmpty())
                    .orElse(f.getName());
                try {
                    Object value = f.get(obj);
                    return "\"" + key + "\":" + formatValue(value);
                } catch (IllegalAccessException e) {
                    return "\"" + key + "\":null";
                }
            })
            .collect(Collectors.joining(",", "{", "}"));
    }

    static String listToJson(List<?> list) {
        return list.stream()
            .map(EnhancedSerializer::formatValue)
            .collect(Collectors.joining(",", "[", "]"));
    }

    static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + s.replace("\"", "\\\"") + "\"";
        if (value instanceof List)  return listToJson((List<?>) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return toJson(value);  // 嵌套对象递归
    }

    // 测试
    static class Order {
        @JsonField(name = "order_id")
        private Long orderId;
        @JsonField(ignore = true)
        private String internalNote;
        private List<String> items;

        Order(Long orderId, String internalNote, List<String> items) {
            this.orderId = orderId;
            this.internalNote = internalNote;
            this.items = items;
        }
    }

    public static void main(String[] args) {
        Order order = new Order(101L, "内部备注（不应出现）",
            Arrays.asList("iPhone 15", "AirPods Pro"));
        System.out.println(toJson(order));
        // 期望：{"order_id":101,"items":["iPhone 15","AirPods Pro"]}
        // internalNote 被 ignore=true 排除
    }
}
```

**考察点：** 反射 + 注解、`Arrays.stream(fields)`、Stream `filter + map + collect`、`Optional` 链式操作处理可选值、递归处理嵌套对象/List。

---

## Q4（综合：时间 API + Stream + 集合）

**题目**：给定一个订单列表（含 `createDate` 字段），实现以下统计：
1. 找出本月（以当前日期为准）的所有订单
2. 统计每周（周一到周日为一周）的订单数量
3. 找出订单数量最多的那一天（LocalDate）

**参考答案：**

```java
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.*;

public class OrderTimeAnalysis {

    record Order(Long id, double amount, LocalDate createDate) {}

    public static void main(String[] args) {
        List<Order> orders = Arrays.asList(
            new Order(1L, 100, LocalDate.of(2024, 5, 1)),
            new Order(2L, 200, LocalDate.of(2024, 5, 6)),
            new Order(3L, 300, LocalDate.of(2024, 5, 6)),
            new Order(4L, 150, LocalDate.of(2024, 5, 13)),
            new Order(5L, 250, LocalDate.of(2024, 5, 13)),
            new Order(6L, 180, LocalDate.of(2024, 5, 13)),
            new Order(7L, 400, LocalDate.of(2024, 4, 20)), // 上个月
            new Order(8L, 500, LocalDate.of(2024, 5, 20))
        );

        LocalDate today = LocalDate.of(2024, 5, 16); // 模拟当前日期

        // 1. 本月订单
        List<Order> thisMonth = orders.stream()
            .filter(o -> o.createDate().getYear() == today.getYear()
                      && o.createDate().getMonth() == today.getMonth())
            .collect(Collectors.toList());
        System.out.println("本月订单数：" + thisMonth.size()); // 6

        // 2. 每周订单数量（以 ISO 周为标准：周一到周日）
        Map<Integer, Long> weeklyCount = thisMonth.stream()
            .collect(Collectors.groupingBy(
                o -> o.createDate().get(WeekFields.ISO.weekOfWeekBasedYear()),
                Collectors.counting()
            ));
        weeklyCount.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> System.out.println("第" + e.getKey() + "周：" + e.getValue() + "单"));

        // 3. 订单数量最多的那一天
        Optional<Map.Entry<LocalDate, Long>> busiestDay = orders.stream()
            .collect(Collectors.groupingBy(Order::createDate, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue());

        busiestDay.ifPresent(e ->
            System.out.println("最忙的一天：" + e.getKey() + "（" + e.getValue() + "单）")
        );
        // 2024-05-13（3单）
    }
}
```

**考察点：** `LocalDate` 日期比较、`WeekFields.ISO`（ISO 周定义）、`groupingBy + counting`、`max` 与 `Optional`。

---

## Q5（综合：全栈 mini-utils 综合题）

**题目**：编写一个完整的数据处理流程，综合使用 4 个工具类：
1. 用 `CsvUtils.readCsv` 读取 `products.csv`
2. 用 `BeanUtils.fromMap` 将每行 Map 转换为 `Product` 对象
3. 用 `CollectionUtils.groupBy` 按类别分组
4. 对每组用 Stream 取价格最高的商品，输出 `类别 -> 最贵商品` 的报告
5. 用 `DateTimeUtils.format` 在报告头部加上生成时间

**参考答案：**

```java
import com.miniutils.csv.CsvUtils;
import com.miniutils.collection.CollectionUtils;
import com.miniutils.datetime.DateTimeUtils;
import com.miniutils.reflect.BeanUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.*;

public class ProductReport {

    static class Product {
        private Long id;
        private String name;
        private double price;
        private String category;

        public Product() {}

        @Override
        public String toString() {
            return name + "（" + price + "元）";
        }
    }

    public static void main(String[] args) {
        // 1. 读取 CSV
        List<Map<String, String>> rawData = CsvUtils.readCsv("data/products.csv");

        // 2. Map -> Product（BeanUtils）
        List<Product> products = rawData.stream()
            .map(row -> BeanUtils.fromMap(row, Product.class))
            .collect(Collectors.toList());

        // 3. 按类别分组（CollectionUtils）
        Map<String, List<Product>> byCategory = CollectionUtils.groupBy(products, p -> p.category);

        // 4. 每组取最贵商品
        System.out.println("=== 各类别最贵商品报告 ===");
        System.out.println("生成时间：" + DateTimeUtils.format(LocalDateTime.now(), DateTimeUtils.DISPLAY_FORMAT));
        System.out.println();

        byCategory.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String category = entry.getKey();
                Optional<Product> mostExpensive = entry.getValue().stream()
                    .max(Comparator.comparingDouble(p -> p.price));
                mostExpensive.ifPresent(p ->
                    System.out.println("【" + category + "】最贵：" + p));
            });
    }
}
```

**期望输出：**
```
=== 各类别最贵商品报告 ===
生成时间：2024年05月16日 14:30

【手机】最贵：华为Mate60（6999.0元）
【电脑】最贵：MacBook Pro（14999.0元）
【耳机】最贵：索尼WH-1000（2499.0元）
```

**考察点：**
- 4个工具类的协同使用
- `Stream.map + BeanUtils.fromMap` 批量转换
- `CollectionUtils.groupBy` 的实际应用
- `Optional.ifPresent` 安全取值
- `DateTimeUtils.format` 格式化时间戳
- `Map.Entry.comparingByKey()` 按 key 排序输出

**这道题是整个 Chapter 11-19 的综合验收，能完整跑通说明工具库实现正确！**
