package cn.iocoder.yudao.module.pay.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayRiskAssessRecordMapper extends BaseMapperX<PayRiskAssessRecordDO> {

    default PageResult<PayRiskAssessRecordDO> selectPage(PayRiskAssessRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PayRiskAssessRecordDO>()
                .eqIfPresent(PayRiskAssessRecordDO::getRiskLevel, reqVO.getRiskLevel())
                .likeIfPresent(PayRiskAssessRecordDO::getScene, reqVO.getScene())
                .likeIfPresent(PayRiskAssessRecordDO::getSource, reqVO.getSource())
                .likeIfPresent(PayRiskAssessRecordDO::getIp, reqVO.getIp())
                .betweenIfPresent(PayRiskAssessRecordDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(PayRiskAssessRecordDO::getId));
    }
}

