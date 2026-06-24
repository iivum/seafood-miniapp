package com.seafood.featureflag.infra;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * feature_flag_audits collection 文档映射（append-only，仅 getters，无 setters）。
 */
@Document(collection = "feature_flag_audits")
public class FeatureFlagAuditDocument {

    @Id
    private String id;

    private String flagKey;

    private String action;

    private Object before;

    private Object after;

    private String actor;

    private Instant timestamp;

    /** 全参数构造器，供 Service 写入审计记录使用。 */
    public FeatureFlagAuditDocument(String flagKey, String action, Object before, Object after,
                                    String actor, Instant timestamp) {
        this.flagKey = flagKey;
        this.action = action;
        this.before = before;
        this.after = after;
        this.actor = actor;
        this.timestamp = timestamp;
    }

    /** Spring Data 反序列化用默认构造器。 */
    public FeatureFlagAuditDocument() {}

    public String getId() { return id; }

    public String getFlagKey() { return flagKey; }

    public String getAction() { return action; }

    public Object getBefore() { return before; }

    public Object getAfter() { return after; }

    public String getActor() { return actor; }

    public Instant getTimestamp() { return timestamp; }
}
