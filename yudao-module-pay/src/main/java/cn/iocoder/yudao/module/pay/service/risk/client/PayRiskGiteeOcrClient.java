package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gitee AI（OpenAI 兼容）多模态 OCR：将图片 data URL 送视觉模型识别为纯文本，供后续 DeepSeek 风控使用。
 * <p>
 * 配置项见 {@code yudao.pay.risk-assess.ocr.*}，api-key 请用环境变量注入，勿提交到仓库。
 */
@Component
@Slf4j
public class PayRiskGiteeOcrClient {

    @Value("${yudao.pay.risk-assess.ocr.enabled:false}")
    private boolean enabled;

    @Value("${yudao.pay.risk-assess.ocr.api-key:}")
    private String apiKey;

    @Value("${yudao.pay.risk-assess.ocr.base-url:https://ai.gitee.com/v1}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.ocr.model:DeepSeek-OCR-2}")
    private String model;

    @Value("${yudao.pay.risk-assess.ocr.timeout-millis:120000}")
    private int timeoutMillis;

    @Value("${yudao.pay.risk-assess.ocr.max-tokens:4096}")
    private int maxTokens;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * @param imageDataUrl 形如 data:image/png;base64,... 的完整 data URL
     * @return 识别出的文字；失败返回 null（调用方降级）
     */
    public String recognizeImageDataUrl(String imageDataUrl) {
        if (!isEnabled()) {
            return null;
        }
        if (imageDataUrl == null || imageDataUrl.length() < 32) {
            return null;
        }

        String url = resolveChatCompletionsUrl();
        List<Map<String, Object>> userContent = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "请完整识别图片中的所有文字，按阅读顺序输出。不要解释，只输出识别到的文字。");
        userContent.add(textPart);
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        imageUrl.put("url", imageDataUrl);
        imagePart.put("image_url", imageUrl);
        userContent.add(imagePart);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.1);
        body.put("max_tokens", maxTokens > 0 ? maxTokens : 4096);
        body.put("messages", Collections.singletonList(userMsg));

        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(timeoutMillis)
                .body(JsonUtils.toJsonString(body))
                .execute()) {
            String respBody = response.body();
            if (!response.isOk()) {
                log.warn("[PayRiskGiteeOcrClient] OCR HTTP 非成功: status={}, body={}", response.getStatus(), truncate(respBody, 800));
                return null;
            }
            JsonNode root = JsonUtils.parseTree(respBody);
            JsonNode err = root.path("error");
            if (!err.isMissingNode() && err.isObject()) {
                log.warn("[PayRiskGiteeOcrClient] OCR 接口错误: {}", truncate(respBody, 1200));
                return null;
            }
            return extractAssistantText(root);
        } catch (Exception e) {
            log.warn("[PayRiskGiteeOcrClient] OCR 调用异常: {}", e.getMessage());
            return null;
        }
    }

    private String resolveChatCompletionsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    private static String extractAssistantText(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            return null;
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        if (contentNode.isTextual()) {
            String t = contentNode.asText();
            return t == null || t.trim().isEmpty() ? null : t.trim();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : contentNode) {
                if ("text".equals(part.path("type").asText())) {
                    sb.append(part.path("text").asText());
                }
            }
            String t = sb.toString().trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
