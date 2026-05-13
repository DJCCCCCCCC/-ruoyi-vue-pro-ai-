package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "管理后台 - 图片 OCR 专项分析 Request VO")
@Data
public class PayRiskImageOcrAnalyzeReqVO {

    @Schema(description = "图片 data URL 列表（data:image/...;base64,...）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少提供一张图片")
    private List<String> imageDataUrls;

    @Schema(description = "是否在 OCR 后调用 LLM 生成「图中文字含义与潜在风险」说明；默认 true")
    private Boolean includeLlmInsight;
}
