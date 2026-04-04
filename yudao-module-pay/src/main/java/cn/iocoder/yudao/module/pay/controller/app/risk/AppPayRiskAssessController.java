package cn.iocoder.yudao.module.pay.controller.app.risk;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.service.risk.PayRiskAssessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;

import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 APP - 支付风险评估")
@RestController
@RequestMapping("/pay/risk")
@Validated
@Slf4j
@TenantIgnore
public class AppPayRiskAssessController {

    @Resource
    private PayRiskAssessService payRiskAssessService;

    @PostMapping("/assess")
    @Operation(summary = "支付风险评估")
    @PermitAll
    public CommonResult<Map<String, Object>> assess(@RequestBody AppPayRiskAssessReqVO reqVO) {
        log.info("========== 【2026-04-04-NEW-CODE】接口被调用 ==========");

        Map<String, Object> result = new HashMap<>();
        result.put("_test_marker_", "如果你看到这个字段，说明新代码已生效！！！");
        result.put("whoisInfo", "这是强制测试的 WHOIS 数据 - cursor.com 隐私保护");
        result.put("riskScore", 999);
        result.put("riskLevel", "TEST-MODE");
        result.put("deepAnalysis", "测试模式 - 验证 whoisInfo 字段是否能正常返回");
        result.put("riskFactors", new String[]{"测试因子1", "测试因子2"});
        result.put("ipInfo", "测试IP数据");

        log.info("[assess] 返回测试数据: {}", result.keySet());

        return success(result);
    }
}
