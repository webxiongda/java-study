# Chapter 02 Java 基础语法 - 自测与验收

> 模板参见 `docs/superpowers/specs/2026-05-25-check-template.md`
> 覆盖率自检:`node scripts/check-coverage.mjs '^02-'`

---

### Q1 [L1·概念·章节内测] 8 种基本类型、字节数、默认值、switch 支持范围

**考点**: 变量声明与 8 种基本类型, 类型转换, switch
**参考答案**:

8 种基本数据类型:

| 类型 | 字节数 | 默认值 | 备注 |
|------|--------|--------|------|
| `byte` | 1 | 0 | -128 ~ 127 |
| `short` | 2 | 0 | -32768 ~ 32767 |
| `int` | 4 | 0 | 最常用整数,字面量默认是 int |
| `long` | 8 | 0L | 字面量必须加 L |
| `float` | 4 | 0.0f | 字面量必须加 f |
| `double` | 8 | 0.0d | 浮点首选 |
| `char` | 2 | `' '` (空) | 存 Unicode 字符,可参与算术(自动提升 int) |
| `boolean` | 规范未定义(通常 1 字节) | false | **不能用于 switch** |

**switch 支持的类型**:`byte` / `short` / `char` / `int` 及其包装类、`String` (JDK 7+)、枚举、`record` 模式 (JDK 21)。**不支持**:`boolean` / `long` / `float` / `double`。

**默认值场景**:成员变量自动赋默认值;**局部变量必须手动初始化**,否则编译报错。

**🔥追问**: 为什么 long 和 double 不能用于 switch?(答:switch 跳转表用 int 索引)

---

### Q2 [L2·Debug·章节内测] 下面代码隐藏 3 个陷阱,找出并修复

**考点**: 类型转换, 整数除法, byte 溢出, 浮点数精度问题
**参考答案**:

```java
public class CalcDemo {
    public static void main(String[] args) {
        int x = 10;
        int y = 3;
        double result = x / y;                      // 陷阱 1
        System.out.println(result);                 // 输出 3.0,不是 3.333...

        byte small = 200;                            // 陷阱 2

        double price = 0.1 + 0.2;                    // 陷阱 3
        if (price == 0.3) {                          // 陷阱 3 续:浮点 == 比较
            System.out.println("ok");
        }
    }
}
```

**陷阱清单**:

1. **整数除法**:`x / y` 都是 int,先做整数除法 = 3,再赋给 double = 3.0。修复:`double result = (double) x / y;` 或 `x * 1.0 / y`。
2. **byte 越界**:`byte` 范围 -128~127,字面量 200 超出 → **编译报错**`possible lossy conversion`。修复:`byte small = (byte) 200;`(截断后得 -56,通常不是想要的);更合理改用 `short` 或 `int`。
3. **浮点精度**:`0.1 + 0.2 = 0.30000000000000004 ≠ 0.3`,IEEE 754 二进制无法精确表示十进制 0.1。任何 `double == 0.3` 这种比较都不可靠。修复:
   - 金额计算用 `BigDecimal`(参数必须用 String:`new BigDecimal("0.1")`,**不能** `new BigDecimal(0.1)`)
   - 容差比较:`Math.abs(price - 0.3) < 1e-9`

**🔥追问**: `short s = 1; s = s + 1;` 为什么编译报错,但 `s += 1;` 不报错?

---

### Q3 [L2·代码阅读·章节内测] 解释下面代码每行输出,讲清 `&&`/`||` 短路与 `++` 求值顺序

**考点**: 运算符, 短路求值, 自增自减, 位运算
**参考答案**:

```java
public class OperatorDemo {
    public static void main(String[] args) {
        int x = 0;
        if (x != 0 && 10 / x > 1) { /* A */ } else { /* B */ }   // 输出 B

        int count = 0;
        if (true || (++count > 0)) {
            System.out.println("count=" + count);                 // 输出 count=0
        }

        int a = 5;
        int b = a++;                                              // b=5,a→6
        int c = ++a;                                              // a→7,c=7
        System.out.println("a=" + a + " b=" + b + " c=" + c);     // a=7 b=5 c=7

        int num = 8;
        System.out.println(num << 1);                             // 16 (左移=×2)
        System.out.println(num & 1);                              // 0 (判奇偶)
    }
}
```

**逐行解释**:

1. **`&&` 短路**:`x != 0` 为 false → 整体已确定 false,**右边不执行**,跳过除零异常,进 else 分支输出 B。
2. **`||` 短路**:左边 `true` 整体已确定 true → `++count` 不执行 → `count` 仍是 0。**这是防御性编程的常见手段**(防 NPE / 除零)。
3. **后置自增 `a++`**:返回 a 的旧值再自增,所以 `b = 5`,接着 `a = 6`。
4. **前置自增 `++a`**:先自增再返回,`a` 从 6 升 7,`c = 7`。
5. **位运算**:`<<1` 等价 ×2,`& 1` 提取最低位判奇偶,比 `% 2 == 0` 更快(JIT 通常也优化,但写法更明确)。

**🔥追问**: `&` 和 `&&` 有什么区别?(答:`&` 是位运算 + 不短路的逻辑与,两侧都求值)

---

### Q4 [L2·代码编写·章节内测] 用 Scanner + switch 实现成绩分级,处理非法输入

**考点**: 流程控制, Scanner 输入, switch, 输入校验
**参考答案**:

```java
import java.util.Scanner;

public class GradeReader {
    public static String getGrade(int score) {
        if (score < 0 || score > 100) return "无效分数";
        return switch (score / 10) {
            case 10, 9 -> "A(优秀)";
            case 8 -> "B(良好)";
            case 7 -> "C(中等)";
            case 6 -> "D(及格)";
            default -> "F(不及格)";
        };
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("学生人数: ");
        int n = scanner.nextInt();
        int sum = 0;

        for (int i = 1; i <= n; i++) {
            int score;
            while (true) {
                System.out.print("第 " + i + " 个学生分数(0-100): ");
                score = scanner.nextInt();
                if (score >= 0 && score <= 100) break;
                System.out.println("非法,重输!");
            }
            sum += score;
            System.out.println("  → " + getGrade(score));
        }
        System.out.printf("平均分: %.2f%n", (double) sum / n);
        scanner.close();
    }
}
```

**关键点**:
- `switch` 表达式(JDK 14+)无 fall-through,避免漏 `break` 陷阱
- `score / 10` 把 0-100 映射到 0-10,把 11 个分支压缩为 5 个
- 用 `while(true) + break` 实现"校验失败重输"
- 平均值要 `(double) sum / n`,不能 `sum / n`(整数除法陷阱)
- `%.2f` 保留 2 位小数,`%n` 是平台无关换行符

**🔥追问**: `nextInt()` 后立刻 `nextLine()` 为什么读到空串?(答:`nextInt` 不消费换行符,残留在缓冲区被 `nextLine` 立即读到)

---

### Q5 [L3·场景设计·面试高频] 优惠券模块为什么不能用 double?设计一个精确的计算方案

**考点**: 浮点数精度问题, BigDecimal, 金额计算
**参考答案**:

**业务场景**:电商优惠券,原价 `0.1` 元 ×3 件 + 满减 `0.1` 元 = 应付 `0.2` 元。

**用 double 的灾难**:

```java
double sum = 0.1 + 0.1 + 0.1;     // 0.30000000000000004
double pay = sum - 0.1;            // 0.20000000000000004
boolean isFree = (pay == 0.2);     // false! 用户该付的 0.2 元被判定为"未到金额"
```

**正确做法 — BigDecimal**:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class CouponCalculator {
    public static BigDecimal finalPrice(BigDecimal original, BigDecimal discountRate, BigDecimal rebate) {
        // 折扣
        BigDecimal price = original.multiply(discountRate);
        // 满 100 减
        if (price.compareTo(new BigDecimal("100")) >= 0) {
            price = price.subtract(rebate);
        }
        // 最低 1 元保护
        BigDecimal min = BigDecimal.ONE;
        if (price.compareTo(min) < 0) price = min;
        // 保留 2 位,银行家舍入(避免持续四舍五入造成偏差)
        return price.setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

**5 个必须记的陷阱**:

1. **不能用 `new BigDecimal(0.1)`** — 接收 double,精度已丢失。必须 `new BigDecimal("0.1")` (String 参数)。
2. **不能用 `equals` 比较** — `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` 为 false (scale 不同)。用 `compareTo == 0`。
3. **必须显式 setScale** — 除法 `a.divide(b)` 若除不尽抛 `ArithmeticException`,必须 `divide(b, 2, RoundingMode.HALF_EVEN)`。
4. **舍入模式选 HALF_EVEN(银行家舍入)** — 大数据量统计下,纯四舍五入会系统性偏高。
5. **DB 字段用 `DECIMAL(M, N)`** — 不用 `FLOAT`/`DOUBLE`。MyBatis 映射 `BigDecimal`。

**面试 2 分钟讲法**:

> "金额绝对不能用 double。double 是 IEEE 754 二进制浮点,0.1 这种十进制小数本质上是无限循环二进制,所以 0.1 + 0.2 ≠ 0.3。生产用 BigDecimal,构造必须用 String,比较用 compareTo,除法必须给 scale 和舍入模式(我们用 HALF_EVEN 银行家舍入),DB 字段 DECIMAL。这套规矩在金融、电商都是硬性要求。"

**🔥追问**: BigDecimal 不可变,大量计算时会创建大量临时对象,如何优化?(答:中间结果在循环外提取;高频路径可考虑 long 存"分"为单位,出口转 BigDecimal)

---

## 通过标准

- [ ] 能默写 8 种基本类型 + 字节数 + 默认值 + switch 不支持的 4 种
- [ ] 能识别整数除法 / byte 越界 / 浮点精度三大陷阱并修复
- [ ] 能讲清 `&&` `||` 短路含义 + 前置/后置自增的求值顺序
- [ ] 能现场写 Scanner + switch 表达式 + 输入校验的成绩程序
- [ ] 能讲清"为什么金额必须用 BigDecimal" + 5 个使用陷阱
