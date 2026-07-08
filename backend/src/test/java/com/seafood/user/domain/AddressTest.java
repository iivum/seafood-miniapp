package com.seafood.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Address} record 携带 {@code district} 字段的构造/getter 断言(design.md D4)。
 *
 * <p>字段顺序 id/name/phone/province/city/district/detail/isDefault,与
 * {@code openspec/specs/address-management/spec.md:17} 已批准的字段顺序一致。
 */
class AddressTest {

    @Test
    void constructor_exposesDistrictAccessor() {
        Address address = new Address("addr-001", "张三", "13900000000",
                "上海市", "浦东新区", "陆家嘴街道", "世纪大道 1 号", true);

        assertThat(address.district()).isEqualTo("陆家嘴街道");
    }

    @Test
    void constructor_allowsNullDistrict_forLegacyDataWithoutTheField() {
        Address address = new Address("addr-001", "张三", "13900000000",
                "上海市", "浦东新区", null, "世纪大道 1 号", true);

        assertThat(address.district()).isNull();
    }

    @Test
    void constructor_keepsOtherAccessorsUnaffectedByNewField() {
        Address address = new Address("addr-001", "张三", "13900000000",
                "上海市", "浦东新区", "陆家嘴街道", "世纪大道 1 号", true);

        assertThat(address.id()).isEqualTo("addr-001");
        assertThat(address.name()).isEqualTo("张三");
        assertThat(address.phone()).isEqualTo("13900000000");
        assertThat(address.province()).isEqualTo("上海市");
        assertThat(address.city()).isEqualTo("浦东新区");
        assertThat(address.detail()).isEqualTo("世纪大道 1 号");
        assertThat(address.isDefault()).isTrue();
    }
}
