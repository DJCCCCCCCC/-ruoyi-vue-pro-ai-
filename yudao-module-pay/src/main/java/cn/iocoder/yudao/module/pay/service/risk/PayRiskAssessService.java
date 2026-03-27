package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;

import javax.validation.Valid;

public interface PayRiskAssessService {

    /**
     * 支付风险评估
     *
     * @param reqVO 风险评估请求
     * @return 风险评估结果
     */
    AppPayRiskAssessRespVO assess(@Valid AppPayRiskAssessReqVO reqVO);

}

