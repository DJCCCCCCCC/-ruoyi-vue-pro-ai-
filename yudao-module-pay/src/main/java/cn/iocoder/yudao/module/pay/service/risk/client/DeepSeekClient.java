package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAgentReflection;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAssessAiResponse;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskAgentReflectionPromptBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskLlmReportPromptBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
@Slf4j
public class DeepSeekClient {

    @Value("${spring.ai.dashscope.api-key:}")
    private String qwenApiKey;

    @Value("${yudao.pay.risk-assess.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.qwen.model:qwen-plus}")
    private String model;

    @Value("${yudao.pay.risk-assess.qwen.temperature:0.2}")
    private Double temperature;

    @Value("${yudao.pay.risk-assess.qwen.max-tokens:800}")
    private Integer maxTokens;

    /** 首次评分：略收紧输出长度，加快生成与后续二次调用的输入体积 */
    @Value("${yudao.pay.risk-assess.qwen.assess-max-tokens:512}")
    private Integer assessMaxTokens;

    /** 综合研判报告：含 personaProfile + tailoredUserGuidance，需足够 token 避免 JSON 截断 */
    @Value("${yudao.pay.risk-assess.qwen.report-max-tokens:2048}")
    private Integer reportMaxTokens;

    /** Agentic 反思流：单个 Agent 的 JSON 输出长度 */
    @Value("${yudao.pay.risk-assess.qwen.agent-max-tokens:900}")
    private Integer agentMaxTokens;

    @Value("${yudao.pay.risk-assess.http-timeout-millis:20000}")
    private Integer timeoutMillis;

    /** 图片 OCR 专项解读：纯文本输出，非 JSON */
    @Value("${yudao.pay.risk-assess.qwen.image-ocr-narrative-max-tokens:640}")
    private Integer imageOcrNarrativeMaxTokens;

    public PayRiskAssessAiResponse assess(String paymentMaskedJson, String ipInfoMaskedJson) {
        if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_API_KEY_MISSING);
        }

        String userPrompt = PayRiskPromptBuilder.buildUserPrompt(paymentMaskedJson, ipInfoMaskedJson);
        String url = baseUrl + "/chat/completions";

        // Qwen DashScope 兼容 OpenAI：使用 Authorization: Bearer xxx
        int cap = assessMaxTokens != null && assessMaxTokens > 0 ? assessMaxTokens : maxTokens;
        Map<String, Object> reqBody = buildRequestBody(userPrompt, PayRiskPromptBuilder.SYSTEM_PROMPT, true, cap);
        try {
            return doCall(url, reqBody, PayRiskAssessAiResponse.class);
        } catch (Exception firstEx) {
            // 有些账号/版本不支持 response_format，第二次不传该字段
            log.warn("[DeepSeekClient][assess] 第一次调用失败，重试(不含 response_format)：{}", firstEx.getMessage());
            reqBody = buildRequestBody(userPrompt, PayRiskPromptBuilder.SYSTEM_PROMPT, false, cap);
            return doCall(url, reqBody, PayRiskAssessAiResponse.class);
        }
    }

    public PayRiskLlmAnalysisReport generateRiskReport(String contextJson) {
        if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_API_KEY_MISSING);
        }

        String userPrompt = PayRiskLlmReportPromptBuilder.buildUserPrompt(contextJson);
        String url = baseUrl + "/chat/completions";

        int cap = reportMaxTokens != null && reportMaxTokens > 0 ? reportMaxTokens : maxTokens;
        Map<String, Object> reqBody = buildRequestBody(userPrompt, PayRiskLlmReportPromptBuilder.SYSTEM_PROMPT, true, cap);
        try {
            return doCall(url, reqBody, PayRiskLlmAnalysisReport.class);
        } catch (Exception firstEx) {
            log.warn("[DeepSeekClient][generateRiskReport] 第一次调用失败，重试(不含 response_format)：{}", firstEx.getMessage());
            reqBody = buildRequestBody(userPrompt, PayRiskLlmReportPromptBuilder.SYSTEM_PROMPT, false, cap);
            try {
                return doCall(url, reqBody, PayRiskLlmAnalysisReport.class);
            } catch (Exception secondEx) {
                log.warn("[DeepSeekClient][generateRiskReport] 解析失败，将由上层使用 FALLBACK 报告：{}", secondEx.getMessage());
                return null;
            }
        }
    }

    public PayRiskAgentReflection.AssessorOpinion generateAssessorOpinion(String reflectionContextJson) {
        String prompt = PayRiskAgentReflectionPromptBuilder.buildAssessorPrompt(reflectionContextJson);
        return doAgenticJsonCall(prompt, PayRiskAgentReflectionPromptBuilder.ASSESSOR_SYSTEM_PROMPT,
                PayRiskAgentReflection.AssessorOpinion.class, "assessor");
    }

    public PayRiskAgentReflection.SkepticOpinion generateSkepticOpinion(String reflectionContextJson,
                                                                        PayRiskAgentReflection.AssessorOpinion assessor) {
        String prompt = PayRiskAgentReflectionPromptBuilder.buildSkepticPrompt(reflectionContextJson, assessor);
        return doAgenticJsonCall(prompt, PayRiskAgentReflectionPromptBuilder.SKEPTIC_SYSTEM_PROMPT,
                PayRiskAgentReflection.SkepticOpinion.class, "skeptic");
    }

    public PayRiskAgentReflection.ArbiterOpinion generateArbiterOpinion(String reflectionContextJson,
                                                                        PayRiskAgentReflection.AssessorOpinion assessor,
                                                                        PayRiskAgentReflection.SkepticOpinion skeptic) {
        String prompt = PayRiskAgentReflectionPromptBuilder.buildArbiterPrompt(reflectionContextJson, assessor, skeptic);
        return doAgenticJsonCall(prompt, PayRiskAgentReflectionPromptBuilder.ARBITER_SYSTEM_PROMPT,
                PayRiskAgentReflection.ArbiterOpinion.class, "arbiter");
    }

    /**
     * 根据 OCR 合并文本，用自然语言概括图中文字可能涉及的支付/诈骗场景与风险点（不返回 JSON）。
     */
    public String analyzeImageOcrNarrative(String ocrMergedText) {
        if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
            return null;
        }
        if (ocrMergedText == null || ocrMergedText.trim().isEmpty()) {
            return null;
        }
        String system = "你是支付风控与反诈分析助手。用户将提供从聊天截图或转账凭证中 OCR 得到的文字（可能多图合并）。"
                + "请用中文分条简洁说明：① 图中文字大致在讲什么；② 是否出现转账、验证码、公检法、刷单、理财、钓鱼链接等高风险话术；"
                + "③ 对一线审核员的提醒（不要重复粘贴 OCR 原文）。总字数控制在 600 字以内，不要编造 OCR 中不存在的具体数字或姓名。";
        String user = "以下为 OCR 文本：\n\n" + ocrMergedText.trim();
        int cap = imageOcrNarrativeMaxTokens != null && imageOcrNarrativeMaxTokens > 0 ? imageOcrNarrativeMaxTokens : 640;
        Map<String, Object> reqBody = buildRequestBody(user, system, false, cap);
        String url = baseUrl + "/chat/completions";
        return doCallPlainText(url, reqBody);
    }

    private Map<String, Object> buildRequestBody(String userPrompt, String systemPrompt, boolean withResponseFormat,
                                                  int maxTokensOverride) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", model);
        req.put("temperature", temperature);
        req.put("max_tokens", maxTokensOverride);

        Map<String, Object> system = new HashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        Map<String, Object> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", userPrompt);
        req.put("messages", Arrays.asList(system, user));

        if (withResponseFormat) {
            // 让模型以 JSON 对象形式输出（若不支持则重试时关闭）
            Map<String, Object> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            req.put("response_format", responseFormat);
        }
        return req;
    }

    private <T> T doAgenticJsonCall(String userPrompt, String systemPrompt, Class<T> resultType, String agentName) {
        if (qwenApiKey == null || qwenApiKey.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_API_KEY_MISSING);
        }
        String url = baseUrl + "/chat/completions";
        int cap = agentMaxTokens != null && agentMaxTokens > 0 ? agentMaxTokens : maxTokens;
        Map<String, Object> reqBody = buildRequestBody(userPrompt, systemPrompt, true, cap);
        try {
            return doCall(url, reqBody, resultType);
        } catch (Exception firstEx) {
            log.warn("[DeepSeekClient][{}] 第一次调用失败，重试(不含 response_format)：{}", agentName, firstEx.getMessage());
            reqBody = buildRequestBody(userPrompt, systemPrompt, false, cap);
            return doCall(url, reqBody, resultType);
        }
    }

    private String doCallPlainText(String url, Map<String, Object> reqBody) {
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + qwenApiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis)
                .body(JsonUtils.toJsonString(reqBody))
                .execute()) {
            String body = response.body();
            JsonNode root = JsonUtils.parseTree(body);
            if (root.path("choices").isArray() && root.path("choices").size() > 0) {
                JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
                if (!contentNode.isMissingNode() && contentNode.isTextual()) {
                    String content = contentNode.asText();
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            }
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_CALL_FAILED, body);
        } catch (Exception e) {
            log.error("[DeepSeekClient][doCallPlainText] 调用 Qwen 失败", e);
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_CALL_FAILED, e.getMessage());
        }
    }

    private <T> T doCall(String url, Map<String, Object> reqBody, Class<T> resultType) {
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + qwenApiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis)
                .body(JsonUtils.toJsonString(reqBody))
                .execute()) {
            String body = response.body();
            JsonNode root = JsonUtils.parseTree(body);
            JsonNode firstChoice = root.path("choices").isArray() && root.path("choices").size() > 0
                    ? root.path("choices").get(0) : null;
            String content = firstChoice != null ? firstChoice.path("message").path("content").asText() : null;
            if (content == null) {
                throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_CALL_FAILED, body);
            }
            String finishReason = firstChoice.path("finish_reason").asText("");
            if ("length".equals(finishReason)) {
                log.warn("[DeepSeekClient][doCall] 模型输出因 max_tokens 被截断，finish_reason=length，type={}",
                        resultType.getSimpleName());
            }

            String jsonText = extractJsonObject(content);
            T result = parseJsonLenient(jsonText, resultType);
            if (result instanceof PayRiskAssessAiResponse) {
                PayRiskAssessAiResponse aiResponse = (PayRiskAssessAiResponse) result;
                if (aiResponse.getRiskScore() == null
                        || aiResponse.getRiskLevel() == null
                        || aiResponse.getDeepAnalysis() == null) {
                    throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_AI_RESPONSE_INVALID);
                }
            }
            if (result instanceof PayRiskLlmAnalysisReport) {
                PayRiskLlmAnalysisReport report = (PayRiskLlmAnalysisReport) result;
                if (report.getSummary() == null || report.getVerdict() == null) {
                    throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_AI_RESPONSE_INVALID);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[DeepSeekClient][doCall] 调用 Qwen 失败", e);
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_CALL_FAILED, e.getMessage());
        }
    }

    private String extractJsonObject(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        // 去掉代码块包装（如果模型输出了）
        if (text.startsWith("```")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return text.substring(firstBrace, lastBrace + 1);
            }
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        int end = text.lastIndexOf('}');
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text.substring(start);
    }

    /**
     * 先正常解析；失败时对截断 JSON 补全括号后再试（常见于 report-max-tokens 不足）。
     */
    private <T> T parseJsonLenient(String jsonText, Class<T> resultType) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_AI_RESPONSE_INVALID);
        }
        try {
            return JsonUtils.parseObject(jsonText, resultType);
        } catch (Exception first) {
            String repaired = repairTruncatedJson(jsonText);
            if (repaired.equals(jsonText)) {
                throw first;
            }
            log.warn("[DeepSeekClient] JSON 解析失败，尝试补全截断括号后重试，type={}", resultType.getSimpleName());
            return JsonUtils.parseObject(repaired, resultType);
        }
    }

    /**
     * 为未闭合的 {、[ 补全结束符（不处理字符串中间的截断）。
     */
    private static String repairTruncatedJson(String json) {
        if (json == null) {
            return "";
        }
        String s = json.trim();
        while (s.endsWith(",")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
            }
        }
        if (braceDepth <= 0 && bracketDepth <= 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < bracketDepth; i++) {
            sb.append(']');
        }
        for (int i = 0; i < braceDepth; i++) {
            sb.append('}');
        }
        return sb.toString();
    }
}

