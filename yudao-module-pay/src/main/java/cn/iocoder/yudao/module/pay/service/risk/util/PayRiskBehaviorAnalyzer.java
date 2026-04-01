package cn.iocoder.yudao.module.pay.service.risk.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class PayRiskBehaviorAnalyzer {

    private static final int MAX_EXTRA_SCORE = 45;

    public static BehaviorRiskAssessment analyze(JsonNode paymentData) {
        JsonNode behaviorNode = locateBehaviorNode(paymentData);
        if (behaviorNode == null || behaviorNode.isMissingNode() || behaviorNode.isNull()) {
            return BehaviorRiskAssessment.empty();
        }

        int extraScore = 0;
        List<String> factors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        Integer operationSpeed = readInt(behaviorNode, "operation_speed", "operationSpeed");
        Integer cardNumberInputDurationMs = readInt(behaviorNode, "card_number_input_duration_ms", "cardNumberInputDurationMs");
        Integer cardNumberLength = readInt(behaviorNode, "card_number_length", "cardNumberLength");
        Integer averageKeyIntervalMs = readInt(behaviorNode, "average_key_interval_ms", "averageKeyIntervalMs");
        Integer keyIntervalStdMs = readInt(behaviorNode, "key_interval_std_ms", "keyIntervalStdMs");
        Double mouseStraightness = readDouble(behaviorNode, "mouse_straightness", "mouseStraightness");
        Integer pointerJumpCount = readInt(behaviorNode, "pointer_jump_count", "pointerJumpCount");
        Boolean pasteDetected = readBoolean(behaviorNode, "paste_detected", "pasteDetected");
        String mouseTrajectoryType = readText(behaviorNode, "mouse_trajectory_type", "mouseTrajectoryType");
        Boolean emulatorDetected = readBoolean(behaviorNode, "emulator_detected", "emulatorDetected");
        Boolean scriptHint = readBoolean(behaviorNode, "script_hint", "scriptHint");

        if (operationSpeed != null && operationSpeed >= 95) {
            extraScore += 28;
            factors.add("operation_speed extremely high, close to automated interaction");
            notes.add("operation_speed=" + operationSpeed + ", significantly above normal manual input.");
        } else if (operationSpeed != null && operationSpeed >= 80) {
            extraScore += 16;
            factors.add("operation_speed abnormal for a normal user");
            notes.add("operation_speed=" + operationSpeed + ", indicates unusually fast interaction.");
        }

        if (cardNumberLength != null && cardNumberLength >= 16 && cardNumberInputDurationMs != null) {
            if (cardNumberInputDurationMs < 500) {
                extraScore += 35;
                factors.add("16-digit card number entered in under 0.5 seconds");
                notes.add("Card number length=" + cardNumberLength + ", input duration=" + cardNumberInputDurationMs
                        + "ms, very similar to script or auto-fill behavior.");
            } else if (cardNumberInputDurationMs < 1200) {
                extraScore += 18;
                factors.add("Card number entry speed is unusually fast");
                notes.add("Card number input duration=" + cardNumberInputDurationMs
                        + "ms, faster than normal manual typing.");
            }
        }

        if (Boolean.TRUE.equals(pasteDetected)) {
            extraScore += 12;
            factors.add("Sensitive field appears to be pasted instead of typed");
            notes.add("paste_detected=true on a payment-sensitive field.");
        }

        if (averageKeyIntervalMs != null && averageKeyIntervalMs < 45) {
            extraScore += 10;
            factors.add("Average keystroke interval is too short");
            notes.add("average_key_interval_ms=" + averageKeyIntervalMs + ", below typical manual input rhythm.");
        }

        if (keyIntervalStdMs != null && keyIntervalStdMs <= 20) {
            extraScore += 12;
            factors.add("Keystroke intervals are overly uniform");
            notes.add("key_interval_std_ms=" + keyIntervalStdMs + ", shows low human pause variance.");
        }

        if (mouseStraightness != null && mouseStraightness >= 0.92D) {
            extraScore += 10;
            factors.add("Mouse trajectory is too straight");
            notes.add("mouse_straightness=" + mouseStraightness + ", close to linear script path.");
        }

        if (pointerJumpCount != null && pointerJumpCount >= 2) {
            extraScore += 12;
            factors.add("Pointer movement shows jump behavior");
            notes.add("pointer_jump_count=" + pointerJumpCount + ", looks like instant position jumps.");
        }

        if (mouseTrajectoryType != null) {
            String normalizedTrajectoryType = mouseTrajectoryType.trim().toUpperCase(Locale.ROOT);
            if ("LINEAR".equals(normalizedTrajectoryType) || "JUMP".equals(normalizedTrajectoryType)) {
                extraScore += 10;
                factors.add("Mouse trajectory type suggests automation");
                notes.add("mouse_trajectory_type=" + normalizedTrajectoryType + ".");
            }
        }

        if (Boolean.TRUE.equals(emulatorDetected)) {
            extraScore += 15;
            factors.add("Emulator or virtualized device hint detected");
            notes.add("emulator_detected=true.");
        }

        if (Boolean.TRUE.equals(scriptHint)) {
            extraScore += 20;
            factors.add("Behavior data contains direct script hint");
            notes.add("script_hint=true, likely automated or instrumented execution.");
        }

        return new BehaviorRiskAssessment(
                Math.min(extraScore, MAX_EXTRA_SCORE),
                deduplicate(factors),
                deduplicate(notes),
                behaviorNode
        );
    }

    private static JsonNode locateBehaviorNode(JsonNode paymentData) {
        if (paymentData == null || paymentData.isNull()) {
            return null;
        }
        if (paymentData.isObject()) {
            JsonNode directNode = findFirstExistingNode(
                    paymentData,
                    "behavioralBiometrics",
                    "behavior_biometrics",
                    "behaviorBiometrics",
                    "behavior",
                    "deviceBehavior",
                    "biometricSignals"
            );
            if (directNode != null && !directNode.isNull()) {
                return directNode;
            }
            if (containsAnyField(
                    paymentData,
                    "operation_speed",
                    "operationSpeed",
                    "card_number_input_duration_ms",
                    "cardNumberInputDurationMs",
                    "mouse_straightness",
                    "mouseStraightness"
            )) {
                return paymentData;
            }
        }
        return null;
    }

    private static boolean containsAnyField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode != null && !fieldNode.isNull()) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findFirstExistingNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = node.get(fieldName);
            if (fieldNode != null) {
                return fieldNode;
            }
        }
        return null;
    }

    private static Integer readInt(JsonNode node, String... fieldNames) {
        JsonNode fieldNode = findFirstValue(node, fieldNames);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isInt() || fieldNode.isLong()) {
            return fieldNode.asInt();
        }
        if (fieldNode.isTextual()) {
            try {
                return Integer.parseInt(fieldNode.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double readDouble(JsonNode node, String... fieldNames) {
        JsonNode fieldNode = findFirstValue(node, fieldNames);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isFloat() || fieldNode.isDouble() || fieldNode.isBigDecimal() || fieldNode.isInt() || fieldNode.isLong()) {
            return fieldNode.asDouble();
        }
        if (fieldNode.isTextual()) {
            try {
                return Double.parseDouble(fieldNode.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean readBoolean(JsonNode node, String... fieldNames) {
        JsonNode fieldNode = findFirstValue(node, fieldNames);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isBoolean()) {
            return fieldNode.asBoolean();
        }
        if (fieldNode.isTextual()) {
            String text = fieldNode.asText().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
                return Boolean.TRUE;
            }
            if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static String readText(JsonNode node, String... fieldNames) {
        JsonNode fieldNode = findFirstValue(node, fieldNames);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private static JsonNode findFirstValue(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            for (String fieldName : fieldNames) {
                if (fieldName.equals(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static List<String> deduplicate(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    @Data
    @AllArgsConstructor
    public static class BehaviorRiskAssessment {
        private Integer extraScore;
        private List<String> factors;
        private List<String> notes;
        private JsonNode behaviorSnapshot;

        public static BehaviorRiskAssessment empty() {
            return new BehaviorRiskAssessment(0, new ArrayList<>(), new ArrayList<>(), null);
        }
    }
}
