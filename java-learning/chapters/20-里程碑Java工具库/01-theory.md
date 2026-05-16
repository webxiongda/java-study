# Chapter 20 里程碑 - mini-utils 工具库项目设计指南

## 一、项目目标

本章是 Chapter 11-19 的综合里程碑项目。你将构建一个名为 `mini-utils` 的可复用工具库，综合运用以下技术：

| 技术 | 应用场景 |
|------|---------|
| 泛型（Chapter 9/11） | CollectionUtils 的类型安全方法 |
| 集合框架（Chapter 11/12） | 数据结构选择、Map 操作 |
| IO 基础（Chapter 13） | CSV 文件读写 |
| Lambda/函数式（Chapter 16） | Function、Predicate 参数 |
| Stream API（Chapter 17） | 集合数据处理 |
| 时间 API（Chapter 18） | DateTimeUtils 日期处理 |
| 注解与反射（Chapter 19） | BeanUtils 对象映射 |

**项目定位**：一个小型但实用的工具库，可以直接用于实际项目，也是 Java 框架开发能力的入门练手。

---

## 二、包结构

```
mini-utils/
├── src/
│   └── com/
│       └── miniutils/
│           ├── csv/
│           │   └── CsvUtils.java          # CSV 读写工具
│           ├── collection/
│           │   └── CollectionUtils.java   # 集合操作工具（泛型）
│           ├── datetime/
│           │   └── DateTimeUtils.java     # 日期时间工具
│           └── reflect/
│               └── BeanUtils.java         # 对象反射工具
├── test/
│   └── Main.java                          # 综合测试入口
└── data/
    ├── products.csv                        # 测试用 CSV 文件
    └── users.csv
```

---

## 三、各工具类职责说明

### 3.1 CsvUtils（com.miniutils.csv）

**职责**：CSV 文件的读取和写入，首行作为 header。

**核心方法：**

| 方法签名 | 说明 |
|---------|------|
| `List<Map<String,String>> readCsv(String filePath)` | 读取 CSV，每行转为 Map（key=列名，value=该格内容） |
| `void writeCsv(String filePath, List<Map<String,String>> data)` | 写入 CSV，List 的第一个 Map 的 key 作为 header |

**设计决策：**
- 用 `Map<String,String>` 而非自定义类，使工具与业务解耦
- 首行永远是 header，数据从第二行开始
- 值中含逗号时需要用双引号包裹（CSV 规范）
- 编码统一使用 UTF-8

**示例 CSV 文件（products.csv）：**
```
id,name,price,category
1,iPhone 15,5999,手机
2,MacBook Pro,14999,电脑
3,AirPods Pro,1999,耳机
```

### 3.2 CollectionUtils（com.miniutils.collection）

**职责**：集合的通用操作，泛型方法，不依赖具体业务类型。

**核心方法：**

| 方法签名 | 说明 |
|---------|------|
| `List<T> paginate(List<T> list, int pageNum, int pageSize)` | 分页，pageNum 从 1 开始 |
| `<K> Map<K,List<T>> groupBy(List<T> list, Function<T,K> classifier)` | 按 classifier 分组 |
| `<K> List<T> distinctBy(List<T> list, Function<T,K> keyExtractor)` | 按指定 key 去重，保留首次出现 |

**设计决策：**
- 所有方法接受 `Function` 参数而非具体字段名（类型安全、无反射开销）
- 分页超出范围返回空 List，不抛异常
- `distinctBy` 保持原顺序（使用 LinkedHashSet 跟踪已见 key）

### 3.3 DateTimeUtils（com.miniutils.datetime）

**职责**：日期时间的格式化、解析、计算，封装 java.time API，简化调用。

**核心方法：**

| 方法签名 | 说明 |
|---------|------|
| `String format(LocalDateTime dt, String pattern)` | 格式化 |
| `Optional<LocalDateTime> parse(String str, String pattern)` | 解析，格式错误返回 empty |
| `long daysBetween(LocalDate start, LocalDate end)` | 两日期间隔天数（绝对值）|
| `boolean isWeekend(LocalDate date)` | 是否是周末（周六或周日）|

**设计决策：**
- `parse` 返回 `Optional` 而非抛异常（友好 API）
- `daysBetween` 返回绝对值，不关心 start/end 顺序
- 日期格式常量化（提供 `DATE_FORMAT`/`DATETIME_FORMAT` 常量）

### 3.4 BeanUtils（com.miniutils.reflect）

**职责**：基于反射的对象与 Map 互转，是 ORM 和序列化的基础能力。

**核心方法：**

| 方法签名 | 说明 |
|---------|------|
| `Map<String,Object> toMap(Object obj)` | 对象所有字段 → Map |
| `<T> T fromMap(Map<String,Object> map, Class<T> clazz)` | Map → 对象（通过无参构造 + setter/字段赋值）|

**设计决策：**
- `toMap` 递归处理父类字段（`getSuperclass()`）
- `fromMap` 要求目标类有无参构造器
- 字段类型转换：Map 中的值若为 String，需转换为目标字段类型（int/long/double/boolean）
- `private` 字段用 `setAccessible(true)` 直接赋值（不依赖 setter）

---

## 四、设计原则

### 4.1 工具类不可实例化

```java
public final class CsvUtils {
    // 私有构造器，防止实例化
    private CsvUtils() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    // 所有方法均为 static
    public static List<Map<String, String>> readCsv(String filePath) { ... }
}
```

### 4.2 静态方法为主

工具类的所有功能方法均为 `static`，调用方无需创建实例：
```java
// 推荐：
List<Map<String,String>> data = CsvUtils.readCsv("data/products.csv");
List<Product> page = CollectionUtils.paginate(products, 1, 10);

// 不推荐：
CsvUtils utils = new CsvUtils();   // 不应该能这样做
utils.readCsv("...");
```

### 4.3 泛型参数合理使用

```java
// 好的泛型设计：类型安全，IDE 有代码提示
public static <T, K> Map<K, List<T>> groupBy(
        List<T> list,
        Function<T, K> classifier) { ... }

// 使用：无需强制转换
Map<String, List<Product>> byCategory =
    CollectionUtils.groupBy(products, Product::getCategory);

// 坏的设计（避免）：原始类型，丢失类型信息
public static Map groupBy(List list, String fieldName) { ... } // 不要这样做
```

### 4.4 异常处理策略

| 场景 | 处理方式 |
|------|---------|
| IO 操作失败（文件不存在等）| 抛 `RuntimeException` 或 `IOException` 向上传递 |
| 解析失败（日期格式错误等）| 返回 `Optional.empty()`（更友好）|
| 参数为 null | 快速失败，抛 `IllegalArgumentException` 或 `NullPointerException` |
| 反射访问失败 | 包装为 `RuntimeException` 抛出，附带原始异常信息 |

---

## 五、项目验收清单

完成项目后，逐项检查：

- [ ] **CsvUtils.readCsv** 能正确读取 `products.csv`，返回 `List<Map<String,String>>`，Map 的 key 与 CSV 列名一致
- [ ] **CsvUtils.writeCsv** 能将数据写入新 CSV 文件，再次 readCsv 读取后内容一致（往返测试）
- [ ] **CollectionUtils.paginate** 分页正确：第1页返回前 N 条，边界值（最后一页可能不足 N 条）处理正确
- [ ] **CollectionUtils.groupBy** 返回正确的 Map，每组包含对应元素
- [ ] **CollectionUtils.distinctBy** 按指定 key 去重，重复时保留第一个，顺序不变
- [ ] **DateTimeUtils.parse** 格式正确时返回非 empty Optional，格式错误时返回 empty（不抛异常）
- [ ] **DateTimeUtils.isWeekend** 对周六/周日返回 true，其余返回 false
- [ ] **BeanUtils.toMap** 能读取对象所有 private 字段，包含父类字段
- [ ] **BeanUtils.fromMap** 能根据 Map 创建正确类型的对象，字段值正确赋值
- [ ] 所有工具类构造器为 `private`，无法从外部实例化
- [ ] 代码无 raw type（原始类型）警告
- [ ] `Main.java` 中每个工具类至少有 2 个测试场景，输出结果正确

---

## 六、扩展方向（可选）

完成基础功能后，可尝试以下扩展：

1. **CsvUtils 支持自定义分隔符**：如 `|` 分隔的 TSV 格式
2. **CollectionUtils.sortBy**：支持多级排序（先按类别，再按价格）
3. **BeanUtils 支持 @JsonField 注解**：与 Chapter 19 的注解结合，序列化时使用注解指定的字段名
4. **DateTimeUtils 支持时区参数**：`format(LocalDateTime dt, String pattern, ZoneId zone)`
5. **单元测试**：为每个工具类写 JUnit 5 单元测试（Chapter 21 之后的内容）
