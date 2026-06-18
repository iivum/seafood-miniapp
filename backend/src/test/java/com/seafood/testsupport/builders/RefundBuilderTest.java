package com.seafood.testsupport.builders;

import com.seafood.order.domain.Refund;
import com.seafood.order.domain.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundBuilderTest {

    @Test
    void defaultBuild_returnsRequestedRefund() {
        Refund r = RefundBuilder.aRefund().build();
        assertThat(r.id()).isEqualTo("r-test");
        assertThat(r.orderId()).isEqualTo("o-test");
        assertThat(r.amount()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(r.reason()).isEqualTo("不再需要");
        assertThat(r.status()).isInstanceOf(RefundStatus.Requested.class);
    }

    @Test
    void withStatus_overridesStatus() {
        Refund r = RefundBuilder.aRefund().withStatus(new RefundStatus.Approved()).build();
        assertThat(r.status()).isInstanceOf(RefundStatus.Approved.class);
    }

    @Test
    void withAmount_overridesAmount() {
        Refund r = RefundBuilder.aRefund().withAmount(new BigDecimal("288.00")).build();
        assertThat(r.amount()).isEqualByComparingTo("288.00");
    }
}
