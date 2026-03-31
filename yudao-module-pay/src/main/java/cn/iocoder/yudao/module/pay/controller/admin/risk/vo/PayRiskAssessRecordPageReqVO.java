package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 支付风险评估记录分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayRiskAssessRecordPageReqVO extends PageParam {

    @Schema(description = "风险等级", example = "HIGH")
    private String riskLevel;

    @Schema(description = "场景", example = "WECHAT_CHAT_RISK")
    private String scene;

    @Schema(description = "来源", example = "chat-risk-test-page")
    private String source;

    @Schema(description = "IP", example = "8.8.8.8")
    private String ip;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}

