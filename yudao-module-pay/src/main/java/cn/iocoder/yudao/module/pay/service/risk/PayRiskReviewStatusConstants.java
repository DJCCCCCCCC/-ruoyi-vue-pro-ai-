package cn.iocoder.yudao.module.pay.service.risk;

/**
 * 人工复核状态（与 {@code pay_risk_assess_record.review_status} 一致）。
 */
public final class PayRiskReviewStatusConstants {

    private PayRiskReviewStatusConstants() {}

    /** 无需人工介入 */
    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    /** 待复核 */
    public static final String PENDING = "PENDING";
    /** 已复核-放行 */
    public static final String RESOLVED_PASS = "RESOLVED_PASS";
    /** 已复核-确认拦截 */
    public static final String RESOLVED_BLOCK = "RESOLVED_BLOCK";
    /** 误报结案 */
    public static final String DISMISSED = "DISMISSED";
}
