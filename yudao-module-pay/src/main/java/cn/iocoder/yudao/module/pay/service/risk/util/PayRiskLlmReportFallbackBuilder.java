package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class PayRiskLlmReportFallbackBuilder {

    public static PayRiskLlmAnalysisReport build(JsonNode context) {
        PayRiskLlmAnalysisReport report = new PayRiskLlmAnalysisReport();
        report.setMode("FALLBACK");
        int riskScore = context.path("assessment").path("riskScore").asInt(0);
        String riskLevel = context.path("assessment").path("riskLevel").asText("LOW");
        int signalCount = context.path("topology").path("summary").path("signalCount").asInt(0);
        int highRiskNodes = context.path("topology").path("summary").path("highRiskNodeCount").asInt(0);
        int behaviorExtraScore = context.path("behavior").path("extraScore").asInt(0);
        int whoisExtraScore = context.path("whois").path("extraScore").asInt(0);

        report.setSummary(String.format("当前综合风险评分为 %d 分，风险等级为 %s，关系拓扑、行为画像与情报结果显示该交易上下文存在多维异常信号。", riskScore, riskLevel));
        report.setVerdict(buildVerdict(riskLevel, signalCount, behaviorExtraScore, whoisExtraScore));
        report.setConfidence(resolveConfidence(riskScore, signalCount, highRiskNodes));
        report.setEvidence(buildEvidence(context, signalCount, highRiskNodes, behaviorExtraScore, whoisExtraScore));
        report.setSuspiciousEntities(buildSuspiciousEntities(context));
        report.setRecommendations(buildRecommendations(riskLevel, signalCount, highRiskNodes));
        return report;
    }

    private static String buildVerdict(String riskLevel, int signalCount, int behaviorExtraScore, int whoisExtraScore) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            return String.format("该请求疑似存在高风险社工诱导或异常转账链路，已命中 %d 条关系信号，且行为加分 %d、Whois 加分 %d。", signalCount, behaviorExtraScore, whoisExtraScore);
        }
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            return "该请求存在一定可疑特征，建议结合聊天上下文、设备归因和账户历史进一步复核。";
        }
        return "当前未发现强烈异常，但建议继续保留对账户、设备和外链情报的持续观察。";
    }

    private static String resolveConfidence(int riskScore, int signalCount, int highRiskNodes) {
        if (riskScore >= 75 || signalCount >= 3 || highRiskNodes >= 2) {
            return "HIGH";
        }
        if (riskScore >= 40 || signalCount >= 1) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static List<String> buildEvidence(JsonNode context, int signalCount, int highRiskNodes, int behaviorExtraScore, int whoisExtraScore) {
        List<String> evidence = new ArrayList<>();
        if (signalCount > 0) {
            evidence.add(String.format("人物关系拓扑命中 %d 条风险信号，涉及 %d 个高风险节点。", signalCount, highRiskNodes));
        }
        if (behaviorExtraScore > 0) {
            evidence.add(String.format("生物行为分析额外加分 %d，说明交互节奏、输入轨迹或设备侧行为存在异常。", behaviorExtraScore));
        }
        if (whoisExtraScore > 0) {
            evidence.add(String.format("Whois 情报额外加分 %d，说明外链域名存在注册时间、隐私保护或主体归属方面的疑点。", whoisExtraScore));
        }
        JsonNode riskFactors = context.path("assessment").path("riskFactors");
        if (riskFactors.isArray() && riskFactors.size() > 0) {
            evidence.add("当前命中的核心风险因子包括：" + joinArray(riskFactors, 3));
        }
        if (evidence.isEmpty()) {
            evidence.add("当前主要依据来自综合风险评分和基础上下文，暂未发现更强的结构化异常证据。");
        }
        return evidence;
    }

    private static List<String> buildSuspiciousEntities(JsonNode context) {
        List<String> entities = new ArrayList<>();
        JsonNode signals = context.path("topology").path("signals");
        if (signals.isArray()) {
            for (JsonNode signal : signals) {
                if (entities.size() >= 5) {
                    break;
                }
                String title = signal.path("title").asText("");
                if (!title.isEmpty()) {
                    entities.add("关系信号：" + title);
                }
            }
        }
        JsonNode records = context.path("whois").path("records");
        if (records.isArray()) {
            for (JsonNode record : records) {
                if (entities.size() >= 6) {
                    break;
                }
                String domain = record.path("domain").asText("");
                if (!domain.isEmpty()) {
                    entities.add("域名：" + domain);
                }
            }
        }
        JsonNode ipInfo = context.path("ipInfo");
        String ip = ipInfo.path("ip").asText("");
        if (!ip.isEmpty()) {
            entities.add("IP：" + ip);
        }
        if (entities.isEmpty()) {
            entities.add("当前未抽取到明确的高风险主体，建议结合原始上下文继续复核。");
        }
        return entities;
    }

    private static List<String> buildRecommendations(String riskLevel, int signalCount, int highRiskNodes) {
        List<String> recommendations = new ArrayList<>();
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            recommendations.add("优先对当前支付链路执行人工复核或临时拦截，避免直接放行。");
            recommendations.add("围绕关联设备、账户、IP 和域名做横向排查，确认是否存在团伙化复用。");
        } else {
            recommendations.add("将该请求纳入重点观察名单，持续关注后续交易、设备与关系链变化。");
        }
        if (signalCount > 0 || highRiskNodes > 0) {
            recommendations.add("重点审查拓扑中被高亮的主体和链路，核对收款主体、聊天身份和外链是否一致。");
        }
        recommendations.add("在前端交互中补充更明确的风险提示与二次确认流程。");
        return recommendations;
    }

    private static String joinArray(JsonNode arrayNode, int limit) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (values.size() >= limit) {
                break;
            }
            String value = item.asText("");
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? "暂无" : String.join("、", values);
    }
}
