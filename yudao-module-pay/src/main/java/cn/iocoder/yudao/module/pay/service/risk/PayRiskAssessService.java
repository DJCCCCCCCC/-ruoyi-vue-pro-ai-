package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessReviewReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskSpeechTranscribeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermsRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.Map;

public interface PayRiskAssessService {

    AppPayRiskAssessRespVO assess(@Valid AppPayRiskAssessReqVO reqVO);

    Map<String, Object> assessAndReturnMap(@Valid AppPayRiskAssessReqVO reqVO);

    PageResult<PayRiskAssessRecordDO> getRiskAssessRecordPage(PayRiskAssessRecordPageReqVO pageReqVO);

    void deleteRiskAssessRecord(Long id);

    void clearRiskAssessRecords();

    /**
     * 人工复核闭环：对「待复核」记录提交放行/拦截/误报结案。
     */
    void reviewRiskAssessRecord(@Valid PayRiskAssessReviewReqVO reqVO);

    /**
     * 驾驶舱：今日在历史中首次出现的风险因子文案（基于 riskFactorsJson 与昨日及以前对比）。
     */
    PayRiskTodayNewTermsRespVO getTodayNewRiskTerms();

    /**
     * 驾驶舱穿透：指定今日新增风险词，返回关联评估工单及沟通过程汇总。
     */
    PayRiskTodayNewTermDetailRespVO getTodayNewRiskTermDetail(@Valid PayRiskTodayNewTermDetailReqVO reqVO);

    /**
     * 专项：仅对图片 data URL 做 OCR，并可选用 LLM 生成图中文字含义与风险解读（不跑完整支付风险评估）。
     */
    PayRiskImageOcrAnalyzeRespVO analyzeImageOcr(@Valid PayRiskImageOcrAnalyzeReqVO reqVO);

    /**
     * 语音转文字（Gitee GLM-ASR 等 OpenAI 兼容 /audio/transcriptions 接口）。
     */
    PayRiskSpeechTranscribeRespVO transcribeSpeech(MultipartFile file);

    /**
     * 事后报警协助：基于聊天记录与风控上下文，生成面向民警的结构化案情摘要与线索报告。
     */
    PayRiskPoliceReportRespVO generatePoliceReport(@Valid PayRiskPoliceReportReqVO reqVO);

}

