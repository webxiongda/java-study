# Chapter 49 文件上传 - 自测与验收

## Q1 概念题: 文件上传"三层校验"分别防什么? 各自能被怎么绕过?

**参考答案**:

| 层 | 内容 | 防什么 | 怎么被绕过 |
|---|---|---|---|
| 前端校验 | JS 看后缀 / 大小 | 普通误传 | F12 / curl / Postman 直接 POST 即可绕 |
| HTTP Content-Type | 客户端自报 MIME | 浏览器默认行为 | 抓包改 header, 或 curl `-H "Content-Type: image/png"` |
| 服务端魔数嗅探 (Tika) | 读文件前几字节判真实类型 | 改后缀的伪装 | 改文件头会破坏文件, 难绕过 |
| 业务校验 | 比如图片必须 ImageIO 能解码 | 畸形构造文件 | 几乎无法绕 (要伪造完整合法文件) |

**关键认知**:

- 前两层只用于 **改善 UX** (前端早提示 / 后端少读流), 不能作为安全依据
- 真正的安全防线在 **服务端**, 至少要 Tika 嗅探 + 业务解码
- 还要 **重命名 (UUID/hash)** 切断路径穿越 + **改 Content-Disposition: attachment** 防浏览器解析为 HTML/JS

**常见错答**:
- 只校验后缀: 改名秒过
- 只校验 Content-Type: 抓包秒过
- 只用 Tika 但不重命名: `../../etc/cron.d/job` 仍可路径穿越

---

## Q2 概念题: 客户端直传 S3 (Presigned URL) 的完整流程? 为什么生产推荐这种方式?

**参考答案**:

**流程**:

```
1. 客户端 → 后端: POST /upload/presigned-url
   { filename: "v.mp4", contentType: "video/mp4", size: 80000000 }

2. 后端校验:
   - 大小是否超限
   - 类型白名单
   - 用户配额是否充足
   生成 objectKey = "videos/2026/05/" + uuid + ".mp4"
   调 S3 SDK 生成 PUT 预签名 URL (限 5-15 分钟)
   ↓
   返回 { uploadUrl: "https://bucket.s3.../objectKey?X-Amz-Signature=...",
          objectKey: "videos/2026/05/uuid.mp4" }

3. 客户端 → S3: PUT uploadUrl + body=文件
   (直接传到 S3, 不经过后端)

4. 客户端 → 后端: POST /upload/complete
   { objectKey: "videos/2026/05/uuid.mp4" }

5. 后端:
   - HEAD objectKey 确认存在 + 大小匹配
   - 写文件元数据到 DB
   - 触发后续处理 (转码 / 缩略图, 异步)
   返回最终访问 URL
```

**为什么推荐**:

| 维度 | 后端代理 | 客户端直传 |
|---|---|---|
| 后端带宽 | 100MB 视频流过后端 | 0 |
| 后端 CPU/内存 | 流式处理消耗资源 | 0 |
| 用户速度 | 受后端带宽限制 | S3 全球加速, 通常更快 |
| 失败重试 | 整传重传 | S3 multipart 自动重试单个 part |
| 后端水平扩展 | 上传流量先打到后端 | 后端只管发签名 + 校验, QPS 极高 |

**关键安全点**:
- 签名 URL 必须 **短 TTL** (5-15 分钟), 防泄漏后被滥用
- 签名时 **绑定 Content-Type / Content-Length-Range**, 防上传超大或类型欺骗
- complete 接口要 **HEAD 校验**, 不能信客户端的 "我传完了"

---

## Q3 代码审查: 找出下面文件上传接口的所有问题

```java
@PostMapping("/upload")
public String upload(@RequestParam MultipartFile file) throws IOException {
    String name = file.getOriginalFilename();
    String ext = name.substring(name.lastIndexOf("."));
    if (!ext.equals(".jpg") && !ext.equals(".png")) {
        throw new RuntimeException("type not allowed");
    }
    File dest = new File("/var/www/html/uploads/" + name);
    file.transferTo(dest);
    return "http://example.com/uploads/" + name;
}
```

**至少 10 个问题**:

1. **没限文件大小**: 100GB 文件直接打满磁盘 (依赖 Spring 默认配置, 但应有业务校验 + 返回友好错误)
2. **后缀校验可绕**: `evil.exe` 改名 `evil.jpg` 通过
3. **没做魔数嗅探**: 应该用 Tika 检测真实 MIME
4. **路径穿越**: `name = "../../../etc/passwd"`, 拼路径直接覆盖系统文件
5. **原文件名落盘**: 同名覆盖, 中文/特殊字符可能乱码, 用 UUID/hash 重命名
6. **上传目录在 webroot 内** (`/var/www/html/uploads/`): 上传 `.jsp/.php` 可被执行 → Webshell
7. **没校验登录态 / 权限**: 任何人可上传, 容易被刷
8. **没限速 / 限频**: 一个用户秒级上传 1 万个文件
9. **`RuntimeException` 滥用**: 应抛业务异常 + 全局处理器返回结构化错误
10. **`name.lastIndexOf(".")`**: 文件名无后缀时 `substring(-1)` StringIndexOutOfBoundsException
11. **URL 拼接写死**: 域名应配置化; 且应使用签名/私有 URL
12. **没事务/没记录元数据**: 上传成功但 DB 写失败 → 孤儿文件, 应有定期清理
13. **没病毒扫描**: 用户场景必须扫
14. **没异步处理缩略图/EXIF 清理**: 同步阻塞 + 隐私泄漏

**修复后骨架**:

```java
@PostMapping("/upload/image")
@PreAuthorize("isAuthenticated()")
public Result<UploadVO> upload(@RequestPart MultipartFile file,
                                @AuthenticationPrincipal User user) {
    uploadValidator.validate(file, ALLOWED_IMAGE_MIME, MAX_IMAGE_SIZE);
    String sha = DigestUtils.md5DigestAsHex(file.getInputStream());
    String key = "images/" + LocalDate.now() + "/" + sha + "." + Mime.toExt(real);
    storage.put(key, file.getInputStream(), file.getSize());
    fileRepo.save(new FileRecord(user.getId(), key, sha, file.getSize()));
    return Result.ok(new UploadVO(storage.urlOf(key)));
}
```

---

## Q4 代码题: 实现分片上传 (后端 + 前端伪码)

**后端**:

```java
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkService chunkService;

    /** 检查已传分片 (断点续传用) */
    @GetMapping("/check")
    public Result<Set<Integer>> check(@RequestParam String fileId) {
        return Result.ok(chunkService.uploadedChunks(fileId));
    }

    /** 上传单片 */
    @PostMapping("/chunk")
    public Result<?> uploadChunk(@RequestParam String fileId,
                                  @RequestParam int chunkIndex,
                                  @RequestParam int totalChunks,
                                  @RequestParam MultipartFile chunk) throws IOException {
        chunkService.saveChunk(fileId, chunkIndex, chunk);
        if (chunkService.isComplete(fileId, totalChunks)) {
            String finalKey = chunkService.merge(fileId, totalChunks);
            return Result.ok(Map.of("status", "done", "key", finalKey));
        }
        return Result.ok(Map.of("status", "partial"));
    }
}

@Service
public class ChunkService {
    @Value("${upload.temp-dir}") private String tempDir;
    @Value("${upload.final-dir}") private String finalDir;

    public void saveChunk(String fileId, int idx, MultipartFile chunk) throws IOException {
        Path dir = Paths.get(tempDir, sanitize(fileId));
        Files.createDirectories(dir);
        chunk.transferTo(dir.resolve("part-" + idx));
    }

    public Set<Integer> uploadedChunks(String fileId) {
        Path dir = Paths.get(tempDir, sanitize(fileId));
        if (!Files.exists(dir)) return Set.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> Integer.parseInt(p.getFileName().toString().substring(5)))
                    .collect(Collectors.toSet());
        } catch (IOException e) { return Set.of(); }
    }

    public boolean isComplete(String fileId, int total) {
        return uploadedChunks(fileId).size() == total;
    }

    public String merge(String fileId, int total) throws IOException {
        Path dir = Paths.get(tempDir, sanitize(fileId));
        Path out = Paths.get(finalDir, fileId);
        try (OutputStream os = Files.newOutputStream(out)) {
            for (int i = 0; i < total; i++) {
                Files.copy(dir.resolve("part-" + i), os);
            }
        }
        FileSystemUtils.deleteRecursively(dir);
        return out.toString();
    }

    private String sanitize(String s) {
        if (!s.matches("[a-zA-Z0-9_-]{1,64}")) throw new BizException(400, "invalid fileId");
        return s;
    }
}
```

**前端伪码**:

```js
async function upload(file) {
  const fileId = await sha256(file).slice(0, 32);
  const CHUNK = 1024 * 1024;
  const total = Math.ceil(file.size / CHUNK);

  const { data: uploaded } = await fetch(`/api/v1/upload/check?fileId=${fileId}`).then(r=>r.json());

  for (let i = 0; i < total; i++) {
    if (uploaded.includes(i)) continue;
    const blob = file.slice(i * CHUNK, (i + 1) * CHUNK);
    const fd = new FormData();
    fd.append('fileId', fileId);
    fd.append('chunkIndex', i);
    fd.append('totalChunks', total);
    fd.append('chunk', blob);
    await fetch('/api/v1/upload/chunk', { method: 'POST', body: fd });
    onProgress((i + 1) / total);
  }
}
```

**关键点**:
- fileId 用 hash, 同一文件可秒传 (后端 check 返回 all = 直接 complete)
- 分片大小 1-5MB (小了请求多, 大了重传贵)
- 后端要校验 fileId 合法 (防路径穿越)
- merge 应异步触发, 大文件 merge 耗时
- 临时目录定期清理 (超过 24h 未完成 → 删)
- 生产推荐 S3 multipart upload, 不要自己管临时目录

---

## Q5 综合设计: 博客项目的图床方案 (头像 / 富文本图 / 视频)

**需求**:
- 头像 (≤ 2MB, jpg/png/webp) 上传后裁剪 200x200
- 文章富文本插图 (≤ 5MB)
- 视频附件 (≤ 500MB, mp4)
- 防止流量被刷
- VIP 用户配额 5GB / 普通用户 500MB

**设计**:

### 1. 三类上传不同策略

| 类型 | 上传方式 | 处理 |
|---|---|---|
| 头像 | 后端代理 (小文件) | 同步 Tika 校验 + Scalr 缩放 + 上 S3 |
| 富文本图 | 后端代理 | 同步 Tika + 异步缩略图 (MQ) + 上 S3 |
| 视频 | S3 Presigned 直传 | complete 后异步触发转码 (云服务) |

### 2. 数据库表

```sql
CREATE TABLE file_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    type ENUM('AVATAR','POST_IMAGE','VIDEO','ATTACHMENT'),
    status ENUM('PENDING','READY','FAILED'),
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_sha (sha256, user_id),  -- 同一用户同文件去重
    KEY idx_user (user_id, created_at)
);

CREATE TABLE user_quota (
    user_id BIGINT PRIMARY KEY,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    limit_bytes BIGINT NOT NULL
);
```

### 3. 配额校验 (上传前)

```java
public void preCheck(Long uid, long incomingSize) {
    UserQuota q = quotaMapper.selectByUid(uid);
    if (q.getUsedBytes() + incomingSize > q.getLimitBytes()) {
        throw new BizException(413, "配额不足");
    }
}
```

上传成功后 `UPDATE user_quota SET used_bytes = used_bytes + ? WHERE user_id = ?`,
删除文件时 `used_bytes - ?`。

### 4. 防盗链 / 访问控制

- 上传后落到 **私有 S3 bucket**
- 头像 / 富文本图: 生成 **长 TTL CDN URL** (24h) 缓存到 Redis, 失效自动续
- 视频: 生成 **短 TTL Signed URL** (1h), 每次请求实时生成

### 5. 限流

```java
@RateLimit(key = "upload:#userId", limit = 30, window = 60)  // 每分钟 30 次
public Result<?> upload(...) { ... }
```

视频上传额外限: 每用户每天 5 个 (Redis INCR + TTL)

### 6. 异步链路

```
后端代理上传 → ack → 投 MQ "file.uploaded"
                              ↓
                  ┌───────────┼───────────┐
              缩略图           EXIF 清理      病毒扫描
              (Scalr)        (重编码)       (clamd)
                              ↓
                       状态变 READY
```

### 7. 失败模式

| 场景 | 应对 |
|---|---|
| 上传中网络断 | 分片续传; 大文件直传 S3 multipart 自动重试 |
| S3 不可用 | 上传接口 503, 客户端重试; 不降级到本地 (一致性) |
| 病毒扫出来 | 标记 status=FAILED, 异步删除, 通知用户 |
| 用户取消上传 | 临时分片 24h 后定时清理 |
| 配额竞态 (并发上传) | UPDATE 时加 `WHERE used + ? <= limit` 条件, 失败回滚 |

**面试 30s 讲法**: 博客图床分三类: 头像走后端代理 + 同步缩放, 富文本图走后端代理 + 异步处理, 视频走 S3 预签名直传 + complete 确认。 所有文件落私有 bucket, 访问走签名 URL 防盗链。 用 sha256 去重 + 文件记录表 + 用户配额表防滥用。 上传前预校验配额, 上传后异步缩略 / EXIF 清理 / 病毒扫描。 失败模式覆盖网络断、S3 宕、病毒、配额竞态。

---

## 通过标准

- [ ] 能讲清三层校验 (前端 / Content-Type / Tika 魔数) 各自能防什么、被怎么绕
- [ ] 能完整讲 S3 预签名直传流程 + 安全要点 (TTL / Content-Type 绑定 / complete HEAD)
- [ ] 能识别上传接口 10+ 个安全问题
- [ ] 能实现分片上传 + 断点续传 (前后端伪码)
- [ ] 能设计图床的三类策略 + 配额 + 防盗链
