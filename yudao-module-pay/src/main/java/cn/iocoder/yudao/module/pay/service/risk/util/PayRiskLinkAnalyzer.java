package cn.iocoder.yudao.module.pay.service.risk.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class PayRiskLinkAnalyzer {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s\"'<>]+|www\\.[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})){3}$");
    private static final List<String> SHORT_LINK_DOMAINS = Arrays.asList(
            "bit.ly", "t.cn", "url.cn", "tinyurl.com", "goo.gl", "is.gd", "ow.ly", "suo.im"
    );
    private static final List<String> QRCODE_KEYWORDS = Arrays.asList("qrcode", "scan", "paycode", "receipt code");

    public static LinkRiskAssessment analyze(JsonNode paymentData) {
        Set<String> links = new LinkedHashSet<>();
        collectLinks(paymentData, links);

        int extraScore = 0;
        List<String> factors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (String rawLink : links) {
            String normalizedLink = normalizeLink(rawLink);
            LinkSignal signal = analyzeSingleLink(normalizedLink);
            extraScore += signal.getScore();
            factors.addAll(signal.getFactors());
            notes.addAll(signal.getNotes());
        }

        if (containsQrCodeKeyword(paymentData)) {
            extraScore += 8;
            factors.add("Conversation contains QR or scan guidance");
            notes.add("Detected qrcode/scan style guidance in request payload.");
        }

        return new LinkRiskAssessment(
                Math.min(extraScore, 40),
                deduplicate(factors),
                deduplicate(notes),
                new ArrayList<>(links)
        );
    }

    private static LinkSignal analyzeSingleLink(String link) {
        int score = 0;
        List<String> factors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        try {
            URI uri = URI.create(link);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

            if (!"https".equals(scheme)) {
                score += 18;
                factors.add("Link does not use HTTPS");
                notes.add("Detected non-HTTPS link: " + link);
            }
            if (host.isEmpty()) {
                score += 8;
                factors.add("Link host is empty or malformed");
                notes.add("Unable to resolve host from link: " + link);
                return new LinkSignal(score, deduplicate(factors), deduplicate(notes));
            }
            if (isIpv4(host)) {
                score += 12;
                factors.add("Link uses raw IP instead of domain");
                notes.add("Detected raw IP host: " + host);
            }
            if (host.contains("xn--")) {
                score += 10;
                factors.add("Link domain contains punycode");
                notes.add("Detected punycode host: " + host);
            }
            if (isShortLinkDomain(host)) {
                score += 8;
                factors.add("Short-link domain detected");
                notes.add("Detected short-link host: " + host);
            }
            if (uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443) {
                score += 6;
                factors.add("Link uses non-standard port");
                notes.add("Detected non-standard port " + uri.getPort() + " in link: " + link);
            }

            String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);
            if (query.contains("redirect=") || query.contains("url=") || query.contains("target=") || query.contains("jump=")) {
                score += 6;
                factors.add("Link contains redirect parameter");
                notes.add("Detected redirect-like parameter in link query string.");
            }
        } catch (Exception ignored) {
            score += 10;
            factors.add("Link format is invalid or intentionally obfuscated");
            notes.add("Unable to parse link: " + link);
        }

        return new LinkSignal(score, deduplicate(factors), deduplicate(notes));
    }

    private static void collectLinks(JsonNode node, Set<String> links) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            Matcher matcher = URL_PATTERN.matcher(node.asText());
            while (matcher.find()) {
                links.add(stripTrailingPunctuation(matcher.group()));
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectLinks(child, links);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                collectLinks(iterator.next().getValue(), links);
            }
        }
    }

    private static boolean containsQrCodeKeyword(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isTextual()) {
            String text = node.asText().toLowerCase(Locale.ROOT);
            return QRCODE_KEYWORDS.stream().anyMatch(text::contains);
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsQrCodeKeyword(child)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                if (containsQrCodeKeyword(iterator.next().getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeLink(String link) {
        if (link == null) {
            return "";
        }
        String trimmed = stripTrailingPunctuation(link.trim());
        if (trimmed.regionMatches(true, 0, "www.", 0, 4)) {
            return "http://" + trimmed;
        }
        return trimmed;
    }

    private static String stripTrailingPunctuation(String text) {
        return text.replaceAll("[),.;!]+$", "");
    }

    private static boolean isShortLinkDomain(String host) {
        return SHORT_LINK_DOMAINS.stream().anyMatch(domain -> domain.equalsIgnoreCase(host));
    }

    private static boolean isIpv4(String host) {
        if (!IPV4_PATTERN.matcher(host).matches()) {
            return false;
        }
        String[] parts = host.split("\\.");
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private static List<String> deduplicate(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    @Data
    @AllArgsConstructor
    public static class LinkRiskAssessment {
        private Integer extraScore;
        private List<String> factors;
        private List<String> notes;
        private List<String> links;
    }

    @Data
    @AllArgsConstructor
    private static class LinkSignal {
        private Integer score;
        private List<String> factors;
        private List<String> notes;
    }
}
