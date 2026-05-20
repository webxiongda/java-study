# 集合框架上 项目任务

## 业务背景

你正在开发一个电商后台的**商品管理模块**。产品经理要求实现以下功能：
1. 维护一个商品库存，支持增删查改。
2. 对商品按价格或名称排序展示。
3. 统计每个分类下的商品数量。
4. 找出库存不足的商品（库存 < 10）。

这是一个不涉及数据库的纯内存版本，用集合框架模拟数据存储。

---

## 技术要求

- 使用 `ArrayList` 存储商品列表
- 使用 `HashMap` 统计分类数量
- 使用 `Comparator` 实现多字段排序
- 使用 `removeIf` 安全删除
- 使用 `stream()` 过滤低库存商品（或 for 循环，不强制用 Stream）

---

## 任务说明

### 第一步：创建 Product 类

```java
public class Product {
    private String id;
    private String name;
    private String category;
    private double price;
    private int stock;

    // 构造方法、getter、setter、toString
}
```

### 第二步：创建 ProductRepository 类，实现以下方法

| 方法签名 | 说明 |
|---------|------|
| `void add(Product p)` | 添加商品 |
| `boolean remove(String id)` | 按 id 删除商品 |
| `Product findById(String id)` | 按 id 查找 |
| `List<Product> findByCategory(String category)` | 按分类查找 |
| `List<Product> sortByPrice(boolean ascending)` | 按价格排序 |
| `List<Product> sortByName()` | 按名称字母排序 |
| `Map<String, Integer> countByCategory()` | 统计各分类商品数 |
| `List<Product> getLowStock(int threshold)` | 获取库存低于阈值的商品 |

### 第三步：编写 Main 类，初始化至少 8 个商品进行测试

初始数据建议（自由补充）：

| id | name | category | price | stock |
|----|------|----------|-------|-------|
| P001 | MacBook Pro | 电脑 | 15999.0 | 5 |
| P002 | iPhone 15 | 手机 | 7999.0 | 3 |
| P003 | AirPods Pro | 配件 | 1999.0 | 20 |
| P004 | iPad Mini | 平板 | 4999.0 | 8 |
| P005 | 机械键盘 | 配件 | 599.0 | 15 |
| P006 | 华为 Mate60 | 手机 | 6999.0 | 12 |
| P007 | 显示器 | 电脑 | 2999.0 | 6 |
| P008 | 充电宝 | 配件 | 199.0 | 2 |

---

## 验收标准

1. `add()` 能正确添加商品，`findById()` 能找到对应商品。
2. `remove()` 删除后 `findById()` 返回 null。
3. `sortByPrice(true)` 输出从低到高排列。
4. `countByCategory()` 输出 `{电脑=2, 手机=2, 配件=3, 平板=1}`。
5. `getLowStock(10)` 输出：MacBook Pro（5）、iPhone 15（3）、iPad Mini（8）、显示器（6）、充电宝（2）。
6. 代码中没有在 for-each 循环里直接调用 `list.remove()`。

---

## 提示

- `Comparator.comparingDouble(Product::getPrice)` 可以按 double 字段排序。
- 链式调用 `.thenComparing()` 可实现多字段排序。
- `removeIf(p -> p.getId().equals(id))` 比用 Iterator 删除更简洁。
- `countByCategory()` 可以用 `getOrDefault()` 或 `merge()` 统计。

---

## 常见坑

1. **remove(int index) vs remove(Object)**：在 `List<Product>` 里调用 `remove(product对象)` 需要 Product 正确实现 `equals()`，否则删不掉。推荐用 `removeIf`。
2. **排序副作用**：`list.sort()` 会直接修改原列表，如果不想影响原数据，先 `new ArrayList<>(list)` 复制一份再排序。
3. **返回值防御性复制**：`findByCategory` 返回的 List 如果直接暴露内部集合，调用方修改会影响原数据。可以返回 `Collections.unmodifiableList(result)` 或 `new ArrayList<>(result)`。
