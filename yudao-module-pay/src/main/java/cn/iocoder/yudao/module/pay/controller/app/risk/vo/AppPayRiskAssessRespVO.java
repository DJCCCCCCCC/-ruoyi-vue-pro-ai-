package cn.iocoder.yudao.module.pay.controller.app.risk.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "User app payment risk assessment response")
@Data
public class AppPayRiskAssessRespVO {

    @Schema(description = "Risk score", example = "82")
    private Integer riskScore;

    @Schema(description = "Risk level", example = "HIGH")
    private String riskLevel;

    @Schema(description = "Detailed analysis")
    private String deepAnalysis;

    @Schema(description = "Risk factors")
    private List<String> riskFactors;

    @Schema(description = "Masked IP intelligence")
    private JsonNode ipInfo;

    @Schema(description = "Whois intelligence details")
    private JsonNode whoisInfo;
}
