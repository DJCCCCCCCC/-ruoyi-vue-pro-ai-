package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class PayRiskRelationTopology {

    private Summary summary;

    private List<Node> nodes;

    private List<Edge> edges;

    private List<Signal> signals;

    @Data
    public static class Summary {
        private Integer nodeCount;
        private Integer edgeCount;
        private Integer participantCount;
        private Integer payerCount;
        private Integer payeeCount;
        private Integer transactionCount;
        private Integer signalCount;
        private Integer sharedAttributeCount;
        private Integer highRiskNodeCount;
        private Integer highRiskEdgeCount;
        private Integer suspiciousClusterCount;
    }

    @Data
    public static class Node {
        private String id;
        private String label;
        private String type;
        private String role;
        private String riskLevel;
        private List<String> tags;
        private Map<String, Object> meta;
    }

    @Data
    public static class Edge {
        private String source;
        private String target;
        private String type;
        private String label;
        private String riskLevel;
        private Integer count;
        private BigDecimal amount;
        private Map<String, Object> meta;
    }

    @Data
    public static class Signal {
        private String code;
        private String level;
        private String title;
        private String description;
        private Integer score;
        private List<String> relatedNodeIds;
    }
}
