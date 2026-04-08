package cn.iocoder.yudao.module.pay.service.risk.util;

import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskRelationTopology;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class PayRiskRelationTopologyAnalyzer {

    private static final List<String> PAYER_KEYS = Arrays.asList(
            "payer", "payerInfo", "payerUser", "paymentUser", "sender", "from", "fromUser", "付款人", "支付方"
    );
    private static final List<String> PAYEE_KEYS = Arrays.asList(
            "payee", "payeeInfo", "receiver", "recipient", "beneficiary", "merchant", "seller", "to", "收款人", "收款方"
    );
    private static final List<String> PARTICIPANT_ID_KEYS = Arrays.asList(
            "userId", "memberId", "accountNo", "account", "accountId", "walletId",
            "merchantId", "merchantNo", "openId", "openid", "mobile", "phone",
            "bankCard", "bankCardNo", "cardNo", "idNo", "id"
    );
    private static final List<String> PARTICIPANT_LABEL_KEYS = Arrays.asList(
            "name", "realName", "nickname", "accountName", "merchantName", "displayName",
            "phone", "mobile", "accountNo", "walletId", "merchantNo", "userId", "memberId"
    );
    private static final List<String> ATTRIBUTE_KEYS = Arrays.asList(
            "ip", "deviceId", "fingerprint", "mobile", "phone", "bankCard", "bankCardNo",
            "cardNo", "accountNo", "email", "merchantNo"
    );
    private static final List<String> AMOUNT_KEYS = Arrays.asList(
            "amount", "payAmount", "totalAmount", "transferAmount", "orderAmount"
    );

    public static TopologyRiskAssessment analyze(JsonNode paymentData) {
        GraphBuilder builder = new GraphBuilder();
        List<TransactionContext> transactions = new ArrayList<>();
        collectTransactions(paymentData, "$", transactions);
        if (transactions.isEmpty() && paymentData != null && paymentData.isObject()) {
            transactions.add(new TransactionContext(paymentData, "$"));
        }

        for (TransactionContext transaction : transactions) {
            ParticipantCandidate payer = resolveParticipant(transaction.getPayload(), PAYER_KEYS, "payer");
            ParticipantCandidate payee = resolveParticipant(transaction.getPayload(), PAYEE_KEYS, "payee");
            BigDecimal amount = extractAmount(transaction.getPayload());
            String relationLabel = firstText(transaction.getPayload(),
                    "relationLabel", "relationship", "relationType", "scenarioTag");

            String payerNodeId = builder.addParticipant(payer, "PAYER");
            String payeeNodeId = builder.addParticipant(payee, "PAYEE");
            if (payerNodeId != null || payeeNodeId != null) {
                builder.incrementTransactionCount();
            }
            if (payerNodeId != null && payeeNodeId != null) {
                builder.addTransferEdge(payerNodeId, payeeNodeId, amount, relationLabel, transaction.getPath());
            }
            if (payer != null) {
                builder.bindAttributes(payerNodeId, payer.getAttributes(), "PAYER");
            }
            if (payee != null) {
                builder.bindAttributes(payeeNodeId, payee.getAttributes(), "PAYEE");
            }
        }

        builder.analyzeSignals();
        return builder.build();
    }

    private static void collectTransactions(JsonNode node, String path, List<TransactionContext> transactions) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            boolean hasPayer = containsRoleField(node, PAYER_KEYS);
            boolean hasPayee = containsRoleField(node, PAYEE_KEYS);
            if (hasPayer && hasPayee) {
                transactions.add(new TransactionContext(node, path));
            }
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                collectTransactions(entry.getValue(), path + "." + entry.getKey(), transactions);
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectTransactions(node.get(i), path + "[" + i + "]", transactions);
            }
        }
    }

    private static boolean containsRoleField(JsonNode node, List<String> roleKeys) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (String roleKey : roleKeys) {
            if (findField(node, roleKey) != null) {
                return true;
            }
            if (!collectPrefixedFields(node, roleKey).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ParticipantCandidate resolveParticipant(JsonNode node, List<String> roleKeys, String defaultRole) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String roleKey : roleKeys) {
            JsonNode direct = findField(node, roleKey);
            if (direct != null && !direct.isNull()) {
                ParticipantCandidate candidate = buildCandidate(direct, defaultRole);
                if (candidate != null) {
                    return candidate;
                }
            }
            Map<String, String> prefixedFields = collectPrefixedFields(node, roleKey);
            if (!prefixedFields.isEmpty()) {
                ParticipantCandidate candidate = buildCandidate(prefixedFields, defaultRole);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static ParticipantCandidate buildCandidate(JsonNode node, String defaultRole) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            String identity = normalizeText(node.asText());
            if (identity == null) {
                return null;
            }
            return new ParticipantCandidate(defaultRole, identity, identity, Collections.<String, String>emptyMap());
        }
        if (!node.isObject()) {
            return null;
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (!entry.getValue().isValueNode()) {
                continue;
            }
            String value = normalizeText(entry.getValue().asText());
            if (value != null) {
                attributes.put(entry.getKey(), value);
            }
        }

        String identity = firstValue(attributes, PARTICIPANT_ID_KEYS);
        String label = firstValue(attributes, PARTICIPANT_LABEL_KEYS);
        if (label == null) {
            label = identity;
        }
        if (identity == null && label == null) {
            return null;
        }
        return new ParticipantCandidate(defaultRole, identity, label, attributes);
    }

    private static ParticipantCandidate buildCandidate(Map<String, String> prefixedFields, String defaultRole) {
        if (prefixedFields.isEmpty()) {
            return null;
        }
        String identity = firstValue(prefixedFields, PARTICIPANT_ID_KEYS);
        String label = firstValue(prefixedFields, PARTICIPANT_LABEL_KEYS);
        if (label == null) {
            label = identity;
        }
        if (identity == null && label == null) {
            return null;
        }
        return new ParticipantCandidate(defaultRole, identity, label, prefixedFields);
    }

    private static String firstValue(Map<String, String> values, List<String> keys) {
        for (String key : keys) {
            String lowerKey = key.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String entryKey = entry.getKey().toLowerCase(Locale.ROOT);
                if (entryKey.equals(lowerKey) || entryKey.endsWith(lowerKey)) {
                    return normalizeText(entry.getValue());
                }
            }
        }
        return null;
    }

    private static JsonNode findField(JsonNode node, String expectedKey) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (entry.getKey().equalsIgnoreCase(expectedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Map<String, String> collectPrefixedFields(JsonNode node, String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if (!entry.getValue().isValueNode()) {
                continue;
            }
            String fieldName = entry.getKey();
            String lowerFieldName = fieldName.toLowerCase(Locale.ROOT);
            if (!lowerFieldName.startsWith(lowerPrefix)) {
                continue;
            }
            String suffix = fieldName.substring(prefix.length());
            if (suffix.isEmpty()) {
                suffix = fieldName;
            } else {
                suffix = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
            }
            String value = normalizeText(entry.getValue().asText());
            if (value != null) {
                values.put(suffix, value);
            }
        }
        return values;
    }

    private static BigDecimal extractAmount(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String amountKey : AMOUNT_KEYS) {
            JsonNode field = findField(node, amountKey);
            if (field != null && field.isValueNode()) {
                try {
                    return new BigDecimal(field.asText().trim());
                } catch (Exception ignored) {
                    // ignore malformed amount
                }
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode field = findField(node, key);
            if (field != null && field.isValueNode()) {
                String value = normalizeText(field.asText());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static String normalizeIdentity(String value) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static List<String> deduplicate(List<String> items) {
        return new ArrayList<String>(new LinkedHashSet<String>(items));
    }

    private static class GraphBuilder {
        private final Map<String, PayRiskRelationTopology.Node> nodes = new LinkedHashMap<>();
        private final Map<String, PayRiskRelationTopology.Edge> edges = new LinkedHashMap<>();
        private final List<PayRiskRelationTopology.Signal> signals = new ArrayList<>();
        private final Map<String, Set<String>> outgoingTransfers = new LinkedHashMap<>();
        private final Map<String, Set<String>> incomingTransfers = new LinkedHashMap<>();
        private final Map<String, Set<String>> attributeBindings = new LinkedHashMap<>();
        private int transactionCount;
        private int sharedAttributeCount;

        private String addParticipant(ParticipantCandidate candidate, String role) {
            if (candidate == null) {
                return null;
            }
            String rawIdentity = candidate.getIdentity() != null ? candidate.getIdentity() : candidate.getLabel();
            String normalizedIdentity = normalizeIdentity(rawIdentity);
            if (normalizedIdentity == null) {
                return null;
            }
            String nodeId = "participant:" + normalizedIdentity;
            PayRiskRelationTopology.Node node = nodes.get(nodeId);
            if (node == null) {
                node = new PayRiskRelationTopology.Node();
                node.setId(nodeId);
                node.setLabel(candidate.getLabel() != null ? candidate.getLabel() : rawIdentity);
                node.setType("PARTICIPANT");
                node.setRole(role);
                node.setRiskLevel("LOW");
                node.setTags(new ArrayList<String>());
                node.setMeta(new LinkedHashMap<String, Object>());
                nodes.put(nodeId, node);
            }
            addTag(node, role);
            if (candidate.getAttributes() != null) {
                node.getMeta().putAll(candidate.getAttributes());
            }
            return nodeId;
        }

        private void bindAttributes(String participantNodeId, Map<String, String> attributes, String role) {
            if (participantNodeId == null || attributes == null || attributes.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String attributeType = resolveAttributeType(entry.getKey());
                if (attributeType == null) {
                    continue;
                }
                String attributeValue = normalizeText(entry.getValue());
                if (attributeValue == null) {
                    continue;
                }
                String attributeNodeId = addAttributeNode(attributeType, attributeValue);
                addAttributeEdge(participantNodeId, attributeNodeId, attributeType, role);
                Set<String> participants = attributeBindings.computeIfAbsent(attributeNodeId, key -> new LinkedHashSet<String>());
                participants.add(participantNodeId);
            }
        }

        private String addAttributeNode(String attributeType, String attributeValue) {
            String nodeId = attributeType.toLowerCase(Locale.ROOT) + ":" + normalizeIdentity(attributeValue);
            PayRiskRelationTopology.Node node = nodes.get(nodeId);
            if (node == null) {
                node = new PayRiskRelationTopology.Node();
                node.setId(nodeId);
                node.setLabel(attributeValue);
                node.setType(attributeType);
                node.setRole(attributeType);
                node.setRiskLevel("LOW");
                node.setTags(new ArrayList<String>());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("value", attributeValue);
                node.setMeta(meta);
                nodes.put(nodeId, node);
            }
            return nodeId;
        }

        private void addAttributeEdge(String source, String target, String attributeType, String role) {
            String edgeId = source + "->" + target + ":USES_" + attributeType;
            PayRiskRelationTopology.Edge edge = edges.get(edgeId);
            if (edge == null) {
                edge = new PayRiskRelationTopology.Edge();
                edge.setSource(source);
                edge.setTarget(target);
                edge.setType("USES_" + attributeType);
                edge.setLabel(role + " uses " + attributeType.toLowerCase(Locale.ROOT));
                edge.setRiskLevel("LOW");
                edge.setCount(1);
                edge.setMeta(new LinkedHashMap<String, Object>());
                edges.put(edgeId, edge);
            } else {
                edge.setCount(edge.getCount() + 1);
            }
        }

        private void addTransferEdge(String source, String target, BigDecimal amount, String relationLabel, String path) {
            String edgeId = source + "->" + target + ":TRANSFER";
            PayRiskRelationTopology.Edge edge = edges.get(edgeId);
            if (edge == null) {
                edge = new PayRiskRelationTopology.Edge();
                edge.setSource(source);
                edge.setTarget(target);
                edge.setType("TRANSFER");
                edge.setLabel(relationLabel != null ? relationLabel : "payer -> payee");
                edge.setRiskLevel("LOW");
                edge.setCount(1);
                edge.setAmount(amount);
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("path", path);
                if (relationLabel != null) {
                    meta.put("relationLabel", relationLabel);
                }
                edge.setMeta(meta);
                edges.put(edgeId, edge);
            } else {
                edge.setCount(edge.getCount() + 1);
                if (amount != null) {
                    edge.setAmount(edge.getAmount() == null ? amount : edge.getAmount().add(amount));
                }
                if (relationLabel != null) {
                    edge.setLabel(relationLabel);
                    edge.getMeta().put("relationLabel", relationLabel);
                }
            }
            outgoingTransfers.computeIfAbsent(source, key -> new LinkedHashSet<String>()).add(target);
            incomingTransfers.computeIfAbsent(target, key -> new LinkedHashSet<String>()).add(source);
        }

        private void analyzeSignals() {
            analyzeSharedAttributes();
            analyzeMultiHopPayers();
            analyzeMultiSourcePayees();
            analyzeBidirectionalTransfers();
            refreshRiskLevels();
        }

        private void analyzeSharedAttributes() {
            for (Map.Entry<String, Set<String>> entry : attributeBindings.entrySet()) {
                if (entry.getValue().size() < 2) {
                    continue;
                }
                sharedAttributeCount++;
                PayRiskRelationTopology.Node attributeNode = nodes.get(entry.getKey());
                String type = attributeNode != null ? attributeNode.getType() : "ATTRIBUTE";
                int score = "DEVICE".equals(type) ? 10 : "IP".equals(type) ? 8 : 6;
                addSignal("SHARED_" + type,
                        score >= 10 ? "HIGH" : "MEDIUM",
                        "Shared " + type.toLowerCase(Locale.ROOT) + " detected",
                        entry.getValue().size() + " participants are bound to the same " + type.toLowerCase(Locale.ROOT) + ".",
                        score,
                        new ArrayList<String>(entry.getValue()));
            }
        }

        private void analyzeMultiHopPayers() {
            for (Map.Entry<String, Set<String>> entry : outgoingTransfers.entrySet()) {
                if (entry.getValue().size() < 3) {
                    continue;
                }
                addSignal("PAYER_FAN_OUT",
                        "HIGH",
                        "Payer fans out to multiple payees",
                        "A single payer is linked to " + entry.getValue().size() + " distinct payees.",
                        12,
                        mergeNodeIds(entry.getKey(), entry.getValue()));
            }
        }

        private void analyzeMultiSourcePayees() {
            for (Map.Entry<String, Set<String>> entry : incomingTransfers.entrySet()) {
                if (entry.getValue().size() < 3) {
                    continue;
                }
                addSignal("PAYEE_FAN_IN",
                        "HIGH",
                        "Payee aggregates multiple upstream payers",
                        "A single payee receives funds from " + entry.getValue().size() + " distinct payers.",
                        12,
                        mergeNodeIds(entry.getKey(), entry.getValue()));
            }
        }

        private void analyzeBidirectionalTransfers() {
            Set<String> visited = new LinkedHashSet<>();
            for (Map.Entry<String, Set<String>> entry : outgoingTransfers.entrySet()) {
                String source = entry.getKey();
                for (String target : entry.getValue()) {
                    String pairKey = source.compareTo(target) < 0 ? source + "|" + target : target + "|" + source;
                    if (visited.contains(pairKey)) {
                        continue;
                    }
                    Set<String> reverseTargets = outgoingTransfers.get(target);
                    if (reverseTargets != null && reverseTargets.contains(source)) {
                        visited.add(pairKey);
                        addSignal("BIDIRECTIONAL_TRANSFER",
                                "MEDIUM",
                                "Bidirectional fund flow detected",
                                "Participants transfer funds to each other in both directions.",
                                8,
                                Arrays.asList(source, target));
                    }
                }
            }
        }

        private void refreshRiskLevels() {
            Map<String, Integer> nodeScores = new LinkedHashMap<>();
            for (PayRiskRelationTopology.Signal signal : signals) {
                if (signal.getRelatedNodeIds() == null) {
                    continue;
                }
                for (String nodeId : signal.getRelatedNodeIds()) {
                    nodeScores.put(nodeId, nodeScores.getOrDefault(nodeId, 0) + (signal.getScore() == null ? 0 : signal.getScore()));
                }
            }
            for (PayRiskRelationTopology.Node node : nodes.values()) {
                node.setRiskLevel(resolveRiskLevel(nodeScores.getOrDefault(node.getId(), 0)));
            }
            for (PayRiskRelationTopology.Edge edge : edges.values()) {
                int score = nodeScores.getOrDefault(edge.getSource(), 0) + nodeScores.getOrDefault(edge.getTarget(), 0);
                edge.setRiskLevel(resolveRiskLevel(score / 2));
            }
        }

        private void addSignal(String code, String level, String title, String description, int score, List<String> relatedNodeIds) {
            PayRiskRelationTopology.Signal signal = new PayRiskRelationTopology.Signal();
            signal.setCode(code);
            signal.setLevel(level);
            signal.setTitle(title);
            signal.setDescription(description);
            signal.setScore(score);
            signal.setRelatedNodeIds(relatedNodeIds);
            signals.add(signal);
        }

        private List<String> mergeNodeIds(String center, Set<String> neighbors) {
            List<String> nodeIds = new ArrayList<>();
            nodeIds.add(center);
            nodeIds.addAll(neighbors);
            return nodeIds;
        }

        private void addTag(PayRiskRelationTopology.Node node, String tag) {
            if (node.getTags() == null) {
                node.setTags(new ArrayList<String>());
            }
            if (!node.getTags().contains(tag)) {
                node.getTags().add(tag);
            }
        }

        private void incrementTransactionCount() {
            transactionCount++;
        }

        private TopologyRiskAssessment build() {
            PayRiskRelationTopology topology = new PayRiskRelationTopology();
            topology.setNodes(new ArrayList<PayRiskRelationTopology.Node>(nodes.values()));
            topology.setEdges(new ArrayList<PayRiskRelationTopology.Edge>(edges.values()));
            topology.setSignals(signals);

            PayRiskRelationTopology.Summary summary = new PayRiskRelationTopology.Summary();
            summary.setNodeCount(topology.getNodes().size());
            summary.setEdgeCount(topology.getEdges().size());
            summary.setParticipantCount(countNodesByType("PARTICIPANT"));
            summary.setPayerCount(countNodesWithTag("PAYER"));
            summary.setPayeeCount(countNodesWithTag("PAYEE"));
            summary.setTransactionCount(transactionCount);
            summary.setSignalCount(signals.size());
            summary.setSharedAttributeCount(sharedAttributeCount);
            topology.setSummary(summary);

            int extraScore = 0;
            List<String> factors = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (PayRiskRelationTopology.Signal signal : signals) {
                extraScore += signal.getScore() == null ? 0 : signal.getScore();
                factors.add(signal.getTitle());
                notes.add(signal.getDescription());
            }
            return new TopologyRiskAssessment(
                    Math.min(extraScore, 35),
                    deduplicate(factors),
                    deduplicate(notes),
                    topology
            );
        }

        private int countNodesByType(String type) {
            int count = 0;
            for (PayRiskRelationTopology.Node node : nodes.values()) {
                if (type.equalsIgnoreCase(node.getType())) {
                    count++;
                }
            }
            return count;
        }

        private int countNodesWithTag(String tag) {
            int count = 0;
            for (PayRiskRelationTopology.Node node : nodes.values()) {
                if (node.getTags() != null && node.getTags().contains(tag)) {
                    count++;
                }
            }
            return count;
        }

        private String resolveRiskLevel(int score) {
            if (score >= 20) {
                return "HIGH";
            }
            if (score >= 10) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private String resolveAttributeType(String fieldName) {
            String lower = fieldName.toLowerCase(Locale.ROOT);
            if (lower.contains("device") || lower.contains("fingerprint")) {
                return "DEVICE";
            }
            if ("ip".equals(lower)) {
                return "IP";
            }
            if (lower.contains("phone") || lower.contains("mobile")) {
                return "PHONE";
            }
            if (lower.contains("card")) {
                return "CARD";
            }
            if (lower.contains("account")) {
                return "ACCOUNT";
            }
            if (lower.contains("email")) {
                return "EMAIL";
            }
            if (lower.contains("merchant")) {
                return "MERCHANT";
            }
            for (String attributeKey : ATTRIBUTE_KEYS) {
                if (lower.equals(attributeKey.toLowerCase(Locale.ROOT))) {
                    return attributeKey.toUpperCase(Locale.ROOT);
                }
            }
            return null;
        }
    }

    @Data
    @AllArgsConstructor
    public static class TopologyRiskAssessment {
        private Integer extraScore;
        private List<String> factors;
        private List<String> notes;
        private PayRiskRelationTopology topology;
    }

    @Data
    @AllArgsConstructor
    private static class TransactionContext {
        private JsonNode payload;
        private String path;
    }

    @Data
    @AllArgsConstructor
    private static class ParticipantCandidate {
        private String role;
        private String identity;
        private String label;
        private Map<String, String> attributes;
    }
}
