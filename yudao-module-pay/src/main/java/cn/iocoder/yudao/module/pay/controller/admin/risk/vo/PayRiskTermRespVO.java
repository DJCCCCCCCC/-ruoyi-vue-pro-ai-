package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 风险词库 Response VO")
@Data
public class PayRiskTermRespVO {

    private Long id;
    private String term;
    private String category;
    private Integer status;
    private String description;
    private String sourceType;
    private Long hitCount;
    private LocalDateTime firstSeenTime;
    private LocalDateTime lastHitTime;
    private Long firstRecordId;
    private LocalDateTime createTime;
}
