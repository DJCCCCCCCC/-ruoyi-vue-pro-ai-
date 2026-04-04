package cn.iocoder.yudao.module.pay.service.risk.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PayRiskPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "你是一名支付风险分析师。"
                    + "你将收到脱敏后的支付JSON数据和脱敏后的IP情报JSON数据。"
                    + "请只返回一个严格的JSON对象，包含riskScore、riskLevel、deepAnalysis和riskFactors字段。"
                    + "不要输出markdown或代码块标记。"
                    + "所有分析内容请使用中文输出。";

    public static String buildUserPrompt(String paymentMaskedJson, String ipInfoMaskedJson) {
        return "脱敏后的支付JSON数据：\n"
                + "<Payment>" + paymentMaskedJson + "</Payment>\n\n"
                + "脱敏后的IP情报JSON数据：\n"
                + "<IPInfo>" + ipInfoMaskedJson + "</IPInfo>\n\n"
                + "请返回JSON格式（deepAnalysis和riskFactors字段的内容必须使用中文）：\n"
                + "{\n"
                + "  \"riskScore\": 0-100,\n"
                + "  \"riskLevel\": \"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\",\n"
                + "  \"deepAnalysis\": \"...\",\n"
                + "  \"riskFactors\": [\"...\", \"...\"]\n"
                + "}\n";
    }
}
