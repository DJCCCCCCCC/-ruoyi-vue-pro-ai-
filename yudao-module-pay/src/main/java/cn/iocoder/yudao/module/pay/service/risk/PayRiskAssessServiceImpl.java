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
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskBehaviorAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskBehaviorMockDataGenerator;
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

        BehaviorAssessBundle behaviorAssessBundle = assessBehaviorRisk(paymentData);
        PayRiskLinkAnalyzer.LinkRiskAssessment linkRiskAssessment = PayRiskLinkAnalyzer.analyze(paymentData);
        WhoisAssessBundle whoisAssessBundle = assessWhoisRisk(paymentData);
        PayRiskWhoisAnalyzer.WhoisRiskAssessment whoisRiskAssessment = whoisAssessBundle.getAssessment();

        PayRiskAssessAiResponse mergedResp = mergeExternalRisk(aiResp,
                behaviorAssessBundle.getAssessment().getExtraScore(),
                behaviorAssessBundle.getAssessment().getFactors(),
                behaviorAssessBundle.getAssessment().getNotes(),
                "生物行为分析");
        mergedResp = mergeExternalRisk(mergedResp,
                linkRiskAssessment.getExtraScore(),
                linkRiskAssessment.getFactors(),
                linkRiskAssessment.getNotes(),
                "链接情报");
        mergedResp = mergeExternalRisk(mergedResp,
                whoisRiskAssessment.getExtraScore(),
                whoisRiskAssessment.getFactors(),
                whoisRiskAssessment.getNotes(),
                "Whois 情报");

        AppPayRiskAssessRespVO respVO = new AppPayRiskAssessRespVO();
        respVO.setRiskScore(mergedResp.getRiskScore());
        respVO.setRiskLevel(mergedResp.getRiskLevel());
        respVO.setDeepAnalysis(mergedResp.getDeepAnalysis());
        respVO.setRiskFactors(mergedResp.getRiskFactors());
        respVO.setIpInfo(ipInfoMaskedJsonNode);
        respVO.setBehaviorInfo(buildBehaviorInfo(behaviorAssessBundle));

        JsonNode whoisInfoNode = buildWhoisInfo(whoisAssessBundle);
        String whoisInfoStr = JsonUtils.toJsonString(whoisInfoNode);
        log.info("[assess] ====== WHOIS INFO 开始 ======");
        log.info("[assess] whoisInfoNode 类型: {}", whoisInfoNode != null ? whoisInfoNode.getClass().getSimpleName() : "null");
        log.info("[assess] whoisInfo JSON 字符串: {}", whoisInfoStr);
        log.info("[assess] ====== WHOIS INFO 结束 ======");

        respVO.setWhoisInfo(whoisInfoStr);

        appendBehaviorInfo(respVO, behaviorAssessBundle);

        if (whoisInfoNode != null && !whoisInfoNode.isNull() && whoisInfoNode.size() > 0) {
            String formattedWhois = formatWhoisInfoForDisplay(whoisInfoNode);
            String currentAnalysis = respVO.getDeepAnalysis() != null ? respVO.getDeepAnalysis() : "";
            respVO.setDeepAnalysis(currentAnalysis + "\n\n" + formattedWhois);

            Set<String> currentFactors = new LinkedHashSet<>();
            if (respVO.getRiskFactors() != null) {
                currentFactors.addAll(respVO.getRiskFactors());
            }
            currentFactors.add("已完成域名 Whois 安全检测");
            respVO.setRiskFactors(new ArrayList<>(currentFactors));

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
        result.put("behaviorInfo", respVO.getBehaviorInfo());

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

    private BehaviorAssessBundle assessBehaviorRisk(JsonNode paymentData) {
        PayRiskBehaviorMockDataGenerator.MockBehaviorProfile profile =
                PayRiskBehaviorMockDataGenerator.generateIfMissing(paymentData);
        PayRiskBehaviorAnalyzer.BehaviorRiskAssessment assessment =
                PayRiskBehaviorAnalyzer.analyze(profile.getBehaviorData());
        return new BehaviorAssessBundle(assessment, profile.getBehaviorData(), profile.isMocked(), profile.getSummary());
    }

    private JsonNode buildBehaviorInfo(BehaviorAssessBundle bundle) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        if (bundle == null || bundle.getAssessment() == null) {
            return root;
        }
        root.put("mocked", bundle.isMocked());
        root.put("summary", bundle.getSummary());
        root.put("extraScore", bundle.getAssessment().getExtraScore() == null ? 0 : bundle.getAssessment().getExtraScore());
        root.putPOJO("factors", bundle.getAssessment().getFactors());
        root.putPOJO("notes", bundle.getAssessment().getNotes());
        if (bundle.getBehaviorSnapshot() != null && !bundle.getBehaviorSnapshot().isNull()) {
            root.set("snapshot", bundle.getBehaviorSnapshot());
        }
        return root;
    }

    private void appendBehaviorInfo(AppPayRiskAssessRespVO respVO, BehaviorAssessBundle bundle) {
        JsonNode behaviorInfo = respVO.getBehaviorInfo();
        if (bundle == null || bundle.getAssessment() == null || behaviorInfo == null
                || behaviorInfo.isNull() || behaviorInfo.size() == 0) {
            return;
        }
        String formattedBehavior = formatBehaviorInfoForDisplay(behaviorInfo);
        if (formattedBehavior == null || formattedBehavior.trim().isEmpty()) {
            return;
        }
        String currentAnalysis = respVO.getDeepAnalysis() != null ? respVO.getDeepAnalysis() : "";
        respVO.setDeepAnalysis(currentAnalysis + "\n\n" + formattedBehavior);

        Set<String> mergedFactors = new LinkedHashSet<>();
        if (respVO.getRiskFactors() != null) {
            mergedFactors.addAll(respVO.getRiskFactors());
        }
        mergedFactors.add(bundle.isMocked() ? "已完成模拟生物行为分析" : "已完成生物行为分析");
        respVO.setRiskFactors(new ArrayList<>(mergedFactors));
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
                    .append(" 增加 ")
                    .append(extraScore)
                    .append(" 分：")
                    .append(String.join("；", extraNotes));
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

    private String formatBehaviorInfoForDisplay(JsonNode behaviorInfoNode) {
        StringBuilder sb = new StringBuilder();
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("  生物行为分析报告\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (behaviorInfoNode.path("mocked").asBoolean(false)) {
            sb.append("\n说明: 当前未接入真实生物行为采集，本次结果基于后端模拟行为画像生成。");
        } else {
            sb.append("\n说明: 当前结果来自请求中上传的真实生物行为信号。");
        }

        String summary = safeNodeText(behaviorInfoNode.path("summary"));
        if (summary != null) {
            sb.append("\n摘要: ").append(summary);
        }

        int extraScore = behaviorInfoNode.path("extraScore").asInt(0);
        sb.append("\n行为加分: +").append(extraScore).append(" 分");

        JsonNode factors = behaviorInfoNode.path("factors");
        if (factors.isArray() && factors.size() > 0) {
            sb.append("\n触发因素:");
            for (JsonNode factor : factors) {
                sb.append("\n - ").append(factor.asText());
            }
        }

        JsonNode snapshot = behaviorInfoNode.path("snapshot");
        if (snapshot.isObject()) {
            sb.append("\n关键指标:");
            appendMetricLine(sb, "操作速度", snapshot, "operationSpeed");
            appendMetricLine(sb, "敏感字段输入耗时(ms)", snapshot, "cardNumberInputDurationMs");
            appendMetricLine(sb, "按键平均间隔(ms)", snapshot, "averageKeyIntervalMs");
            appendMetricLine(sb, "按键波动标准差(ms)", snapshot, "keyIntervalStdMs");
            appendMetricLine(sb, "鼠标轨迹笔直度", snapshot, "mouseStraightness");
            appendMetricLine(sb, "指针跳点次数", snapshot, "pointerJumpCount");
            appendMetricLine(sb, "轨迹类型", snapshot, "mouseTrajectoryType");
            appendMetricLine(sb, "是否粘贴输入", snapshot, "pasteDetected");
            appendMetricLine(sb, "是否模拟器", snapshot, "emulatorDetected");
            appendMetricLine(sb, "是否脚本提示", snapshot, "scriptHint");
        }
        return sb.toString();
    }

    private void appendMetricLine(StringBuilder sb, String label, JsonNode snapshot, String fieldName) {
        JsonNode node = snapshot.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        sb.append("\n - ").append(label).append(": ").append(node.asText());
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

    private static class BehaviorAssessBundle {
        private final PayRiskBehaviorAnalyzer.BehaviorRiskAssessment assessment;
        private final JsonNode behaviorSnapshot;
        private final boolean mocked;
        private final String summary;

        private BehaviorAssessBundle(PayRiskBehaviorAnalyzer.BehaviorRiskAssessment assessment,
                                     JsonNode behaviorSnapshot,
                                     boolean mocked,
                                     String summary) {
            this.assessment = assessment;
            this.behaviorSnapshot = behaviorSnapshot;
            this.mocked = mocked;
            this.summary = summary;
        }

        private PayRiskBehaviorAnalyzer.BehaviorRiskAssessment getAssessment() {
            return assessment;
        }

        private JsonNode getBehaviorSnapshot() {
            return behaviorSnapshot;
        }

        private boolean isMocked() {
            return mocked;
        }

        private String getSummary() {
            return summary;
        }
    }
}
