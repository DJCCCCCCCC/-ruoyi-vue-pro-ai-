package cn.iocoder.yudao.module.pay.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordRespVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.service.risk.PayRiskAssessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;

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

    @GetMapping("/page")
    @Operation(summary = "支付风险评估记录分页")
    @PermitAll
    public CommonResult<PageResult<PayRiskAssessRecordRespVO>> getRecordPage(@Valid PayRiskAssessRecordPageReqVO pageReqVO) {
        PageResult<PayRiskAssessRecordDO> pageResult = payRiskAssessService.getRiskAssessRecordPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PayRiskAssessRecordRespVO.class));
    }
}

