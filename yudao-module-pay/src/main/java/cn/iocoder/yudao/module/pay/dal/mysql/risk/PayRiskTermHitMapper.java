package cn.iocoder.yudao.module.pay.dal.mysql.risk;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermHitDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface PayRiskTermHitMapper extends BaseMapperX<PayRiskTermHitDO> {

    default PayRiskTermHitDO selectByTermIdAndRecordId(Long termId, Long recordId) {
        return selectOne(new LambdaQueryWrapperX<PayRiskTermHitDO>()
                .eq(PayRiskTermHitDO::getTermId, termId)
                .eq(PayRiskTermHitDO::getRecordId, recordId)
                .last("LIMIT 1"));
    }

    default List<Long> selectRecordIdsByTermId(Long termId) {
        List<PayRiskTermHitDO> rows = selectList(new LambdaQueryWrapperX<PayRiskTermHitDO>()
                .select(PayRiskTermHitDO::getRecordId)
                .eq(PayRiskTermHitDO::getTermId, termId)
                .orderByDesc(PayRiskTermHitDO::getId));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(PayRiskTermHitDO::getRecordId).collect(Collectors.toList());
    }

    default List<Long> selectRecordIdsByTermIdBetween(Long termId, LocalDateTime startInclusive,
                                                      LocalDateTime endExclusive) {
        List<PayRiskTermHitDO> rows = selectList(new LambdaQueryWrapperX<PayRiskTermHitDO>()
                .select(PayRiskTermHitDO::getRecordId)
                .eq(PayRiskTermHitDO::getTermId, termId)
                .ge(PayRiskTermHitDO::getCreateTime, startInclusive)
                .lt(PayRiskTermHitDO::getCreateTime, endExclusive)
                .orderByDesc(PayRiskTermHitDO::getId));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(PayRiskTermHitDO::getRecordId).collect(Collectors.toList());
    }

    default int countByTermIdBetween(Long termId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        Long count = selectCount(new LambdaQueryWrapperX<PayRiskTermHitDO>()
                .eq(PayRiskTermHitDO::getTermId, termId)
                .ge(PayRiskTermHitDO::getCreateTime, startInclusive)
                .lt(PayRiskTermHitDO::getCreateTime, endExclusive));
        return count == null ? 0 : count.intValue();
    }
}
