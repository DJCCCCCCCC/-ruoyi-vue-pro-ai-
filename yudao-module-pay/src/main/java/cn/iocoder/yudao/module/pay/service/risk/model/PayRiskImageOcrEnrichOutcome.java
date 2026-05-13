package cn.iocoder.yudao.module.pay.service.risk.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * 聊天/支付入参经图片 OCR 预处理后的结果与统计，用于写入评估响应说明。
 */
@Value
@Builder
public class PayRiskImageOcrEnrichOutcome {

    /** 供后续链路使用的 paymentData（可能已写入 OCR 字段、替换掉 base64） */
    JsonNode paymentData;

    /** 请求中检测到的内嵌图片（data URL）数量 */
    int embeddedImageCount;

    /** 是否启用了服务端 OCR（配置 enabled 且 api-key 非空） */
    boolean ocrServiceEnabled;

    /** 实际调用 OCR 接口的次数（受 maxImages 限制） */
    int ocrApiCallCount;

    /** 获得非空 OCR 文本的段数 */
    int ocrValidTextCount;

    /**
     * 面向产品/用户的中文说明（多句一段落）
     */
    String imageOcrSummary;

    /**
     * OCR 合并正文的短预览（已截断），完整内容在 paymentData.multimodalImageOcrMerged
     */
    String imageOcrTextPreview;

    public static PayRiskImageOcrEnrichOutcome noImages(JsonNode paymentData) {
        return PayRiskImageOcrEnrichOutcome.builder()
                .paymentData(paymentData)
                .embeddedImageCount(0)
                .ocrServiceEnabled(false)
                .ocrApiCallCount(0)
                .ocrValidTextCount(0)
                .imageOcrSummary(null)
                .imageOcrTextPreview(null)
                .build();
    }
}
