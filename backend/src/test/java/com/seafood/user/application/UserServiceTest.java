package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import com.seafood.shared.error.NotFoundException;
import com.seafood.shared.security.Role;
import com.seafood.shared.security.UserPrincipal;
import com.seafood.user.api.dto.AddAddressRequest;
import com.seafood.user.api.dto.UpdateAddressRequest;
import com.seafood.user.api.dto.UserResponse;
import com.seafood.user.domain.Address;
import com.seafood.user.domain.User;
import com.seafood.user.infra.UserDocument;
import com.seafood.user.infra.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository repo;
    private UserService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        service = new UserService(repo);
    }

    private UserPrincipal me(String id, Role role) {
        return new UserPrincipal(id, role);
    }

    private UserDocument docOf(String id, Role role) {
        UserDocument d = new UserDocument();
        d.setId(id);
        d.setOpenId("open-" + id);
        d.setNickname("nick");
        d.setRole(role.name());
        d.setCreatedAt(Instant.now());
        d.setAddresses(List.of());
        return d;
    }

    @Test
    void get_self_succeeds() {
        when(repo.findById("u1")).thenReturn(Optional.of(docOf("u1", Role.CUSTOMER)));
        UserResponse res = service.get("u1", me("u1", Role.CUSTOMER));
        assertThat(res.id()).isEqualTo("u1");
    }

    @Test
    void get_otherCustomer_denied() {
        when(repo.findById("u2")).thenReturn(Optional.of(docOf("u2", Role.CUSTOMER)));
        assertThatThrownBy(() -> service.get("u2", me("u1", Role.CUSTOMER)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void get_otherAsAdmin_succeeds() {
        when(repo.findById("u1")).thenReturn(Optional.of(docOf("u1", Role.CUSTOMER)));
        UserResponse res = service.get("u1", me("admin", Role.ADMIN));
        assertThat(res.id()).isEqualTo("u1");
    }

    @Test
    void get_missing_throws() {
        when(repo.findById("u1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("u1", me("u1", Role.CUSTOMER)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addAddress_appendsAndPersists() {
        when(repo.findById("u1")).thenReturn(Optional.of(docOf("u1", Role.CUSTOMER)));
        when(repo.save(any(UserDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        AddAddressRequest req = new AddAddressRequest("张三", "13900000000",
                "上海市", "上海市", "浦东新区", "世纪大道 1 号", true);
        UserResponse res = service.addAddress("u1", req, me("u1", Role.CUSTOMER));

        assertThat(res.addresses()).hasSize(1);
        assertThat(res.addresses().get(0).detail()).isEqualTo("世纪大道 1 号");
        assertThat(res.addresses().get(0).isDefault()).isTrue();
    }

    @Test
    void addAddress_and_updateAddress_roundTripDistrict() {
        // addAddress:传入的 district 原样出现在返回的 Address 上(design.md D4)
        when(repo.findById("u1")).thenReturn(Optional.of(docOf("u1", Role.CUSTOMER)));
        when(repo.save(any(UserDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        AddAddressRequest addReq = new AddAddressRequest("张三", "13900000000",
                "上海市", "浦东新区", "陆家嘴街道", "世纪大道 1 号", true);
        UserResponse afterAdd = service.addAddress("u1", addReq, me("u1", Role.CUSTOMER));
        Address added = afterAdd.addresses().get(0);
        assertThat(added.district()).isEqualTo("陆家嘴街道");

        // updateAddress:回读时能重新回填(regression:此前折叠进 detail 导致地区选择器
        // 无法回填,见 design.md D4 与已删除的 AddressUpsertRequest#foldedDetail())
        UserDocument doc = docOf("u1", Role.CUSTOMER);
        doc.setAddresses(List.of(added));
        when(repo.findById("u1")).thenReturn(Optional.of(doc));

        UpdateAddressRequest updateReq = new UpdateAddressRequest(
                null, null, null, null, "张江镇", null, false);
        UserResponse afterUpdate = service.updateAddress("u1", added.id(), updateReq, me("u1", Role.CUSTOMER));
        assertThat(afterUpdate.addresses().get(0).district()).isEqualTo("张江镇");
    }

    @Test
    void addAddress_otherUser_denied() {
        assertThatThrownBy(() -> service.addAddress("u2",
                new AddAddressRequest("x", "x", "x", "x", "x", "x", false),
                me("u1", Role.CUSTOMER)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void removeAddress_succeeds() {
        UserDocument doc = docOf("u1", Role.CUSTOMER);
        doc.setAddresses(List.of(new Address("a1", "张三", "13900000000",
                "上海市", "上海市", "某区", "某处", true)));
        when(repo.findById("u1")).thenReturn(Optional.of(doc));
        when(repo.save(any(UserDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = service.removeAddress("u1", "a1", me("u1", Role.CUSTOMER));
        assertThat(res.addresses()).isEmpty();
    }

    @Test
    void updateAddress_mergesPartial() {
        UserDocument doc = docOf("u1", Role.CUSTOMER);
        doc.setAddresses(List.of(new Address("a1", "张三", "13900000000",
                "上海市", "上海市", "旧区", "旧地址", true)));
        when(repo.findById("u1")).thenReturn(Optional.of(doc));
        when(repo.save(any(UserDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAddressRequest req = new UpdateAddressRequest(
                null, null, "北京市", "北京市", null, "新地址", false);
        UserResponse res = service.updateAddress("u1", "a1", req, me("u1", Role.CUSTOMER));

        Address a = res.addresses().get(0);
        assertThat(a.province()).isEqualTo("北京市");
        assertThat(a.detail()).isEqualTo("新地址");
        assertThat(a.isDefault()).isTrue(); // 原 default 保留
    }

    @Test
    void list_asCustomer_denied() {
        assertThatThrownBy(() -> service.list(org.springframework.data.domain.PageRequest.of(0, 20),
                me("u1", Role.CUSTOMER)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("仅管理员");
    }

    @Test
    void list_asAdmin_returnsAll() {
        when(repo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        var page = service.list(org.springframework.data.domain.PageRequest.of(0, 20),
                me("admin", Role.ADMIN));
        assertThat(page.getContent()).isEmpty();
    }
}
