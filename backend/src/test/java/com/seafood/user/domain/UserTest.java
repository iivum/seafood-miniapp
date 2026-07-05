package com.seafood.user.domain;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private final Instant t0 = Instant.parse("2026-06-01T00:00:00Z");

    private User sample() {
        return new User("u1", "open-1", "nick", "http://a", Role.CUSTOMER,
                "13900000000", List.of(), t0);
    }

    private Address addr(String id, String detail, boolean def) {
        return new Address(id, "张三", "13900000000", "上海市", "上海市", "某区", detail, def);
    }

    @Test
    void constructor_rejectsNullRole() {
        assertThatThrownBy(() -> new User("u1", "open-1", "n", "u", null, null, List.of(), t0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("role");
    }

    @Test
    void constructor_rejectsBlankOpenId() {
        assertThatThrownBy(() -> new User("u1", " ", "n", "u", Role.CUSTOMER, null, List.of(), t0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("openId");
    }

    @Test
    void addAddress_firstBecomesDefault() {
        User u = sample().addAddress(addr(null, "地址1", false));
        assertThat(u.addresses()).hasSize(1);
        assertThat(u.addresses().get(0).isDefault()).isTrue();
    }

    @Test
    void addAddress_defaultFlagClearsPreviousDefault() {
        User u = sample()
                .addAddress(addr(null, "A", true))
                .addAddress(addr(null, "B", true));
        assertThat(u.addresses()).hasSize(2);
        assertThat(u.addresses().get(0).isDefault()).isFalse();
        assertThat(u.addresses().get(1).isDefault()).isTrue();
    }

    @Test
    void updateAddress_mergesPartialFields() {
        User u = sample().addAddress(addr("a1", "old", true));
        Address patch = new Address("a1", null, null, "北京市", "北京市", "新区", "新地址", false);
        User updated = u.updateAddress("a1", patch);
        Address a = updated.addresses().get(0);
        assertThat(a.name()).isEqualTo("张三");
        assertThat(a.province()).isEqualTo("北京市");
        assertThat(a.detail()).isEqualTo("新地址");
    }

    @Test
    void updateAddress_unknown_throws() {
        User u = sample();
        assertThatThrownBy(() -> u.updateAddress("nope",
                new Address("nope", "x", "x", "x", "x", "x", "x", false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeAddress_unknown_throws() {
        assertThatThrownBy(() -> sample().removeAddress("nope"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void setDefault_clearsPreviousDefault() {
        User u = sample()
                .addAddress(addr("a1", "A", true))
                .addAddress(addr("a2", "B", false))
                .setDefaultAddress("a2");
        assertThat(u.addresses()).extracting(Address::isDefault).containsExactly(false, true);
    }

    @Test
    void isAdmin_andIsCustomer() {
        assertThat(new User("a", "o", "n", "u", Role.ADMIN, null, List.of(), t0).isAdmin()).isTrue();
        assertThat(sample().isCustomer()).isTrue();
    }
}
