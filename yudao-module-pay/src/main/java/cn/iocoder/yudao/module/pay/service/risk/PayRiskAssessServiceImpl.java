package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessReviewReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskSpeechTranscribeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermRelatedTicketVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermItemVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermsRespVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermDO;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskAssessRecordMapper;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.client.DeepSeekClient;
import cn.iocoder.yudao.module.pay.service.risk.client.DifyAgentReflectionClient;
import cn.iocoder.yudao.module.pay.service.risk.client.IpInfoClient;
import cn.iocoder.yudao.module.pay.service.risk.client.PayRiskGiteeAsrClient;
import cn.iocoder.yudao.module.pay.service.risk.client.PayRiskGiteeOcrClient;
import cn.iocoder.yudao.module.pay.service.risk.client.WhoisXmlApiClient;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAdvancedAnalysis;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAgentReflection;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAssessAiResponse;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskDecisionResult;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskImageOcrEnrichOutcome;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskCaseSimilarityAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskChatTermExtractor;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskLlmAnalysisReport;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskPoliceReport;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskBehaviorAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskAdvancedAnalysisBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskBehaviorMockDataGenerator;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskDesensitizer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskLinkAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskLlmReportFallbackBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskAgentReflectionPromptBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskPoliceReportFallbackBuilder;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskPaymentImageOcrEnricher;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskRelationTopologyAnalyzer;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskWhoisAnalyzer;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@Slf4j
public class PayRiskAssessServiceImpl implements PayRiskAssessService {

    /**
     * 并行执行首次 LLM、Whois、本地规则分析等 IO/CPU 任务，避免串行累加延迟。
     */
    private final ExecutorService riskAssessExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r);
        t.setName("pay-risk-assess-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    /** 二次 LLM 上下文中的 deepAnalysis 上限，控制 token 与耗时 */
    private static final int LLM_CONTEXT_DEEP_ANALYSIS_MAX_CHARS = 6000;

    /** 图片 OCR 专项 LLM 解读：合并正文输入上限 */
    private static final int IMAGE_OCR_LLM_INPUT_MAX_CHARS = 12000;

    private static final long DEFAULT_IP_INFO_CACHE_TTL_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final long DEFAULT_WHOIS_CACHE_TTL_MILLIS = TimeUnit.DAYS.toMillis(30);
    private static final int DEFAULT_RISK_ASSESS_CACHE_MAX_SIZE = 2048;

    private final ConcurrentHashMap<String, CacheEntry<JsonNode>> ipInfoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<JsonNode>> whoisCache = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdownRiskAssessExecutor() {
        riskAssessExecutor.shutdown();
    }

    @Resource
    private IpInfoClient ipInfoClient;

    @Resource
    private DeepSeekClient deepSeekClient;

    @Resource
    private DifyAgentReflectionClient difyAgentReflectionClient;

    @Value("${yudao.pay.risk-assess.agent-reflection.provider:deepseek}")
    private String agentReflectionProvider;

    @Value("${yudao.pay.risk-assess.agent-reflection.fallback-to-deepseek:true}")
    private boolean agentReflectionFallbackToDeepSeek;

    @Value("${yudao.pay.risk-assess.agent-reflection.async:true}")
    private boolean agentReflectionAsync;

    @Value("${yudao.pay.risk-assess.ipinfo.cache-enabled:true}")
    private boolean ipInfoCacheEnabled;

    @Value("${yudao.pay.risk-assess.ipinfo.cache-ttl-millis:259200000}")
    private long ipInfoCacheTtlMillis;

    @Value("${yudao.pay.risk-assess.whoisxml.cache-enabled:true}")
    private boolean whoisCacheEnabled;

    @Value("${yudao.pay.risk-assess.whoisxml.cache-ttl-millis:2592000000}")
    private long whoisCacheTtlMillis;

    @Value("${yudao.pay.risk-assess.cache-max-size:2048}")
    private int riskAssessCacheMaxSize;

    @Resource
    private PayRiskGiteeOcrClient payRiskGiteeOcrClient;

    @Resource
    private PayRiskGiteeAsrClient payRiskGiteeAsrClient;

    @Value("${yudao.pay.risk-assess.asr.max-file-bytes:10485760}")
    private long asrMaxFileBytes;

    @Value("${yudao.pay.risk-assess.ocr.max-payload-chars:15000000}")
    private long ocrMaxPayloadChars;

    @Value("${yudao.pay.risk-assess.ocr.max-images-per-request:5}")
    private int ocrMaxImagesPerRequest;

    @Value("${yudao.pay.risk-assess.ocr.strip-image-data-after-ocr:true}")
    private boolean ocrStripImageDataAfterOcr;

    @Resource
    private WhoisXmlApiClient whoisXmlApiClient;

    @Resource
    private PayRiskAssessRecordMapper payRiskAssessRecordMapper;

    @Resource
    private PayRiskTermService payRiskTermService;

    @Resource
    private PayRiskDecisionEngine payRiskDecisionEngine;

    @Override
    public AppPayRiskAssessRespVO assess(@Valid AppPayRiskAssessReqVO reqVO) {
        PayRiskImageOcrEnrichOutcome ocrOutcome = PayRiskPaymentImageOcrEnricher.enrichWithOutcome(
                reqVO.getPaymentData(),
                payRiskGiteeOcrClient,
                ocrMaxPayloadChars,
                ocrMaxImagesPerRequest,
                ocrStripImageDataAfterOcr);
        JsonNode paymentData = ocrOutcome.getPaymentData();

        String ip = reqVO.getIp();
        if (ip == null || ip.trim().isEmpty()) {
            ip = PayRiskDesensitizer.extractFirstIp(paymentData);
        }
        if (ip == null || ip.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IP_MISSING);
        }

        JsonNode ipInfo = fetchIpInfoWithCache(ip);
        JsonNode paymentMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(paymentData);
        JsonNode ipInfoMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(ipInfo);

        String paymentMaskedJson = JsonUtils.toJsonString(paymentMaskedJsonNode);
        String ipInfoMaskedJson = JsonUtils.toJsonString(ipInfoMaskedJsonNode);

        CompletableFuture<PayRiskAssessAiResponse> aiFuture = CompletableFuture.supplyAsync(
                () -> deepSeekClient.assess(paymentMaskedJson, ipInfoMaskedJson), riskAssessExecutor);
        CompletableFuture<BehaviorAssessBundle> behaviorFuture = CompletableFuture.supplyAsync(
                () -> assessBehaviorRisk(paymentData), riskAssessExecutor);
        CompletableFuture<PayRiskLinkAnalyzer.LinkRiskAssessment> linkFuture = CompletableFuture.supplyAsync(
                () -> PayRiskLinkAnalyzer.analyze(paymentData), riskAssessExecutor);
        CompletableFuture<PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment> topoFuture =
                CompletableFuture.supplyAsync(
                        () -> PayRiskRelationTopologyAnalyzer.analyze(paymentData), riskAssessExecutor);
        CompletableFuture<WhoisAssessBundle> whoisFuture = CompletableFuture.supplyAsync(
                () -> assessWhoisRisk(paymentData), riskAssessExecutor);
        CompletableFuture<PayRiskCaseSimilarityAnalyzer.CaseSimilarityResult> caseFuture =
                CompletableFuture.supplyAsync(
                        () -> PayRiskCaseSimilarityAnalyzer.analyze(paymentData, ipInfoMaskedJsonNode, null,
                                payRiskAssessRecordMapper), riskAssessExecutor);

        CompletableFuture.allOf(aiFuture, behaviorFuture, linkFuture, topoFuture, whoisFuture, caseFuture).join();

        PayRiskAssessAiResponse aiResp = joinCf(aiFuture);
        BehaviorAssessBundle behaviorAssessBundle = joinCf(behaviorFuture);
        PayRiskLinkAnalyzer.LinkRiskAssessment linkRiskAssessment = joinCf(linkFuture);
        PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment topologyRiskAssessment = joinCf(topoFuture);
        WhoisAssessBundle whoisAssessBundle = joinCf(whoisFuture);
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
                topologyRiskAssessment.getExtraScore(),
                topologyRiskAssessment.getFactors(),
                topologyRiskAssessment.getNotes(),
                "payment relation topology");
        mergedResp = mergeExternalRisk(mergedResp,
                whoisRiskAssessment.getExtraScore(),
                whoisRiskAssessment.getFactors(),
                whoisRiskAssessment.getNotes(),
                "Whois 情报");

        PayRiskCaseSimilarityAnalyzer.CaseSimilarityResult caseSimilarityResult = joinCf(caseFuture);
        mergedResp = mergeExternalRisk(mergedResp,
                caseSimilarityResult.getBonusScore(),
                caseSimilarityResult.getRiskFactors(),
                caseSimilarityResult.getNotes(),
                "历史案例相似性");

        AppPayRiskAssessRespVO respVO = new AppPayRiskAssessRespVO();
        respVO.setRiskScore(mergedResp.getRiskScore());
        respVO.setRiskLevel(mergedResp.getRiskLevel());
        respVO.setDeepAnalysis(mergedResp.getDeepAnalysis());
        respVO.setRiskFactors(mergedResp.getRiskFactors());
        applyImageOcrToResponse(respVO, ocrOutcome);
        respVO.setCaseSimilarityBonus(caseSimilarityResult.getBonusScore());
        respVO.setIpInfo(ipInfoMaskedJsonNode);
        respVO.setBehaviorInfo(buildBehaviorInfo(behaviorAssessBundle));
        respVO.setTopologyInfo(topologyRiskAssessment.getTopology());

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

        respVO.setLlmReport(buildLlmAnalysisReport(paymentMaskedJsonNode, ipInfoMaskedJsonNode, respVO, whoisInfoNode,
                caseSimilarityResult));
        respVO.setAdvancedAnalysis(buildAdvancedAnalysis(paymentData, ipInfoMaskedJsonNode, whoisInfoNode, respVO));
        if (respVO.getAdvancedAnalysis() != null) {
            respVO.getAdvancedAnalysis().setCaseMatches(caseSimilarityResult.getMatches());
        }
        if (!agentReflectionAsync) {
            respVO.setAgentReflection(buildAgentReflection(paymentMaskedJsonNode, ipInfoMaskedJsonNode, whoisInfoNode, respVO));
            applyAgentReflectionToResponse(respVO);
        }

        PayRiskDecisionResult decision = payRiskDecisionEngine.decide(respVO.getRiskScore(), respVO.getRiskLevel(),
                respVO.getCaseSimilarityBonus(), respVO.getLlmReport());
        respVO.setDecision(decision);

        PayRiskAssessRecordDO record = saveAssessRecord(reqVO, ip, ipInfoMaskedJsonNode, respVO);
        if (agentReflectionAsync && record != null && record.getId() != null) {
            submitAgentReflectionAsync(record.getId(), paymentMaskedJsonNode, ipInfoMaskedJsonNode, whoisInfoNode, respVO);
        }
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
        result.put("topologyInfo", respVO.getTopologyInfo());
        result.put("llmReport", respVO.getLlmReport());
        result.put("advancedAnalysis", respVO.getAdvancedAnalysis());
        result.put("agentReflection", respVO.getAgentReflection());
        result.put("caseSimilarityBonus", respVO.getCaseSimilarityBonus());
        result.put("caseSimilarityMatches", respVO.getAdvancedAnalysis() == null ? null : respVO.getAdvancedAnalysis().getCaseMatches());
        result.put("decision", respVO.getDecision());
        result.put("embeddedImageCount", respVO.getEmbeddedImageCount());
        result.put("imageOcrServiceEnabled", respVO.getImageOcrServiceEnabled());
        result.put("imageOcrApiCallCount", respVO.getImageOcrApiCallCount());
        result.put("imageOcrValidTextCount", respVO.getImageOcrValidTextCount());
        result.put("imageOcrSummary", respVO.getImageOcrSummary());
        result.put("imageOcrTextPreview", respVO.getImageOcrTextPreview());

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

    @Override
    public void reviewRiskAssessRecord(PayRiskAssessReviewReqVO reqVO) {
        PayRiskAssessRecordDO record = payRiskAssessRecordMapper.selectById(reqVO.getId());
        if (record == null) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_RECORD_NOT_FOUND);
        }
        if (!PayRiskReviewStatusConstants.PENDING.equals(record.getReviewStatus())) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_REVIEW_STATUS_INVALID);
        }
        String action = reqVO.getReviewAction() == null ? "" : reqVO.getReviewAction().trim().toUpperCase();
        String newStatus;
        switch (action) {
            case "PASS":
                newStatus = PayRiskReviewStatusConstants.RESOLVED_PASS;
                break;
            case "BLOCK":
                newStatus = PayRiskReviewStatusConstants.RESOLVED_BLOCK;
                break;
            case "DISMISS":
                newStatus = PayRiskReviewStatusConstants.DISMISSED;
                break;
            default:
                throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_REVIEW_ACTION_INVALID);
        }
        record.setReviewStatus(newStatus);
        record.setReviewRemark(reqVO.getRemark());
        record.setReviewTime(LocalDateTime.now());
        String reviewer = SecurityFrameworkUtils.getLoginUserNickname();
        if (StrUtil.isEmpty(reviewer)) {
            Long uid = SecurityFrameworkUtils.getLoginUserId();
            reviewer = uid != null ? String.valueOf(uid) : "anonymous";
        }
        record.setReviewer(reviewer);
        payRiskAssessRecordMapper.updateById(record);
    }

    /**
     * 将图片 OCR 统计与说明写入响应：独立字段 + deepAnalysis 前置说明 + 命中因子补充。
     */
    private void applyImageOcrToResponse(AppPayRiskAssessRespVO respVO, PayRiskImageOcrEnrichOutcome ocrOutcome) {
        if (ocrOutcome == null || ocrOutcome.getEmbeddedImageCount() <= 0) {
            return;
        }
        respVO.setEmbeddedImageCount(ocrOutcome.getEmbeddedImageCount());
        respVO.setImageOcrServiceEnabled(ocrOutcome.isOcrServiceEnabled());
        respVO.setImageOcrApiCallCount(ocrOutcome.getOcrApiCallCount());
        respVO.setImageOcrValidTextCount(ocrOutcome.getOcrValidTextCount());
        respVO.setImageOcrSummary(ocrOutcome.getImageOcrSummary());
        respVO.setImageOcrTextPreview(ocrOutcome.getImageOcrTextPreview());

        if (StrUtil.isNotBlank(ocrOutcome.getImageOcrSummary())) {
            String body = StrUtil.nullToDefault(respVO.getDeepAnalysis(), "").trim();
            respVO.setDeepAnalysis(ocrOutcome.getImageOcrSummary() + (body.isEmpty() ? "" : "\n\n" + body));
        }

        LinkedHashSet<String> factors = new LinkedHashSet<>();
        if (respVO.getRiskFactors() != null) {
            factors.addAll(respVO.getRiskFactors());
        }
        if (ocrOutcome.isOcrServiceEnabled() && ocrOutcome.getOcrValidTextCount() > 0) {
            factors.add("图片 OCR：已识别 " + ocrOutcome.getOcrValidTextCount()
                    + " 段文字并参与研判（见 imageOcrSummary / imageOcrTextPreview）");
        } else if (ocrOutcome.isOcrServiceEnabled()) {
            factors.add("图片 OCR：已调用但未得到有效文字");
        } else {
            factors.add("检测到内嵌图片，服务端 OCR 未开启，未对图中文字做专项识别");
        }
        respVO.setRiskFactors(new ArrayList<>(factors));
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
            JsonNode payload = lookupWhoisWithCache(domain);
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

    private PayRiskLlmAnalysisReport buildLlmAnalysisReport(JsonNode paymentMaskedJsonNode,
                                                            JsonNode ipInfoMaskedJsonNode,
                                                            AppPayRiskAssessRespVO respVO,
                                                            JsonNode whoisInfoNode,
                                                            PayRiskCaseSimilarityAnalyzer.CaseSimilarityResult caseSimilarityResult) {
        JsonNode contextNode = buildLlmContext(paymentMaskedJsonNode, ipInfoMaskedJsonNode, respVO, whoisInfoNode,
                caseSimilarityResult);
        try {
            PayRiskLlmAnalysisReport report = deepSeekClient.generateRiskReport(JsonUtils.toJsonString(contextNode));
            if (report == null) {
                log.warn("[buildLlmAnalysisReport] LLM 报告为空或解析失败，使用 FALLBACK 报告");
                return PayRiskLlmReportFallbackBuilder.build(contextNode);
            }
            if (report.getMode() == null || report.getMode().trim().isEmpty()) {
                report.setMode("LLM");
            }
            return report;
        } catch (Exception ex) {
            log.warn("[buildLlmAnalysisReport] LLM 综合研判失败，使用兜底报告：{}", ex.getMessage());
            return PayRiskLlmReportFallbackBuilder.build(contextNode);
        }
    }

    private JsonNode buildLlmContext(JsonNode paymentMaskedJsonNode,
                                     JsonNode ipInfoMaskedJsonNode,
                                     AppPayRiskAssessRespVO respVO,
                                     JsonNode whoisInfoNode,
                                     PayRiskCaseSimilarityAnalyzer.CaseSimilarityResult caseSimilarityResult) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();

        ObjectNode assessmentNode = root.putObject("assessment");
        assessmentNode.put("riskScore", respVO.getRiskScore() == null ? 0 : respVO.getRiskScore());
        assessmentNode.put("riskLevel", respVO.getRiskLevel() == null ? "LOW" : respVO.getRiskLevel());
        assessmentNode.put("deepAnalysis", truncateForPrompt(respVO.getDeepAnalysis(), LLM_CONTEXT_DEEP_ANALYSIS_MAX_CHARS));
        assessmentNode.putPOJO("riskFactors", respVO.getRiskFactors() == null ? new ArrayList<>() : respVO.getRiskFactors());

        if (paymentMaskedJsonNode != null && !paymentMaskedJsonNode.isNull()) {
            root.set("paymentData", paymentMaskedJsonNode);
            JsonNode userProfile = paymentMaskedJsonNode.path("userProfile");
            if (userProfile != null && !userProfile.isNull() && userProfile.size() > 0) {
                root.set("userProfile", userProfile);
            }
        }
        if (ipInfoMaskedJsonNode != null && !ipInfoMaskedJsonNode.isNull()) {
            root.set("ipInfo", ipInfoMaskedJsonNode);
        }
        if (respVO.getBehaviorInfo() != null && !respVO.getBehaviorInfo().isNull()) {
            root.set("behavior", respVO.getBehaviorInfo());
        }
        if (respVO.getTopologyInfo() != null) {
            root.set("topology", JsonUtils.parseTree(JsonUtils.toJsonString(respVO.getTopologyInfo())));
        }
        if (whoisInfoNode != null && !whoisInfoNode.isNull()) {
            root.set("whois", slimWhoisForLlm(whoisInfoNode));
        }
        ArrayNode similarCases = buildHistoricalSimilarCasesForLlm(caseSimilarityResult);
        if (similarCases.size() > 0) {
            root.set("historicalSimilarCases", similarCases);
        }
        return root;
    }

    /**
     * 将少量历史相似案例摘要注入 LLM 上下文，用于对照「已知族系 / 变体 / 相对新型」判断（检索增强，非模型权重训练）。
     */
    private ArrayNode buildHistoricalSimilarCasesForLlm(PayRiskCaseSimilarityAnalyzer.CaseSimilarityResult caseSimilarityResult) {
        ArrayNode arr = JsonUtils.getObjectMapper().createArrayNode();
        if (caseSimilarityResult == null || caseSimilarityResult.getMatches() == null) {
            return arr;
        }
        int added = 0;
        for (PayRiskAdvancedAnalysis.CaseSimilarityMatch match : caseSimilarityResult.getMatches()) {
            if (added >= 3) {
                break;
            }
            if (match == null || match.getRecordId() == null) {
                continue;
            }
            PayRiskAssessRecordDO past = payRiskAssessRecordMapper.selectById(match.getRecordId());
            if (past == null) {
                continue;
            }
            ObjectNode item = arr.addObject();
            item.put("recordId", past.getId());
            item.put("similarity", match.getSimilarity() == null ? 0d : match.getSimilarity());
            item.put("riskLevel", past.getRiskLevel() == null ? "" : past.getRiskLevel());
            item.put("riskScore", past.getRiskScore() == null ? 0 : past.getRiskScore());
            item.put("scene", past.getScene() == null ? "" : past.getScene());
            item.put("matchedReasons", truncateForPrompt(match.getMatchedReasons(), 240));
            JsonNode pastLlm = parseJsonSafe(past.getLlmReportJson());
            if (pastLlm != null && !pastLlm.isNull()) {
                item.put("pastFraudFamily", truncateForPrompt(textOrEmpty(pastLlm, "fraudFamily"), 120));
                item.put("pastVariantLabel", truncateForPrompt(textOrEmpty(pastLlm, "variantLabel"), 120));
                item.put("pastNoveltyLevel", truncateForPrompt(textOrEmpty(pastLlm, "noveltyLevel"), 64));
                item.put("pastSummary", truncateForPrompt(textOrEmpty(pastLlm, "summary"), 280));
                item.put("pastVerdict", truncateForPrompt(textOrEmpty(pastLlm, "verdict"), 280));
            } else {
                item.put("pastSummary", truncateForPrompt(past.getDeepAnalysis(), 280));
            }
            added++;
        }
        return arr;
    }

    private static JsonNode parseJsonSafe(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JsonUtils.parseTree(json);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return "";
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("");
    }

    private static String truncateForPrompt(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen) + "…";
    }

    /**
     * 去掉 Whois 原始 payload，仅保留评分因子与域名级摘要，显著缩小二次 LLM 请求体。
     */
    private static JsonNode slimWhoisForLlm(JsonNode whois) {
        if (whois == null || whois.isNull()) {
            return whois;
        }
        ObjectNode out = JsonUtils.getObjectMapper().createObjectNode();
        out.put("extraScore", whois.path("extraScore").asInt(0));
        JsonNode factors = whois.path("factors");
        if (!factors.isMissingNode() && !factors.isNull()) {
            out.set("factors", factors);
        }
        JsonNode notes = whois.path("notes");
        if (!notes.isMissingNode() && !notes.isNull()) {
            out.set("notes", notes);
        }
        ArrayNode slimRecords = out.putArray("records");
        JsonNode records = whois.path("records");
        if (records.isArray()) {
            int n = 0;
            for (JsonNode rec : records) {
                if (n++ >= 8) {
                    break;
                }
                ObjectNode item = slimRecords.addObject();
                item.put("domain", rec.path("domain").asText(""));
                JsonNode payload = rec.path("payload");
                String registrar = "";
                if (!payload.isMissingNode() && !payload.isNull()) {
                    JsonNode whoisRecord = payload.path("WhoisRecord");
                    registrar = whoisRecord.path("registrarName").asText("");
                    if (registrar.isEmpty()) {
                        registrar = whoisRecord.path("registryData").path("registrarName").asText("");
                    }
                }
                item.put("registrarName", truncateForPrompt(registrar, 200));
            }
        }
        out.put("detailLevel", "summary_only");
        return out;
    }

    private static <T> T joinCf(CompletableFuture<T> cf) {
        try {
            return cf.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private PayRiskAdvancedAnalysis buildAdvancedAnalysis(JsonNode paymentData,
                                                          JsonNode ipInfoMaskedJsonNode,
                                                          JsonNode whoisInfoNode,
                                                          AppPayRiskAssessRespVO respVO) {
        return PayRiskAdvancedAnalysisBuilder.build(paymentData, ipInfoMaskedJsonNode, whoisInfoNode, respVO);
    }

    private PayRiskAgentReflection buildAgentReflection(JsonNode paymentMaskedJsonNode,
                                                        JsonNode ipInfoMaskedJsonNode,
                                                        JsonNode whoisInfoNode,
                                                        AppPayRiskAssessRespVO respVO) {
        JsonNode contextNode = PayRiskAgentReflectionPromptBuilder.buildContext(paymentMaskedJsonNode,
                ipInfoMaskedJsonNode, respVO.getRiskScore(), respVO.getRiskLevel(), respVO.getDeepAnalysis(),
                respVO.getRiskFactors(), respVO.getBehaviorInfo(), respVO.getTopologyInfo(), whoisInfoNode,
                respVO.getLlmReport(), respVO.getAdvancedAnalysis());
        String contextJson = JsonUtils.toJsonString(contextNode);
        try {
            if ("dify".equalsIgnoreCase(StrUtil.nullToDefault(agentReflectionProvider, ""))) {
                try {
                    PayRiskAgentReflection reflection = difyAgentReflectionClient.generateAgentReflection(contextJson);
                    if (reflection != null && StrUtil.isBlank(reflection.getVersion())) {
                        reflection.setVersion("agent-reflection-v1-dify");
                    }
                    return reflection;
                } catch (Exception difyEx) {
                    if (!agentReflectionFallbackToDeepSeek) {
                        throw difyEx;
                    }
                    log.warn("[buildAgentReflection] Dify 反思流失败，回退 DeepSeek 三 Agent：{}", difyEx.getMessage());
                }
            }
            return buildDeepSeekAgentReflection(contextJson);
        } catch (Exception ex) {
            log.warn("[buildAgentReflection] Agentic 反思流生成失败，跳过反思结果但保留主评估结果：{}", ex.getMessage(), ex);
            return null;
        }
    }

    private PayRiskAgentReflection buildDeepSeekAgentReflection(String contextJson) {
        PayRiskAgentReflection reflection = new PayRiskAgentReflection();
        reflection.setVersion("agent-reflection-v1-deepseek");
        PayRiskAgentReflection.AssessorOpinion assessor = deepSeekClient.generateAssessorOpinion(contextJson);
        reflection.setAssessor(assessor);
        PayRiskAgentReflection.SkepticOpinion skeptic = deepSeekClient.generateSkepticOpinion(contextJson, assessor);
        reflection.setSkeptic(skeptic);
        reflection.setArbiter(deepSeekClient.generateArbiterOpinion(contextJson, assessor, skeptic));
        return reflection;
    }

    private void submitAgentReflectionAsync(Long recordId,
                                            JsonNode paymentMaskedJsonNode,
                                            JsonNode ipInfoMaskedJsonNode,
                                            JsonNode whoisInfoNode,
                                            AppPayRiskAssessRespVO baseRespVO) {
        AppPayRiskAssessRespVO snapshot = JsonUtils.parseObject(JsonUtils.toJsonString(baseRespVO), AppPayRiskAssessRespVO.class);
        CompletableFuture.runAsync(() -> {
            try {
                PayRiskAgentReflection reflection = buildAgentReflection(paymentMaskedJsonNode, ipInfoMaskedJsonNode, whoisInfoNode, snapshot);
                snapshot.setAgentReflection(reflection);
                applyAgentReflectionToResponse(snapshot);
                PayRiskDecisionResult decision = payRiskDecisionEngine.decide(snapshot.getRiskScore(), snapshot.getRiskLevel(),
                        snapshot.getCaseSimilarityBonus(), snapshot.getLlmReport());
                snapshot.setDecision(decision);

                PayRiskAssessRecordDO update = new PayRiskAssessRecordDO();
                update.setId(recordId);
                update.setRiskScore(snapshot.getRiskScore());
                update.setRiskLevel(snapshot.getRiskLevel());
                update.setDeepAnalysis(snapshot.getDeepAnalysis());
                update.setRiskFactorsJson(JsonUtils.toJsonString(snapshot.getRiskFactors()));
                update.setAgentReflectionJson(JsonUtils.toJsonString(reflection));
                if (decision != null) {
                    update.setDecisionAction(decision.getRecommendedAction());
                    update.setDecisionJson(JsonUtils.toJsonString(decision));
                    update.setReviewStatus(decision.isRequiresHumanReview()
                            ? PayRiskReviewStatusConstants.PENDING
                            : PayRiskReviewStatusConstants.NOT_REQUIRED);
                }
                payRiskAssessRecordMapper.updateById(update);
                log.info("[submitAgentReflectionAsync] Agentic 反思流已异步回写，recordId={}", recordId);
            } catch (Exception ex) {
                log.warn("[submitAgentReflectionAsync] Agentic 反思流异步回写失败，recordId={}, error={}",
                        recordId, ex.getMessage(), ex);
            }
        }, riskAssessExecutor);
    }

    private void applyAgentReflectionToResponse(AppPayRiskAssessRespVO respVO) {
        PayRiskAgentReflection reflection = respVO.getAgentReflection();
        if (reflection == null || reflection.getArbiter() == null) {
            return;
        }
        PayRiskAgentReflection.ArbiterOpinion arbiter = reflection.getArbiter();
        if (arbiter.getFinalScore() != null) {
            respVO.setRiskScore(Math.min(100, Math.max(0, arbiter.getFinalScore())));
        }
        if (StrUtil.isNotBlank(arbiter.getFinalRiskLevel())) {
            respVO.setRiskLevel(arbiter.getFinalRiskLevel());
        }
        if (StrUtil.isNotBlank(arbiter.getSummary())) {
            String currentAnalysis = StrUtil.nullToDefault(respVO.getDeepAnalysis(), "").trim();
            respVO.setDeepAnalysis(currentAnalysis + (currentAnalysis.isEmpty() ? "" : "\n\n")
                    + "Agentic 反思流仲裁结论：" + arbiter.getSummary());
        }
        LinkedHashSet<String> factors = new LinkedHashSet<>();
        if (respVO.getRiskFactors() != null) {
            factors.addAll(respVO.getRiskFactors());
        }
        factors.add("已完成 Agentic 反思流：判定Agent → 质疑Agent → 仲裁Agent");
        if (Boolean.TRUE.equals(arbiter.getNeedManualReview())) {
            factors.add("仲裁Agent建议人工复核：存在争议点或证据不确定性");
        }
        respVO.setRiskFactors(new ArrayList<>(factors));
    }

    private PayRiskAssessRecordDO saveAssessRecord(AppPayRiskAssessReqVO reqVO, String ip, JsonNode ipInfoMaskedJsonNode,
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
        record.setWhoisInfoJson(respVO.getWhoisInfo());
        record.setBehaviorInfoJson(JsonUtils.toJsonString(respVO.getBehaviorInfo()));
        record.setTopologyInfoJson(JsonUtils.toJsonString(respVO.getTopologyInfo()));
        record.setLlmReportJson(JsonUtils.toJsonString(respVO.getLlmReport()));
        record.setAdvancedAnalysisJson(JsonUtils.toJsonString(respVO.getAdvancedAnalysis()));
        record.setAgentReflectionJson(JsonUtils.toJsonString(respVO.getAgentReflection()));
        PayRiskDecisionResult decision = respVO.getDecision();
        if (decision != null) {
            record.setDecisionAction(decision.getRecommendedAction());
            record.setDecisionJson(JsonUtils.toJsonString(decision));
            record.setReviewStatus(decision.isRequiresHumanReview()
                    ? PayRiskReviewStatusConstants.PENDING
                    : PayRiskReviewStatusConstants.NOT_REQUIRED);
        } else {
            record.setReviewStatus(PayRiskReviewStatusConstants.NOT_REQUIRED);
        }
        try {
            payRiskAssessRecordMapper.insert(record);
            if (record.getId() != null) {
                payRiskTermService.syncChatTermsFromAssess(record.getPaymentDataJson(), record.getId());
            }
            return record;
        } catch (Exception ex) {
            log.warn("[saveAssessRecord] 风险分析结果保存失败，将跳过落库但继续返回分析结果。scene={}, source={}, error={}",
                    record.getScene(), record.getSource(), ex.getMessage(), ex);
            return null;
        }
    }

    private JsonNode fetchIpInfoWithCache(String ip) {
        if (!ipInfoCacheEnabled) {
            return ipInfoClient.fetchIpInfo(ip);
        }
        String cacheKey = ip == null ? "" : ip.trim();
        return getOrLoadJsonCache(ipInfoCache, cacheKey, ipInfoCacheTtlMillis,
                () -> ipInfoClient.fetchIpInfo(ip), "ipinfo");
    }

    private JsonNode lookupWhoisWithCache(String domain) {
        if (!whoisCacheEnabled) {
            return whoisXmlApiClient.lookupDomain(domain);
        }
        String cacheKey = domain == null ? "" : domain.trim().toLowerCase();
        return getOrLoadJsonCache(whoisCache, cacheKey, whoisCacheTtlMillis,
                () -> whoisXmlApiClient.lookupDomain(domain), "whois");
    }

    private JsonNode getOrLoadJsonCache(ConcurrentHashMap<String, CacheEntry<JsonNode>> cache,
                                        String key,
                                        long ttlMillis,
                                        JsonNodeSupplier loader,
                                        String cacheName) {
        if (StrUtil.isBlank(key) || ttlMillis <= 0) {
            return loader.get();
        }
        long now = System.currentTimeMillis();
        CacheEntry<JsonNode> cached = cache.get(key);
        if (cached != null && cached.expireAtMillis > now) {
            log.debug("[getOrLoadJsonCache] {} cache hit, key={}", cacheName, key);
            return cached.value;
        }
        JsonNode value = loader.get();
        trimCacheIfNecessary(cache);
        cache.put(key, new CacheEntry<>(value, now + ttlMillis));
        return value;
    }

    private void trimCacheIfNecessary(ConcurrentHashMap<String, CacheEntry<JsonNode>> cache) {
        int maxSize = riskAssessCacheMaxSize <= 0 ? DEFAULT_RISK_ASSESS_CACHE_MAX_SIZE : riskAssessCacheMaxSize;
        if (cache.size() < maxSize) {
            return;
        }
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expireAtMillis <= now);
        if (cache.size() >= maxSize) {
            cache.clear();
        }
    }

    private interface JsonNodeSupplier {
        JsonNode get();
    }

    private static final class CacheEntry<T> {
        private final T value;
        private final long expireAtMillis;

        private CacheEntry(T value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }
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

    private static final int MAX_RISK_TERM_LEN = 256;
    private static final int MAX_CONV_MSG_LINES = 50;
    private static final int MAX_MSG_CONTENT_LEN = 240;

    @Override
    public PayRiskTodayNewTermsRespVO getTodayNewRiskTerms() {
        LocalDateTime[] range = resolveLocalTodayRange();
        Map<String, PayRiskTodayNewTermItemVO> itemMap = new LinkedHashMap<>();

        payRiskTermService.listTodayNewTerms(range[0], range[1]).forEach(termRow -> {
            PayRiskTodayNewTermItemVO vo = new PayRiskTodayNewTermItemVO();
            vo.setTerm(termRow.getTerm());
            List<Long> recordIds = payRiskTermService.listTodayHitRecordIds(termRow.getId(), range[0], range[1]);
            int todayHits = payRiskTermService.countTodayHits(termRow.getId(), range[0], range[1]);
            vo.setTodayHitCount(todayHits > 0 ? todayHits : Math.max(recordIds.size(), 1));
            vo.setRelatedRecordIds(recordIds);
            vo.setTermId(termRow.getId());
            vo.setSourceType(termRow.getSourceType());
            vo.setHitCount(termRow.getHitCount());
            itemMap.put(termRow.getTerm(), vo);
        });

        // 兜底：直接从评估记录 paymentData 聊天记录回算「今日新增」。避免词库同步失败或时间字段异常导致驾驶舱为空。
        Map<String, LinkedHashSet<Long>> recordComputed = computeTodayNewTermToRecordIds(range[0], range[1]);
        mergeComputedTodayNewTerms(itemMap, recordComputed);

        // 再兜底：如果本地自然日没有命中，按最近 24 小时回算，规避数据库/JVM/容器时区不一致导致 create_time 不在“本地今天”的问题。
        if (itemMap.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            Map<String, LinkedHashSet<Long>> rollingComputed = computeTodayNewTermToRecordIds(now.minusHours(24), now.plusMinutes(1));
            mergeComputedTodayNewTerms(itemMap, rollingComputed);
        }

        // 最后兜底：展示最近评估记录中的聊天话术。这样即使「新增」判定被历史记录抵消，也不会让驾驶舱空白。
        if (itemMap.isEmpty()) {
            mergeComputedTodayNewTerms(itemMap, computeRecentRiskTermToRecordIds());
        }

        PayRiskTodayNewTermsRespVO resp = new PayRiskTodayNewTermsRespVO();
        List<PayRiskTodayNewTermItemVO> items = itemMap.values().stream()
                .sorted(Comparator.comparing(PayRiskTodayNewTermItemVO::getTodayHitCount,
                                Comparator.nullsLast(Integer::compareTo)).reversed()
                        .thenComparing(PayRiskTodayNewTermItemVO::getTerm, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        resp.setTerms(items);
        return resp;
    }

    @Override
    public PayRiskTodayNewTermDetailRespVO getTodayNewRiskTermDetail(PayRiskTodayNewTermDetailReqVO reqVO) {
        String term = normalizeRiskTermInput(reqVO.getTerm());
        if (StrUtil.isEmpty(term) || term.length() > MAX_RISK_TERM_LEN) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_TERM_PARAM_INVALID);
        }
        LocalDateTime[] range = resolveLocalTodayRange();
        PayRiskTermDO termRow = payRiskTermService.getTermByText(term);
        List<Long> matchedIds = new ArrayList<>();
        if (termRow != null && termRow.getFirstSeenTime() != null
                && !termRow.getFirstSeenTime().isBefore(range[0])
                && termRow.getFirstSeenTime().isBefore(range[1])) {
            matchedIds.addAll(payRiskTermService.listTodayHitRecordIds(termRow.getId(), range[0], range[1]));
            if (matchedIds.isEmpty() && termRow.getFirstRecordId() != null) {
                matchedIds.add(termRow.getFirstRecordId());
            }
        }
        LinkedHashSet<Long> recordComputedIds = computeTodayNewTermToRecordIds(range[0], range[1]).get(term);
        if (recordComputedIds != null) {
            matchedIds.addAll(recordComputedIds);
        }
        matchedIds = matchedIds.stream().distinct().collect(Collectors.toList());
        if (matchedIds.isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_TERM_NOT_TODAY_NEW);
        }
        List<PayRiskAssessRecordDO> records = payRiskAssessRecordMapper.selectByIdsOrderByDesc(matchedIds);
        List<PayRiskTermRelatedTicketVO> tickets = new ArrayList<>();
        for (PayRiskAssessRecordDO record : records) {
            PayRiskTermRelatedTicketVO t = new PayRiskTermRelatedTicketVO();
            t.setId(record.getId());
            t.setScene(record.getScene());
            t.setSource(record.getSource());
            t.setCreateTime(record.getCreateTime());
            t.setRiskLevel(record.getRiskLevel());
            t.setReviewStatus(record.getReviewStatus());
            t.setDecisionAction(record.getDecisionAction());
            t.setConversationSummary(buildConversationSummaryForPaymentData(record.getPaymentDataJson()));
            tickets.add(t);
        }
        PayRiskTodayNewTermDetailRespVO resp = new PayRiskTodayNewTermDetailRespVO();
        resp.setTerm(term);
        resp.setTickets(tickets);
        return resp;
    }

    private LocalDateTime[] resolveLocalTodayRange() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        return new LocalDateTime[]{dayStart, dayStart.plusDays(1)};
    }

    private void mergeComputedTodayNewTerms(Map<String, PayRiskTodayNewTermItemVO> itemMap,
                                            Map<String, LinkedHashSet<Long>> computed) {
        computed.forEach((term, recordIds) -> {
            PayRiskTodayNewTermItemVO vo = itemMap.computeIfAbsent(term, k -> {
                PayRiskTodayNewTermItemVO created = new PayRiskTodayNewTermItemVO();
                created.setTerm(k);
                created.setSourceType(PayRiskTermConstants.SOURCE_AUTO);
                created.setHitCount((long) recordIds.size());
                return created;
            });
            LinkedHashSet<Long> mergedIds = new LinkedHashSet<>();
            if (vo.getRelatedRecordIds() != null) {
                mergedIds.addAll(vo.getRelatedRecordIds());
            }
            mergedIds.addAll(recordIds);
            vo.setRelatedRecordIds(new ArrayList<>(mergedIds));
            vo.setTodayHitCount(Math.max(vo.getTodayHitCount() == null ? 0 : vo.getTodayHitCount(), mergedIds.size()));
        });
    }

    private Map<String, LinkedHashSet<Long>> computeTodayNewTermToRecordIds(LocalDateTime dayStartInclusive,
                                                                            LocalDateTime dayEndExclusive) {
        Set<String> historical = new HashSet<>();
        for (PayRiskAssessRecordDO row : payRiskAssessRecordMapper.selectPaymentDataJsonBefore(dayStartInclusive)) {
            historical.addAll(extractChatTermsFromPaymentData(row.getPaymentDataJson()));
        }
        Map<String, LinkedHashSet<Long>> termToIds = new LinkedHashMap<>();
        for (PayRiskAssessRecordDO row : payRiskAssessRecordMapper.selectIdAndPaymentDataBetween(dayStartInclusive, dayEndExclusive)) {
            Long id = row.getId();
            if (id == null) {
                continue;
            }
            for (String term : extractChatTermsFromPaymentData(row.getPaymentDataJson())) {
                if (!historical.contains(term)) {
                    termToIds.computeIfAbsent(term, k -> new LinkedHashSet<>()).add(id);
                }
            }
        }
        return termToIds;
    }

    private Map<String, LinkedHashSet<Long>> computeRecentRiskTermToRecordIds() {
        Map<String, LinkedHashSet<Long>> termToIds = new LinkedHashMap<>();
        for (PayRiskAssessRecordDO row : payRiskAssessRecordMapper.selectRecentPaymentData(10)) {
            Long id = row.getId();
            if (id == null) {
                continue;
            }
            for (String term : extractChatTermsFromPaymentData(row.getPaymentDataJson())) {
                termToIds.computeIfAbsent(term, k -> new LinkedHashSet<>()).add(id);
            }
        }
        return termToIds;
    }

    private static String normalizeRiskTermInput(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static List<String> extractChatTermsFromPaymentData(String paymentDataJson) {
        return PayRiskChatTermExtractor.extractTerms(paymentDataJson);
    }

    private static String buildConversationSummaryForPaymentData(String paymentDataJson) {
        if (StrUtil.isBlank(paymentDataJson)) {
            return "";
        }
        try {
            JsonNode root = JsonUtils.parseTree(paymentDataJson);
            JsonNode messages = root.path("messages");
            StringBuilder sb = new StringBuilder();
            if (messages.isArray() && messages.size() > 0) {
                int n = 0;
                for (JsonNode m : messages) {
                    if (n++ >= MAX_CONV_MSG_LINES) {
                        sb.append("…（对话较长，已截断展示）");
                        break;
                    }
                    String name = m.path("senderName").asText("");
                    String role = m.path("role").asText("");
                    String sender = StrUtil.blankToDefault(name, role);
                    String content = m.path("content").asText("");
                    if (content.length() > MAX_MSG_CONTENT_LEN) {
                        content = content.substring(0, MAX_MSG_CONTENT_LEN) + "…";
                    }
                    sb.append("【").append(sender).append("】").append(content).append('\n');
                }
                return sb.toString().trim();
            }
            String latest = root.path("latestPeerMessage").asText("");
            if (StrUtil.isNotBlank(latest)) {
                return "（无结构化对话，仅最新对方摘要）\n【对方】" + latest;
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    @Override
    public PayRiskImageOcrAnalyzeRespVO analyzeImageOcr(@Valid PayRiskImageOcrAnalyzeReqVO reqVO) {
        List<String> urls = reqVO.getImageDataUrls().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .limit(ocrMaxImagesPerRequest)
                .collect(Collectors.toList());
        if (urls.isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_IMAGE_ANALYZE_NO_VALID_DATA_URL);
        }
        com.fasterxml.jackson.databind.node.ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode arr = JsonUtils.getObjectMapper().createArrayNode();
        for (String u : urls) {
            arr.add(u);
        }
        root.set("imageDataUrls", arr);
        if (PayRiskPaymentImageOcrEnricher.countImageDataUrls(root) == 0) {
            throw exception(ErrorCodeConstants.PAY_RISK_IMAGE_ANALYZE_NO_VALID_DATA_URL);
        }
        PayRiskImageOcrEnrichOutcome outcome = PayRiskPaymentImageOcrEnricher.enrichWithOutcome(
                root, payRiskGiteeOcrClient, ocrMaxPayloadChars, ocrMaxImagesPerRequest, true);
        PayRiskImageOcrAnalyzeRespVO resp = new PayRiskImageOcrAnalyzeRespVO();
        resp.setEmbeddedImageCount(outcome.getEmbeddedImageCount());
        resp.setOcrServiceEnabled(outcome.isOcrServiceEnabled());
        resp.setOcrApiCallCount(outcome.getOcrApiCallCount());
        resp.setOcrValidTextCount(outcome.getOcrValidTextCount());
        resp.setImageOcrSummary(outcome.getImageOcrSummary());
        resp.setImageOcrTextPreview(outcome.getImageOcrTextPreview());
        JsonNode pd = outcome.getPaymentData();
        List<String> texts = new ArrayList<>();
        if (pd != null && pd.has("multimodalImageOcrTexts") && pd.get("multimodalImageOcrTexts").isArray()) {
            for (JsonNode n : pd.get("multimodalImageOcrTexts")) {
                texts.add(n.asText(""));
            }
        }
        resp.setMultimodalImageOcrTexts(texts);
        String merged = null;
        if (pd != null && pd.has("multimodalImageOcrMerged")) {
            JsonNode m = pd.get("multimodalImageOcrMerged");
            if (m != null && m.isTextual()) {
                String t = m.asText("");
                merged = t.isEmpty() ? null : t;
            }
        }
        resp.setMultimodalImageOcrMerged(merged);
        boolean wantLlm = reqVO.getIncludeLlmInsight() == null || Boolean.TRUE.equals(reqVO.getIncludeLlmInsight());
        if (wantLlm && StrUtil.isNotBlank(merged)) {
            String snippet = merged.length() > IMAGE_OCR_LLM_INPUT_MAX_CHARS
                    ? merged.substring(0, IMAGE_OCR_LLM_INPUT_MAX_CHARS) + "…（已截断，用于模型输入）"
                    : merged;
            try {
                resp.setLlmImageContentNarrative(deepSeekClient.analyzeImageOcrNarrative(snippet));
            } catch (Exception ex) {
                log.warn("[analyzeImageOcr] LLM 解读失败，已跳过：{}", ex.getMessage());
                resp.setLlmImageContentNarrative(null);
            }
        }
        return resp;
    }

    @Override
    public PayRiskSpeechTranscribeRespVO transcribeSpeech(MultipartFile file) {
        if (!payRiskGiteeAsrClient.isEnabled()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASR_NOT_ENABLED);
        }
        if (file == null || file.isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASR_FILE_EMPTY);
        }
        if (file.getSize() > asrMaxFileBytes) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASR_FILE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASR_FILE_EMPTY);
        }
        String filename = file.getOriginalFilename();
        String text = payRiskGiteeAsrClient.transcribe(bytes, filename);
        if (StrUtil.isBlank(text)) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASR_CALL_FAILED);
        }
        PayRiskSpeechTranscribeRespVO resp = new PayRiskSpeechTranscribeRespVO();
        resp.setText(text);
        resp.setModel(payRiskGiteeAsrClient.getModel());
        return resp;
    }

    @Override
    public PayRiskPoliceReportRespVO generatePoliceReport(@Valid PayRiskPoliceReportReqVO reqVO) {
        PayRiskImageOcrEnrichOutcome ocrOutcome = PayRiskPaymentImageOcrEnricher.enrichWithOutcome(
                reqVO.getPaymentData(),
                payRiskGiteeOcrClient,
                ocrMaxPayloadChars,
                ocrMaxImagesPerRequest,
                ocrStripImageDataAfterOcr);
        JsonNode paymentData = ocrOutcome.getPaymentData();

        String ip = reqVO.getIp();
        if (ip == null || ip.trim().isEmpty()) {
            ip = PayRiskDesensitizer.extractFirstIp(paymentData);
        }
        if (ip == null || ip.trim().isEmpty()) {
            throw exception(ErrorCodeConstants.PAY_RISK_ASSESS_IP_MISSING);
        }

        JsonNode ipInfo = fetchIpInfoWithCache(ip);
        JsonNode paymentMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(paymentData);
        JsonNode ipInfoMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(ipInfo);

        CompletableFuture<PayRiskLinkAnalyzer.LinkRiskAssessment> linkFuture = CompletableFuture.supplyAsync(
                () -> PayRiskLinkAnalyzer.analyze(paymentData), riskAssessExecutor);
        CompletableFuture<PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment> topoFuture =
                CompletableFuture.supplyAsync(
                        () -> PayRiskRelationTopologyAnalyzer.analyze(paymentData), riskAssessExecutor);
        CompletableFuture.allOf(linkFuture, topoFuture).join();

        PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment topologyRiskAssessment = joinCf(topoFuture);
        joinCf(linkFuture);

        ObjectNode contextNode = buildPoliceReportContext(reqVO, paymentMaskedJsonNode, ipInfoMaskedJsonNode,
                topologyRiskAssessment, ocrOutcome);

        PayRiskPoliceReport report;
        try {
            report = deepSeekClient.generatePoliceReport(JsonUtils.toJsonString(contextNode));
            if (report == null) {
                log.warn("[generatePoliceReport] LLM 报告为空或解析失败，使用 FALLBACK 报告");
                report = PayRiskPoliceReportFallbackBuilder.build(contextNode, topologyRiskAssessment.getTopology());
            } else if (report.getMode() == null || report.getMode().trim().isEmpty()) {
                report.setMode("LLM");
            }
        } catch (Exception ex) {
            log.warn("[generatePoliceReport] LLM 报警协助报告失败，使用兜底报告：{}", ex.getMessage());
            report = PayRiskPoliceReportFallbackBuilder.build(contextNode, topologyRiskAssessment.getTopology());
        }

        PayRiskPoliceReportRespVO respVO = new PayRiskPoliceReportRespVO();
        respVO.setReport(report);
        respVO.setTopologyInfo(topologyRiskAssessment.getTopology());
        respVO.setGeneratedAt(LocalDateTime.now().toString());
        return respVO;
    }

    private ObjectNode buildPoliceReportContext(PayRiskPoliceReportReqVO reqVO,
                                                JsonNode paymentMaskedJsonNode,
                                                JsonNode ipInfoMaskedJsonNode,
                                                PayRiskRelationTopologyAnalyzer.TopologyRiskAssessment topologyRiskAssessment,
                                                PayRiskImageOcrEnrichOutcome ocrOutcome) {
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        if (paymentMaskedJsonNode != null && !paymentMaskedJsonNode.isNull()) {
            root.set("paymentData", paymentMaskedJsonNode);
        }
        if (ipInfoMaskedJsonNode != null && !ipInfoMaskedJsonNode.isNull()) {
            root.set("ipInfo", ipInfoMaskedJsonNode);
        }
        if (topologyRiskAssessment != null && topologyRiskAssessment.getTopology() != null) {
            root.set("topology", JsonUtils.parseTree(JsonUtils.toJsonString(topologyRiskAssessment.getTopology())));
        }
        if (reqVO.getPriorAssessSnapshot() != null && !reqVO.getPriorAssessSnapshot().isNull()) {
            root.set("priorAssessSnapshot", reqVO.getPriorAssessSnapshot());
        }
        if (reqVO.getConfirmedTransferred() != null) {
            root.put("confirmedTransferred", reqVO.getConfirmedTransferred());
        }
        if (StrUtil.isNotBlank(reqVO.getAdditionalVictimNotes())) {
            root.put("additionalVictimNotes", reqVO.getAdditionalVictimNotes().trim());
        }
        if (ocrOutcome != null && ocrOutcome.getEmbeddedImageCount() > 0) {
            root.put("embeddedImageCount", ocrOutcome.getEmbeddedImageCount());
            if (StrUtil.isNotBlank(ocrOutcome.getImageOcrSummary())) {
                root.put("imageOcrSummary", ocrOutcome.getImageOcrSummary());
            }
            if (StrUtil.isNotBlank(ocrOutcome.getImageOcrTextPreview())) {
                root.put("imageOcrTextPreview", truncateForPrompt(ocrOutcome.getImageOcrTextPreview(), 4000));
            }
        }
        root.put("reportPurpose", "POST_FRAUD_POLICE_ASSIST");
        return root;
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
