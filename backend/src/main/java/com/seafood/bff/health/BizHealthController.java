package com.seafood.bff.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务端口 8080 上的 /actuator/health 兼容端点(2026-06-14 引入)。
 *
 * <p>设计意图:Sprint 2 §2.7.5 known limitation 2 — 业务端口 8080 在
 * management context 独立后,8080 context 没有 actuator handler,
 * 命中 `/actuator/**` 应返 404。但实际生产环境很多外部探针(ALB
 * health check / k8s livenessProbe on port 8080)期望 200 而不是 404。
 * 本 controller 在 8080 业务 context 上代理 health 端点,**快速返 UP**
 * (不查 MongoDB,避免 30s serverSelectionTimeout 拖死探针);完整
 * actuator/health 在 management 端口 9090 暴露(metrics / details /
 * 内部探针)。
 *
 * <p>本端点只服务 200 + 业务级 UP 状态;真实业务依赖健康度由
 * 9090/actuator/health 的 mongo / diskSpace / ping 等 indicators 守。
 */
@RestController
public class BizHealthController {

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        return ResponseEntity.ok(body);
    }
}
