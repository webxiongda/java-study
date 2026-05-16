# Chapter 13 - IO 基础：代码演示

> 三个 Demo 逐步覆盖字节流、字符流缓冲流和文件目录操作。建议在本地运行并观察输出。

---

## Demo 1：用 FileOutputStream / FileInputStream 实现文件字节读写和复制

```java
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ByteStreamDemo {

    public static void main(String[] args) throws IOException {
        // ==================== 字节写入 ====================
        writeBytes();

        // ==================== 字节读取 ====================
        readBytes();

        // ==================== 文件复制 ====================
        copyFile("byte_output.txt", "byte_copy.txt");

        System.out.println("Demo 1 完成，请查看生成的文件。");
    }

    /**
     * 用 FileOutputStream 写入字节数据
     */
    static void writeBytes() throws IOException {
        // try-with-resources 自动关闭流
        try (FileOutputStream fos = new FileOutputStream("byte_output.txt")) {
            String content = "Hello, Java IO!\n你好，字节流！";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
            System.out.println("写入字节数：" + bytes.length);
        }
        // fos 在此已自动关闭

        // 演示追加模式
        try (FileOutputStream fos = new FileOutputStream("byte_output.txt", true)) {
            fos.write("\n追加的内容".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 用 FileInputStream 读取字节数据
     */
    static void readBytes() throws IOException {
        try (FileInputStream fis = new FileInputStream("byte_output.txt")) {
            // 方式1：逐字节读取（效率低，仅演示原理）
            System.out.println("=== 逐字节读取（原始字节值）===");
            int byteVal;
            int count = 0;
            while ((byteVal = fis.read()) != -1) {
                count++;
            }
            System.out.println("共读取 " + count + " 字节");
        }

        // 方式2：批量读取到 byte 数组（推荐，效率高）
        try (FileInputStream fis = new FileInputStream("byte_output.txt")) {
            System.out.println("\n=== 批量读取 ===");
            byte[] buffer = new byte[1024];
            int bytesRead;
            StringBuilder sb = new StringBuilder();
            while ((bytesRead = fis.read(buffer)) != -1) {
                // 注意：必须指定实际读取的字节数，不能用 new String(buffer)
                // 因为 buffer 末尾可能有上次读取残留的垃圾数据
                sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
            System.out.println("文件内容：");
            System.out.println(sb);
        }
    }

    /**
     * 文件复制：从 src 复制到 dest
     * 核心思路：从输入流读 → 写到输出流
     */
    static void copyFile(String src, String dest) throws IOException {
        long startTime = System.currentTimeMillis();

        try (FileInputStream fis  = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {

            byte[] buffer = new byte[8192];  // 8 KB 缓冲区，平衡内存和 IO 次数
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);  // 只写实际读到的字节数！
                totalBytes += bytesRead;
            }
            // fos.flush() 不需要显式调用，close() 会自动 flush

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.printf("\n复制完成：%s → %s（%d 字节，耗时 %d ms）%n",
                src, dest, totalBytes, elapsed);
        }
    }
}
```

**运行后观察：**
- `byte_output.txt` 包含写入和追加的内容
- `byte_copy.txt` 与 `byte_output.txt` 内容完全一致
- `buffer, 0, bytesRead` 中第三个参数的重要性：防止最后一次读取的残留字节被写入

---

## Demo 2：用 BufferedReader / BufferedWriter 逐行读写文本文件

```java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BufferedStreamDemo {

    public static void main(String[] args) throws IOException {
        String filename = "students.txt";

        // ==================== 逐行写入 ====================
        writeLines(filename);

        // ==================== 逐行读取 ====================
        List<String> lines = readLines(filename);
        System.out.println("\n读取到 " + lines.size() + " 行：");
        lines.forEach(System.out::println);

        // ==================== 演示 readLine 返回 null ====================
        System.out.println("\n=== readLine 末尾行为演示 ===");
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 1;
            while ((line = br.readLine()) != null) {
                // readLine 返回的行不含换行符
                System.out.printf("第%d行：[%s]%n", lineNum++, line);
            }
            System.out.println("readLine 返回 null，文件读取结束");
        }
    }

    /**
     * 用 BufferedWriter 逐行写入文本文件
     * 显式指定 UTF-8 编码，避免平台差异
     */
    static void writeLines(String filename) throws IOException {
        // InputStreamReader/OutputStreamWriter 是字节流与字符流的桥梁，可指定编码
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8))) {

            String[] students = {
                "001,张三,88",
                "002,李四,92",
                "003,王五,75",
                "004,赵六,95",
                "005,陈七,60"
            };

            for (String student : students) {
                bw.write(student);
                bw.newLine();  // 跨平台换行符：Windows=\r\n，Mac/Linux=\n
            }

            System.out.println("写入完成：" + filename);
        }
        // bw 自动关闭，close() 内部会先调用 flush()，确保缓冲区数据写入磁盘
    }

    /**
     * 用 BufferedReader 逐行读取文本文件，返回所有行
     */
    static List<String> readLines(String filename) throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        return lines;
    }
}
```

**关键点：**

| 细节 | 说明 |
|------|------|
| `bw.newLine()` | 比 `"\n"` 更好，自动适配操作系统的行分隔符 |
| `OutputStreamWriter` + 编码 | `FileWriter` 无法指定编码，生产代码优先用此方式 |
| `readLine()` 返回 null | 表示到达文件末尾，循环结束标志 |
| close 自动 flush | try-with-resources 关闭时会自动刷新缓冲区 |

---

## Demo 3：用 File 类遍历目录树（递归列出所有 .java 文件）

```java
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileTraversalDemo {

    public static void main(String[] args) {
        // 遍历当前目录（也可以改成你的 Java 项目路径）
        String rootPath = ".";  // 当前工作目录
        File root = new File(rootPath);

        System.out.println("根目录：" + root.getAbsolutePath());
        System.out.println();

        // ==================== 演示 File 基本属性 ====================
        printFileInfo(root);

        // ==================== 递归列出所有 .java 文件 ====================
        System.out.println("\n=== 递归查找所有 .java 文件 ===");
        List<File> javaFiles = new ArrayList<>();
        findFiles(root, ".java", javaFiles);

        if (javaFiles.isEmpty()) {
            System.out.println("（当前目录下没有 .java 文件，换一个项目目录试试）");
        } else {
            System.out.println("共找到 " + javaFiles.size() + " 个 .java 文件：");
            javaFiles.forEach(f -> System.out.println("  " + f.getAbsolutePath()));
        }

        // ==================== 演示目录创建 ====================
        System.out.println("\n=== 创建多层目录 ===");
        File newDir = new File("demo_output/logs/2024");
        if (newDir.mkdirs()) {
            System.out.println("目录创建成功：" + newDir.getAbsolutePath());
        } else {
            System.out.println("目录已存在或创建失败：" + newDir.getAbsolutePath());
        }

        // ==================== 演示带过滤器的 listFiles ====================
        System.out.println("\n=== 列出当前目录的 .txt 文件 ===");
        File[] txtFiles = root.listFiles(file ->
            file.isFile() && file.getName().endsWith(".txt")
        );
        if (txtFiles != null && txtFiles.length > 0) {
            for (File f : txtFiles) {
                System.out.printf("  %-30s %,d 字节%n", f.getName(), f.length());
            }
        } else {
            System.out.println("  （没有 .txt 文件）");
        }
    }

    /**
     * 打印文件/目录的基本信息
     */
    static void printFileInfo(File f) {
        System.out.println("=== 文件信息 ===");
        System.out.println("名称：      " + f.getName());
        System.out.println("绝对路径：  " + f.getAbsolutePath());
        System.out.println("是否存在：  " + f.exists());
        System.out.println("是否是文件：" + f.isFile());
        System.out.println("是否是目录：" + f.isDirectory());
        if (f.isFile()) {
            System.out.println("文件大小：  " + f.length() + " 字节");
        }
    }

    /**
     * 递归遍历目录，找出所有指定扩展名的文件
     * @param dir    当前目录
     * @param suffix 扩展名（如 ".java"）
     * @param result 收集结果的列表
     */
    static void findFiles(File dir, String suffix, List<File> result) {
        // 健壮性检查：dir 不存在或不是目录，直接返回
        if (dir == null || !dir.isDirectory()) {
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return;  // 无法读取目录（可能权限不足）
        }

        for (File child : children) {
            if (child.isDirectory()) {
                // 跳过隐藏目录（如 .git）和 node_modules 等
                if (!child.getName().startsWith(".")) {
                    findFiles(child, suffix, result);  // 递归进入子目录
                }
            } else if (child.getName().endsWith(suffix)) {
                result.add(child);
            }
        }
    }
}
```

**运行效果说明：**

```
根目录：/Users/alice/java-study

=== 文件信息 ===
名称：      java-study
绝对路径：  /Users/alice/java-study
是否存在：  true
是否是文件：false
是否是目录：true

=== 递归查找所有 .java 文件 ===
共找到 3 个 .java 文件：
  /Users/alice/java-study/ByteStreamDemo.java
  /Users/alice/java-study/BufferedStreamDemo.java
  /Users/alice/java-study/FileTraversalDemo.java

=== 创建多层目录 ===
目录创建成功：/Users/alice/java-study/demo_output/logs/2024

=== 列出当前目录的 .txt 文件 ===
  students.txt                   55 字节
  byte_output.txt                62 字节
  byte_copy.txt                  62 字节
```

**扩展思考：**
- 如果目录层级很深（如扫描整个硬盘），递归可能导致栈溢出。可改为**迭代 + 栈/队列**实现。
- Java 7+ 的 `Files.walkFileTree()` 或 Java 8+ 的 `Files.walk()` 是更现代的遍历方式（见 Chapter 14）。
