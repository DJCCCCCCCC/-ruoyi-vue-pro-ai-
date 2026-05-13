package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 与风险词关联的评估工单（支付风险评估记录）")
@Data
public class PayRiskTermRelatedTicketVO {

    @Schema(description = "评估记录编号")
    private Long id;

    @Schema(description = "场景")
    private String scene;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "复核状态")
    private String reviewStatus;

    @Schema(description = "策略建议动作")
    private String decisionAction;

    @Schema(description = "沟通过程汇总（来自入参 paymentData.messages 等）")
    private String conversationSummary;
}
