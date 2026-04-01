package cn.iocoder.yudao.module.pay.service.risk.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PayRiskPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "You are a payment risk analyst. "
                    + "You will receive masked payment JSON and masked IP intelligence JSON. "
                    + "Return only a strict JSON object with riskScore, riskLevel, deepAnalysis and riskFactors. "
                    + "Do not output markdown or code fences.";

    public static String buildUserPrompt(String paymentMaskedJson, String ipInfoMaskedJson) {
        return "Masked payment JSON:\n"
                + "<Payment>" + paymentMaskedJson + "</Payment>\n\n"
                + "Masked IP intelligence JSON:\n"
                + "<IPInfo>" + ipInfoMaskedJson + "</IPInfo>\n\n"
                + "Return JSON only:\n"
                + "{\n"
                + "  \"riskScore\": 0-100,\n"
                + "  \"riskLevel\": \"LOW\"|\"MEDIUM\"|\"HIGH\"|\"CRITICAL\",\n"
                + "  \"deepAnalysis\": \"...\",\n"
                + "  \"riskFactors\": [\"...\", \"...\"]\n"
                + "}\n";
    }
}
