# Chapter 15 - 阶段一总结：知识点速查手册

> 本手册整理 Chapter 01-14 的核心知识点，供复习和面试前快速回顾。

---

## Chapter 01 - 环境搭建与 Java 初印象

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| JDK / JRE / JVM | JDK = JRE + 开发工具；JRE = JVM + 标准库；JVM 是 Java 跨平台的核心 | JDK、JRE、JVM 的区别？ |
| 编译与运行 | `javac` 将 .java 编译为 .class（字节码），`java` 启动 JVM 运行字节码 | Java 是编译型还是解释型语言？ |
| 跨平台原理 | "Write Once, Run Anywhere"：字节码统一，各平台 JVM 负责适配 | Java 为什么可以跨平台？ |
| main 方法签名 | `public static void main(String[] args)` 是程序入口，签名不能改 | main 方法为什么是 static 的？ |

---

## Chapter 02 - Java 基础语法

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 基本数据类型 | 8 种：byte/short/int/long/float/double/char/boolean，默认整数字面量是 int，浮点是 double | int 和 Integer 的区别？ |
| 自动类型转换 | 小范围 → 大范围自动转（byte→int→long→float→double），反向需强制转换 | byte b = 128 会发生什么？ |
| 整数运算溢出 | int 溢出不报错，直接"绕回"；用 long 或 Math.addExact 检测 | int 最大值 +1 等于什么？ |
| 字符串不可变 | String 是不可变对象，每次 + 操作都创建新对象；大量拼接用 StringBuilder | String 为什么设计成不可变的？ |
| switch 表达式 | Java 14+ 支持箭头写法（`case X -> expr`），不再有 fall-through 问题 | switch 的 fall-through 是什么？ |

---

## Chapter 03 - 方法与调试

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 值传递 | Java 只有值传递：基本类型传值副本，引用类型传引用副本（两者都是"按值传递"） | Java 是值传递还是引用传递？ |
| 方法重载 | 同名、参数列表不同（类型/数量/顺序），与返回值无关 | 重载和重写的区别？ |
| 递归 | 必须有终止条件，否则 StackOverflowError；深度超过几千层需改迭代 | 递归的风险是什么？ |
| varargs 可变参数 | `void foo(String... args)` 本质是数组，必须是最后一个参数 | 可变参数和数组参数有什么区别？ |

---

## Chapter 04 - 数组与字符串

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 数组特性 | 长度固定、同类型、下标从 0 开始；越界抛 ArrayIndexOutOfBoundsException | 数组和 ArrayList 的区别？ |
| 多维数组 | `int[][] arr = new int[3][4]`；实际是"数组的数组"，各行长度可不同（锯齿数组） | Java 有真正的二维数组吗？ |
| String 常用 API | length/charAt/substring/indexOf/contains/replace/split/trim/toUpperCase | String 的 equals 和 == 的区别？ |
| StringBuilder | 可变字符串，append/insert/delete/reverse，线程不安全；线程安全用 StringBuffer | 什么时候用 StringBuilder？ |
| String.format | `String.format("%.2f", 3.14159)` → "3.14"，格式化输出 | printf 和 format 的关系？ |

---

## Chapter 05 - 面向对象入门

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 类与对象 | 类是模板，对象是实例；`new` 在堆上分配内存，栈上保存引用 | 对象在内存中如何存储？ |
| 构造方法 | 与类同名、无返回值；不写则 JVM 提供默认无参构造；写了有参构造则默认构造消失 | 构造方法可以重载吗？ |
| this 关键字 | 指向当前对象；用于区分字段与参数同名、调用本类其他构造方法（this(...)） | this() 和 super() 能同时使用吗？ |
| 封装 | private 字段 + public getter/setter；隐藏实现细节，控制访问 | 为什么字段要用 private？ |

---

## Chapter 06 - 继承与多态

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 继承 | `extends` 实现单继承；子类拥有父类所有非 private 成员；构造时先调 super() | Java 为什么不支持多继承？ |
| 方法重写 | 子类覆盖父类方法，签名必须相同，访问权限不能缩小，加 @Override 注解 | 重写时返回值可以改变吗？ |
| 多态 | 父类引用指向子类对象；运行时决定调用哪个方法（动态分派） | 多态的实现原理是什么？ |
| instanceof | 判断对象是否是某类（或其子类）的实例；Java 16+ 支持 Pattern Matching | 向下转型失败会怎样？ |
| final | final 类不可继承，final 方法不可重写，final 变量不可重新赋值 | final 修饰的引用变量，对象内容可以改吗？ |

---

## Chapter 07 - 包、访问控制与常用类

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 访问修饰符 | private < （默认 package）< protected < public；protected 允许子类跨包访问 | protected 和默认访问权限的区别？ |
| static | static 成员属于类，所有实例共享；static 方法不能访问非 static 成员 | static 方法能被重写吗？ |
| Object 类 | 所有类的根父类；equals/hashCode/toString/getClass/clone 等方法来自 Object | 为什么重写 equals 要同时重写 hashCode？ |
| Math 类 | Math.abs/max/min/pow/sqrt/round/random，均为 static 方法 | Math.round 和强制转 int 的区别？ |
| 包装类 | Integer/Double 等；自动装箱/拆箱；Integer.parseInt 字符串→整数 | Integer 缓存池范围是多少？ |

---

## Chapter 08 - 异常处理

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 异常体系 | Throwable → Error（不处理）/ Exception → RuntimeException（非受检）/ 其他（受检） | Error 和 Exception 的区别？ |
| try-catch-finally | finally 无论是否异常都执行；return 在 finally 里会覆盖 try 中的 return | finally 一定会执行吗？ |
| throws vs throw | throws 声明方法可能抛的受检异常；throw 主动抛出一个异常对象 | 什么时候用 throws，什么时候用 throw？ |
| 自定义异常 | 继承 RuntimeException（非受检）或 Exception（受检），提供有意义的构造方法 | 自定义异常应该继承哪个类？ |
| try-with-resources | Java 7+，自动关闭 AutoCloseable 资源，比 finally 更安全 | try-with-resources 和 finally 的区别？ |

---

## Chapter 09 - 枚举与泛型入门

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 枚举 enum | 类型安全的常量集合，JVM 保证单例；可有字段、方法、构造方法 | 枚举和常量接口有什么区别？ |
| 枚举方法 | values() 返回所有枚举值，valueOf("NAME") 按名字查找，ordinal() 返回序号 | 枚举可以实现接口吗？ |
| 泛型类/方法 | `class Box<T>` / `<T> T get()`；编译期类型检查，运行时类型擦除 | Java 泛型的类型擦除是什么意思？ |
| 泛型通配符 | `? extends T`（上界，只读）；`? super T`（下界，只写）；`?`（无界） | List<?> 和 List<Object> 有什么区别？ |

---

## Chapter 10 - 里程碑：Java SE 小项目

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 项目分层 | Model（数据）/ Service（业务逻辑）/ Main（入口）；职责分离是设计基础 | 为什么要分层？ |
| 接口设计 | 先定义接口（What），再实现（How）；依赖抽象而非具体实现（依赖倒置） | 什么是面向接口编程？ |
| 集合选型 | 有序可重复→List；无序不重复→Set；Key-Value→Map；按 FIFO→Queue | ArrayList 和 LinkedList 分别适合什么场景？ |

---

## Chapter 11 - 集合框架上

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| ArrayList | 基于动态数组；随机访问 O(1)，中间插删 O(n)；扩容 1.5 倍 | ArrayList 扩容机制是什么？ |
| LinkedList | 基于双向链表；插删 O(1)，随机访问 O(n)；同时实现了 Deque 接口 | LinkedList 为什么不适合随机访问？ |
| HashSet | 底层 HashMap；无序、不重复；需要正确实现 hashCode/equals | HashSet 如何判断重复？ |
| TreeSet | 基于红黑树；有序（自然顺序或自定义 Comparator）；不重复 | TreeSet 和 HashSet 怎么选？ |
| Iterator | 遍历集合的标准方式；遍历中删除必须用 iterator.remove()，否则 ConcurrentModificationException | 为什么 for-each 中不能删除元素？ |

---

## Chapter 12 - 集合框架下

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| HashMap | 数组 + 链表/红黑树；key 无序；线程不安全；允许一个 null key | HashMap 和 Hashtable 的区别？ |
| TreeMap | 基于红黑树；按 key 自然顺序或 Comparator 有序；不允许 null key | 什么时候用 TreeMap？ |
| LinkedHashMap | 保持插入顺序（或访问顺序）的 HashMap；可实现 LRU 缓存 | LinkedHashMap 怎么实现 LRU 缓存？ |
| put 流程 | hash → 定位桶 → 判断 key 是否相等 → 链表/红黑树处理 → 扩容检查 | HashMap 的 put 方法执行了哪些步骤？ |
| Comparator | 外部比较器；支持 comparing/reversed/thenComparing 链式写法 | Comparable 和 Comparator 的区别？ |

---

## Chapter 13 - IO 基础

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 字节流 vs 字符流 | 字节流处理二进制；字符流处理文本（自动编码转换） | 字节流和字符流的区别？ |
| BufferedReader/Writer | 内置 8 KB 缓冲区，减少系统调用次数，性能大幅提升 | BufferedReader 为什么更快？ |
| 编码问题 | FileReader/FileWriter 用 JVM 默认编码；推荐用 InputStreamReader 显式指定 UTF-8 | 中文乱码的根本原因是什么？ |
| try-with-resources | Java 7+，自动关闭实现 AutoCloseable 的资源，比 finally 更简洁安全 | try-with-resources 能同时管理多个资源吗？ |
| 装饰器模式 | IO 流通过套层实现功能叠加（FileInputStream → BufferedInputStream → InputStreamReader） | Java IO 使用了哪种设计模式？ |

---

## Chapter 14 - NIO 与序列化

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| Path vs File | Path 更现代，错误信息更丰富；Files 工具类集中了所有文件操作 | NIO 和传统 IO 的区别？ |
| Files 工具类 | readAllLines/readString/writeString/copy/move/walk 等，一行代码完成常见操作 | Files.readAllLines 和 Files.lines 的区别？ |
| Properties | load/getProperty/setProperty/store；只支持 String 类型，数字需手动转换 | Properties 如何处理 UTF-8 中文？ |
| JSON 序列化 | Jackson（writeValueAsString/readValue）；优于原生 Serializable（可读/跨语言/安全） | 为什么不推荐用 Java 原生序列化？ |
| StandardOpenOption | APPEND 追加；CREATE 不存在则创建；REPLACE_EXISTING 覆盖；CREATE_NEW 仅新建 | Files.writeString 默认是什么模式？ |

---

## 容易混淆的概念对比

### 1. == vs equals

| | == | equals |
|--|--|--|
| 基本类型 | 比较值是否相等 | 不适用 |
| 引用类型 | 比较是否是同一个对象（内存地址） | 比较内容是否相等（需正确重写） |
| String 特例 | `"a" == "a"` 可能为 true（字符串常量池），但 `new String("a") == new String("a")` 为 false | 比较内容，推荐用 equals |

**结论：** 比较对象内容是否相同，永远用 `equals()`。

---

### 2. ArrayList vs LinkedList

| | ArrayList | LinkedList |
|--|--|--|
| 底层结构 | 动态数组 | 双向链表 |
| 随机访问 get(i) | O(1) | O(n) |
| 头/尾插入 | O(n)（需移动元素）/ O(1)（均摊） | O(1) |
| 中间插入/删除 | O(n) | O(1)（找到位置后） |
| 内存占用 | 更少（连续数组） | 更多（每节点存 prev/next 指针） |
| 适用场景 | 多读少写，随机访问多 | 多写少读，频繁头尾操作 |

---

### 3. byte stream（字节流）vs char stream（字符流）

| | 字节流 | 字符流 |
|--|--|--|
| 数据单位 | 1 byte（8 bit） | 1 char（Java 中 2 byte） |
| 编码处理 | 无，原样读写 | 自动编码转换 |
| 基类 | InputStream / OutputStream | Reader / Writer |
| 适用 | 图片/视频/二进制 | 文本文件 |

---

### 4. throw vs throws

| | throw | throws |
|--|--|--|
| 位置 | 方法体内 | 方法签名 |
| 作用 | 主动抛出一个异常对象 | 声明该方法可能抛出的受检异常 |
| 后面跟 | 异常对象（`throw new XXX()`） | 异常类名（`throws IOException`） |

---

### 5. Comparable vs Comparator

| | Comparable | Comparator |
|--|--|--|
| 实现方式 | 类实现 `Comparable<T>` 接口，重写 `compareTo` | 外部类实现 `Comparator<T>`，重写 `compare` |
| 排序逻辑 | 定义在类内部（自然顺序） | 定义在类外部（可有多个不同策略） |
| 使用场景 | 有明确唯一排序标准时 | 需要多种排序方式或不想修改原类时 |

---

### 6. HashMap vs TreeMap vs LinkedHashMap

| | HashMap | TreeMap | LinkedHashMap |
|--|--|--|--|
| 顺序 | 无序 | 按 key 排序 | 按插入顺序（或访问顺序） |
| null key | 允许 1 个 | 不允许 | 允许 1 个 |
| 时间复杂度（get/put） | O(1) 均摊 | O(log n) | O(1) 均摊 |
| 底层 | 数组+链表/红黑树 | 红黑树 | 数组+链表/红黑树+双向链表 |

---

### 7. final vs finally vs finalize

| | 含义 |
|--|--|
| final | 修饰符：类不可继承，方法不可重写，变量不可再赋值 |
| finally | try-catch 的子句，无论是否异常都执行 |
| finalize | Object 的方法，GC 回收对象前调用（已废弃，Java 9+ 标记为 @Deprecated） |

---

### 8. Path vs File

| | java.io.File | java.nio.file.Path |
|--|--|--|
| 引入版本 | Java 1.0 | Java 7 |
| 错误处理 | 返回 boolean，信息少 | 抛具体 IOException 子类 |
| 文件操作 | 方法在 File 类中 | 集中在 Files 工具类 |
| 推荐程度 | 旧代码维护 | 新代码首选 |

---

### 8. 受检异常（Checked）vs 非受检异常（Unchecked）

| | 受检异常 | 非受检异常 |
|--|--|--|
| 继承自 | Exception（非 RuntimeException 分支） | RuntimeException |
| 编译器要求 | 必须 try-catch 或 throws 声明 | 不强制要求 |
| 典型例子 | IOException, SQLException | NullPointerException, IllegalArgumentException |
| 适用场景 | 可预期、可恢复的外部错误（文件不存在） | 程序逻辑错误（空指针、数组越界） |

---

## 附录：阶段一后段 16-20 速查

> 这部分原本属于阶段二，但因为是 Java SE 收尾，常一起被考。

### Chapter 16 - Lambda 与函数式接口

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 函数式接口 | 只有一个抽象方法的接口（`@FunctionalInterface`），可被 Lambda / 方法引用赋值 | Runnable、Callable、Comparator 是函数式接口吗？ |
| 四大内置 | `Function<T,R>` / `Predicate<T>` / `Consumer<T>` / `Supplier<T>` | Function 和 BiFunction 区别？ |
| 方法引用 | `Class::method` 是 Lambda 的简写：静态、实例、构造、绑定 4 种 | `Integer::parseInt` 等价于什么 Lambda？ |
| 变量捕获 | Lambda 引用的局部变量必须是 effectively final | 为什么不能引用可变局部变量？ |

### Chapter 17 - Stream API

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 流操作分类 | 中间（map/filter/sorted，惰性）+ 终结（collect/forEach/reduce） | Stream 是惰性的吗？ |
| collect | `Collectors.toList/toSet/toMap/groupingBy/partitioningBy` | groupingBy 和 partitioningBy 区别？ |
| reduce | `(a,b) -> a+b` 累积；可指定 identity | reduce 必须可结合可交换吗？ |
| parallelStream | 走 ForkJoinPool.commonPool，CPU 密集且无共享状态才用 | 为什么生产慎用 parallelStream？ |
| 性能 | 短流用 for 更快；长流 + 转换链 Stream 更清晰 | Stream 比 for 循环慢吗？ |

### Chapter 18 - Optional 与时间 API

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| Optional 用法 | `ofNullable / orElse / orElseGet / map / ifPresent`，禁止 `get()` | Optional 应该作字段吗？ |
| orElse vs orElseGet | `orElse` 总会求值，`orElseGet` 仅 null 时求值 | 性能差异？ |
| LocalDateTime | 不可变、线程安全；替代老的 Date / Calendar | LocalDateTime 有时区吗？ |
| ZonedDateTime | 带时区；与 `Instant` 互转 | UTC 与 Asia/Shanghai 互转？ |
| Duration vs Period | `Duration` 秒/纳秒；`Period` 年/月/日 | 算两个 LocalDate 间隔用哪个？ |

### Chapter 19 - 注解与反射

| 知识点 | 一句话总结 | 常见面试问题 |
|--------|-----------|-------------|
| 注解元注解 | `@Target / @Retention / @Inherited / @Documented` | RetentionPolicy 三种？ |
| 自定义注解 | 定义属性、配合反射 / APT 处理 | 注解能继承吗？ |
| Class 反射 | `Class.forName / getMethods / getDeclaredFields / setAccessible` | getMethods 与 getDeclaredMethods 区别？ |
| 反射代价 | JIT 难优化、安全检查开销；缓存 Method / Field 可缓解 | 反射慢在哪？ |
| 动态代理 | `Proxy.newProxyInstance` + InvocationHandler；用于 AOP 横切 | JDK 代理 vs CGLIB？ |

### Chapter 20 - 里程碑：Java 工具库

| 知识点 | 一句话总结 |
|--------|-----------|
| Collections | `sort / reverse / unmodifiableList / synchronizedList` |
| Arrays | `asList / sort / stream / copyOf`；`asList` 返回的 List 不可结构修改 |
| Objects | `requireNonNull / equals / hash / toString` 空安全工具 |
| String 新 API（JDK 11+） | `isBlank / strip / lines / repeat` |
| 后续衔接 | 进入 21 章 Maven 工程化 → [[21-Maven工程化]] |
