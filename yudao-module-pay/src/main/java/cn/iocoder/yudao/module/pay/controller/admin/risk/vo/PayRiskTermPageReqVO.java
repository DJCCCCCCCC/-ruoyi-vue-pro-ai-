package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 风险词库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayRiskTermPageReqVO extends PageParam {

    @Schema(description = "风险词（模糊）")
    private String term;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "状态：0 启用 1 禁用")
    private Integer status;

    @Schema(description = "来源：MANUAL / AUTO_ASSESS")
    private String sourceType;

    @Schema(description = "首次出现时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] firstSeenTime;
}
