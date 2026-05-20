# Chapter 20 里程碑 - 项目任务：补全 mini-utils 工具库

> 前置：[[19-注解与反射]]
> 后续：[[21-Maven工程化]]（把这个项目用 Maven 构建打包，作为后续 Spring Boot 的基础）

## 任务概述

基于 `02-demo.md` 中提供的骨架代码，补全所有 `TODO`，实现 `mini-utils` 工具库的完整功能，并让 `Main.java` 中所有测试场景通过。

## 进入阶段二

完成本章后即结束阶段一（纯 Java SE）。 下一站是 Maven 工程化（21 章），具体会做：
- 把 `mini-utils` 拆成 Maven 多模块项目，规范 `pom.xml`。
- 把 `CsvUtils` 当作未来博客 API 的「文件导出」基础。
- `BeanUtils` 后续会被 Jackson + MapStruct 替代，但你需要先理解原理。

> 这一阶段「自己撸轮子」的代码后面不会再写，但**底层认知**贯穿整个 Java 后端职业生涯。

---

## 任务1：补全 CsvUtils

**文件**：`com/miniutils/csv/CsvUtils.java`

**需要实现的部分：**

```
TODO 1：读取第一行作为 header，按逗号分割
       headerLine.split(",", -1)
       注意：split 第二个参数 -1 表示保留尾部空字符串

TODO 2：逐行读取数据，分割后与 headers 对应

TODO 3：values 长度不够时，缺失列用空字符串补全
       String value = (i < values.length) ? values[i] : "";

TODO 4：从第一个 Map 的 keySet() 获取 headers 顺序，拼接后写入首行

TODO 5：按 headers 顺序从每个 Map 取值，拼接后写入

TODO 6：值含逗号时用双引号包裹
       val.contains(",") ? "\"" + val + "\"" : val
```

**验证方法**：
```java
// 写入再读取，验证内容一致
List<Map<String, String>> original = buildTestData();
CsvUtils.writeCsv("test.csv", original);
List<Map<String, String>> readBack = CsvUtils.readCsv("test.csv");
assert readBack.size() == original.size();
assert readBack.get(0).get("name").equals(original.get(0).get("name"));
```

---

## 任务2：补全 CollectionUtils

**文件**：`com/miniutils/collection/CollectionUtils.java`

**需要实现的部分：**

```
TODO 1：paginate - 计算 fromIndex
        int fromIndex = (pageNum - 1) * pageSize;

TODO 2：paginate - 计算 toIndex
        int toIndex = fromIndex + pageSize;

TODO 3：paginate - 越界保护
        toIndex = Math.min(toIndex, list.size());
        return list.subList(fromIndex, toIndex);  // 注意 subList 是左闭右开

TODO 4：groupBy - Stream + groupingBy
        return list.stream()
            .collect(Collectors.groupingBy(classifier));

TODO 5：distinctBy - 用 seen Set 过滤重复
        return list.stream()
            .filter(item -> seen.add(keyExtractor.apply(item)))
            .collect(Collectors.toList());
        // Set.add 返回 true 表示新增成功（不重复），false 表示已存在（重复）

TODO 6：partition - 切割子列表
        batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
```

**关键细节**：`distinctBy` 的 `seen.add()` 返回值技巧——`LinkedHashSet.add(element)` 返回 `true` 表示元素是新的，`false` 表示重复。作为 `filter` 的谓词，正好可以过滤掉重复元素，同时保持顺序。

---

## 任务3：补全 DateTimeUtils

**文件**：`com/miniutils/datetime/DateTimeUtils.java`

**需要实现的部分：**

```
TODO 1：format(LocalDateTime) 
        return dt.format(DateTimeFormatter.ofPattern(pattern));

TODO 2：format(LocalDate)
        return date.format(DateTimeFormatter.ofPattern(pattern));

TODO 3：parse
        return Optional.of(
            LocalDateTime.parse(str.trim(), DateTimeFormatter.ofPattern(pattern))
        );

TODO 4：daysBetween
        return Math.abs(ChronoUnit.DAYS.between(start, end));

TODO 5：isWeekend
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;

TODO 6：lastDayOfMonth
        import java.time.temporal.TemporalAdjusters;
        return date.with(TemporalAdjusters.lastDayOfMonth());
```

---

## 任务4：补全 BeanUtils

**文件**：`com/miniutils/reflect/BeanUtils.java`

**需要实现的部分：**

```
TODO 1-3：toMap - 遍历字段并读取值（骨架已基本提供，补全 field.get(obj)）
        map.put(field.getName(), field.get(obj));

TODO 4：fromMap - 创建实例
        instance = constructor.newInstance();

TODO 5：fromMap - 遍历字段赋值（骨架已提供，补全 field.set(instance, ...)）
        已有 convertValue 辅助方法，直接调用

TODO 6：convertValue - 类型转换（骨架已完整实现，检查是否覆盖所有常用类型）
```

**注意事项**：`BeanUtils.fromMap` 要求目标类有**无参构造器**（public 或 private 均可，private 时需要 `setAccessible(true)`）。

---

## 完整参考答案

<details>
<summary>点击展开完整参考答案（建议先自行实现后再对照）</summary>

### CsvUtils 关键实现

```java
public static List<Map<String, String>> readCsv(String filePath) {
    List<Map<String, String>> result = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

        String headerLine = reader.readLine();
        if (headerLine == null) return result;
        String[] headers = headerLine.split(",", -1);  // -1 保留尾部空字符串

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String[] values = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String value = (i < values.length) ? values[i] : "";
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

public static void writeCsv(String filePath, List<Map<String, String>> data) {
    if (data == null || data.isEmpty()) return;
    try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

        List<String> headers = new ArrayList<>(data.get(0).keySet());
        writer.write(String.join(",", headers));
        writer.newLine();

        for (Map<String, String> row : data) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                String val = row.getOrDefault(header, "");
                values.add(val.contains(",") ? "\"" + val + "\"" : val);
            }
            writer.write(String.join(",", values));
            writer.newLine();
        }
    } catch (IOException e) {
        throw new RuntimeException("写入 CSV 文件失败：" + filePath, e);
    }
}
```

### CollectionUtils 关键实现

```java
public static <T> List<T> paginate(List<T> list, int pageNum, int pageSize) {
    if (list == null || list.isEmpty()) return Collections.emptyList();
    if (pageNum < 1 || pageSize < 1) return Collections.emptyList();
    int fromIndex = (pageNum - 1) * pageSize;
    if (fromIndex >= list.size()) return Collections.emptyList();
    int toIndex = Math.min(fromIndex + pageSize, list.size());
    return list.subList(fromIndex, toIndex);
}

public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
    if (list == null || list.isEmpty()) return Collections.emptyMap();
    return list.stream().collect(Collectors.groupingBy(classifier));
}

public static <T, K> List<T> distinctBy(List<T> list, Function<T, K> keyExtractor) {
    if (list == null || list.isEmpty()) return Collections.emptyList();
    Set<K> seen = new LinkedHashSet<>();
    return list.stream()
        .filter(item -> seen.add(keyExtractor.apply(item)))
        .collect(Collectors.toList());
}

public static <T> List<List<T>> partition(List<T> list, int batchSize) {
    if (list == null || list.isEmpty()) return Collections.emptyList();
    if (batchSize <= 0) throw new IllegalArgumentException("batchSize 必须大于 0");
    List<List<T>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
        batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return batches;
}
```

### DateTimeUtils 关键实现

```java
public static String format(LocalDateTime dt, String pattern) {
    if (dt == null || pattern == null) return "";
    return dt.format(DateTimeFormatter.ofPattern(pattern));
}

public static String format(LocalDate date, String pattern) {
    if (date == null || pattern == null) return "";
    return date.format(DateTimeFormatter.ofPattern(pattern));
}

public static Optional<LocalDateTime> parse(String str, String pattern) {
    if (str == null || str.isBlank()) return Optional.empty();
    try {
        return Optional.of(LocalDateTime.parse(str.trim(),
            DateTimeFormatter.ofPattern(pattern)));
    } catch (DateTimeParseException e) {
        return Optional.empty();
    }
}

public static long daysBetween(LocalDate start, LocalDate end) {
    if (start == null || end == null) return 0;
    return Math.abs(ChronoUnit.DAYS.between(start, end));
}

public static boolean isWeekend(LocalDate date) {
    if (date == null) return false;
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
}

public static LocalDate lastDayOfMonth(LocalDate date) {
    if (date == null) return null;
    return date.with(TemporalAdjusters.lastDayOfMonth());
}
```

### BeanUtils 关键实现

```java
public static Map<String, Object> toMap(Object obj) {
    if (obj == null) return new HashMap<>();
    Map<String, Object> map = new LinkedHashMap<>();
    Class<?> clazz = obj.getClass();
    while (clazz != null && clazz != Object.class) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (!map.containsKey(field.getName())) {
                field.setAccessible(true);
                try {
                    map.put(field.getName(), field.get(obj));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("无法读取字段：" + field.getName(), e);
                }
            }
        }
        clazz = clazz.getSuperclass();
    }
    return map;
}

public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
    if (map == null || clazz == null) return null;
    T instance;
    try {
        Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        instance = constructor.newInstance();
    } catch (NoSuchMethodException e) {
        throw new RuntimeException(clazz.getName() + " 缺少无参构造器", e);
    } catch (Exception e) {
        throw new RuntimeException("创建实例失败：" + clazz.getName(), e);
    }
    Class<?> current = clazz;
    while (current != null && current != Object.class) {
        for (Field field : current.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (!map.containsKey(field.getName())) continue;
            field.setAccessible(true);
            try {
                field.set(instance, convertValue(map.get(field.getName()), field.getType()));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("无法赋值字段：" + field.getName(), e);
            }
        }
        current = current.getSuperclass();
    }
    return instance;
}
```

</details>

---

## 验收标准

1. **CsvUtils 往返测试**：`writeCsv` 写出的文件能被 `readCsv` 正确读回，行数和每行字段值与原始数据完全一致；含逗号的值用双引号包裹后仍能正确解析。

2. **CollectionUtils 边界测试**：`paginate` 在 `pageNum` 超出范围时返回空 List 而非抛异常；`distinctBy` 在有重复时保留第一个，顺序不变；`partition` 最后一批不足时返回剩余元素。

3. **DateTimeUtils 格式化/解析**：`parse` 对格式正确的字符串返回非 empty Optional，格式错误时返回 `empty()` 而非抛异常；`isWeekend` 对周六周日返回 `true`，其余返回 `false`。

4. **BeanUtils 类型转换**：`fromMap` 能处理 Map 中 `String` 类型的数字值（如 `"5999.0"`）并正确转换为 `double` 赋值给 `double` 类型字段。

5. **Main.java 全部通过**：运行 `Main.java`，所有 `System.out.println` 输出的"期望"值与实际输出一致，无异常抛出。

---

## 常见坑

**坑1：`String.split(",")` 尾部空字符串被丢弃**
```java
// 错误：split 默认丢弃尾部空字符串
"a,b,".split(",");      // 结果：["a", "b"]，长度2，丢失了最后的空字段

// 正确：使用 -1 作为 limit 参数，保留所有字段（包括尾部空字符串）
"a,b,".split(",", -1);  // 结果：["a", "b", ""]，长度3
```

**坑2：`BeanUtils.fromMap` 目标类没有无参构造器**
```java
// 错误：只有有参构造器的类
public class Product {
    public Product(Long id, String name) { ... }
    // 没有 Product() 无参构造！
}

// fromMap 调用时抛 NoSuchMethodException
BeanUtils.fromMap(map, Product.class); // 异常！

// 改正：必须提供无参构造器
public class Product {
    public Product() {}   // 添加无参构造器
    public Product(Long id, String name) { ... }
}
```

**坑3：`subList` 返回的是原 List 的视图，修改会影响原 List**
```java
// 警告：subList 是视图，不是独立副本
List<String> sub = list.subList(0, 3);
sub.set(0, "modified"); // list 的第0个元素也被修改了！

// 如果需要独立副本（CollectionUtils 的 paginate 通常不需要，但 partition 要注意）：
List<String> copy = new ArrayList<>(list.subList(0, 3));
```
