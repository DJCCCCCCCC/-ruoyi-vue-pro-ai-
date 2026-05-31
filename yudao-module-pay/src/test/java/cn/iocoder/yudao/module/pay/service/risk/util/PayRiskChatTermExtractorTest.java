package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayRiskChatTermExtractorTest {

    @Test
    void shouldExtractPeerChatPhrasesAndSignals() {
        String payload = "{\n"
                + "  \"detectedSignals\": [\"链接\", \"转账催促\", \"时限施压\"],\n"
                + "  \"latestPeerMessage\": \"请点击链接完成验证并尽快转账，三分钟内有效。\",\n"
                + "  \"messages\": [\n"
                + "    {\"role\": \"peer\", \"content\": \"这是退款链接，你马上点开并转账，否则就来不及了\"},\n"
                + "    {\"role\": \"self\", \"content\": \"这是什么链接？\"}\n"
                + "  ]\n"
                + "}";

        List<String> terms = PayRiskChatTermExtractor.extractTerms(JsonUtils.parseTree(payload));

        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().anyMatch(t -> t.contains("转账")));
        assertTrue(terms.stream().anyMatch(t -> t.contains("链接")));
        assertTrue(terms.stream().anyMatch("转账催促"::equals));
        assertFalse(terms.stream().anyMatch(t -> t.contains("这是什么链接")));
    }

    @Test
    void shouldNotExtractSystemRiskFactors() {
        assertTrue(PayRiskChatTermExtractor.isSystemAnalysisPhrase("IP地址位于韩国，可能涉及跨境欺诈"));
        assertFalse(PayRiskChatTermExtractor.isValidChatTerm("IP地址位于韩国，可能涉及跨境欺诈"));

        List<String> terms = PayRiskChatTermExtractor.extractTerms("{\"riskFactors\":[\"IP地址位于韩国，可能涉及跨境欺诈\"]}");
        assertTrue(terms.isEmpty());
    }

    @Test
    void shouldFallbackToLatestPeerMessageWhenNoMessages() {
        String payload = "{\"latestPeerMessage\":\"加这个微信马上转账到安全账户\"}";
        List<String> terms = PayRiskChatTermExtractor.extractTerms(payload);
        assertFalse(terms.isEmpty());
        assertTrue(terms.stream().anyMatch(t -> t.contains("安全账户") || t.contains("转账")));
    }
}
