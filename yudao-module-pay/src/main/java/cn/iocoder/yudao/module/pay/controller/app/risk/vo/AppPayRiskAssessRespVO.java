package cn.iocoder.yudao.module.pay.controller.app.risk.vo;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskRelationTopology;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private JsonNode ipInfo;

    @Schema(description = "Whois intelligence details")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String whoisInfo;

    @Schema(description = "Behavioral biometrics analysis details")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private JsonNode behaviorInfo;

    @Schema(description = "Payment relation topology")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private PayRiskRelationTopology topologyInfo;
}
