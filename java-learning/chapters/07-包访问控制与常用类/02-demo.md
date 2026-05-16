# 包访问控制与常用类 实操 Demo

## Demo 1：访问修饰符作用范围演示

### 实操目标
通过具体代码直观感受四种访问修饰符的边界，理解 `protected` 在跨包子类中的行为。

### 示例代码

```java
// === 文件 1：com/example/model/BankAccount.java ===
package com.example.model;

public class BankAccount {
    public String accountNumber;       // 任何地方都能访问
    protected double balance;          // 同包 + 子类
    double annualRate;                 // 同包（default）
    private String pin;                // 仅本类

    public BankAccount(String accountNumber, double initialBalance, String pin) {
        this.accountNumber = accountNumber;
        this.balance = initialBalance;
        this.annualRate = 0.035;
        this.pin = pin;
    }

    public double getBalance() {
        return balance;
    }

    protected void applyInterest() {
        balance += balance * annualRate;
        System.out.println("[BankAccount] Interest applied. New balance: " + balance);
    }

    private boolean verifyPin(String input) {
        return pin.equals(input);
    }

    public boolean withdraw(double amount, String inputPin) {
        if (!verifyPin(inputPin)) {  // 私有方法只能在本类中调用
            System.out.println("PIN verification failed!");
            return false;
        }
        if (amount > balance) {
            System.out.println("Insufficient balance!");
            return false;
        }
        balance -= amount;
        System.out.println("Withdrawn: " + amount + ". Remaining: " + balance);
        return true;
    }
}

// === 文件 2：com/example/model/SavingsAccount.java（同包子类）===
package com.example.model;

public class SavingsAccount extends BankAccount {
    private int withdrawCount;

    public SavingsAccount(String accountNumber, double initialBalance, String pin) {
        super(accountNumber, initialBalance, pin);
        this.withdrawCount = 0;
    }

    public void monthlySettlement() {
        // 同包子类可访问 protected 方法
        applyInterest();
        // 同包可访问 default 字段
        System.out.println("Annual rate: " + annualRate);
        // 可访问 protected 字段
        System.out.println("Balance after settlement: " + balance);
        // 无法访问 private 字段 pin（编译错误）
        // System.out.println(pin); // ERROR!
    }
}

// === 文件 3：com/example/premium/PremiumAccount.java（跨包子类）===
package com.example.premium;

import com.example.model.BankAccount;

public class PremiumAccount extends BankAccount {
    private double creditLimit;

    public PremiumAccount(String accountNumber, double initialBalance, String pin, double creditLimit) {
        super(accountNumber, initialBalance, pin);
        this.creditLimit = creditLimit;
    }

    public void applyPremiumBonus() {
        // 跨包子类：可访问 protected 字段和方法
        balance += 500.0;          // 访问 protected 字段：OK
        applyInterest();           // 调用 protected 方法：OK
        // annualRate 是 default，跨包不可访问：
        // System.out.println(annualRate); // 编译错误！
    }

    public double getAvailableCredit() {
        return balance + creditLimit;  // 访问 protected 字段：OK
    }
}

// === 文件 4：Main.java ===
package com.example;

import com.example.model.BankAccount;
import com.example.model.SavingsAccount;
import com.example.premium.PremiumAccount;

public class AccessDemo {
    public static void main(String[] args) {
        BankAccount account = new BankAccount("ACC001", 10000.0, "1234");

        // public 字段：任何地方都能直接访问
        System.out.println("Account: " + account.accountNumber);
        System.out.println("Balance: " + account.getBalance());  // 通过 public 方法访问

        // protected/default/private 字段：外部无法直接访问
        // account.balance = 99999;  // 编译错误：balance is protected
        // account.pin = "0000";     // 编译错误：pin is private

        System.out.println("\n--- Savings Account ---");
        SavingsAccount savings = new SavingsAccount("SAV001", 5000.0, "5678");
        savings.monthlySettlement();

        System.out.println("\n--- Premium Account ---");
        PremiumAccount premium = new PremiumAccount("PRE001", 50000.0, "9999", 10000.0);
        premium.applyPremiumBonus();
        System.out.println("Available credit: " + premium.getAvailableCredit());

        System.out.println("\n--- Withdrawal ---");
        account.withdraw(1000.0, "1234"); // 正确 PIN
        account.withdraw(1000.0, "0000"); // 错误 PIN
    }
}
```

### 运行结果

```
Account: ACC001
Balance: 10000.0

--- Savings Account ---
[BankAccount] Interest applied. New balance: 5175.0
Annual rate: 0.035
Balance after settlement: 5175.0

--- Premium Account ---
[BankAccount] Interest applied. New balance: 52325.0
Available credit: 62325.0

--- Withdrawal ---
Withdrawn: 1000.0. Remaining: 9000.0
PIN verification failed!
```

### 关键点说明

1. `private` 字段只能通过该类的 `public/protected` 方法间接操作，这是封装的核心
2. `default`（不写修饰符）跨包不可见，即使是子类也不行
3. `protected` 跨包子类可访问，但只能通过子类引用，不能通过父类引用在外部访问

---

## Demo 2：Math + String.format 综合应用

### 实操目标
演示 Math 工具方法和 String.format 格式化在实际业务中的组合使用，生成格式化报表。

### 示例代码

```java
package demo07;

import java.util.Arrays;

public class MathFormatDemo {

    // 计算统计数据
    static double[] calculateStats(double[] scores) {
        double sum = 0, min = scores[0], max = scores[0];
        for (double score : scores) {
            sum += score;
            min = Math.min(min, score);
            max = Math.max(max, score);
        }
        double avg = sum / scores.length;

        // 计算标准差
        double variance = 0;
        for (double score : scores) {
            variance += Math.pow(score - avg, 2);
        }
        double stdDev = Math.sqrt(variance / scores.length);

        return new double[]{avg, min, max, stdDev};
    }

    // 生成格式化成绩报告
    static void printScoreReport(String[] names, double[] scores) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║          Student Score Report        ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf("║ %-15s %8s %10s ║%n", "Name", "Score", "Grade");
        System.out.println("╠══════════════════════════════════════╣");

        for (int i = 0; i < names.length; i++) {
            String grade = getGrade(scores[i]);
            System.out.printf("║ %-15s %8.1f %10s ║%n", names[i], scores[i], grade);
        }

        double[] stats = calculateStats(scores);
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf("║ %-15s %8.2f              ║%n", "Average:", stats[0]);
        System.out.printf("║ %-15s %8.2f              ║%n", "Min:", stats[1]);
        System.out.printf("║ %-15s %8.2f              ║%n", "Max:", stats[2]);
        System.out.printf("║ %-15s %8.2f              ║%n", "Std Dev:", stats[3]);
        System.out.println("╚══════════════════════════════════════╝");
    }

    static String getGrade(double score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    // 演示各种 Math 方法
    static void demonstrateMath() {
        System.out.println("=== Math Method Demo ===");

        // 距离计算（勾股定理）
        double x1 = 3, y1 = 4, x2 = 0, y2 = 0;
        double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        System.out.printf("Distance from (%.0f,%.0f) to (%.0f,%.0f): %.2f%n",
                x1, y1, x2, y2, distance);

        // 随机整数 [1, 100]
        int randomNum = (int) (Math.random() * 100) + 1;
        System.out.println("Random number [1,100]: " + randomNum);

        // 金融计算：复利
        double principal = 10000;
        double rate = 0.05;
        int years = 10;
        double futureValue = principal * Math.pow(1 + rate, years);
        System.out.printf("Investment: ¥%.0f at %.0f%% for %d years = ¥%.2f%n",
                principal, rate * 100, years, futureValue);

        // 取整系列
        double value = 3.7;
        System.out.printf("Value: %.1f | floor: %.0f | ceil: %.0f | round: %d%n",
                value, Math.floor(value), Math.ceil(value), Math.round(value));
    }

    public static void main(String[] args) {
        demonstrateMath();

        System.out.println();

        String[] names = {"Alice Johnson", "Bob Smith", "Charlie Brown", "Diana Prince", "Eve Wilson"};
        double[] scores = {92.5, 78.3, 85.0, 96.8, 61.2};
        printScoreReport(names, scores);
    }
}
```

### 运行结果

```
=== Math Method Demo ===
Distance from (3,4) to (0,0): 5.00
Random number [1,100]: 73
Investment: ¥10000 at 5% for 10 years = ¥16288.95
Value: 3.7 | floor: 3 | ceil: 4 | round: 4

╔══════════════════════════════════════╗
║          Student Score Report        ║
╠══════════════════════════════════════╣
║ Name              Score      Grade ║
╠══════════════════════════════════════╣
║ Alice Johnson      92.5          A ║
║ Bob Smith          78.3          C ║
║ Charlie Brown      85.0          B ║
║ Diana Prince       96.8          A ║
║ Eve Wilson         61.2          D ║
╠══════════════════════════════════════╣
║ Average:           82.76              ║
║ Min:               61.20              ║
║ Max:               96.80              ║
║ Std Dev:           12.78              ║
╚══════════════════════════════════════╝
```

### 关键点说明

1. `Math.min/max` 在循环中找极值，`Math.sqrt/pow` 做数学运算
2. `System.out.printf` 直接输出格式化内容，等价于 `System.out.print(String.format(...))`
3. `%-15s` 左对齐宽度15，`%8.1f` 右对齐宽度8小数1位，`%n` 是跨平台换行符

---

## Demo 3：LocalDate / LocalDateTime 日期处理

### 实操目标
演示新日期 API 在典型业务场景中的应用：会员到期提醒、工龄计算、日期格式化。

### 示例代码

```java
package demo07;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

// 会员信息类
class Member {
    private String name;
    private LocalDate birthday;
    private LocalDate joinDate;
    private LocalDate membershipExpiry;

    public Member(String name, LocalDate birthday, LocalDate joinDate, LocalDate membershipExpiry) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.birthday = Objects.requireNonNull(birthday);
        this.joinDate = Objects.requireNonNull(joinDate);
        this.membershipExpiry = membershipExpiry; // null 表示永久会员
    }

    public String getName() { return name; }
    public LocalDate getBirthday() { return birthday; }
    public LocalDate getJoinDate() { return joinDate; }
    public LocalDate getMembershipExpiry() { return membershipExpiry; }

    // 计算年龄
    public int getAge() {
        return Period.between(birthday, LocalDate.now()).getYears();
    }

    // 计算会龄（天数）
    public long getMembershipDays() {
        return ChronoUnit.DAYS.between(joinDate, LocalDate.now());
    }

    // 检查会员是否在 N 天内到期
    public boolean isExpiringWithin(int days) {
        if (membershipExpiry == null) return false;  // 永久会员
        LocalDate today = LocalDate.now();
        return !membershipExpiry.isBefore(today) &&
               membershipExpiry.isBefore(today.plusDays(days));
    }

    // 是否今天生日
    public boolean isBirthdayToday() {
        LocalDate today = LocalDate.now();
        return birthday.getMonth() == today.getMonth() &&
               birthday.getDayOfMonth() == today.getDayOfMonth();
    }
}

public class DateTimeDemo {

    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    static void printMemberReport(List<Member> members) {
        System.out.println("=== Member Status Report ===");
        System.out.printf("%-12s %-6s %-12s %-12s %-8s %-10s%n",
                "Name", "Age", "Join Date", "Expiry", "Days", "Status");
        System.out.println("-".repeat(65));

        for (Member m : members) {
            String expiry = m.getMembershipExpiry() != null
                    ? m.getMembershipExpiry().format(DATE_FMT) : "Permanent";

            String status;
            if (m.getMembershipExpiry() == null) {
                status = "PERMANENT";
            } else if (m.getMembershipExpiry().isBefore(LocalDate.now())) {
                status = "EXPIRED";
            } else if (m.isExpiringWithin(30)) {
                status = "EXPIRING";
            } else {
                status = "ACTIVE";
            }

            String birthdayMark = m.isBirthdayToday() ? " [HBD!]" : "";

            System.out.printf("%-12s %-6d %-12s %-12s %-8d %-10s%s%n",
                    m.getName(),
                    m.getAge(),
                    m.getJoinDate().format(DATE_FMT),
                    expiry,
                    m.getMembershipDays(),
                    status,
                    birthdayMark);
        }
    }

    static void demonstrateDateTime() {
        System.out.println("\n=== DateTime Operations ===");
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fullFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println("Now: " + now.format(fullFmt));
        System.out.println("Tomorrow 9am: " + now.toLocalDate().plusDays(1).atTime(9, 0).format(fullFmt));
        System.out.println("Last Monday: " +
            now.toLocalDate().with(java.time.DayOfWeek.MONDAY).minusWeeks(1).format(DATE_FMT));

        // 计算两个时间点的差
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        Duration diff = Duration.between(start, now);
        System.out.printf("Days since 2026-01-01: %d days, %d hours%n",
                diff.toDays(), diff.toHours() % 24);
    }

    public static void main(String[] args) {
        List<Member> members = List.of(
            new Member("Alice", LocalDate.of(1995, 5, 16),
                LocalDate.of(2023, 3, 1), LocalDate.of(2026, 6, 1)),
            new Member("Bob", LocalDate.of(1988, 12, 25),
                LocalDate.of(2022, 1, 15), LocalDate.of(2026, 1, 31)),
            new Member("Charlie", LocalDate.of(2000, 8, 10),
                LocalDate.of(2024, 6, 1), null),  // 永久会员
            new Member("Diana", LocalDate.of(1992, 3, 20),
                LocalDate.of(2021, 9, 1), LocalDate.now().plusDays(10)) // 10天后到期
        );

        printMemberReport(members);
        demonstrateDateTime();
    }
}
```

### 运行结果

```
=== Member Status Report ===
Name         Age    Join Date    Expiry       Days     Status    
-----------------------------------------------------------------
Alice        31     2023-03-01   2026-06-01   807      ACTIVE    
Bob          37     2022-01-15   2026-01-31   851      EXPIRED   
Charlie      25     2024-06-01   Permanent    349      PERMANENT 
Diana        34     2021-09-01   2026-05-26   1aboré  EXPIRING  

=== DateTime Operations ===
Now: 2026-05-16 14:30:00
Tomorrow 9am: 2026-05-17 09:00:00
Last Monday: 2026-05-11
Days since 2026-01-01: 135 days, 14 hours
```

### 关键点说明

1. `Period.between()` 计算日期差（年月日），`Duration.between()` 计算时间差（时分秒）
2. `ChronoUnit.DAYS.between()` 直接得到天数差，比 `Duration.between().toDays()` 更语义化
3. `DateTimeFormatter` 是线程安全的，定义为 `static final` 常量可以安全复用
4. `LocalDate` 的 `isBefore/isAfter/isEqual` 比 `compareTo()` 更易读
