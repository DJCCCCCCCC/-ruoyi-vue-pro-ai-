package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskAssessRecordMapper;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAdvancedAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@UtilityClass
public class PayRiskCaseSimilarityAnalyzer {

    private static final int MAX_CANDIDATES = 120;
    private static final int MAX_MATCHES = 5;
    private static final double MIN_MATCH_SCORE = 0.28d;

    public static CaseSimilarityResult analyze(JsonNode paymentData,
                                               JsonNode ipInfo,
                                               JsonNode whoisInfo,
                                               PayRiskAssessRecordMapper mapper) {
        CaseFingerprint current = CaseFingerprint.from(paymentData, ipInfo, whoisInfo, null, null, null);
        List<PayRiskAssessRecordDO> records = mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayRiskAssessRecordDO>()
                .orderByDesc(PayRiskAssessRecordDO::getId)
                .last("LIMIT " + MAX_CANDIDATES));

        List<PayRiskAdvancedAnalysis.CaseSimilarityMatch> matches = new ArrayList<>();
        for (PayRiskAssessRecordDO record : records) {
            CaseFingerprint historical = CaseFingerprint.from(
                    parseJson(record.getPaymentDataJson()),
                    parseJson(record.getIpInfoJson()),
                    parseJson(record.getWhoisInfoJson()),
                    parseJson(record.getBehaviorInfoJson()),
                    parseJson(record.getTopologyInfoJson()),
                    parseJson(record.getLlmReportJson())
            );
            SimilarityScore score = current.similarity(historical);
            if (score.totalScore < MIN_MATCH_SCORE) {
                continue;
            }
            PayRiskAdvancedAnalysis.CaseSimilarityMatch match = new PayRiskAdvancedAnalysis.CaseSimilarityMatch();
            match.setRecordId(record.getId());
            match.setScene(record.getScene());
            match.setSource(record.getSource());
            match.setRiskLevel(record.getRiskLevel());
            match.setRiskScore(record.getRiskScore());
            match.setSimilarity(round(score.totalScore));
            match.setBonusScore(score.toBonusScore());
            match.setMatchedReasons(String.join("；", score.reasons));
            match.setSummary(buildSummary(record));
            matches.add(match);
        }

        matches.sort(Comparator.comparing(PayRiskAdvancedAnalysis.CaseSimilarityMatch::getSimilarity, Comparator.nullsLast(Double::compareTo)).reversed());
        if (matches.size() > MAX_MATCHES) {
            matches = new ArrayList<>(matches.subList(0, MAX_MATCHES));
        }

        int bonusScore = matches.stream().mapToInt(m -> m.getBonusScore() == null ? 0 : m.getBonusScore()).sum();
        bonusScore = Math.min(20, bonusScore);

        List<String> factors = new ArrayList<>();
        if (bonusScore > 0) {
            factors.add("命中历史相似案例，最高相似度 " + (matches.isEmpty() ? "0" : matches.get(0).getSimilarity()) + "，触发案例经验加权");
            if (!matches.isEmpty()) {
                factors.add("最相似案例风险等级：" + matches.get(0).getRiskLevel());
            }
        }

        List<String> notes = new ArrayList<>();
        if (matches.isEmpty()) {
            notes.add("未检出足够相似的历史案例，未触发案例加分。");
        } else {
            notes.add("已根据历史案例相似性进行风险加权，建议结合高相似案例进行复核。");
        }
        return new CaseSimilarityResult(bonusScore, factors, notes, matches);
    }

    private static String buildSummary(PayRiskAssessRecordDO record) {
        StringBuilder sb = new StringBuilder();
        if (record.getScene() != null) {
            sb.append("场景=").append(record.getScene()).append(' ');
        }
        if (record.getSource() != null) {
            sb.append("来源=").append(record.getSource()).append(' ');
        }
        if (record.getRiskLevel() != null) {
            sb.append("等级=").append(record.getRiskLevel()).append(' ');
        }
        if (record.getRiskScore() != null) {
            sb.append("分数=").append(record.getRiskScore());
        }
        return sb.toString().trim();
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static JsonNode parseJson(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : JsonUtils.parseTree(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    @Data
    public static class CaseSimilarityResult {
        private final int bonusScore;
        private final List<String> riskFactors;
        private final List<String> notes;
        private final List<PayRiskAdvancedAnalysis.CaseSimilarityMatch> matches;
    }

    @Data

    private static final class CaseFingerprint {
        private final String scene;
        private final String source;
        private final String ip;
        private final String riskLevel;
        private final String riskBand;
        private final Set<String> tokens;
        private final Map<String, String> weightedSignals;

        private CaseFingerprint(String scene,
                                String source,
                                String ip,
                                String riskLevel,
                                String riskBand,
                                Set<String> tokens,
                                Map<String, String> weightedSignals) {
            this.scene = normalize(scene);
            this.source = normalize(source);
            this.ip = normalize(ip);
            this.riskLevel = normalize(riskLevel);
            this.riskBand = normalize(riskBand);
            this.tokens = tokens;
            this.weightedSignals = weightedSignals;
        }

        static CaseFingerprint from(JsonNode paymentData,
                                    JsonNode ipInfo,
                                    JsonNode whoisInfo,
                                    JsonNode behaviorInfo,
                                    JsonNode topologyInfo,
                                    JsonNode llmReport) {
            Set<String> tokens = new LinkedHashSet<>();
            Map<String, String> weightedSignals = new LinkedHashMap<>();

            addToken(tokens, weightedSignals, "scene", text(paymentData, "scene"));
            addToken(tokens, weightedSignals, "source", text(paymentData, "source"));
            addToken(tokens, weightedSignals, "merchantName", text(paymentData, "merchantName"));
            addToken(tokens, weightedSignals, "merchantId", text(paymentData, "merchantId"));
            addToken(tokens, weightedSignals, "payerAccount", text(paymentData, "payerAccount"));
            addToken(tokens, weightedSignals, "payeeAccount", text(paymentData, "payeeAccount"));
            addToken(tokens, weightedSignals, "payerBank", text(paymentData, "payerBank"));
            addToken(tokens, weightedSignals, "payeeBank", text(paymentData, "payeeBank"));
            addToken(tokens, weightedSignals, "deviceId", text(paymentData, "deviceId"));
            addToken(tokens, weightedSignals, "deviceFingerprint", text(paymentData, "deviceFingerprint"));
            addToken(tokens, weightedSignals, "userId", text(paymentData, "userId"));
            addToken(tokens, weightedSignals, "orderId", text(paymentData, "orderId"));
            addToken(tokens, weightedSignals, "tradeNo", text(paymentData, "tradeNo"));
            addToken(tokens, weightedSignals, "phone", text(paymentData, "phone"));
            addToken(tokens, weightedSignals, "email", text(paymentData, "email"));
            addToken(tokens, weightedSignals, "linkCount", text(paymentData, "linkCount"));
            addToken(tokens, weightedSignals, "messageCount", text(paymentData, "messageCount"));
            addToken(tokens, weightedSignals, "amount", bucketAmount(text(paymentData, "amount")));
            addToken(tokens, weightedSignals, "channel", text(paymentData, "channel"));
            addToken(tokens, weightedSignals, "currency", text(paymentData, "currency"));
            addToken(tokens, weightedSignals, "ip", text(ipInfo, "ip"));
            addToken(tokens, weightedSignals, "country", text(ipInfo, "country"));
            addToken(tokens, weightedSignals, "countryCode", text(ipInfo, "countryCode"));
            addToken(tokens, weightedSignals, "org", text(ipInfo, "org"));
            addToken(tokens, weightedSignals, "city", text(ipInfo, "city"));
            addToken(tokens, weightedSignals, "regionName", text(ipInfo, "regionName"));
            addToken(tokens, weightedSignals, "domain", textFromWhoisRecords(whoisInfo, "domain"));
            addToken(tokens, weightedSignals, "registrar", textFromWhoisRecords(whoisInfo, "registrar"));
            addToken(tokens, weightedSignals, "nameServers", textFromWhoisRecords(whoisInfo, "nameServers"));
            addToken(tokens, weightedSignals, "behaviorExtra", text(behaviorInfo, "extraScore"));
            addToken(tokens, weightedSignals, "behaviorMocked", text(behaviorInfo, "mocked"));
            addToken(tokens, weightedSignals, "behaviorSummary", text(behaviorInfo, "summary"));
            addToken(tokens, weightedSignals, "topologySignals", text(topologyInfo, "signalCount"));
            addToken(tokens, weightedSignals, "topologyHighRiskNodes", text(topologyInfo, "highRiskNodeCount"));
            addToken(tokens, weightedSignals, "topologyClusters", text(topologyInfo, "suspiciousClusterCount"));
            addToken(tokens, weightedSignals, "llmVerdict", text(llmReport, "verdict"));
            addToken(tokens, weightedSignals, "llmConfidence", text(llmReport, "confidence"));
            addToken(tokens, weightedSignals, "llmEntities", textArray(llmReport, "suspiciousEntities"));
            addToken(tokens, weightedSignals, "llmEvidence", textArray(llmReport, "evidence"));

            String scene = text(paymentData, "scene");
            String source = text(paymentData, "source");
            String ip = text(ipInfo, "ip");
            String riskLevel = text(paymentData, "riskLevel");
            String riskBand = riskBand(text(paymentData, "riskScore"));
            return new CaseFingerprint(scene, source, ip, riskLevel, riskBand, tokens, weightedSignals);
        }

        static CaseFingerprint from(JsonNode paymentData, JsonNode ipInfo, JsonNode whoisInfo, JsonNode behaviorInfo, JsonNode topologyInfo, JsonNode llmReport, boolean unused) {
            return from(paymentData, ipInfo, whoisInfo, behaviorInfo, topologyInfo, llmReport);
        }

        SimilarityScore similarity(CaseFingerprint other) {
            List<String> reasons = new ArrayList<>();
            double score = 0d;

            score += matchExact("场景一致", 0.16d, scene, other.scene, reasons);
            score += matchExact("来源一致", 0.10d, source, other.source, reasons);
            score += matchExact("IP一致", 0.18d, ip, other.ip, reasons);
            score += matchExact("风险等级接近", 0.05d, riskLevel, other.riskLevel, reasons);
            score += matchExact("风险分段接近", 0.06d, riskBand, other.riskBand, reasons);

            score += weightedTokenScore(other, reasons);
            score += signalOverlapScore(other, reasons);

            score = Math.min(1d, score);
            return new SimilarityScore(score, reasons);
        }

        private double weightedTokenScore(CaseFingerprint other, List<String> reasons) {
            if (tokens.isEmpty() || other.tokens.isEmpty()) {
                return 0d;
            }
            int intersection = 0;
            for (String token : tokens) {
                if (other.tokens.contains(token)) {
                    intersection++;
                }
            }
            int union = Math.max(1, tokens.size() + other.tokens.size() - intersection);
            double jaccard = (double) intersection / union;
            if (jaccard <= 0d) {
                return 0d;
            }
            double score = Math.min(0.34d, jaccard * 0.58d);
            reasons.add("关键字段重合度=" + round(jaccard));
            return score;
        }

        private double signalOverlapScore(CaseFingerprint other, List<String> reasons) {
            if (weightedSignals.isEmpty() || other.weightedSignals.isEmpty()) {
                return 0d;
            }
            int hit = 0;
            int weight = 0;
            for (Map.Entry<String, String> entry : weightedSignals.entrySet()) {
                weight += signalWeight(entry.getKey());
                String otherValue = other.weightedSignals.get(entry.getKey());
                if (otherValue != null && Objects.equals(entry.getValue(), otherValue)) {
                    hit += signalWeight(entry.getKey());
                }
            }
            if (weight <= 0 || hit <= 0) {
                return 0d;
            }
            double ratio = (double) hit / weight;
            double score = Math.min(0.22d, ratio * 0.38d);
            reasons.add("强信号命中率=" + round(ratio));
            return score;
        }

        private static double matchExact(String reason,
                                         double maxWeight,
                                         String left,
                                         String right,
                                         List<String> reasons) {
            if (!nonEmpty(left) || !Objects.equals(left, right)) {
                return 0d;
            }
            reasons.add(reason);
            return maxWeight;
        }

        private static void addToken(Set<String> tokens, Map<String, String> signals, String key, String value) {
            String normalized = normalize(value);
            if (normalized == null || normalized.isEmpty()) {
                return;
            }
            tokens.add(key + ':' + normalized);
            if (isStrongSignal(key)) {
                signals.put(key, normalized);
            }
        }

        private static boolean isStrongSignal(String key) {
            if ("scene".equals(key) || "source".equals(key) || "ip".equals(key)
                    || "deviceId".equals(key) || "deviceFingerprint".equals(key)
                    || "payerAccount".equals(key) || "payeeAccount".equals(key)
                    || "merchantId".equals(key) || "merchantName".equals(key)
                    || "tradeNo".equals(key) || "orderId".equals(key)
                    || "domain".equals(key) || "registrar".equals(key) || "nameServers".equals(key)) {
                return true;
            }
            return false;
        }

        private static int signalWeight(String key) {
            if ("scene".equals(key)) {
                return 3;
            }
            if ("source".equals(key)) {
                return 2;
            }
            if ("ip".equals(key)) {
                return 4;
            }
            if ("deviceId".equals(key) || "deviceFingerprint".equals(key)) {
                return 4;
            }
            if ("payerAccount".equals(key) || "payeeAccount".equals(key)) {
                return 4;
            }
            if ("merchantId".equals(key) || "merchantName".equals(key)) {
                return 3;
            }
            if ("tradeNo".equals(key) || "orderId".equals(key)) {
                return 2;
            }
            if ("domain".equals(key) || "registrar".equals(key) || "nameServers".equals(key)) {
                return 3;
            }
            return 1;
        }

        private static String text(JsonNode node, String field) {
            if (node == null || node.isNull()) {
                return null;
            }
            JsonNode val = node.path(field);
            return val.isMissingNode() || val.isNull() ? null : val.asText(null);
        }

        private static String textArray(JsonNode node, String field) {
            if (node == null || node.isNull()) {
                return null;
            }
            JsonNode val = node.path(field);
            if (val.isMissingNode() || val.isNull()) {
                return null;
            }
            return val.isArray() ? JsonUtils.toJsonString(val) : val.asText(null);
        }


        private static String textFromWhoisRecords(JsonNode whoisInfo, String field) {
            if (whoisInfo == null || whoisInfo.isNull()) {
                return null;
            }
            JsonNode records = whoisInfo.path("records");
            if (!records.isArray() || records.isEmpty()) {
                return text(whoisInfo, field);
            }
            JsonNode first = records.get(0);
            if (first == null || first.isNull()) {
                return null;
            }
            JsonNode payload = first.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                return text(first, field);
            }
            return text(payload, field);
        }

        private static String bucketAmount(String amountText) {
            if (amountText == null || amountText.trim().isEmpty()) {
                return null;
            }
            try {
                double amount = Double.parseDouble(amountText.replaceAll(",", ""));
                if (amount < 100) {
                    return "<100";
                }
                if (amount < 1000) {
                    return "100-999";
                }
                if (amount < 10000) {
                    return "1000-9999";
                }
                return ">=10000";
            } catch (Exception ignore) {
                return normalize(amountText);
            }
        }

        private static String riskBand(String riskScoreText) {
            if (riskScoreText == null || riskScoreText.trim().isEmpty()) {
                return null;
            }
            try {
                int score = Integer.parseInt(riskScoreText.trim());
                if (score < 30) {
                    return "L";
                }
                if (score < 60) {
                    return "M";
                }
                if (score < 80) {
                    return "H";
                }
                return "C";
            } catch (Exception ignore) {
                return normalize(riskScoreText);
            }
        }

        private static String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase();
        }

        private static boolean nonEmpty(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }

    @Data
    private static final class SimilarityScore {
        private final double totalScore;
        private final List<String> reasons;

        int toBonusScore() {
            if (totalScore >= 0.88d) return 10;
            if (totalScore >= 0.76d) return 8;
            if (totalScore >= 0.62d) return 6;
            if (totalScore >= 0.48d) return 4;
            return 2;
        }
    }
}
