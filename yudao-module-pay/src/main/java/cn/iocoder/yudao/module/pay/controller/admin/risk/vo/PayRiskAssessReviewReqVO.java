package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - 支付风险评估人工复核 Request VO")
@Data
public class PayRiskAssessReviewReqVO {

    @Schema(description = "记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "记录编号不能为空")
    private Long id;

    /**
     * PASS — 放行；BLOCK — 确认拦截；DISMISS — 误报结案
     */
    @Schema(description = "复核动作：PASS / BLOCK / DISMISS", requiredMode = Schema.RequiredMode.REQUIRED, example = "PASS")
    @NotBlank(message = "复核动作不能为空")
    private String reviewAction;

    @Schema(description = "备注")
    private String remark;
}
