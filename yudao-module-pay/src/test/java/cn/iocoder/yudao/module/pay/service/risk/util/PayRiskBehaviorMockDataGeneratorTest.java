package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayRiskBehaviorMockDataGeneratorTest {

    @Test
    void shouldGenerateMockBehaviorProfileForRiskyChatPayload() {
        String payload = "{\n" +
                "  \"scene\": \"WECHAT_CHAT_RISK\",\n" +
                "  \"source\": \"chat-risk-test-page\",\n" +
                "  \"messageCount\": 4,\n" +
                "  \"linkCount\": 1,\n" +
                "  \"detectedSignals\": [\"链接\", \"转账催促\", \"时限施压\"],\n" +
                "  \"latestPeerMessage\": \"这是退款链接，你马上点开并转账，否则就来不及了\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"peer\", \"content\": \"这是退款链接 https://risk.example/pay\"},\n" +
                "    {\"role\": \"peer\", \"content\": \"你现在立刻转账处理，否则就会超时\"}\n" +
                "  ]\n" +
                "}";

        PayRiskBehaviorMockDataGenerator.MockBehaviorProfile profile =
                PayRiskBehaviorMockDataGenerator.generateIfMissing(JsonUtils.parseTree(payload));

        assertTrue(profile.isMocked());
        assertNotNull(profile.getBehaviorData());
        assertTrue(profile.getBehaviorData().path("mocked").asBoolean());
        assertTrue(profile.getBehaviorData().path("operationSpeed").asInt() >= 80);
        assertTrue(profile.getBehaviorData().path("pasteDetected").asBoolean());

        PayRiskBehaviorAnalyzer.BehaviorRiskAssessment assessment =
                PayRiskBehaviorAnalyzer.analyze(profile.getBehaviorData());
        assertTrue(assessment.getExtraScore() > 0);
        assertFalse(assessment.getFactors().isEmpty());
    }

    @Test
    void shouldReuseProvidedBehaviorPayload() {
        String payload = "{\n" +
                "  \"scene\": \"WECHAT_CHAT_RISK\",\n" +
                "  \"behaviorBiometrics\": {\n" +
                "    \"operationSpeed\": 61,\n" +
                "    \"cardNumberInputDurationMs\": 1600,\n" +
                "    \"mouseStraightness\": 0.71\n" +
                "  }\n" +
                "}";

        PayRiskBehaviorMockDataGenerator.MockBehaviorProfile profile =
                PayRiskBehaviorMockDataGenerator.generateIfMissing(JsonUtils.parseTree(payload));

        assertFalse(profile.isMocked());
        assertEquals(61, profile.getBehaviorData().path("operationSpeed").asInt());
    }
}
