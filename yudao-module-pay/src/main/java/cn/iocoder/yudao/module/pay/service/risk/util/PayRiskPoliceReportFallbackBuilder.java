package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskPoliceReport;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskRelationTopology;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class PayRiskPoliceReportFallbackBuilder {

    public static PayRiskPoliceReport build(JsonNode context, PayRiskRelationTopology topology) {
        PayRiskPoliceReport report = new PayRiskPoliceReport();
        report.setMode("FALLBACK");
        report.setReportTitle("电信网络诈骗报案协助摘要（规则回退）");
        report.setUrgencyLevel(resolveUrgency(context));
        report.setFraudType("待核实（建议向民警说明对方话术与转账原因）");
        report.setCaseSummary(buildCaseSummary(context));
        report.setFraudModusOperandi(buildModusOperandi(context));
        report.setTimeline(buildTimeline(context));
        report.setTransferSummary(buildTransferSummary(context));
        report.setSuspectClues(buildSuspectClues(context, topology));
        report.setFundFlowAnalysis(buildFundFlow(context, topology));
        report.setEvidenceInventory(buildEvidenceInventory(context));
        report.setSystemWarnings(buildSystemWarnings(context));
        report.setPoliceChecklist(buildPoliceChecklist());
        report.setVictimActionItems(buildVictimActions());
        report.setPrintableStatement(buildPrintableStatement(context));
        report.setDisclaimer("本报告由系统根据现有聊天记录与线索自动整理（LLM 暂不可用），仅供参考，具体以公安机关调查为准。请勿编造或修改关键账号信息。");
        return report;
    }

    private static String resolveUrgency(JsonNode context) {
        if (Boolean.TRUE.equals(context.path("confirmedTransferred").asBoolean(false))) {
            return "URGENT";
        }
        JsonNode prior = context.path("priorAssessSnapshot");
        if (prior.has("riskLevel")) {
            String level = prior.path("riskLevel").asText("");
            if ("CRITICAL".equalsIgnoreCase(level) || "HIGH".equalsIgnoreCase(level)) {
                return "HIGH";
            }
        }
        return "MEDIUM";
    }

    private static String buildCaseSummary(JsonNode context) {
        int msgCount = context.path("paymentData").path("messageCount").asInt(0);
        String preset = context.path("paymentData").path("presetTitle").asText("");
        String amount = context.path("paymentData").path("amount").asText("");
        StringBuilder sb = new StringBuilder("受害人反映通过即时通讯与对方发生资金相关对话");
        if (msgCount > 0) {
            sb.append("（共 ").append(msgCount).append(" 条消息）");
        }
        if (!preset.isEmpty()) {
            sb.append("，场景近似：").append(preset);
        }
        if (!amount.isEmpty()) {
            sb.append("，聊天中出现金额 ").append(amount).append(" 元相关表述");
        }
        if (context.path("confirmedTransferred").asBoolean(false)) {
            sb.append("；受害人确认已发生转账。");
        } else {
            sb.append("；是否已转账待核实。");
        }
        return sb.toString();
    }

    private static String buildModusOperandi(JsonNode context) {
        JsonNode signals = context.path("paymentData").path("detectedSignals");
        List<String> parts = new ArrayList<>();
        if (signals.isArray()) {
            for (JsonNode s : signals) {
                parts.add(s.asText(""));
            }
        }
        if (parts.isEmpty()) {
            return "从聊天记录可见对方存在诱导转账、发送链接或催促付款等可疑行为，具体手法需结合完整对话向民警说明。";
        }
        return "聊天中命中可疑信号：" + String.join("、", parts) + "。建议重点向民警说明对方如何取得信任、如何催促转账及使用的收款方式。";
    }

    private static List<PayRiskPoliceReport.TimelineItem> buildTimeline(JsonNode context) {
        List<PayRiskPoliceReport.TimelineItem> items = new ArrayList<>();
        JsonNode messages = context.path("paymentData").path("messages");
        if (messages.isArray()) {
            int idx = 0;
            for (JsonNode m : messages) {
                String role = m.path("role").asText("");
                if ("system".equals(role)) {
                    continue;
                }
                PayRiskPoliceReport.TimelineItem item = new PayRiskPoliceReport.TimelineItem();
                item.setTime(m.path("timestamp").asText("待核实"));
                item.setPhase(idx == 0 ? "接触" : (idx < 3 ? "诱导" : "转账相关"));
                String sender = m.path("senderName").asText("peer".equals(role) ? "对方" : "我");
                String content = m.path("content").asText("");
                if (content.length() > 120) {
                    content = content.substring(0, 120) + "…";
                }
                item.setDescription("【" + sender + "】" + content);
                item.setRole("peer".equals(role) ? "suspect" : "victim");
                items.add(item);
                idx++;
            }
        }
        if (items.isEmpty()) {
            PayRiskPoliceReport.TimelineItem item = new PayRiskPoliceReport.TimelineItem();
            item.setTime("待核实");
            item.setPhase("接触");
            item.setDescription("暂无结构化聊天记录，请受害人向民警口述完整经过。");
            item.setRole("victim");
            items.add(item);
        }
        return items;
    }

    private static PayRiskPoliceReport.TransferSummary buildTransferSummary(JsonNode context) {
        PayRiskPoliceReport.TransferSummary summary = new PayRiskPoliceReport.TransferSummary();
        List<String> amounts = new ArrayList<>();
        JsonNode amountNode = context.path("paymentData").path("amount");
        if (!amountNode.isMissingNode() && !amountNode.isNull()) {
            amounts.add(amountNode.asText("") + " 元（聊天中出现，待核实是否实际转出）");
        }
        summary.setAmounts(amounts);
        summary.setChannels(new ArrayList<>());
        summary.setPayeeAccounts(extractPayeeHints(context));
        summary.setTotalLossEstimate(amounts.isEmpty() ? "待受害人核实" : amounts.get(0));
        summary.setTransferTimeHint(context.path("additionalVictimNotes").asText("待核实"));
        return summary;
    }

    private static List<String> extractPayeeHints(JsonNode context) {
        Set<String> hints = new LinkedHashSet<>();
        JsonNode links = context.path("paymentData").path("links");
        if (links.isArray()) {
            for (JsonNode l : links) {
                hints.add("链接：" + l.asText(""));
            }
        }
        JsonNode transactions = context.path("paymentData").path("transactions");
        if (transactions.isArray()) {
            for (JsonNode t : transactions) {
                JsonNode payee = t.path("payee");
                if (payee.has("account")) {
                    hints.add("收款账户：" + payee.path("account").asText(""));
                }
                if (payee.has("name")) {
                    hints.add("收款方：" + payee.path("name").asText(""));
                }
            }
        }
        return new ArrayList<>(hints);
    }

    private static List<PayRiskPoliceReport.SuspectClue> buildSuspectClues(JsonNode context,
                                                                           PayRiskRelationTopology topology) {
        List<PayRiskPoliceReport.SuspectClue> clues = new ArrayList<>();
        JsonNode links = context.path("paymentData").path("links");
        if (links.isArray()) {
            for (JsonNode l : links) {
                PayRiskPoliceReport.SuspectClue clue = new PayRiskPoliceReport.SuspectClue();
                clue.setCategory("link");
                clue.setValue(l.asText(""));
                clue.setSource("chat");
                clue.setNote("聊天中出现的可疑链接");
                clues.add(clue);
            }
        }
        JsonNode ipInfo = context.path("ipInfo");
        if (ipInfo != null && !ipInfo.isNull()) {
            PayRiskPoliceReport.SuspectClue clue = new PayRiskPoliceReport.SuspectClue();
            clue.setCategory("ip");
            clue.setValue(ipInfo.path("ip").asText(ipInfo.path("query").asText("")));
            clue.setSource("topology");
            clue.setNote("关联 IP 情报（脱敏）");
            clues.add(clue);
        }
        if (topology != null && topology.getNodes() != null) {
            topology.getNodes().stream()
                    .filter(n -> n.getRiskLevel() != null
                            && ("HIGH".equalsIgnoreCase(n.getRiskLevel()) || "CRITICAL".equalsIgnoreCase(n.getRiskLevel())))
                    .limit(5)
                    .forEach(n -> {
                        PayRiskPoliceReport.SuspectClue clue = new PayRiskPoliceReport.SuspectClue();
                        clue.setCategory("account");
                        clue.setValue(n.getLabel());
                        clue.setSource("topology");
                        clue.setNote("关系拓扑高风险节点");
                        clues.add(clue);
                    });
        }
        String notes = context.path("additionalVictimNotes").asText("").trim();
        if (!notes.isEmpty()) {
            PayRiskPoliceReport.SuspectClue clue = new PayRiskPoliceReport.SuspectClue();
            clue.setCategory("other");
            clue.setValue(notes.length() > 200 ? notes.substring(0, 200) + "…" : notes);
            clue.setSource("user_note");
            clue.setNote("受害人补充说明");
            clues.add(clue);
        }
        return clues;
    }

    private static PayRiskPoliceReport.FundFlowAnalysis buildFundFlow(JsonNode context,
                                                                      PayRiskRelationTopology topology) {
        PayRiskPoliceReport.FundFlowAnalysis flow = new PayRiskPoliceReport.FundFlowAnalysis();
        flow.setSummary("根据现有聊天与拓扑线索，资金可能经对方指定账户或第三方支付渠道转移；"
                + "具体去向需公安机关通过支付机构、银行流水协查确认。");
        List<String> paths = new ArrayList<>();
        if (topology != null && topology.getSummary() != null && topology.getSummary().getPayeeCount() != null
                && topology.getSummary().getPayeeCount() > 0) {
            paths.add("拓扑显示存在 " + topology.getSummary().getPayeeCount() + " 个收款方节点，可能存在多跳转移。");
        }
        paths.add("若受害人已向个人银行卡、支付宝、微信或虚拟币地址转账，资金可能在数分钟内被分拆转出。");
        flow.setInferredPaths(paths);
        flow.setFreezeTargets(new ArrayList<>(extractPayeeHints(context)));
        List<String> limits = new ArrayList<>();
        limits.add("本分析未接入实时银行流水，去向为基于聊天与公开拓扑的推测。");
        limits.add("请受害人尽快提供转账凭证、对方账号，以便民警启动紧急止付。");
        flow.setLimitations(limits);
        return flow;
    }

    private static List<String> buildEvidenceInventory(JsonNode context) {
        List<String> list = new ArrayList<>();
        list.add("完整聊天记录截图（含对方账号、昵称、时间）");
        list.add("转账/付款凭证（银行短信、支付账单、回单）");
        list.add("对方发送的链接、二维码、收款码图片");
        if (context.path("paymentData").path("voiceTranscripts").isArray()
                && context.path("paymentData").path("voiceTranscripts").size() > 0) {
            list.add("语音消息及转写文字（如有）");
        }
        if (context.path("embeddedImageCount").asInt(0) > 0) {
            list.add("聊天中的图片原图及 OCR 识别结果");
        }
        list.add("本系统生成的风险预警记录（如有）");
        return list;
    }

    private static List<String> buildSystemWarnings(JsonNode context) {
        List<String> warnings = new ArrayList<>();
        JsonNode prior = context.path("priorAssessSnapshot");
        if (prior != null && !prior.isNull() && prior.size() > 0) {
            int score = prior.path("riskScore").asInt(0);
            String level = prior.path("riskLevel").asText("");
            if (score > 0 || !level.isEmpty()) {
                warnings.add(String.format("系统曾评估风险分 %d，等级 %s", score, level.isEmpty() ? "未知" : level));
            }
            JsonNode verdict = prior.path("llmReport").path("verdict");
            if (verdict.isTextual() && !verdict.asText("").trim().isEmpty()) {
                warnings.add("系统研判：" + verdict.asText(""));
            }
            JsonNode decision = prior.path("decision");
            if (decision.has("action")) {
                warnings.add("系统建议动作：" + decision.path("action").asText(""));
            }
        }
        if (warnings.isEmpty()) {
            warnings.add("暂无系统事前预警记录；若曾弹出风险提醒，请向民警说明当时是否看到提示。");
        }
        return warnings;
    }

    private static List<String> buildPoliceChecklist() {
        List<String> list = new ArrayList<>();
        list.add("核查收款账户开户信息及近期流水");
        list.add("对涉案链接、域名做溯源与访问日志协查");
        list.add("查询 IP/设备关联的其他受害人");
        list.add("联系支付机构对可疑订单做紧急止付");
        return list;
    }

    private static List<String> buildVictimActions() {
        List<String> list = new ArrayList<>();
        list.add("立即拨打 110 或前往就近派出所报案，说明已转账并请求紧急止付");
        list.add("联系开户银行/支付平台客服，申请冻结或查询资金流向");
        list.add("保存全部聊天记录、转账凭证，勿删除对方联系方式");
        list.add("将本报告打印或截图，作为报案辅助材料提交民警");
        return list;
    }

    private static String buildPrintableStatement(JsonNode context) {
        StringBuilder sb = new StringBuilder();
        sb.append("民警同志您好，我要报案。我遭遇电信网络诈骗，现将经过说明如下。");
        sb.append(buildCaseSummary(context));
        sb.append(" ");
        sb.append(buildModusOperandi(context));
        String notes = context.path("additionalVictimNotes").asText("").trim();
        if (!notes.isEmpty()) {
            sb.append(" 补充说明：").append(notes);
        }
        if (context.path("confirmedTransferred").asBoolean(false)) {
            sb.append(" 我已向对方转账，请帮忙追查资金去向并止付。");
        }
        sb.append(" 以上是我目前能提供的全部情况，相关聊天记录与转账凭证我可以配合提交。");
        return sb.toString();
    }
}
