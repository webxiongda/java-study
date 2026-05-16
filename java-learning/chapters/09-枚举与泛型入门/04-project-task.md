# Chapter 09 - 项目任务：泛型缓存容器 + 枚举状态机

## 业务背景

本次任务分两个独立模块：

1. **泛型缓存容器**：开发一个支持任意键值类型的 `Cache<K, V>` 类，供项目各模块缓存数据，避免重复查询。
2. **订单状态机**：用 `OrderStatus` 枚举实现一个订单状态流转场景，模拟真实电商业务流程。

---

## 模块一：泛型 Cache<K, V>

### 要求

实现 `Cache<K, V>` 类，支持以下操作：

```java
// 存入键值对
void put(K key, V value)

// 根据键获取值，不存在时返回 null
V get(K key)

// 删除键值对，返回被删除的值（不存在时返回 null）
V remove(K key)

// 判断是否包含某个键
boolean contains(K key)

// 返回当前缓存的条数
int size()

// 清空缓存
void clear()

// 返回所有缓存条目（只读快照，不允许修改原始数据）
Map<K, V> snapshot()
```

### 附加要求（选做）

- 支持最大容量限制（构造时传入 `maxSize`），超出容量时拒绝写入并抛出 `CacheFullException`（自定义 RuntimeException）
- `snapshot()` 返回的 Map 是副本，对副本的修改不影响缓存内容

### 测试场景

```java
Cache<String, Integer> wordCount = new Cache<>();
wordCount.put("apple", 3);
wordCount.put("banana", 5);
wordCount.put("cherry", 1);

System.out.println(wordCount.get("apple"));    // 3
System.out.println(wordCount.get("mango"));    // null
System.out.println(wordCount.contains("banana")); // true
System.out.println(wordCount.size());          // 3

wordCount.remove("banana");
System.out.println(wordCount.size());          // 2

Map<String, Integer> snap = wordCount.snapshot();
snap.put("extra", 99);  // 修改副本
System.out.println(wordCount.contains("extra")); // false（不影响原始缓存）
```

---

## 模块二：OrderStatus 枚举状态机

### 要求

定义 `OrderStatus` 枚举，包含以下状态：

```
CREATED（已创建）→ PAID（已付款）→ SHIPPED（已发货）→ DELIVERED（已签收）
                                                 ↘
CREATED 可以 → CANCELLED（已取消）
PAID    可以 → CANCELLED
```

枚举必须提供：

1. **`code`** 字段（整数状态码，如 1001-1005）和 **`description`** 字段（中文描述）
2. **`nextStatus()`** 方法：返回正常流程中的下一个状态（DELIVERED 和 CANCELLED 是终态，调用时抛出 `IllegalStateException`）
3. **`canTransitionTo(OrderStatus target)`** 方法：判断是否可以从当前状态流转到目标状态（根据上方流程图）
4. **`isFinal()`** 方法：判断是否是终态

### 测试场景

```java
OrderStatus status = OrderStatus.CREATED;
System.out.println(status.getDescription());  // 已创建
System.out.println(status.isFinal());         // false

// 正常流转
status = status.nextStatus();  // PAID
System.out.println(status);    // PAID

// 判断是否可以取消
System.out.println(status.canTransitionTo(OrderStatus.CANCELLED));  // true（已付款可取消）
System.out.println(status.canTransitionTo(OrderStatus.SHIPPED));    // true
System.out.println(status.canTransitionTo(OrderStatus.CREATED));    // false

// 终态操作
OrderStatus delivered = OrderStatus.DELIVERED;
System.out.println(delivered.isFinal());  // true
try {
    delivered.nextStatus();  // 抛出 IllegalStateException
} catch (IllegalStateException e) {
    System.out.println("预期异常：" + e.getMessage());
}
```

---

## 参考结构

```
src/
└── com/
    └── example/
        ├── cache/
        │   ├── Cache.java
        │   └── CacheFullException.java（选做）
        ├── order/
        │   └── OrderStatus.java
        └── Main.java
```

---

## 验收标准

1. **Cache 类型安全**：`Cache<String, Integer>` 不能 `put(String, String)`，编译期就报错，不允许出现 `@SuppressWarnings("unchecked")` 掩盖类型警告（除非 snapshot 内部实现确实需要）。
2. **snapshot 隔离性**：对 `snapshot()` 返回的 Map 进行增删改，不影响 Cache 内部数据，通过上方测试场景验证。
3. **枚举状态流转正确**：`canTransitionTo` 严格按照题目中的状态图实现，所有合法和非法流转路径通过测试场景验证。
4. **终态保护**：对 DELIVERED 和 CANCELLED 调用 `nextStatus()` 必须抛出 `IllegalStateException`，且错误信息明确指出当前是终态（如 `"DELIVERED 是终态，无法继续流转"`）。

---

## 常见坑

**坑 1：snapshot() 直接返回内部 Map 引用，导致外部可修改缓存**

```java
// 错误：直接暴露内部引用
public Map<K, V> snapshot() {
    return store;  // 调用方 snap.put("k", v) 会直接修改 store！
}

// 正确：返回副本
public Map<K, V> snapshot() {
    return new HashMap<>(store);  // 新的 HashMap，修改不影响原始数据
}
```

**坑 2：canTransitionTo 没有覆盖所有状态组合**

写 `canTransitionTo` 时容易只考虑正常流程，忘记处理"非法"路径。建议用 switch 或 Map 明确列出每个状态的合法目标集合，而不是用 `ordinal()` 比大小（"目标序号比当前大就行"的逻辑对 CANCELLED 不适用）：

```java
// 错误：用序号判断，SHIPPED -> CANCELLED 会被错误拒绝（CANCELLED.ordinal < SHIPPED.ordinal）
public boolean canTransitionTo(OrderStatus target) {
    return target.ordinal() > this.ordinal();
}

// 正确：明确列出每个状态的合法后继
public boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
        case CREATED  -> target == PAID || target == CANCELLED;
        case PAID     -> target == SHIPPED || target == CANCELLED;
        case SHIPPED  -> target == DELIVERED;
        default       -> false;  // DELIVERED、CANCELLED 是终态
    };
}
```
