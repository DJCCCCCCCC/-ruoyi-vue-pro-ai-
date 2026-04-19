package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

@Data
public class PayRiskLlmAnalysisReport {

    private String mode;

    private String summary;

    private String fraudFamily;

    private String variantLabel;

    private String verdict;

    private String confidence;

    private String noveltyLevel;

    private Integer noveltyScore;

    private List<String> evidence;

    private List<String> suspiciousEntities;

    private List<String> preventionFocus;

    private List<String> recommendations;

    /**
     * 人物画像（对端身份/话术/施压方式等）
     */
    private PayRiskLlmPersonaProfile personaProfile;

    /**
     * 面向当前用户本人的个性化防诈说明与建议（结合年龄、性格、防诈了解程度等）
     */
    private PayRiskLlmTailoredUserGuidance tailoredUserGuidance;
}
