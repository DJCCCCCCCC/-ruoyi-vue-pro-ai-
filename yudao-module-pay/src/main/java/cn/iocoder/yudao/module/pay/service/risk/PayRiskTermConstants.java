package cn.iocoder.yudao.module.pay.service.risk;

/**
 * 支付风险词库常量
 */
public final class PayRiskTermConstants {

    private PayRiskTermConstants() {
    }

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AUTO = "AUTO_ASSESS";

    public static final String CATEGORY_FRAUD_SCRIPT = "FRAUD_SCRIPT";
    public static final String CATEGORY_LINK = "LINK";
    public static final String CATEGORY_BEHAVIOR = "BEHAVIOR";
    public static final String CATEGORY_PAYMENT = "PAYMENT";
    public static final String CATEGORY_OTHER = "OTHER";

    public static final int MAX_TERM_LEN = 256;
}
