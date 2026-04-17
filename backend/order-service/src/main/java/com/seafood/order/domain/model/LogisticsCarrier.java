package com.seafood.order.domain.model;

/**
 * Logistics carrier enum for Chinese mainland express delivery services.
 * Each carrier includes code, name, and tracking URL template.
 */
public enum LogisticsCarrier {
    SF("SF", "顺丰速运", "https://www.sf-express.com/cn/sc/delivery-step/delivery-step.html?trackingNumber=%s"),
    EMS("EMS", "邮政EMS", "https://www.ems.com.cn/query?mailCode=%s"),
    ZTO("ZTO", "中通快递", "https://www.zto.com/express/search/search.html?searchText=%s"),
    STO("STO", "申通快递", "https://www.its66.com/express/search?number=%s"),
    YTO("YTO", "圆通速递", "https://www.yto.net.cn/api/query?number=%s"),
    JD("JD", "京东物流", "https://www.jd.com/track?trackNo=%s"),
    YUNDA("YUNDA", "韵达快递", "https://www.yunda56.com/customer-portal/track-query?waybillNumber=%s"),
    TTK("TTK", "天天快递", "https://www.tt.com.tw/search?trackingNumber=%s");

    private final String code;
    private final String name;
    private final String trackingUrlTemplate;

    LogisticsCarrier(String code, String name, String trackingUrlTemplate) {
        this.code = code;
        this.name = name;
        this.trackingUrlTemplate = trackingUrlTemplate;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * Generate tracking URL with the given tracking number.
     *
     * @param trackingNumber the tracking number
     * @return the tracking URL
     */
    public String getTrackingUrl(String trackingNumber) {
        return trackingUrlTemplate.replace("%s", trackingNumber);
    }

    /**
     * Find carrier by code.
     *
     * @param code the carrier code
     * @return the carrier
     * @throws IllegalArgumentException if code not found
     */
    public static LogisticsCarrier fromCode(String code) {
        for (LogisticsCarrier carrier : values()) {
            if (carrier.code.equalsIgnoreCase(code)) {
                return carrier;
            }
        }
        throw new IllegalArgumentException("Unknown carrier code: " + code);
    }

    /**
     * Check if a code matches any carrier.
     *
     * @param code the carrier code
     * @return true if found
     */
    public static boolean isValidCode(String code) {
        for (LogisticsCarrier carrier : values()) {
            if (carrier.code.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }
}
