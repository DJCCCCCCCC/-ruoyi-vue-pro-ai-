package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;

import javax.validation.Valid;

public interface PayRiskAssessService {

    /**
     * 支付风险评估
     *
     * @param reqVO 风险评估请求
     * @return 风险评估结果
     */
    AppPayRiskAssessRespVO assess(@Valid AppPayRiskAssessReqVO reqVO);

    /**
     * 支付风险评估记录分页
     *
     * @param pageReqVO 分页请求
     * @return 分页记录
     */
    PageResult<PayRiskAssessRecordDO> getRiskAssessRecordPage(PayRiskAssessRecordPageReqVO pageReqVO);

    /**
     * 鍒犻櫎鏀粯椋庨櫓璇勪及璁板綍
     *
     * @param id 缂栧彿
     */
    void deleteRiskAssessRecord(Long id);

    /**
     * 娓呯┖鏀粯椋庨櫓璇勪及璁板綍
     */
    void clearRiskAssessRecords();

}

