package cn.iocoder.yudao.module.pay.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessReviewReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskImageOcrAnalyzeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskPoliceReportRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskSpeechTranscribeRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermDetailRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTodayNewTermsRespVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.service.risk.PayRiskAssessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 支付风险评估")
@RestController
@RequestMapping("/pay/risk")
@Validated
@Slf4j
@TenantIgnore
public class AdminPayRiskAssessController {

    @Resource
    private PayRiskAssessService payRiskAssessService;

    @PostMapping("/assess")
    @Operation(summary = "支付风险评估")
    @PermitAll
    public CommonResult<AppPayRiskAssessRespVO> assess(@RequestBody AppPayRiskAssessReqVO reqVO) {
        return success(payRiskAssessService.assess(reqVO));
    }

    @PostMapping("/image-ocr-analyze")
    @Operation(summary = "图片 OCR 专项分析（可选 LLM 解读图中文字含义与风险点）")
    @PermitAll
    public CommonResult<PayRiskImageOcrAnalyzeRespVO> analyzeImageOcr(@Valid @RequestBody PayRiskImageOcrAnalyzeReqVO reqVO) {
        return success(payRiskAssessService.analyzeImageOcr(reqVO));
    }

    @PostMapping("/speech-transcribe")
    @Operation(summary = "语音转文字（Gitee GLM-ASR）")
    @PermitAll
    public CommonResult<PayRiskSpeechTranscribeRespVO> transcribeSpeech(
            @RequestParam("file") MultipartFile file) {
        return success(payRiskAssessService.transcribeSpeech(file));
    }

    @GetMapping("/page")
    @Operation(summary = "支付风险评估记录分页")
    @PermitAll
    public CommonResult<PageResult<PayRiskAssessRecordRespVO>> getRecordPage(@Valid PayRiskAssessRecordPageReqVO pageReqVO) {
        PageResult<PayRiskAssessRecordDO> pageResult = payRiskAssessService.getRiskAssessRecordPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PayRiskAssessRecordRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除支付风险评估记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<Boolean> deleteRecord(@RequestParam("id") Long id) {
        payRiskAssessService.deleteRiskAssessRecord(id);
        return success(true);
    }

    @PostMapping("/delete")
    @Operation(summary = "删除支付风险评估记录（POST 兼容）")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<Boolean> deleteRecordByPost(@RequestParam("id") Long id) {
        payRiskAssessService.deleteRiskAssessRecord(id);
        return success(true);
    }

    @DeleteMapping("/clear")
    @Operation(summary = "清空支付风险评估记录")
    @PermitAll
    public CommonResult<Boolean> clearRecords() {
        payRiskAssessService.clearRiskAssessRecords();
        return success(true);
    }

    @PostMapping("/clear")
    @Operation(summary = "清空支付风险评估记录（POST 兼容）")
    @PermitAll
    public CommonResult<Boolean> clearRecordsByPost() {
        payRiskAssessService.clearRiskAssessRecords();
        return success(true);
    }

    @GetMapping("/today-new-risk-terms")
    @Operation(summary = "驾驶舱 - 今日新增风险词（相对历史记录首次出现的风险因子）")
    @PermitAll
    public CommonResult<PayRiskTodayNewTermsRespVO> getTodayNewRiskTerms() {
        return success(payRiskAssessService.getTodayNewRiskTerms());
    }

    @PostMapping("/today-new-risk-term-detail")
    @Operation(summary = "驾驶舱 - 今日新增风险词穿透（关联评估工单与沟通过程汇总）")
    @PermitAll
    public CommonResult<PayRiskTodayNewTermDetailRespVO> getTodayNewRiskTermDetail(
            @Valid @RequestBody PayRiskTodayNewTermDetailReqVO reqVO) {
        return success(payRiskAssessService.getTodayNewRiskTermDetail(reqVO));
    }

    @PostMapping("/review")
    @Operation(summary = "支付风险评估人工复核（决策闭环）")
    @PermitAll
    public CommonResult<Boolean> reviewRecord(@Valid @RequestBody PayRiskAssessReviewReqVO reqVO) {
        payRiskAssessService.reviewRiskAssessRecord(reqVO);
        return success(true);
    }

    @PostMapping("/police-report")
    @Operation(summary = "事后报警协助（基于聊天记录生成面向民警的结构化案情摘要）")
    @PermitAll
    public CommonResult<PayRiskPoliceReportRespVO> generatePoliceReport(
            @Valid @RequestBody PayRiskPoliceReportReqVO reqVO) {
        return success(payRiskAssessService.generatePoliceReport(reqVO));
    }
}