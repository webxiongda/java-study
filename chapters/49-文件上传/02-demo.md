# Chapter 49 文件上传 - 实操 Demo

## Demo 目标

安全的图片上传：大小 / MIME / 真实类型（魔数）三重校验 + 路径穿越防护 + 哈希命名 + 本地落盘（生产换 S3/MinIO 接口一致）。

## 前置条件

- 基线 pom + 44 章 Security（要鉴权）。

## 增量依赖

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
```

## 1. `application.yml`

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 5MB
      max-request-size: 20MB
      file-size-threshold: 1MB    # 超过 1MB 才落临时文件
      location: ${java.io.tmpdir}/blog-upload

app:
  upload:
    dir: ./data/uploads
    public-base-url: http://localhost:8080/static/
    allowed-mime: image/png,image/jpeg,image/webp,image/gif
    max-bytes: 5242880            # 5 MB
```

## 2. 配置类

```java
@ConfigurationProperties(prefix = "app.upload")
@Data
public class UploadProperties {
    private String dir;
    private String publicBaseUrl;
    private Set<String> allowedMime;
    private long maxBytes;
}

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadConfig implements WebMvcConfigurer {
    private final UploadProperties p;
    public UploadConfig(UploadProperties p) { this.p = p; }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry r) {
        r.addResourceHandler("/static/**")
         .addResourceLocations("file:" + Paths.get(p.getDir()).toAbsolutePath() + "/");
    }
}
```

## 3. 上传服务（核心）

```java
@Service
@RequiredArgsConstructor
public class UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final Tika TIKA = new Tika();

    private final UploadProperties cfg;

    public String upload(MultipartFile file, Long userId) throws IOException {
        // 1. 基础校验
        if (file == null || file.isEmpty())
            throw new BusinessException(ErrorCode.PARAM_ERROR, "empty file");
        if (file.getSize() > cfg.getMaxBytes())
            throw new BusinessException(ErrorCode.PARAM_ERROR, "file too large");

        // 2. MIME（client 自报，可篡改）
        String declared = file.getContentType();
        if (declared == null || !cfg.getAllowedMime().contains(declared))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "mime not allowed: " + declared);

        // 3. 真实类型（魔数嗅探，关键）
        String real;
        try (InputStream in = file.getInputStream()) {
            real = TIKA.detect(in);
        }
        if (!cfg.getAllowedMime().contains(real))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "real mime not allowed: " + real);

        // 4. 文件名：用哈希，不信任原始名
        String ext = switch (real) {
            case "image/png"  -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif"  -> "gif";
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "unsupported");
        };

        String sha;
        try (InputStream in = file.getInputStream()) {
            sha = DigestUtils.md5DigestAsHex(in);
        }
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String relative = "%s/%s/%s.%s".formatted(yyyymm, sha.substring(0, 2), sha, ext);

        // 5. 路径穿越防护：normalize 后必须仍在 dir 内
        Path base = Paths.get(cfg.getDir()).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base))
            throw new BusinessException(ErrorCode.PARAM_ERROR, "invalid path");

        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("upload ok uid={} mime={} size={} path={}", userId, real, file.getSize(), relative);
        return cfg.getPublicBaseUrl() + relative;
    }
}
```

## 4. Controller

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService service;

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> avatar(@RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal CustomUser user) throws IOException {
        return ApiResponse.ok(service.upload(file, user.getId()));
    }
}
```

## 5. 异常映射（在 GlobalExceptionHandler 中补）

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ApiResponse<Void>> handleSize(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(413)
        .body(ApiResponse.fail(ErrorCode.PARAM_ERROR.getCode(), "file too large"));
}
```

## 6. 安全加固清单

- 资源访问域名独立（如 `cdn.example.com`）→ 即使上传了 HTML，也是另一域，规避 XSS / cookie 盗取。
- 静态目录禁用 PHP/JSP 执行（生产用 nginx + `location ~* \.(php|jsp)$ { deny all; }`）。
- 图片再处理（ImageIO 重编码）可去除恶意 EXIF / SVG 脚本。
- 上传接口必须鉴权 + 限流（45 章）。
- 病毒扫描（如 ClamAV）按业务必要性可选。

## 运行与验证

| 检查项 | 命令 |
|---|---|
| 正常 | `curl -F file=@a.png -H 'Authorization: Bearer xxx' :8080/api/files/avatar` → URL |
| 超大 | 上传 10MB → 返回 PARAM_ERROR + 413 |
| 假后缀 | `cp evil.exe fake.png && curl -F file=@fake.png` → 魔数嗅探拒绝 |
| 路径穿越 | 试 `../../etc/passwd` 作 name（前面已不信任原名）→ 写入 `data/uploads/yyyymm/..` |
| 同文件再传 | 两次同图 → 第二次复用，不重复写盘 |

## 常见坑

- 用原始文件名拼路径 → `../../../../etc/passwd`。 一律改哈希命名。
- 只校验 ContentType，不嗅探 → `.html` 改名 `.png` 直接通过。
- 把上传目录直接放 `src/main/resources/static` 下 → 重启被覆盖 / 容器内变只读。
- 没设 `max-file-size` → 默认 1MB，前端报错莫名。
- 同步阻塞大文件上传 → 用 chunked / 预签名 URL（S3/OSS）让客户端直传。

## 提交

```bash
git commit -m "chapter 49: secure upload with mime sniff + path safety"
```
