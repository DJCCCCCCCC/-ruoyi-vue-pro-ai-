package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class PayRiskBehaviorMockDataGenerator {

    public static MockBehaviorProfile generateIfMissing(JsonNode paymentData) {
        JsonNode existingBehavior = locateBehaviorNode(paymentData);
        if (existingBehavior != null && !existingBehavior.isNull() && !existingBehavior.isMissingNode()) {
            return new MockBehaviorProfile(existingBehavior, false, "请求已携带生物行为数据，直接使用原始行为画像。");
        }
        return new MockBehaviorProfile(buildMockBehaviorProfile(paymentData), true,
                "未提供真实生物行为数据，已根据交易上下文生成模拟行为画像。");
    }

    private static JsonNode locateBehaviorNode(JsonNode paymentData) {
        if (paymentData == null || paymentData.isNull() || !paymentData.isObject()) {
            return null;
        }
        String[] fieldNames = {"behavioralBiometrics", "behavior_biometrics", "behaviorBiometrics",
                "behavior", "deviceBehavior", "biometricSignals"};
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = paymentData.get(fieldName);
            if (fieldNode != null && !fieldNode.isNull()) {
                return fieldNode;
            }
        }
        String[] directMetricFields = {"operation_speed", "operationSpeed", "card_number_input_duration_ms",
                "cardNumberInputDurationMs", "mouse_straightness", "mouseStraightness"};
        for (String fieldName : directMetricFields) {
            JsonNode fieldNode = paymentData.get(fieldName);
            if (fieldNode != null && !fieldNode.isNull()) {
                return paymentData;
            }
        }
        return null;
    }

    private static ObjectNode buildMockBehaviorProfile(JsonNode paymentData) {
        ObjectNode profile = JsonUtils.getObjectMapper().createObjectNode();

        String scenarioText = collectScenarioText(paymentData);
        int seed = Math.floorMod(scenarioText.hashCode(), 10_000);
        int messageCount = readInt(paymentData, "messageCount", 0);
        int linkCount = readInt(paymentData, "linkCount", 0);
        int signalCount = collectDetectedSignals(paymentData).size();
        int urgencyHits = countKeywords(scenarioText, "立即", "马上", "立刻", "否则", "超时", "来不及", "验证");
        int transferHits = countKeywords(scenarioText, "转账", "打款", "汇款", "付款", "收款码", "二维码", "扫码");

        int contextRiskScore = 15
                + signalCount * 14
                + Math.min(linkCount, 3) * 10
                + Math.min(urgencyHits, 3) * 8
                + Math.min(transferHits, 3) * 7
                + Math.min(messageCount, 8);
        boolean suspicious = contextRiskScore >= 55;
        boolean highlySuspicious = contextRiskScore >= 80;

        int operationSpeed = highlySuspicious ? 94 + seed % 5 : suspicious ? 80 + seed % 8 : 48 + seed % 18;
        int cardInputDuration = highlySuspicious ? 320 + seed % 180 : suspicious ? 780 + seed % 260 : 2100 + seed % 1200;
        int averageKeyInterval = highlySuspicious ? 24 + seed % 12 : suspicious ? 38 + seed % 18 : 95 + seed % 60;
        int keyIntervalStd = highlySuspicious ? 6 + seed % 8 : suspicious ? 14 + seed % 10 : 35 + seed % 18;
        double mouseStraightness = highlySuspicious ? 0.94 + (seed % 4) * 0.01D
                : suspicious ? 0.86 + (seed % 6) * 0.01D
                : 0.56 + (seed % 12) * 0.02D;
        int pointerJumpCount = highlySuspicious ? 2 + seed % 3 : suspicious ? 1 + seed % 2 : seed % 2;
        boolean pasteDetected = suspicious;
        boolean emulatorDetected = highlySuspicious || (signalCount >= 4 && linkCount >= 1);
        boolean scriptHint = highlySuspicious && signalCount >= 4;
        String trajectoryType = highlySuspicious ? (seed % 2 == 0 ? "LINEAR" : "JUMP")
                : suspicious ? "CURVE_WITH_JUMP"
                : "NATURAL";

        profile.put("operationSpeed", operationSpeed);
        profile.put("cardNumberInputDurationMs", cardInputDuration);
        profile.put("cardNumberLength", 16);
        profile.put("averageKeyIntervalMs", averageKeyInterval);
        profile.put("keyIntervalStdMs", keyIntervalStd);
        profile.put("mouseStraightness", Math.min(mouseStraightness, 0.99D));
        profile.put("pointerJumpCount", pointerJumpCount);
        profile.put("pasteDetected", pasteDetected);
        profile.put("mouseTrajectoryType", trajectoryType);
        profile.put("emulatorDetected", emulatorDetected);
        profile.put("scriptHint", scriptHint);
        profile.put("mocked", true);
        profile.put("mockContextRiskScore", Math.min(contextRiskScore, 100));
        profile.put("mockGeneratedAt", LocalDateTime.now().toString());
        profile.put("mockProfileType", highlySuspicious ? "HIGH_RISK_SCENE" : suspicious ? "MEDIUM_RISK_SCENE" : "NORMAL_SCENE");
        profile.put("mockReason", "当前请求缺少真实生物行为数据，后端按场景信号自动构造了可解释的模拟画像。");

        ArrayNode signalArray = profile.putArray("mockSignals");
        for (String signal : collectDetectedSignals(paymentData)) {
            signalArray.add(signal);
        }
        return profile;
    }

    private static List<String> collectDetectedSignals(JsonNode paymentData) {
        List<String> signals = new ArrayList<>();
        if (paymentData == null || paymentData.isNull()) {
            return signals;
        }
        JsonNode signalNode = paymentData.get("detectedSignals");
        if (signalNode != null && signalNode.isArray()) {
            for (JsonNode node : signalNode) {
                if (node != null && !node.isNull()) {
                    signals.add(node.asText());
                }
            }
        }
        return signals;
    }

    private static String collectScenarioText(JsonNode paymentData) {
        if (paymentData == null || paymentData.isNull()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, paymentData.path("scene").asText(null));
        appendIfPresent(builder, paymentData.path("source").asText(null));
        appendIfPresent(builder, paymentData.path("latestPeerMessage").asText(null));
        for (String signal : collectDetectedSignals(paymentData)) {
            appendIfPresent(builder, signal);
        }
        JsonNode messagesNode = paymentData.path("messages");
        if (messagesNode.isArray()) {
            for (JsonNode messageNode : messagesNode) {
                appendIfPresent(builder, messageNode.path("content").asText(null));
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendIfPresent(StringBuilder builder, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(text.trim());
    }

    private static int countKeywords(String source, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private static int readInt(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asInt(defaultValue);
    }

    @Data
    @AllArgsConstructor
    public static class MockBehaviorProfile {
        private JsonNode behaviorData;
        private boolean mocked;
        private String summary;
    }
}
