package cn.iocoder.yudao.module.pay.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付风险词库 DO
 */
@TableName("pay_risk_term")
@KeySequence("pay_risk_term_seq")
@Data
public class PayRiskTermDO extends BaseDO {

    @TableId
    private Long id;

    /** 风险词文案 */
    private String term;

    /** 分类 */
    private String category;

    /** 状态：0 启用 1 禁用 */
    private Integer status;

    /** 说明 */
    private String description;

    /** 来源：MANUAL / AUTO_ASSESS */
    private String sourceType;

    /** 累计命中次数 */
    private Long hitCount;

    /** 首次出现时间 */
    private LocalDateTime firstSeenTime;

    /** 最近命中时间 */
    private LocalDateTime lastHitTime;

    /** 首次命中的评估记录 */
    private Long firstRecordId;
}
