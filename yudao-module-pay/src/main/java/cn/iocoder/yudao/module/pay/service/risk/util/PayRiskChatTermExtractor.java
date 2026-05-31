package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从评估入参 {@code paymentData} 的聊天记录中提取诈骗话术风险词。
 */
@UtilityClass
public class PayRiskChatTermExtractor {

    public static final int MIN_PHRASE_LEN = 4;
    public static final int MIN_SIGNAL_LEN = 2;
    public static final int MAX_TERM_LEN = 256;
    /** 较短整句可直接作为风险词 */
    private static final int SHORT_MESSAGE_MAX = 80;

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[，。！？；、,.!?;\\n\\r]+");

    private static final String[] FRAUD_KEYWORDS = {
            "转账", "打款", "汇款", "收款", "验证码", "安全账户", "冻结", "解冻", "公安", "检察院", "法院",
            "客服", "退款", "链接", "点击", "刷单", "理财", "投资", "保证金", "手续费", "认证", "验证",
            "远程", "共享屏幕", "银行卡", "支付宝", "微信", "马上", "立刻", "立即", "紧急", "来不及",
            "超时", "涉嫌", "违法", "账户异常", "资金", "提现", "返利", "兼职", "贷款", "征信",
            "冒充", "公检法", "中奖", "领奖", "快递", "理赔", "解冻账户", "安全认证", "操作失败"
    };

    /**
     * 从 paymentData JSON 字符串提取聊天侧风险词。
     */
    public static List<String> extractTerms(String paymentDataJson) {
        if (StrUtil.isBlank(paymentDataJson)) {
            return new ArrayList<>();
        }
        try {
            return extractTerms(JsonUtils.parseTree(paymentDataJson));
        } catch (Exception ignore) {
            return new ArrayList<>();
        }
    }

    /**
     * 从 paymentData 节点提取聊天侧风险词（对方消息、最新对方摘要、detectedSignals）。
     */
    public static List<String> extractTerms(JsonNode paymentData) {
        if (paymentData == null || paymentData.isNull()) {
            return new ArrayList<>();
        }
        Set<String> terms = new LinkedHashSet<>();

        JsonNode messages = paymentData.path("messages");
        if (messages.isArray()) {
            for (JsonNode message : messages) {
                if (!isPeerMessage(message)) {
                    continue;
                }
                collectFromText(message.path("content").asText(""), terms);
            }
        }

        String latestPeer = paymentData.path("latestPeerMessage").asText("");
        if (StrUtil.isNotBlank(latestPeer)) {
            collectFromText(latestPeer, terms);
        }

        JsonNode signals = paymentData.path("detectedSignals");
        if (signals.isArray()) {
            for (JsonNode signal : signals) {
                addIfValidSignal(signal.asText(""), terms);
            }
        }

        return new ArrayList<>(terms);
    }

    private static void collectFromText(String raw, Set<String> terms) {
        String cleaned = normalizeText(raw);
        if (StrUtil.isBlank(cleaned)) {
            return;
        }
        if (cleaned.length() <= SHORT_MESSAGE_MAX && isValidChatTerm(cleaned)) {
            terms.add(cleaned);
        }
        for (String segment : SPLIT_PATTERN.split(cleaned)) {
            String phrase = normalizeText(segment);
            if (phrase.length() >= MIN_PHRASE_LEN && isValidChatTerm(phrase)) {
                terms.add(truncate(phrase));
            }
        }
    }

    private static void addIfValidSignal(String raw, Set<String> terms) {
        String signal = normalizeText(raw);
        if (signal.length() < MIN_SIGNAL_LEN || signal.length() > MAX_TERM_LEN) {
            return;
        }
        if (isSystemAnalysisPhrase(signal)) {
            return;
        }
        if (containsFraudKeyword(signal) || signal.length() >= MIN_PHRASE_LEN) {
            terms.add(truncate(signal));
        }
    }

    static boolean isPeerMessage(JsonNode message) {
        if (message == null || message.isNull()) {
            return false;
        }
        String role = message.path("role").asText("").trim();
        if (role.isEmpty()) {
            return false;
        }
        String lower = role.toLowerCase(Locale.ROOT);
        if (isSelfRole(lower, role)) {
            return false;
        }
        return "peer".equals(lower) || "对方".equals(role) || "scammer".equals(lower)
                || "other".equals(lower) || "opponent".equals(lower);
    }

    private static boolean isSelfRole(String lower, String original) {
        return "self".equals(lower) || "user".equals(lower) || "me".equals(lower)
                || "我".equals(original) || "本人".equals(original);
    }

    static boolean isValidChatTerm(String term) {
        if (StrUtil.isBlank(term) || term.length() > MAX_TERM_LEN) {
            return false;
        }
        if (isSystemAnalysisPhrase(term)) {
            return false;
        }
        return containsFraudKeyword(term);
    }

    static boolean isSystemAnalysisPhrase(String term) {
        if (StrUtil.isBlank(term)) {
            return true;
        }
        String lower = term.toLowerCase(Locale.ROOT);
        if (term.contains("IP地址") || lower.contains("ipinfo") || lower.contains("whois")) {
            return true;
        }
        if (term.startsWith("已完成") || term.contains("Agentic") || term.contains("图片 OCR")) {
            return true;
        }
        if (term.contains("生物行为") || term.contains("域名 Whois") || term.contains("历史案例相似")) {
            return true;
        }
        if (term.contains("仲裁Agent") || term.contains("风险加分") || term.contains("研判结论")) {
            return true;
        }
        return term.contains("可能涉及跨境") && term.contains("IP");
    }

    static boolean containsFraudKeyword(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : FRAUD_KEYWORDS) {
            if (text.contains(keyword) || lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = URL_PATTERN.matcher(raw).replaceAll(" ").trim();
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }

    private static String truncate(String term) {
        if (term.length() <= MAX_TERM_LEN) {
            return term;
        }
        return term.substring(0, MAX_TERM_LEN);
    }
}
