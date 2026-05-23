# Chapter 49 文件上传 - 项目任务

## 任务概述

给博客 API 加上 **生产级文件上传**:

1. 头像上传 (后端代理 + 缩放 + S3/MinIO)
2. 文章插图上传 (后端代理 + 异步缩略)
3. 文件元数据表 + 用户配额
4. 防盗链 (签名 URL)
5. Tika 魔数 + 业务校验
6. 路径穿越 / Webshell 防护
7. 集成测试

## 业务背景

前面章节业务都是纯文字 (用户、文章、评论)。 真实博客必有图: 头像、封面、文章插图、Markdown 图床。 没有上传能力, 用户得手动找图床贴外链, 体验为 0。 同时上传是攻击重灾区, 不能裸接。

## 任务拆解

### Step 1: 引入 MinIO + Tika 依赖 (15 分钟)

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>
<dependency>
    <groupId>org.imgscalr</groupId>
    <artifactId>imgscalr-lib</artifactId>
    <version>4.2</version>
</dependency>
```

`docker-compose.yml` 起 MinIO:

```yaml
minio:
  image: minio/minio:latest
  ports: ["9000:9000","9001:9001"]
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin123
  command: server /data --console-address ":9001"
  volumes: ["./minio-data:/data"]
```

`application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
      file-size-threshold: 1MB
      location: /tmp/upload

storage:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket: blog
  public-base-url: http://localhost:9000/blog
```

### Step 2: StorageService 抽象 (30 分钟)

```java
public interface StorageService {
    String put(String key, InputStream in, long size, String contentType);
    String signedUrl(String key, Duration ttl);
    void delete(String key);
}

@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {
    private final MinioClient client;
    @Value("${storage.bucket}") private String bucket;

    @PostConstruct
    public void init() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    public String put(String key, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(in, size, -1)
                .contentType(contentType)
                .build());
            return key;
        } catch (Exception e) {
            throw new BizException(500, "storage put failed: " + e.getMessage());
        }
    }

    public String signedUrl(String key, Duration ttl) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(bucket).object(key)
                .method(Method.GET)
                .expiry((int) ttl.getSeconds())
                .build());
        } catch (Exception e) {
            throw new BizException(500, "sign failed");
        }
    }

    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ignored) {}
    }
}
```

### Step 3: 上传校验器 (20 分钟)

```java
@Component
public class UploadValidator {
    private static final Tika TIKA = new Tika();

    public static final Set<String> IMAGE_MIME = Set.of("image/jpeg","image/png","image/webp","image/gif");

    public String validate(MultipartFile file, Set<String> allowedMime, long maxSize) {
        if (file == null || file.isEmpty()) throw new BizException(400, "empty file");
        if (file.getSize() > maxSize) throw new BizException(413, "file too large");

        String realMime;
        try (InputStream in = file.getInputStream()) {
            realMime = TIKA.detect(in);
        } catch (IOException e) {
            throw new BizException(400, "cannot read file");
        }
        if (!allowedMime.contains(realMime)) {
            throw new BizException(415, "type not allowed: " + realMime);
        }

        if (realMime.startsWith("image/")) {
            try {
                BufferedImage img = ImageIO.read(file.getInputStream());
                if (img == null) throw new BizException(400, "invalid image");
            } catch (IOException e) {
                throw new BizException(400, "image decode failed");
            }
        }
        return realMime;
    }
}
```

### Step 4: 文件元数据 + 配额表 (20 分钟)

```sql
CREATE TABLE file_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_sha (user_id, sha256),
    KEY idx_user (user_id, created_at)
);

CREATE TABLE user_quota (
    user_id BIGINT PRIMARY KEY,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    limit_bytes BIGINT NOT NULL DEFAULT 524288000   -- 500MB
);
```

### Step 5: 头像上传接口 (30 分钟)

```java
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {
    private final UploadValidator validator;
    private final StorageService storage;
    private final FileRecordService fileService;

    private static final long MAX_AVATAR = 2 * 1024 * 1024;

    @PostMapping("/avatar")
    @PreAuthorize("isAuthenticated()")
    public Result<UploadVO> uploadAvatar(@RequestPart MultipartFile file,
                                          @AuthenticationPrincipal Long uid) throws IOException {
        String mime = validator.validate(file, UploadValidator.IMAGE_MIME, MAX_AVATAR);

        BufferedImage src = ImageIO.read(file.getInputStream());
        BufferedImage thumb = Scalr.resize(src, Scalr.Method.QUALITY, 200, 200);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", baos);
        byte[] bytes = baos.toByteArray();
        String sha = DigestUtils.md5DigestAsHex(bytes);

        String key = "avatars/" + sha + ".jpg";
        FileRecord existing = fileService.findBySha(uid, sha);
        if (existing == null) {
            storage.put(key, new ByteArrayInputStream(bytes), bytes.length, "image/jpeg");
            fileService.save(uid, sha, key, "image/jpeg", bytes.length, "AVATAR");
        }
        return Result.ok(new UploadVO(key, storage.signedUrl(key, Duration.ofHours(24))));
    }

    @PostMapping("/image")
    @PreAuthorize("isAuthenticated()")
    public Result<UploadVO> uploadImage(@RequestPart MultipartFile file,
                                         @AuthenticationPrincipal Long uid) throws IOException {
        String mime = validator.validate(file, UploadValidator.IMAGE_MIME, 5 * 1024 * 1024);
        String ext = mime.substring(mime.indexOf("/") + 1);

        byte[] bytes = file.getBytes();
        String sha = DigestUtils.md5DigestAsHex(bytes);
        String key = "posts/" + LocalDate.now() + "/" + sha + "." + ext;

        fileService.checkQuota(uid, bytes.length);
        if (fileService.findBySha(uid, sha) == null) {
            storage.put(key, new ByteArrayInputStream(bytes), bytes.length, mime);
            fileService.save(uid, sha, key, mime, bytes.length, "POST_IMAGE");
        }
        return Result.ok(new UploadVO(key, storage.signedUrl(key, Duration.ofHours(24))));
    }
}

record UploadVO(String objectKey, String url) {}
```

### Step 6: 全局异常 + 错误返回 (10 分钟)

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<Result<?>> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(413).body(Result.fail(413, "file too large"));
}

@ExceptionHandler(MultipartException.class)
public ResponseEntity<Result<?>> multipart(MultipartException e) {
    return ResponseEntity.status(400).body(Result.fail(400, "invalid multipart request"));
}
```

### Step 7: 集成测试 (20 分钟)

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UploadControllerTest {

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:latest")
        .withExposedPorts(9000)
        .withEnv("MINIO_ROOT_USER","minioadmin")
        .withEnv("MINIO_ROOT_PASSWORD","minioadmin123")
        .withCommand("server","/data");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("storage.endpoint", () -> "http://"+minio.getHost()+":"+minio.getMappedPort(9000));
    }

    @Autowired MockMvc mvc;

    @Test @WithMockUser
    void uploadAvatar_ok() throws Exception {
        byte[] png = TestImages.png(300, 300);
        MockMultipartFile file = new MockMultipartFile("file","avatar.png","image/png", png);
        mvc.perform(multipart("/api/v1/upload/avatar").file(file))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.url").exists());
    }

    @Test @WithMockUser
    void uploadAvatar_fakeImage_rejected() throws Exception {
        byte[] exe = {'M','Z',0,0};   // EXE 魔数
        MockMultipartFile file = new MockMultipartFile("file","fake.png","image/png", exe);
        mvc.perform(multipart("/api/v1/upload/avatar").file(file))
           .andExpect(status().is4xxClientError());
    }

    @Test @WithMockUser
    void uploadAvatar_tooLarge_413() throws Exception {
        byte[] big = new byte[3 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file","big.png","image/png", big);
        mvc.perform(multipart("/api/v1/upload/avatar").file(file))
           .andExpect(status().is(413));
    }
}
```

## 交付物

- [ ] `MinioStorageService` (put / signedUrl / delete)
- [ ] `UploadValidator` (Tika + ImageIO 双重校验)
- [ ] `file_record` + `user_quota` 两张表 + Mapper
- [ ] `UploadController` 头像 + 富文本图两个接口
- [ ] 全局异常处理 (413 / 415 / 400)
- [ ] 至少 4 个测试 (正常 / 假图 / 超限 / 配额)
- [ ] docker-compose 含 MinIO
- [ ] README "上传架构图" + curl 示例
- [ ] git commit: `ch49: avatar + image upload with MinIO + Tika`

## 验收清单

| 验收项 | 标准 |
|---|---|
| 真实文件上传 | 上传 200x200 PNG → 返回 signedUrl, MinIO 控制台能看到对象 |
| 头像缩放 | 上传 1000x1000, MinIO 里的对象 ImageIO 读出来是 200x200 |
| 假后缀拒绝 | 把 EXE 改名 evil.png 上传, 返回 415 |
| 超大文件 | 上传 3MB 头像, 返回 413 |
| 路径穿越 | 文件名 `../../etc/passwd`, 不会写到 /etc/ (我们已重命名为 sha) |
| 配额 | 配额 1MB 的用户传 800KB, 再传 500KB, 第二次返回 413 |
| 去重 | 同一用户传两张相同图, file_record 只有 1 条 |
| 签名 URL | 复制返回的 URL, 浏览器能直接看图; 24h 后失效 |

## 扩展挑战

1. **客户端直传 MinIO**: 后端只发 presigned PUT URL, 前端直传, complete 接口校验
2. **分片上传**: 大附件 (50MB+) 切 1MB 块, 支持断点续传
3. **异步缩略图**: 上传成功后投 MQ, worker 生成 200/400/800 三档缩略
4. **EXIF 清理**: 上传 JPEG 时去掉 GPS / 设备信息 (隐私)
5. **ClamAV 病毒扫描**: 上传后异步扫, 阳性标记 BLOCKED + 删除
6. **CDN 接入**: signed URL 走 CDN 而不是直连 MinIO, 防盗链 + 加速
