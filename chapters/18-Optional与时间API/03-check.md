# Chapter 18 Optional 与时间 API - 自测题

## Q1（概念）：Optional.of、Optional.ofNullable、Optional.empty 分别在什么场景下使用？

**参考答案：**

| 方法 | 适用场景 | 传入 null 时 |
|------|---------|------------|
| `Optional.of(value)` | 明确知道值不为 null，强调"此处不该为空"，如果为空说明有 bug | 立即抛 NPE |
| `Optional.ofNullable(value)` | 值可能为 null（来自外部输入、数据库查询、第三方接口等） | 返回 empty |
| `Optional.empty()` | 需要明确返回"无结果"的场景（如 Repository 查询不到数据）| — |

```java
// of：内部已确认不为 null 的配置值
Optional<String> config = Optional.of(System.getenv("APP_HOME")); // 若未配置则 NPE（期望行为）

// ofNullable：用户输入或外部数据，不确定是否为空
Optional<String> input = Optional.ofNullable(request.getParam("name"));

// empty：方法返回"无结果"
public Optional<User> findByEmail(String email) {
    User user = db.queryByEmail(email);
    return Optional.ofNullable(user);   // null 自动变 empty
    // 或显式：
    // return user != null ? Optional.of(user) : Optional.empty();
}
```

**实践原则：**
- 绝大多数业务代码用 `ofNullable`（更安全）
- 内部确认非空时用 `of`（相当于断言）
- 方法返回无结果时用 `empty()`（配合 `ofNullable` 等价）

---

## Q2（概念）：LocalDate、LocalDateTime、ZonedDateTime 各自适用什么场景？

**参考答案：**

**LocalDate（纯日期，无时间无时区）：**
- 场景：生日、节假日、合同有效期、账单日
- 特点：没有时区概念，`LocalDate.now()` 依赖系统时区
```java
LocalDate birthday = LocalDate.of(1995, 5, 16);
LocalDate contractExpiry = LocalDate.of(2025, 12, 31);
boolean isExpired = LocalDate.now().isAfter(contractExpiry);
```

**LocalDateTime（日期+时间，无时区）：**
- 场景：记录本地事件时间（不涉及跨时区）、数据库存储（通常配合时区上下文）
- 特点：适合单一时区应用内部时间处理
```java
LocalDateTime orderTime = LocalDateTime.of(2024, 5, 16, 14, 30);
LocalDateTime oneHourLater = orderTime.plusHours(1);
```

**ZonedDateTime（日期+时间+时区）：**
- 场景：跨时区的会议、国际机票、全球用户系统、与外部系统交换时间
- 特点：包含时区，可正确转换到任意时区
```java
ZonedDateTime event = ZonedDateTime.of(
    LocalDateTime.of(2024, 5, 16, 14, 0),
    ZoneId.of("Asia/Shanghai")
);
ZonedDateTime nyEvent = event.withZoneSameInstant(ZoneId.of("America/New_York"));
```

**选择原则：** 如果业务只在一个时区内（如国内业务），用 `LocalDate/LocalDateTime` 足够；一旦涉及多时区用户或与境外系统交互，必须用 `ZonedDateTime` 或 `Instant`。

---

## Q3（实操）：找出以下代码的问题并改正

**有问题的代码：**
```java
// 代码片段1
Optional<String> opt1 = Optional.of(null);  // Line A

// 代码片段2
Optional<User> optUser = findUser(userId);
if (optUser.isPresent()) {
    User user = optUser.get();
    System.out.println(user.getName());
}

// 代码片段3
LocalDate d1 = LocalDate.of(2024, 1, 31);
LocalDate d2 = d1.plusMonths(1);  // 期望得到 2024-02-31
System.out.println(d2);

// 代码片段4
Period p = Period.between(
    LocalDate.of(2024, 1, 1),
    LocalDate.of(2024, 6, 15)
);
System.out.println("共 " + p.getDays() + " 天");  // 期望输出总天数
```

**参考答案：**

**问题1（Line A）：`Optional.of(null)` 抛 NPE**
```java
// 错误原因：of() 不接受 null
// 改正：
Optional<String> opt1 = Optional.ofNullable(null); // 返回 Optional.empty()
```

**问题2：滥用 isPresent + get，等同于手动 null 检查，失去 Optional 的意义**
```java
// 改正：使用 ifPresent 或 map
findUser(userId).ifPresent(user -> System.out.println(user.getName()));

// 或：
String name = findUser(userId).map(User::getName).orElse("未知用户");
System.out.println(name);
```

**问题3：2024年2月没有31日，`plusMonths` 会自动调整到月末**
```java
// 这不是 bug，是 Java 的正确行为：
LocalDate d2 = LocalDate.of(2024, 1, 31).plusMonths(1);
System.out.println(d2); // 输出 2024-02-29（2024年是闰年）

// 如果期望"同月同日"，需要注意月末溢出问题
// 这是正确行为，不是错误，但要在业务逻辑中处理好溢出
```

**问题4：`Period.getDays()` 返回的是"日历上的剩余天数"，不是总天数**
```java
// Period.between(2024-01-01, 2024-06-15)
// = 5个月 + 14天，getDays() 返回 14，不是总天数！

// 改正：使用 ChronoUnit.DAYS.between 获取总天数
long totalDays = ChronoUnit.DAYS.between(
    LocalDate.of(2024, 1, 1),
    LocalDate.of(2024, 6, 15)
);
System.out.println("共 " + totalDays + " 天"); // 166
```

---

## Q4（实操）：实现一个方法，将出生日期字符串（格式 "yyyy-MM-dd"）解析后计算年龄，处理格式错误情况

**参考答案：**

```java
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class AgeCalculator {

    static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 根据生日字符串计算年龄
     * @param birthdayStr 格式："yyyy-MM-dd"，如 "1995-05-16"
     * @return Optional<Integer> 成功返回年龄，格式错误或未来日期返回 empty
     */
    public static Optional<Integer> calculateAge(String birthdayStr) {
        if (birthdayStr == null || birthdayStr.isBlank()) {
            return Optional.empty();
        }

        try {
            LocalDate birthday = LocalDate.parse(birthdayStr, FORMATTER);
            LocalDate today = LocalDate.now();

            if (birthday.isAfter(today)) {
                System.out.println("警告：生日不能是未来日期");
                return Optional.empty();
            }

            int age = Period.between(birthday, today).getYears();
            return Optional.of(age);

        } catch (DateTimeParseException e) {
            System.out.println("日期格式错误：" + birthdayStr);
            return Optional.empty();
        }
    }

    public static void main(String[] args) {
        // 正常情况
        calculateAge("1995-05-16")
            .ifPresentOrElse(
                age -> System.out.println("年龄：" + age),
                () -> System.out.println("无法计算年龄")
            );

        // 格式错误
        calculateAge("1995/05/16")
            .ifPresentOrElse(
                age -> System.out.println("年龄：" + age),
                () -> System.out.println("无法计算年龄")
            );

        // null
        calculateAge(null)
            .ifPresentOrElse(
                age -> System.out.println("年龄：" + age),
                () -> System.out.println("无法计算年龄")
            );

        // 链式使用
        String result = calculateAge("1995-05-16")
            .map(age -> age + " 岁")
            .orElse("年龄未知");
        System.out.println(result);
    }
}
```

**关键点：**
- 捕获 `DateTimeParseException` 而非 `Exception`（更精确）
- 返回 `Optional<Integer>` 而非 `-1` 等魔法值
- 日期验证（不能是未来日期）也包含在方法内

---

## Q5（项目应用）：实现时间段重叠检测

**题目**：会议室预订系统中，判断两个时间段是否存在冲突（时间重叠）。

```java
// 已知时间段：
// 已有预订：09:00 - 11:00
// 新申请1：10:30 - 12:00  → 冲突
// 新申请2：11:00 - 13:00  → 不冲突（紧接）
// 新申请3：07:00 - 09:00  → 不冲突（紧接前面）
// 新申请4：08:00 - 10:00  → 冲突
```

**参考答案：**

```java
import java.time.LocalDateTime;
import java.time.LocalTime;

public class MeetingOverlapCheck {

    /**
     * 判断两个时间段是否重叠
     * 时间段首尾相接（end == other.start）不算重叠
     *
     * @param start1 时间段1开始
     * @param end1   时间段1结束
     * @param start2 时间段2开始
     * @param end2   时间段2结束
     * @return true 表示重叠
     */
    public static boolean isOverlap(
            LocalDateTime start1, LocalDateTime end1,
            LocalDateTime start2, LocalDateTime end2) {

        // 核心公式：两段不重叠的条件是：一段完全在另一段之前或之后
        // 不重叠：end1 <= start2 或 end2 <= start1
        // 重叠：!(不重叠) = end1 > start2 && end2 > start1
        return end1.isAfter(start2) && end2.isAfter(start1);
    }

    public static void main(String[] args) {
        LocalDateTime base = LocalDateTime.of(2024, 5, 16, 0, 0);

        // 已有预订：09:00 - 11:00
        LocalDateTime existStart = base.withHour(9);
        LocalDateTime existEnd   = base.withHour(11);

        // 测试各场景
        System.out.printf("10:30-12:00 冲突？%s（期望：true）%n",
            isOverlap(existStart, existEnd, base.withHour(10).withMinute(30), base.withHour(12)));

        System.out.printf("11:00-13:00 冲突？%s（期望：false）%n",
            isOverlap(existStart, existEnd, base.withHour(11), base.withHour(13)));

        System.out.printf("07:00-09:00 冲突？%s（期望：false）%n",
            isOverlap(existStart, existEnd, base.withHour(7), base.withHour(9)));

        System.out.printf("08:00-10:00 冲突？%s（期望：true）%n",
            isOverlap(existStart, existEnd, base.withHour(8), base.withHour(10)));

        System.out.printf("09:00-11:00 冲突？%s（期望：true，完全重合）%n",
            isOverlap(existStart, existEnd, base.withHour(9), base.withHour(11)));

        System.out.printf("08:00-12:00 冲突？%s（期望：true，包含关系）%n",
            isOverlap(existStart, existEnd, base.withHour(8), base.withHour(12)));
    }
}
```

**输出：**
```
10:30-12:00 冲突？true（期望：true）
11:00-13:00 冲突？false（期望：false）
07:00-09:00 冲突？false（期望：false）
08:00-10:00 冲突？true（期望：true）
09:00-11:00 冲突？true（期望：true，完全重合）
08:00-12:00 冲突？true（期望：true，包含关系）
```

**重叠公式记忆技巧：**
```
两段重叠 ⟺ 不是（一段完全在另一段前面）
          ⟺ !(end1 <= start2 || end2 <= start1)
          ⟺  end1 > start2  &&  end2 > start1
```
