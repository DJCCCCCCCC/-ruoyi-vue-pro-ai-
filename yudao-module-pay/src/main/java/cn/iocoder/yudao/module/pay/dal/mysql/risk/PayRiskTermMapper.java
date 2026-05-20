package cn.iocoder.yudao.module.pay.dal.mysql.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermPageReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PayRiskTermMapper extends BaseMapperX<PayRiskTermDO> {

    default PageResult<PayRiskTermDO> selectPage(PayRiskTermPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PayRiskTermDO>()
                .likeIfPresent(PayRiskTermDO::getTerm, reqVO.getTerm())
                .eqIfPresent(PayRiskTermDO::getCategory, reqVO.getCategory())
                .eqIfPresent(PayRiskTermDO::getStatus, reqVO.getStatus())
                .eqIfPresent(PayRiskTermDO::getSourceType, reqVO.getSourceType())
                .betweenIfPresent(PayRiskTermDO::getFirstSeenTime, reqVO.getFirstSeenTime())
                .orderByDesc(PayRiskTermDO::getId));
    }

    default PayRiskTermDO selectByTerm(String term) {
        return selectOne(new LambdaQueryWrapperX<PayRiskTermDO>()
                .eq(PayRiskTermDO::getTerm, term)
                .last("LIMIT 1"));
    }

    default List<PayRiskTermDO> selectFirstSeenBetween(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return selectList(new LambdaQueryWrapperX<PayRiskTermDO>()
                .ge(PayRiskTermDO::getFirstSeenTime, startInclusive)
                .lt(PayRiskTermDO::getFirstSeenTime, endExclusive)
                .orderByDesc(PayRiskTermDO::getHitCount)
                .orderByAsc(PayRiskTermDO::getTerm));
    }
}
