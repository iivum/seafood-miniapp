package com.seafood.user.api.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AddressUpsertRequest#toAddRequest()}/{@link AddressUpsertRequest#toUpdateRequest()}
 * 契约(design.md D4):{@code district} 直接透传给 {@link AddAddressRequest}/
 * {@link UpdateAddressRequest},不再折进 {@code detail};{@code detailAddress}
 * 仍然照旧映射到 domain 的 {@code detail}(mp 请求字段名与 domain 字段名不同,不受影响)。
 *
 * <p>替换此前记录"折叠有损"这一 workaround 的旧断言(该断言曾经故意锁住折叠行为,
 * 现在这个 workaround 已被移除 —— 见 {@link AddressUpsertRequest} 类注释历史)。
 */
class AddressUpsertRequestTest {

    private static AddressUpsertRequest mpBody() {
        return new AddressUpsertRequest("张三", "13800000000", "广东省", "深圳市",
                "南山区", "科技园1号", true);
    }

    @Test
    void toAddRequest_passesDistrictThrough_withoutFoldingIntoDetail() {
        AddAddressRequest req = mpBody().toAddRequest();

        assertThat(req.district()).isEqualTo("南山区");
        assertThat(req.detail()).isEqualTo("科技园1号");
    }

    @Test
    void toUpdateRequest_passesDistrictThrough_withoutFoldingIntoDetail() {
        UpdateAddressRequest req = mpBody().toUpdateRequest();

        assertThat(req.district()).isEqualTo("南山区");
        assertThat(req.detail()).isEqualTo("科技园1号");
    }

    @Test
    void toAddRequest_blankDistrict_passesThroughAsIs_noLongerFoldedAway() {
        AddressUpsertRequest body = new AddressUpsertRequest("张三", "13800000000",
                "广东省", "深圳市", null, "科技园1号", true);

        AddAddressRequest req = body.toAddRequest();

        assertThat(req.district()).isNull();
        assertThat(req.detail()).isEqualTo("科技园1号");
    }

    @Test
    void toAddRequest_copiesOtherFieldsUnchanged() {
        AddAddressRequest req = mpBody().toAddRequest();

        assertThat(req.name()).isEqualTo("张三");
        assertThat(req.phone()).isEqualTo("13800000000");
        assertThat(req.province()).isEqualTo("广东省");
        assertThat(req.city()).isEqualTo("深圳市");
        assertThat(req.isDefault()).isTrue();
    }
}
