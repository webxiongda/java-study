# 集合框架上 理论文档

## 核心概念

### 为什么需要集合框架？

数组的长度固定，使用不便。Java 集合框架（Java Collections Framework）提供了一套统一的、可复用的数据结构接口与实现，解决了数组的局限性，支持动态增删、查找、排序等操作。

集合框架的顶层接口是 `java.util.Collection`（单列集合）和 `java.util.Map`（键值对集合），本章聚焦 Collection 体系。

---

### Collection 体系总览

```
Collection
├── List（有序、可重复）
│   ├── ArrayList
│   ├── LinkedList
│   └── Vector（已过时）
├── Set（无序、不可重复）
│   ├── HashSet
│   ├── LinkedHashSet
│   └── TreeSet
└── Queue（队列）
    ├── LinkedList
    ├── ArrayDeque
    └── PriorityQueue
```

---

### List 接口

List 是有序集合，允许重复元素，支持通过索引访问。

**ArrayList**：底层是动态数组，随机访问快（O(1)），中间插入/删除慢（O(n)，需移动元素）。

```java
List<String> list = new ArrayList<>();
list.add("Java");
list.add("Spring");
list.add("MySQL");
list.add(1, "Python"); // 在索引 1 处插入
System.out.println(list.get(0)); // Java
list.remove("MySQL");
System.out.println(list.size()); // 3
```

**LinkedList**：底层是双向链表，头尾插入/删除快（O(1)），随机访问慢（O(n)）。LinkedList 同时实现了 `Deque` 接口，可当双端队列或栈使用。

```java
LinkedList<String> linked = new LinkedList<>();
linked.addFirst("头部");
linked.addLast("尾部");
linked.add("中间");
System.out.println(linked.pollFirst()); // 头部
```

**ArrayList vs LinkedList 对比**

| 特性 | ArrayList | LinkedList |
|------|-----------|------------|
| 底层结构 | 动态数组 | 双向链表 |
| 随机访问 | O(1) | O(n) |
| 头部插入 | O(n) | O(1) |
| 尾部插入 | 均摊 O(1) | O(1) |
| 内存占用 | 连续内存，更省 | 每个节点有 prev/next 指针，更费内存 |
| 推荐场景 | 读多写少，随机访问多 | 频繁头尾增删 |

---

### Set 接口

Set 不允许重复元素（通过 `equals()` 判断），不同实现类有不同的排序特性。

**HashSet**：底层是 HashMap，不保证顺序，添加/查找平均 O(1)。

```java
Set<String> set = new HashSet<>();
set.add("banana");
set.add("apple");
set.add("banana"); // 重复，不会加入
System.out.println(set.size()); // 2
System.out.println(set.contains("apple")); // true
```

**LinkedHashSet**：继承 HashSet，维护插入顺序（用双向链表记录），遍历时按插入顺序输出。

```java
Set<String> linked = new LinkedHashSet<>();
linked.add("C");
linked.add("A");
linked.add("B");
System.out.println(linked); // [C, A, B] 保持插入顺序
```

**TreeSet**：底层是红黑树，元素自动排序（自然排序或自定义 Comparator），添加/查找 O(log n)。

```java
Set<Integer> tree = new TreeSet<>();
tree.add(5);
tree.add(1);
tree.add(3);
System.out.println(tree); // [1, 3, 5] 自动升序
```

**三种 Set 对比**

| 特性 | HashSet | LinkedHashSet | TreeSet |
|------|---------|---------------|---------|
| 底层 | HashMap | LinkedHashMap | 红黑树 |
| 顺序 | 无序 | 插入顺序 | 排序 |
| null 值 | 允许 1 个 | 允许 1 个 | 不允许（比较会 NPE） |
| 性能 | 最快 | 稍慢 | O(log n) |

---

### Queue 接口

Queue 是队列，遵循 FIFO（先进先出）原则。常用方法：

```java
Queue<String> queue = new LinkedList<>();
queue.offer("first");   // 入队（推荐，失败返回 false）
queue.offer("second");
queue.poll();           // 出队（队首），返回 "first"
queue.peek();           // 查看队首，不移除
```

`ArrayDeque` 是双端队列的推荐实现（比 `LinkedList` 性能更好，无锁）：

```java
Deque<Integer> deque = new ArrayDeque<>();
deque.push(1); // 压栈（头部）
deque.push(2);
deque.pop();   // 出栈，返回 2
```

---

### HashMap 基本用法

Map 是键值对集合，键唯一，值可重复。

```java
Map<String, Integer> scores = new HashMap<>();
scores.put("Alice", 90);
scores.put("Bob", 85);
scores.put("Alice", 95); // 覆盖原来的值

System.out.println(scores.get("Alice"));          // 95
System.out.println(scores.getOrDefault("Tom", 0)); // 0（不存在时返回默认值）
System.out.println(scores.containsKey("Bob"));    // true

// 遍历 Map
for (Map.Entry<String, Integer> entry : scores.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}

// Java 8+ forEach
scores.forEach((k, v) -> System.out.println(k + ": " + v));
```

---

### Iterator 遍历

Iterator 是集合统一的遍历接口，支持在遍历时安全删除元素。

```java
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String val = it.next();
    if ("b".equals(val)) {
        it.remove(); // 安全删除，不会 ConcurrentModificationException
    }
}
System.out.println(list); // [a, c, d]
```

增强 for 循环（for-each）底层也是 Iterator，但不能在循环中调用 `list.remove()`，否则会抛 `ConcurrentModificationException`。

---

### Collections 工具类

`java.util.Collections` 提供了大量静态工具方法：

```java
List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9));

Collections.sort(nums);           // 升序排序 → [1, 1, 3, 4, 5, 9]
Collections.reverse(nums);        // 反转 → [9, 5, 4, 3, 1, 1]
Collections.shuffle(nums);        // 随机打乱
int max = Collections.max(nums);  // 最大值
int min = Collections.min(nums);  // 最小值
Collections.frequency(nums, 1);   // 元素出现次数
Collections.unmodifiableList(nums); // 返回不可修改的视图

List<String> synced = Collections.synchronizedList(new ArrayList<>()); // 线程安全包装
```

---

## 使用场景

- **ArrayList**：绝大多数场景的默认选择，读多写少。
- **LinkedList**：需要频繁在头部插入删除，或作为队列/栈使用。
- **HashSet**：快速判断元素是否存在，去重。
- **LinkedHashSet**：需要去重且保持插入顺序（如保留用户操作历史中的唯一元素）。
- **TreeSet**：需要排序的不重复集合（如排行榜）。
- **HashMap**：键值对存储，最通用的 Map。
- **Queue/Deque**：任务队列、BFS 算法、undo/redo 功能。

---

## 工作原理

**ArrayList 扩容机制**：初始容量默认 10，满了之后扩容为原来的 1.5 倍（`newCapacity = oldCapacity + (oldCapacity >> 1)`），底层调用 `Arrays.copyOf()` 将数据拷贝到新数组。

**HashSet 去重机制**：底层调用 `HashMap.put(element, PRESENT)`，利用 HashMap 的 key 唯一性实现去重。判断重复需要 `hashCode()` 和 `equals()` 两者一致。

**TreeSet 排序机制**：底层是红黑树（`TreeMap`），插入时自动比较，使用元素自然排序（实现 `Comparable`）或构造时传入 `Comparator`。

---

## 常见坑与易错点

### 坑 1：for-each 循环中删除元素

```java
// 错误做法 - 抛出 ConcurrentModificationException
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
for (String s : list) {
    if ("b".equals(s)) {
        list.remove(s); // 危险！
    }
}

// 正确做法 1 - 使用 Iterator
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if ("b".equals(it.next())) {
        it.remove();
    }
}

// 正确做法 2 - Java 8+
list.removeIf(s -> "b".equals(s));
```

### 坑 2：HashSet 存自定义对象时未重写 hashCode/equals

```java
// 错误做法
class Student {
    String name;
    Student(String name) { this.name = name; }
    // 没有重写 hashCode 和 equals
}

Set<Student> set = new HashSet<>();
set.add(new Student("Alice"));
set.add(new Student("Alice")); // 两个不同对象，HashSet 认为不重复！
System.out.println(set.size()); // 2（预期是 1）

// 正确做法：在 Student 中重写 hashCode() 和 equals()
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Student)) return false;
    Student s = (Student) o;
    return Objects.equals(name, s.name);
}

@Override
public int hashCode() {
    return Objects.hash(name);
}
```

### 坑 3：Arrays.asList() 返回的 List 不支持增删

```java
// 错误做法
List<String> list = Arrays.asList("a", "b", "c");
list.add("d"); // 抛出 UnsupportedOperationException！

// 正确做法：包一层 ArrayList
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
list.add("d"); // 正常
```

### 坑 4：TreeSet 存自定义对象时未实现 Comparable

```java
// 错误做法 - 抛 ClassCastException
Set<Student> treeSet = new TreeSet<>();
treeSet.add(new Student("Alice")); // 如果 Student 没有实现 Comparable

// 正确做法：实现 Comparable 或传入 Comparator
Set<Student> treeSet = new TreeSet<>(Comparator.comparing(s -> s.name));
```

---

## 面试高频问题

### Q1：ArrayList 和 LinkedList 的区别是什么？什么时候选哪个？

**参考答案**：
- ArrayList 底层是 Object[] 动态数组，支持随机访问 O(1)，但插入/删除需要移动元素 O(n)；LinkedList 底层是双向链表，头尾增删 O(1)，但随机访问 O(n)。
- 实际开发中 **绝大多数情况选 ArrayList**，因为 CPU 缓存对连续内存友好，LinkedList 的每个节点要存 prev/next 指针，内存开销大；LinkedList 中间插入也不快（找到中间节点还是 O(n)）。
- LinkedList 适合：频繁在头部插入/删除，或当作队列/栈使用（此时 ArrayDeque 更优）。

### Q2：HashSet 是如何保证元素不重复的？

**参考答案**：
HashSet 底层是 HashMap，put 元素时实际是 `map.put(e, PRESENT)`。HashMap 判断 key 是否重复的逻辑是：先比较 `hashCode()`，hash 相同再用 `equals()` 比较。两者都相同才认为是同一个 key，覆盖旧值。所以 HashSet 要求存入的自定义对象必须正确重写 `hashCode()` 和 `equals()`，否则"相等"的对象会被存多份。

### Q3：List.of()、Arrays.asList()、new ArrayList() 有什么区别？

**参考答案**：
- `List.of()`（Java 9+）：返回完全不可变列表，不允许增删改，也不允许包含 null。
- `Arrays.asList()`：返回固定大小列表（底层是数组），可以 set 修改元素，但不能 add/remove（抛 UnsupportedOperationException），允许 null。
- `new ArrayList<>()`：完整可变列表，支持增删改查，这是通用选择。

### Q4：Collections.sort() 和 List.sort() 有什么区别？

**参考答案**：
两者底层都调用 `Arrays.sort()`，使用 TimSort 算法（时间复杂度 O(n log n)，稳定排序）。`List.sort()` 是 Java 8 加入的实例方法，略简洁：`list.sort(Comparator.naturalOrder())`。`Collections.sort()` 是工具类静态方法。实际开发推荐用 `list.sort()` 或 Stream API 的 `sorted()`。

### Q5：Iterator 的 fail-fast 机制是什么？

**参考答案**：
Java 集合（ArrayList、HashMap 等）内部维护一个 `modCount` 字段，记录结构性修改次数。Iterator 创建时记录 `expectedModCount = modCount`，每次调用 `next()` 时检查两者是否一致，不一致则立即抛出 `ConcurrentModificationException`。这是一种"快速失败"机制，目的是及早暴露并发修改问题，而不是让程序在错误状态下继续运行。注意：这不是线程安全保证，多线程场景应使用 `CopyOnWriteArrayList` 或显式加锁。

### Q6：如何选择合适的集合？

**参考答案**：
1. 是否需要键值对？→ Map（HashMap / TreeMap / LinkedHashMap）
2. 是否需要去重？→ Set（HashSet / TreeSet / LinkedHashSet）
3. 需要顺序访问、允许重复？→ List（ArrayList 默认，需频繁头尾操作用 LinkedList 或 ArrayDeque）
4. 需要排序？→ TreeSet / TreeMap，或 list.sort()
5. 需要线程安全？→ ConcurrentHashMap / CopyOnWriteArrayList，避免用 Vector/Hashtable（已过时）
