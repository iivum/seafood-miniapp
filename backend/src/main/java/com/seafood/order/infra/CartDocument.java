package com.seafood.order.infra;

import com.seafood.order.domain.CartItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** carts collection — _id = userId(参见 design.md §6.1)。 */
@Document(collection = "carts")
public class CartDocument {

    @Id
    private String userId;

    private List<CartItem> items;
    private Instant updatedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
