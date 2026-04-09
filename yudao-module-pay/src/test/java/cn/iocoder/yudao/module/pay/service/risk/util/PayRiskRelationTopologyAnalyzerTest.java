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

    @Test
    void shouldDetectNarrativeDrivenTransferSignals() {
        String payload = "{\n" +
                "  \"scene\": \"WECHAT_CHAT_RISK\",\n" +
                "  \"messageCount\": 4,\n" +
                "  \"links\": [\"https://refund-verify.example.com/check\"],\n" +
                "  \"latestPeerMessage\": \"客服说马上退款，先点链接完成验证\",\n" +
                "  \"detectedSignals\": [\"链接\", \"时限施压\"],\n" +
                "  \"transactions\": [\n" +
                "    {\n" +
                "      \"amount\": 1888,\n" +
                "      \"relationType\": \"FAKE_CUSTOMER_SERVICE\",\n" +
                "      \"payer\": {\n" +
                "        \"userId\": \"victim-1\",\n" +
                "        \"name\": \"Victim\"\n" +
                "      },\n" +
                "      \"payee\": {\n" +
                "        \"merchantNo\": \"fake-cs-01\",\n" +
                "        \"merchantName\": \"Refund Verify\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment assessment =
                PayRiskRelationTopologyAnalyzer.analyze(JsonUtils.parseTree(payload));

        assertTrue(assessment.getTopology().getSignals().stream()
                .anyMatch(signal -> "SOCIAL_ENGINEERING_SCRIPT".equals(signal.getCode())));
        assertTrue(assessment.getTopology().getSignals().stream()
                .anyMatch(signal -> "LINK_GUIDED_PAYMENT".equals(signal.getCode())));
        assertTrue(assessment.getTopology().getSignals().stream()
                .anyMatch(signal -> "SHORT_CONVERSATION_PAYMENT".equals(signal.getCode())));
    }

    @Test
    void shouldDetectMoneyMuleHub() {
        String payload = "{\n" +
                "  \"transactions\": [\n" +
                "    {\n" +
                "      \"amount\": 500,\n" +
                "      \"payer\": {\"userId\": \"u-1001\", \"name\": \"A\"},\n" +
                "      \"payee\": {\"userId\": \"hub-1\", \"name\": \"Hub\"}\n" +
                "    },\n" +
                "    {\n" +
                "      \"amount\": 800,\n" +
                "      \"payer\": {\"userId\": \"u-1002\", \"name\": \"B\"},\n" +
                "      \"payee\": {\"userId\": \"hub-1\", \"name\": \"Hub\"}\n" +
                "    },\n" +
                "    {\n" +
                "      \"amount\": 1200,\n" +
                "      \"payer\": {\"userId\": \"hub-1\", \"name\": \"Hub\"},\n" +
                "      \"payee\": {\"userId\": \"target-1\", \"name\": \"Target\"}\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment assessment =
                PayRiskRelationTopologyAnalyzer.analyze(JsonUtils.parseTree(payload));

        assertTrue(assessment.getTopology().getSignals().stream()
                .anyMatch(signal -> "MONEY_MULE_HUB".equals(signal.getCode())));
        assertTrue(assessment.getTopology().getSummary().getHighRiskNodeCount() > 0);
    }
}
