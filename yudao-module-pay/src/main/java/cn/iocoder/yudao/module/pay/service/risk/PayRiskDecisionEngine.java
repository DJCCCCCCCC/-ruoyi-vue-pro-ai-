package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.module.pay.framework.pay.config.PayRiskAssessProperties;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskDecisionResult;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规则型决策引擎：综合评分、等级、历史相似度与 LLM 置信度，输出建议动作与复核提示。
 */
@Component
public class PayRiskDecisionEngine {

    public static final String ACTION_ALLOW = "ALLOW";
    public static final String ACTION_MANUAL_REVIEW = "MANUAL_REVIEW";
    public static final String ACTION_BLOCK = "BLOCK";

    private static final String REASON_SCORE_BLOCK = "SCORE_GTE_BLOCK_THRESHOLD";
    private static final String REASON_SCORE_REVIEW = "SCORE_GTE_REVIEW_THRESHOLD";
    private static final String REASON_LEVEL_BASELINE = "LEVEL_BASELINE";
    private static final String REASON_CASE_SIMILARITY = "CASE_SIMILARITY_STRONG";
    private static final String REASON_LLM_HIGH_CONF = "LLM_HIGH_CONFIDENCE_RISK";

    @Resource
    private PayRiskAssessProperties payRiskAssessProperties;

    public PayRiskDecisionResult decide(Integer riskScore,
                                        String riskLevel,
                                        Integer caseSimilarityBonus,
                                        PayRiskLlmAnalysisReport llmReport) {
        PayRiskAssessProperties.Decision cfg = payRiskAssessProperties.getDecision();
        int score = riskScore == null ? 0 : Math.min(100, Math.max(0, riskScore));
        String level = normalizeLevel(riskLevel);
        int bonus = caseSimilarityBonus == null ? 0 : Math.max(0, caseSimilarityBonus);

        List<String> codes = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        String baselineAction = defaultActionForLevel(level, cfg.getLevelDefaults());
        codes.add(REASON_LEVEL_BASELINE);
        messages.add("等级「" + level + "」基准建议：" + describeAction(baselineAction));

        String action = baselineAction;
        if (score >= cfg.getBlockMinScore()) {
            action = ACTION_BLOCK;
            addOnce(codes, REASON_SCORE_BLOCK);
            messages.add(String.format(Locale.CHINA, "风险分 %d ≥ 拦截阈值 %d，建议拦截或暂停交易", score, cfg.getBlockMinScore()));
        } else if (score >= cfg.getManualReviewMinScore()) {
            if (ACTION_ALLOW.equals(action)) {
                action = ACTION_MANUAL_REVIEW;
            }
            addOnce(codes, REASON_SCORE_REVIEW);
            messages.add(String.format(Locale.CHINA, "风险分 %d ≥ 复核阈值 %d，建议人工复核", score, cfg.getManualReviewMinScore()));
        }

        if (bonus >= cfg.getCaseSimilarityReviewBonusThreshold()) {
            addOnce(codes, REASON_CASE_SIMILARITY);
            messages.add("历史案例相似加分 +" + bonus + "，建议结合案例库人工确认");
            if (ACTION_ALLOW.equals(action)) {
                action = ACTION_MANUAL_REVIEW;
            }
        }

        if (llmHighConfidenceRisk(llmReport)) {
            addOnce(codes, REASON_LLM_HIGH_CONF);
            messages.add("大模型评估为高置信风险，建议人工复核");
            if (ACTION_ALLOW.equals(action)) {
                action = ACTION_MANUAL_REVIEW;
            }
        }

        boolean requiresHuman = !ACTION_ALLOW.equals(action);
        PayRiskDecisionResult r = new PayRiskDecisionResult();
        r.setPolicyVersion(cfg.getPolicyVersion());
        r.setRecommendedAction(action);
        r.setReasonCodes(codes);
        r.setReasonMessages(messages);
        r.setRequiresHumanReview(requiresHuman);
        r.setReviewHint(requiresHuman
                ? "该笔评估已进入「待复核」队列，请在管理端完成放行/拦截/误报结案以闭环。"
                : "当前策略为放行，可按业务需要抽检审计。");
        return r;
    }

    private static void addOnce(List<String> codes, String code) {
        if (!codes.contains(code)) {
            codes.add(code);
        }
    }

    private static String defaultActionForLevel(String level, Map<String, String> levelDefaults) {
        if (levelDefaults != null) {
            String a = levelDefaults.get(level);
            if (a != null && !a.isEmpty()) {
                return a.trim().toUpperCase(Locale.ROOT);
            }
        }
        switch (level) {
            case "LOW":
                return ACTION_ALLOW;
            case "MEDIUM":
            case "HIGH":
                return ACTION_MANUAL_REVIEW;
            case "CRITICAL":
                return ACTION_BLOCK;
            default:
                return ACTION_MANUAL_REVIEW;
        }
    }

    private static String normalizeLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            return "MEDIUM";
        }
        return riskLevel.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean llmHighConfidenceRisk(PayRiskLlmAnalysisReport llm) {
        if (llm == null) {
            return false;
        }
        String conf = llm.getConfidence();
        if (conf == null || !"HIGH".equalsIgnoreCase(conf.trim())) {
            return false;
        }
        String v = llm.getVerdict();
        if (v == null) {
            return false;
        }
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("诈骗") || s.contains("风险") || s.contains("高危") || s.contains("欺诈")
                || s.contains("scam") || s.contains("fraud");
    }

    private static String describeAction(String action) {
        if (ACTION_ALLOW.equals(action)) {
            return "放行/继续观察";
        }
        if (ACTION_BLOCK.equals(action)) {
            return "拦截/暂停交易";
        }
        return "人工复核";
    }
}
