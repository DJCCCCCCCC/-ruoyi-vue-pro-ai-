package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskAssessRecordMapper;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.client.DeepSeekClient;
import cn.iocoder.yudao.module.pay.service.risk.client.IpInfoClient;
import cn.iocoder.yudao.module.pay.service.risk.client.WhoisXmlApiClient;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAssessAiResponse;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskDesensitizer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskLinkAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskWhoisAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@Slf4j
public class PayRiskAssessServiceImpl implements PayRiskAssessService {

    @Resource
    private IpInfoClient ipInfoClient;

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private WhoisXmlApiClient whoisXmlApiClient;

    @Resource
    private PayRiskAssessRecordMapper payRiskAssessRecordMapper;

    @Override
    public AppPayRiskAssessRespVO assess(@Valid AppPayRiskAssessReqVO reqVO) {
        JsonNode paymentData = reqVO.getPaymentData();

        String ip = reqVO.getIp();
        if (ip == null || ip.trim().isEmpty()) {
            ip = PayRiskDesensitizer.extractFirstIp(paymentData);
        }
        if (ip == null || ip.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IP_MISSING);
        }

        JsonNode ipInfo = ipInfoClient.fetchIpInfo(ip);
        JsonNode paymentMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(paymentData);
        JsonNode ipInfoMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(ipInfo);

        String paymentMaskedJson = JsonUtils.toJsonString(paymentMaskedJsonNode);
        String ipInfoMaskedJson = JsonUtils.toJsonString(ipInfoMaskedJsonNode);

        PayRiskAssessAiResponse aiResp = deepSeekClient.assess(paymentMaskedJson, ipInfoMaskedJson);

        PayRiskLinkAnalyzer.LinkRiskAssessment linkRiskAssessment = PayRiskLinkAnalyzer.analyze(paymentData);
        WhoisAssessBundle whoisAssessBundle = assessWhoisRisk(paymentData);
        PayRiskWhoisAnalyzer.WhoisRiskAssessment whoisRiskAssessment = whoisAssessBundle.getAssessment();

        PayRiskAssessAiResponse mergedResp = mergeExternalRisk(aiResp,
                linkRiskAssessment.getExtraScore(),
                linkRiskAssessment.getFactors(),
                linkRiskAssessment.getNotes(),
                "Link intelligence");
        mergedResp = mergeExternalRisk(mergedResp,
                whoisRiskAssessment.getExtraScore(),
                whoisRiskAssessment.getFactors(),
                whoisRiskAssessment.getNotes(),
                "Whois intelligence");

        AppPayRiskAssessRespVO respVO = new AppPayRiskAssessRespVO();
        respVO.setRiskScore(mergedResp.getRiskScore());
        respVO.setRiskLevel(mergedResp.getRiskLevel());
        respVO.setDeepAnalysis(mergedResp.getDeepAnalysis());
        respVO.setRiskFactors(mergedResp.getRiskFactors());
        respVO.setIpInfo(ipInfoMaskedJsonNode);

        JsonNode whoisInfoNode = buildWhoisInfo(whoisAssessBundle);
        String whoisInfoStr = JsonUtils.toJsonString(whoisInfoNode);
        log.info("[assess] ====== WHOIS INFO 开始 ======");
        log.info("[assess] whoisInfoNode 类型: {}", whoisInfoNode != null ? whoisInfoNode.getClass().getSimpleName() : "null");
        log.info("[assess] whoisInfo JSON 字符串: {}", whoisInfoStr);
        log.info("[assess] ====== WHOIS INFO 结束 ======");

        respVO.setWhoisInfo(whoisInfoStr);

        if (whoisInfoNode != null && !whoisInfoNode.isNull() && whoisInfoNode.size() > 0) {
            String formattedWhois = formatWhoisInfoForDisplay(whoisInfoNode);
            String currentAnalysis = mergedResp.getDeepAnalysis() != null ? mergedResp.getDeepAnalysis() : "";
            respVO.setDeepAnalysis(currentAnalysis + "\n\n" + formattedWhois);

            List<String> currentFactors = mergedResp.getRiskFactors();
            if (currentFactors == null) {
                currentFactors = new ArrayList<>();
            }
            currentFactors.add("已完成域名 Whois 安全检测");
            respVO.setRiskFactors(currentFactors);

            log.info("[assess] ✅ 已将格式化的 Whois 详情加入 deepAnalysis");
        }

        saveAssessRecord(reqVO, ip, ipInfoMaskedJsonNode, respVO);
        return respVO;
    }

    @Override
    public Map<String, Object> assessAndReturnMap(@Valid AppPayRiskAssessReqVO reqVO) {
        AppPayRiskAssessRespVO respVO = this.assess(reqVO);

        Map<String, Object> result = new HashMap<>();
        result.put("riskScore", respVO.getRiskScore());
        result.put("riskLevel", respVO.getRiskLevel());
        result.put("deepAnalysis", respVO.getDeepAnalysis());
        result.put("riskFactors", respVO.getRiskFactors());
        result.put("ipInfo", respVO.getIpInfo());
        result.put("whoisInfo", respVO.getWhoisInfo());

        log.info("[assessAndReturnMap] 返回 Map，whoisInfo = {}", result.get("whoisInfo"));

        return result;
    }

    @Override
    public PageResult<PayRiskAssessRecordDO> getRiskAssessRecordPage(PayRiskAssessRecordPageReqVO pageReqVO) {
        return payRiskAssessRecordMapper.selectPage(pageReqVO);
    }

    @Override
    public void deleteRiskAssessRecord(Long id) {
        payRiskAssessRecordMapper.deleteByIdPhysically(id);
    }

    @Override
    public void clearRiskAssessRecords() {
        payRiskAssessRecordMapper.deleteAllPhysically();
    }

    private WhoisAssessBundle assessWhoisRisk(JsonNode paymentData) {
        List<String> domains = PayRiskWhoisAnalyzer.extractDomains(paymentData);
        log.info("[assessWhoisRisk] 从 paymentData 中提取到 {} 个域名: {}", domains.size(), domains);
        if (domains.isEmpty()) {
            log.warn("[assessWhoisRisk] paymentData 中未检测到任何域名/URL信息，跳过 Whois 查询");
            return new WhoisAssessBundle(PayRiskWhoisAnalyzer.WhoisRiskAssessment.empty(), new ArrayList<>());
        }

        List<PayRiskWhoisAnalyzer.WhoisLookupResult> lookupResults = new ArrayList<>();
        for (String domain : domains) {
            log.info("[assessWhoisRisk] 正在查询域名 Whois 信息: {}", domain);
            JsonNode payload = whoisXmlApiClient.lookupDomain(domain);
            log.info("[assessWhoisInfo] 域名 {} 查询完成, 是否有错误: {}", domain,
                    payload != null && payload.path("ErrorMessage").isMissingNode() ? "否" : "是");
            lookupResults.add(new PayRiskWhoisAnalyzer.WhoisLookupResult(domain, payload));
        }
        return new WhoisAssessBundle(PayRiskWhoisAnalyzer.analyze(lookupResults), lookupResults);
    }

    private JsonNode buildWhoisInfo(WhoisAssessBundle bundle) {
        ObjectNode root = JsonUtils.parseObject("{}", ObjectNode.class);
        if (bundle == null || bundle.getAssessment() == null) {
            log.warn("[buildWhoisInfo] WhoisAssessBundle 为空");
            return root;
        }

        root.put("extraScore", bundle.getAssessment().getExtraScore() == null ? 0 : bundle.getAssessment().getExtraScore());
        root.putPOJO("factors", bundle.getAssessment().getFactors());
        root.putPOJO("notes", bundle.getAssessment().getNotes());

        ArrayNode records = root.putArray("records");
        if (bundle.getLookupResults() != null) {
            log.info("[buildWhoisInfo] 构建 Whois 记录, 共 {} 条", bundle.getLookupResults().size());
            for (PayRiskWhoisAnalyzer.WhoisLookupResult lookupResult : bundle.getLookupResults()) {
                ObjectNode item = records.addObject();
                item.put("domain", lookupResult.getDomain());
                item.set("payload", lookupResult.getPayload());
                log.info("[buildWhoisInfo] 添加域名记录: {}, payload 大小: {}",
                        lookupResult.getDomain(),
                        lookupResult.getPayload() != null ? lookupResult.getPayload().toString().length() : 0);
            }
        } else {
            log.warn("[buildWhoisInfo] lookupResults 为空");
        }

        log.info("[buildWhoisInfo] Whois 信息构建完成: extraScore={}, factors数量={}, records数量={}",
                root.path("extraScore").asInt(),
                bundle.getAssessment().getFactors() != null ? bundle.getAssessment().getFactors().size() : 0,
                bundle.getLookupResults() != null ? bundle.getLookupResults().size() : 0);

        return root;
    }

    private void saveAssessRecord(AppPayRiskAssessReqVO reqVO, String ip, JsonNode ipInfoMaskedJsonNode,
                                  AppPayRiskAssessRespVO respVO) {
        JsonNode paymentData = reqVO.getPaymentData();
        PayRiskAssessRecordDO record = new PayRiskAssessRecordDO();
        record.setScene(readText(paymentData, "scene"));
        record.setSource(readText(paymentData, "source"));
        record.setIp(ip);
        record.setRiskScore(respVO.getRiskScore());
        record.setRiskLevel(respVO.getRiskLevel());
        record.setDeepAnalysis(respVO.getDeepAnalysis());
        record.setRiskFactorsJson(JsonUtils.toJsonString(respVO.getRiskFactors()));
        record.setPaymentDataJson(JsonUtils.toJsonString(paymentData));
        record.setIpInfoJson(JsonUtils.toJsonString(ipInfoMaskedJsonNode));
        payRiskAssessRecordMapper.insert(record);
    }

    private PayRiskAssessAiResponse mergeExternalRisk(PayRiskAssessAiResponse baseResp,
                                                      Integer extraScore,
                                                      List<String> extraFactors,
                                                      List<String> extraNotes,
                                                      String sourceLabel) {
        if (baseResp == null || extraScore == null || extraScore <= 0) {
            return baseResp;
        }

        int baseScore = baseResp.getRiskScore() == null ? 0 : baseResp.getRiskScore();
        int finalScore = Math.min(100, baseScore + extraScore);
        String finalRiskLevel = maxRiskLevel(baseResp.getRiskLevel(), resolveRiskLevel(finalScore));

        Set<String> mergedFactors = new LinkedHashSet<>();
        if (baseResp.getRiskFactors() != null) {
            mergedFactors.addAll(baseResp.getRiskFactors());
        }
        if (extraFactors != null) {
            mergedFactors.addAll(extraFactors);
        }

        StringBuilder deepAnalysis = new StringBuilder(baseResp.getDeepAnalysis() == null
                ? ""
                : baseResp.getDeepAnalysis().trim());
        if (extraNotes != null && !extraNotes.isEmpty()) {
            if (deepAnalysis.length() > 0) {
                deepAnalysis.append("\n\n");
            }
            deepAnalysis.append(sourceLabel)
                    .append(" added ")
                    .append(extraScore)
                    .append(" points: ")
                    .append(String.join("; ", extraNotes));
        }

        PayRiskAssessAiResponse mergedResp = new PayRiskAssessAiResponse();
        mergedResp.setRiskScore(finalScore);
        mergedResp.setRiskLevel(finalRiskLevel);
        mergedResp.setDeepAnalysis(deepAnalysis.toString());
        mergedResp.setRiskFactors(new ArrayList<>(mergedFactors));
        return mergedResp;
    }

    private String resolveRiskLevel(int riskScore) {
        if (riskScore >= 85) {
            return "CRITICAL";
        }
        if (riskScore >= 65) {
            return "HIGH";
        }
        if (riskScore >= 35) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String maxRiskLevel(String left, String right) {
        return riskLevelWeight(left) >= riskLevelWeight(right) ? left : right;
    }

    private int riskLevelWeight(String riskLevel) {
        if ("CRITICAL".equalsIgnoreCase(riskLevel)) {
            return 4;
        }
        if ("HIGH".equalsIgnoreCase(riskLevel)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(riskLevel)) {
            return 2;
        }
        if ("LOW".equalsIgnoreCase(riskLevel)) {
            return 1;
        }
        return 0;
    }

    private String readText(JsonNode jsonNode, String fieldName) {
        if (jsonNode == null) {
            return null;
        }
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    private static class WhoisAssessBundle {
        private final PayRiskWhoisAnalyzer.WhoisRiskAssessment assessment;
        private final List<PayRiskWhoisAnalyzer.WhoisLookupResult> lookupResults;

        private WhoisAssessBundle(PayRiskWhoisAnalyzer.WhoisRiskAssessment assessment,
                                  List<PayRiskWhoisAnalyzer.WhoisLookupResult> lookupResults) {
            this.assessment = assessment;
            this.lookupResults = lookupResults;
        }

        private PayRiskWhoisAnalyzer.WhoisRiskAssessment getAssessment() {
            return assessment;
        }

        private List<PayRiskWhoisAnalyzer.WhoisLookupResult> getLookupResults() {
            return lookupResults;
        }
    }

    private String formatWhoisInfoForDisplay(JsonNode whoisInfoNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════════\n");
        sb.append("  🔍 域名 Whois 安全检测报告\n");
        sb.append("═══════════════════════════════════════");

        int extraScore = whoisInfoNode.path("extraScore").asInt(0);
        if (extraScore > 0) {
            sb.append("\n⚠️ 风险加分: +").append(extraScore).append(" 分");
        }

        JsonNode factors = whoisInfoNode.path("factors");
        if (factors.isArray() && factors.size() > 0) {
            sb.append("\n\n📋 风险因素:");
            for (JsonNode factor : factors) {
                sb.append("\n   • ").append(factor.asText());
            }
        }

        JsonNode records = whoisInfoNode.path("records");
        if (records.isArray() && records.size() > 0) {
            sb.append("\n\n📊 域名注册详情:");
            for (JsonNode record : records) {
                String domain = record.path("domain").asText("未知");
                sb.append("\n\n┌─────────────────────────────────────");
                sb.append("\n│ 🌐 域名: ").append(domain);

                JsonNode payload = record.path("payload");
                if (!payload.isNull() && !payload.isMissingNode()) {
                    extractWhoisDetails(sb, payload, domain);
                }

                sb.append("\n└─────────────────────────────────────");
            }
        }

        return sb.toString();
    }

    private void extractWhoisDetails(StringBuilder sb, JsonNode payload, String domain) {
        JsonNode whoisRecord = payload.path("WhoisRecord");
        if (whoisRecord.isMissingNode() || whoisRecord.isNull()) {
            sb.append("\n│ ⚠️ 未查询到 Whois 记录");
            return;
        }

        String createdDate = safeNodeText(whoisRecord.path("createdDateNormalized"),
                whoisRecord.path("createdDate"));
        if (createdDate != null && !createdDate.isEmpty()) {
            try {
                long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(createdDate.substring(0, 10)),
                        java.time.LocalDate.now());
                int years = (int)(daysOld / 365);
                sb.append("\n│ 📅 注册: ").append(createdDate.substring(0, 10));
                sb.append("\n│ ⏱️ 年龄: ");
                if (years >= 1) {
                    sb.append(years).append("年");
                    if (daysOld % 365 > 0) sb.append(daysOld % 365).append("天");
                    sb.append(" (成熟)");
                } else if (daysOld < 30) {
                    sb.append(daysOld).append("天 (新注册⚠️)");
                } else {
                    sb.append(daysOld).append("天 (年轻)");
                }
            } catch (Exception e) {
                sb.append("\n│ 📅 注册时间: ").append(createdDate);
            }
        }

        String registrarName = safeNodeText(whoisRecord.path("registrarName"));
        if (registrarName != null && !registrarName.isEmpty()) {
            sb.append("\n│ 🏢 注册商: ").append(registrarName);
        }

        JsonNode registrant = whoisRecord.path("registrant");
        if (!registrant.isMissingNode() && !registrant.isNull()) {
            String org = safeNodeText(registrant.path("organization"), registrant.path("name"));
            if (org != null && !org.isEmpty()) {
                String lowerOrg = org.toLowerCase();
                boolean isPrivacy = lowerOrg.contains("privacy") || lowerOrg.contains("proxy") ||
                        lowerOrg.contains("redacted") || lowerOrg.contains("whoisguard") ||
                        lowerOrg.contains("withheld");
                sb.append("\n│ ");
                if (isPrivacy) {
                    sb.append("🔒 注册人: 隐私保护服务");
                } else {
                    sb.append("👤 注册人: ").append(org);
                }
            }
        }

        StringBuilder nameServers = new StringBuilder();
        JsonNode ns = whoisRecord.path("nameServers");
        if (ns.has("hostNames") && ns.path("hostNames").isArray()) {
            for (JsonNode n : ns.path("hostNames")) {
                if (nameServers.length() > 0) nameServers.append(", ");
                nameServers.append(n.asText());
            }
        }
        if (nameServers.length() > 0) {
            sb.append("\n│ 🌐 DNS: ").append(nameServers);
        }

        JsonNode errorMessage = payload.path("ErrorMessage");
        if (!errorMessage.isMissingNode() && !errorMessage.isNull()) {
            sb.append("\n│ ❌ 错误: ").append(safeNodeText(errorMessage.path("msg")));
        }
    }

    private static String safeNodeText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                String text = node.asText();
                if (text != null && !text.trim().isEmpty() && !"null".equals(text.trim())) {
                    return text.trim();
                }
            }
        }
        return null;
    }
}
