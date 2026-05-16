# Chapter 05 - 面向对象入门 | Demo 演示

---

## Demo 1：完整的 BankAccount 类

> 目标：综合演示封装（private + getter/setter）、构造器、实例方法、静态成员的协同使用。

```java
public class BankAccount {

    // ===== 静态字段：所有账户共享，记录总交易次数 =====
    private static int totalTransactionCount = 0;

    // ===== 实例字段（private 封装） =====
    private final String accountId;   // final：账户 ID 一旦设定不可更改
    private String ownerName;
    private double balance;
    private int personalTransactionCount;  // 当前账户的交易次数

    // ===== 构造器 =====
    public BankAccount(String accountId, String ownerName, double initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("初始余额不能为负数");
        }
        this.accountId = accountId;
        this.ownerName = ownerName;
        this.balance = initialBalance;
        this.personalTransactionCount = 0;
    }

    // 简化构造器：初始余额默认为 0
    public BankAccount(String accountId, String ownerName) {
        this(accountId, ownerName, 0.0);
    }

    // ===== Getter（只读访问） =====
    public String getAccountId() { return accountId; }
    public String getOwnerName() { return ownerName; }
    public double getBalance() { return balance; }
    public int getPersonalTransactionCount() { return personalTransactionCount; }

    // ===== Setter（只开放 ownerName 的修改，余额不允许直接设置） =====
    public void setOwnerName(String ownerName) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("账户持有人姓名不能为空");
        }
        this.ownerName = ownerName.trim();
    }

    // ===== 业务方法 =====

    /**
     * 存款
     * @param amount 存款金额，必须 > 0
     */
    public void deposit(double amount) {
        if (amount <= 0) {
            System.out.println("存款失败：存款金额必须大于 0");
            return;
        }
        balance += amount;
        personalTransactionCount++;
        totalTransactionCount++;   // 更新全局交易计数
        System.out.printf("[%s] 存款 %.2f 元，当前余额：%.2f 元%n",
                          accountId, amount, balance);
    }

    /**
     * 取款
     * @param amount 取款金额，必须 > 0 且 <= 余额
     * @return 是否取款成功
     */
    public boolean withdraw(double amount) {
        if (amount <= 0) {
            System.out.println("取款失败：取款金额必须大于 0");
            return false;
        }
        if (amount > balance) {
            System.out.printf("[%s] 取款失败：余额不足（余额：%.2f，申请：%.2f）%n",
                              accountId, balance, amount);
            return false;
        }
        balance -= amount;
        personalTransactionCount++;
        totalTransactionCount++;
        System.out.printf("[%s] 取款 %.2f 元，当前余额：%.2f 元%n",
                          accountId, amount, balance);
        return true;
    }

    /**
     * 转账到另一个账户
     */
    public boolean transfer(BankAccount target, double amount) {
        if (this.withdraw(amount)) {
            target.deposit(amount);
            System.out.printf("[转账] %s → %s，金额：%.2f 元%n",
                              this.accountId, target.accountId, amount);
            return true;
        }
        return false;
    }

    // ===== 静态方法：访问类级别信息 =====
    public static int getTotalTransactionCount() {
        return totalTransactionCount;
    }

    // ===== toString：打印账户摘要 =====
    @Override
    public String toString() {
        return String.format("账户[%s] 持有人：%s，余额：%.2f 元，本账户交易次数：%d",
                             accountId, ownerName, balance, personalTransactionCount);
    }

    // ===== 主方法：演示 =====
    public static void main(String[] args) {
        System.out.println("===== BankAccount 演示 =====\n");

        // 创建账户
        BankAccount alice = new BankAccount("ACC001", "Alice", 1000.0);
        BankAccount bob = new BankAccount("ACC002", "Bob");  // 初始余额 0

        System.out.println("初始状态：");
        System.out.println(alice);
        System.out.println(bob);
        System.out.println();

        // 存款
        bob.deposit(500.0);
        System.out.println();

        // 取款
        alice.withdraw(200.0);
        alice.withdraw(2000.0);  // 余额不足
        System.out.println();

        // 转账
        alice.transfer(bob, 300.0);
        System.out.println();

        // 最终状态
        System.out.println("最终状态：");
        System.out.println(alice);
        System.out.println(bob);
        System.out.println();

        // 查看全局交易次数（通过类名访问 static 方法）
        System.out.println("全系统总交易次数：" + BankAccount.getTotalTransactionCount());

        // 验证封装：无法直接修改 balance
        // alice.balance = 999999;  // 编译错误！balance 是 private 的
    }
}
```

**运行输出：**
```
===== BankAccount 演示 =====

初始状态：
账户[ACC001] 持有人：Alice，余额：1000.00 元，本账户交易次数：0
账户[ACC002] 持有人：Bob，余额：0.00 元，本账户交易次数：0

[ACC002] 存款 500.00 元，当前余额：500.00 元

[ACC001] 取款 200.00 元，当前余额：800.00 元
[ACC001] 取款失败：余额不足（余额：800.00，申请：2000.00）

[ACC001] 取款 300.00 元，当前余额：500.00 元
[ACC002] 存款 300.00 元，当前余额：800.00 元
[转账] ACC001 → ACC002，金额：300.00 元

最终状态：
账户[ACC001] 持有人：Alice，余额：500.00 元，本账户交易次数：2
账户[ACC002] 持有人：Bob，余额：800.00 元，本账户交易次数：2

全系统总交易次数：4
```

---

## Demo 2：构造器重载（Person 类）

> 目标：演示无参、单参、全参构造器以及用 `this()` 在构造器间互相调用，避免重复代码。

```java
public class Person {

    private String name;
    private int age;
    private String email;
    private String city;

    // ===== 全参构造器（最完整，其他构造器最终都调用这里） =====
    public Person(String name, int age, String email, String city) {
        this.name = name;
        this.age = age > 0 ? age : 0;   // 年龄不能为负
        this.email = email != null ? email : "";
        this.city = city != null ? city : "未知";
        System.out.println("【全参构造器】创建 Person: " + this.name);
    }

    // ===== 三参构造器：city 使用默认值 =====
    public Person(String name, int age, String email) {
        this(name, age, email, "未知");  // 委托给全参构造器
        System.out.println("【三参构造器】设置完毕");
    }

    // ===== 双参构造器：email 和 city 使用默认值 =====
    public Person(String name, int age) {
        this(name, age, "");  // 委托给三参构造器
        System.out.println("【双参构造器】设置完毕");
    }

    // ===== 单参构造器：只设置姓名，其余用默认值 =====
    public Person(String name) {
        this(name, 0);  // 委托给双参构造器
        System.out.println("【单参构造器】设置完毕");
    }

    // ===== 无参构造器：所有字段使用默认值 =====
    public Person() {
        this("匿名用户");  // 委托给单参构造器
        System.out.println("【无参构造器】设置完毕");
    }

    // ===== Getters =====
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getEmail() { return email; }
    public String getCity() { return city; }

    // ===== Setters with validation =====
    public void setAge(int age) {
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("年龄不合法：" + age);
        }
        this.age = age;
    }

    @Override
    public String toString() {
        return String.format("Person{name='%s', age=%d, email='%s', city='%s'}",
                             name, age, email, city);
    }

    public static void main(String[] args) {
        System.out.println("=== 无参构造器 ===");
        Person p1 = new Person();
        System.out.println(p1);

        System.out.println("\n=== 单参构造器 ===");
        Person p2 = new Person("Alice");
        System.out.println(p2);

        System.out.println("\n=== 全参构造器 ===");
        Person p3 = new Person("Bob", 25, "bob@example.com", "Shanghai");
        System.out.println(p3);
    }
}
```

**运行输出（注意构造器调用链的顺序）：**
```
=== 无参构造器 ===
【全参构造器】创建 Person: 匿名用户
【三参构造器】设置完毕
【双参构造器】设置完毕
【单参构造器】设置完毕
【无参构造器】设置完毕
Person{name='匿名用户', age=0, email='', city='未知'}

=== 单参构造器 ===
【全参构造器】创建 Person: Alice
【三参构造器】设置完毕
【双参构造器】设置完毕
【单参构造器】设置完毕
Person{name='Alice', age=0, email='', city='未知'}

=== 全参构造器 ===
【全参构造器】创建 Person: Bob
Person{name='Bob', age=25, email='bob@example.com', city='Shanghai'}
```

**关键观察：** `this()` 的调用是链式的，最终都收敛到全参构造器。这种模式叫做**构造器委托**，确保初始化逻辑只在一处维护。

---

## Demo 3：static 代码块和实例代码块的执行顺序

> 目标：通过打印语句，直观观察类加载时各代码块的执行顺序。

```java
public class InitOrderDemo {

    // ===== 静态字段 =====
    private static String staticField = initStaticField();

    private static String initStaticField() {
        System.out.println("① 静态字段初始化");
        return "static_value";
    }

    // ===== 静态代码块：类加载时执行，只执行一次 =====
    static {
        System.out.println("② 静态代码块执行（staticField = " + staticField + "）");
    }

    // ===== 实例字段 =====
    private String instanceField = initInstanceField();

    private String initInstanceField() {
        System.out.println("③ 实例字段初始化");
        return "instance_value";
    }

    // ===== 实例代码块：每次 new 对象时执行，在构造器之前 =====
    {
        System.out.println("④ 实例代码块执行");
    }

    // ===== 构造器：每次 new 对象时执行，在实例代码块之后 =====
    public InitOrderDemo() {
        System.out.println("⑤ 无参构造器执行");
    }

    public InitOrderDemo(String tag) {
        System.out.println("⑤ 有参构造器执行，tag = " + tag);
    }

    public static void main(String[] args) {
        System.out.println("===== 第一次创建对象 =====");
        InitOrderDemo obj1 = new InitOrderDemo();

        System.out.println("\n===== 第二次创建对象（静态块不再重复执行）=====");
        InitOrderDemo obj2 = new InitOrderDemo("second");

        System.out.println("\n===== 总结 =====");
        System.out.println("静态代码块只在类第一次加载时执行一次");
        System.out.println("实例代码块 + 构造器在每次 new 对象时都执行");
    }
}
```

**运行输出：**
```
===== 第一次创建对象 =====
① 静态字段初始化
② 静态代码块执行（staticField = static_value）
③ 实例字段初始化
④ 实例代码块执行
⑤ 无参构造器执行

===== 第二次创建对象（静态块不再重复执行）=====
③ 实例字段初始化
④ 实例代码块执行
⑤ 有参构造器执行，tag = second

===== 总结 =====
静态代码块只在类第一次加载时执行一次
实例代码块 + 构造器在每次 new 对象时都执行
```

**执行顺序总结：**

```
首次加载类：
静态字段初始化 → 静态代码块

每次 new 对象：
实例字段初始化 → 实例代码块 → 构造器
```

**static 代码块的常见用途：**
- 加载配置文件或数据库驱动（`Class.forName("com.mysql.jdbc.Driver")`）
- 初始化复杂的静态数据结构
- 注册工厂或策略到全局 Map 中
