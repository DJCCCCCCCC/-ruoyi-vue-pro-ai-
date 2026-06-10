package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskPoliceReport;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskRelationTopology;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 事后报警协助 Response VO")
@Data
public class PayRiskPoliceReportRespVO {

    @Schema(description = "面向民警的结构化报警协助报告")
    private PayRiskPoliceReport report;

    @Schema(description = "关系拓扑（账户/链路线索）")
    private PayRiskRelationTopology topologyInfo;

    @Schema(description = "报告生成时间（ISO-8601）")
    private String generatedAt;
}
