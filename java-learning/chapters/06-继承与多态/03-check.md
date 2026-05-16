# 继承与多态 自测题

> 先独立作答，再看参考答案。

## 题目

### Q1（概念）：下面代码的输出是什么？为什么？

```java
class A {
    public String name() { return "A"; }
    public static String staticName() { return "A-static"; }
}

class B extends A {
    @Override
    public String name() { return "B"; }
    public static String staticName() { return "B-static"; }
}

public class Test {
    public static void main(String[] args) {
        A obj = new B();
        System.out.println(obj.name());
        System.out.println(obj.staticName());
    }
}
```

### Q2（概念）：抽象类可以有构造器吗？可以被实例化吗？下面代码有什么问题？

```java
public abstract class Vehicle {
    protected int speed;

    public Vehicle(int speed) {
        this.speed = speed;
    }

    public abstract void move();
}

// 使用方：
Vehicle v = new Vehicle(100);
v.move();
```

### Q3（实操）：以下代码能编译通过吗？如果不能，指出所有错误并修正。

```java
public interface Printable {
    void print();
    int getPageCount();
    default void preview() {
        System.out.println("Preview: " + getPageCount() + " pages");
    }
}

public class Document implements Printable {
    private String content;
    private int pages;

    public Document(String content, int pages) {
        this.content = content;
        this.pages = pages;
    }

    @Override
    public void print() {
        System.out.println("Printing: " + content);
    }
    
    // 问题：缺少对 getPageCount() 的实现
}
```

### Q4（实操）：写一个方法 `processShapes(List<Shape> shapes)`，接受一个 Shape 列表，对每个 Shape 调用 `area()` 方法打印面积，如果是 Circle 还要额外打印半径，如果是 Rectangle 还要打印宽和高。Shape、Circle、Rectangle 的类结构自己定义。

### Q5（项目应用）：你正在设计一个电商平台的支付模块。平台需要支持支付宝、微信支付、银行卡三种支付方式。每种支付方式都需要：验证支付信息（`validate()`）、发起支付（`pay(double amount)`）、查询支付状态（`queryStatus(String orderId)`）。此外，支付宝和微信支付都支持退款（`refund(String orderId, double amount)`），但银行卡不支持退款。

请设计这个支付模块的类/接口结构，写出关键代码（不需要实现真实的支付逻辑，用打印语句模拟即可），并说明你的设计理由。

---

## 参考答案

### A1：

输出：
```
B
A-static
```

**原因**：
- `obj.name()` 是实例方法，发生**运行时多态**（动态分派）。虽然引用类型是 `A`，但对象实际类型是 `B`，JVM 通过虚方法表找到 `B.name()`，输出 `"B"`
- `obj.staticName()` 是静态方法，**不参与多态**。静态方法属于类，按引用类型（`A`）调用，输出 `"A-static"`

关键结论：静态方法的"重写"实际上是**隐藏（hiding）**，不是真正的重写，不会触发动态分派。

### A2：

抽象类**可以有构造器**，但**不能直接被实例化**。

代码问题：`Vehicle v = new Vehicle(100)` 会编译错误，因为 `Vehicle` 是抽象类，不能直接 `new`。

抽象类的构造器作用是：当子类实例化时，通过 `super()` 调用，初始化父类定义的字段。

正确用法：
```java
public class Car extends Vehicle {
    private String model;

    public Car(int speed, String model) {
        super(speed);  // 调用抽象父类的构造器
        this.model = model;
    }

    @Override
    public void move() {
        System.out.println(model + " moves at " + speed + " km/h");
    }
}

// 正确实例化
Vehicle v = new Car(100, "Tesla");  // 向上转型
v.move();
```

### A3：

代码**不能编译通过**。`Document` 实现了 `Printable` 接口，但没有实现接口中的 `getPageCount()` 方法，编译器报错：`Document is not abstract and does not override abstract method getPageCount() in Printable`。

修正方案：

```java
public class Document implements Printable {
    private String content;
    private int pages;

    public Document(String content, int pages) {
        this.content = content;
        this.pages = pages;
    }

    @Override
    public void print() {
        System.out.println("Printing: " + content);
    }

    @Override
    public int getPageCount() {   // 补充缺失的实现
        return pages;
    }
    
    // preview() 不需要重写，使用接口的 default 实现即可
}
```

### A4：

```java
import java.util.List;

abstract class Shape {
    public abstract double area();
}

class Circle extends Shape {
    private double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    public double getRadius() { return radius; }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }
}

class Rectangle extends Shape {
    private double width, height;

    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }

    @Override
    public double area() {
        return width * height;
    }
}

public class ShapeProcessor {
    public static void processShapes(List<Shape> shapes) {
        for (Shape shape : shapes) {
            System.out.printf("Area: %.2f", shape.area());

            // Pattern matching (Java 16+)
            if (shape instanceof Circle c) {
                System.out.printf(", Radius: %.2f", c.getRadius());
            } else if (shape instanceof Rectangle r) {
                System.out.printf(", Width: %.2f, Height: %.2f", r.getWidth(), r.getHeight());
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        List<Shape> shapes = List.of(
            new Circle(5.0),
            new Rectangle(4.0, 6.0),
            new Circle(3.0)
        );
        processShapes(shapes);
    }
}
```

### A5：

**设计思路**：

1. 定义 `PaymentService` 接口，包含所有支付方式的共同方法
2. 定义 `Refundable` 接口，包含退款方法（只有支付宝和微信实现）
3. 抽象类 `AbstractPaymentService` 实现公共逻辑（日志、校验格式等）

```java
// 核心接口
public interface PaymentService {
    boolean validate(String paymentInfo);
    String pay(double amount);
    String queryStatus(String orderId);
}

// 退款接口（只有部分支付方式实现）
public interface Refundable {
    boolean refund(String orderId, double amount);
}

// 抽象基类：实现公共逻辑
public abstract class AbstractPaymentService implements PaymentService {
    protected String serviceName;

    public AbstractPaymentService(String serviceName) {
        this.serviceName = serviceName;
    }

    // 公共日志逻辑
    protected void logPayment(double amount) {
        System.out.println("[" + serviceName + "] Processing payment: ¥" + amount);
    }
}

// 支付宝：实现退款
public class AlipayService extends AbstractPaymentService implements Refundable {
    public AlipayService() { super("Alipay"); }

    @Override
    public boolean validate(String paymentInfo) {
        System.out.println("[Alipay] Validating: " + paymentInfo);
        return paymentInfo != null && !paymentInfo.isEmpty();
    }

    @Override
    public String pay(double amount) {
        logPayment(amount);
        return "alipay_order_" + System.currentTimeMillis();
    }

    @Override
    public String queryStatus(String orderId) {
        return "SUCCESS";
    }

    @Override
    public boolean refund(String orderId, double amount) {
        System.out.println("[Alipay] Refunding ¥" + amount + " for order " + orderId);
        return true;
    }
}

// 银行卡：不实现退款
public class BankCardService extends AbstractPaymentService {
    public BankCardService() { super("BankCard"); }

    @Override
    public boolean validate(String paymentInfo) {
        return paymentInfo != null && paymentInfo.length() == 16;
    }

    @Override
    public String pay(double amount) {
        logPayment(amount);
        return "bank_order_" + System.currentTimeMillis();
    }

    @Override
    public String queryStatus(String orderId) {
        return "SUCCESS";
    }
}

// 使用示例
public class PaymentDemo {
    public static void processPayment(PaymentService service, double amount) {
        if (service.validate("payment_info")) {
            String orderId = service.pay(amount);
            System.out.println("Order ID: " + orderId);
        }
    }

    public static void main(String[] args) {
        PaymentService alipay = new AlipayService();
        processPayment(alipay, 99.9);

        // 退款只对 Refundable 类型
        if (alipay instanceof Refundable refundable) {
            refundable.refund("alipay_order_123", 99.9);
        }

        PaymentService bankCard = new BankCardService();
        processPayment(bankCard, 199.0);
        System.out.println("Bank card supports refund: " + (bankCard instanceof Refundable));
    }
}
```

**设计理由**：
- `PaymentService` 接口定义核心契约，调用方面向接口编程，不依赖具体实现
- `Refundable` 接口分离退款能力，符合**接口隔离原则（ISP）**，不强迫银行卡实现它不支持的方法
- `AbstractPaymentService` 抽象类复用公共逻辑，避免代码重复
- 使用 `instanceof Refundable` 检查是否支持退款，比在基础接口加 `boolean supportsRefund()` 更优雅
