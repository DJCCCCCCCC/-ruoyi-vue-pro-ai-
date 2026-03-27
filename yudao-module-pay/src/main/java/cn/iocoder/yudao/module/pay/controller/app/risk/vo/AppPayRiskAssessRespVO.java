package cn.iocoder.yudao.module.pay.controller.app.risk.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "用户 APP - 支付风险评估 Response VO")
@Data
public class AppPayRiskAssessRespVO {

    @Schema(description = "风险评分(0-100)", example = "82")
    private Integer riskScore;

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "深度分析")
    private String deepAnalysis;

    @Schema(description = "风险因素列表")
    private List<String> riskFactors;

    @Schema(description = "IP 情报（脱敏后）")
    private JsonNode ipInfo;

}

