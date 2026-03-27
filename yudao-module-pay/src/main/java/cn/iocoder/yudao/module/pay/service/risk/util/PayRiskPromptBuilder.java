package cn.iocoder.yudao.module.pay.service.risk.util;

import lombok.experimental.UtilityClass;

/**
 * 构建 DeepSeek Prompt（强制 JSON 输出）。
 */
@UtilityClass
public class PayRiskPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "你是首席支付风险评估专家。\n" +
                    "你将收到：\n" +
                    "1）脱敏后的支付请求 JSON\n" +
                    "2）IP 情报 JSON（来自 ipinfo）\n" +
                    "请综合分析潜在的支付欺诈/盗刷/异常交易/高风险地区/可疑行为迹象。\n" +
                    "\n" +
                    "要求：\n" +
                    "- 只输出严格的 JSON 对象，不要输出任何多余文本、换行以外的解释内容\n" +
                    "- 不要使用代码块（```）\n" +
                    "- JSON 必须符合下述字段：\n" +
                    "{\n" +
                    "  \"riskScore\": 0-100,\n" +
                    "  \"riskLevel\": \"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\",\n" +
                    "  \"deepAnalysis\": \"深度分析，包含关键原因与风险点\",\n" +
                    "  \"riskFactors\": [\"风险因素1\",\"风险因素2\"]\n" +
                    "}\n" +
                    "- 如果信息不足，也要基于已有信息给出最合理的风险评估，并在 deepAnalysis 中说明不确定性。\n";

    public static String buildUserPrompt(String paymentMaskedJson, String ipInfoMaskedJson) {
        return "支付请求 JSON（已脱敏）：\n" +
                "<Payment>" + paymentMaskedJson + "</Payment>\n" +
                "\n" +
                "IP 情报 JSON（已脱敏）：\n" +
                "<IPInfo>" + ipInfoMaskedJson + "</IPInfo>\n" +
                "\n" +
                "请输出严格 JSON：\n" +
                "{\n" +
                "  \"riskScore\": 0-100,\n" +
                "  \"riskLevel\": \"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\",\n" +
                "  \"deepAnalysis\": \"...\",\n" +
                "  \"riskFactors\": [\"...\", \"...\"]\n" +
                "}\n";
    }

}

