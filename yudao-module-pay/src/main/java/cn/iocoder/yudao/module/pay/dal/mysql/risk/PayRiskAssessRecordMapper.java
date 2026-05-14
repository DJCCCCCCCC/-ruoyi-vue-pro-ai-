package cn.iocoder.yudao.module.pay.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskAssessRecordPageReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mapper
public interface PayRiskAssessRecordMapper extends BaseMapperX<PayRiskAssessRecordDO> {

    default PageResult<PayRiskAssessRecordDO> selectPage(PayRiskAssessRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PayRiskAssessRecordDO>()
                .eqIfPresent(PayRiskAssessRecordDO::getId, reqVO.getId())
                .eqIfPresent(PayRiskAssessRecordDO::getRiskLevel, reqVO.getRiskLevel())
                .likeIfPresent(PayRiskAssessRecordDO::getScene, reqVO.getScene())
                .likeIfPresent(PayRiskAssessRecordDO::getSource, reqVO.getSource())
                .likeIfPresent(PayRiskAssessRecordDO::getIp, reqVO.getIp())
                .eqIfPresent(PayRiskAssessRecordDO::getReviewStatus, reqVO.getReviewStatus())
                .betweenIfPresent(PayRiskAssessRecordDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(PayRiskAssessRecordDO::getId));
    }

    /**
     * 物理删除单条记录（绕过逻辑删除）
     */
    @Delete("DELETE FROM pay_risk_assess_record WHERE id = #{id}")
    int deleteByIdPhysically(@Param("id") Long id);

    /**
     * 物理删除全部记录（绕过逻辑删除）
     */
    @Delete("DELETE FROM pay_risk_assess_record")
    int deleteAllPhysically();

    /**
     * 仅扫描今日之前记录的命中因子，用于判断「今日新增」风险词
     */
    default List<PayRiskAssessRecordDO> selectRiskFactorsJsonBefore(LocalDateTime deadlineExclusive) {
        return selectList(new LambdaQueryWrapperX<PayRiskAssessRecordDO>()
                .select(PayRiskAssessRecordDO::getRiskFactorsJson)
                .lt(PayRiskAssessRecordDO::getCreateTime, deadlineExclusive));
    }

    /**
     * 今日内的记录 id + 风险因子（轻量，用于「今日新增风险词」聚合）
     */
    default List<PayRiskAssessRecordDO> selectIdAndRiskFactorsBetween(LocalDateTime startInclusive,
                                                                       LocalDateTime endExclusive) {
        return selectList(new LambdaQueryWrapperX<PayRiskAssessRecordDO>()
                .select(PayRiskAssessRecordDO::getId, PayRiskAssessRecordDO::getRiskFactorsJson)
                .ge(PayRiskAssessRecordDO::getCreateTime, startInclusive)
                .lt(PayRiskAssessRecordDO::getCreateTime, endExclusive)
                .orderByDesc(PayRiskAssessRecordDO::getId));
    }

    default List<PayRiskAssessRecordDO> selectByIdsOrderByDesc(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<PayRiskAssessRecordDO>()
                .in(PayRiskAssessRecordDO::getId, ids)
                .orderByDesc(PayRiskAssessRecordDO::getId));
    }
}
