package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 事后报警协助 Request VO")
@Data
public class PayRiskPoliceReportReqVO {

    @Schema(description = "支付/聊天上下文 JSON（含 messages 等）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "paymentData 不能为空")
    private JsonNode paymentData;

    @Schema(description = "IP（可选；为空则从 paymentData 提取）")
    private String ip;

    @Schema(description = "用户确认已发生转账/被骗")
    private Boolean confirmedTransferred;

    @Schema(description = "受害人补充说明（转账时间、渠道、对方账号等）")
    private String additionalVictimNotes;

    @Schema(description = "此前风控评估结果快照（含 riskScore、llmReport、decision 等）")
    private JsonNode priorAssessSnapshot;
}
