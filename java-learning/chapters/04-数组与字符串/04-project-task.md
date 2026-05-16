# Chapter 04 - 数组与字符串 | 项目任务

---

## 业务背景

你在开发一款面向英语学习者的**文本分析工具**。该工具的第一个功能模块是**单词统计**：用户输入一段英文文本，系统自动分析并输出统计报告，包括总单词数、最长单词、每个单词出现的频次，最终格式化输出结果。

本任务要求仅使用数组和字符串的基础能力实现，不使用 HashMap 等集合类。

---

## 任务要求

实现 `WordCounter` 类，完成以下功能：

### 功能清单

1. **`countWords(String text)`**：统计文本中的单词总数（单词以空白字符分隔）
2. **`findLongestWord(String text)`**：找出文本中最长的单词（同长度取最先出现的）
3. **`countFrequency(String text)`**：统计每个单词（不区分大小写）出现的次数，结果以格式化字符串返回
4. **`generateReport(String text)`**：整合以上结果，生成一份格式化的统计报告

### 输入示例

```
"To be or not to be that is the question whether tis nobler in the mind to suffer"
```

### 输出示例

```
========== 单词统计报告 ==========
原文：To be or not to be that is the question whether tis nobler in the mind to suffer
---------------------------------
总单词数：17
最长单词：question（8个字符）
---------------------------------
单词频次统计：
  to         : 3 次 ████████████████████████████████
  be         : 2 次 █████████████████████
  the        : 2 次 █████████████████████
  or         : 1 次 ██████████
  not        : 1 次 ██████████
  that       : 1 次 ██████████
  is         : 1 次 ██████████
  question   : 1 次 ██████████
  whether    : 1 次 ██████████
  tis        : 1 次 ██████████
  nobler     : 1 次 ██████████
  in         : 1 次 ██████████
  mind       : 1 次 ██████████
  suffer     : 1 次 ██████████
=================================
```

---

## 参考实现

```java
public class WordCounter {

    /**
     * 将文本分割为单词数组（转小写，去除标点）
     */
    private static String[] splitWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }
        // 先全部转小写，再用正则去除非字母字符，最后按空白分割
        return text.trim()
                   .toLowerCase()
                   .replaceAll("[^a-zA-Z\\s]", "")   // 去除标点符号
                   .split("\\s+");                    // 按一个或多个空白分割
    }

    /**
     * 统计单词总数
     */
    public static int countWords(String text) {
        String[] words = splitWords(text);
        return words.length;
    }

    /**
     * 找出最长单词（同长度取第一个出现的）
     */
    public static String findLongestWord(String text) {
        String[] words = splitWords(text);
        if (words.length == 0) return "";

        String longest = words[0];
        for (int i = 1; i < words.length; i++) {
            if (words[i].length() > longest.length()) {
                longest = words[i];
            }
        }
        return longest;
    }

    /**
     * 统计每个单词的出现次数（不使用 HashMap，用两个数组分别存单词和计数）
     * @return 格式化后的统计字符串
     */
    public static String countFrequency(String text) {
        String[] words = splitWords(text);
        if (words.length == 0) return "（无单词）";

        // 用两个数组模拟 Map：uniqueWords 存不重复单词，counts 存对应计数
        String[] uniqueWords = new String[words.length];
        int[] counts = new int[words.length];
        int uniqueCount = 0;

        for (String word : words) {
            // 查找是否已在 uniqueWords 中
            boolean found = false;
            for (int i = 0; i < uniqueCount; i++) {
                if (uniqueWords[i].equals(word)) {
                    counts[i]++;
                    found = true;
                    break;
                }
            }
            // 没找到，新增一个
            if (!found) {
                uniqueWords[uniqueCount] = word;
                counts[uniqueCount] = 1;
                uniqueCount++;
            }
        }

        // 按出现次数降序排序（简单冒泡排序）
        for (int i = 0; i < uniqueCount - 1; i++) {
            for (int j = 0; j < uniqueCount - 1 - i; j++) {
                if (counts[j] < counts[j + 1]) {
                    // 交换计数
                    int tmpCount = counts[j];
                    counts[j] = counts[j + 1];
                    counts[j + 1] = tmpCount;
                    // 同步交换单词
                    String tmpWord = uniqueWords[j];
                    uniqueWords[j] = uniqueWords[j + 1];
                    uniqueWords[j + 1] = tmpWord;
                }
            }
        }

        // 格式化输出
        int maxCount = counts[0];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uniqueCount; i++) {
            // 生成频次柱状图（最多 30 个 █）
            int barLength = (int) ((double) counts[i] / maxCount * 30);
            String bar = "█".repeat(barLength);
            sb.append(String.format("  %-12s: %d 次 %s%n", uniqueWords[i], counts[i], bar));
        }
        return sb.toString();
    }

    /**
     * 生成完整统计报告
     */
    public static String generateReport(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "文本为空，无法生成报告。";
        }

        String longest = findLongestWord(text);
        StringBuilder report = new StringBuilder();

        report.append("========== 单词统计报告 ==========\n");
        report.append("原文：").append(text.trim()).append("\n");
        report.append("---------------------------------\n");
        report.append("总单词数：").append(countWords(text)).append("\n");
        report.append("最长单词：").append(longest)
              .append("（").append(longest.length()).append("个字符）\n");
        report.append("---------------------------------\n");
        report.append("单词频次统计：\n");
        report.append(countFrequency(text));
        report.append("=================================\n");

        return report.toString();
    }

    public static void main(String[] args) {
        String text = "To be or not to be that is the question " +
                      "whether tis nobler in the mind to suffer";

        System.out.println(generateReport(text));

        // 测试边界情况
        System.out.println("--- 边界测试 ---");
        System.out.println("空字符串：" + countWords(""));
        System.out.println("单个单词：" + countWords("hello"));
        System.out.println("最长单词（含标点）：" + findLongestWord("Hello, world! Programming is fun."));
    }
}
```

---

## 验收标准（Checklist）

- [ ] `countWords` 对空字符串返回 0，对含多个连续空格的文本能正确统计单词数
- [ ] `findLongestWord` 能正确处理标点符号（如 `"Hello,"` 应识别为 `"hello"`），同长度单词返回第一个
- [ ] `countFrequency` 不区分大小写（`"To"` 和 `"to"` 视为同一单词），且输出结果按出现次数从高到低排序
- [ ] `generateReport` 格式清晰，柱状图能直观展示各单词频次比例

---

## 常见坑

**坑 1：`split(" ")` 无法处理多个连续空格**

```java
// 错误：两个单词之间有多个空格时，split(" ") 会产生空字符串元素
String[] words = "hello  world".split(" ");
// words = ["hello", "", "world"]，长度 3，不是 2

// 正确：使用正则 "\\s+" 匹配一个或多个空白字符
String[] words = "hello  world".trim().split("\\s+");
// words = ["hello", "world"]，长度 2
```

**坑 2：忘记去除标点符号导致统计不准确**

```java
// 如果不去除标点，"be," 和 "be" 会被认为是两个不同的单词
String text = "To be or not to be, that is the question.";
// 不处理时："be," 出现 1 次，"be" 出现 1 次（实际应合并为 2 次）

// 正确：在分词前用正则去除标点
text = text.replaceAll("[^a-zA-Z\\s]", "");
```

**坑 3：用两个数组模拟频次统计时，忘记同步交换两个数组的元素**

```java
// 排序时错误：只排了 counts，没有同步移动 uniqueWords
// 导致 counts[0] 是最高频次，但 uniqueWords[0] 不是对应的单词

// 正确：交换 counts[j] 和 counts[j+1] 时，必须同步交换 uniqueWords[j] 和 uniqueWords[j+1]
int tmpCount = counts[j];
counts[j] = counts[j + 1];
counts[j + 1] = tmpCount;

String tmpWord = uniqueWords[j];
uniqueWords[j] = uniqueWords[j + 1];
uniqueWords[j + 1] = tmpWord;
```
