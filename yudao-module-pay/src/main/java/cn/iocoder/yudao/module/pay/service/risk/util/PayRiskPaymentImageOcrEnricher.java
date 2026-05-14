package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.service.risk.client.PayRiskGiteeOcrClient;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskImageOcrEnrichOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 在支付风险评估前，扫描 {@code paymentData} 中的图片 data URL，调用 OCR 后将文本写入 JSON，
 * 并生成面向响应体的中文说明字段。
 */
@Slf4j
public final class PayRiskPaymentImageOcrEnricher {

    private static final String DATA_IMAGE_PREFIX = "data:image/";
    private static final String BASE64_MARKER = ";base64,";

    private PayRiskPaymentImageOcrEnricher() {
    }

    /**
     * 兼容旧调用：仅返回处理后的 paymentData。
     */
    public static JsonNode enrich(JsonNode paymentData,
                                  PayRiskGiteeOcrClient ocrClient,
                                  long maxPayloadChars,
                                  int maxImages,
                                  boolean stripAfterOcr) {
        return enrichWithOutcome(paymentData, ocrClient, maxPayloadChars, maxImages, stripAfterOcr).getPaymentData();
    }

    public static PayRiskImageOcrEnrichOutcome enrichWithOutcome(JsonNode paymentData,
                                                                 PayRiskGiteeOcrClient ocrClient,
                                                                 long maxPayloadChars,
                                                                 int maxImages,
                                                                 boolean stripAfterOcr) {
        if (paymentData == null || !paymentData.isObject()) {
            return PayRiskImageOcrEnrichOutcome.noImages(paymentData);
        }
        int embedded = countImageDataUrls(paymentData);
        if (embedded == 0) {
            return PayRiskImageOcrEnrichOutcome.noImages(paymentData);
        }

        boolean enabled = ocrClient != null && ocrClient.isEnabled();
        if (!enabled) {
            String summary = buildSummaryOcrDisabled(embedded, maxImages);
            return PayRiskImageOcrEnrichOutcome.builder()
                    .paymentData(paymentData)
                    .embeddedImageCount(embedded)
                    .ocrServiceEnabled(false)
                    .ocrApiCallCount(0)
                    .ocrValidTextCount(0)
                    .imageOcrSummary(summary)
                    .imageOcrTextPreview(null)
                    .build();
        }

        ObjectNode root = paymentData.deepCopy();
        List<String> ocrPieces = new ArrayList<>();
        int[] counter = new int[]{0};
        walk(root, ocrClient, ocrPieces, counter, maxPayloadChars, maxImages, stripAfterOcr);
        int apiCalls = counter[0];
        int valid = ocrPieces.size();
        if (!ocrPieces.isEmpty()) {
            ArrayNode arr = root.putArray("multimodalImageOcrTexts");
            for (String piece : ocrPieces) {
                arr.add(piece);
            }
            String merged = String.join("\n\n--- 图片分隔 ---\n\n", ocrPieces);
            root.put("multimodalImageOcrMerged", merged);
        }
        String preview = valid > 0 ? truncatePreview(String.join("\n", ocrPieces), 600) : null;
        String summary = buildSummaryOcrEnabled(embedded, maxImages, apiCalls, valid, preview);
        return PayRiskImageOcrEnrichOutcome.builder()
                .paymentData(root)
                .embeddedImageCount(embedded)
                .ocrServiceEnabled(true)
                .ocrApiCallCount(apiCalls)
                .ocrValidTextCount(valid)
                .imageOcrSummary(summary)
                .imageOcrTextPreview(preview)
                .build();
    }

    public static int countImageDataUrls(JsonNode node) {
        if (node == null) {
            return 0;
        }
        if (node.isTextual() && isImageDataUrl(node.asText())) {
            return 1;
        }
        if (node.isArray()) {
            int sum = 0;
            for (JsonNode c : node) {
                sum += countImageDataUrls(c);
            }
            return sum;
        }
        if (node.isObject()) {
            int sum = 0;
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                sum += countImageDataUrls(it.next().getValue());
            }
            return sum;
        }
        return 0;
    }

    private static String buildSummaryOcrDisabled(int embedded, int maxImages) {
        return "【图片说明】本次请求中检测到 "
                + embedded
                + " 处内嵌聊天/截图图片，但服务端 OCR 未开启（请配置 yudao.pay.risk-assess.ocr.enabled=true 并填写 api-key）。"
                + "综合评分主要依据文字聊天内容与 IP/链接/行为等其它情报；未对图中文字做专项识别。"
                + (embedded > maxImages ? " 说明：单次评估最多处理 " + maxImages + " 张图。" : "");
    }

    private static String buildSummaryOcrEnabled(int embedded, int maxImages, int apiCalls, int valid, String preview) {
        StringBuilder sb = new StringBuilder();
        sb.append("【图片说明】共检测到 ").append(embedded).append(" 处内嵌图片");
        if (embedded > maxImages) {
            sb.append("，本次按配置最多处理 ").append(maxImages).append(" 张");
        }
        sb.append("；已调用 OCR ").append(apiCalls).append(" 次");
        if (valid > 0) {
            sb.append("，其中 ").append(valid).append(" 张得到有效文字，已写入脱敏 JSON 字段 multimodalImageOcrMerged 并参与 DeepSeek 综合研判。");
        } else {
            sb.append("，但未得到有效文字（接口失败或图中缺少清晰可识别文字）。模型主要依据聊天原文与其它情报分析。");
        }
        if (preview != null && !preview.trim().isEmpty()) {
            sb.append(" 识别内容预览：");
            sb.append(preview);
        }
        return sb.toString();
    }

    private static String truncatePreview(String merged, int maxLen) {
        if (merged == null) {
            return null;
        }
        String t = merged.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }

    private static void walk(JsonNode node,
                             PayRiskGiteeOcrClient ocrClient,
                             List<String> ocrPieces,
                             int[] counter,
                             long maxPayloadChars,
                             int maxImages,
                             boolean stripAfterOcr) {
        if (node == null || counter[0] >= maxImages) {
            return;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                if (counter[0] >= maxImages) {
                    break;
                }
                JsonNode el = arr.get(i);
                if (el.isTextual()) {
                    String t = el.asText();
                    if (isImageDataUrl(t) && t.length() <= maxPayloadChars) {
                        String ocr = runOcrAndCollect(ocrClient, t, ocrPieces, counter, maxImages);
                        if (stripAfterOcr) {
                            arr.set(i, TextNode.valueOf(placeholderForReplacedImage(ocr)));
                        }
                    }
                } else {
                    walk(el, ocrClient, ocrPieces, counter, maxPayloadChars, maxImages, stripAfterOcr);
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (counter[0] >= maxImages) {
                break;
            }
            if (v != null && v.isTextual()) {
                String t = v.asText();
                if (isImageDataUrl(t) && t.length() <= maxPayloadChars) {
                    String ocr = runOcrAndCollect(ocrClient, t, ocrPieces, counter, maxImages);
                    if (stripAfterOcr) {
                        obj.set(e.getKey(), TextNode.valueOf(placeholderForReplacedImage(ocr)));
                    }
                    continue;
                }
            }
            walk(v, ocrClient, ocrPieces, counter, maxPayloadChars, maxImages, stripAfterOcr);
        }
    }

    private static String runOcrAndCollect(PayRiskGiteeOcrClient ocrClient,
                                           String dataUrl,
                                           List<String> ocrPieces,
                                           int[] counter,
                                           int maxImages) {
        if (counter[0] >= maxImages) {
            return null;
        }
        counter[0]++;
        String text = ocrClient.recognizeImageDataUrl(dataUrl);
        if (text != null && !text.trim().isEmpty()) {
            ocrPieces.add(text.trim());
            return text.trim();
        }
        log.info("[PayRiskPaymentImageOcrEnricher] 单张图片 OCR 无有效文字（可能识别失败）");
        return null;
    }

    private static String placeholderForReplacedImage(String ocr) {
        if (ocr == null || ocr.trim().isEmpty()) {
            return "[图片:已尝试OCR，未得到有效文字]";
        }
        return "[图片:已OCR识别，原文数据已省略]";
    }

    public static boolean isImageDataUrl(String t) {
        if (t == null) {
            return false;
        }
        String s = t.trim();
        return s.startsWith(DATA_IMAGE_PREFIX)
                && s.contains(BASE64_MARKER)
                && s.length() > BASE64_MARKER.length() + 50;
    }
}
