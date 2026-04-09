package cn.iocoder.yudao.module.pay.dal.dataobject.risk;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 支付风险评估记录 DO
 */
@TableName("pay_risk_assess_record")
@KeySequence("pay_risk_assess_record_seq")
@Data
public class PayRiskAssessRecordDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 来源（例如：chat-risk-test-page）
     */
    private String source;
    /**
     * 场景（例如：WECHAT_CHAT_RISK）
     */
    private String scene;
    /**
     * 评估 IP
     */
    private String ip;
    /**
     * 风险评分
     */
    private Integer riskScore;
    /**
     * 风险等级
     */
    private String riskLevel;
    /**
     * 风险因素 JSON 字符串
     */
    private String riskFactorsJson;
    /**
     * 深度分析
     */
    private String deepAnalysis;
    /**
     * 原始入参 paymentData JSON 字符串
     */
    private String paymentDataJson;
    /**
     * 脱敏后的 IP 情报 JSON 字符串
     */
    private String ipInfoJson;

    /**
     * Whois 情报 JSON 字符串
     */
    private String whoisInfoJson;

    /**
     * 生物行为分析 JSON 字符串
     */
    private String behaviorInfoJson;

    /**
     * 关系拓扑 JSON 字符串
     */
    private String topologyInfoJson;

    private String llmReportJson;

    private String advancedAnalysisJson;
}
