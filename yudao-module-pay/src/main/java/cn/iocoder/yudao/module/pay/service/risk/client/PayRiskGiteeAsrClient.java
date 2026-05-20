package cn.iocoder.yudao.module.pay.service.risk.client;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gitee AI（OpenAI 兼容）语音识别：将音频文件转为文本。
 * <p>
 * 配置项见 {@code yudao.pay.risk-assess.asr.*}，api-key 请用环境变量注入，勿提交到仓库。
 */
@Component
@Slf4j
public class PayRiskGiteeAsrClient {

    @Value("${yudao.pay.risk-assess.asr.enabled:false}")
    private boolean enabled;

    @Value("${yudao.pay.risk-assess.asr.api-key:}")
    private String apiKey;

    @Value("${yudao.pay.risk-assess.asr.base-url:https://ai.gitee.com/v1}")
    private String baseUrl;

    @Value("${yudao.pay.risk-assess.asr.model:GLM-ASR}")
    private String model;

    @Value("${yudao.pay.risk-assess.asr.language:zh}")
    private String language;

    @Value("${yudao.pay.risk-assess.asr.timeout-millis:120000}")
    private int timeoutMillis;

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }

    public String getModel() {
        return model;
    }

    /**
     * @param audioBytes   音频二进制
     * @param filename     原始文件名（用于 multipart，如 recording.webm）
     * @return 识别文本；失败返回 null
     */
    public String transcribe(byte[] audioBytes, String filename) {
        if (!isEnabled()) {
            return null;
        }
        if (audioBytes == null || audioBytes.length < 16) {
            return null;
        }
        String safeName = filename == null || filename.trim().isEmpty() ? "recording.webm" : filename.trim();
        String url = resolveTranscriptionsUrl();

        try (HttpResponse response = HttpUtil.createPost(url)
                .header("Authorization", "Bearer " + apiKey.trim())
                .timeout(timeoutMillis)
                .form("model", model)
                .form("language", language)
                .form("file", audioBytes, safeName)
                .execute()) {
            String respBody = response.body();
            if (!response.isOk()) {
                log.warn("[PayRiskGiteeAsrClient] ASR HTTP 非成功: status={}, body={}", response.getStatus(), truncate(respBody, 800));
                return null;
            }
            JsonNode root = JsonUtils.parseTree(respBody);
            JsonNode err = root.path("error");
            if (!err.isMissingNode() && err.isObject()) {
                log.warn("[PayRiskGiteeAsrClient] ASR 接口错误: {}", truncate(respBody, 1200));
                return null;
            }
            return extractText(root);
        } catch (Exception e) {
            log.warn("[PayRiskGiteeAsrClient] ASR 调用异常: {}", e.getMessage());
            return null;
        }
    }

    private String resolveTranscriptionsUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/audio/transcriptions";
    }

    private static String extractText(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode textNode = root.path("text");
        if (textNode.isTextual()) {
            String t = textNode.asText();
            return t == null || t.trim().isEmpty() ? null : t.trim();
        }
        // 部分实现将结果放在 result / transcription 字段
        for (String key : new String[]{"result", "transcription", "transcript"}) {
            JsonNode alt = root.path(key);
            if (alt.isTextual()) {
                String t = alt.asText();
                if (t != null && !t.trim().isEmpty()) {
                    return t.trim();
                }
            }
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
