# Chapter 20 里程碑 - mini-utils 骨架代码

> 以下是 mini-utils 工具库的完整骨架代码，每个工具类包含核心结构和 TODO 标记。
> 你的任务是补全所有 TODO，让 Main.java 中的测试全部通过。

---

## CsvUtils.java

```java
package com.miniutils.csv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * CSV 文件读写工具类
 * 规则：首行为 header，值中含逗号时需用双引号包裹
 */
public final class CsvUtils {

    // 工具类不可实例化
    private CsvUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 读取 CSV 文件
     * @param filePath 文件路径（绝对路径或相对路径）
     * @return 每行对应一个 Map，key 为列名（首行），value 为该格内容
     */
    public static List<Map<String, String>> readCsv(String filePath) {
        List<Map<String, String>> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            // TODO 1：读取第一行作为 header，按逗号分割得到列名数组
            String headerLine = reader.readLine();
            if (headerLine == null) return result;
            String[] headers = /* TODO: 分割 headerLine */ null;

            // TODO 2：逐行读取剩余行，每行按逗号分割，与 headers 组合成 Map
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = /* TODO: 分割 line */ null;

                Map<String, String> row = new LinkedHashMap<>(); // LinkedHashMap 保持列顺序
                for (int i = 0; i < headers.length; i++) {
                    // TODO 3：如果 values 长度不够（某行列数少），缺失的列值设为空字符串
                    String value = /* TODO */ "";
                    row.put(headers[i].trim(), value.trim());
                }
                result.add(row);
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException("CSV 文件不存在：" + filePath, e);
        } catch (IOException e) {
            throw new RuntimeException("读取 CSV 文件失败：" + filePath, e);
        }

        return result;
    }

    /**
     * 写入 CSV 文件
     * @param filePath 目标文件路径
     * @param data     数据列表，每个 Map 代表一行；以第一个 Map 的 key 顺序作为 header
     */
    public static void writeCsv(String filePath, List<Map<String, String>> data) {
        if (data == null || data.isEmpty()) {
            // TODO：写入空文件或只写 header（当前简化处理：直接返回）
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            // TODO 4：从第一个 Map 获取所有 key，作为 header 行写入
            List<String> headers = new ArrayList<>(data.get(0).keySet());
            writer.write(/* TODO: 用逗号拼接 headers */);
            writer.newLine();

            // TODO 5：遍历 data，每个 Map 按 headers 顺序取值，写入一行
            for (Map<String, String> row : data) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    String val = row.getOrDefault(header, "");
                    // TODO 6：如果值含逗号，用双引号包裹
                    values.add(/* TODO: 处理含逗号的值 */ val);
                }
                writer.write(/* TODO: 用逗号拼接 values */);
                writer.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException("写入 CSV 文件失败：" + filePath, e);
        }
    }

    // 辅助：解析一行 CSV（简化版，处理引号包裹的字段）
    static String[] parseLine(String line) {
        // TODO（进阶）：处理 "value with, comma" 的情况
        // 简化版直接 split 即可（基础实现不考虑引号内的逗号）
        return line.split(",", -1);
    }
}
```

---

## CollectionUtils.java

```java
package com.miniutils.collection;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

/**
 * 集合操作工具类（泛型）
 */
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 分页
     * @param list     原始列表
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数
     * @return 当前页的元素列表，超出范围返回空 List
     */
    public static <T> List<T> paginate(List<T> list, int pageNum, int pageSize) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        if (pageNum < 1 || pageSize < 1) return Collections.emptyList();

        // TODO 1：计算 fromIndex 和 toIndex
        int fromIndex = /* TODO */ 0;
        int toIndex   = /* TODO */ 0;

        // TODO 2：如果 fromIndex 超出 list 大小，返回空 List
        if (fromIndex >= list.size()) return Collections.emptyList();

        // TODO 3：toIndex 不能超过 list.size()，取 min
        toIndex = Math.min(toIndex, list.size());

        return list.subList(fromIndex, toIndex);
    }

    /**
     * 按指定 key 分组
     * @param list       原始列表
     * @param classifier 分组函数，返回分组 key
     * @return Map：key -> 该组的元素列表
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        if (list == null || list.isEmpty()) return Collections.emptyMap();

        // TODO 4：使用 Stream + Collectors.groupingBy 实现
        return /* TODO */ null;
    }

    /**
     * 按指定 key 去重，保留首次出现的元素，保持原列表顺序
     * @param list         原始列表
     * @param keyExtractor 提取去重 key 的函数
     */
    public static <T, K> List<T> distinctBy(List<T> list, Function<T, K> keyExtractor) {
        if (list == null || list.isEmpty()) return Collections.emptyList();

        Set<K> seen = new LinkedHashSet<>();
        // TODO 5：遍历 list，seen 中不存在的 key 才保留元素
        return list.stream()
            .filter(item -> /* TODO */ false)
            .collect(Collectors.toList());
    }

    /**
     * 安全获取列表中的元素（下标越界时返回 null 而非异常）
     */
    public static <T> T getOrNull(List<T> list, int index) {
        if (list == null || index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    /**
     * 将列表按 batchSize 切割为多个子列表（批量处理场景）
     */
    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize 必须大于 0");

        List<List<T>> batches = new ArrayList<>();
        // TODO 6：循环切割，每段长度为 batchSize（最后一段可能更短）
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(/* TODO: subList */ null);
        }
        return batches;
    }
}
```

---

## DateTimeUtils.java

```java
package com.miniutils.datetime;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 日期时间工具类
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    // 常用格式常量
    public static final String DATE_FORMAT     = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DISPLAY_FORMAT  = "yyyy年MM月dd日 HH:mm";

    /**
     * 格式化 LocalDateTime
     * @param dt      日期时间，null 时返回空字符串
     * @param pattern 格式，如 "yyyy-MM-dd HH:mm:ss"
     */
    public static String format(LocalDateTime dt, String pattern) {
        if (dt == null || pattern == null) return "";
        // TODO 1：使用 DateTimeFormatter.ofPattern(pattern) 格式化
        return /* TODO */ "";
    }

    /**
     * 格式化 LocalDate
     */
    public static String format(LocalDate date, String pattern) {
        if (date == null || pattern == null) return "";
        // TODO 2：同上，注意 LocalDate 没有时间部分
        return /* TODO */ "";
    }

    /**
     * 解析日期时间字符串
     * @return 格式正确返回 Optional<LocalDateTime>，格式错误返回 Optional.empty()
     */
    public static Optional<LocalDateTime> parse(String str, String pattern) {
        if (str == null || str.isBlank()) return Optional.empty();
        // TODO 3：尝试解析，捕获 DateTimeParseException 返回 empty
        try {
            return /* TODO */ Optional.empty();
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * 计算两个日期之间的天数（返回绝对值，不关心顺序）
     */
    public static long daysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        // TODO 4：使用 ChronoUnit.DAYS.between，返回绝对值
        return /* TODO */ 0;
    }

    /**
     * 判断是否是周末（周六或周日）
     */
    public static boolean isWeekend(LocalDate date) {
        if (date == null) return false;
        // TODO 5：获取 DayOfWeek，判断是否为 SATURDAY 或 SUNDAY
        DayOfWeek day = date.getDayOfWeek();
        return /* TODO */ false;
    }

    /**
     * 获取某月的第一天
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        if (date == null) return null;
        return date.withDayOfMonth(1);
    }

    /**
     * 获取某月的最后一天
     */
    public static LocalDate lastDayOfMonth(LocalDate date) {
        if (date == null) return null;
        // TODO 6：使用 TemporalAdjusters.lastDayOfMonth()
        return /* TODO */ null;
    }

    /**
     * 将 java.util.Date 转换为 LocalDateTime（系统时区）
     */
    public static LocalDateTime fromDate(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
```

---

## BeanUtils.java

```java
package com.miniutils.reflect;

import java.lang.reflect.*;
import java.util.*;

/**
 * 对象与 Map 互转工具类（基于反射）
 */
public final class BeanUtils {

    private BeanUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 将对象的所有字段（含父类）转为 Map<String, Object>
     * @param obj 源对象，null 时返回空 Map
     */
    public static Map<String, Object> toMap(Object obj) {
        if (obj == null) return new HashMap<>();

        Map<String, Object> map = new LinkedHashMap<>();

        // TODO 1：遍历 obj.getClass() 及所有父类（getSuperclass()），收集所有 DeclaredFields
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                // TODO 2：跳过合成字段（synthetic，内部类相关）
                if (field.isSynthetic()) continue;
                // TODO 3：setAccessible(true)，读取字段值，存入 map
                // 注意：子类字段优先（map.containsKey 检查，已有则跳过父类同名字段）
                if (!map.containsKey(field.getName())) {
                    field.setAccessible(true);
                    try {
                        map.put(field.getName(), /* TODO: field.get(obj) */ null);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("无法读取字段：" + field.getName(), e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        return map;
    }

    /**
     * 根据 Map 创建指定类的实例（要求目标类有无参构造器）
     * 支持的字段类型：String、int/Integer、long/Long、double/Double、boolean/Boolean
     *
     * @param map   字段名 -> 字段值 的 Map
     * @param clazz 目标类型
     * @return 填充了 map 中对应字段值的对象实例
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        if (map == null || clazz == null) return null;

        T instance;
        try {
            // TODO 4：通过无参构造器创建实例（getDeclaredConstructor().newInstance()）
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = /* TODO */ null;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(clazz.getName() + " 缺少无参构造器", e);
        } catch (Exception e) {
            throw new RuntimeException("创建实例失败：" + clazz.getName(), e);
        }

        // TODO 5：遍历 clazz 的所有字段（含父类），从 map 中取值并赋给对应字段
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic()) continue;
                if (!map.containsKey(field.getName())) continue;

                field.setAccessible(true);
                Object value = map.get(field.getName());
                try {
                    // TODO 6：类型转换（map 中的 value 类型可能与 field 类型不同）
                    field.set(instance, convertValue(value, field.getType()));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("无法赋值字段：" + field.getName(), e);
                }
            }
            current = current.getSuperclass();
        }

        return instance;
    }

    /**
     * 将 value 转换为目标类型（简化版，支持常用类型）
     */
    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value; // 类型匹配，直接返回

        String str = value.toString();
        // TODO 7：根据 targetType 做类型转换
        if (targetType == String.class)                          return str;
        if (targetType == int.class || targetType == Integer.class)   return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class)     return Long.parseLong(str);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        if (targetType == float.class || targetType == Float.class)   return Float.parseFloat(str);

        return value; // 其他类型，原样返回（可能在 field.set 时抛 IllegalArgumentException）
    }
}
```

---

## Main.java（综合测试）

```java
import com.miniutils.csv.CsvUtils;
import com.miniutils.collection.CollectionUtils;
import com.miniutils.datetime.DateTimeUtils;
import com.miniutils.reflect.BeanUtils;

import java.time.*;
import java.util.*;

public class Main {

    // 测试用数据类
    static class Product {
        private Long id;
        private String name;
        private double price;
        private String category;

        // 无参构造（BeanUtils.fromMap 需要）
        public Product() {}

        public Product(Long id, String name, double price, String category) {
            this.id = id; this.name = name;
            this.price = price; this.category = category;
        }

        @Override
        public String toString() {
            return String.format("Product{id=%d, name='%s', price=%.0f, category='%s'}",
                id, name, price, category);
        }
    }

    public static void main(String[] args) {
        testCsvUtils();
        testCollectionUtils();
        testDateTimeUtils();
        testBeanUtils();
    }

    // ===========================
    // CsvUtils 测试
    // ===========================
    static void testCsvUtils() {
        System.out.println("==========================================");
        System.out.println("CsvUtils 测试");
        System.out.println("==========================================");

        // 准备写入数据
        List<Map<String, String>> data = new ArrayList<>();
        String[] names = {"iPhone 15", "MacBook Pro", "AirPods Pro", "小米14", "联想笔记本"};
        String[] prices = {"5999", "14999", "1999", "3999", "7999"};
        String[] categories = {"手机", "电脑", "耳机", "手机", "电脑"};

        for (int i = 0; i < names.length; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(i + 1));
            row.put("name", names[i]);
            row.put("price", prices[i]);
            row.put("category", categories[i]);
            data.add(row);
        }

        // 写入
        String csvPath = "data/products_test.csv";
        CsvUtils.writeCsv(csvPath, data);
        System.out.println("写入完成：" + csvPath);

        // 读取
        List<Map<String, String>> read = CsvUtils.readCsv(csvPath);
        System.out.println("读取行数：" + read.size() + "（期望：5）");
        System.out.println("第一行：" + read.get(0));
        System.out.println("第一行 name：" + read.get(0).get("name") + "（期望：iPhone 15）");

        System.out.println();
    }

    // ===========================
    // CollectionUtils 测试
    // ===========================
    static void testCollectionUtils() {
        System.out.println("==========================================");
        System.out.println("CollectionUtils 测试");
        System.out.println("==========================================");

        List<Product> products = Arrays.asList(
            new Product(1L, "iPhone 15",   5999, "手机"),
            new Product(2L, "小米14",       3999, "手机"),
            new Product(3L, "华为Mate60",   6999, "手机"),
            new Product(4L, "MacBook Pro", 14999, "电脑"),
            new Product(5L, "联想笔记本",   7999, "电脑"),
            new Product(6L, "AirPods Pro", 1999, "耳机"),
            new Product(7L, "索尼WH-1000", 2499, "耳机"),
            new Product(8L, "小米14",      3999, "手机")   // 重复 name
        );

        // 分页测试
        System.out.println("--- paginate ---");
        List<Product> page1 = CollectionUtils.paginate(products, 1, 3);
        System.out.println("第1页（3条）：" + page1.size() + " 条（期望：3）");

        List<Product> page3 = CollectionUtils.paginate(products, 3, 3);
        System.out.println("第3页（3条）：" + page3.size() + " 条（期望：2，最后不足3条）");

        List<Product> page99 = CollectionUtils.paginate(products, 99, 3);
        System.out.println("第99页：" + page99.size() + " 条（期望：0）");

        // 分组测试
        System.out.println("\n--- groupBy ---");
        Map<String, List<Product>> byCategory = CollectionUtils.groupBy(products, p -> p.category);
        byCategory.forEach((cat, prods) ->
            System.out.println(cat + ": " + prods.size() + " 件"));

        // 去重测试
        System.out.println("\n--- distinctBy ---");
        List<Product> distinct = CollectionUtils.distinctBy(products, p -> p.name);
        System.out.println("按 name 去重后：" + distinct.size() + " 件（期望：7，去掉1个重复小米14）");

        // partition 测试
        System.out.println("\n--- partition ---");
        List<List<Product>> batches = CollectionUtils.partition(products, 3);
        System.out.println("每批3条，共 " + batches.size() + " 批（期望：3）");

        System.out.println();
    }

    // ===========================
    // DateTimeUtils 测试
    // ===========================
    static void testDateTimeUtils() {
        System.out.println("==========================================");
        System.out.println("DateTimeUtils 测试");
        System.out.println("==========================================");

        // format
        LocalDateTime dt = LocalDateTime.of(2024, 5, 16, 14, 30, 0);
        System.out.println("format：" + DateTimeUtils.format(dt, DateTimeUtils.DISPLAY_FORMAT));
        // 期望：2024年05月16日 14:30

        // parse
        DateTimeUtils.parse("2024-05-16 14:30:00", DateTimeUtils.DATETIME_FORMAT)
            .ifPresentOrElse(
                d -> System.out.println("parse 成功：" + d),
                () -> System.out.println("parse 失败")
            );

        DateTimeUtils.parse("invalid", DateTimeUtils.DATETIME_FORMAT)
            .ifPresentOrElse(
                d -> System.out.println("不应该到这里：" + d),
                () -> System.out.println("格式错误返回 empty（正确）")
            );

        // daysBetween
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 5, 16);
        System.out.println("daysBetween：" + DateTimeUtils.daysBetween(d1, d2) + " 天");

        // isWeekend
        LocalDate saturday = LocalDate.of(2024, 5, 18); // 周六
        LocalDate monday   = LocalDate.of(2024, 5, 20); // 周一
        System.out.println("2024-05-18 是周末：" + DateTimeUtils.isWeekend(saturday) + "（期望：true）");
        System.out.println("2024-05-20 是周末：" + DateTimeUtils.isWeekend(monday) + "（期望：false）");

        System.out.println();
    }

    // ===========================
    // BeanUtils 测试
    // ===========================
    static void testBeanUtils() {
        System.out.println("==========================================");
        System.out.println("BeanUtils 测试");
        System.out.println("==========================================");

        // toMap
        Product p = new Product(1L, "iPhone 15", 5999.0, "手机");
        Map<String, Object> map = BeanUtils.toMap(p);
        System.out.println("toMap 结果：" + map);
        System.out.println("name 字段：" + map.get("name") + "（期望：iPhone 15）");

        // fromMap
        Map<String, Object> data = new HashMap<>();
        data.put("id", 2L);
        data.put("name", "MacBook Pro");
        data.put("price", 14999.0);
        data.put("category", "电脑");

        Product restored = BeanUtils.fromMap(data, Product.class);
        System.out.println("fromMap 结果：" + restored);
        System.out.println("name：" + restored.name + "（期望：MacBook Pro）");

        // fromMap：值类型转换（String -> 数字）
        Map<String, Object> strData = new HashMap<>();
        strData.put("id", "3");         // String 类型，需转为 Long
        strData.put("name", "iPad");
        strData.put("price", "4599.0"); // String 类型，需转为 double
        strData.put("category", "平板");

        Product fromStr = BeanUtils.fromMap(strData, Product.class);
        System.out.println("String->类型转换：" + fromStr);
        System.out.println("price：" + fromStr.price + "（期望：4599.0）");

        System.out.println();
    }
}
```

---

## 测试 CSV 文件（data/products.csv）

在项目根目录创建 `data/` 文件夹，并放入以下测试文件：

**data/products.csv**
```
id,name,price,category
1,iPhone 15,5999,手机
2,MacBook Pro,14999,电脑
3,AirPods Pro,1999,耳机
4,小米14,3999,手机
5,联想笔记本,7999,电脑
```

**data/users.csv**
```
id,username,email,age
1,Alice,alice@example.com,25
2,Bob,bob@example.com,30
3,Carol,carol@example.com,28
```
