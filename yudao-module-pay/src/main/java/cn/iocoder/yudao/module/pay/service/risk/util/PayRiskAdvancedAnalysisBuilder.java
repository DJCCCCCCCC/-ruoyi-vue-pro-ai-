package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAdvancedAnalysis;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskRelationTopology;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class PayRiskAdvancedAnalysisBuilder {

    public static PayRiskAdvancedAnalysis build(JsonNode paymentData,
                                                JsonNode ipInfo,
                                                JsonNode whoisInfo,
                                                AppPayRiskAssessRespVO respVO) {
        PayRiskAdvancedAnalysis analysis = new PayRiskAdvancedAnalysis();
        analysis.setTimeline(buildTimeline(paymentData, whoisInfo, respVO));
        analysis.setCounterfactuals(buildCounterfactuals(paymentData, whoisInfo, respVO));
        analysis.setUniverse(buildUniverse(ipInfo, whoisInfo, respVO));
        analysis.setInterventions(buildInterventions(paymentData, respVO));
        return analysis;
    }

    private static List<PayRiskAdvancedAnalysis.TimelineEvent> buildTimeline(JsonNode paymentData,
                                                                             JsonNode whoisInfo,
                                                                             AppPayRiskAssessRespVO respVO) {
        List<PayRiskAdvancedAnalysis.TimelineEvent> timeline = new ArrayList<>();
        int messageCount = paymentData.path("messageCount").asInt(0);
        int linkCount = paymentData.path("linkCount").asInt(0);
        int signalCount = safeSummary(respVO.getTopologyInfo(), "signalCount");
        int transactionCount = safeSummary(respVO.getTopologyInfo(), "transactionCount");

        if (messageCount > 0 || hasText(paymentData.path("latestPeerMessage"))) {
            timeline.add(buildEvent("CONTACT", "建立信任", "对话上下文中已出现主动沟通或引导性话术，开始形成社工接触面。", 8, "MEDIUM"));
        }
        if (linkCount > 0 || hasWhoisRecords(whoisInfo)) {
            timeline.add(buildEvent("LINK", "投放链接", "当前上下文出现外部链接或域名情报，风险从单纯话术转向链路诱导。", 14, "HIGH"));
        }
        if (transactionCount > 0) {
            timeline.add(buildEvent("PAYMENT", "触发转账", "关系拓扑中已识别到资金流转，说明风险已进入实际支付阶段。", 20, "HIGH"));
        }
        if (signalCount > 0) {
            timeline.add(buildEvent("SPREAD", "关系扩散", "拓扑命中多条关系信号，显示异常不再局限于单个主体。", 12, signalCount >= 3 ? "HIGH" : "MEDIUM"));
        }
        if (respVO.getBehaviorInfo() != null && respVO.getBehaviorInfo().path("extraScore").asInt(0) > 0) {
            timeline.add(buildEvent("BEHAVIOR", "异常确认", "行为画像进一步抬高风险，说明用户操作节奏或设备交互与正常模式存在偏离。", respVO.getBehaviorInfo().path("extraScore").asInt(0), "MEDIUM"));
        }
        if (timeline.isEmpty()) {
            timeline.add(buildEvent("BASELINE", "初始观察", "当前主要依据来自基础风险评估结果，尚未形成明显的阶段化攻击链。", 0, "LOW"));
        }
        return timeline;
    }

    private static List<PayRiskAdvancedAnalysis.CounterfactualItem> buildCounterfactuals(JsonNode paymentData,
                                                                                          JsonNode whoisInfo,
                                                                                          AppPayRiskAssessRespVO respVO) {
        List<PayRiskAdvancedAnalysis.CounterfactualItem> items = new ArrayList<>();
        int currentScore = respVO.getRiskScore() == null ? 0 : respVO.getRiskScore();
        int whoisExtra = whoisInfo.path("extraScore").asInt(0);
        int behaviorExtra = respVO.getBehaviorInfo() == null ? 0 : respVO.getBehaviorInfo().path("extraScore").asInt(0);
        int topologyPressure = Math.min(18, safeSummary(respVO.getTopologyInfo(), "signalCount") * 4);

        if (paymentData.path("linkCount").asInt(0) > 0 || hasWhoisRecords(whoisInfo)) {
            items.add(buildCounterfactual("移除外链",
                    "如果本次场景没有出现域名或跳转链接，风险会怎样变化？",
                    Math.max(0, currentScore - Math.max(10, whoisExtra + 6)),
                    currentScore,
                    "外链和 Whois 情报是当前诱导链路的核心放大器。"));
        }
        if (behaviorExtra > 0) {
            items.add(buildCounterfactual("回归正常交互节奏",
                    "如果输入轨迹、点击节奏和设备行为恢复到常规模式，风险会怎样变化？",
                    Math.max(0, currentScore - behaviorExtra),
                    currentScore,
                    "行为异常是当前综合评分中的附加确认信号。"));
        }
        if (topologyPressure > 0) {
            items.add(buildCounterfactual("切断异常关系链",
                    "如果没有共享设备、聚合收款或中转链路，风险会怎样变化？",
                    Math.max(0, currentScore - topologyPressure),
                    currentScore,
                    "拓扑信号说明异常主体之间存在复用和扩散关系。"));
        }
        if (items.isEmpty()) {
            items.add(buildCounterfactual("仅保留基础上下文",
                    "如果移除当前附加情报，仅保留基础交易信息，风险会怎样变化？",
                    Math.max(0, currentScore - 8),
                    currentScore,
                    "当前风险主要来自综合信号叠加而非单一字段。"));
        }
        return items;
    }

    private static PayRiskAdvancedAnalysis.RelationshipUniverse buildUniverse(JsonNode ipInfo,
                                                                              JsonNode whoisInfo,
                                                                              AppPayRiskAssessRespVO respVO) {
        PayRiskAdvancedAnalysis.RelationshipUniverse universe = new PayRiskAdvancedAnalysis.RelationshipUniverse();
        int highRiskNodes = safeSummary(respVO.getTopologyInfo(), "highRiskNodeCount");
        int suspiciousClusters = safeSummary(respVO.getTopologyInfo(), "suspiciousClusterCount");
        int signalCount = safeSummary(respVO.getTopologyInfo(), "signalCount");

        universe.setSummary(String.format("当前风险宇宙中已识别 %d 个高风险节点、%d 个可疑簇、%d 条关系信号，说明本次风险可能不是孤立事件。", highRiskNodes, suspiciousClusters, signalCount));
        universe.setRepeatedIndicators(buildRepeatedIndicators(ipInfo, whoisInfo, respVO));
        universe.setWatchList(buildWatchList(ipInfo, whoisInfo, respVO));
        universe.setCampaignHints(buildCampaignHints(respVO));
        return universe;
    }

    private static List<String> buildRepeatedIndicators(JsonNode ipInfo,
                                                        JsonNode whoisInfo,
                                                        AppPayRiskAssessRespVO respVO) {
        List<String> indicators = new ArrayList<>();
        if (hasText(ipInfo.path("ip"))) {
            indicators.add("关键 IP：" + ipInfo.path("ip").asText());
        }
        if (hasWhoisRecords(whoisInfo)) {
            whoisInfo.path("records").forEach(record -> {
                if (indicators.size() >= 4) {
                    return;
                }
                if (hasText(record.path("domain"))) {
                    indicators.add("域名：" + record.path("domain").asText());
                }
            });
        }
        if (respVO.getTopologyInfo() != null && respVO.getTopologyInfo().getSignals() != null) {
            for (PayRiskRelationTopology.Signal signal : respVO.getTopologyInfo().getSignals()) {
                if (indicators.size() >= 6) {
                    break;
                }
                indicators.add("关系信号：" + signal.getTitle());
            }
        }
        if (indicators.isEmpty()) {
            indicators.add("当前尚未识别出可复用的关键指示器。");
        }
        return indicators;
    }

    private static List<String> buildWatchList(JsonNode ipInfo,
                                               JsonNode whoisInfo,
                                               AppPayRiskAssessRespVO respVO) {
        Set<String> watchList = new LinkedHashSet<>();
        PayRiskLlmAnalysisReport llmReport = respVO.getLlmReport();
        if (llmReport != null && llmReport.getSuspiciousEntities() != null) {
            watchList.addAll(llmReport.getSuspiciousEntities());
        }
        if (respVO.getTopologyInfo() != null && respVO.getTopologyInfo().getNodes() != null) {
            respVO.getTopologyInfo().getNodes().stream()
                    .filter(node -> "HIGH".equalsIgnoreCase(node.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(node.getRiskLevel()))
                    .limit(3)
                    .forEach(node -> watchList.add("高风险节点：" + node.getLabel()));
        }
        if (hasText(ipInfo.path("org"))) {
            watchList.add("网络归属：" + ipInfo.path("org").asText());
        }
        if (hasWhoisRecords(whoisInfo) && watchList.size() < 6) {
            whoisInfo.path("records").forEach(record -> {
                if (watchList.size() >= 6) {
                    return;
                }
                if (hasText(record.path("domain"))) {
                    watchList.add("持续观察域名：" + record.path("domain").asText());
                }
            });
        }
        if (watchList.isEmpty()) {
            watchList.add("暂无明确观察对象，建议继续结合后续请求累积画像。");
        }
        return new ArrayList<>(watchList);
    }

    private static List<String> buildCampaignHints(AppPayRiskAssessRespVO respVO) {
        List<String> hints = new ArrayList<>();
        if (respVO.getTopologyInfo() != null && respVO.getTopologyInfo().getSignals() != null) {
            respVO.getTopologyInfo().getSignals().stream().limit(4).forEach(signal ->
                    hints.add(signal.getTitle() + "：说明相同关系模板可能在多个主体之间复用"));
        }
        if (respVO.getBehaviorInfo() != null && respVO.getBehaviorInfo().path("mocked").asBoolean(false)) {
            hints.add("当前行为画像为模拟生成，建议后续补真实设备交互数据做团伙对比。");
        }
        if (hints.isEmpty()) {
            hints.add("当前尚未观察到明显的批量化剧本痕迹。");
        }
        return hints;
    }

    private static List<PayRiskAdvancedAnalysis.InterventionAction> buildInterventions(JsonNode paymentData,
                                                                                        AppPayRiskAssessRespVO respVO) {
        List<PayRiskAdvancedAnalysis.InterventionAction> actions = new ArrayList<>();
        String riskLevel = respVO.getRiskLevel() == null ? "LOW" : respVO.getRiskLevel();
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            actions.add(buildAction("P0", "BLOCK", "强制人工复核", "对当前支付链路进入人工审核或临时拦截，避免直接放行。", "AUTO"));
            actions.add(buildAction("P1", "VERIFY", "二次确认收款主体", "校验聊天身份、收款账户和实名主体是否一致。", "SEMI_AUTO"));
        } else if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            actions.add(buildAction("P1", "CHALLENGE", "发起二次确认", "通过延迟确认、风险问答或短信确认降低误付概率。", "AUTO"));
        }
        if (paymentData.path("linkCount").asInt(0) > 0) {
            actions.add(buildAction("P1", "LINK_GUARD", "限制外链跳转", "在交易前对链接来源进行再次校验，并提示域名风险。", "AUTO"));
        }
        if (safeSummary(respVO.getTopologyInfo(), "signalCount") > 0) {
            actions.add(buildAction("P1", "NETWORK_WATCH", "扩展关联排查", "对共享设备、IP、域名和收款主体做横向扩散分析。", "SEMI_AUTO"));
        }
        if (actions.isEmpty()) {
            actions.add(buildAction("P2", "OBSERVE", "持续观察", "当前先保留观察，不立即拦截，但记录后续画像变化。", "MANUAL"));
        }
        return actions;
    }

    private static PayRiskAdvancedAnalysis.TimelineEvent buildEvent(String stage,
                                                                    String title,
                                                                    String description,
                                                                    Integer riskDelta,
                                                                    String evidenceLevel) {
        PayRiskAdvancedAnalysis.TimelineEvent event = new PayRiskAdvancedAnalysis.TimelineEvent();
        event.setStage(stage);
        event.setTitle(title);
        event.setDescription(description);
        event.setRiskDelta(riskDelta);
        event.setEvidenceLevel(evidenceLevel);
        return event;
    }

    private static PayRiskAdvancedAnalysis.CounterfactualItem buildCounterfactual(String title,
                                                                                  String hypothesis,
                                                                                  Integer expectedRiskScore,
                                                                                  Integer currentScore,
                                                                                  String reason) {
        PayRiskAdvancedAnalysis.CounterfactualItem item = new PayRiskAdvancedAnalysis.CounterfactualItem();
        item.setTitle(title);
        item.setHypothesis(hypothesis);
        item.setExpectedRiskScore(expectedRiskScore);
        item.setDelta(expectedRiskScore == null || currentScore == null ? 0 : expectedRiskScore - currentScore);
        item.setReason(reason);
        return item;
    }

    private static PayRiskAdvancedAnalysis.InterventionAction buildAction(String priority,
                                                                          String type,
                                                                          String title,
                                                                          String description,
                                                                          String automationLevel) {
        PayRiskAdvancedAnalysis.InterventionAction action = new PayRiskAdvancedAnalysis.InterventionAction();
        action.setPriority(priority);
        action.setType(type);
        action.setTitle(title);
        action.setDescription(description);
        action.setAutomationLevel(automationLevel);
        return action;
    }

    private static boolean hasText(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() && !node.asText("").trim().isEmpty();
    }

    private static boolean hasWhoisRecords(JsonNode whoisInfo) {
        return whoisInfo != null && whoisInfo.path("records").isArray() && whoisInfo.path("records").size() > 0;
    }

    private static int safeSummary(PayRiskRelationTopology topology, String key) {
        if (topology == null || topology.getSummary() == null) {
            return 0;
        }
        switch (key) {
            case "signalCount":
                return topology.getSummary().getSignalCount() == null ? 0 : topology.getSummary().getSignalCount();
            case "transactionCount":
                return topology.getSummary().getTransactionCount() == null ? 0 : topology.getSummary().getTransactionCount();
            case "highRiskNodeCount":
                return topology.getSummary().getHighRiskNodeCount() == null ? 0 : topology.getSummary().getHighRiskNodeCount();
            case "suspiciousClusterCount":
                return topology.getSummary().getSuspiciousClusterCount() == null ? 0 : topology.getSummary().getSuspiciousClusterCount();
            default:
                return 0;
        }
    }
}
