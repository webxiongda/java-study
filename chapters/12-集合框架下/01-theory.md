# 集合框架下 理论文档

## 核心概念

本章是面试最高频的部分，重点掌握 HashMap 底层原理，这一块几乎是中高级 Java 面试的必考题。

---

### HashMap 底层数据结构

HashMap 在 Java 8 及之后的底层结构是：**数组 + 链表 + 红黑树**（之前只有数组+链表）。

**核心字段：**

```java
// 底层数组（桶数组），每个桶可以是链表头节点或红黑树根节点
transient Node<K,V>[] table;

// 负载因子，默认 0.75
final float loadFactor;

// 实际存储的键值对数量
transient int size;

// 扩容阈值 = capacity * loadFactor
int threshold;

// 修改次数（fail-fast 用）
transient int modCount;
```

**Node 节点结构：**

```java
static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;    // key 的 hash 值
    final K key;
    V value;
    Node<K,V> next;    // 指向下一个节点（链表）
}
```

---

### HashMap 的 put 流程（面试必答）

当你调用 `map.put(key, value)` 时，内部流程如下：

```
1. 计算 hash(key)
   → 先调用 key.hashCode()
   → 再做扰动：(h = key.hashCode()) ^ (h >>> 16)
   → 目的是让高位也参与散列，减少碰撞

2. 确定桶的下标
   → index = (n-1) & hash  （n 是数组长度，必须是 2 的幂）
   → 等价于 hash % n，但位运算更快

3. 判断该桶是否为空
   → 为空：直接创建 Node，放入桶中
   → 不为空（发生 hash 碰撞）：

4. 碰撞处理
   → 遍历链表/红黑树，找 key 是否已存在（先比 hash，再比 equals）
   → 已存在：覆盖 value
   → 不存在：
       - 链表长度 < 8：尾插入链表
       - 链表长度 >= 8 且数组长度 >= 64：转为红黑树（treeifyBin）
       - 链表长度 >= 8 但数组长度 < 64：先扩容

5. 检查是否需要扩容
   → 插入后 size > threshold（即 capacity * 0.75）时触发 resize()
```

**流程图文字版：**

```
put(k, v)
  ├─ table 为空 → resize() 初始化
  ├─ 计算 index，桶为空 → 直接放入
  └─ 桶不为空
        ├─ key 与桶头相同 → 覆盖
        ├─ 桶是红黑树节点 → 树插入
        └─ 桶是链表
              ├─ 遍历找到相同 key → 覆盖
              └─ 未找到 → 尾插，长度>=8 考虑树化
  → size++ → 超过 threshold → resize()
```

---

### 扩容机制（resize）

**触发条件：** `size > capacity * loadFactor`（默认：size > 16 * 0.75 = 12）

**扩容步骤：**
1. 新容量 = 旧容量 × 2（例如 16 → 32）
2. 创建新数组
3. 重新散列（rehash）所有节点到新数组
   - 利用 `(hash & oldCapacity) == 0` 判断节点在新数组中的位置是不变还是 +oldCapacity
   - 这是 2 的幂设计带来的优化，O(1) 计算新位置

**为什么负载因子是 0.75？**
这是时间和空间的权衡：
- 太小（如 0.5）：碰撞少，但内存浪费，频繁扩容
- 太大（如 1.0）：内存利用率高，但碰撞多，查找退化为链表 O(n)
- 0.75 是泊松分布下碰撞概率最低的经验值

**初始容量为什么是 16？**
16 是 2 的幂，满足位运算优化（index = hash & (n-1)）。如果你预知数据量，应在构造时指定合适容量：`new HashMap<>(initialCapacity / 0.75 + 1)` 避免扩容。

---

### 链表树化与反树化

**树化条件（同时满足）：**
- 单个桶的链表长度 >= 8
- 数组容量 >= 64

**反树化（退化回链表）条件：**
- 树节点数量 <= 6（在 resize 时检查）

**为什么是 8？**
按泊松分布，一个桶里有 8 个元素的概率约为 0.00000006（极低），正常情况很少触发树化。树化的主要目的是防止恶意构造大量相同 hashCode 的 key 导致 DOS 攻击（链表查找 O(n) 变树查找 O(log n)）。

---

### equals() 和 hashCode() 契约

这是 Java 对象相等性的核心规定，使用 HashMap/HashSet 必须遵守：

**规定：**
1. 如果 `a.equals(b)` 为 true，则 `a.hashCode() == b.hashCode()` 必须成立
2. 如果 `a.hashCode() == b.hashCode()`，`a.equals(b)` 不一定为 true（允许 hash 碰撞）
3. 反过来：如果 `a.hashCode() != b.hashCode()`，则 `a.equals(b)` 一定为 false

**违反后果：**
- 只重写 equals，不重写 hashCode：HashMap 找不到 key（两个"相等"的对象 hashCode 不同，存到不同桶里）
- 只重写 hashCode，不重写 equals：存到同一桶里，但 equals 认为不同，会存两份

```java
// 错误示例：只重写 equals
class BadKey {
    String name;
    BadKey(String name) { this.name = name; }
    @Override
    public boolean equals(Object o) {
        return o instanceof BadKey && ((BadKey)o).name.equals(this.name);
    }
    // 没有重写 hashCode，使用 Object 默认的（基于内存地址）
}

Map<BadKey, String> map = new HashMap<>();
map.put(new BadKey("alice"), "value");
System.out.println(map.get(new BadKey("alice"))); // null！！！
// 两个 new BadKey("alice") 的 hashCode 不同，put 和 get 找到不同的桶

// 正确做法：同时重写
@Override
public int hashCode() {
    return Objects.hash(name); // 用 Objects.hash() 简化
}
```

---

### Comparable vs Comparator

**Comparable**（可比较的）：侵入式，对象本身实现比较逻辑，定义"自然顺序"。

```java
class Student implements Comparable<Student> {
    String name;
    int age;

    @Override
    public int compareTo(Student other) {
        return this.age - other.age; // 按年龄升序（负数=小于，0=等于，正数=大于）
    }
}

List<Student> students = ...;
Collections.sort(students); // 使用自然顺序
TreeSet<Student> set = new TreeSet<>(); // 自动按年龄排序
```

**Comparator**（比较器）：非侵入式，独立的比较策略，更灵活，可临时定义多种排序。

```java
// 按名字排序
Comparator<Student> byName = Comparator.comparing(Student::getName);

// 按年龄降序
Comparator<Student> byAgeDesc = Comparator.comparingInt(Student::getAge).reversed();

// 多字段排序：先按年龄升序，年龄相同按名字
Comparator<Student> combined = Comparator.comparingInt(Student::getAge)
                                          .thenComparing(Student::getName);

students.sort(combined);
```

**选择建议：**
- 有明确"自然顺序"的用 `Comparable`（如 Integer、String 已经实现了）
- 需要多种排序策略或不能修改类源码时用 `Comparator`

---

### ConcurrentHashMap 概念

HashMap 是线程不安全的，多线程环境下应使用 `ConcurrentHashMap`（JUC 包）。

**Java 8+ 的 ConcurrentHashMap 实现：**
- 底层结构与 HashMap 相同（数组+链表+红黑树）
- 使用 `synchronized` 锁定单个桶（Node 节点）+ CAS 操作，锁粒度更细
- JDK 7 的分段锁（Segment）设计在 JDK 8 被抛弃，因为锁桶粒度更细

**常用场景：**
```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// 线程安全的原子性更新
map.merge("key", 1, Integer::sum);
map.compute("key", (k, v) -> v == null ? 1 : v + 1);

// 非原子操作仍然需要手动同步
// if (!map.containsKey(k)) map.put(k, v); // 这不是原子操作！
// 正确：用 putIfAbsent 或 computeIfAbsent
map.putIfAbsent("key", "value");
```

---

## 使用场景

- **HashMap**：单线程键值对存储，最通用。
- **LinkedHashMap**：需要保持插入顺序或访问顺序（LRU 缓存经典实现）。
- **TreeMap**：需要键有序（范围查询、排名）。
- **ConcurrentHashMap**：多线程共享的键值对存储。
- **Comparable**：类有明确的"默认排序"语义（如按 ID、按时间）。
- **Comparator**：临时排序、多策略排序、不能修改类源码时。

---

## 工作原理

**HashMap 的 hash 扰动函数：**
```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```
高 16 位 XOR 低 16 位，让 hash 的高位也参与到下标计算中（因为 `(n-1)&hash` 在 n 较小时只用到低位），减少碰撞。

**红黑树插入：** 红黑树是自平衡二叉搜索树，保证最坏情况下操作是 O(log n)，而链表是 O(n)。树化后查找性能大幅提升。

---

## 常见坑与易错点

### 坑 1：HashMap 在多线程下丢数据或死循环

JDK 7 的 HashMap 在并发 resize 时可能形成链表环，导致 `get()` 死循环（CPU 100%）。JDK 8 虽然改了头插为尾插，解决了死循环问题，但仍然线程不安全（会丢数据）。

```java
// 错误：多线程共享 HashMap
Map<String, String> shared = new HashMap<>(); // 危险！

// 正确：使用 ConcurrentHashMap
Map<String, String> safe = new ConcurrentHashMap<>();
```

### 坑 2：用可变对象做 HashMap 的 key

```java
// 错误做法
List<String> key = new ArrayList<>();
key.add("a");
map.put(key, "value");

key.add("b"); // 修改了 key，hashCode 变了！
map.get(key); // 返回 null，找不到了

// 正确做法：key 应该用不可变对象（String、Integer、枚举等）
```

### 坑 3：HashMap 的 size 不等于 capacity

```java
// capacity（容量）是数组长度，默认 16
// size 是实际存储的键值对数量
// loadFactor = 0.75
// threshold = capacity * loadFactor = 12
// 当 size > 12 时触发扩容，不是 size > 16

Map<String, Integer> map = new HashMap<>();
// 此时 capacity=16, size=0, threshold=12
for (int i = 0; i < 13; i++) {
    map.put("key" + i, i);
    // 当 i=12，size 变为 13，超过 threshold=12，触发扩容为 32
}
```

---

## 面试高频问题

### Q1：HashMap 的底层数据结构是什么？Java 7 和 Java 8 有什么区别？

**参考答案：**
- Java 7：数组 + 链表，链表用**头插法**（并发时可能形成环导致死循环）。
- Java 8：数组 + 链表 + 红黑树，链表改为**尾插法**，当链表长度 >= 8 且数组容量 >= 64 时，链表转为红黑树（提升最坏情况查找性能从 O(n) 到 O(log n)）。

### Q2：HashMap 的 put 流程是什么？

**参考答案（背下来）：**
1. 计算 `hash(key)` = `hashCode() ^ (hashCode() >>> 16)`（扰动函数）
2. 计算桶下标 `index = (n-1) & hash`
3. 桶为空：直接插入
4. 桶不为空：遍历，key 相同（hash 相等且 equals 为 true）则覆盖 value；不同则尾插链表或树插
5. 链表长度 >= 8 且容量 >= 64：树化
6. `size > threshold` 则 resize（扩容为 2 倍）

### Q3：HashMap 为什么线程不安全？

**参考答案：**
1. **JDK 7**：`transfer()` 方法在 resize 时使用头插法，两个线程同时 resize 可能形成循环链表，导致 `get()` 进入无限循环（CPU 飙升）。
2. **JDK 8**：改了尾插法解决死循环，但 `put()` 操作不是原子的：检查、创建节点、插入是三步，多线程下会出现数据覆盖丢失的问题（线程 A 和 B 同时写同一个桶的尾部，后者覆盖前者的 next 指针）。
3. **解决方案**：`ConcurrentHashMap`（推荐）、`Collections.synchronizedMap()`（性能差，锁整个对象）。

### Q4：为什么 HashMap 的容量必须是 2 的幂次方？

**参考答案：**
计算下标用 `(n-1) & hash` 替代 `hash % n`，这要求 n 是 2 的幂（此时 n-1 的二进制全是 1，位与操作等价于取模）。位运算比取模运算快很多。另外，扩容时 rehash 的计算也利用了 2 的幂：`(hash & oldCapacity) == 0` 直接判断新位置是原索引还是原索引 + oldCapacity，O(1) 计算无需重新计算 hash。

### Q5：HashMap 和 Hashtable、ConcurrentHashMap 的区别？

**参考答案：**

| | HashMap | Hashtable | ConcurrentHashMap |
|--|---------|-----------|-------------------|
| 线程安全 | 否 | 是（全表锁） | 是（桶级锁+CAS） |
| null key | 允许 1 个 | 不允许 | 不允许 |
| 性能 | 最高 | 最低 | 较高 |
| 继承 | AbstractMap | Dictionary | AbstractMap |
| 推荐 | 单线程 | 已过时 | 多线程 |

Hashtable 所有方法加了 `synchronized`，锁整个对象，并发度极低，已过时，不要用。ConcurrentHashMap 锁粒度到单个桶，并发写不同桶时互不阻塞。

### Q6：equals() 和 hashCode() 为什么必须同时重写？

**参考答案：**
HashMap 查找 key 分两步：先用 `hashCode()` 定位桶，再用 `equals()` 在桶内找。

- 只重写 equals：两个"相等"对象的 hashCode 可能不同（用的 Object 默认实现，基于内存地址），放到不同桶里，put 和 get 找不同的桶，get 永远返回 null。
- 只重写 hashCode：两个对象 hashCode 相同（进同一个桶），但 equals 为 false，认为是不同 key，HashSet 会存两份"相等"对象。

Java 规范要求：**equals 为 true 的对象 hashCode 必须相同**。实践中用 `Objects.hash(field1, field2, ...)` 和 IDE 自动生成确保一致。

### Q7：如何实现一个简单的 LRU 缓存？

**参考答案：**
用 `LinkedHashMap` 的 accessOrder 模式（按访问顺序排序）：

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    LRUCache(int capacity) {
        super(capacity, 0.75f, true); // true = accessOrder
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity; // 超出容量时移除最久未访问的
    }
}
```

`LinkedHashMap(initialCapacity, loadFactor, true)` 的第三个参数 `true` 表示按访问顺序（每次 get/put 把节点移到尾部），`false` 是按插入顺序。重写 `removeEldestEntry` 控制淘汰策略。
