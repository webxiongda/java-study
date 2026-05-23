# Chapter 41 认证基础 - 自测与验收

> 5 题：2 概念 + 2 实操 + 1 综合，全部含**参考答案**。

---

## Q1（概念）：为什么必须用 BCrypt（或 Argon2）存密码？MD5/SHA256 + salt 不行吗？

**参考答案：**

不行。MD5/SHA256 即便加 salt，也有两个致命问题：

| 维度 | MD5/SHA256 + salt | BCrypt |
|------|-------------------|--------|
| **速度** | GPU 每秒 100 亿次 | cost=12 时每秒约 4 次 |
| **抗硬件升级** | 算法固定，硬件越来越快 | cost 可调，硬件升级时 +1 即可 |
| **彩虹表** | 加 salt 失效 | 自带 salt + 慢哈希 |
| **设计目的** | 数据完整性校验 | **专为密码设计**（KDF）|

**BCrypt 关键属性**：

```java
String hash = BCrypt.hashpw("Aa12345!", BCrypt.gensalt(12));
// 输出：$2a$12$N9qo8uLOickgx2ZMRZoMy.Mrqa...  固定 60 字符
// $2a$       算法版本
// $12$       cost
// 22 chars   salt
// 31 chars   hash
```

特征：
- **每次 hash 不同**：salt 随机，同一密码两次 hash 结果不同。
- **慢**：cost=12 约 250ms。GPU 优化空间小（设计上规避了并行）。
- **可升级**：未来 cost=14 太慢就改为 14，老用户登录时**透明 rehash**。

**面试常考**：

- "BCrypt 怎么对比？" → `BCrypt.checkpw(input, dbHash)`，库内部读 dbHash 里的 salt + cost 重算。
- "BCrypt 输出 60 字符，怎么存？" → `varchar(60)`，不要 `varchar(255)`。
- "替代方案？" → Argon2id（OWASP 2026 推荐）、scrypt、PBKDF2-HMAC-SHA256。

---

## Q2（概念）：登录失败时，提示"用户名不存在"和"密码错误"分开，会有什么风险？正确做法是什么？

**参考答案：**

风险：**用户名枚举攻击**。攻击者批量请求登录接口，根据不同提示语就能知道哪些用户名存在，下一步实施：

1. **撞库**：用泄露的"username + password"组合在你站点试。
2. **暴力破解**：知道用户名后只用爆密码。
3. **社工**：邮件钓鱼时精准伪造（"alice 你好，你的账号..."）。

**正确做法**：

```java
// ❌ 错误
if (user == null) throw new BizException("用户不存在");
if (!matches(pwd, user.hash)) throw new BizException("密码错误");

// ✅ 正确
boolean ok = (user != null) && encoder.matches(pwd, user.getPasswordHash());
if (!ok) throw new BizException("用户名或密码错误");
```

**还要做的**：

| 维度 | 措施 |
|------|------|
| 时间一致 | 用户不存在时也跑一次 BCrypt（用固定假 hash），避免时序差异暴露存在性 |
| 注册接口 | 同样不要返回"该用户名已被注册"。要么走邮件验证，要么前端 debounce + 验证码 |
| 限流 | 同 IP / 同账号失败 5 次 → 锁定（本章 demo 已实现） |
| 监控 | 同 IP 短时大量失败登录 → 告警 |
| 日志 | 失败时记日志但**不要打密码明文**或 hash |

---

## Q3（实操）：以下登录代码有 5 处安全问题，找出并改正。

```java
@PostMapping("/login")
public LoginResp login(@RequestBody LoginReq req) {                   // ①
    User u = userMapper.findByUsername(req.username);
    if (u == null) {
        log.warn("user not found: {}", req.username);
        throw new BizException("用户不存在");                          // ②
    }
    if (!u.getPassword().equals(req.password)) {                     // ③④
        throw new BizException("密码错误");
    }
    String token = String.valueOf(System.currentTimeMillis());        // ⑤
    return new LoginResp(token, u.getId(), u.getPassword());          // 也返回 password
}
```

**参考答案：**

**① 缺 @Valid**

`LoginReq` 字段没经过校验，攻击者传 null/超长字符串绕过逻辑。

```java
public Result<LoginResp> login(@Valid @RequestBody LoginReq req) { ... }
```

**② 暴露用户是否存在**

见 Q2。改为统一"用户名或密码错误"。

**③ 明文比较密码**

数据库里存了明文密码？立即修复：迁移到 BCrypt，老密码用 `DelegatingPasswordEncoder` 标 `{noop}`，下次登录时透明 rehash。

```java
if (!encoder.matches(req.password(), u.getPasswordHash())) { ... }
```

**④ 用 `equals` 比对（潜在时序攻击）**

即使加密后，比对签名/MAC 时 `String.equals` 短路退出。BCrypt 自身已是常量时间，但其他场景（如 HMAC 比较）需要 `MessageDigest.isEqual`：

```java
MessageDigest.isEqual(expected.getBytes(), actual.getBytes());
```

**⑤ 用时间戳当 Token**

可预测、可碰撞。改用密码学安全随机数：

```java
byte[] bytes = new byte[32];
new SecureRandom().nextBytes(bytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
```

或直接用 JWT（42 章）。

**另外**：返回值不要带 password / passwordHash，定义专用 `UserVO`。

---

## Q4（实操）：写一个 Spring Security 的 `PasswordEncoder` Bean，支持新用户用 BCrypt 12、老用户兼容历史 `{noop}` 明文，登录成功后自动 rehash 老密码。

**参考答案：**

### 一、Encoder 配置

```java
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        encoders.put("noop", NoOpPasswordEncoder.getInstance());

        DelegatingPasswordEncoder delegating =
            new DelegatingPasswordEncoder("bcrypt", encoders);
        // 默认存储格式 {bcrypt}xxx
        // 老数据 {noop}plaintext 也能匹配
        return delegating;
    }
}
```

### 二、登录时透明 rehash

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder encoder;   // DelegatingPasswordEncoder

    @Transactional
    public LoginResp login(LoginReq req) {
        UserDO user = userMapper.findByUsername(req.username());
        boolean ok = user != null && encoder.matches(req.password(), user.getPasswordHash());
        if (!ok) throw new BizException("用户名或密码错误");

        // 关键：检测到老格式 → rehash
        if (encoder.upgradeEncoding(user.getPasswordHash())) {
            String newHash = encoder.encode(req.password());
            userMapper.updatePasswordHash(user.getId(), newHash);
            log.info("rehashed password for user {}", user.getId());
        }
        return new LoginResp(issueToken(user), user.getId(), user.getUsername());
    }
}
```

`upgradeEncoding(hash)`：DelegatingPasswordEncoder 内置方法，hash 前缀不是当前默认（`bcrypt`）时返回 true。

### 三、迁移脚本

```sql
-- 把还在用 plaintext 的老用户加前缀，便于 DelegatingPasswordEncoder 识别
UPDATE user
SET password_hash = CONCAT('{noop}', password_hash)
WHERE password_hash NOT LIKE '{%}%';
```

### 四、关键点

1. **永远不要在生产环境保留 `{noop}` 长期**，rehash 完成后写定时任务批量强制重置仍是明文的账号。
2. **rehash 失败不能影响登录**：建议异步执行（`@Async` 或事件），即使写失败用户也能登录。
3. **password_hash 字段长度调整**：明文可能长于 60 字符，迁移期需 `varchar(255)`，迁移完再回收。
4. **单测**：分别用 `{bcrypt}xxx` 和 `{noop}plain` 跑一次 login，确认都能登录且老 hash 被升级。

---

## Q5（综合）：你的博客系统出现了大规模"撞库攻击"，运维发现：

- 同一时间段内 10000+ 不同 IP 来尝试登录
- 每个 IP 只试 2-3 个账号，命中率 ~0.5%（说明用了真实泄露的 username + password）
- 现有限流是按账号锁的（5 次失败/15 分钟），但攻击者每次换账号试，所以根本触发不了

请设计一个 24 小时内能上线的应急方案。

**参考答案：**

### 一、止损（紧急，1 小时内）

```java
// 1. 全局开关：临时启用图形验证码
@Value("${security.captcha.enabled:false}")
private boolean captchaEnabled;

@PostMapping("/login")
public Result<LoginResp> login(@Valid @RequestBody LoginReq req,
                               @RequestParam(required=false) String captchaToken) {
    if (captchaEnabled && !captchaService.verify(captchaToken)) {
        throw new BizException("captcha required");
    }
    ...
}
```

```yaml
# 紧急上线
security:
  captcha:
    enabled: true
```

效果：攻击者要识别验证码，成本陡升 10-100 倍。

### 二、补限流维度（4 小时内）

现有限流只看账号维度，加 3 个新维度：

| 维度 | Redis Key | 阈值 |
|------|-----------|------|
| 账号 | `login:fail:{username}` | 5/15min（已有）|
| IP | `login:fail:ip:{ip}` | 50/15min |
| IP × Username 组合 | `login:fail:combo:{ip}:{user}` | 3/15min |
| 全局 QPS | `login:qps:{minute}` | 1000/min |

任何一个维度超阈值 → 直接 reject 并要求验证码。

### 三、行为分析（24 小时内）

```java
@Component @Slf4j
public class LoginAuditLogger {
    @EventListener
    public void onLoginAttempt(LoginAttemptEvent e) {
        Map<String, Object> data = Map.of(
            "ts", e.getTime(),
            "user", e.getUsername(),
            "ip", e.getIp(),
            "ua", e.getUserAgent(),
            "country", geoIp(e.getIp()),
            "success", e.isSuccess()
        );
        log.info("AUDIT|login|{}", JSON.toJSONString(data));
        // Filebeat / Loki → 异常 IP 段、异常国家 → Grafana 告警
    }
}
```

把日志接到 ES / Loki，写规则：

- 同 ASN 5 分钟内 > 100 IP 登录尝试 → 临时封 ASN 网段。
- 海外 IP 大量登录国内账号 → 触发额外二次验证。

### 四、被攻破账号的处理

```sql
-- 命中的 0.5% 账号 = 真实泄露
-- 找出"近 24 小时登录成功且 IP / UA 与历史不一致"的用户
SELECT user_id FROM login_history
WHERE created_at > NOW() - INTERVAL 24 HOUR
  AND success = 1
  AND ip_country != (SELECT ip_country FROM login_history WHERE user_id = lh.user_id AND created_at < NOW() - INTERVAL 7 DAY ORDER BY id DESC LIMIT 1);
```

对这些账号：
1. **强制下线**：清掉 Redis 中的 token。
2. **强制改密**：登录后跳转改密页面。
3. **邮件通知**：告知"检测到异常登录，请确认"。

### 五、长期方案（不在 24 小时内，但要列计划）

| 方案 | 收益 |
|------|------|
| **被动 MFA**：异地登录强制二次验证（邮箱 / 短信 / TOTP） | 撞库即使密码对也进不来 |
| **HIBP 查询**：用户注册 / 改密时调 haveibeenpwned API 检查密码是否在已知泄露库 | 阻止用弱密码 |
| **Risk-based Auth**：风险评分（IP 信誉、设备指纹、行为）动态决定是否触发 MFA | 用户无感 + 高安全 |
| **WebAuthn / Passkey** | 彻底无密码 |

### 六、应急方案影响评估

| 维度 | 风险 | 缓解 |
|------|------|------|
| 误伤正常用户 | 验证码体验差 | 仅对失败用户 / 异常 IP 触发，正常用户透明 |
| Redis QPS 暴涨 | 限流 key 过多 | 用 `setex` 而非 `set + expire`，避免两次往返 |
| 验证码服务挂 | 全站登录瘫痪 | 双 provider（极验 + 自建），fallback 简化逻辑 |
| 通知邮件爆炸 | SMTP 限额 | 通知合并（每用户 1 封，含异常列表） |

**关键点**：撞库防御的本质是"提高攻击成本"，没有银弹。最实用的组合是：**多维度限流 + 异地触发 MFA + 行为审计**。账号锁定只是最后一道防线。
