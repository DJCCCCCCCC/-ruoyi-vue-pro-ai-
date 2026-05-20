package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - 风险词库创建/更新 Request VO")
@Data
public class PayRiskTermSaveReqVO {

    @Schema(description = "编号（更新时必填）")
    private Long id;

    @Schema(description = "风险词", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "风险词不能为空")
    @Size(max = 256, message = "风险词长度不能超过 256")
    private String term;

    @Schema(description = "分类", example = "FRAUD_SCRIPT")
    private String category;

    @Schema(description = "状态：0 启用 1 禁用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "说明")
    @Size(max = 512, message = "说明长度不能超过 512")
    private String description;
}
