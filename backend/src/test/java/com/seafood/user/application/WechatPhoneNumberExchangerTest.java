package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatPhoneNumberExchangerTest {

    private MockRestServiceServer server;
    private WechatPhoneNumberExchanger exchanger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        server = MockRestServiceServer.bindTo(builder).build();
        exchanger = new WechatPhoneNumberExchanger(builder.build());
        ReflectionTestUtils.setField(exchanger, "appid", "wxapp");
        ReflectionTestUtils.setField(exchanger, "secret", "wxsecret");
        ReflectionTestUtils.setField(exchanger, "phoneBindingEnabled", true);
    }

    @Test
    void exchange_devMode_devPrefixedCode_returnsDeterministicPhone() {
        ReflectionTestUtils.setField(exchanger, "enabled", false);

        String phone1 = exchanger.exchange("dev-abc");
        String phone2 = exchanger.exchange("dev-abc");
        String phoneOther = exchanger.exchange("dev-xyz");

        assertThat(phone1).isEqualTo(phone2);
        assertThat(phone1).isNotEqualTo(phoneOther);
        assertThat(phone1).matches("^1\\d{10}$");
    }

    @Test
    void exchange_devMode_nonDevCode_throws() {
        ReflectionTestUtils.setField(exchanger, "enabled", false);

        assertThatThrownBy(() -> exchanger.exchange("real-code-from-wx"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void exchange_prodMode_success_exchangesAccessTokenThenPhone() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-1"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"phone_info\":{\"phoneNumber\":\"13800001111\"}}",
                        MediaType.APPLICATION_JSON));

        String phone = exchanger.exchange("real-code");

        assertThat(phone).isEqualTo("13800001111");
        server.verify();
    }

    @Test
    void exchange_prodMode_accessTokenCachedAcrossCalls() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-cached\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-cached"))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"phone_info\":{\"phoneNumber\":\"13800002222\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-cached"))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"phone_info\":{\"phoneNumber\":\"13800003333\"}}",
                        MediaType.APPLICATION_JSON));

        exchanger.exchange("code-1");
        exchanger.exchange("code-2");

        // access_token 只换取一次(第二次调用命中缓存,不再打 /cgi-bin/token)
        server.verify();
    }

    @Test
    void exchange_prodMode_accessTokenFetchFails_throws() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> exchanger.exchange("real-code"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void exchange_prodMode_phoneBindingDisabled_throwsWithoutCallingWechat() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);
        ReflectionTestUtils.setField(exchanger, "phoneBindingEnabled", false);

        assertThatThrownBy(() -> exchanger.exchange("real-code"))
                .isInstanceOf(DomainException.class);
        // 未开放时不应发起任何微信请求(server 没有注册任何 expectation,若发起会报未匹配请求)
    }

    @Test
    void exchange_prodMode_phoneExchangeFails_throws() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-2\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-2"))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> exchanger.exchange("bad-code"))
                .isInstanceOf(DomainException.class);
        // 非 token 失效类错误码(40029=invalid code)不应触发重试——只打了 1 次 token + 1 次换号
        server.verify();
    }

    @Test
    void exchange_prodMode_accessTokenNonStringValue_throwsDomainExceptionNotClassCast() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":12345,\"expires_in\":7200}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> exchanger.exchange("real-code"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void exchange_prodMode_phoneNumberNonStringValue_throwsDomainExceptionNotClassCast() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-x\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-x"))
                .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"phoneNumber\":12345}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> exchanger.exchange("real-code"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void exchange_prodMode_wechatRejectsCachedToken_retriesOnceWithFreshToken() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-stale\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-stale"))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-fresh\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-fresh"))
                .andRespond(withSuccess(
                        "{\"errcode\":0,\"errmsg\":\"ok\",\"phone_info\":{\"phoneNumber\":\"13800004444\"}}",
                        MediaType.APPLICATION_JSON));

        String phone = exchanger.exchange("real-code");

        assertThat(phone).isEqualTo("13800004444");
        server.verify();
    }

    @Test
    void exchange_prodMode_wechatRejectsFreshTokenToo_throwsWithoutInfiniteRetry() {
        ReflectionTestUtils.setField(exchanger, "enabled", true);

        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-a\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-a"))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wxapp&secret=wxsecret"))
                .andRespond(withSuccess("{\"access_token\":\"tok-b\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=tok-b"))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> exchanger.exchange("real-code"))
                .isInstanceOf(DomainException.class);
        // 恰好 2 次 token + 2 次换号(重试 1 次后放弃,不无限重试)
        server.verify();
    }

    @Test
    void phoneFromHash_integerMinValue_neverProducesNegativeOrMalformedDigits() {
        String phone = WechatPhoneNumberExchanger.phoneFromHash(Integer.MIN_VALUE);
        assertThat(phone).matches("^1\\d{10}$");
    }
}
