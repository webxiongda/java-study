# Chapter 17 Stream API - 自测与验收

> 模板见 `docs/superpowers/specs/2026-05-25-check-template.md`
> 覆盖率自检:`node scripts/check-coverage.mjs '^17-'`

---

### Q1 [L1·概念·章节内测] 惰性求值与流水线执行 + 短路操作有哪些?

**考点**: 惰性求值与流水线执行, 中间操作 vs 终端操作, 短路操作
**参考答案**:

**惰性求值(Lazy Evaluation)**:中间操作不立即执行,只描述"要做什么";终端操作才触发整条 Pipeline 真正运行。

```java
// 没有终端操作 → 一个字符都不会打印
Stream<String> s = List.of("a", "bb", "ccc").stream()
    .filter(x -> { System.out.println("filter: " + x); return x.length() > 1; })
    .map(x -> { System.out.println("map: " + x); return x.toUpperCase(); });
// (此处无输出)

s.forEach(System.out::println);  // 此时 filter / map 才被执行
```

**中间操作 vs 终端操作**:

| 维度 | 中间操作 | 终端操作 |
|---|---|---|
| 返回类型 | Stream | 非 Stream(集合/Optional/基本类型/void) |
| 是否触发执行 | 否(描述) | 是 |
| 链式 | 可多个 | 只能 1 个,执行后 Stream 关闭 |
| 例子 | filter / map / flatMap / sorted / distinct / limit / skip / peek | collect / reduce / count / forEach / findFirst / anyMatch / toArray |

**短路操作(满足条件立刻停)**:

- 终端:`findFirst` / `findAny` / `anyMatch` / `allMatch` / `noneMatch`
- 中间:`limit`

```java
// 不会真的产生 1_000_000 个元素 — limit 是短路的
Optional<Integer> first = Stream.iterate(1, n -> n + 1)
    .filter(n -> n > 500)
    .limit(1_000_000)
    .findFirst();   // 找到 501 立即返回
```

**JVM 的"垂直执行"(vertical slicing)**:对无状态操作,JVM 让一个元素依次过完所有中间操作,而不是先全 filter 再全 map。所以 `stream.filter(...).findFirst()` 在百万元素里可能只处理 1 个元素。

**🔥追问**:有状态 vs 无状态中间操作?(答:`sorted` / `distinct` / `limit` / `skip` 需要看到多个元素才决定 → 有状态;`filter` / `map` / `peek` 元素独立 → 无状态。并行流里有状态操作会变贵)

---

### Q2 [L2·代码阅读·章节内测] 解释 GroupingByDemo 里的多级分组 + downstream 工作原理

**考点**: collect, groupingBy, partitioningBy, downstream Collector, GroupingByDemo, Order
**参考答案**:

`groupingBy(classifier, downstreamCollector)` = **按 classifier 分桶,每个桶里再做一次 collect(downstream)**。

参考 02-demo.md 的 `GroupingByDemo`(Order: orderId / category / status / amount):

```java
// 1. 单参数:Map<分类, List<整个对象>>
Map<String, List<Order>> byCategory = orders.stream()
    .collect(Collectors.groupingBy(Order::category));

// 2. 双参数:每桶再 counting() → Map<状态, 数量>
Map<String, Long> countByStatus = orders.stream()
    .collect(Collectors.groupingBy(Order::status, Collectors.counting()));

// 3. summingDouble:每桶求和 → Map<分类, 已支付总收入>
Map<String, Double> revenueByCategory = orders.stream()
    .filter(o -> "PAID".equals(o.status()))
    .collect(Collectors.groupingBy(Order::category,
        Collectors.summingDouble(Order::amount)));

// 4. maxBy:每桶找最大 → Map<分类, Optional<最贵订单>>
Map<String, Optional<Order>> mostExp = orders.stream()
    .collect(Collectors.groupingBy(Order::category,
        Collectors.maxBy(Comparator.comparingDouble(Order::amount))));

// 5. 多级分组:嵌套 groupingBy → Map<分类, Map<状态, 数量>>
Map<String, Map<String, Long>> multi = orders.stream()
    .collect(Collectors.groupingBy(Order::category,
        Collectors.groupingBy(Order::status, Collectors.counting())));
```

**partitioningBy vs groupingBy(boolean classifier)**:

```java
// partitioningBy 性能更好 — 只有 true/false 两个桶,固定结构
Map<Boolean, List<Order>> paidOrNot = orders.stream()
    .collect(Collectors.partitioningBy(o -> "PAID".equals(o.status())));
// {true=[...已支付...], false=[...未支付...]}
```

| 维度 | groupingBy | partitioningBy |
|---|---|---|
| 桶数 | N(classifier 返回值) | 固定 2 个 |
| key 类型 | 任意 | Boolean |
| key 缺失 | 桶不出现 | true/false 一定都有(可能是空 list) |
| 性能 | 一般 | 更好(避免 HashMap 开销) |

**🔥追问**:`groupingBy` 默认返回什么 Map?要保证有序怎么办?

**参考答案**:默认返回 `HashMap`(无序)。要有序用三参数版指定 Map 工厂:
```java
new TreeMap<>()   // 按 key 自然序
new LinkedHashMap<>()  // 按插入(遇到)顺序
```

**关联**: interview-bank.md#stream-groupingby-downstream

---

### Q3 [L2·代码编写·章节内测] 用 reduce 三参数版 + joining,完成 ReduceDemo 里的"工程部薪资统计"题

**考点**: reduce, collect, joining, ReduceDemo, Employee, SalaryAccumulator
**参考答案**:

题:给定 `List<Employee>`(name / dept / salary),求工程部(`"Engineering"`)的 **总薪资 + 人数 + 平均薪资 + 名单字符串(用 ", " 连接)**,只能遍历一次。

```java
record Employee(String name, String dept, int salary) {}

// 方案 A:用 reduce 三参数版(并行安全)做累积
record SalaryAccumulator(long totalSalary, int count, String names) {
    double average() { return count == 0 ? 0 : (double) totalSalary / count; }
}

List<Employee> employees = List.of(
    new Employee("Alice", "Engineering", 15000),
    new Employee("Bob",   "Engineering", 18000),
    new Employee("Carol", "Marketing",   12000),
    new Employee("Dave",  "Engineering", 20000)
);

SalaryAccumulator stats = employees.stream()
    .filter(e -> "Engineering".equals(e.dept()))
    .reduce(
        new SalaryAccumulator(0, 0, ""),              // identity
        (acc, emp) -> new SalaryAccumulator(           // accumulator
            acc.totalSalary() + emp.salary(),
            acc.count() + 1,
            acc.names().isEmpty() ? emp.name() : acc.names() + ", " + emp.name()
        ),
        (a, b) -> new SalaryAccumulator(               // combiner(并行流用)
            a.totalSalary() + b.totalSalary(),
            a.count() + b.count(),
            a.names().isEmpty() ? b.names() : (b.names().isEmpty() ? a.names() : a.names() + ", " + b.names())
        )
    );
System.out.printf("总薪资=%d 人数=%d 平均=%.0f 名单=[%s]%n",
    stats.totalSalary(), stats.count(), stats.average(), stats.names());
// 总薪资=53000 人数=3 平均=17667 名单=[Alice, Bob, Dave]
```

**为什么 reduce 要传 combiner?**

`reduce(identity, accumulator, combiner)` 第三参是并行流合并多个部分结果用。顺序流不会调它,但必须传(签名要求)。一般规则:**accumulator 把"累加器 + 元素"合并;combiner 把"两个累加器"合并**。

**方案 B(更地道)— 用 collect / joining**:

```java
// 多个独立统计 → 分别 collect 也行,但要遍历多次
String engNames = employees.stream()
    .filter(e -> "Engineering".equals(e.dept()))
    .map(Employee::name)
    .collect(Collectors.joining(", ", "[", "]"));   // "[Alice, Bob, Dave]"

IntSummaryStatistics engStat = employees.stream()
    .filter(e -> "Engineering".equals(e.dept()))
    .mapToInt(Employee::salary)
    .summaryStatistics();    // count/sum/min/max/avg 一次拿全
```

**reduce vs collect 该选谁?**

| | reduce | collect |
|---|---|---|
| 适合 | 把多个值"折"成一个不可变值(求和、求积、字符串拼接小数据) | 用可变容器累积(构集合、Map、分组) |
| 并行 | 函数必须满足结合律 | 内置支持(combiner 自动合并部分容器) |
| 构集合 | ❌ 每步都新建对象,性能差 | ✅ 用 ArrayList 累积 |
| 字符串拼接 | 小数据可,大数据慢 | `Collectors.joining` 内部 StringBuilder |

**🔥追问**:为什么 `IntStream.sum()` 比 `Stream<Integer>.reduce(0, Integer::sum)` 快?(答:IntStream 是基本类型流,避免 Integer 装箱/拆箱开销)

**关联**: interview-bank.md#stream-reduce-vs-collect

---

### Q4 [L2·Debug·面试高频] 5 段 Stream 代码各有什么坑?改正

**考点**: Stream 只能用一次, toMap 重复 key, map vs flatMap, parallelStream 陷阱, toList 不可变
**参考答案**:

```java
// === 坑 1:Stream 二次消费 ===
Stream<String> s = words.stream();
long n = s.count();
List<String> lst = s.collect(Collectors.toList());   // ❌ IllegalStateException

// 改:每次新建 Stream
long n2 = words.stream().count();
List<String> lst2 = words.stream().collect(Collectors.toList());

// === 坑 2:lambda 改外部局部变量 ===
int sum = 0;
numbers.stream().forEach(x -> sum += x);   // ❌ 编译报错(必须 effectively final)

// 改:用 reduce / mapToInt().sum()
int s1 = numbers.stream().mapToInt(Integer::intValue).sum();

// === 坑 3:map 套 Stream → Stream<Stream<T>>,要扁平化用 flatMap ===
List<Integer> flat = nested.stream()
    .map(List::stream)                       // ❌ 类型变成 Stream<Stream<Integer>>
    .collect(Collectors.toList());

// 改:flatMap
List<Integer> flat2 = nested.stream()
    .flatMap(List::stream)                   // ✅ Stream<Integer>
    .collect(Collectors.toList());

// === 坑 4:toMap 遇到重复 key 抛 IllegalStateException ===
Map<Character, String> m = words.stream()
    .collect(Collectors.toMap(s -> s.charAt(0), s -> s));   // ❌ 'a' 重复

// 改 A:提供 merge 函数
Map<Character, String> m1 = words.stream()
    .collect(Collectors.toMap(s -> s.charAt(0), s -> s,
        (existing, newVal) -> existing + "," + newVal));

// 改 B:换 groupingBy(语义更清晰)
Map<Character, List<String>> m2 = words.stream()
    .collect(Collectors.groupingBy(s -> s.charAt(0)));

// === 坑 5:Java 16 Stream.toList() 返回不可变 List ===
List<String> r = stream.toList();
r.add("new");           // ❌ UnsupportedOperationException

// 改:需要可变就用 Collectors.toList() / toCollection(ArrayList::new)
List<String> r2 = stream.collect(Collectors.toCollection(ArrayList::new));
```

**🔥追问**:`Stream.toList()` 与 `Collectors.toList()` 你优先选哪个?

**参考答案**:**默认 `Stream.toList()`(JDK 16+)**。理由:
- 语义清晰("我就要个 List"),不需要拼 `Collectors.xxx`
- 返回不可变 List,反向逼迫调用方不要修改返回值
- JVM 可能做内部优化(已知大小一次性分配)

**只有"返回后还要 add/remove"** 时才用 `Collectors.toCollection(ArrayList::new)`(比 `Collectors.toList()` 更明确表达"我要 ArrayList")。

**关联**: interview-bank.md#stream-toMap-pitfall

---

### Q5 [L3·场景设计·面试高频] 用 Stream 实现"按类别分组每组 TOP-N",讨论并行流的选型

**考点**: collectingAndThen, Comparator, 并行流, StreamPipelineDemo, Product
**参考答案**:

参考 02-demo.md `StreamPipelineDemo` 的 `Product`(id / name / category / price / tags)。需求:**按类别分组,每组按价格降序取 TOP-3**,500 个商品。

**写法 1:groupingBy + collectingAndThen(标准答案)**

```java
record Product(String id, String name, String category, double price, List<String> tags) {}

Map<String, List<Product>> top3ByCategory = products.stream()
    .collect(Collectors.groupingBy(
        Product::category,
        Collectors.collectingAndThen(
            Collectors.toList(),
            list -> list.stream()
                .sorted(Comparator.comparingDouble(Product::price).reversed())
                .limit(3)
                .toList())
    ));
```

`collectingAndThen(downstream, finisher)` = 先用 downstream 收集,再对结果做一次后处理。这里:先 `toList` 拿到该分类全部商品 → 再对 list 排序取前 3。

**写法 2:先排序再 groupingBy**

```java
Map<String, List<Product>> top3 = products.stream()
    .sorted(Comparator.comparingDouble(Product::price).reversed())
    .collect(Collectors.groupingBy(Product::category))
    .entrySet().stream()
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> e.getValue().stream().limit(3).toList(),
        (a, b) -> a,
        LinkedHashMap::new));   // 保持插入顺序
```

写法 2 排了整个 list,5 万条数据时比写法 1 慢。**写法 1 优**(每个分组单独排,数据量小)。

**这里能不能用并行流?**

```java
products.parallelStream()...   // ❓
```

**结论**:500 个商品 = **不能**。

**并行流 4 个陷阱**:

1. **数据量小 → 反而更慢**。并行流走 `ForkJoinPool.commonPool`,任务拆分 + 线程调度开销在小数据下吃光收益。**经验**:CPU 密集型 + 数据量 ≥ 1 万才考虑。
2. **IO 操作 → 灾难**。`commonPool` 默认线程数 = `CPU 核数 - 1`,IO 阻塞会把池打满,拖累整个 JVM 内所有用并行流的代码。
3. **有状态操作贵**。`sorted` / `distinct` / `limit` 并行后要合并多个子结果,fork/join 开销大;`forEachOrdered` 直接放弃并行优势。
4. **副作用 = 线程安全坑**。lambda 里写共享变量(写 List、改 Map、累加 int)会有竞争。`stream.forEach(list::add)` 在并行流下大概率丢数据。

**判断并行流是否值得**:

```java
// 满足以下全部才考虑 parallel:
// 1. 数据量 ≥ 10_000
// 2. 单元素处理是 CPU 密集(纯计算,无 IO,无锁)
// 3. 操作是无状态、无副作用
// 4. 不需要严格顺序

// 反例 — 这种千万别 parallel:
list.parallelStream()
    .map(id -> db.findById(id))     // ❌ IO
    .forEach(results::add);          // ❌ 共享可变状态
```

**面试 2 分钟讲法**:

> "标准实现是 groupingBy + collectingAndThen,在 downstream 里对每个分组排序取 TOP-3,这样比'先全排序再分组'快,因为局部排序的数据量小。能不能并行?要看 4 条:数据量 ≥ 1 万、CPU 密集、无状态、不要求顺序。500 个商品没必要并行;真要做 TOP-N over 千万级数据,我宁可用堆(`PriorityQueue` size N,新元素和堆顶比),时间 O(n log N),不依赖排序整个列表;再大就上 MapReduce 或流式系统了。"

**🔥追问**:`Collectors.toList()` 内部其实是 ArrayList,为什么不直接 `new ArrayList<>(stream)`?

**参考答案**:`ArrayList<>(Collection)` 要求传入 Collection,Stream 不是 Collection(它不存储)。`stream.toList()` / `collect(Collectors.toList())` 是从无到有累积一个 List 的标准入口。

**关联**: interview-bank.md#stream-parallel-cautions

---

## 通过标准

- [ ] 能讲清惰性求值 + 中间/终端 + 6 个短路操作 + 有状态/无状态区别
- [ ] 能默写 `groupingBy(classifier, downstreamCollector)` 的 4 种 downstream 组合 + 多级分组
- [ ] 能用 reduce 三参数版完成"一次遍历多统计"题,并讲清 combiner 的角色
- [ ] 能识别 Stream 5 个常见坑:二次消费 / 外部变量 / map vs flatMap / toMap 重复 key / toList 不可变
- [ ] 能现场写 TOP-N over 分组,并讲清并行流 4 个适用条件 / 4 个陷阱
