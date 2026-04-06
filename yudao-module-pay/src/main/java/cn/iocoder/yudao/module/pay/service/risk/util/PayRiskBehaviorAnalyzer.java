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
            factors.add("操作速度极高，接近自动化交互");
            notes.add("operationSpeed=" + operationSpeed + "，明显高于正常人工操作区间。");
        } else if (operationSpeed != null && operationSpeed >= 80) {
            extraScore += 16;
            factors.add("操作速度异常偏快");
            notes.add("operationSpeed=" + operationSpeed + "，表现出明显的非常规高速交互。");
        }

        if (cardNumberLength != null && cardNumberLength >= 16 && cardNumberInputDurationMs != null) {
            if (cardNumberInputDurationMs < 500) {
                extraScore += 35;
                factors.add("16 位敏感字段在 0.5 秒内完成输入");
                notes.add("cardNumberLength=" + cardNumberLength + "，inputDuration=" + cardNumberInputDurationMs
                        + "ms，极像脚本填充或自动回放行为。");
            } else if (cardNumberInputDurationMs < 1200) {
                extraScore += 18;
                factors.add("敏感字段输入速度异常偏快");
                notes.add("cardNumberInputDurationMs=" + cardNumberInputDurationMs + "，快于正常人工录入。");
            }
        }

        if (Boolean.TRUE.equals(pasteDetected)) {
            extraScore += 12;
            factors.add("敏感字段疑似为粘贴输入");
            notes.add("pasteDetected=true，支付敏感字段存在直接粘贴迹象。");
        }

        if (averageKeyIntervalMs != null && averageKeyIntervalMs < 45) {
            extraScore += 10;
            factors.add("按键平均间隔过短");
            notes.add("averageKeyIntervalMs=" + averageKeyIntervalMs + "，低于常见人工输入节奏。");
        }

        if (keyIntervalStdMs != null && keyIntervalStdMs <= 20) {
            extraScore += 12;
            factors.add("按键节奏过于均匀");
            notes.add("keyIntervalStdMs=" + keyIntervalStdMs + "，缺少人类输入常见的停顿波动。");
        }

        if (mouseStraightness != null && mouseStraightness >= 0.92D) {
            extraScore += 10;
            factors.add("鼠标轨迹过于笔直");
            notes.add("mouseStraightness=" + mouseStraightness + "，接近脚本生成的线性路径。");
        }

        if (pointerJumpCount != null && pointerJumpCount >= 2) {
            extraScore += 12;
            factors.add("指针轨迹存在跳点行为");
            notes.add("pointerJumpCount=" + pointerJumpCount + "，更像程序瞬移式定位。");
        }

        if (mouseTrajectoryType != null) {
            String normalizedTrajectoryType = mouseTrajectoryType.trim().toUpperCase(Locale.ROOT);
            if ("LINEAR".equals(normalizedTrajectoryType) || "JUMP".equals(normalizedTrajectoryType)) {
                extraScore += 10;
                factors.add("鼠标轨迹类型疑似自动化");
                notes.add("mouseTrajectoryType=" + normalizedTrajectoryType + "。");
            }
        }

        if (Boolean.TRUE.equals(emulatorDetected)) {
            extraScore += 15;
            factors.add("检测到模拟器或虚拟化设备迹象");
            notes.add("emulatorDetected=true。");
        }

        if (Boolean.TRUE.equals(scriptHint)) {
            extraScore += 20;
            factors.add("行为数据出现直接脚本特征");
            notes.add("scriptHint=true，高度怀疑存在自动化执行或插桩。");
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
