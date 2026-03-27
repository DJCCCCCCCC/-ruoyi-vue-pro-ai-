package cn.iocoder.yudao.module.pay.service.risk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向「给 AI 用」的简单脱敏工具：
 * 1）移除明显敏感字段（token/password 等）
 * 2）对银行卡/手机号/身份证/邮箱等做部分掩码
 * <p>
 * 注意：这是 MVP 级别脱敏，不等同于完备的合规脱敏体系。
 */
@UtilityClass
public class PayRiskDesensitizer {

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})){3}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^(\\d{17}[0-9Xx])$");
    private static final Pattern CARD_PATTERN = Pattern.compile("^\\d{12,19}$");

    /**
     * 从任意 JSON 中尽可能找出第一个 IPv4 地址（用于 ipinfo 查询）。
     */
    public static String extractFirstIp(JsonNode node) {
        if (node == null) {
            return null;
        }
        // DFS
        return extractFirstIp0(node, 0, 2000);
    }

    private static String extractFirstIp0(JsonNode node, int visited, int maxVisited) {
        if (node == null || visited > maxVisited) {
            return null;
        }
        visited++;
        if (node.isTextual()) {
            String text = node.asText();
            if (isValidIpv4(text)) {
                return text;
            }
            return null;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                String ip = extractFirstIp0(child, visited, maxVisited);
                if (ip != null) {
                    return ip;
                }
            }
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> iterator = obj.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String ip = extractFirstIp0(entry.getValue(), visited, maxVisited);
                if (ip != null) {
                    return ip;
                }
            }
        }
        return null;
    }

    /**
     * 给 AI 的脱敏 JSON（复制原树后在副本上处理）。
     */
    public static JsonNode desensitizeForPrompt(JsonNode origin) {
        if (origin == null) {
            return null;
        }
        JsonNode copy = origin.deepCopy();
        desensitizeInPlace(copy);
        return copy;
    }

    private static void desensitizeInPlace(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                desensitizeInPlace(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iterator = obj.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (shouldRemoveByKey(key)) {
                iterator.remove();
                continue;
            }

            if (value != null && value.isTextual()) {
                String text = value.asText();
                String masked = maskIfSensitive(key, text);
                if (masked != null) {
                    obj.put(key, masked);
                }
            } else {
                desensitizeInPlace(value);
            }
        }
    }

    private static boolean shouldRemoveByKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("pwd")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("accesstoken")
                || lower.contains("refreshtoken")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("cvv")
                || lower.contains("cvc")
                || lower.contains("securitycode");
    }

    private static String maskIfSensitive(String key, String text) {
        if (text == null) {
            return null;
        }
        String lowerKey = key == null ? "" : key.toLowerCase();

        if (lowerKey.contains("ip") || isValidIpv4(text)) {
            return maskIpV4(text);
        }
        if (lowerKey.contains("mobile") || lowerKey.contains("phone") || isPhone(text)) {
            return maskPhone(text);
        }
        if (lowerKey.contains("email") || isEmail(text)) {
            return maskEmail(text);
        }
        if (lowerKey.contains("idcard") || lowerKey.contains("id_card") || lowerKey.contains("id") && isIdCard(text)) {
            return maskIdCard(text);
        }
        if (lowerKey.contains("bankcard") || lowerKey.contains("card") || lowerKey.contains("cardno") || isCard(text)) {
            return maskCard(text);
        }
        // 兜底：如果 value 看起来像身份证/银行卡/手机号，也尽量掩码
        if (isIdCard(text)) {
            return maskIdCard(text);
        }
        if (isCard(text)) {
            return maskCard(text);
        }
        if (isPhone(text)) {
            return maskPhone(text);
        }
        if (isEmail(text)) {
            return maskEmail(text);
        }
        return null;
    }

    private static boolean isPhone(String text) {
        return PHONE_PATTERN.matcher(text).matches();
    }

    private static boolean isEmail(String text) {
        return EMAIL_PATTERN.matcher(text).matches();
    }

    private static boolean isIdCard(String text) {
        Matcher matcher = ID_CARD_PATTERN.matcher(text);
        return matcher.matches();
    }

    private static boolean isCard(String text) {
        return CARD_PATTERN.matcher(text).matches();
    }

    private static boolean isValidIpv4(String text) {
        if (text == null) {
            return false;
        }
        if (!IPV4_PATTERN.matcher(text).matches()) {
            return false;
        }
        String[] parts = text.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p);
                if (v < 0 || v > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String maskIpV4(String ip) {
        if (!isValidIpv4(ip)) {
            return ip;
        }
        String[] parts = ip.split("\\.");
        // a.b.c.d => a.b.*.*
        return parts[0] + "." + parts[1] + ".*.*";
    }

    private static String maskPhone(String phone) {
        if (!isPhone(phone)) {
            return phone;
        }
        // 1xxxxxxxxxx => 1xx****xxxx
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        if (!isEmail(email)) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String prefix = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (prefix.length() <= 1) {
            return "*" + domain;
        }
        return prefix.substring(0, 1) + "***" + domain;
    }

    private static String maskIdCard(String idCard) {
        if (!isIdCard(idCard)) {
            return idCard;
        }
        // 6位前缀 + 4位后缀，中间脱敏
        if (idCard.length() < 10) {
            return "***";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private static String maskCard(String card) {
        if (!isCard(card)) {
            return card;
        }
        // 保留后 4 位
        int keep = 4;
        int starsLen = Math.max(0, card.length() - keep);
        StringBuilder sb = new StringBuilder(starsLen);
        for (int i = 0; i < starsLen; i++) {
            sb.append('*');
        }
        return sb.toString() + card.substring(card.length() - keep);
    }

}

