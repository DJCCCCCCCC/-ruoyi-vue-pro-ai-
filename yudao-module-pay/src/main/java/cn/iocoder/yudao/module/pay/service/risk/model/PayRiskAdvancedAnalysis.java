package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

@Data
public class PayRiskAdvancedAnalysis {

    private List<TimelineEvent> timeline;

    private List<CounterfactualItem> counterfactuals;

    private RelationshipUniverse universe;

    private List<CaseSimilarityMatch> caseMatches;

    private List<InterventionAction> interventions;

    @Data
    public static class CaseSimilarityMatch {
        private Long recordId;
        private String scene;
        private String source;
        private String riskLevel;
        private Integer riskScore;
        private Double similarity;
        private Integer bonusScore;
        private String matchedReasons;
        private String summary;
    }

    @Data
    public static class TimelineEvent {
        private String stage;
        private String title;
        private String description;
        private Integer riskDelta;
        private String evidenceLevel;
    }

    @Data
    public static class CounterfactualItem {
        private String title;
        private String hypothesis;
        private Integer expectedRiskScore;
        private Integer delta;
        private String reason;
    }

    @Data
    public static class RelationshipUniverse {
        private String summary;
        private List<String> repeatedIndicators;
        private List<String> watchList;
        private List<String> campaignHints;
    }

    @Data
    public static class InterventionAction {
        private String priority;
        private String type;
        private String title;
        private String description;
        private String automationLevel;
    }
}
