package cn.iocoder.yudao.module.pay.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 风险词与评估记录命中关联 DO
 */
@TableName("pay_risk_term_hit")
@KeySequence("pay_risk_term_hit_seq")
@Data
public class PayRiskTermHitDO extends BaseDO {

    @TableId
    private Long id;

    private Long termId;

    private Long recordId;
}
