package cn.iocoder.yudao.module.pay.service.risk.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PayRiskLlmReportPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "你是一名支付风控与社工诈骗研判专家。"
                    + "你会收到脱敏后的支付上下文、IP 情报、Whois 情报、生物行为分析和人物关系拓扑摘要。"
                    + "请基于这些上下文给出结构化研判，不要输出 markdown，不要输出代码块。"
                    + "请只返回一个 JSON 对象，字段必须包含 mode、summary、verdict、confidence、evidence、suspiciousEntities、recommendations。"
                    + "summary、verdict、evidence、suspiciousEntities、recommendations 的内容必须使用中文。";

    public static String buildUserPrompt(String contextJson) {
        return "以下是脱敏后的综合风控上下文：\n"
                + "<RiskContext>" + contextJson + "</RiskContext>\n\n"
                + "请返回如下 JSON：\n"
                + "{\n"
                + "  \"mode\": \"LLM\",\n"
                + "  \"summary\": \"用 1 到 2 句话总结整体风险判断\",\n"
                + "  \"verdict\": \"一句话说明是否疑似诈骗/异常转账，以及主要原因\",\n"
                + "  \"confidence\": \"LOW|MEDIUM|HIGH\",\n"
                + "  \"evidence\": [\"列出关键证据 1\", \"列出关键证据 2\"],\n"
                + "  \"suspiciousEntities\": [\"列出可疑主体、域名、设备、IP 或账户\"],\n"
                + "  \"recommendations\": [\"给出处置建议 1\", \"给出处置建议 2\"]\n"
                + "}";
    }
}
