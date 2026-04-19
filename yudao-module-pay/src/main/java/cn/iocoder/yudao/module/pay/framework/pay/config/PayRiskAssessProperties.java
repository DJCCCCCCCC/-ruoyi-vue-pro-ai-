package cn.iocoder.yudao.module.pay.framework.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付风险评估相关配置（策略阈值等）。
 */
@ConfigurationProperties(prefix = "yudao.pay.risk-assess")
@Data
public class PayRiskAssessProperties {

    private Decision decision = new Decision();

    @Data
    public static class Decision {

        /**
         * 策略版本标识
         */
        private String policyVersion = "v1";

        /**
         * 达到该分数建议 BLOCK（可被等级与 LLM 信号叠加调整）
         */
        private int blockMinScore = 85;

        /**
         * 达到该分数至少 MANUAL_REVIEW（若等级默认可放行时仍抬升）
         */
        private int manualReviewMinScore = 65;

        /**
         * 历史案例加分达到该值时，增加「相似案例」复核理由，并在默认可放行时抬升至复核
         */
        private int caseSimilarityReviewBonusThreshold = 8;

        /**
         * 风险等级 -> 默认建议动作（ALLOW / MANUAL_REVIEW / BLOCK）
         */
        private Map<String, String> levelDefaults = new LinkedHashMap<String, String>() {
            {
                put("LOW", "ALLOW");
                put("MEDIUM", "MANUAL_REVIEW");
                put("HIGH", "MANUAL_REVIEW");
                put("CRITICAL", "BLOCK");
            }
        };
    }
}
