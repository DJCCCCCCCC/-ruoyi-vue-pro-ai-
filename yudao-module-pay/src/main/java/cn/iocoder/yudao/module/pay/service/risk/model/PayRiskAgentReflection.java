package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

/**
 * Agentic 反思流结果：初判 Agent、质疑 Agent、仲裁 Agent。
 */
@Data
public class PayRiskAgentReflection {

    /** 反思流版本，便于后续 prompt/schema 升级与审计回放。 */
    private String version;

    /** 判定 Agent：给出可被质疑的初步观点。 */
    private AssessorOpinion assessor;

    /** 质疑 Agent：专门检查证据不足、逻辑跳跃和幻觉。 */
    private SkepticOpinion skeptic;

    /** 仲裁 Agent：整合双方证据后给出最终裁决。 */
    private ArbiterOpinion arbiter;

    @Data
    public static class AssessorOpinion {
        private Integer preliminaryScore;
        private String riskLevel;
        private String recommendation;
        private Double confidence;
        private List<EvidenceClaim> coreClaims;
        private List<String> counterSignalsSeen;
        private List<String> uncertainties;
    }

    @Data
    public static class SkepticOpinion {
        private Double overallChallengeStrength;
        private List<ChallengeIssue> issues;
        private List<String> missedCounterEvidence;
        private List<String> hallucinationFlags;
        private Integer revisedScoreSuggestion;
    }

    @Data
    public static class ArbiterOpinion {
        private Integer finalScore;
        private String finalRiskLevel;
        private String finalDecision;
        private Double confidence;
        private Double uncertainty;
        private String disputeLevel;
        private Boolean needManualReview;
        private List<ArbitrationPoint> adoptedPoints;
        private List<ArbitrationPoint> rejectedPoints;
        private List<String> manualReviewFocus;
        private String summary;
    }

    @Data
    public static class EvidenceClaim {
        private String claim;
        private List<String> evidenceIds;
        private String reasoning;
        private Double confidence;
    }

    @Data
    public static class ChallengeIssue {
        private String targetClaim;
        private String issueType;
        private String description;
        private String severity;
        private String suggestedAdjustment;
    }

    @Data
    public static class ArbitrationPoint {
        private String from;
        private String point;
        private String reason;
    }
}
