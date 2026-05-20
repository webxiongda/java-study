# Chapter 04 - 数组与字符串 | 知识检测

> 完成以下 5 道题，再对照参考答案自查。建议先独立作答，再看答案。

---

## Q1 概念题 — 字符串比较

**题目：** 阅读以下代码，判断每行输出的结果，并说明原因。

```java
public class StringCompareTest {
    public static void main(String[] args) {
        String s1 = "hello";
        String s2 = "hello";
        String s3 = new String("hello");
        String s4 = s3.intern();

        System.out.println(s1 == s2);           // 第 1 行
        System.out.println(s1 == s3);           // 第 2 行
        System.out.println(s1.equals(s3));      // 第 3 行
        System.out.println(s1 == s4);           // 第 4 行
        System.out.println(s1.equals(s4));      // 第 5 行
    }
}
```

---

**参考答案：**

| 行 | 输出 | 原因 |
|----|------|------|
| 第 1 行 | `true` | `s1` 和 `s2` 都是字面量，指向常量池中同一个 `"hello"` 对象 |
| 第 2 行 | `false` | `s3 = new String("hello")` 在堆中新建了对象，`s1` 指向常量池，两者引用不同 |
| 第 3 行 | `true` | `equals` 比较内容，两个字符串内容都是 `"hello"` |
| 第 4 行 | `true` | `intern()` 返回常量池中的引用，与 `s1` 指向同一对象 |
| 第 5 行 | `true` | 内容相同 |

**核心原则：** 比较字符串内容时，永远使用 `equals()`，不要用 `==`。将已知的常量放在前面（如 `"yes".equals(userInput)`）可避免 `NullPointerException`。

---

## Q2 概念题 — StringBuilder vs StringBuffer vs String

**题目：** 在以下 3 个场景中，应分别选择 `String`、`StringBuilder`，还是 `StringBuffer`？请说明理由。

1. 存储用户的姓名，只读取，不修改
2. 在单线程环境下，将 1 万个数字拼接成一个长字符串
3. 多个线程同时向一个公共字符串对象追加日志内容

---

**参考答案：**

1. **用 `String`**。字符串内容不需要修改，String 的不可变性保证了安全性，常量池还可以复用对象，是最优选择。

2. **用 `StringBuilder`**。单线程环境下 StringBuilder 没有同步开销，性能最高。用 `String +` 每次拼接都会创建新对象，循环 1 万次会产生大量临时对象，性能极差。

3. **用 `StringBuffer`**。多线程共享同一个可变字符串对象时，需要线程安全的实现。StringBuffer 的方法都加了 `synchronized` 锁，可以保证线程安全。如果每个线程有独立的 StringBuilder，最后 join 合并，性能会更好，但场景要求共享同一对象，所以选 StringBuffer。

---

## Q3 实操题 — 数组操作

**题目：** 给定一个整数数组 `int[] nums = {5, 3, 8, 1, 9, 2, 7, 4, 6}`，不使用 `Arrays.sort`，手动实现以下功能：

1. 找出数组中的最大值和最小值
2. 计算所有元素的平均值
3. 将数组中所有偶数提取到一个新数组中返回

---

**参考答案：**

```java
import java.util.Arrays;

public class ArrayOpsDemo {

    // 1. 找最大值
    public static int findMax(int[] nums) {
        int max = nums[0];
        for (int n : nums) {
            if (n > max) max = n;
        }
        return max;
    }

    // 2. 找最小值
    public static int findMin(int[] nums) {
        int min = nums[0];
        for (int n : nums) {
            if (n < min) min = n;
        }
        return min;
    }

    // 3. 求平均值
    public static double average(int[] nums) {
        int sum = 0;
        for (int n : nums) sum += n;
        return (double) sum / nums.length;
    }

    // 4. 提取偶数（先统计数量，再创建数组）
    public static int[] extractEvens(int[] nums) {
        int count = 0;
        for (int n : nums) {
            if (n % 2 == 0) count++;
        }
        int[] evens = new int[count];
        int idx = 0;
        for (int n : nums) {
            if (n % 2 == 0) evens[idx++] = n;
        }
        return evens;
    }

    public static void main(String[] args) {
        int[] nums = {5, 3, 8, 1, 9, 2, 7, 4, 6};
        System.out.println("数组：" + Arrays.toString(nums));
        System.out.println("最大值：" + findMax(nums));      // 9
        System.out.println("最小值：" + findMin(nums));      // 1
        System.out.printf("平均值：%.2f%n", average(nums));  // 5.00
        System.out.println("偶数：" + Arrays.toString(extractEvens(nums)));  // [8, 2, 4, 6]
    }
}
```

---

## Q4 实操题 — 字符串处理

**题目：** 写一个方法 `String processInput(String raw)`，接受一段可能含多余空格和大小写混乱的输入，按以下规则处理后返回：

1. 去除首尾空白
2. 转为全小写
3. 将所有空格替换为下划线 `_`
4. 如果结果为空字符串，返回 `"unknown"`

示例：`"  Hello World  "` → `"hello_world"`，`"   "` → `"unknown"`

---

**参考答案：**

```java
public class StringProcessor {

    public static String processInput(String raw) {
        // 防御性判断：null 直接返回 unknown
        if (raw == null) return "unknown";

        String result = raw.trim()          // 去除首尾空白
                           .toLowerCase()  // 转小写
                           .replace(" ", "_"); // 空格换下划线

        // 处理后为空字符串
        return result.isEmpty() ? "unknown" : result;
    }

    public static void main(String[] args) {
        System.out.println(processInput("  Hello World  "));  // hello_world
        System.out.println(processInput("   "));               // unknown
        System.out.println(processInput("JAVA"));              // java
        System.out.println(processInput(null));                // unknown
        System.out.println(processInput("  My  Name  "));     // my__name（中间多个空格只替换一个）

        // 进阶：如果要合并多个连续空格，用正则 "\\s+"
        System.out.println("  My  Name  ".trim().toLowerCase().replaceAll("\\s+", "_")); // my_name
    }
}
```

**注意：** `replace(" ", "_")` 只替换单个空格；`replaceAll("\\s+", "_")` 使用正则，可以将多个连续空白合并为一个下划线，更健壮。

---

## Q5 综合题 — 数组 + 字符串

**题目：** 写一个方法，接受一句英文句子（如 `"the quick brown fox jumps over the lazy dog"`），返回其中长度最长的单词。如果有多个同样长度的单词，返回第一个出现的。

---

**参考答案：**

```java
public class LongestWordFinder {

    public static String findLongestWord(String sentence) {
        if (sentence == null || sentence.trim().isEmpty()) {
            return "";
        }

        // 按空白字符分割（\\s+ 可处理多个连续空格）
        String[] words = sentence.trim().split("\\s+");

        String longest = words[0];
        for (int i = 1; i < words.length; i++) {
            if (words[i].length() > longest.length()) {
                longest = words[i];
            }
        }
        return longest;
    }

    public static void main(String[] args) {
        String sentence = "the quick brown fox jumps over the lazy dog";
        System.out.println("句子：" + sentence);
        System.out.println("最长单词：" + findLongestWord(sentence));  // jumps（5个字母）

        System.out.println(findLongestWord("hi hello world"));  // hello（5个字母）
        System.out.println(findLongestWord("a bb ccc"));        // ccc
        System.out.println(findLongestWord(""));                // ""
    }
}
```

**关键点：**
- 使用 `split("\\s+")` 而不是 `split(" ")`，可正确处理多个连续空格
- 先用 `trim()` 去除首尾空白，避免 split 产生空字符串元素
- 初始值设为 `words[0]`，不要设为 `""`，否则在单词都是单字符时会出错
