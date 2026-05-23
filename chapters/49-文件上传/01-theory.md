# Chapter 49 文件上传 - 理论篇

## 一、学习定位

文件上传是 Web 应用的"高危区": 90% 的服务器入侵漏洞都跟它有关 (上传 Webshell → 远程执行 → 拿权限)。 本章解决:

- **安全**: MIME 校验 / 魔数嗅探 / 路径穿越 / 病毒扫描 / 执行权限
- **性能**: 大文件分片 / 断点续传 / 直传到对象存储
- **存储**: 本地 / S3 / OSS / MinIO 接口统一
- **业务**: 头像 / 富文本图片 / 附件 / 视频

- 优先级: L2 项目常用 + L3 面试必问
- 预计投入: 4 小时
- 阶段产出: 头像上传 + 富文本图片上传 + 大文件分片上传 (任选)

## 二、核心概念

### 1. multipart/form-data 协议

```
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=----xyz

------xyz
Content-Disposition: form-data; name="file"; filename="a.png"
Content-Type: image/png

<binary data>
------xyz--
```

Spring 用 `MultipartFile` 接收, 底层是 Servlet 3.0+ 的 `Part`。

### 2. 三层校验

| 层 | 校验内容 | 可绕过? |
|---|---|---|
| **前端** | 后缀 / 大小 | 完全可绕过 (改 HTTP 包) |
| **HTTP Content-Type** | 客户端自报的 MIME | 可篡改 |
| **魔数 / 文件头嗅探** | 读前几字节匹配 | 难绕过 (改文件头就破坏文件) |
| **业务校验** | 比如图片必须能用 ImageIO 解码 | 几乎无法绕 |

**魔数示例**:

| 文件 | 魔数 (hex) |
|---|---|
| PNG | `89 50 4E 47 0D 0A 1A 0A` |
| JPEG | `FF D8 FF` |
| GIF | `47 49 46 38` |
| PDF | `25 50 44 46` |
| ZIP | `50 4B 03 04` |
| EXE | `4D 5A` |

用 **Apache Tika** 检测真实类型:

```java
Tika tika = new Tika();
String real = tika.detect(file.getInputStream());
// real = "image/png", 即使文件名是 evil.exe
```

### 3. 路径穿越 (Path Traversal)

**攻击**: 用户上传文件名 `../../../etc/passwd`, 服务端拼路径写到非预期位置。

**防护**:

```java
Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
Path target = base.resolve(filename).normalize();
if (!target.startsWith(base)) {
    throw new SecurityException("invalid path");
}
```

**更彻底**: 完全不信任用户文件名, **用 hash / UUID 重新命名**:

```java
String sha = DigestUtils.md5DigestAsHex(file.getInputStream());
String name = sha + "." + ext;
```

### 4. 执行权限

**攻击**: 上传 `shell.jsp` 到 webapp 目录, 然后访问 `/uploads/shell.jsp?cmd=ls /` → 远程执行任意命令。

**防护**:

- 上传目录放在 **webroot 之外**, 通过专门接口或 nginx static 提供访问
- **静态目录禁用脚本执行**:
  ```nginx
  location /uploads/ {
      location ~* \.(php|jsp|asp|aspx|sh|py)$ { deny all; }
  }
  ```
- **独立域名 / CDN**: 上传文件走 `cdn.example.com`, 即使 XSS 也不在主域, cookie 偷不到
- **mime 强制**: nginx 给上传文件强制 `Content-Type: application/octet-stream` (浏览器不解析为 HTML/JS)

### 5. 大文件 / 分片上传

**问题**: 100MB 视频, 单次上传:
- 网络抖动断了 → 重传
- 服务端内存压力大
- Tomcat / Spring 默认 multipart 限制

**方案**:

#### A. 分片上传 (前端切块, 后端拼)

```
前端: 把 100MB 文件切成 100 个 1MB chunk, 逐个 POST
后端: 收到 chunk i → 存临时目录 partN
最后一片到: 拼接 → 移到正式目录
```

```java
@PostMapping("/upload/chunk")
public Result<?> uploadChunk(@RequestParam String fileId,
                              @RequestParam int chunkIndex,
                              @RequestParam int totalChunks,
                              @RequestParam MultipartFile chunk) throws IOException {
    Path partPath = Paths.get(tempDir, fileId, "part" + chunkIndex);
    Files.createDirectories(partPath.getParent());
    chunk.transferTo(partPath);
    if (allChunksReceived(fileId, totalChunks)) {
        mergeChunks(fileId, totalChunks);
    }
    return Result.success();
}
```

#### B. 断点续传

前端先 GET `/upload/check?fileId=xxx` 看已传哪些 chunk, 只补传缺失的。

#### C. 客户端直传对象存储 (推荐)

```
1. 前端 GET /upload/presigned-url → 后端调 S3/OSS SDK 生成预签名 URL (限 5 分钟)
2. 前端用 PUT 直接传到 S3, 后端不过流量
3. 前端 POST /upload/complete 通知后端
```

**优势**: 后端不占带宽 / CPU, 用户 → S3 直传速度快。

### 6. 存储选型

| 方案 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| **本地磁盘** | 简单 | 不可水平扩展, 单点故障 | 学习 / 单机部署 |
| **NFS / NAS** | 多服务器共享 | 性能瓶颈, 故障域大 | 中小型 |
| **S3 / OSS / COS** | 高可用 / 无限扩 / CDN 一体 | 收费 / 网络依赖 | **生产首选** |
| **MinIO** | S3 兼容自建 | 运维成本 | 私有云 |
| **CDN + 对象存储** | 全球加速 / 防盗链 | 配置复杂 | 高流量 |

### 7. 防盗链 / 访问控制

**问题**: 上传后 URL 泄漏, 别人随便访问, 流量被刷。

**方案**:

- **Referer 校验**: nginx `valid_referers blog.example.com *.blog.example.com`
- **签名 URL**: 访问时带 token, 后端校验 (S3 / OSS 都原生支持)
- **私有 bucket + 临时签名**: 文件默认私有, 访问时后端生成短时签名

```java
public String generateSignedUrl(String objectKey, Duration ttl) {
    return s3.generatePresignedUrl(
        new GeneratePresignedUrlRequest(bucket, objectKey)
            .withExpiration(Date.from(Instant.now().plus(ttl)))
            .withMethod(HttpMethod.GET))
        .toString();
}
```

### 8. 图片处理

上传后端常见需求:

- **缩放**: 头像 / 列表缩略图 (200x200)
- **压缩**: JPEG quality 80
- **EXIF 清理**: 去除 GPS / 拍摄设备元信息 (隐私)
- **水印**: 防盗
- **重编码**: ImageIO 解码再编码, 自动去掉恶意 SVG 脚本 / 畸形 PNG

```java
BufferedImage img = ImageIO.read(file.getInputStream());
BufferedImage thumb = Scalr.resize(img, 200);
ImageIO.write(thumb, "jpg", outputFile);
```

### 9. 病毒扫描

**ClamAV** (开源):

```bash
clamscan upload-file.png
```

集成: 通过 TCP socket 调 clamd, 或 java-clamav 库。

生产场景: 用户论坛 / SaaS / 企业网盘, 必须扫。

## 三、工作原理

| 维度 | 要点 | 你需要能说清 |
|---|---|---|
| 入口 | `MultipartFile` / `@RequestPart` | Spring 解析 multipart 的步骤 |
| 配置 | `spring.servlet.multipart.*` | max-file-size / max-request-size / file-size-threshold / location 各自含义 |
| 执行 | 接收 → 校验 → 落盘 / 转发对象存储 → 返回 URL | 全流程异常处理 |
| 边界 | 大文件 / 假后缀 / 路径穿越 / 病毒 / 并发同名 | 每一项的防护点 |
| 验证 | curl 测试 / OWASP 文件测试套件 | 看错误码、文件实际落盘位置 |

## 四、项目落地

博客 API 的文件上传场景:

1. **用户头像**: 大小 ≤ 2MB, 仅 jpg/png/webp, 上传后裁剪到 200x200
2. **文章插图**: 大小 ≤ 5MB, 富文本编辑器调 `/api/v1/upload/image`
3. **附件下载**: 限定 ≤ 50MB, 仅 VIP 用户能用
4. **管理端 banner / 公告图**: 仅 ADMIN

## 五、常见坑

| 坑 | 后果 | 处理 |
|---|---|---|
| 信任客户端 Content-Type | `.exe` 改名 `.png` 通过 | Tika 嗅探真实类型 |
| 用原始文件名 | 路径穿越 / 覆盖系统文件 | hash / UUID 命名 |
| 上传目录在 webroot 内 | Webshell 可被执行 | 移到 webroot 外 + nginx 屏蔽脚本 |
| 没限大小 | 一个 100GB 文件打满磁盘 | `max-file-size` + 业务校验 |
| 同步阻塞 | 一个慢上传占用线程 | 异步 / 直传对象存储 |
| 不去 EXIF | 隐私泄漏 (GPS / 设备型号) | ImageIO 重编码 |
| 缩略图同步生成 | 上传慢 | 异步 (MQ + worker) |
| 重复上传不去重 | 浪费存储 | 用 hash 命名, 已存在则跳过 |
| 无病毒扫描 | 用户 / 客户中招 | ClamAV |
| Cookie 同域 | 上传 HTML 可偷 cookie | CDN 独立域 |

## 六、面试高频问题

1. 一个生产文件上传接口需要做哪些安全校验?
2. 怎么防止用户上传 Webshell?
3. 大文件 100MB 上传, 服务端怎么处理才不内存爆?
4. 分片上传的实现思路, 怎么做断点续传?
5. 客户端直传 S3 是什么流程? 后端做什么?
6. 上传后的图片, 你会在服务端做哪些处理?
7. 上传接口被刷流量怎么办?
8. 用 hash 命名文件, 怎么处理同 hash 不同图片?
9. 公司图床防盗链, 怎么做?
10. 上传过的图片用户改文件再上传, 怎么判定是否同一张?

## 七、决策表: 上传场景 → 方案

| 场景 | 推荐方案 |
|---|---|
| 头像 (< 2MB) | 直传后端 + 服务端缩略 + S3 |
| 富文本图片 (< 5MB) | 直传后端 + S3 + 防盗链 URL |
| 附件 (< 50MB) | 后端代理 + S3 + 病毒扫 |
| 视频 (> 100MB) | 客户端直传 S3 + 转码 (云服务) |
| 大文件批量 (> 1GB) | 分片上传 + S3 multipart |
| 多媒体直播 | RTMP / WebRTC |
