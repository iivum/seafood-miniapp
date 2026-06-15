package com.seafood.bff.admin;

import com.seafood.shared.config.StaticResourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * v2 视觉 5.14-5.17 — 路径同源 + 启动期 writability fail-fast 单测。
 *
 * <p>5.16 E2E 修复(2026-06-14):writability 校验从 {@code AdminUploadController}
 * 抽到独立 {@link UploadWritabilityChecker} component。理由:controller 类
 * 有 {@code @PreAuthorize("hasRole('ADMIN')")},如果
 * {@code @EventListener(ApplicationReadyEvent)} 方法留在 controller,
 * 启动期 Spring 会用类级 @PreAuthorize 校验,因无 SecurityContext 抛
 * AuthenticationCredentialsNotFoundException,整 app crash。
 */
class UploadWritabilityCheckerTest {

    @Test
    void pathConsistency_writerAndReaderResolveSameAbsoluteDirectory(@TempDir Path tempDir) throws Exception {
        // Given: 同一上传目录配置
        String uploadDir = tempDir.resolve("uploads").toString();

        // When: 三个组件各自解析
        UploadWritabilityChecker checker = new UploadWritabilityChecker(uploadDir);
        AdminUploadController writer = new AdminUploadController(uploadDir);
        StaticResourceConfig reader = new StaticResourceConfig(uploadDir);

        // Then: 三者路径(去掉 file: 前缀后)必须相等
        Path checkerRoot = checker.uploadRoot();
        Path writerRoot = (Path) ReflectionTestUtils.getField(writer, "uploadRoot");
        String readerLocation = (String) ReflectionTestUtils.getField(reader, "uploadsLocation");
        String readerPath = readerLocation.replaceFirst("^file:", "").replaceAll("/$", "");

        assertThat(checkerRoot.toString())
                .as("checker 解析路径")
                .isEqualTo(writerRoot.toString());
        assertThat(writerRoot.toString())
                .as("写盘(AdminUploadController)与读盘(StaticResourceConfig)必须解析到同一绝对路径")
                .isEqualTo(readerPath);
    }

    @Test
    void onApplicationReady_passesWhenDirectoryIsWritable(@TempDir Path tempDir) {
        UploadWritabilityChecker checker = new UploadWritabilityChecker(tempDir.toString());

        assertThatCode(checker::onApplicationReady)
                .as("目录存在且可写时不应抛异常")
                .doesNotThrowAnyException();
    }

    @Test
    void onApplicationReady_failsWhenDirectoryDoesNotExist(@TempDir Path tempDir) {
        Path nonexistent = tempDir.resolve("does-not-exist");
        UploadWritabilityChecker checker = new UploadWritabilityChecker(nonexistent.toString());
        // 删掉构造器创建的目录
        assertThat(nonexistent.toFile().delete()).isTrue();

        assertThatThrownBy(checker::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上传根目录不存在")
                .hasMessageContaining(nonexistent.toAbsolutePath().toString());
    }

    @Test
    void onApplicationReady_failsWhenDirectoryIsNotWritable(@TempDir Path tempDir) throws Exception {
        // 跳过 Windows / CI 不支持 POSIX 权限的环境
        try {
            Files.setPosixFilePermissions(tempDir, EnumSet.noneOf(PosixFilePermission.class));
        } catch (UnsupportedOperationException e) {
            return; // Windows / 非 POSIX FS:skip
        }
        // 验证确实不可写
        Set<PosixFilePermission> perms;
        try {
            perms = Files.getPosixFilePermissions(tempDir);
        } catch (Exception e) {
            return;
        }
        assumeFalse(
                perms.contains(PosixFilePermission.OWNER_WRITE),
                "本环境无法移除 OWNER_WRITE,跳过");

        UploadWritabilityChecker checker = new UploadWritabilityChecker(tempDir.toString());

        assertThatThrownBy(checker::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上传根目录不可写");
    }
}
