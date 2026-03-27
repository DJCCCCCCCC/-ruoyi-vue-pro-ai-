package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.client.DeepSeekClient;
import cn.iocoder.yudao.module.pay.service.risk.client.IpInfoClient;
import cn.iocoder.yudao.module.pay.service.risk.model.PayRiskAssessAiResponse;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskDesensitizer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@Slf4j
public class PayRiskAssessServiceImpl implements PayRiskAssessService {

    @Resource
    private IpInfoClient ipInfoClient;

    @Resource
    private DeepSeekClient deepSeekClient;

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

        // 1）ipinfo 获取 IP 信息（用于后续让 AI 判断风险）
        JsonNode ipInfo = ipInfoClient.fetchIpInfo(ip);

        // 2）脱敏：对支付 JSON + ipinfo JSON 做递归脱敏，避免把敏感数据发给 AI
        JsonNode paymentMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(paymentData);
        JsonNode ipInfoMaskedJsonNode = PayRiskDesensitizer.desensitizeForPrompt(ipInfo);

        String paymentMaskedJson = JsonUtils.toJsonString(paymentMaskedJsonNode);
        String ipInfoMaskedJson = JsonUtils.toJsonString(ipInfoMaskedJsonNode);

        // 3）调用 DeepSeek 做风控评估，并解析出结构化 JSON
        PayRiskAssessAiResponse aiResp = deepSeekClient.assess(paymentMaskedJson, ipInfoMaskedJson);

        // 4）返回给前端
        AppPayRiskAssessRespVO respVO = new AppPayRiskAssessRespVO();
        respVO.setRiskScore(aiResp.getRiskScore());
        respVO.setRiskLevel(aiResp.getRiskLevel());
        respVO.setDeepAnalysis(aiResp.getDeepAnalysis());
        respVO.setRiskFactors(aiResp.getRiskFactors());
        respVO.setIpInfo(ipInfoMaskedJsonNode);
        return respVO;
    }
}

