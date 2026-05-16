# Chapter 18 Optional 与时间 API - Demo 示例

## Demo 1：Optional 使用对比

**场景**：用户查找服务，根据用户 ID 返回用户所在城市。用户可能不存在，地址可能为空，城市可能为空。

```java
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

public class OptionalDemo {

    // 数据模型
    static class Address {
        private String city;
        public Address(String city) { this.city = city; }
        public String getCity() { return city; }
    }

    static class User {
        private Long id;
        private String name;
        private Address address;

        public User(Long id, String name, Address address) {
            this.id = id;
            this.name = name;
            this.address = address;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public Address getAddress() { return address; }
    }

    // 模拟数据库
    static Map<Long, User> db = new HashMap<>();
    static {
        db.put(1L, new User(1L, "Alice", new Address("北京")));
        db.put(2L, new User(2L, "Bob",   null));          // 无地址
        db.put(3L, new User(3L, "Carol", new Address(null))); // 城市为空
    }

    // ===========================
    // 传统写法：层层 null 检查
    // ===========================
    static String getCityTraditional(Long userId) {
        User user = db.get(userId);
        if (user == null) {
            return "用户不存在";
        }
        Address address = user.getAddress();
        if (address == null) {
            return "地址未填写";
        }
        String city = address.getCity();
        if (city == null || city.isEmpty()) {
            return "城市未知";
        }
        return city;
    }

    // ===========================
    // Optional 链式写法
    // ===========================
    static String getCityWithOptional(Long userId) {
        return Optional.ofNullable(db.get(userId))          // Optional<User>
            .map(User::getAddress)                           // Optional<Address>
            .map(Address::getCity)                           // Optional<String>
            .filter(city -> !city.isEmpty())                 // 过滤空字符串
            .orElse("城市未知");                             // 默认值
    }

    // ===========================
    // 模拟 Repository 返回 Optional
    // ===========================
    static Optional<User> findUserById(Long userId) {
        return Optional.ofNullable(db.get(userId));
    }

    static void processUser(Long userId) {
        findUserById(userId)
            .ifPresentOrElse(
                user -> System.out.println("找到用户：" + user.getName()),
                () -> System.out.println("用户 " + userId + " 不存在")
            );
    }

    // ===========================
    // orElse vs orElseGet 对比
    // ===========================
    static String createExpensiveDefault() {
        System.out.println(">>> 正在创建默认值（开销很大）...");
        return "DEFAULT";
    }

    public static void main(String[] args) {
        System.out.println("=== 传统 null 检查 ===");
        System.out.println(getCityTraditional(1L));  // 北京
        System.out.println(getCityTraditional(2L));  // 地址未填写
        System.out.println(getCityTraditional(3L));  // 城市未知
        System.out.println(getCityTraditional(99L)); // 用户不存在

        System.out.println("\n=== Optional 链式写法 ===");
        System.out.println(getCityWithOptional(1L));  // 北京
        System.out.println(getCityWithOptional(2L));  // 城市未知
        System.out.println(getCityWithOptional(3L));  // 城市未知
        System.out.println(getCityWithOptional(99L)); // 城市未知

        System.out.println("\n=== ifPresentOrElse ===");
        processUser(1L);   // 找到用户：Alice
        processUser(99L);  // 用户 99 不存在

        System.out.println("\n=== orElse vs orElseGet ===");
        Optional<String> present = Optional.of("有值");
        System.out.println("-- orElse（有值时）--");
        present.orElse(createExpensiveDefault());      // 仍然调用了 createExpensiveDefault！
        System.out.println("-- orElseGet（有值时）--");
        present.orElseGet(() -> createExpensiveDefault()); // 不调用，因为有值

        Optional<String> empty = Optional.empty();
        System.out.println("-- orElse（无值时）--");
        empty.orElse(createExpensiveDefault());        // 调用
        System.out.println("-- orElseGet（无值时）--");
        empty.orElseGet(() -> createExpensiveDefault()); // 也调用
    }
}
```

**运行输出：**
```
=== 传统 null 检查 ===
北京
地址未填写
城市未知
用户不存在

=== Optional 链式写法 ===
北京
城市未知
城市未知
城市未知

=== ifPresentOrElse ===
找到用户：Alice
用户 99 不存在

=== orElse vs orElseGet ===
-- orElse（有值时）--
>>> 正在创建默认值（开销很大）...    ← 即使有值也调用了！
-- orElseGet（有值时）--             ← 没有输出，Supplier 未调用
-- orElse（无值时）--
>>> 正在创建默认值（开销很大）...
-- orElseGet（无值时）--
>>> 正在创建默认值（开销很大）...
```

---

## Demo 2：LocalDateTime 综合操作

```java
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class LocalDateTimeDemo {

    public static void main(String[] args) {

        // ===========================
        // 1. 创建日期时间
        // ===========================
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime current = LocalDateTime.now();

        System.out.println("今天：" + today);
        System.out.println("当前时间：" + now);
        System.out.println("当前日期时间：" + current);

        LocalDate birthday = LocalDate.of(1995, 5, 16);
        LocalDateTime meetingTime = LocalDateTime.of(2024, 5, 16, 14, 30, 0);
        System.out.println("生日：" + birthday);
        System.out.println("会议时间：" + meetingTime);

        // ===========================
        // 2. 日期加减操作
        // ===========================
        System.out.println("\n=== 日期加减 ===");
        LocalDate d = LocalDate.of(2024, 5, 16);
        System.out.println("原始日期：" + d);
        System.out.println("加7天：" + d.plusDays(7));                    // 2024-05-23
        System.out.println("加1个月：" + d.plusMonths(1));                // 2024-06-16
        System.out.println("减3年：" + d.minusYears(3));                  // 2021-05-16
        System.out.println("本月第一天：" + d.withDayOfMonth(1));          // 2024-05-01
        System.out.println("本月最后一天：" + d.with(TemporalAdjusters.lastDayOfMonth())); // 2024-05-31
        System.out.println("下个周一：" + d.with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // 2024-05-20

        // ===========================
        // 3. 格式化与解析
        // ===========================
        System.out.println("\n=== 格式化与解析 ===");
        DateTimeFormatter fmt1 = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss");
        DateTimeFormatter fmt2 = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter fmt3 = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String formatted1 = current.format(fmt1);
        String formatted2 = today.format(fmt2);
        System.out.println("中文格式：" + formatted1);
        System.out.println("美式格式：" + formatted2);

        // 解析字符串
        LocalDateTime parsed = LocalDateTime.parse("2024年05月16日 14:30:00", fmt1);
        LocalDate parsedDate = LocalDate.parse("2024-05-16", fmt3);
        System.out.println("解析日期时间：" + parsed);
        System.out.println("解析日期：" + parsedDate);

        // ===========================
        // 4. 计算时间差
        // ===========================
        System.out.println("\n=== 计算时间差 ===");
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 5, 16);

        // Period：日历差（年/月/日）
        Period period = Period.between(start, end);
        System.out.printf("Period 差：%d年 %d月 %d日%n",
            period.getYears(), period.getMonths(), period.getDays());

        // ChronoUnit：总天数（更常用）
        long totalDays = ChronoUnit.DAYS.between(start, end);
        System.out.println("总天数：" + totalDays + " 天");

        // Duration：精确到秒
        LocalDateTime dt1 = LocalDateTime.of(2024, 5, 16, 9, 0, 0);
        LocalDateTime dt2 = LocalDateTime.of(2024, 5, 16, 11, 45, 30);
        Duration duration = Duration.between(dt1, dt2);
        System.out.printf("会议时长：%d小时 %d分钟 %d秒%n",
            duration.toHours(),
            duration.toMinutesPart(),   // Java 9+
            duration.toSecondsPart());  // Java 9+

        // 计算年龄
        LocalDate birth = LocalDate.of(1995, 5, 16);
        LocalDate todayDate = LocalDate.of(2024, 5, 16);
        int age = Period.between(birth, todayDate).getYears();
        System.out.println("年龄：" + age + " 岁");

        // ===========================
        // 5. 日期比较
        // ===========================
        System.out.println("\n=== 日期比较 ===");
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 12, 31);
        System.out.println("d1 在 d2 之前：" + d1.isBefore(d2));
        System.out.println("d1 在 d2 之后：" + d1.isAfter(d2));
        System.out.println("d1 等于 d2：" + d1.isEqual(d2));
        System.out.println("是否是闰年：" + d1.isLeapYear());
        System.out.println("是周几：" + d1.getDayOfWeek());
    }
}
```

---

## Demo 3：ZonedDateTime 跨时区会议时间转换

**场景**：北京研发团队（Asia/Shanghai）要和纽约客户（America/New_York）及伦敦合作方（Europe/London）开会，给定北京时间，自动显示各地对应时间。

```java
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ZonedDateTimeDemo {

    public static void main(String[] args) {

        // ===========================
        // 1. 基本时区操作
        // ===========================
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId newYork  = ZoneId.of("America/New_York");
        ZoneId london   = ZoneId.of("Europe/London");
        ZoneId tokyo    = ZoneId.of("Asia/Tokyo");

        // 北京时间 2024-05-16 14:00（下午2点开会）
        LocalDateTime beijingMeetingLocal = LocalDateTime.of(2024, 5, 16, 14, 0, 0);
        ZonedDateTime beijingTime = ZonedDateTime.of(beijingMeetingLocal, shanghai);

        System.out.println("=== 全球会议时间表 ===");
        System.out.printf("北京 (Asia/Shanghai)   : %s%n", formatZdt(beijingTime));

        // 转换到其他时区
        ZonedDateTime nyTime     = beijingTime.withZoneSameInstant(newYork);
        ZonedDateTime londonTime = beijingTime.withZoneSameInstant(london);
        ZonedDateTime tokyoTime  = beijingTime.withZoneSameInstant(tokyo);

        System.out.printf("纽约 (America/New_York) : %s%n", formatZdt(nyTime));
        System.out.printf("伦敦 (Europe/London)    : %s%n", formatZdt(londonTime));
        System.out.printf("东京 (Asia/Tokyo)       : %s%n", formatZdt(tokyoTime));

        // ===========================
        // 2. 判断是否跨天
        // ===========================
        System.out.println("\n=== 跨天检查 ===");
        System.out.printf("北京 %s 开会，纽约是 %s%n",
            beijingTime.toLocalDate(),
            nyTime.toLocalDate());
        if (!beijingTime.toLocalDate().equals(nyTime.toLocalDate())) {
            System.out.println("注意：纽约已跨天！");
        }

        // ===========================
        // 3. 找一个各方都在工作时间内的会议时间
        // ===========================
        System.out.println("\n=== 寻找合适的会议窗口（北京工作时间 9-18 点）===");
        for (int hour = 9; hour <= 17; hour++) {
            ZonedDateTime candidate = ZonedDateTime.of(
                LocalDateTime.of(2024, 5, 16, hour, 0), shanghai);
            ZonedDateTime candidateNY = candidate.withZoneSameInstant(newYork);
            int nyHour = candidateNY.getHour();
            if (nyHour >= 9 && nyHour <= 17) {
                System.out.printf("北京 %02d:00 = 纽约 %02d:00 ✓ 双方均在工作时间%n",
                    hour, nyHour);
            }
        }

        // ===========================
        // 4. Instant 桥接（跨系统时间传递）
        // ===========================
        System.out.println("\n=== Instant 时间戳 ===");
        Instant instant = beijingTime.toInstant();
        System.out.println("Instant（UTC纪元秒）：" + instant.getEpochSecond());

        // 从 Instant 还原到不同时区
        ZonedDateTime restored = instant.atZone(newYork);
        System.out.println("从 Instant 还原为纽约时间：" + formatZdt(restored));

        // ===========================
        // 5. 与旧版 Date 互转
        // ===========================
        System.out.println("\n=== 旧版 Date 互转 ===");
        java.util.Date oldDate = java.util.Date.from(instant);
        System.out.println("转为 Date：" + oldDate);

        ZonedDateTime backToZdt = oldDate.toInstant().atZone(shanghai);
        System.out.println("Date 转回 ZonedDateTime：" + formatZdt(backToZdt));
    }

    static DateTimeFormatter DISPLAY_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEE) z");

    static String formatZdt(ZonedDateTime zdt) {
        return zdt.format(DISPLAY_FMT);
    }
}
```

**运行输出示例：**
```
=== 全球会议时间表 ===
北京 (Asia/Shanghai)   : 2024-05-16 14:00 (Thu) CST
纽约 (America/New_York) : 2024-05-16 02:00 (Thu) EDT
伦敦 (Europe/London)    : 2024-05-16 07:00 (Thu) BST
东京 (Asia/Tokyo)       : 2024-05-16 15:00 (Thu) JST

=== 跨天检查 ===
北京 2024-05-16 开会，纽约是 2024-05-16
（纽约凌晨2点，同一天但很不友好）

=== 寻找合适的会议窗口 ===
（结论：北京9-18点对应纽约21-次日6点，无重叠工作时间，需要一方牺牲）

=== Instant 时间戳 ===
Instant（UTC纪元秒）：1715846400

=== 旧版 Date 互转 ===
转为 Date：Thu May 16 14:00:00 CST 2024
Date 转回 ZonedDateTime：2024-05-16 14:00 (Thu) CST
```

**核心要点：**
- `withZoneSameInstant`：转换时区，代表同一个瞬间
- `withZoneSameLocal`：只改时区标签，本地时间不变（代表不同瞬间）
- `Instant` 是跨时区传递时间的统一标准（UTC 纪元秒）
