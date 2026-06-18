package com.seafood.testsupport.builders;

import com.seafood.user.domain.Address;
import com.seafood.shared.security.Role;
import com.seafood.user.domain.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserBuilderTest {

    @Test
    void defaultBuild_returnsCustomerUser() {
        User u = UserBuilder.aUser().build();
        assertThat(u.id()).isEqualTo("u-test");
        assertThat(u.openId()).isEqualTo("dev-open-test");
        assertThat(u.nickname()).isEqualTo("测试用户");
        assertThat(u.role()).isEqualTo(Role.CUSTOMER);
        assertThat(u.addresses()).isEmpty();
    }

    @Test
    void withRole_overridesRole() {
        User u = UserBuilder.aUser().withRole(Role.ADMIN).build();
        assertThat(u.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void withAddresses_addsAddresses() {
        Address addr = new Address(null, "张三", "13800000000",
            "福建", "厦门", "思明区软件园", true);
        User u = UserBuilder.aUser().withAddresses(List.of(addr)).build();
        assertThat(u.addresses()).hasSize(1);
        assertThat(u.addresses().get(0).name()).isEqualTo("张三");
    }

    @Test
    void withOpenId_overridesOpenId() {
        User u = UserBuilder.aUser().withOpenId("dev-real-openid").build();
        assertThat(u.openId()).isEqualTo("dev-real-openid");
    }
}