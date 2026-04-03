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
import java.util.LinkedHashSet;
import java.util.List;
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
        respVO.setWhoisInfo(buildWhoisInfo(whoisAssessBundle));

        saveAssessRecord(reqVO, ip, ipInfoMaskedJsonNode, respVO);
        return respVO;
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
        if (domains.isEmpty()) {
            return new WhoisAssessBundle(PayRiskWhoisAnalyzer.WhoisRiskAssessment.empty(), new ArrayList<>());
        }

        List<PayRiskWhoisAnalyzer.WhoisLookupResult> lookupResults = new ArrayList<>();
        for (String domain : domains) {
            lookupResults.add(new PayRiskWhoisAnalyzer.WhoisLookupResult(domain, whoisXmlApiClient.lookupDomain(domain)));
        }
        return new WhoisAssessBundle(PayRiskWhoisAnalyzer.analyze(lookupResults), lookupResults);
    }

    private JsonNode buildWhoisInfo(WhoisAssessBundle bundle) {
        ObjectNode root = JsonUtils.parseObject("{}", ObjectNode.class);
        if (bundle == null || bundle.getAssessment() == null) {
            return root;
        }
        root.put("extraScore", bundle.getAssessment().getExtraScore() == null ? 0 : bundle.getAssessment().getExtraScore());
        root.putPOJO("factors", bundle.getAssessment().getFactors());
        root.putPOJO("notes", bundle.getAssessment().getNotes());

        ArrayNode records = root.putArray("records");
        if (bundle.getLookupResults() != null) {
            for (PayRiskWhoisAnalyzer.WhoisLookupResult lookupResult : bundle.getLookupResults()) {
                ObjectNode item = records.addObject();
                item.put("domain", lookupResult.getDomain());
                item.set("payload", lookupResult.getPayload());
            }
        }
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
}
