# Chapter 08 - 项目任务：用户注册校验器

## 业务背景

你在开发一个用户注册接口。前端提交用户名和密码，后端需要在保存到数据库之前对输入进行严格校验。如果校验不通过，应该给出明确的字段名和错误原因，而不是笼统地说"注册失败"。

---

## 任务要求

### 第一步：实现自定义异常 ValidationException

```
字段：
  - String field    // 哪个字段校验失败，如 "username"、"password"
  - String message  // 错误信息，如 "用户名长度必须在 3-20 个字符之间"
```

要求：
- 继承 `RuntimeException`
- 提供带 field 和 message 的构造器
- 提供 getter 方法
- `toString()` 输出类似：`ValidationException{field='username', message='用户名不能为空'}`

### 第二步：实现 UserValidator 校验类

提供静态方法 `validate(String username, String password)`，校验规则如下：

**用户名校验**（字段名：`"username"`）：
1. 不能为 null 或空白字符串 → `"用户名不能为空"`
2. 长度必须在 3-20 个字符之间 → `"用户名长度必须在 3-20 个字符之间，当前长度：X"`
3. 只能包含字母和数字（`[a-zA-Z0-9]+`） → `"用户名只能包含字母和数字"`

**密码校验**（字段名：`"password"`）：
1. 不能为 null 或空白字符串 → `"密码不能为空"`
2. 长度不能少于 8 个字符 → `"密码长度不能少于 8 个字符，当前长度：X"`
3. 必须同时包含字母和数字 → `"密码必须同时包含字母和数字"`

**校验顺序**：先完整校验用户名，再校验密码（每个字段按上述顺序，第一个不通过即抛出，不用一次返回所有错误）。

### 第三步：调用演示

写一个 `main` 方法，演示以下场景的输出：

```java
// 场景1：用户名为空
UserValidator.validate("", "Password123");
// 预期抛出：ValidationException{field='username', message='用户名不能为空'}

// 场景2：用户名太短
UserValidator.validate("ab", "Password123");
// 预期抛出：ValidationException{field='username', message='用户名长度必须在 3-20 个字符之间，当前长度：2'}

// 场景3：用户名含非法字符
UserValidator.validate("user_name", "Password123");
// 预期抛出：ValidationException{field='username', message='用户名只能包含字母和数字'}

// 场景4：密码太短
UserValidator.validate("validUser", "abc123");
// 预期抛出：ValidationException{field='password', message='密码长度不能少于 8 个字符，当前长度：6'}

// 场景5：密码没有数字
UserValidator.validate("validUser", "abcdefgh");
// 预期抛出：ValidationException{field='password', message='密码必须同时包含字母和数字'}

// 场景6：合法输入
UserValidator.validate("validUser", "Pass1234");
// 预期：正常通过，无异常
```

---

## 参考结构

```
src/
└── com/
    └── example/
        └── validator/
            ├── ValidationException.java
            ├── UserValidator.java
            └── Main.java
```

---

## 验收标准

1. **异常信息完整**：抛出的 `ValidationException` 包含准确的 `field` 字段名和具体的错误描述（含实际值，如当前长度），不能是模糊的 "校验失败"。
2. **校验顺序正确**：按照规定的顺序校验（先用户名，再密码；每个字段内按编号顺序），确保场景1-6的触发顺序与预期一致。
3. **正则表达式正确**：用户名只允许 `[a-zA-Z0-9]+`，密码同时含字母和数字的判断使用正则或字符逐一扫描均可，但结果必须正确（"abcdefgh" 应失败，"abc12345" 应通过）。
4. **调用方正确处理异常**：`main` 方法中用 `try-catch` 捕获 `ValidationException`，输出字段名和错误信息，而不是让程序崩溃；合法输入时打印 `"注册校验通过"` 之类的提示。

---

## 常见坑

**坑 1：用 `== null` 检查字符串，漏掉空白字符串的情况**

```java
// 错误：只检查 null，空格字符串 "   " 会通过
if (username == null) {
    throw new ValidationException("username", "用户名不能为空");
}

// 正确：同时检查 null 和空白
if (username == null || username.trim().isEmpty()) {
    throw new ValidationException("username", "用户名不能为空");
}
```

**坑 2：密码"同时包含字母和数字"的判断逻辑写反**

```java
// 错误：条件写反，含字母OR含数字就抛异常
String password = "abc12345";
if (password.matches(".*[a-zA-Z].*") || password.matches(".*[0-9].*")) {
    throw new ValidationException("password", "密码必须同时包含字母和数字");
}
// "abc12345" 含字母，条件成立，错误地抛出异常！

// 正确：不含字母 OR 不含数字时才抛异常
boolean hasLetter = password.matches(".*[a-zA-Z].*");
boolean hasDigit  = password.matches(".*[0-9].*");
if (!hasLetter || !hasDigit) {
    throw new ValidationException("password", "密码必须同时包含字母和数字");
}
```
