package com.seafood.bff.admin;

import com.seafood.shared.error.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * v2 视觉 5.16 启动期 fail-fast 校验 — 抽到独立 component,不带
 * {@code @PreAuthorize}({@code AdminUploadController} 类有
 * {@code @PreAuthorize("hasRole('ADMIN')")},如果
 * {@link EventListener} 方法留在 controller,启动期 ApplicationReadyEvent
 * 触发时会因无 SecurityContext 抛 AuthenticationCredentialsNotFoundException,
 * 整 app crash)。
 *
 * <p>职责:确认 {@code seafood.upload.dir} 真实可写。失败抛
 * {@link IllegalStateException},Spring 不会 emit ready 事件,k8s
 * readinessProbe 不通过 → pod 不接流量(比"静默到第一次上传才报错"
 * 安全得多)。
 */
@Component
public class UploadWritabilityChecker {

    private static final Logger log = LoggerFactory.getLogger(UploadWritabilityChecker.class);

    private final Path uploadRoot;

    public UploadWritabilityChecker(@Value("${seafood.upload.dir:./var/seafood/uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadRoot, e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!Files.isDirectory(uploadRoot)) {
            throw new IllegalStateException(
                    "上传根目录不存在: " + uploadRoot + " (检查 docker-compose volume 挂载 / SEAFOOD_UPLOAD_DIR)");
        }
        if (!Files.isWritable(uploadRoot)) {
            throw new IllegalStateException(
                    "上传根目录不可写: " + uploadRoot + " (检查文件系统权限 / 容器用户 UID)");
        }
        log.info("Upload root verified writable: {}", uploadRoot);
    }

    /**
     * 测试用 accessor(让 AdminUploadControllerTest 可以反射拿 uploadRoot
     * 做路径同源断言)。
     */
    public Path uploadRoot() {
        return uploadRoot;
    }
}
