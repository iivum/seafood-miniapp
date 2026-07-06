package com.seafood.user.infra;

import com.seafood.user.domain.Address;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * users collection(参见 design.md §6.1)。
 * openId 唯一索引在 {@code MongoIndexInitializer} 启动时建。
 */
@Document(collection = "users")
public class UserDocument {

    @Id
    private String id;

    @Field("openId")
    private String openId;

    private String nickname;
    private String avatarUrl;
    private String role;        // CUSTOMER | ADMIN
    private String phone;
    private List<Address> addresses;
    private List<String> favoriteProductIds;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOpenId() { return openId; }
    public void setOpenId(String openId) { this.openId = openId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<Address> getAddresses() { return addresses; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }

    public List<String> getFavoriteProductIds() { return favoriteProductIds; }
    public void setFavoriteProductIds(List<String> favoriteProductIds) { this.favoriteProductIds = favoriteProductIds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
