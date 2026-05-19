package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAgentReflection;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class DifyAgentReflectionClient {

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.base-url:}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.api-key:}")
    private String apiKey;

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.input-name:reflection_context_json}")
    private String inputName;

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.output-name:agent_reflection_json}")
    private String outputName;

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.user:pay-risk-system}")
    private String user;

    @Value("${yudao.pay.risk-assess.agent-reflection.dify.timeout-millis:90000}")
    private Integer timeoutMillis;

    public boolean isConfigured() {
        return StrUtil.isNotBlank(baseUrl) && StrUtil.isNotBlank(apiKey);
    }

    public PayRiskAgentReflection generateAgentReflection(String reflectionContextJson) {
        if (!isConfigured()) {
            throw new IllegalStateException("Dify agent reflection is not configured");
        }
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(inputName, reflectionContextJson);

        Map<String, Object> req = new HashMap<>();
        req.put("inputs", inputs);
        req.put("response_mode", "blocking");
        req.put("user", StrUtil.blankToDefault(user, "pay-risk-system"));

        String url = normalizeBaseUrl(baseUrl) + "/workflows/run";
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis == null || timeoutMillis <= 0 ? 90000 : timeoutMillis)
                .body(JsonUtils.toJsonString(req))
                .execute()) {
            String body = response.body();
            if (!response.isOk()) {
                throw new IllegalStateException("Dify workflow call failed, status=" + response.getStatus() + ", body=" + body);
            }
            PayRiskAgentReflection reflection = parseReflection(body);
            if (reflection == null || reflection.getArbiter() == null) {
                throw new IllegalStateException("Dify workflow response missing arbiter output");
            }
            if (StrUtil.isBlank(reflection.getVersion())) {
                reflection.setVersion("agent-reflection-v1-dify");
            }
            return reflection;
        } catch (Exception ex) {
            log.warn("[DifyAgentReflectionClient] Dify Workflow 调用失败：{}", ex.getMessage(), ex);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    private PayRiskAgentReflection parseReflection(String body) {
        JsonNode root = JsonUtils.parseTree(body);
        JsonNode outputs = root.path("data").path("outputs");
        if (outputs.isMissingNode() || outputs.isNull()) {
            outputs = root.path("outputs");
        }
        JsonNode configuredOutput = outputs.path(outputName);
        if (!configuredOutput.isMissingNode() && !configuredOutput.isNull()) {
            return parseReflectionNode(configuredOutput);
        }
        JsonNode camelOutput = outputs.path("agentReflection");
        if (!camelOutput.isMissingNode() && !camelOutput.isNull()) {
            return parseReflectionNode(camelOutput);
        }
        if (outputs.has("assessor") || outputs.has("skeptic") || outputs.has("arbiter")) {
            return JsonUtils.parseObject(JsonUtils.toJsonString(outputs), PayRiskAgentReflection.class);
        }
        return JsonUtils.parseObject(JsonUtils.toJsonString(root), PayRiskAgentReflection.class);
    }

    private PayRiskAgentReflection parseReflectionNode(JsonNode node) {
        if (node.isTextual()) {
            String text = extractJsonObject(node.asText());
            return JsonUtils.parseObject(text, PayRiskAgentReflection.class);
        }
        return JsonUtils.parseObject(JsonUtils.toJsonString(node), PayRiskAgentReflection.class);
    }

    private String extractJsonObject(String content) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
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

    private String normalizeBaseUrl(String raw) {
        String url = raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v1")) {
            url = url + "/v1";
        }
        return url;
    }
}
