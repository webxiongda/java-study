# Stream API 理论文档

## 核心概念

### 什么是 Stream

Stream 是 Java 8 引入的**数据处理管道**，用于以声明式、函数式的风格处理集合数据。Stream 不是数据结构，它不存储数据，而是对数据源（集合、数组、IO 等）进行一系列转换和汇聚操作。

**核心特征：**
- **惰性求值（Lazy Evaluation）**：中间操作不立即执行，只有终端操作触发时整条管道才运行
- **不可复用**：Stream 只能消费一次，使用后再操作会抛出 `IllegalStateException`
- **不改变原始数据**：Stream 操作不修改数据源
- **可并行化**：`parallelStream()` 可简单切换为并行处理

### Stream 的创建

```java
// 1. 从集合创建（最常用）
List<String> list = List.of("a", "b", "c");
Stream<String> s1 = list.stream();
Stream<String> s2 = list.parallelStream();  // 并行流

// 2. Stream.of()：从元素创建
Stream<String> s3 = Stream.of("a", "b", "c");

// 3. Arrays.stream()：从数组创建
int[] arr = {1, 2, 3, 4, 5};
IntStream s4 = Arrays.stream(arr);

// 4. Stream.generate()：无限流（需配合 limit 截断）
Stream<Double> randoms = Stream.generate(Math::random).limit(5);

// 5. Stream.iterate()：无限流（有状态，类似递推）
// Java 8：iterate(seed, unaryOperator)
Stream<Integer> evens = Stream.iterate(0, n -> n + 2).limit(10);
// Java 9+：iterate(seed, predicate, unaryOperator)（带终止条件）
Stream<Integer> evens9 = Stream.iterate(0, n -> n < 20, n -> n + 2);

// 6. 基本类型特化流（避免装箱开销）
IntStream intStream = IntStream.range(1, 11);     // [1, 10]
IntStream intStream2 = IntStream.rangeClosed(1, 10); // [1, 10] 含右边界
LongStream longStream = LongStream.of(1L, 2L, 3L);
DoubleStream doubleStream = DoubleStream.of(1.0, 2.0);
```

### 中间操作（Intermediate Operations）

中间操作返回一个新的 Stream，可以链式调用，不会立即执行。

#### filter —— 过滤

```java
// 保留满足条件的元素
List<Integer> evens = List.of(1, 2, 3, 4, 5, 6)
    .stream()
    .filter(n -> n % 2 == 0)
    .collect(Collectors.toList());
// [2, 4, 6]
```

#### map —— 转换（一对一）

```java
// 将每个元素转换为另一种类型或值
List<String> names = List.of("Alice", "Bob", "Charlie");
List<Integer> lengths = names.stream()
    .map(String::length)
    .collect(Collectors.toList());
// [5, 3, 7]
```

#### flatMap —— 扁平化（一对多）

```java
// 将每个元素转换为一个 Stream，然后合并所有 Stream
List<List<Integer>> nested = List.of(
    List.of(1, 2, 3),
    List.of(4, 5),
    List.of(6, 7, 8, 9)
);
List<Integer> flat = nested.stream()
    .flatMap(Collection::stream)
    .collect(Collectors.toList());
// [1, 2, 3, 4, 5, 6, 7, 8, 9]

// 实用场景：将每个订单的商品列表合并
List<String> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .collect(Collectors.toList());
```

#### sorted —— 排序

```java
// 自然排序
List<Integer> sorted1 = Stream.of(3, 1, 4, 1, 5, 9)
    .sorted()
    .collect(Collectors.toList());
// [1, 1, 3, 4, 5, 9]

// 自定义排序（多级排序）
List<String> sorted2 = Stream.of("banana", "apple", "cherry", "apricot")
    .sorted(Comparator.comparingInt(String::length)
                      .thenComparing(Comparator.naturalOrder()))
    .collect(Collectors.toList());
// [apple, banana, cherry, apricot] 先按长度，长度相同按字典序
```

#### distinct / limit / skip

```java
// distinct：去重（依赖 equals 和 hashCode）
List<Integer> unique = Stream.of(1, 2, 2, 3, 3, 3).distinct()
    .collect(Collectors.toList());  // [1, 2, 3]

// limit：截取前 n 个
List<Integer> first3 = Stream.iterate(1, n -> n + 1).limit(3)
    .collect(Collectors.toList());  // [1, 2, 3]

// skip：跳过前 n 个
List<Integer> after3 = Stream.of(1, 2, 3, 4, 5).skip(3)
    .collect(Collectors.toList());  // [4, 5]

// 分页：skip + limit 组合
int page = 2, pageSize = 3;
List<String> pageData = names.stream()
    .skip((long) (page - 1) * pageSize)
    .limit(pageSize)
    .collect(Collectors.toList());
```

#### peek —— 调试用（不改变 Stream）

```java
// peek 用于调试，查看 Stream 中间状态
List<String> result = Stream.of("alice", "bob", "charlie")
    .peek(s -> System.out.println("before filter: " + s))
    .filter(s -> s.length() > 3)
    .peek(s -> System.out.println("after filter: " + s))
    .map(String::toUpperCase)
    .collect(Collectors.toList());
// 只有调试时用，生产代码避免在 peek 里写副作用逻辑
```

### 终端操作（Terminal Operations）

终端操作触发整条管道执行，返回非 Stream 的结果。

#### forEach / forEachOrdered

```java
Stream.of("a", "b", "c").forEach(System.out::println);
// 并行流中元素顺序不保证，forEachOrdered 保证顺序（但性能低）
```

#### collect —— 汇聚（最重要的终端操作）

```java
// 收集到 List
List<String> list = stream.collect(Collectors.toList());
// Java 16+ 更简洁：
List<String> list2 = stream.toList();  // 返回不可变 List

// 收集到 Set
Set<String> set = stream.collect(Collectors.toSet());

// 收集到指定集合类型
LinkedList<String> linkedList = stream.collect(
    Collectors.toCollection(LinkedList::new));
```

#### reduce —— 归约（汇总计算）

```java
// reduce(identity, accumulator)：从 identity 开始，将所有元素累积
int sum = Stream.of(1, 2, 3, 4, 5)
    .reduce(0, Integer::sum);  // 0+1+2+3+4+5 = 15

// reduce(accumulator)：无 identity，返回 Optional（因为 Stream 可能为空）
Optional<Integer> product = Stream.of(1, 2, 3, 4, 5)
    .reduce((a, b) -> a * b);  // 1*2*3*4*5 = 120

// 字符串拼接
String joined = Stream.of("Hello", "World", "Java")
    .reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b);
// "Hello World Java"
```

#### count / min / max / sum / average

```java
long count = stream.count();

Optional<Integer> min = Stream.of(3, 1, 4, 1, 5).min(Integer::compareTo);
Optional<Integer> max = Stream.of(3, 1, 4, 1, 5).max(Integer::compareTo);

// IntStream 专有
int sum = IntStream.range(1, 6).sum();         // 15
double avg = IntStream.range(1, 6).average().orElse(0);  // 3.0
```

#### findFirst / findAny

```java
Optional<String> first = stream.filter(s -> s.startsWith("A")).findFirst();
// findAny 在并行流中性能更好，但不保证取哪个
```

#### anyMatch / allMatch / noneMatch

```java
boolean any  = stream.anyMatch(s -> s.contains("Java"));    // 有任意一个满足
boolean all  = stream.allMatch(s -> s.length() > 0);        // 全部满足
boolean none = stream.noneMatch(s -> s.startsWith("Z"));    // 全部不满足
```

### Collectors —— 强大的汇聚器

#### toMap

```java
// 将 Stream 转换为 Map
Map<String, Integer> nameToLength = names.stream()
    .collect(Collectors.toMap(
        Function.identity(),  // key：元素本身
        String::length        // value：元素长度
    ));

// 如果 key 可能重复，需要提供合并函数
Map<String, Long> wordCount = words.stream()
    .collect(Collectors.toMap(
        Function.identity(),
        w -> 1L,
        Long::sum  // key 冲突时：将值相加
    ));
```

#### groupingBy —— 分组（最常用）

```java
// 按首字母分组
Map<Character, List<String>> byFirstChar = names.stream()
    .collect(Collectors.groupingBy(s -> s.charAt(0)));

// 按长度分组，值只取名字本身（downstreamCollector）
Map<Integer, List<String>> byLength = names.stream()
    .collect(Collectors.groupingBy(String::length));

// 分组后统计数量
Map<Integer, Long> countByLength = names.stream()
    .collect(Collectors.groupingBy(String::length, Collectors.counting()));

// 多级分组
Map<String, Map<String, List<Order>>> grouped = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getCustomerType,
        Collectors.groupingBy(Order::getStatus)
    ));
```

#### joining —— 字符串拼接

```java
String result = Stream.of("A", "B", "C")
    .collect(Collectors.joining(", ", "[", "]"));
// "[A, B, C]"
```

#### counting / summingInt / averagingInt

```java
long count = stream.collect(Collectors.counting());
int total = stream.collect(Collectors.summingInt(String::length));
double avg = stream.collect(Collectors.averagingInt(String::length));
```

#### partitioningBy —— 二分法

```java
// 将集合分为满足条件和不满足条件两部分，key 是 true/false
Map<Boolean, List<Integer>> partitioned = Stream.of(1, 2, 3, 4, 5, 6)
    .collect(Collectors.partitioningBy(n -> n % 2 == 0));
// {true=[2, 4, 6], false=[1, 3, 5]}
```

### 并行流

```java
// 创建并行流
list.parallelStream()
    .filter(...)
    .map(...)
    .collect(Collectors.toList());

// 已有流转换为并行
stream.parallel()...

// 并行流的合并统计示例
long sumParallel = LongStream.rangeClosed(1, 1_000_000)
    .parallel()
    .sum();
```

---

## 使用场景

| 场景 | 推荐操作 |
|------|---------|
| 过滤集合 | `filter()` |
| 转换集合类型 | `map()` |
| 展开嵌套集合 | `flatMap()` |
| 统计求和 | `reduce()` / `IntStream.sum()` |
| 分组统计 | `groupingBy()` + `counting()` |
| 去重 | `distinct()` |
| 分页 | `skip()` + `limit()` |
| 字符串拼接 | `Collectors.joining()` |
| 转 Map | `Collectors.toMap()` |

---

## 工作原理

### 惰性求值与流水线执行

Stream 操作分两类：
- **有状态中间操作**（stateful）：`sorted`、`distinct`、`limit`、`skip`——需要知道所有或部分元素才能执行
- **无状态中间操作**（stateless）：`filter`、`map`、`peek`——每个元素独立处理

执行方式：**源头 → 中间操作链（Spliterator + 操作管道）→ 终端操作**

对于无状态操作，JVM 通常采用**垂直执行**（vertical slicing）——将一个元素依次通过所有无状态操作，而不是一次性处理整个集合再进行下一步。这意味着如果 `limit(1)` 在后面，前面的 `filter` 可能只执行一次就结束了。

### 短路操作

`findFirst`、`findAny`、`anyMatch`、`allMatch`、`noneMatch`、`limit` 都是短路操作，满足条件后立即停止处理剩余元素：

```java
// 这不会处理所有 1000000 个元素
Optional<Integer> first = Stream.iterate(1, n -> n + 1)
    .limit(1_000_000)
    .filter(n -> n > 500)
    .findFirst();  // 找到 501 就停止
```

---

## 常见坑与易错点

### 坑 1：Stream 只能使用一次

```java
Stream<String> stream = list.stream();
long count = stream.count();         // 正常
List<String> lst = stream.collect(Collectors.toList()); // 抛出 IllegalStateException！

// 正确做法：每次操作新建 Stream
long count = list.stream().count();
List<String> lst = list.stream().collect(Collectors.toList());
```

### 坑 2：toMap 遇到重复 key 会抛异常

```java
List<String> words = List.of("apple", "ant", "banana");
// 错误：按首字母分组，'a' 对应两个元素，toMap 会抛 IllegalStateException
Map<Character, String> map = words.stream()
    .collect(Collectors.toMap(s -> s.charAt(0), s -> s));

// 正确：提供 merge 函数或改用 groupingBy
Map<Character, String> map2 = words.stream()
    .collect(Collectors.toMap(
        s -> s.charAt(0),
        s -> s,
        (existing, newVal) -> existing + "," + newVal  // 冲突时拼接
    ));
// {a=apple,ant, b=banana}

// 或者用 groupingBy
Map<Character, List<String>> grouped = words.stream()
    .collect(Collectors.groupingBy(s -> s.charAt(0)));
```

### 坑 3：并行流不总是更快

```java
// 小数据量反而更慢（线程调度开销 > 计算节省）
// 有状态操作（如 sorted）并行后需要合并，不一定有收益
// 有副作用的 Lambda（如写文件）并行会有线程安全问题

// 并行流适合：数据量大（万级以上）、无状态操作、CPU 密集型计算
// 不适合：IO 操作、涉及共享可变状态、需要严格顺序的操作
```

### 坑 4：用 Collectors.toList() 还是 .toList()？

```java
// Java 16 引入了 Stream.toList()
List<String> list = stream.toList();  // 返回不可变 List（不能 add/remove）

// Collectors.toList() 返回可变 List（通常是 ArrayList）
List<String> mutableList = stream.collect(Collectors.toList());

// 如果后续需要修改列表，必须用 Collectors.toList() 或 toCollection(ArrayList::new)
list.add("new item");  // UnsupportedOperationException！如果用的是 toList()
```

### 坑 5：基本类型 Stream 与对象 Stream 的类型不一致

```java
int[] arr = {1, 2, 3};

// 错误：Arrays.stream(int[]) 返回 IntStream，不是 Stream<Integer>
// IntStream 和 Stream<Integer> 的 API 不完全一样

IntStream intStream = Arrays.stream(arr);
// 如果需要 Stream<Integer>，需要装箱
Stream<Integer> boxed = Arrays.stream(arr).boxed();

// groupingBy 等操作只在 Stream<T> 上有，IntStream 没有
// 所以需要先 boxed()
Map<Integer, Long> countMap = Arrays.stream(arr)
    .boxed()  // IntStream -> Stream<Integer>
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
```

---

## 面试高频问题

### Q1：Stream 的中间操作和终端操作有什么区别？什么是惰性求值？

**答：** 中间操作返回一个新的 Stream，可以链式调用，本身不触发任何计算，只是描述"要做什么"，如 `filter`、`map`、`sorted`。终端操作返回非 Stream 类型的结果（如 `List`、`int`、`Optional`、`void`），触发整条管道的实际执行，如 `collect`、`count`、`forEach`。

惰性求值（Lazy Evaluation）：中间操作被定义时不执行，只有终端操作调用时，JVM 才会真正遍历数据源，将每个元素依次通过所有中间操作处理。好处是：可以短路——如果终端操作只需要部分结果（如 `findFirst`），不需要处理所有元素。

### Q2：map 和 flatMap 有什么区别？什么时候用 flatMap？

**答：** `map` 是一对一转换：每个输入元素映射到一个输出元素。`flatMap` 是一对多映射加扁平化：每个输入元素映射到一个 Stream，然后将所有 Stream 合并为一个 Stream。

用 `flatMap` 的场景：处理嵌套集合（如 `List<List<T>>`）、将一个对象展开为多个元素、将字符串分割为字符等。典型例子：

```java
// 统计一篇文章中所有单词
List<String> sentences = List.of("Hello World", "Java Stream API");
List<String> words = sentences.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))
    .collect(Collectors.toList());
// [Hello, World, Java, Stream, API]
```

### Q3：reduce 和 collect 有什么区别？什么时候用 reduce？

**答：** `reduce` 是**归约**操作，将 Stream 中所有元素通过二元操作合并为一个值，适合求和、求积、字符串拼接等"将多个值合并为一个值"的场景。`reduce` 操作要求是关联的（结合律），以便并行化。

`collect` 是**汇聚**操作，将 Stream 中的元素按照某种容器方式收集起来，底层使用可变的中间容器（如 `ArrayList`）。适合收集到集合、构建 Map、分组统计等。

关键区别：`reduce` 生成一个不可变的最终值；`collect` 使用可变容器积累结果，然后转化为最终结果。在并行流中，`collect` 通过合并多个部分容器来实现并行，性能通常优于 `reduce`。

### Q4：groupingBy 的多级分组和 downstream Collector 怎么用？

**答：** `groupingBy(classifier, downstreamCollector)` 的第二个参数是对分组后每个桶内元素的进一步汇聚。

```java
// 按部门分组，每组统计人数
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));

// 按部门分组，每组求平均薪资
Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.averagingInt(Employee::getSalary)));

// 多级分组：先按部门，再按职级
Map<String, Map<String, List<Employee>>> multiLevel = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDept,
        Collectors.groupingBy(Employee::getLevel)));
```

理解 `downstream` 的关键：把 `groupingBy` 想象成"按 key 分桶后，每个桶里的元素再做一次 `collect(downstream)`"。

### Q5：并行流的适用场景和注意事项是什么？

**答：** 适用场景：
1. 数据量大（通常万条以上才有意义）
2. 操作是 CPU 密集型（无 IO）
3. 操作是无状态的、没有副作用
4. 不需要严格的处理顺序

注意事项：
1. **不要用于 IO 操作**：并行流的线程池是 ForkJoinPool，线程数量有限，IO 阻塞会耗尽线程
2. **避免共享可变状态**：在 Lambda 中修改共享变量会有线程安全问题
3. **避免 sorted**：有状态操作并行后需要在 fork/join 框架中合并结果，代价高
4. **测量再决定**：数据量小时，线程调度开销可能让并行流比顺序流更慢
5. **forEachOrdered 性能差**：并行流中用 `forEachOrdered` 保证顺序，会丧失并行优势

### Q6：Stream 的 toList() 和 Collectors.toList() 有什么区别？（JDK 16+）

**答：** `Stream.toList()`（Java 16+）返回**不可变的 List**（实际上是 `List.of(...)` 的等效实现），调用 `add`/`remove` 等修改方法会抛 `UnsupportedOperationException`。`Collectors.toList()` 返回可变的 `ArrayList`，可以正常修改。

实际选择建议：如果后续不需要修改，优先用 `toList()`（更简洁，语义更清晰，JVM 可能优化更好）；如果需要修改，用 `Collectors.toCollection(ArrayList::new)`（比 `Collectors.toList()` 更明确）。
