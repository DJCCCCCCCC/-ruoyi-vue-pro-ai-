package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmPersonaProfile;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmTailoredUserGuidance;
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
        report.setFraudFamily(resolveFraudFamily(riskLevel, signalCount));
        report.setVariantLabel("规则回退生成，待 LLM 恢复后细化变体命名");
        report.setNoveltyLevel(resolveNoveltyLevel(riskScore, signalCount, highRiskNodes));
        report.setNoveltyScore(resolveNoveltyScore(riskScore, signalCount, highRiskNodes));
        report.setVerdict(buildVerdict(riskLevel, signalCount, behaviorExtraScore, whoisExtraScore));
        report.setConfidence(resolveConfidence(riskScore, signalCount, highRiskNodes));
        report.setEvidence(buildEvidence(context, signalCount, highRiskNodes, behaviorExtraScore, whoisExtraScore));
        report.setSuspiciousEntities(buildSuspiciousEntities(context));
        List<String> recommendations = buildRecommendations(riskLevel, signalCount, highRiskNodes);
        report.setRecommendations(recommendations);
        report.setPreventionFocus(buildPreventionFocus(riskLevel, signalCount, highRiskNodes, recommendations));
        report.setPersonaProfile(buildPersonaProfile(context, riskLevel, riskScore));
        report.setTailoredUserGuidance(buildTailoredUserGuidance(context, riskLevel, riskScore));
        return report;
    }

    private static PayRiskLlmTailoredUserGuidance buildTailoredUserGuidance(JsonNode context, String riskLevel, int riskScore) {
        PayRiskLlmTailoredUserGuidance g = new PayRiskLlmTailoredUserGuidance();
        JsonNode profile = context.path("userProfile");
        String age = profile.path("ageBand").asText("").trim();
        String personality = profile.path("personalityHint").asText("").trim();
        String literacy = profile.path("riskLiteracy").asText("").trim();
        boolean hasProfile = !age.isEmpty() || !personality.isEmpty() || !literacy.isEmpty();

        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            g.setWhyLikelyScamPlainLanguage(
                    "当前系统评分与多条线索显示：对方话术或链路与「先让你紧张、再让你快点转账/点链接」这类常见诈骗节奏高度相似。"
                            + (hasProfile ? "（已按你填写的年龄段与个性做粗颗粒提示，细节待模型恢复后补充。）" : ""));
        } else if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            g.setWhyLikelyScamPlainLanguage(
                    "目前还不能百分百认定对方是骗子，但已出现值得警惕的组合信号；很多骗局会从「看似正常」逐步过渡到要钱或要验证码。");
        } else {
            g.setWhyLikelyScamPlainLanguage(
                    "当前更像低风险场景；若对方突然开始催转账、要屏幕共享或索要验证码，风险会快速升高，需要立刻提高警惕。");
        }

        List<String> tips = new ArrayList<>();
        tips.add("先暂停任何转账、屏幕共享或下载对方发来的陌生应用。");
        if ("SENIOR".equalsIgnoreCase(age) || "MIDDLE_AGED".equalsIgnoreCase(age)) {
            tips.add("可以打电话给子女或银行官方客服电话（自己从银行卡背面查找号码），复述对方说法，请第三方帮你判断。");
        } else if ("UNDER_18".equalsIgnoreCase(age)) {
            tips.add("把聊天记录截图给家长或老师，不要独自按对方指示操作支付。");
        } else {
            tips.add("把关键聊天与链接截图保存，换渠道（当面或电话）向对方本人或平台官方核实。");
        }
        if ("ANXIOUS".equalsIgnoreCase(personality) || "AUTHORITY_TRUSTING".equalsIgnoreCase(personality)) {
            tips.add("越是自称「官方」「紧急」越要慢下来：真公检法与正规平台不会用社交软件催你立刻转款。");
        }
        if ("DIGITAL_NOVICE".equalsIgnoreCase(personality) || "LOW".equalsIgnoreCase(literacy)) {
            tips.add("不要向对方透露短信验证码、支付密码；任何「帮你操作手机」的人都要警惕。");
        }
        tips.add("若已付款或泄露验证码，请尽快联系银行冻结并报警。");
        g.setPreventionTipsForThisUser(tips);

        g.setReassuranceLine("遇到可疑情况很常见，先停下来核实不是小题大做；你愿意多确认一步，就是在保护自己和家人。");
        return g;
    }

    private static PayRiskLlmPersonaProfile buildPersonaProfile(JsonNode context, String riskLevel, int riskScore) {
        PayRiskLlmPersonaProfile p = new PayRiskLlmPersonaProfile();
        JsonNode payment = context.path("paymentData");
        int msgCount = payment.path("messageCount").asInt(0);
        int linkCount = payment.path("linkCount").asInt(0);
        String latest = payment.path("latestPeerMessage").asText("").trim();
        String snippet = latest.length() > 160 ? latest.substring(0, 160) + "…" : latest;

        if (snippet.isEmpty()) {
            p.setSummary("未提供对方近期话术摘录，人物画像仅依据交易与情报侧结构化信号做粗颗粒描述。");
        } else {
            p.setSummary("基于对方最近可见话术摘录，其在对话中呈现可观察的互动特征（规则回退，待 LLM 恢复后细描）。");
        }
        p.setClaimedOrImpliedRole("待模型从对话中抽取自称或暗示身份");
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            p.setInferredArchetype("高风险上下文下的沟通对端（常与转账催促、外链诱导等组合相关）");
        } else if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            p.setInferredArchetype("存在若干可疑迹象的沟通对端，身份与意图需进一步对齐");
        } else {
            p.setInferredArchetype("未见明显异常话术定型的沟通对端");
        }

        List<String> traits = new ArrayList<>();
        if (msgCount > 0) {
            traits.add("对话消息条数（上下文规模）：约 " + msgCount);
        }
        if (linkCount > 0) {
            traits.add("消息中出现外链或跳转线索：" + linkCount + " 处");
        }
        if (!snippet.isEmpty()) {
            traits.add("最近对方话术片段：「" + snippet + "」");
        }
        JsonNode detected = payment.path("detectedSignals");
        if (detected.isArray() && detected.size() > 0) {
            List<String> sig = new ArrayList<>();
            for (JsonNode d : detected) {
                if (sig.size() >= 4) {
                    break;
                }
                String s = d.asText("").trim();
                if (!s.isEmpty()) {
                    sig.add(s);
                }
            }
            if (!sig.isEmpty()) {
                traits.add("本地规则命中信号：" + String.join("、", sig));
            }
        }
        if (traits.isEmpty()) {
            traits.add("结构化上下文中对话特征有限，建议补充聊天记录后再做人格与话术复盘。");
        }
        p.setCommunicationTraits(traits);

        List<String> pressure = new ArrayList<>();
        if (riskScore >= 65) {
            pressure.add("综合风险分偏高，需关注是否存在催促付款、限时操作等施压组合（规则推断）");
        }
        if (linkCount > 0) {
            pressure.add("存在外链投递，常与「点击完成核验/领取补贴」等诱导组合相关");
        }
        if (pressure.isEmpty()) {
            pressure.add("未从规则侧单独拆分出强烈的话术操控项，交由 LLM 恢复后细化。");
        }
        p.setPressureAndControlSignals(pressure);
        return p;
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

    private static String resolveFraudFamily(String riskLevel, int signalCount) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            return signalCount > 0 ? "复合型社工诱导（涉关系链）" : "异常支付/转账上下文（待细分族系）";
        }
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            return "可疑交易上下文（未定型）";
        }
        return "未见明显定型诈骗族系";
    }

    private static String resolveNoveltyLevel(int riskScore, int signalCount, int highRiskNodes) {
        if (signalCount >= 2 || highRiskNodes >= 2 || riskScore >= 70) {
            return "EMERGING_PATTERN";
        }
        if (riskScore >= 35 || signalCount >= 1) {
            return "KNOWN_VARIANT";
        }
        return "KNOWN_VARIANT";
    }

    private static int resolveNoveltyScore(int riskScore, int signalCount, int highRiskNodes) {
        int score = 30 + Math.min(40, riskScore / 2) + Math.min(20, signalCount * 6) + Math.min(10, highRiskNodes * 5);
        return Math.min(100, Math.max(0, score));
    }

    private static List<String> buildPreventionFocus(String riskLevel, int signalCount, int highRiskNodes,
                                                     List<String> recommendations) {
        List<String> focus = new ArrayList<>();
        if ("CRITICAL".equalsIgnoreCase(riskLevel) || "HIGH".equalsIgnoreCase(riskLevel)) {
            focus.add("对高风险评分与关键实体自动触发二次核验与人审队列");
        }
        if (signalCount > 0 || highRiskNodes > 0) {
            focus.add("强化关系拓扑可视化与收款主体一致性校验");
        }
        focus.add("沉淀本案例结构化标签，纳入相似案例检索与提示词上下文");
        if (!recommendations.isEmpty() && focus.size() < 4) {
            focus.add(recommendations.get(0));
        }
        return focus;
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
