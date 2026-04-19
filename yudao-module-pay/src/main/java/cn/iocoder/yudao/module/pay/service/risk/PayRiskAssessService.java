package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessReqVO;
import cn.iocoder.yudao.module.pay.controller.app.risk.vo.AppPayRiskAssessRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessReviewReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;

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

}

