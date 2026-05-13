package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 今日新增风险词穿透详情 Request VO")
@Data
public class PayRiskTodayNewTermDetailReqVO {

    @Schema(description = "风险词（须为今日新增项）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "风险词不能为空")
    private String term;
}
