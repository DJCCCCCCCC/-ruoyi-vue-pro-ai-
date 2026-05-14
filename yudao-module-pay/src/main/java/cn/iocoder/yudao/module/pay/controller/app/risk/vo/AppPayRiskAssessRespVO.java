package cn.iocoder.yudao.module.pay.controller.app.risk.vo;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAdvancedAnalysis;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAgentReflection;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskDecisionResult;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
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

    @Schema(description = "LLM risk analysis report")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private PayRiskLlmAnalysisReport llmReport;

    @Schema(description = "Advanced risk analysis")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private PayRiskAdvancedAnalysis advancedAnalysis;

    @Schema(description = "Agentic reflection result: assessor, skeptic and arbiter")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private PayRiskAgentReflection agentReflection;

    @Schema(description = "历史案例相似性加分", example = "12")
    private Integer caseSimilarityBonus;

    @Schema(description = "策略决策（放行/复核/拦截建议与原因）")
    private PayRiskDecisionResult decision;

    @Schema(description = "内嵌图片（data URL）数量，无图时为 null")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer embeddedImageCount;

    @Schema(description = "服务端图片 OCR 是否已开启（配置）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean imageOcrServiceEnabled;

    @Schema(description = "实际调用 OCR 接口次数")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer imageOcrApiCallCount;

    @Schema(description = "获得有效 OCR 文本的段数")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer imageOcrValidTextCount;

    @Schema(description = "图片多模态处理说明（中文），便于前端直接展示")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String imageOcrSummary;

    @Schema(description = "OCR 合并正文预览（截断），完整见落库 paymentData.multimodalImageOcrMerged")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String imageOcrTextPreview;
}
