package cn.iocoder.yudao.module.pay.service.risk.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class PayRiskWhoisAnalyzer {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@([A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("(?:^|[^A-Z0-9-])([A-Z0-9-]+(?:\\.[A-Z0-9-]+)+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})(?:\\.(\\d{1,3})){3}$");
    private static final Pattern ALPHA_TLD_PATTERN = Pattern.compile("^[a-z]{2,24}$");
    private static final int MAX_DOMAIN_LOOKUPS = 3;

    public static List<String> extractDomains(JsonNode paymentData) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        collectDomains(paymentData, domains);
        List<String> result = new ArrayList<>(domains);
        if (result.size() > MAX_DOMAIN_LOOKUPS) {
            return result.subList(0, MAX_DOMAIN_LOOKUPS);
        }
        return result;
    }

    public static WhoisRiskAssessment analyze(List<WhoisLookupResult> lookupResults) {
        if (lookupResults == null || lookupResults.isEmpty()) {
            return WhoisRiskAssessment.empty();
        }

        int extraScore = 0;
        List<String> factors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (WhoisLookupResult lookupResult : lookupResults) {
            String domain = lookupResult.getDomain();
            JsonNode payload = lookupResult.getPayload();
            if (payload == null || payload.isNull()) {
                extraScore += 6;
                factors.add("Whois lookup failed for domain: " + domain);
                notes.add("No Whois payload returned for domain " + domain + ".");
                continue;
            }

            JsonNode errorMessage = payload.path("ErrorMessage");
            if (!errorMessage.isMissingNode() && !errorMessage.isNull()) {
                extraScore += 6;
                factors.add("Whois service returned an error for domain: " + domain);
                notes.add("Whois error for " + domain + ": " + safeText(errorMessage.path("msg")));
            }

            JsonNode whoisRecord = payload.path("WhoisRecord");
            if (whoisRecord.isMissingNode() || whoisRecord.isNull() || whoisRecord.size() == 0) {
                extraScore += 10;
                factors.add("No Whois record found for domain: " + domain);
                notes.add("WhoisRecord is empty for domain " + domain + ".");
                continue;
            }

            String createdDate = firstNonBlank(
                    safeText(whoisRecord.path("createdDateNormalized")),
                    safeText(whoisRecord.path("createdDate")),
                    safeText(whoisRecord.path("registryData").path("createdDateNormalized")),
                    safeText(whoisRecord.path("registryData").path("createdDate"))
            );
            Long ageDays = resolveAgeDays(createdDate);
            if (ageDays == null) {
                extraScore += 4;
                factors.add("Domain age is unavailable: " + domain);
                notes.add("Unable to parse domain creation date for " + domain + ".");
            } else if (ageDays < 30) {
                extraScore += 22;
                factors.add("Very new domain detected: " + domain);
                notes.add("Domain " + domain + " age is " + ageDays + " days.");
            } else if (ageDays < 180) {
                extraScore += 12;
                factors.add("Young domain detected: " + domain);
                notes.add("Domain " + domain + " age is " + ageDays + " days.");
            }

            String registrarName = firstNonBlank(
                    safeText(whoisRecord.path("registrarName")),
                    safeText(whoisRecord.path("registryData").path("registrarName"))
            );
            if (registrarName == null) {
                extraScore += 4;
                factors.add("Registrar information is missing: " + domain);
                notes.add("Registrar name is missing in Whois response for " + domain + ".");
            }

            String registrantIdentity = firstNonBlank(
                    safeText(whoisRecord.path("registrant").path("organization")),
                    safeText(whoisRecord.path("registrant").path("name")),
                    safeText(whoisRecord.path("registryData").path("registrant").path("organization")),
                    safeText(whoisRecord.path("registryData").path("registrant").path("name"))
            );
            if (registrantIdentity == null) {
                extraScore += 4;
                factors.add("Registrant identity is missing: " + domain);
                notes.add("Registrant organization/name is missing for " + domain + ".");
            } else {
                String normalizedRegistrant = registrantIdentity.toLowerCase(Locale.ROOT);
                if (normalizedRegistrant.contains("privacy")
                        || normalizedRegistrant.contains("proxy")
                        || normalizedRegistrant.contains("redacted")
                        || normalizedRegistrant.contains("whoisguard")) {
                    extraScore += 6;
                    factors.add("Registrant uses privacy masking: " + domain);
                    notes.add("Registrant identity for " + domain + " looks privacy protected: " + registrantIdentity + ".");
                }
            }
        }

        return new WhoisRiskAssessment(Math.min(extraScore, 35), deduplicate(factors), deduplicate(notes));
    }

    private static void collectDomains(JsonNode node, Set<String> domains) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            collectFromText(text, domains);
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectDomains(child, domains);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                collectDomains(iterator.next().getValue(), domains);
            }
        }
    }

    private static void collectFromText(String text, Set<String> domains) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Matcher urlMatcher = URL_PATTERN.matcher(text);
        while (urlMatcher.find()) {
            tryAddDomain(extractHost(urlMatcher.group()), domains);
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            tryAddDomain(emailMatcher.group(1), domains);
        }

        Matcher domainMatcher = DOMAIN_PATTERN.matcher(text);
        while (domainMatcher.find()) {
            tryAddDomain(domainMatcher.group(1), domains);
        }
    }

    private static String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void tryAddDomain(String raw, Set<String> domains) {
        String normalized = normalizeDomain(raw);
        if (normalized != null) {
            domains.add(normalized);
        }
    }

    private static String normalizeDomain(String value) {
        if (value == null) {
            return null;
        }
        String domain = value.trim().toLowerCase(Locale.ROOT);
        if (domain.startsWith("www.")) {
            domain = domain.substring(4);
        }
        if (domain.startsWith("http://") || domain.startsWith("https://")) {
            domain = extractHost(domain);
            if (domain == null) {
                return null;
            }
        }
        if (domain.contains("@")) {
            int index = domain.indexOf('@');
            domain = domain.substring(index + 1);
        }
        domain = domain.replaceAll("[:/].*$", "");
        domain = domain.replaceAll("[^a-z0-9.-]", "");
        if (domain.isEmpty() || "localhost".equals(domain) || isIpv4(domain)) {
            return null;
        }
        if (!domain.contains(".")) {
            return null;
        }
        if (!isDomainShapeValid(domain)) {
            return null;
        }
        return toRegistrableDomain(domain);
    }

    private static boolean isDomainShapeValid(String domain) {
        if (domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            return false;
        }
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return false;
        }
        String tld = labels[labels.length - 1];
        if (!ALPHA_TLD_PATTERN.matcher(tld).matches()) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return false;
            }
            if (label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
        }
        return true;
    }

    private static String toRegistrableDomain(String domain) {
        String[] labels = domain.split("\\.");
        if (labels.length <= 2) {
            return domain;
        }
        String suffix2 = labels[labels.length - 2] + "." + labels[labels.length - 1];
        if (isMultiPartPublicSuffix(suffix2) && labels.length >= 3) {
            return labels[labels.length - 3] + "." + suffix2;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static boolean isMultiPartPublicSuffix(String suffix2) {
        return "com.cn".equals(suffix2)
                || "net.cn".equals(suffix2)
                || "org.cn".equals(suffix2)
                || "gov.cn".equals(suffix2)
                || "edu.cn".equals(suffix2)
                || "co.uk".equals(suffix2)
                || "org.uk".equals(suffix2)
                || "ac.uk".equals(suffix2)
                || "com.au".equals(suffix2)
                || "net.au".equals(suffix2)
                || "org.au".equals(suffix2);
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

    private static Long resolveAgeDays(String createdDate) {
        LocalDate createDay = parseDate(createdDate);
        if (createDay == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(createDay, LocalDate.now());
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        if (value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static String safeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static List<String> deduplicate(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }

    @Data
    @AllArgsConstructor
    public static class WhoisLookupResult {
        private String domain;
        private JsonNode payload;
    }

    @Data
    @AllArgsConstructor
    public static class WhoisRiskAssessment {
        private Integer extraScore;
        private List<String> factors;
        private List<String> notes;

        public static WhoisRiskAssessment empty() {
            return new WhoisRiskAssessment(0, new ArrayList<>(), new ArrayList<>());
        }
    }
}
