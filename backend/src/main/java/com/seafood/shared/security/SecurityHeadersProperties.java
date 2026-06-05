package com.seafood.shared.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP 安全响应头基线配置(Sprint 2 §2.1,specs/runtime-security §HTTP responses carry
 * baseline security headers,design §5 decision 5)。
 *
 * <p>每个字段一个 HTTP 头。默认值对所有部署足够安全,允许在
 * {@code application.yml} 中按环境收紧(例如 prod 给 {@code report-uri})。
 *
 * <p>本类只持有头字符串,实际写入由 {@link SecurityHeadersFilter} 完成;这样做的目的
 * 是确保 ArchUnit 能强制「唯一写入位置 = SecurityHeadersFilter」规则(2.3)。
 */
@ConfigurationProperties(prefix = "security.headers")
@Validated
public class SecurityHeadersProperties {

    /** 1 年 HSTS,所有子域。 */
    @NotBlank
    private String strictTransportSecurity = "max-age=31536000; includeSubDomains";

    /** 阻止 MIME 嗅探。 */
    @NotBlank
    private String xContentTypeOptions = "nosniff";

    /** 拒绝 iframe 嵌入。 */
    @NotBlank
    private String xFrameOptions = "DENY";

    /** 跨源只发 origin,不泄漏 path。 */
    @NotBlank
    private String referrerPolicy = "strict-origin-when-cross-origin";

    /** 显式禁用常见传感器能力(geolocation/microphone/camera)。 */
    @NotBlank
    private String permissionsPolicy = "geolocation=(), microphone=(), camera=()";

    /**
     * CSP:允许自托管 JS + 内联 style(Element Plus 运行时)。
     * image 允许 https: + data: 因为商品图可能外链。
     * 收紧到 nonce 需 admin-ui 配合(§9 重构),故此处保守。
     */
    @NotBlank
    private String contentSecurityPolicy =
            "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; script-src 'self'";

    public String getStrictTransportSecurity() { return strictTransportSecurity; }
    public void setStrictTransportSecurity(String v) { this.strictTransportSecurity = v; }

    public String getXContentTypeOptions() { return xContentTypeOptions; }
    public void setXContentTypeOptions(String v) { this.xContentTypeOptions = v; }

    public String getXFrameOptions() { return xFrameOptions; }
    public void setXFrameOptions(String v) { this.xFrameOptions = v; }

    public String getReferrerPolicy() { return referrerPolicy; }
    public void setReferrerPolicy(String v) { this.referrerPolicy = v; }

    public String getPermissionsPolicy() { return permissionsPolicy; }
    public void setPermissionsPolicy(String v) { this.permissionsPolicy = v; }

    public String getContentSecurityPolicy() { return contentSecurityPolicy; }
    public void setContentSecurityPolicy(String v) { this.contentSecurityPolicy = v; }
}
