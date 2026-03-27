package cn.iocoder.yudao.module.pay.controller.app.risk.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Schema(description = "用户 APP - 支付风险评估 Request VO")
@Data
public class AppPayRiskAssessReqVO {

    @Schema(description = "支付请求 IP（可选；为空则会从 paymentData 内自动识别）", example = "8.8.8.8")
    private String ip;

    @Schema(description = "前端传来的支付信息 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "paymentData 不能为空")
    private JsonNode paymentData;

}

