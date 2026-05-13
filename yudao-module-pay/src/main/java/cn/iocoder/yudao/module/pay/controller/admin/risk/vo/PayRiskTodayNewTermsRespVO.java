package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 今日新增风险词列表 Response VO")
@Data
public class PayRiskTodayNewTermsRespVO {

    @Schema(description = "今日首次在历史记录中出现过的风险因子文案")
    private List<PayRiskTodayNewTermItemVO> terms;
}
