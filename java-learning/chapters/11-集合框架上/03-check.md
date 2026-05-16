# 集合框架上 自测题

> 先独立作答，再看参考答案。

## 题目

### Q1（概念）：ArrayList 和 LinkedList 各自的时间复杂度是什么？什么场景下应该优先选 ArrayList？

### Q2（概念）：HashSet、LinkedHashSet、TreeSet 三者的区别是什么？如果需要存储不重复的学生姓名并按字母顺序遍历，应该用哪个？

### Q3（实操）：以下代码会抛出什么异常？如何修复？

```java
List<String> list = Arrays.asList("a", "b", "c");
list.add("d");
```

### Q4（实操）：以下代码的输出是什么？为什么？

```java
List<Integer> nums = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
for (String s : list) {  // 假设这里是 nums
    if (nums.get(nums.indexOf(s)) % 2 == 0) {
        nums.remove(nums.indexOf(s));
    }
}
```

实际题目代码：

```java
List<Integer> nums = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
for (Integer n : nums) {
    if (n % 2 == 0) {
        nums.remove(n);
    }
}
System.out.println(nums);
```

### Q5（项目应用）：在 Spring Boot 项目中，你需要统计一批用户请求日志中每个接口的访问次数，并返回访问次数前 3 的接口。请描述思路并写出核心代码。

---

## 参考答案

### A1：

**时间复杂度：**

| 操作 | ArrayList | LinkedList |
|------|-----------|------------|
| 随机访问 get(i) | O(1) | O(n) |
| 尾部添加 add() | 均摊 O(1) | O(1) |
| 头部/中间插入 add(i, e) | O(n) | O(n)（找节点也要 O(n)） |
| 头部删除 | O(n) | O(1) |
| 尾部删除 | O(1) | O(1) |

**优先选 ArrayList 的场景**：绝大多数情况都选 ArrayList，因为：
1. CPU 缓存对连续内存更友好，实际访问速度比理论值更快。
2. 大多数业务代码是读多写少，ArrayList 随机访问 O(1) 占优势。
3. LinkedList 每个节点要存两个额外指针，内存开销是 ArrayList 的 3 倍左右。

只有频繁在头部增删（不用索引访问）时才考虑 LinkedList，但此时 ArrayDeque 通常更优。

---

### A2：

| 特性 | HashSet | LinkedHashSet | TreeSet |
|------|---------|---------------|---------|
| 顺序 | 无序 | 插入顺序 | 自然排序/Comparator |
| 底层 | HashMap | LinkedHashMap | TreeMap（红黑树） |
| null | 允许 | 允许 | 不允许 |
| 性能 | O(1) | O(1) | O(log n) |

**存储不重复学生姓名并按字母顺序遍历**：选 **TreeSet**。TreeSet 底层红黑树自动维护排序，String 默认按字典序比较，遍历时直接按字母顺序输出，无需额外排序。

---

### A3：

**抛出 `UnsupportedOperationException`。**

`Arrays.asList()` 返回的是 `java.util.Arrays$ArrayList`（Arrays 的内部类），不是 `java.util.ArrayList`。它底层固定在原始数组上，大小不可变，调用 `add/remove` 会抛 `UnsupportedOperationException`。`set` 操作是支持的。

**修复方法：**

```java
// 方法 1：包一层 ArrayList
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
list.add("d"); // 正常

// 方法 2：Java 9+，但 List.of() 完全不可变
List<String> list = new ArrayList<>(List.of("a", "b", "c"));
list.add("d"); // 正常
```

---

### A4：

**输出：`[1, 3, 5]`（偶数 2、4 都没被删掉，只删掉了一些）**

实际上抛出 `ConcurrentModificationException`。

不，更准确的答案：这段代码**可能抛出 `ConcurrentModificationException`**，也可能跑完但结果不正确（取决于 JVM 版本）。

原因：在 for-each 循环中调用 `nums.remove(n)` 会修改 `modCount`，下次循环调用 `it.next()` 时 `expectedModCount != modCount`，抛出异常。

如果侥幸不抛异常（如只有一个偶数），结果也会不正确，因为删除元素后索引偏移。

**正确写法：**

```java
nums.removeIf(n -> n % 2 == 0);
System.out.println(nums); // [1, 3, 5]
```

---

### A5：

**思路：**
1. 遍历日志列表，用 `HashMap<String, Integer>` 统计每个接口的访问次数。
2. 将 entrySet 转为 List，按 value 降序排序。
3. 取前 3 条返回。

**核心代码：**

```java
public List<Map.Entry<String, Integer>> getTop3Endpoints(List<String> logs) {
    // 统计词频
    Map<String, Integer> countMap = new HashMap<>();
    for (String endpoint : logs) {
        countMap.merge(endpoint, 1, Integer::sum);
    }

    // 排序取前 3
    return countMap.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());
}
```

**或者用 PriorityQueue（大数据量时更节省内存）：**

```java
PriorityQueue<Map.Entry<String, Integer>> pq =
    new PriorityQueue<>(3, Map.Entry.comparingByValue(Comparator.reverseOrder()));
pq.addAll(countMap.entrySet());
List<Map.Entry<String, Integer>> top3 = new ArrayList<>();
for (int i = 0; i < 3 && !pq.isEmpty(); i++) {
    top3.add(pq.poll());
}
```
