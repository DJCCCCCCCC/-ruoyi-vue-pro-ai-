package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 今日新增风险词穿透详情 Response VO")
@Data
public class PayRiskTodayNewTermDetailRespVO {

    @Schema(description = "风险词")
    private String term;

    @Schema(description = "关联评估工单及沟通摘要")
    private List<PayRiskTermRelatedTicketVO> tickets;
}
