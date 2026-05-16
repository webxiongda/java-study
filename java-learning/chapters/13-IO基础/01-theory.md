# Chapter 13 - IO 基础：理论知识

## 学习目标

- 理解 Java IO 流的四大分类体系
- 掌握 File 类的核心操作
- 理解字节流和字符流的区别及适用场景
- 理解缓冲流的原理和必要性
- 掌握装饰器模式在 IO 中的应用
- 正确处理编码问题（GBK / UTF-8）
- 学会用 try-with-resources 安全关闭流

---

## 1. IO 流分类

### 1.1 按数据单位分

| 分类 | 单位 | 基类 | 适用场景 |
|------|------|------|----------|
| 字节流 | 1 byte（8 bit） | InputStream / OutputStream | 图片、视频、音频、二进制文件、网络传输 |
| 字符流 | 1 char（2 byte，Unicode） | Reader / Writer | 纯文本文件（.txt / .csv / .java 等） |

### 1.2 按流向分

| 分类 | 方向 | 说明 |
|------|------|------|
| 输入流 | 外部 → 程序 | 从文件/网络读数据 |
| 输出流 | 程序 → 外部 | 向文件/网络写数据 |

### 1.3 四大体系总览

```
字节流
├── InputStream（抽象基类）
│   ├── FileInputStream          ← 从文件读字节
│   ├── BufferedInputStream      ← 带缓冲的字节输入流
│   └── ObjectInputStream        ← 反序列化
└── OutputStream（抽象基类）
    ├── FileOutputStream         ← 向文件写字节
    ├── BufferedOutputStream     ← 带缓冲的字节输出流
    └── ObjectOutputStream       ← 序列化

字符流
├── Reader（抽象基类）
│   ├── FileReader               ← 从文件读字符（默认系统编码）
│   ├── BufferedReader           ← 带缓冲，支持 readLine()
│   └── InputStreamReader        ← 字节流 → 字符流（可指定编码）
└── Writer（抽象基类）
    ├── FileWriter               ← 向文件写字符
    ├── BufferedWriter           ← 带缓冲，支持 newLine()
    └── OutputStreamWriter       ← 字符流 → 字节流（可指定编码）
```

---

## 2. File 类

`java.io.File` 代表文件系统中的**文件或目录**，它只是路径的抽象，本身不涉及读写内容。

### 2.1 创建 File 对象

```java
File file = new File("data/test.txt");          // 相对路径
File file = new File("/Users/alice/test.txt");  // 绝对路径
File file = new File("/Users/alice", "test.txt"); // 父目录 + 文件名
```

### 2.2 核心方法

```java
// 判断
file.exists()           // 是否存在
file.isFile()           // 是否是文件
file.isDirectory()      // 是否是目录

// 创建
file.createNewFile()    // 创建空文件（父目录必须存在）
file.mkdir()            // 创建单层目录
file.mkdirs()           // 创建多层目录（推荐，自动创建中间目录）

// 删除
file.delete()           // 删除文件或空目录（非空目录不能直接删）

// 获取属性
file.getName()          // 文件名（含扩展名）
file.getPath()          // 构造时传入的路径
file.getAbsolutePath()  // 绝对路径
file.length()           // 文件大小（字节数）
file.lastModified()     // 最后修改时间（毫秒时间戳）

// 遍历目录
String[] names = dir.list()           // 返回子文件/目录名数组
File[] files  = dir.listFiles()       // 返回 File 对象数组
File[] javaFiles = dir.listFiles(f -> f.getName().endsWith(".java")); // 带过滤器
```

### 2.3 路径分隔符问题

```java
// 不推荐（硬编码 /，在 Windows 上可能出问题）
File f = new File("src/main/Test.java");

// 推荐（使用系统分隔符）
File f = new File("src" + File.separator + "main" + File.separator + "Test.java");
```

---

## 3. InputStream / OutputStream 体系（字节流）

### 3.1 InputStream 核心方法

```java
int read()                    // 读一个字节，返回 0-255；末尾返回 -1
int read(byte[] buf)          // 读若干字节到 buf，返回实际读到的字节数；末尾返回 -1
int read(byte[] buf, int off, int len) // 读到 buf 的指定位置
void close()                  // 关闭流，释放资源
```

### 3.2 OutputStream 核心方法

```java
void write(int b)             // 写一个字节（只用低 8 位）
void write(byte[] buf)        // 写字节数组
void write(byte[] buf, int off, int len)
void flush()                  // 强制将缓冲区数据写出
void close()                  // 关闭（会自动 flush）
```

### 3.3 FileInputStream / FileOutputStream 示例

```java
// FileOutputStream 默认覆盖原文件
FileOutputStream fos = new FileOutputStream("test.txt");

// 追加模式（第二参数 true = append）
FileOutputStream fos = new FileOutputStream("test.txt", true);

// 写数据
fos.write("Hello".getBytes(StandardCharsets.UTF_8));
fos.close();
```

---

## 4. 字符流 Reader / Writer

### 4.1 为什么需要字符流？

字节流处理文本时，每次读写的是字节，而汉字在 UTF-8 下占 3 字节。若每次只读一个字节，就会出现乱码。字符流会自动处理字节→字符的转换（按指定编码）。

### 4.2 FileReader / FileWriter

```java
// 注意：FileReader/FileWriter 使用 JVM 默认编码（不可指定）
// 如果需要指定编码，改用 InputStreamReader/OutputStreamWriter

FileReader reader = new FileReader("test.txt");
int ch;
while ((ch = reader.read()) != -1) {
    System.out.print((char) ch);
}
reader.close();
```

```java
FileWriter writer = new FileWriter("test.txt");          // 覆盖
FileWriter writer = new FileWriter("test.txt", true);    // 追加
writer.write("Hello, 世界！");
writer.close();
```

---

## 5. 缓冲流（BufferedReader / BufferedWriter）

### 5.1 为什么要用缓冲流？

直接使用 `FileInputStream` 每次 `read()` 都会发起一次**系统调用**（从磁盘读数据），系统调用代价极高。

缓冲流维护一个内存缓冲区（默认 8192 字节），一次系统调用读满缓冲区，后续 `read()` 直接从内存读，**大幅减少系统调用次数**，性能提升显著。

```
未用缓冲流：读 1000 个字节 = 1000 次系统调用
用了缓冲流：读 1000 个字节 ≈ 1 次系统调用（8192 字节一次读完）
```

### 5.2 BufferedReader 核心方法

```java
String line = reader.readLine();  // 读一整行（不含换行符）；到达文件末尾返回 null
```

### 5.3 用法示例

```java
// 包装 FileReader（装饰器模式）
BufferedReader br = new BufferedReader(new FileReader("test.txt"));
String line;
while ((line = br.readLine()) != null) {
    System.out.println(line);
}
br.close();
```

```java
BufferedWriter bw = new BufferedWriter(new FileWriter("out.txt"));
bw.write("第一行");
bw.newLine();  // 跨平台换行（比直接写 "\n" 更推荐）
bw.write("第二行");
bw.close();
```

---

## 6. 装饰器模式理解

Java IO 的设计核心是**装饰器模式（Decorator Pattern）**：通过将一个流对象包装在另一个流对象中，为其添加新功能，而无需修改原类。

```java
// 基础功能：从文件读字节
InputStream fis = new FileInputStream("data.bin");

// 加缓冲（装饰 fis）
InputStream bis = new BufferedInputStream(fis);

// 再加字节 → 字符转换（再套一层装饰）
Reader reader = new InputStreamReader(bis, StandardCharsets.UTF_8);

// 再加行读取（再套一层）
BufferedReader br = new BufferedReader(reader);
```

每一层装饰都只增加一个职责，符合"单一职责原则"。这也是为什么 IO 类看起来需要套多层 `new` 的原因。

---

## 7. 编码问题

### 7.1 GBK vs UTF-8

| 编码 | 汉字占字节数 | 特点 |
|------|-------------|------|
| GBK  | 2 字节 | 中国标准，Windows 系统默认 |
| UTF-8 | 3 字节 | 国际标准，Linux/Mac/互联网默认 |

### 7.2 乱码产生原因

写入时用 GBK 编码，读取时用 UTF-8 解码 → 乱码（字节序列的解释方式不匹配）。

### 7.3 用 InputStreamReader 指定编码（推荐）

```java
// 指定 UTF-8 读取（避免平台默认编码干扰）
BufferedReader br = new BufferedReader(
    new InputStreamReader(new FileInputStream("test.txt"), StandardCharsets.UTF_8)
);

// 指定 GBK 写入（处理旧系统文件）
BufferedWriter bw = new BufferedWriter(
    new OutputStreamWriter(new FileOutputStream("out.txt"), "GBK")
);
```

**最佳实践：** 项目中统一使用 UTF-8，所有文件读写都显式指定编码，不依赖系统默认值。

---

## 8. try-with-resources 关闭流

### 8.1 传统写法（繁琐且容易漏关）

```java
BufferedReader br = null;
try {
    br = new BufferedReader(new FileReader("test.txt"));
    // 使用 br
} catch (IOException e) {
    e.printStackTrace();
} finally {
    if (br != null) {
        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### 8.2 try-with-resources（Java 7+，推荐）

```java
try (BufferedReader br = new BufferedReader(new FileReader("test.txt"))) {
    String line;
    while ((line = br.readLine()) != null) {
        System.out.println(line);
    }
} catch (IOException e) {
    e.printStackTrace();
}
// br 自动关闭，无论是否发生异常
```

原理：`try()` 括号中的对象必须实现 `AutoCloseable` 接口，JVM 在 try 块结束时自动调用 `close()`。

多个资源可以用分号隔开：
```java
try (FileInputStream fis = new FileInputStream("in.txt");
     FileOutputStream fos = new FileOutputStream("out.txt")) {
    // 使用 fis 和 fos
}
// 关闭顺序：fos 先关，fis 后关（与声明顺序相反）
```

---

## 常见坑

### 坑 1：不关闭流导致资源泄漏

```java
// 错误：没有 close()，文件句柄泄漏
FileWriter fw = new FileWriter("log.txt");
fw.write("数据");
// 程序崩溃 → 数据未写入文件（flush 没有执行），文件句柄没有释放
```

**修复：** 始终使用 try-with-resources，让 JVM 保证流的关闭。

---

### 坑 2：用 FileWriter 写中文出现乱码

```java
// 可能乱码：FileWriter 使用 JVM 默认编码（Windows 下是 GBK）
FileWriter fw = new FileWriter("out.txt");
fw.write("你好世界");

// 正确：显式指定 UTF-8
BufferedWriter bw = new BufferedWriter(
    new OutputStreamWriter(new FileOutputStream("out.txt"), StandardCharsets.UTF_8)
);
bw.write("你好世界");
```

---

### 坑 3：Windows 与 Mac/Linux 路径分隔符不同

```java
// 在 Mac/Linux 上运行没问题，部署到 Windows 服务器就出错
File f = new File("data/logs/app.log");

// 正确：使用 File.separator 或 Paths.get()
File f = new File("data" + File.separator + "logs" + File.separator + "app.log");
// 或（推荐用 NIO）
Path p = Paths.get("data", "logs", "app.log");
```

---

## 面试高频问题

### Q1：字节流和字符流的区别？什么时候用哪个？

**答：**
- **数据单位：** 字节流以 byte（8 bit）为单位，字符流以 char（Java 中 2 byte，Unicode）为单位。
- **编码处理：** 字节流不处理编码，原样读写二进制；字符流在读写时自动进行字节↔字符的编码转换。
- **适用场景：**
  - 字节流：图片、视频、音频、压缩包等二进制文件，以及网络数据传输。
  - 字符流：纯文本文件（.txt、.csv、.java 等），需要逐行读取的场景。
- **核心原则：** 字符流底层也是字节流，只是加了编码/解码层（`InputStreamReader` 就是转换桥梁）。

---

### Q2：BufferedReader 为什么比 FileReader 快？

**答：**
- `FileReader` 每次 `read()` 都会发起**系统调用**，从磁盘读取数据，系统调用的上下文切换代价很高（用户态→内核态→用户态）。
- `BufferedReader` 内部维护一个 8192 字节（默认）的内存缓冲区，每次系统调用一次性读取最多 8192 字节到缓冲区，后续 `read()` 调用直接从内存缓冲区取数据，不再触发系统调用。
- 读 10000 字节时，`FileReader` 约 10000 次系统调用，`BufferedReader` 约 2 次系统调用，性能差距可达数十倍。

---

### Q3：如何读取大文件（如 10 GB 的日志文件）避免 OOM？

**答：**
- **错误做法：** `Files.readAllBytes()` 或 `Files.readAllLines()` 会把全部内容读入内存，10 GB 文件直接 OOM。
- **正确做法：逐行读取，处理完一行就丢弃：**
  ```java
  try (BufferedReader br = new BufferedReader(
          new InputStreamReader(new FileInputStream("big.log"), StandardCharsets.UTF_8))) {
      String line;
      while ((line = br.readLine()) != null) {
          processLine(line);  // 每次只有一行在内存中
      }
  }
  ```
- Java 8+ 可用 `Files.lines()` 返回惰性加载的 Stream，也是逐行处理。
- 若要随机访问文件，使用 `RandomAccessFile`。

---

### Q4：try-with-resources 和 finally 关闭流，哪个更好？为什么？

**答：**
- `try-with-resources` 更好，原因：
  1. **代码简洁**：不需要写嵌套的 try-finally 和 null 检查。
  2. **异常安全**：若 try 块和 `close()` 都抛出异常，`finally` 写法中 close 的异常会**覆盖**原始异常；`try-with-resources` 会把 close 的异常作为"被抑制的异常"（Suppressed Exception）附加到原始异常上，原始异常不会丢失。
  3. **自动调用顺序正确**：多资源时按声明的逆序关闭。
- Java 7 引入，所有实现 `AutoCloseable` 接口的类都支持。

---

### Q5：FileOutputStream 的两种构造方式有什么区别？

**答：**
```java
new FileOutputStream("file.txt")        // 覆盖模式：从头写，原内容会被清空
new FileOutputStream("file.txt", true)  // 追加模式：在文件末尾继续写
```
- 如果需要向日志文件追加内容，必须用第二种（append=true），否则每次程序启动都会清空日志。
- 对应的字符流：`new FileWriter("file.txt", true)` 也是追加模式。
