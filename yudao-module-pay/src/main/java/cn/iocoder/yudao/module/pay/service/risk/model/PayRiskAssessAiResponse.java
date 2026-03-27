package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

@Data
public class PayRiskAssessAiResponse {

    /**
     * 风险分数：0-100（越高越高风险）
     */
    private Integer riskScore;

    /**
     * 风险等级：LOW|MEDIUM|HIGH|CRITICAL
     */
    private String riskLevel;

    /**
     * 深度分析：包含关键原因与风险点
     */
    private String deepAnalysis;

    /**
     * 风险因素列表
     */
    private List<String> riskFactors;

}

