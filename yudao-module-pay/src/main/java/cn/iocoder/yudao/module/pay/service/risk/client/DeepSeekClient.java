package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAssessAiResponse;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
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

    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${yudao.pay.risk-assess.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${yudao.pay.risk-assess.deepseek.temperature:0.2}")
    private Double temperature;

    @Value("${yudao.pay.risk-assess.deepseek.max-tokens:800}")
    private Integer maxTokens;

    @Value("${yudao.pay.risk-assess.http-timeout-millis:20000}")
    private Integer timeoutMillis;

    public PayRiskAssessAiResponse assess(String paymentMaskedJson, String ipInfoMaskedJson) {
        if (deepseekApiKey == null || deepseekApiKey.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_API_KEY_MISSING);
        }

        String userPrompt = PayRiskPromptBuilder.buildUserPrompt(paymentMaskedJson, ipInfoMaskedJson);
        String url = baseUrl + "/chat/completions";

        // DeepSeek 默认兼容 OpenAI：使用 Authorization: Bearer xxx
        Map<String, Object> reqBody = buildRequestBody(userPrompt, true);
        try {
            return doCall(url, reqBody, PayRiskAssessAiResponse.class);
        } catch (Exception firstEx) {
            // 有些账号/版本不支持 response_format，第二次不传该字段
            log.warn("[DeepSeekClient][assess] 第一次调用失败，重试(不含 response_format)：{}", firstEx.getMessage());
            reqBody = buildRequestBody(userPrompt, false);
            return doCall(url, reqBody, PayRiskAssessAiResponse.class);
        }
    }

    public PayRiskLlmAnalysisReport generateRiskReport(String contextJson) {
        if (deepseekApiKey == null || deepseekApiKey.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_API_KEY_MISSING);
        }

        String userPrompt = PayRiskLlmReportPromptBuilder.buildUserPrompt(contextJson);
        String url = baseUrl + "/chat/completions";

        Map<String, Object> reqBody = buildRequestBody(userPrompt, PayRiskLlmReportPromptBuilder.SYSTEM_PROMPT, true);
        try {
            return doCall(url, reqBody, PayRiskLlmAnalysisReport.class);
        } catch (Exception firstEx) {
            log.warn("[DeepSeekClient][generateRiskReport] 绗竴娆¤皟鐢ㄥけ璐ワ紝閲嶈瘯(涓嶅惈 response_format)锛歿}", firstEx.getMessage());
            reqBody = buildRequestBody(userPrompt, PayRiskLlmReportPromptBuilder.SYSTEM_PROMPT, false);
            return doCall(url, reqBody, PayRiskLlmAnalysisReport.class);
        }
    }

    private Map<String, Object> buildRequestBody(String userPrompt, boolean withResponseFormat) {
        return buildRequestBody(userPrompt, PayRiskPromptBuilder.SYSTEM_PROMPT, withResponseFormat);
    }

    private Map<String, Object> buildRequestBody(String userPrompt, String systemPrompt, boolean withResponseFormat) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", model);
        req.put("temperature", temperature);
        req.put("max_tokens", maxTokens);

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

    private <T> T doCall(String url, Map<String, Object> reqBody, Class<T> resultType) {
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + deepseekApiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis)
                .body(JsonUtils.toJsonString(reqBody))
                .execute()) {
            String body = response.body();
            JsonNode root = JsonUtils.parseTree(body);
            String content = root.path("choices").isArray() && root.path("choices").size() > 0 ?
                    root.path("choices").get(0).path("message").path("content").asText() : null;
            if (content == null) {
                throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_DEEPSEEK_CALL_FAILED, body);
            }

            String jsonText = extractJsonObject(content);
            T result = JsonUtils.parseObject(jsonText, resultType);
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
            log.error("[DeepSeekClient][doCall] 调用 DeepSeek 失败", e);
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
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}

