# Chapter 12 - 集合框架下：项目任务

## 业务背景

你正在为一家电商公司开发后台系统，当前需求是：**商品库存管理工具**。

仓库管理员每天需要查看哪些商品库存充足、哪些即将告罄，并进行入库/出库操作。系统需要以商品为 key 存储库存数量，因此正确实现商品对象的 `equals` 和 `hashCode` 至关重要——否则同一商品会被当成两个不同商品处理，导致库存数据混乱。

---

## 任务说明

### 第一步：设计 Product 类

实现一个 `Product` 商品类，要求：

- 字段：`id`（String，商品唯一标识）、`name`（String，商品名称）、`price`（double，单价）
- 提供全参构造方法和 getter
- **按 `id` 实现 `equals()` 和 `hashCode()`**（两个 id 相同的商品视为同一商品，无论 name/price 是否不同）
- 重写 `toString()`，格式：`Product{id='P001', name='苹果', price=5.5}`

```java
public class Product {
    private String id;
    private String name;
    private double price;

    // 请补全：构造方法、getter、equals、hashCode、toString
}
```

### 第二步：实现 InventoryService 类

使用 `HashMap<Product, Integer>` 作为底层存储（key 是 Product，value 是库存数量），实现以下方法：

```java
public class InventoryService {

    private Map<Product, Integer> inventory = new HashMap<>();

    /**
     * 入库：增加指定商品的库存数量
     * 若商品不存在则新增；若已存在则在原数量基础上累加
     * @param product 商品
     * @param quantity 入库数量（必须 > 0，否则抛 IllegalArgumentException）
     */
    public void addStock(Product product, int quantity) { ... }

    /**
     * 出库：减少指定商品的库存数量
     * 若库存不足则抛出 IllegalStateException（含提示信息）
     * 若出库后数量为 0，从 Map 中移除该商品记录
     * @param product 商品
     * @param quantity 出库数量（必须 > 0）
     */
    public void reduceStock(Product product, int quantity) { ... }

    /**
     * 查询库存：返回指定商品的当前库存数量
     * 若商品不存在，返回 0
     */
    public int getStock(Product product) { ... }

    /**
     * 返回库存数量最多的前 N 个商品（按库存降序）
     * 若库存商品总数 < N，返回全部
     * @return List<Map.Entry<Product, Integer>>
     */
    public List<Map.Entry<Product, Integer>> getTopN(int n) { ... }

    /**
     * 打印当前全部库存（格式自定义，要求清晰可读）
     */
    public void printInventory() { ... }
}
```

### 第三步：编写 main 方法验证

```java
public class InventoryDemo {
    public static void main(String[] args) {
        InventoryService service = new InventoryService();

        Product apple  = new Product("P001", "苹果", 5.5);
        Product banana = new Product("P002", "香蕉", 3.0);
        Product orange = new Product("P003", "橙子", 8.0);
        Product mango  = new Product("P004", "芒果", 12.0);

        // 入库
        service.addStock(apple,  100);
        service.addStock(banana, 50);
        service.addStock(orange, 200);
        service.addStock(mango,  30);

        // 追加入库（同一商品，用新对象模拟）
        Product appleAgain = new Product("P001", "苹果", 5.5);
        service.addStock(appleAgain, 50);  // 苹果库存应变为 150

        // 出库
        service.reduceStock(banana, 20);   // 香蕉库存应变为 30

        // 打印当前库存
        service.printInventory();

        // Top 2 库存
        System.out.println("\n=== 库存最多的 Top 2 ===");
        service.getTopN(2).forEach(e ->
            System.out.println(e.getKey().getName() + ": " + e.getValue()));

        // 验证用新 id 相同的对象 getStock 也能找到
        System.out.println("\n用新对象查苹果库存: " + service.getStock(appleAgain));
    }
}
```

---

## 验收标准

1. **equals/hashCode 正确**：`new Product("P001", ...)` 和另一个 `new Product("P001", ...)` 在 HashMap 中被视为同一个 key，`addStock` 会累加而非新增条目。
2. **addStock 累加逻辑正确**：苹果两次入库（100 + 50），最终库存为 150，Map 中只有一条苹果记录。
3. **reduceStock 库存不足时抛异常**：尝试对库存 30 的香蕉出库 50，应抛出 `IllegalStateException`，信息含"库存不足"。
4. **getTopN 结果正确有序**：`getTopN(2)` 返回橙子(200)、苹果(150)，顺序不能错。
5. **边界处理完整**：`addStock` 传入负数量时抛 `IllegalArgumentException`；`getStock` 查询不存在的商品返回 0 而非 null。

---

## 常见坑

### 坑 1：equals 和 hashCode 用了不同字段

```java
// 错误示例：equals 比较 id，hashCode 却用了 name
@Override
public boolean equals(Object o) {
    return Objects.equals(this.id, ((Product) o).id);  // 按 id
}

@Override
public int hashCode() {
    return Objects.hash(name);  // 按 name ← 坑！
}
```

结果：两个 id 相同但 name 不同的 Product，equals 返回 true，但 hashCode 不同，HashMap 无法正确识别为同一 key。**equals 和 hashCode 必须基于完全相同的字段。**

---

### 坑 2：reduceStock 出库后忘记处理库存为 0 的情况

```java
// 错误：直接 put 新值，库存变为 0 的商品仍留在 Map 中
inventory.put(product, currentStock - quantity);
```

按需求，出库后数量为 0 应调用 `inventory.remove(product)` 清除记录，否则 `getTopN` 会返回库存为 0 的商品，影响业务逻辑。

---

### 坑 3：getTopN 用了 entrySet() 直接排序后修改原 Map

```java
// 危险写法：对 entrySet 排序的结果集不可靠，且可能抛 UnsupportedOperationException
List<Map.Entry<Product, Integer>> list = new ArrayList<>(inventory.entrySet());
list.sort(...);  // 正确，要先复制到新 List
```

`inventory.entrySet()` 返回的是视图，直接对其调用 sort 会抛异常。**必须先 `new ArrayList<>(inventory.entrySet())` 复制出来再排序。**
