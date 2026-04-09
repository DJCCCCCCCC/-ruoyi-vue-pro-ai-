package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 支付风险评估记录 Response VO")
@Data
public class PayRiskAssessRecordRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "来源", example = "chat-risk-test-page")
    private String source;

    @Schema(description = "场景", example = "WECHAT_CHAT_RISK")
    private String scene;

    @Schema(description = "IP", example = "8.8.8.8")
    private String ip;

    @Schema(description = "风险评分", example = "82")
    private Integer riskScore;

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "风险因素 JSON 字符串")
    private String riskFactorsJson;

    @Schema(description = "深度分析")
    private String deepAnalysis;

    @Schema(description = "输入数据 JSON 字符串")
    private String paymentDataJson;

    @Schema(description = "IP 情报 JSON 字符串")
    private String ipInfoJson;

    @Schema(description = "Whois 情报 JSON 字符串")
    private String whoisInfoJson;

    @Schema(description = "生物行为分析 JSON 字符串")
    private String behaviorInfoJson;

    @Schema(description = "关系拓扑 JSON 字符串")
    private String topologyInfoJson;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    private String llmReportJson;
    private String advancedAnalysisJson;
}

