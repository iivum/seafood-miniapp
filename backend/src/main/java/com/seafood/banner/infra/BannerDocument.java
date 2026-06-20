package com.seafood.banner.infra;

import com.seafood.banner.domain.BannerStatus;
import com.seafood.banner.domain.BannerTone;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * banners 集合(参见 specs/banner-management,design §D4)。
 * sortOrder 升序索引(公共列表按序返回);tone/status 枚举默认存字符串。
 */
@Document(collection = "banners")
public class BannerDocument {

    @Id
    private String id;

    private BannerTone tone;

    private String emoji;

    private String title;

    private String subtitle;

    /** 可空:点击跳商品详情的目标商品 id(存在性由 BannerService 写入期校验)。 */
    private String targetProductId;

    @Indexed
    private int sortOrder;

    @Indexed
    private BannerStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public BannerTone getTone() { return tone; }
    public void setTone(BannerTone tone) { this.tone = tone; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getTargetProductId() { return targetProductId; }
    public void setTargetProductId(String targetProductId) { this.targetProductId = targetProductId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public BannerStatus getStatus() { return status; }
    public void setStatus(BannerStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
