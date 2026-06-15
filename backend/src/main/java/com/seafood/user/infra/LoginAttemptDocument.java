package com.seafood.user.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * sprint-1-closure 2.2 — 登录尝试追踪文档(供 IP+account 双维度锁使用)。
 *
 * <p>TTL index 900s(15 分钟)由 {@code MongoIndexInitializer} 显式创建 —
 * 不依赖 Spring Data auto-index-creation(CLAUDE.md 单仓坑点:无显式建索引,
 * 启动期 {@code MongoIndexInitializer} 报 NPE / 失败)。{@code @Indexed(expireAfterSeconds=...)}
 * 在 Spring Data Mongo 4.x 已 deprecated,改在 {@code MongoIndexInitializer} 集中
 * 用 {@code IndexOperations.ensureIndex(...expireAfter(...))} 建 TTL。
 *
 * <p>典型查询模式:
 * <ul>
 *   <li>IP 锁:同 {@code ip} + {@code success=false} + 最近 15 分钟内计数 ≥ 3</li>
 *   <li>Account 锁:同 {@code account} + {@code success=false} + 最近 15 分钟内计数 ≥ 3</li>
 * </ul>
 */
@Document(collection = "login_attempts")
@CompoundIndex(name = "ip_ts", def = "{'ip': 1, 'ts': -1}")
@CompoundIndex(name = "account_ts", def = "{'account': 1, 'ts': -1}")
public class LoginAttemptDocument {

    @Id
    private String id;

    @Field("ip")
    private String ip;

    @Field("account")
    private String account;

    @Field("success")
    private boolean success;

    @Field("ts")
    private Instant ts;

    public LoginAttemptDocument() {}

    public LoginAttemptDocument(String ip, String account, boolean success, Instant ts) {
        this.ip = ip;
        this.account = account;
        this.success = success;
        this.ts = ts;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
}
