package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAgentReflection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class PayRiskAgentReflectionPromptBuilder {

    public static final String ASSESSOR_SYSTEM_PROMPT = "你是支付风控三阶段反思流中的【判定Agent】。"
            + "你的职责是基于给定证据包提出可审查、可质疑的初步风险观点。"
            + "只能引用 evidenceList 中的证据编号，不得编造输入中不存在的事实。"
            + "如果证据不足，要明确写入 uncertainties，不要把推断包装成确定事实。"
            + "只返回严格 JSON，不要输出 markdown。所有文本使用中文。";

    public static final String SKEPTIC_SYSTEM_PROMPT = "你是支付风控三阶段反思流中的【质疑Agent】。"
            + "你的唯一任务是审查判定Agent的漏洞：证据不足、逻辑跳跃、替代解释、遗漏反证、事实幻觉。"
            + "不要重新写一份普通风险报告；必须逐项针对 assessor.coreClaims 找问题。"
            + "只能依据 evidenceList 和 assessor 输出，不能新增事实。"
            + "只返回严格 JSON，不要输出 markdown。所有文本使用中文。";

    public static final String ARBITER_SYSTEM_PROMPT = "你是支付风控三阶段反思流中的【仲裁Agent】。"
            + "你的职责不是简单平均双方分数，而是逐项审查判定Agent和质疑Agent的论证质量。"
            + "必须剔除无证据支撑、过度外推或事实幻觉的主张。"
            + "不得新增输入中不存在的事实；最终结论应体现风险、置信度、争议度和人工复核建议。"
            + "只返回严格 JSON，不要输出 markdown。所有文本使用中文。";

    public static String buildAssessorPrompt(String reflectionContextJson) {
        return "以下是标准化证据包，请输出判定Agent JSON：\n"
                + reflectionContextJson
                + "\n\n输出格式：\n"
                + "{\n"
                + "  \"preliminaryScore\": 0-100,\n"
                + "  \"riskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n"
                + "  \"recommendation\": \"ALLOW|MANUAL_REVIEW|BLOCK\",\n"
                + "  \"confidence\": 0.0-1.0,\n"
                + "  \"coreClaims\": [{\"claim\":\"...\",\"evidenceIds\":[\"E1\"],\"reasoning\":\"...\",\"confidence\":0.0}],\n"
                + "  \"counterSignalsSeen\": [\"...\"],\n"
                + "  \"uncertainties\": [\"...\"]\n"
                + "}";
    }

    public static String buildSkepticPrompt(String reflectionContextJson,
                                            PayRiskAgentReflection.AssessorOpinion assessor) {
        return "以下是标准化证据包：\n"
                + reflectionContextJson
                + "\n\n以下是判定Agent输出：\n"
                + JsonUtils.toJsonString(assessor)
                + "\n\n请输出质疑Agent JSON：\n"
                + "{\n"
                + "  \"overallChallengeStrength\": 0.0-1.0,\n"
                + "  \"issues\": [{\"targetClaim\":\"...\",\"issueType\":\"INSUFFICIENT_EVIDENCE|LOGIC_GAP|ALTERNATIVE_EXPLANATION|MISSED_COUNTER_EVIDENCE|HALLUCINATION\",\"description\":\"...\",\"severity\":\"LOW|MEDIUM|HIGH\",\"suggestedAdjustment\":\"...\"}],\n"
                + "  \"missedCounterEvidence\": [\"...\"],\n"
                + "  \"hallucinationFlags\": [\"...\"],\n"
                + "  \"revisedScoreSuggestion\": 0-100\n"
                + "}";
    }

    public static String buildArbiterPrompt(String reflectionContextJson,
                                            PayRiskAgentReflection.AssessorOpinion assessor,
                                            PayRiskAgentReflection.SkepticOpinion skeptic) {
        return "以下是标准化证据包：\n"
                + reflectionContextJson
                + "\n\n判定Agent输出：\n"
                + JsonUtils.toJsonString(assessor)
                + "\n\n质疑Agent输出：\n"
                + JsonUtils.toJsonString(skeptic)
                + "\n\n请输出仲裁Agent JSON：\n"
                + "{\n"
                + "  \"finalScore\": 0-100,\n"
                + "  \"finalRiskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n"
                + "  \"finalDecision\": \"ALLOW|MANUAL_REVIEW|BLOCK\",\n"
                + "  \"confidence\": 0.0-1.0,\n"
                + "  \"uncertainty\": 0.0-1.0,\n"
                + "  \"disputeLevel\": \"LOW|MEDIUM|HIGH\",\n"
                + "  \"needManualReview\": true|false,\n"
                + "  \"adoptedPoints\": [{\"from\":\"assessor|skeptic\",\"point\":\"...\",\"reason\":\"...\"}],\n"
                + "  \"rejectedPoints\": [{\"from\":\"assessor|skeptic\",\"point\":\"...\",\"reason\":\"...\"}],\n"
                + "  \"manualReviewFocus\": [\"...\"],\n"
                + "  \"summary\": \"...\"\n"
                + "}";
    }

    public static JsonNode buildContext(JsonNode paymentMaskedJsonNode,
                                        JsonNode ipInfoMaskedJsonNode,
                                        Integer riskScore,
                                        String riskLevel,
                                        String deepAnalysis,
                                        List<String> riskFactors,
                                        JsonNode behaviorInfo,
                                        Object topologyInfo,
                                        JsonNode whoisInfoNode,
                                        Object llmReport,
                                        Object advancedAnalysis) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        ObjectNode baseline = root.putObject("baselineAssessment");
        baseline.put("riskScore", riskScore == null ? 0 : riskScore);
        baseline.put("riskLevel", riskLevel == null ? "LOW" : riskLevel);
        baseline.put("deepAnalysis", truncate(deepAnalysis, 1200));
        baseline.putPOJO("riskFactors", riskFactors);

        ArrayNode evidenceList = root.putArray("evidenceList");
        int idx = 1;
        idx = addEvidence(evidenceList, idx, "BASELINE_SCORE", "硬证据", baseline);
        idx = addEvidence(evidenceList, idx, "PAYMENT_DATA", "硬证据", slimNode(paymentMaskedJsonNode, 6000));
        idx = addEvidence(evidenceList, idx, "IP_INFO", "硬证据", slimNode(ipInfoMaskedJsonNode, 3000));
        if (behaviorInfo != null && !behaviorInfo.isNull()) {
            idx = addEvidence(evidenceList, idx, "BEHAVIOR", "行为证据", behaviorInfo);
        }
        if (topologyInfo != null) {
            idx = addEvidence(evidenceList, idx, "TOPOLOGY", "关系证据", JsonUtils.parseTree(JsonUtils.toJsonString(topologyInfo)));
        }
        if (whoisInfoNode != null && !whoisInfoNode.isNull()) {
            idx = addEvidence(evidenceList, idx, "WHOIS", "外部情报", slimNode(whoisInfoNode, 3000));
        }
        if (llmReport != null) {
            idx = addEvidence(evidenceList, idx, "LLM_REPORT", "模型推断", JsonUtils.parseTree(JsonUtils.toJsonString(llmReport)));
        }
        if (advancedAnalysis != null) {
            addEvidence(evidenceList, idx, "ADVANCED_ANALYSIS", "模型/规则综合推演", JsonUtils.parseTree(JsonUtils.toJsonString(advancedAnalysis)));
        }
        root.put("instruction", "所有 Agent 只能引用 evidenceList 中的 id，例如 E1/E2。硬证据优先于模型推断，软证据需降低权重。高风险或拦截结论必须说明证据门槛。");
        return root;
    }

    private static int addEvidence(ArrayNode arr, int idx, String type, String reliability, JsonNode content) {
        ObjectNode item = arr.addObject();
        item.put("id", "E" + idx);
        item.put("type", type);
        item.put("reliability", reliability);
        item.set("content", content == null ? JsonUtils.getObjectMapper().createObjectNode() : content);
        return idx + 1;
    }

    private static JsonNode slimNode(JsonNode node, int maxChars) {
        if (node == null || node.isNull()) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        String json = JsonUtils.toJsonString(node);
        if (json == null || json.length() <= maxChars) {
            return node;
        }
        ObjectNode out = JsonUtils.getObjectMapper().createObjectNode();
        out.put("truncated", true);
        out.put("preview", json.substring(0, maxChars));
        return out;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars) + "…";
    }
}
