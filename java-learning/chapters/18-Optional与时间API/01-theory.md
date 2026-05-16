# Chapter 18 Optional 与时间 API - 理论篇

## 一、Optional

### 1.1 设计目的

`Optional<T>` 是 Java 8 引入的容器类，其核心目标是：**用类型系统表达"值可能不存在"，强制调用者处理空值情况，从而减少 NullPointerException（NPE）**。

**传统写法的问题：**
```java
// 每一层都可能 NPE，要写大量 null 检查
String city = null;
User user = userRepository.findById(userId);
if (user != null) {
    Address addr = user.getAddress();
    if (addr != null) {
        city = addr.getCity();
    }
}
```

**Optional 写法（链式、无 null 检查）：**
```java
String city = userRepository.findById(userId)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("未知城市");
```

### 1.2 创建 Optional

| 方法 | 说明 | 是否允许 null |
|------|------|--------------|
| `Optional.of(value)` | 包装非 null 值，null 会抛 NPE | 不允许 |
| `Optional.ofNullable(value)` | 包装任意值，null 变为 empty | 允许 |
| `Optional.empty()` | 创建空的 Optional | — |

```java
Optional<String> a = Optional.of("hello");          // OK
Optional<String> b = Optional.of(null);             // 抛 NullPointerException！
Optional<String> c = Optional.ofNullable(null);     // OK，等价于 Optional.empty()
Optional<String> d = Optional.empty();              // 空 Optional
```

### 1.3 使用 Optional 的值

**判断是否有值：**
```java
Optional<String> opt = Optional.ofNullable(getValue());

opt.isPresent();      // 有值返回 true（老写法，不推荐单独使用）
opt.isEmpty();        // Java 11+，没有值返回 true
```

**消费值（有值时执行操作）：**
```java
opt.ifPresent(v -> System.out.println("值是：" + v));  // 推荐

// Java 9+
opt.ifPresentOrElse(
    v -> System.out.println("有值：" + v),
    () -> System.out.println("无值")
);
```

**获取值（有默认值）：**
```java
String v1 = opt.orElse("默认值");               // 无论是否有值，"默认值"表达式都会被执行
String v2 = opt.orElseGet(() -> computeDefault()); // 懒执行，只有无值时才调用 Supplier
String v3 = opt.orElseThrow(() -> new RuntimeException("not found")); // 无值时抛异常
```

**转换值（map / flatMap / filter）：**
```java
// map：有值时转换，无值时还是 empty
Optional<Integer> length = opt.map(String::length);

// flatMap：转换函数本身返回 Optional，避免嵌套 Optional<Optional<T>>
Optional<String> city = optUser
    .flatMap(u -> Optional.ofNullable(u.getAddress()))
    .flatMap(a -> Optional.ofNullable(a.getCity()));

// filter：有值且满足条件才保留，否则变为 empty
Optional<String> longWord = opt.filter(s -> s.length() > 5);
```

---

## 二、新时间 API（java.time）

### 2.1 旧版 Date/Calendar 的缺点

| 问题 | 说明 |
|------|------|
| 线程不安全 | `SimpleDateFormat` 多线程下会产生错误结果 |
| 设计混乱 | `Date` 代表时刻，但有 `getMonth()`；月份从 0 开始（1月=0） |
| 可变性 | `Date` 是可变对象，容易产生副作用 |
| 缺少日期概念 | 没有纯"日期"类型，混用时间戳和日期操作 |

**Java 8 新时间 API 解决了这些问题：**
- **不可变（Immutable）**：所有操作返回新对象
- **线程安全**：无状态，天生线程安全
- **设计清晰**：LocalDate（纯日期）/ LocalTime（纯时间）/ LocalDateTime（日期+时间）
- **月份从1开始**：1月 = Month.JANUARY = 1

### 2.2 LocalDate / LocalTime / LocalDateTime

**创建：**
```java
// LocalDate：只有日期，没有时间和时区
LocalDate today = LocalDate.now();                       // 当前日期
LocalDate birthday = LocalDate.of(1995, 5, 16);         // 指定日期
LocalDate parsed = LocalDate.parse("2024-05-16");        // 解析 ISO 格式

// LocalTime：只有时间
LocalTime now = LocalTime.now();
LocalTime meeting = LocalTime.of(14, 30, 0);             // 14:30:00
LocalTime t = LocalTime.parse("14:30:00");

// LocalDateTime：日期 + 时间
LocalDateTime dt = LocalDateTime.now();
LocalDateTime dt2 = LocalDateTime.of(2024, 5, 16, 14, 30);
LocalDateTime dt3 = LocalDateTime.of(birthday, meeting); // 组合
```

**加减（plus / minus / with）：**
```java
LocalDate d = LocalDate.of(2024, 5, 16);

d.plusDays(7);          // 加7天
d.plusMonths(1);        // 加1个月
d.plusYears(1);         // 加1年
d.minusDays(3);         // 减3天

// with：设置某个字段
d.withDayOfMonth(1);    // 该月第一天
d.withMonth(1);         // 该年1月

// 也可用 TemporalAdjusters
import java.time.temporal.TemporalAdjusters;
d.with(TemporalAdjusters.firstDayOfMonth());   // 月初
d.with(TemporalAdjusters.lastDayOfMonth());    // 月末
d.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)); // 下一个周一
```

**比较：**
```java
LocalDate d1 = LocalDate.of(2024, 1, 1);
LocalDate d2 = LocalDate.of(2024, 6, 1);

d1.isBefore(d2);    // true
d1.isAfter(d2);     // false
d1.isEqual(d2);     // false
d1.compareTo(d2);   // 负数（d1 < d2）
```

### 2.3 DateTimeFormatter 格式化与解析

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

// 格式化
LocalDateTime dt = LocalDateTime.of(2024, 5, 16, 14, 30);
String formatted = dt.format(formatter);   // "2024年05月16日 14:30"

// 解析
LocalDateTime parsed = LocalDateTime.parse("2024年05月16日 14:30", formatter);

// 常用内置格式
DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;          // "2024-05-16"
DateTimeFormatter isoDateTime = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // "2024-05-16T14:30:00"
```

### 2.4 Duration vs Period

| 类 | 适用场景 | 精度 |
|----|---------|------|
| `Duration` | 两个时刻之间的精确时间差 | 秒、纳秒 |
| `Period` | 两个日期之间的"日历差" | 年、月、日 |

```java
// Duration：基于时间量（小时、分钟、秒）
LocalDateTime start = LocalDateTime.of(2024, 5, 16, 9, 0);
LocalDateTime end   = LocalDateTime.of(2024, 5, 16, 11, 30);
Duration duration = Duration.between(start, end);
duration.toHours();    // 2
duration.toMinutes();  // 150
duration.getSeconds(); // 9000

// Period：基于日历（年月日）
LocalDate birth = LocalDate.of(1995, 5, 16);
LocalDate now = LocalDate.of(2024, 5, 16);
Period period = Period.between(birth, now);
period.getYears();   // 29
period.getMonths();  // 0
period.getDays();    // 0

// 注意：Period 不是总天数！
long totalDays = ChronoUnit.DAYS.between(birth, now); // 10958 天（正确总天数）
```

### 2.5 ZonedDateTime 概念

```java
import java.time.ZoneId;
import java.time.ZonedDateTime;

// 创建带时区的日期时间
ZonedDateTime shanghaiTime = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
ZonedDateTime nyTime = ZonedDateTime.now(ZoneId.of("America/New_York"));

// 时区转换
ZonedDateTime toNY = shanghaiTime.withZoneSameInstant(ZoneId.of("America/New_York"));

// LocalDateTime -> ZonedDateTime（指定时区）
LocalDateTime localDt = LocalDateTime.of(2024, 5, 16, 14, 0);
ZonedDateTime zdt = localDt.atZone(ZoneId.of("Asia/Shanghai"));

// 查看所有可用时区
ZoneId.getAvailableZoneIds().stream()
    .filter(z -> z.contains("Asia"))
    .sorted()
    .forEach(System.out::println);
```

### 2.6 旧版 Date 与新 API 互转

```java
import java.util.Date;
import java.time.Instant;
import java.time.ZoneId;

// Date -> LocalDateTime
Date oldDate = new Date();
LocalDateTime newDt = oldDate.toInstant()
    .atZone(ZoneId.systemDefault())
    .toLocalDateTime();

// LocalDateTime -> Date
LocalDateTime ldt = LocalDateTime.now();
Date backToOld = Date.from(
    ldt.atZone(ZoneId.systemDefault()).toInstant()
);
```

---

## 三、常见坑

**坑1：`Optional.of(null)` 会立刻抛 NPE**
```java
// 错误：已知可能为 null 时不能用 of
Optional<String> opt = Optional.of(null); // NullPointerException!

// 正确：来源不确定时用 ofNullable
Optional<String> opt2 = Optional.ofNullable(getValueMaybeNull());
```

**坑2：`orElse` vs `orElseGet` 的本质区别**
```java
// orElse：参数是值，无论有没有值都会执行参数表达式
String result1 = opt.orElse(createExpensiveDefault()); // 始终调用 createExpensiveDefault()

// orElseGet：参数是 Supplier（函数），只有 Optional 为 empty 时才调用
String result2 = opt.orElseGet(() -> createExpensiveDefault()); // 有值时不调用

// 结论：默认值获取有副作用或代价高昂时，必须用 orElseGet
```

**坑3：`LocalDate` 不含时区信息，直接比较可能有问题**
```java
// LocalDate.now() 依赖系统默认时区
LocalDate today = LocalDate.now();                          // 机器时区
LocalDate todayShanghai = LocalDate.now(ZoneId.of("Asia/Shanghai")); // 指定时区

// 在跨时区场景下（服务器在美国，业务在中国），务必显式指定时区
// 否则在日期切换临界点（0点前后）会得到不同日期
```

---

## 四、面试高频问题

**Q1：Optional.orElse 和 orElseGet 的区别？**

A：`orElse(T other)` 接收一个值，无论 Optional 是否有值，`other` 表达式都会被求值。`orElseGet(Supplier<T> supplier)` 接收一个 Supplier，只在 Optional 为 empty 时才调用该函数。当默认值的获取有副作用（数据库查询、网络请求等）时，应使用 `orElseGet` 避免不必要的开销。

**Q2：为什么要用新时间 API，旧的 Date 有什么问题？**

A：旧版 `Date`/`Calendar` 的主要问题：① `SimpleDateFormat` 线程不安全，多线程下需要同步；② `Date` 表示时刻但包含日期方法，设计混乱；③ 月份从0开始（一月=0），容易出 bug；④ `Date` 是可变的，作为参数传递有副作用风险。新 API（`java.time`）不可变、线程安全、设计清晰，并且提供了 `LocalDate`/`LocalTime`/`LocalDateTime` 等专用类型。

**Q3：如何将 Date 转换为 LocalDateTime？**

A：通过 `Instant` 桥接：`date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()`。反向转换：`Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())`。

**Q4：Duration 和 Period 的区别？**

A：`Duration` 表示基于时间的间隔（小时、分钟、秒、纳秒），适用于 `LocalDateTime`/`Instant`；`Period` 表示基于日历的间隔（年、月、日），适用于 `LocalDate`。注意 `Period.getDays()` 返回的是"剩余天数"而非总天数，要获取总天数应使用 `ChronoUnit.DAYS.between(d1, d2)`。

**Q5：Optional 是否应该用于方法参数和字段？**

A：不推荐。Optional 的设计目的是作为方法返回值，告知调用者"结果可能不存在"。用作方法参数会造成调用方需要包装，增加复杂性；用作字段会带来序列化问题（Optional 没有实现 Serializable）。正确用法：返回值类型用 Optional，参数和字段保持普通类型并在方法内部处理 null。
