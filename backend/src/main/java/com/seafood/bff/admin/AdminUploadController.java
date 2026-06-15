package com.seafood.bff.admin;

import com.seafood.bff.admin.dto.UploadResponse;
import com.seafood.shared.error.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 路线图 3.6 后端图片上传端点(ad-04 商品表单多图上传 + 主图标记基础)。
 *
 * <p>设计要点:
 * <ul>
 *   <li>POST /api/admin/uploads(ADMIN only)接 multipart/form-data,files: MultipartFile[]</li>
 *   <li>写本地磁盘 {upload.dir}/yyyy/MM/{uuid}.ext(默认 ${SEAFOOD_UPLOAD_DIR:./var/seafood/uploads},Spring ${seafood.upload.dir})</li>
 *   <li>静态文件经 /api/static/uploads/** 暴露(参见 {@code StaticResourceConfig.addResourceHandlers})</li>
 *   <li>安全:mime 白名单 image/jpeg+png+webp,size 5MB/单,filename 强制重写(UUID)防 path 穿越</li>
 *   <li>OSS/S3 决策:本迭代写本地磁盘 + 注入 upload.dir,Sprint 4 切 OSS 时只换 save 逻辑,响应 shape 不变</li>
 * </ul>
 *
 * <p>v2 视觉 5.14-5.17:路径同源 — 写盘(本类)与读盘(StaticResourceConfig)
 * 都 @Value 注入 {@code seafood.upload.dir}。启动期 fail-fast 校验
 * 抽到 {@link UploadWritabilityChecker}(独立 component,不带 @PreAuthorize,
 * 避免启动期 ApplicationReadyEvent 触发时被类级 hasRole 拦截抛
 * AuthenticationCredentialsNotFoundException)。docker-compose 加
 * {@code seafood_uploads} named volume 持久化(5.14)。
 */
@RestController
@RequestMapping("/api/admin/uploads")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter
            .ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);
    private static final Logger log = LoggerFactory.getLogger(AdminUploadController.class);

    private final Path uploadRoot;

    public AdminUploadController(@Value("${seafood.upload.dir:./var/seafood/uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadRoot, e);
        }
        log.info("AdminUploadController initialized, uploadRoot={}", uploadRoot);
    }

    /**
     * 多文件上传。返回每文件的 url + size + mime;UI 拿到后塞 Product.images[] 数组。
     */
    @PostMapping
    public ResponseEntity<UploadResponse> upload(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new DomainException("至少上传 1 个文件");
        }
        if (files.length > 9) {
            throw new DomainException("单次最多上传 9 个文件");
        }
        // 按 yyyy/MM 分目录(UTC,简化时区;Sprint 4 接 OSS 时改 region 本地时间)
        String subDir = YYYY_MM.format(Instant.now());
        Path dir = uploadRoot.resolve(subDir).normalize();
        if (!dir.startsWith(uploadRoot)) {
            // 防止路径穿越(即便 yyyy/MM 是固定串也防一手)
            throw new DomainException("非法路径");
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建子目录: " + dir, e);
        }

        List<UploadResponse.UploadedFile> uploaded = new ArrayList<>(files.length);
        for (MultipartFile f : files) {
            if (f.isEmpty()) {
                throw new DomainException("文件为空:" + f.getOriginalFilename());
            }
            if (f.getSize() > MAX_FILE_SIZE) {
                throw new DomainException("文件超过 5MB 上限:" + f.getOriginalFilename());
            }
            String mime = f.getContentType();
            if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase())) {
                throw new DomainException("不支持的图片类型:" + mime
                        + ";仅允许 jpeg/png/webp");
            }
            String ext = extFromMime(mime);
            String name = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(name);
            try {
                f.transferTo(target);
            } catch (IOException e) {
                throw new IllegalStateException("写入文件失败:" + target, e);
            }
            String url = "/api/static/uploads/" + subDir + "/" + name;
            uploaded.add(new UploadResponse.UploadedFile(url, f.getSize(), mime));
        }
        return ResponseEntity.ok(new UploadResponse(uploaded));
    }

    private static String extFromMime(String mime) {
        return switch (mime.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "bin"; // 不会到达(白名单已守)
        };
    }
}
