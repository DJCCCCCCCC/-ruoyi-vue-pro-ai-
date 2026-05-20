package cn.iocoder.yudao.module.pay.controller.admin.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermRespVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermSaveReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermDO;
import cn.iocoder.yudao.module.pay.service.risk.PayRiskTermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 支付风险词库")
@RestController
@RequestMapping("/pay/risk/term")
@Validated
@TenantIgnore
public class AdminPayRiskTermController {

    @Resource
    private PayRiskTermService payRiskTermService;

    @PostMapping("/create")
    @Operation(summary = "创建风险词")
    @PermitAll
    public CommonResult<Long> createTerm(@Valid @RequestBody PayRiskTermSaveReqVO reqVO) {
        return success(payRiskTermService.createTerm(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新风险词")
    @PermitAll
    public CommonResult<Boolean> updateTerm(@Valid @RequestBody PayRiskTermSaveReqVO reqVO) {
        payRiskTermService.updateTerm(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除风险词")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<Boolean> deleteTerm(@RequestParam("id") Long id) {
        payRiskTermService.deleteTerm(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得风险词")
    @Parameter(name = "id", description = "编号", required = true)
    @PermitAll
    public CommonResult<PayRiskTermRespVO> getTerm(@RequestParam("id") Long id) {
        PayRiskTermDO row = payRiskTermService.getTerm(id);
        return success(BeanUtils.toBean(row, PayRiskTermRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "风险词分页")
    @PermitAll
    public CommonResult<PageResult<PayRiskTermRespVO>> getTermPage(@Valid PayRiskTermPageReqVO pageReqVO) {
        PageResult<PayRiskTermDO> page = payRiskTermService.getTermPage(pageReqVO);
        return success(BeanUtils.toBean(page, PayRiskTermRespVO.class));
    }
}
