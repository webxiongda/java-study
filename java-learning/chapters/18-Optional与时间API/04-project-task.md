# Chapter 18 Optional 与时间 API - 项目任务

## 业务背景

你是一个会议室预订系统的后端开发工程师。系统需要处理各种时间格式的输入、计算会议时长、检测时间冲突，并查找可用会议室。这一切都涉及大量的时间处理逻辑。

本任务要求你实现 `BookingUtils` 工具类和相关业务方法，综合运用 `Optional`、`LocalDateTime`、`Duration`、`DateTimeFormatter` 等新时间 API。

---

## 数据模型

```java
import java.time.LocalDateTime;

public class Room {
    private String roomId;
    private String name;
    private int capacity;
    private boolean available;  // 基础可用状态（未被禁用）

    public Room(String roomId, String name, int capacity, boolean available) {
        this.roomId = roomId;
        this.name = name;
        this.capacity = capacity;
        this.available = available;
    }

    public String getRoomId()   { return roomId; }
    public String getName()     { return name; }
    public int getCapacity()    { return capacity; }
    public boolean isAvailable(){ return available; }
}

public class Booking {
    private String roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Booking(String roomId, LocalDateTime startTime, LocalDateTime endTime) {
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getRoomId()           { return roomId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime()   { return endTime; }
}
```

---

## 任务要求

### 任务1：实现 BookingUtils 工具类

```java
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public class BookingUtils {

    // 禁止实例化（工具类规范）
    private BookingUtils() {}

    /**
     * 任务1-A：解析时间字符串
     * 支持的格式："yyyy-MM-dd HH:mm" 和 "yyyy/MM/dd HH:mm" 两种
     * 格式错误时返回 Optional.empty()，不要抛异常给调用者
     */
    public static Optional<LocalDateTime> parseBookingTime(String dateTimeStr) {
        // TODO：
        // 1. 处理 null 和空字符串
        // 2. 尝试用 "yyyy-MM-dd HH:mm" 格式解析
        // 3. 失败则尝试 "yyyy/MM/dd HH:mm"
        // 4. 都失败则返回 Optional.empty()
        return Optional.empty(); // 替换此行
    }

    /**
     * 任务1-B：计算会议时长（分钟）
     * end 必须在 start 之后，否则返回 0
     */
    public static long getDuration(LocalDateTime start, LocalDateTime end) {
        // TODO：使用 Duration.between，注意参数顺序和负值处理
        return 0; // 替换此行
    }

    /**
     * 任务1-C：判断两个时间段是否冲突
     * 首尾相接（end == other.start）不算冲突
     */
    public static boolean isOverlap(
            LocalDateTime start1, LocalDateTime end1,
            LocalDateTime start2, LocalDateTime end2) {
        // TODO：使用时间段重叠公式
        return false; // 替换此行
    }

    /**
     * 任务1-D：格式化时间为显示格式
     * 输出格式：例如 "2024年05月16日 14:30"
     */
    public static String formatForDisplay(LocalDateTime dt) {
        // TODO
        return ""; // 替换此行
    }

    /**
     * 任务2：查找可用会议室（Optional 实战）
     * 条件：
     * 1. room.isAvailable() == true（未被禁用）
     * 2. 该会议室在 [start, end] 时间段内没有已存在的预订冲突
     * 返回第一个满足条件的会议室
     */
    public static Optional<Room> findAvailableRoom(
            List<Room> rooms,
            List<Booking> existingBookings,
            LocalDateTime start,
            LocalDateTime end) {
        // TODO：
        // 1. 过滤 available == true 的会议室
        // 2. 对每个会议室，检查 existingBookings 中是否有同 roomId 的预订与 [start,end] 冲突
        // 3. 返回第一个无冲突的会议室（用 Optional）
        return Optional.empty(); // 替换此行
    }
}
```

### 任务2：编写测试主类

```java
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class BookingSystem {

    public static void main(String[] args) {

        // 测试数据：会议室
        List<Room> rooms = Arrays.asList(
            new Room("R001", "小型会议室A", 6,  true),
            new Room("R002", "中型会议室B", 12, true),
            new Room("R003", "大型会议室C", 30, false), // 已停用
            new Room("R004", "VIP会议室",   8,  true)
        );

        // 测试数据：已有预订
        LocalDateTime base = LocalDateTime.of(2024, 5, 16, 0, 0);
        List<Booking> bookings = Arrays.asList(
            new Booking("R001", base.withHour(9),  base.withHour(11)),  // R001 上午占用
            new Booking("R001", base.withHour(14), base.withHour(16)),  // R001 下午占用
            new Booking("R002", base.withHour(10), base.withHour(12)),  // R002 上午占用
            new Booking("R004", base.withHour(9),  base.withHour(18))   // VIP 全天占用
        );

        // =========================
        // 测试 parseBookingTime
        // =========================
        System.out.println("=== 解析时间 ===");
        BookingUtils.parseBookingTime("2024-05-16 14:30")
            .ifPresentOrElse(
                dt -> System.out.println("解析成功：" + dt),
                () -> System.out.println("解析失败")
            );

        BookingUtils.parseBookingTime("2024/05/16 14:30")
            .ifPresentOrElse(
                dt -> System.out.println("解析成功（斜杠格式）：" + dt),
                () -> System.out.println("解析失败")
            );

        BookingUtils.parseBookingTime("invalid date")
            .ifPresentOrElse(
                dt -> System.out.println("解析成功：" + dt),
                () -> System.out.println("格式错误，返回 empty")
            );

        // =========================
        // 测试 getDuration
        // =========================
        System.out.println("\n=== 计算时长 ===");
        LocalDateTime start = base.withHour(9);
        LocalDateTime end   = base.withHour(11).withMinute(30);
        System.out.println("09:00 - 11:30 时长：" + BookingUtils.getDuration(start, end) + " 分钟");

        // =========================
        // 测试 formatForDisplay
        // =========================
        System.out.println("\n=== 格式化显示 ===");
        System.out.println(BookingUtils.formatForDisplay(base.withHour(14).withMinute(30)));

        // =========================
        // 测试 findAvailableRoom
        // =========================
        System.out.println("\n=== 查找可用会议室 ===");

        // 场景1：找 11:00-13:00 的会议室（R001 13点后才可用，R002 12点后可用，应返回 R002）
        LocalDateTime s1 = base.withHour(11);
        LocalDateTime e1 = base.withHour(13);
        BookingUtils.findAvailableRoom(rooms, bookings, s1, e1)
            .ifPresentOrElse(
                r -> System.out.printf("找到会议室：%s（%s）%n", r.getRoomId(), r.getName()),
                () -> System.out.println("无可用会议室")
            );

        // 场景2：找 09:00-18:00 全天会议室
        LocalDateTime s2 = base.withHour(9);
        LocalDateTime e2 = base.withHour(18);
        BookingUtils.findAvailableRoom(rooms, bookings, s2, e2)
            .ifPresentOrElse(
                r -> System.out.printf("找到会议室：%s（%s）%n", r.getRoomId(), r.getName()),
                () -> System.out.println("无可用会议室（期望：无）")
            );
    }
}
```

---

## 参考答案

<details>
<summary>点击展开参考答案（建议先自行实现）</summary>

```java
// 任务1-A：parseBookingTime
public static Optional<LocalDateTime> parseBookingTime(String dateTimeStr) {
    if (dateTimeStr == null || dateTimeStr.isBlank()) {
        return Optional.empty();
    }
    List<DateTimeFormatter> formatters = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );
    for (DateTimeFormatter fmt : formatters) {
        try {
            return Optional.of(LocalDateTime.parse(dateTimeStr.trim(), fmt));
        } catch (DateTimeParseException ignored) {}
    }
    return Optional.empty();
}

// 任务1-B：getDuration
public static long getDuration(LocalDateTime start, LocalDateTime end) {
    if (start == null || end == null || !end.isAfter(start)) {
        return 0;
    }
    return Duration.between(start, end).toMinutes();
}

// 任务1-C：isOverlap
public static boolean isOverlap(
        LocalDateTime start1, LocalDateTime end1,
        LocalDateTime start2, LocalDateTime end2) {
    return end1.isAfter(start2) && end2.isAfter(start1);
}

// 任务1-D：formatForDisplay
public static String formatForDisplay(LocalDateTime dt) {
    if (dt == null) return "";
    return dt.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"));
}

// 任务2：findAvailableRoom
public static Optional<Room> findAvailableRoom(
        List<Room> rooms,
        List<Booking> existingBookings,
        LocalDateTime start,
        LocalDateTime end) {
    return rooms.stream()
        .filter(Room::isAvailable)
        .filter(room -> existingBookings.stream()
            .filter(b -> b.getRoomId().equals(room.getRoomId()))
            .noneMatch(b -> isOverlap(start, end, b.getStartTime(), b.getEndTime()))
        )
        .findFirst();
}
```

</details>

---

## 验收标准

1. **parseBookingTime**：正确支持两种格式（"-" 和 "/"），格式错误时返回 `Optional.empty()` 而非抛异常，null 和空字符串均能处理。
2. **getDuration**：返回分钟数正确，`end` 不在 `start` 之后时返回 0 而非负数。
3. **isOverlap**：通过所有 6 种边界场景（完全前、紧邻前、部分重叠、包含、紧邻后、完全后）。
4. **findAvailableRoom**：正确过滤掉 `available=false` 的会议室以及有冲突预订的会议室，返回类型为 `Optional<Room>`。

---

## 常见坑

**坑1：parseBookingTime 忘记处理 null 导致 NPE**
```java
// 错误：直接调用 dateTimeStr.isBlank() 在 null 时抛 NPE
public static Optional<LocalDateTime> parseBookingTime(String dateTimeStr) {
    if (dateTimeStr.isBlank()) { ... }  // null 时 NPE！

// 正确：先检查 null
    if (dateTimeStr == null || dateTimeStr.isBlank()) {
        return Optional.empty();
    }
```

**坑2：isOverlap 使用 >= 而非 > 导致紧邻时间段被判断为冲突**
```java
// 错误：使用 >= 会把首尾相接判断为冲突
return !end1.isBefore(start2) && !end2.isBefore(start1);  // >= 语义

// 正确：用严格大于（> 代表真正重叠，= 是紧邻）
return end1.isAfter(start2) && end2.isAfter(start1);  // 严格 >
```
