package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 图片 OCR 专项分析 Response VO")
@Data
public class PayRiskImageOcrAnalyzeRespVO {

    @Schema(description = "检测到的内嵌图片数量")
    private Integer embeddedImageCount;

    @Schema(description = "OCR 服务是否已开启")
    private Boolean ocrServiceEnabled;

    @Schema(description = "OCR 接口调用次数")
    private Integer ocrApiCallCount;

    @Schema(description = "得到有效文字的段数")
    private Integer ocrValidTextCount;

    @Schema(description = "面向产品的中文统计说明")
    private String imageOcrSummary;

    @Schema(description = "OCR 合并正文截断预览")
    private String imageOcrTextPreview;

    @Schema(description = "各张图片 OCR 文本（顺序与处理顺序一致）")
    private List<String> multimodalImageOcrTexts;

    @Schema(description = "全部 OCR 合并正文")
    private String multimodalImageOcrMerged;

    @Schema(description = "基于 OCR 正文的 LLM 解读（诈骗话术、转账指令、钓鱼特征等），未开启 DeepSeek 或无有效文字时为 null")
    private String llmImageContentNarrative;
}
