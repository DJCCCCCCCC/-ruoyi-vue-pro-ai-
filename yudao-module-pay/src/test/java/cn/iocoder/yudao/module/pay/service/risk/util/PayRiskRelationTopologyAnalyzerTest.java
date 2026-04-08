package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayRiskRelationTopologyAnalyzerTest {

    @Test
    void shouldBuildTopologyAndDetectSharedDeviceRisk() {
        String payload = "{\n" +
                "  \"transactions\": [\n" +
                "    {\n" +
                "      \"amount\": 1888,\n" +
                "      \"payer\": {\n" +
                "        \"userId\": \"u-1001\",\n" +
                "        \"name\": \"Alice\",\n" +
                "        \"deviceId\": \"dev-9\",\n" +
                "        \"ip\": \"10.8.1.9\",\n" +
                "        \"mobile\": \"13800001111\"\n" +
                "      },\n" +
                "      \"payee\": {\n" +
                "        \"merchantNo\": \"m-3001\",\n" +
                "        \"merchantName\": \"Risky Shop\",\n" +
                "        \"accountNo\": \"acc-01\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"amount\": 2999,\n" +
                "      \"payer\": {\n" +
                "        \"userId\": \"u-1002\",\n" +
                "        \"name\": \"Bob\",\n" +
                "        \"deviceId\": \"dev-9\",\n" +
                "        \"ip\": \"10.8.1.10\",\n" +
                "        \"mobile\": \"13800002222\"\n" +
                "      },\n" +
                "      \"payee\": {\n" +
                "        \"merchantNo\": \"m-3001\",\n" +
                "        \"merchantName\": \"Risky Shop\",\n" +
                "        \"accountNo\": \"acc-01\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment assessment =
                PayRiskRelationTopologyAnalyzer.analyze(JsonUtils.parseTree(payload));

        assertNotNull(assessment.getTopology());
        assertTrue(assessment.getExtraScore() > 0);
        assertFalse(assessment.getFactors().isEmpty());
        assertEquals(2, assessment.getTopology().getSummary().getTransactionCount());
        assertTrue(assessment.getTopology().getSummary().getSharedAttributeCount() > 0);
        assertTrue(assessment.getTopology().getSignals().stream()
                .anyMatch(signal -> "SHARED_DEVICE".equals(signal.getCode())));
    }
}
