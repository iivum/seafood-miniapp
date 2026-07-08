package com.seafood.user.application;

import com.seafood.shared.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 微信手机号绑定 code → 手机号(对齐 specs/backend-api §Customer phone number binding)。
 *
 * <p>真实实现:先用 {@code cgi-bin/token} 换 access_token(内存缓存,避免撞微信限流),
 * 再调 {@code wxa/business/getuserphonenumber} 用 code 换手机号。
 * 与 {@link WechatCodeExchanger} 一致的开发期约定:{@code wechat.enabled=false} 时
 * 接受 {@code dev-*} 前缀 code,派生一个确定性测试手机号,不发起真实请求。
 */
@Component
public class WechatPhoneNumberExchanger {

    private final RestClient http;

    @Value("${wechat.appid:dummy}")
    private String appid;
    @Value("${wechat.secret:dummy}")
    private String secret;
    @Value("${wechat.enabled:false}")
    private boolean enabled;
    @Value("${wechat.phone-binding.enabled:false}")
    private boolean phoneBindingEnabled;

    // 微信标准错误码:access_token 失效/过期(凭证问题,值得清缓存重试一次;
    // 其它错误码——如 40029 code 无效——重试没有意义,直接抛错)。
    private static final Set<Integer> INVALID_TOKEN_ERRCODES = Set.of(40001, 40014, 42001);

    private volatile String cachedAccessToken;
    private volatile Instant accessTokenExpiresAt = Instant.MIN;

    /**
     * Spring 装配用的唯一公开构造函数——不要删除或给它加 {@code @Autowired}。
     * 本类曾直接注入 {@code RestClient.Builder},结果应用启动时报
     * {@code NoSuchBeanDefinitionException}(该 bean 在本项目里不是自动装配的);
     * 现在自己 new 一个 RestClient(同 {@link WechatCodeExchanger} 既有约定),
     * 靠"唯一公开构造函数 + 无 @Autowired 时 Spring 隐式选它"这条规则被装配。
     */
    public WechatPhoneNumberExchanger() {
        this(RestClient.builder().baseUrl("https://api.weixin.qq.com").build());
    }

    // 测试专用(package-private,不是给 Spring 用的):注入已绑定 MockRestServiceServer
    // 的 RestClient,让单测能真正驱动 exchange()/accessToken() 的 HTTP 逻辑而不用碰网络。
    WechatPhoneNumberExchanger(RestClient http) {
        this.http = http;
    }

    public String exchange(String code) {
        if (!enabled) {
            if (code != null && code.startsWith("dev-")) {
                return devPhoneFor(code);
            }
            throw new DomainException("微信手机号绑定未启用(wechat.enabled=false),code 必须以 dev- 开头");
        }
        if (!phoneBindingEnabled) {
            throw new DomainException("手机号绑定功能暂未开放(wechat.phone-binding.enabled=false)");
        }
        return exchangeViaWechat(code, true);
    }

    /**
     * @param allowRetry 首次调用为 true;若微信判定当前 access_token 失效(而非 code 本身有问题),
     *                   清缓存换新 token 后重试恰好一次(避免缓存的 token 被微信提前失效——比如
     *                   appsecret 轮换——导致后续请求在 TTL 到期前持续失败,最长可达 ~2 小时)。
     */
    private String exchangeViaWechat(String code, boolean allowRetry) {
        String accessToken = accessToken();
        Map<?, ?> body = http.post()
                .uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                        .queryParam("access_token", accessToken)
                        .build())
                .body(Map.of("code", code))
                .retrieve()
                .body(Map.class);
        Object errcode = body == null ? null : body.get("errcode");
        if (allowRetry && errcode instanceof Number n && INVALID_TOKEN_ERRCODES.contains(n.intValue())) {
            invalidateAccessTokenCache();
            return exchangeViaWechat(code, false);
        }
        Object phoneInfoObj = body == null ? null : body.get("phone_info");
        if (!(phoneInfoObj instanceof Map<?, ?> phoneInfo) || !(phoneInfo.get("phoneNumber") instanceof String phoneNumber)) {
            throw new DomainException("微信手机号换取失败:" + errMsgOf(body));
        }
        return phoneNumber;
    }

    private synchronized void invalidateAccessTokenCache() {
        cachedAccessToken = null;
        accessTokenExpiresAt = Instant.MIN;
    }

    private synchronized String accessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(accessTokenExpiresAt)) {
            return cachedAccessToken;
        }
        Map<?, ?> body = http.get()
                .uri(uri -> uri.path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", appid)
                        .queryParam("secret", secret)
                        .build())
                .retrieve()
                .body(Map.class);
        if (body == null || !(body.get("access_token") instanceof String token)) {
            throw new DomainException("微信 access_token 获取失败:" + errMsgOf(body));
        }
        cachedAccessToken = token;
        int expiresIn = body.get("expires_in") instanceof Number n ? n.intValue() : 7200;
        // 提前 60s 过期,避免请求发出瞬间恰好撞上失效边界
        accessTokenExpiresAt = now.plusSeconds(Math.max(expiresIn - 60, 0));
        return cachedAccessToken;
    }

    private static String errMsgOf(Map<?, ?> body) {
        if (body == null) {
            return "empty";
        }
        Object errmsg = body.get("errmsg");
        return errmsg == null ? "empty" : errmsg.toString();
    }

    private static String devPhoneFor(String code) {
        return phoneFromHash(code.hashCode());
    }

    /**
     * package-private 便于直测 Integer.MIN_VALUE 这类边界(找一个真 hashCode 恰好等于
     * Integer.MIN_VALUE 的字符串不现实,直接测这个纯函数更可靠)。
     *
     * <p>用位运算 {@code & Integer.MAX_VALUE} 清符号位而非 {@code Math.abs}——Java 里
     * {@code Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE}(两补码溢出,仍是负数),
     * 位运算对任何 int 输入都保证非负,不存在这个边界。
     */
    static String phoneFromHash(int hash) {
        int nonNegative = hash & Integer.MAX_VALUE;
        return String.format(Locale.ROOT, "138%08d", nonNegative % 100000000);
    }
}
