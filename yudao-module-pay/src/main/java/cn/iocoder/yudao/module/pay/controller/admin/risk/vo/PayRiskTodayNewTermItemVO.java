package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 今日新增风险词项")
@Data
public class PayRiskTodayNewTermItemVO {

    @Schema(description = "词库编号")
    private Long termId;

    @Schema(description = "风险词（与评估记录 riskFactors 中条目一致）")
    private String term;

    @Schema(description = "来源：MANUAL / AUTO_ASSESS")
    private String sourceType;

    @Schema(description = "词库累计命中次数")
    private Long hitCount;

    @Schema(description = "今日命中该词的评估工单数")
    private Integer todayHitCount;

    @Schema(description = "关联评估记录编号列表")
    private List<Long> relatedRecordIds;
}
