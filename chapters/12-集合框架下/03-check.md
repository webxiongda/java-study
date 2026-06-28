# Chapter 12 集合框架下 - 自测与验收

> 模板见 `docs/superpowers/specs/2026-05-25-check-template.md`
> 覆盖率自检:`node scripts/check-coverage.mjs '^12-'`

---

### Q1 [L2·概念·章节内测] HashMap 底层数据结构 + Java7 vs Java8 改了什么

**考点**: HashMap 底层数据结构, 链表树化与反树化
**参考答案**:

**JDK 7**:
- 数组 + 链表
- **头插法**(并发 resize 时形成环 → `get()` 死循环 → CPU 100%)

**JDK 8**:
- 数组 + 链表 + **红黑树**
- **尾插法**(解决了死循环,但仍线程不安全)
- 树化条件:**链表长度 ≥ 8 且数组容量 ≥ 64**(只满足前者会先 resize 不会树化)
- 反树化:树节点 ≤ 6 时退化回链表(在 resize 时检查)

**为什么是 8?** 按泊松分布,一个桶里有 8 个元素的概率约 6×10⁻⁸,正常 hash 函数下几乎不会触发。**树化主要防恶意 DoS**(攻击者构造大量相同 hashCode 的 key,把 HashMap 退化成链表 O(n))。

**🔥追问**: 红黑树 vs AVL 树为什么 HashMap 选红黑?(答:插入/删除旋转次数少,适合频繁修改;查询略慢但能接受)
**关联**: interview-bank.md#hashmap-bottom-structure

---

### Q2 [L2·代码阅读·章节内测] 解释 HashMap.put 流程,扰动函数为什么用 `^ (h >>> 16)`

**考点**: HashMap 的 put 流程, 扰动函数, 桶下标计算
**参考答案**:

```java
// HashMap 源码(简化)
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

**put(k, v) 完整流程**:

1. **计算 hash**:`hashCode() ^ (hashCode() >>> 16)` — 扰动
2. **算桶下标**:`index = (n - 1) & hash` (n 必须 2 的幂,等价于 hash % n,但位运算更快)
3. table 为空 → `resize()` 初始化为 16
4. 桶为空 → 直接放新 Node
5. 桶非空:
   - key 相同(`hash 相等 && equals 为 true`) → **覆盖 value**
   - 桶头是树节点 → **树插入**
   - 是链表 → 遍历,找到则覆盖;否则 **尾插**;插入后链长 ≥ 8 且 cap ≥ 64 → `treeifyBin`
6. `size++`,若 `size > threshold` → `resize()` 翻倍

**为什么扰动 `h ^ (h >>> 16)`?**

`(n-1) & hash` 在 n 较小(如 16)时只取 hash 的低 4 位 — 高位完全不参与。`^ (h >>> 16)` 让高 16 位与低 16 位异或,**把高位的特征注入低位**,显著降低高位相同低位不同时的碰撞率。

**为什么用 `^` 不用 `+`?** XOR 是无进位、可逆、均匀分布的;`+` 有进位会产生位级偏置。

**🔥追问**:为什么负载因子默认 0.75?为什么初始容量是 16?

**参考答案**:
- 0.75 是泊松分布下空间/时间平衡的经验最优(< 0.75 浪费内存,> 0.75 碰撞激增)
- 16 = 2⁴,满足"n 必须 2 的幂"的位运算优化前提;**已知数据量时**应指定 `new HashMap<>(expectedSize / 0.75 + 1)` 避免扩容

**关联**: interview-bank.md#hashmap-bottom-structure

---

### Q3 [L3·Debug·面试高频] 找出 HashCodeEqualsDemo 中 BadKey 的灾难,讲清 equals/hashCode 契约

**考点**: equals() 和 hashCode() 契约, HashCodeEqualsDemo, BadKey, GoodKey, PersonKey
**参考答案**:

```java
// 02-demo.md 的 BadKey - 只重写 equals 不重写 hashCode
class BadKey {
    String name;
    @Override public boolean equals(Object o) {
        return o instanceof BadKey && ((BadKey) o).name.equals(this.name);
    }
    // 没重写 hashCode → 用 Object 默认的(基于对象内存地址)
}

Map<BadKey, String> map = new HashMap<>();
map.put(new BadKey("alice"), "v1");
System.out.println(map.get(new BadKey("alice")));  // null !!!
```

**问题根因(equals/hashCode 三契约)**:

1. `a.equals(b) == true` ⇒ `a.hashCode() == b.hashCode()` (强制)
2. `a.hashCode() == b.hashCode()` 不保证 equals 为 true (允许哈希碰撞)
3. `a.hashCode() != b.hashCode()` ⇒ `a.equals(b) == false` (反契约)

BadKey 违反契约 1 — 两个"alice"的 hashCode 来自地址,不同 → put 进桶 5, get 算到桶 12 → null。

**两种错误模式后果**:
- **只重 equals,不重 hashCode** → HashMap 找不到 key(本题)
- **只重 hashCode,不重 equals** → 同一个桶,但被认作不同对象,HashSet 存两份"相等"对象

**修复(GoodKey 写法)**:

```java
class GoodKey {
    String name;
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoodKey g)) return false;  // JDK 16+ pattern matching
        return name.equals(g.name);
    }
    @Override public int hashCode() {
        return Objects.hash(name);  // 多字段:Objects.hash(f1, f2, ...)
    }
}
```

**生产实践**:
- 用 IDE 自动生成 equals/hashCode,基于同一组字段
- 或用 Lombok `@EqualsAndHashCode(of = {"id"})`
- **key 必须用不可变对象** — 见 02-demo.md 的 PersonKey 反例(可变字段会让 hashCode 在 put 后改变,等于"丢失" key)

**🔥追问**: 字符串的 hashCode 算法?为什么用 31?(答:`h = 31 * h + c[i]`,31 是奇素数 + JIT 可优化为 `(h << 5) - h`)

**关联**: interview-bank.md#hashmap-bottom-structure

---

### Q4 [L2·对比·面试高频] HashMap vs Hashtable vs ConcurrentHashMap

**考点**: HashMap 线程不安全, ConcurrentHashMap (JDK 8 实现)
**参考答案**:

| 维度 | HashMap | Hashtable | ConcurrentHashMap |
|---|---|---|---|
| 线程安全 | ❌ | ✅ 全表 synchronized | ✅ 桶级 CAS + synchronized |
| null key/value | key 1 个,value 多个 | 都不允许 | 都不允许 |
| 性能(并发) | 高(单线程) | 极低(锁整个) | 高(锁单桶) |
| JDK 7 实现 | 数组+链表 | 数组+链表 + 全表锁 | **Segment 分段锁** |
| JDK 8 实现 | + 红黑树 | 同 7 | 取消 Segment,**桶头 CAS + synchronized** |
| 推荐 | 单线程 | 已过时,别用 | 多线程首选 |

**HashMap 为什么线程不安全(JDK 8)**?

```java
// HashMap.putVal 简化 (非原子)
if ((p = tab[i = (n - 1) & hash]) == null)
    tab[i] = newNode(hash, key, value, null);  // 检查 + 创建 + 插入 三步
```

并发场景下两个线程同时 put 同一个桶,**后写者会覆盖前写者的 next 指针** → 数据丢失。

**ConcurrentHashMap JDK 8 的精妙**:

1. **首节点为空**:`Unsafe.compareAndSwapObject` 直接 CAS 写入(无锁)
2. **首节点非空**:`synchronized(firstNode)` 锁桶头节点(锁粒度 = 单桶)
3. **扩容**:多线程协助迁移(`ForwardingNode` 占位),不阻塞读
4. **size 估算**:`baseCount` + `CounterCell[]` 分段统计(类似 LongAdder)

**坑**:`if (!map.containsKey(k)) map.put(k, v);` 不是原子!必须用 `putIfAbsent` 或 `computeIfAbsent`。

**🔥追问**: JDK 7 的 Segment 分段锁为什么 JDK 8 抛弃了?(答:Segment 是 ReentrantLock 重对象 + 锁的是一整段桶,粒度仍粗;桶级锁可以做到 N 个桶 N 把锁;此外 CAS 在首节点空时完全无锁)

**关联**: interview-bank.md#hashmap-thread-unsafe, interview-bank.md#chm-jdk8

---

### Q5 [L3·场景设计·面试高频] 用 LinkedHashMap 实现 LRU 缓存,并讨论生产怎么改

**考点**: LinkedHashMap (accessOrder), 缓存淘汰策略, ConcurrentHashMap 概念
**参考答案**:

**最小实现(20 行)**:

```java
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public LRUCache(int capacity) {
        // 第 3 参数 true = accessOrder: 每次 get/put 把节点挪到尾部
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;  // 超容量自动剔最久未访问的
    }
}
```

**关键设计点**:
- `LinkedHashMap` 在 HashMap 基础上用**双向链表**串起所有节点
- `accessOrder = true` 时,`get/put` 都会把当前节点移到链表尾(O(1))
- `removeEldestEntry` 是 put 后回调,返回 true 就移除链表头(最久未访问)

**生产升级路径**:

| 需求 | 方案 |
|---|---|
| 线程安全 | 用 `Caffeine.newBuilder().maximumSize(N).build()` (替代 Guava Cache) |
| 按时间过期 | `expireAfterWrite(Duration.ofMinutes(10))` |
| 分布式 | Redis `allkeys-lru` 策略 + key 设 TTL |
| 高并发读 + 低写 | 读用本地缓存(Caffeine) + 写穿透到 Redis(多级缓存) |
| 防缓存击穿 | `Caffeine` 的 `LoadingCache.get(k, loader)` 内部加锁 |

**面试 2 分钟讲法**:

> "LRU 最小实现继承 LinkedHashMap,构造传 accessOrder=true,重写 removeEldestEntry 返回 size > cap。生产不会这么做,因为不线程安全、不能按时间过期。我会用 Caffeine,它内部用类 W-TinyLFU 算法(命中率比 LRU 高),线程安全 + 异步刷新 + 容量、时间、引用三种过期。分布式场景挪到 Redis,maxmemory-policy 设 allkeys-lru,key 加 TTL 兜底。"

**🔥追问**:
- W-TinyLFU 比纯 LRU 好在哪?(答:抗扫描攻击 — 一次性 hot scan 不会污染缓存)
- 实现 LRU 用 `HashMap + 双向链表` vs 直接继承 `LinkedHashMap` 有什么取舍?(答:前者灵活可控、可加 stats;后者代码量极小但难定制)
- **LinkedHashMap vs TreeMap 怎么选?**(答:LinkedHashMap 维护插入序或访问序,O(1) get/put;TreeMap 是红黑树按 key 排序,O(log n),需要 key 有序遍历时用)
- **TreeMap 的 key 怎么排序?Comparable vs Comparator 怎么选?**

**Comparable vs Comparator(配合 02-demo.md 的 ComparatorDemo)**:

```java
// ComparatorDemo 中 Employee 实现 Comparable —— "自然顺序",侵入式
class Employee implements Comparable<Employee> {
    String name; int salary;
    @Override
    public int compareTo(Employee other) {
        return Integer.compare(this.salary, other.salary);  // 按薪资升序
    }
    @Override public String toString() { return name + "(" + salary + ")"; }
}

// 临时要换一种排序? 用 Comparator —— 非侵入,可多种
list.sort(Comparator.comparing(Employee::getName));               // 按名字
list.sort(Comparator.comparingInt(Employee::getSalary).reversed());// 薪资降序
TreeMap<Employee, String> map = new TreeMap<>(
    Comparator.comparing(Employee::getName));                    // 给 TreeMap 注入比较器
```

| 维度 | Comparable | Comparator |
|---|---|---|
| 接口位置 | `java.lang` | `java.util` |
| 方法 | `compareTo(T)` | `compare(T, T)` |
| 排序源 | 类自身的"自然顺序" | 外部定义,可多个 |
| 侵入性 | 改类源码 | 不改类 |
| 使用场景 | 类只会按一种方式排(如 Integer) | 同一对象多种排序需求 |

**返回值约定**:负数→this 排前;0→相等;正数→other 排前。**陷阱**:`a.salary - b.salary` 在 int 溢出时会返回错值,必须 `Integer.compare(a, b)`。

---

## 通过标准

- [ ] 能讲清 HashMap 数据结构 + 树化双条件(链长 ≥8 && cap ≥64)+ 反树化阈值 6
- [ ] 能默写 put 流程 + 扰动函数 + 为什么用 ^ 不用 +
- [ ] 能识别 BadKey 的灾难,讲清 equals/hashCode 三契约
- [ ] 能默写 HashMap / Hashtable / ConcurrentHashMap 三方对比 + CHM JDK8 实现
- [ ] 能现场写 LRU(LinkedHashMap 版),讲清生产用 Caffeine/Redis 的理由
- [ ] 能讲清 Comparable vs Comparator 的差异 + TreeMap 的两种构造方式
