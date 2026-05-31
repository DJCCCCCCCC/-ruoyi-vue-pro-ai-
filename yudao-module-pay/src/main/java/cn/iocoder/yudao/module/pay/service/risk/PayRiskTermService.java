package cn.iocoder.yudao.module.pay.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermSaveReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermDO;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

public interface PayRiskTermService {

    Long createTerm(@Valid PayRiskTermSaveReqVO reqVO);

    void updateTerm(@Valid PayRiskTermSaveReqVO reqVO);

    void deleteTerm(Long id);

    PayRiskTermDO getTerm(Long id);

    PageResult<PayRiskTermDO> getTermPage(PayRiskTermPageReqVO pageReqVO);

    /**
     * 评估落库后从聊天记录提取话术并同步至词库（自动建档 + 命中关联）。
     */
    void syncChatTermsFromAssess(String paymentDataJson, Long recordId);

    /**
     * 今日首次进入词库的风险词（first_seen_time 在今日）。
     */
    List<PayRiskTermDO> listTodayNewTerms(LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive);

    PayRiskTermDO getTermByText(String term);

    int countTodayHits(Long termId, LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive);

    List<Long> listTodayHitRecordIds(Long termId, LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive);
}
