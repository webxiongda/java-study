# Chapter 13 - IO 基础：自测题

> 完成 Demo 练习后，独立作答以下 5 道题，再对照答案检验掌握程度。

---

## Q1（概念）：字节流和字符流的本质区别是什么？各自适用什么场景？

---

### 参考答案

**本质区别：**

| 维度 | 字节流 | 字符流 |
|------|--------|--------|
| 数据单位 | 1 byte（8 bit） | 1 char（Java 中 2 byte，Unicode） |
| 编码处理 | 不处理，原样读写二进制 | 自动处理字节↔字符的编码转换 |
| 基类 | InputStream / OutputStream | Reader / Writer |
| 典型子类 | FileInputStream / FileOutputStream | FileReader / FileWriter |

**适用场景：**
- **字节流：** 图片、视频、音频、压缩包、网络数据流等一切二进制文件。
- **字符流：** 纯文本文件（.txt、.csv、.java、.xml、.json 等），需要按字符或按行处理的场景。

**关系：** 字符流底层仍然是字节流，`InputStreamReader` 和 `OutputStreamWriter` 是两者的桥梁，负责在字节和字符之间按指定编码做转换。

---

## Q2（概念）：以下代码有什么问题？如何修复？

```java
public static void writeLog(String message) {
    try {
        FileWriter fw = new FileWriter("app.log");
        fw.write(message);
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

---

### 参考答案

**问题 1：流没有关闭**

`FileWriter` 使用了缓冲区，若不调用 `close()`（或 `flush()`），缓冲区中的数据可能**永远不会写入磁盘**。同时文件句柄没有释放，在长期运行的程序中会造成**文件句柄泄漏**。

**问题 2：每次调用都会覆盖原文件**

`new FileWriter("app.log")` 默认是覆盖模式，每次写日志都会清空之前的内容。日志文件应该用追加模式。

**问题 3：编码问题**

`FileWriter` 使用 JVM 默认编码，在 Windows 上是 GBK，可能导致中文乱码。

**修复方案：**

```java
public static void writeLog(String message) {
    // 追加模式（true）+ 显式指定 UTF-8 + try-with-resources 自动关闭
    try (BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream("app.log", true),  // true = 追加
                StandardCharsets.UTF_8
            ))) {
        bw.write(message);
        bw.newLine();
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

---

## Q3（实操）：分析下面代码的输出并解释原因

```java
try (FileOutputStream fos = new FileOutputStream("test.bin")) {
    fos.write(65);       // (1)
    fos.write(66);       // (2)
    fos.write(0);        // (3)
    fos.write(255);      // (4)
    fos.write(256);      // (5)
    fos.write(-1);       // (6)
}

try (FileInputStream fis = new FileInputStream("test.bin")) {
    int b;
    while ((b = fis.read()) != -1) {
        System.out.print(b + " ");
    }
}
```

**问题：** 文件写入了几个字节？读取后打印的数字是什么？为什么 `write(256)` 和 `write(-1)` 的行为与直觉不符？

---

### 参考答案

**文件写入了 6 个字节，打印结果为：**
```
65 66 0 255 0 0
```

**逐行解释：**

| 调用 | 实际写入 | 读取值 | 原因 |
|------|---------|--------|------|
| `write(65)` | 字节 65 | 65 | 正常，65 在 0-255 范围内 |
| `write(66)` | 字节 66 | 66 | 正常 |
| `write(0)` | 字节 0 | 0 | 正常 |
| `write(255)` | 字节 255 | 255 | 正常，255 = 0xFF |
| `write(256)` | 字节 0 | 0 | **取低 8 位**：256 = 0x100，低 8 位 = 0x00 = 0 |
| `write(-1)` | 字节 255 | 0 | **取低 8 位**：-1 = 0xFFFFFFFF，低 8 位 = 0xFF = 255；但读取时返回 255，不是 -1 |

**关键规则：**
- `OutputStream.write(int b)` 只写入 `b` 的**低 8 位**（0-255 范围），超出部分截断。
- `InputStream.read()` 返回值范围是 **0-255**（将字节扩展为 int），返回 -1 专门表示"流结束"，而非读到了字节 -1。

---

## Q4（实操）：实现一个方法，统计文本文件的行数

**要求：**
- 方法签名：`public static int countLines(String filePath) throws IOException`
- 正确处理 UTF-8 编码
- 使用 try-with-resources
- 空文件返回 0

---

### 参考答案

```java
import java.io.*;
import java.nio.charset.StandardCharsets;

public class LineCounter {

    public static int countLines(String filePath) throws IOException {
        int count = 0;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            // readLine 每次返回一行（不含换行符），末尾返回 null
            while (br.readLine() != null) {
                count++;
            }
        }
        return count;  // 空文件：readLine 第一次就返回 null，count 保持 0
    }

    public static void main(String[] args) throws IOException {
        // 准备测试文件
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream("test_lines.txt"),
                                       StandardCharsets.UTF_8))) {
            bw.write("第一行");
            bw.newLine();
            bw.write("第二行");
            bw.newLine();
            bw.write("第三行");
            // 注意：最后一行没有换行符，readLine 仍能读到并计数
        }

        System.out.println("行数：" + countLines("test_lines.txt")); // 输出：行数：3
    }
}
```

**注意事项：**
- 最后一行没有换行符时，`readLine()` 仍然能读到该行（返回非 null），所以计数是准确的。
- `Files.lines(path).count()` 是 Java 8 的等价写法，但对于大文件需要注意关闭流。

---

## Q5（综合）：读取一个 CSV 文件，按姓名字段去重后写入新文件

**题目：** 有如下 `students.csv` 内容（第一行是表头）：

```
id,name,score
001,张三,88
002,李四,92
003,张三,75
004,赵六,95
005,李四,60
```

写一个程序：读取该文件，去掉 name 重复的行（保留第一次出现的），将结果（含表头）写入 `students_unique.csv`。

---

### 参考答案

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvDedup {

    public static void main(String[] args) throws IOException {
        dedup("students.csv", "students_unique.csv");
    }

    static void dedup(String inputFile, String outputFile) throws IOException {
        Set<String> seenNames = new LinkedHashSet<>();  // 用 Set 记录已见过的 name
        List<String> outputLines = new ArrayList<>();

        // ---- 读取 ----
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    outputLines.add(line);  // 表头直接保留
                    isHeader = false;
                    continue;
                }

                // 按逗号分割，取第二列（index=1）作为 name
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String name = parts[1].trim();
                    if (seenNames.add(name)) {  // Set.add 返回 false 表示已存在
                        outputLines.add(line);
                    } else {
                        System.out.println("跳过重复行（name=" + name + "）：" + line);
                    }
                }
            }
        }

        // ---- 写出 ----
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            for (int i = 0; i < outputLines.size(); i++) {
                bw.write(outputLines.get(i));
                if (i < outputLines.size() - 1) {
                    bw.newLine();
                }
            }
        }

        System.out.println("去重完成，输出到：" + outputFile);
        System.out.println("保留行数（含表头）：" + outputLines.size());
    }
}
```

**预期输出：**
```
跳过重复行（name=张三）：003,张三,75
跳过重复行（name=李四）：005,李四,60
去重完成，输出到：students_unique.csv
保留行数（含表头）：4
```

**students_unique.csv 内容：**
```
id,name,score
001,张三,88
002,李四,92
004,赵六,95
```
