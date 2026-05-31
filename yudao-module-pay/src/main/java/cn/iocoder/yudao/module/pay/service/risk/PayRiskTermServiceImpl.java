package cn.iocoder.yudao.module.pay.service.risk;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermPageReqVO;
import cn.iocoder.yudao.module.pay.controller.admin.risk.vo.PayRiskTermSaveReqVO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskAssessRecordDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.risk.PayRiskTermHitDO;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskAssessRecordMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskTermHitMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.risk.PayRiskTermMapper;
import cn.iocoder.yudao.module.pay.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.pay.service.risk.util.PayRiskChatTermExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@Slf4j
public class PayRiskTermServiceImpl implements PayRiskTermService {

    @Resource
    private PayRiskTermMapper payRiskTermMapper;

    @Resource
    private PayRiskTermHitMapper payRiskTermHitMapper;

    @Resource
    private PayRiskAssessRecordMapper payRiskAssessRecordMapper;

    @Override
    public Long createTerm(@Valid PayRiskTermSaveReqVO reqVO) {
        String term = normalizeTerm(reqVO.getTerm());
        validateTermText(term);
        if (payRiskTermMapper.selectByTerm(term) != null) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        PayRiskTermDO row = new PayRiskTermDO();
        row.setTerm(term);
        row.setCategory(normalizeCategory(reqVO.getCategory()));
        row.setStatus(reqVO.getStatus());
        row.setDescription(StrUtil.trim(reqVO.getDescription()));
        row.setSourceType(PayRiskTermConstants.SOURCE_MANUAL);
        row.setHitCount(0L);
        row.setFirstSeenTime(now);
        row.setLastHitTime(null);
        row.setFirstRecordId(null);
        payRiskTermMapper.insert(row);
        return row.getId();
    }

    @Override
    public void updateTerm(@Valid PayRiskTermSaveReqVO reqVO) {
        PayRiskTermDO existing = validateTermExists(reqVO.getId());
        String term = normalizeTerm(reqVO.getTerm());
        validateTermText(term);
        PayRiskTermDO duplicate = payRiskTermMapper.selectByTerm(term);
        if (duplicate != null && !duplicate.getId().equals(existing.getId())) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_EXISTS);
        }
        PayRiskTermDO update = new PayRiskTermDO();
        update.setId(existing.getId());
        update.setTerm(term);
        update.setCategory(normalizeCategory(reqVO.getCategory()));
        update.setStatus(reqVO.getStatus());
        update.setDescription(StrUtil.trim(reqVO.getDescription()));
        payRiskTermMapper.updateById(update);
    }

    @Override
    public void deleteTerm(Long id) {
        validateTermExists(id);
        payRiskTermMapper.deleteById(id);
    }

    @Override
    public PayRiskTermDO getTerm(Long id) {
        return validateTermExists(id);
    }

    @Override
    public PageResult<PayRiskTermDO> getTermPage(PayRiskTermPageReqVO pageReqVO) {
        backfillTermsFromRecentAssessRecords();
        return payRiskTermMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncChatTermsFromAssess(String paymentDataJson, Long recordId) {
        if (recordId == null) {
            return;
        }
        List<String> terms = PayRiskChatTermExtractor.extractTerms(paymentDataJson);
        if (terms.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (String raw : terms) {
            String termText = normalizeTerm(raw);
            if (StrUtil.isBlank(termText)) {
                continue;
            }
            try {
                upsertHit(termText, recordId, now);
            } catch (Exception ex) {
                log.warn("[syncChatTermsFromAssess] 同步风险词失败 term={}, recordId={}, err={}",
                        termText, recordId, ex.getMessage());
            }
        }
    }

    private void upsertHit(String termText, Long recordId, LocalDateTime now) {
        PayRiskTermDO term = payRiskTermMapper.selectByTerm(termText);
        if (term == null) {
            term = new PayRiskTermDO();
            term.setTerm(termText);
            term.setCategory(guessCategory(termText));
            term.setStatus(CommonStatusEnum.ENABLE.getStatus());
            term.setDescription(null);
            term.setSourceType(PayRiskTermConstants.SOURCE_AUTO);
            term.setHitCount(1L);
            term.setFirstSeenTime(now);
            term.setLastHitTime(now);
            term.setFirstRecordId(recordId);
            payRiskTermMapper.insert(term);
            insertHitIfAbsent(term.getId(), recordId);
            return;
        }
        PayRiskTermDO update = new PayRiskTermDO();
        update.setId(term.getId());
        update.setHitCount((term.getHitCount() == null ? 0L : term.getHitCount()) + 1);
        update.setLastHitTime(now);
        payRiskTermMapper.updateById(update);
        insertHitIfAbsent(term.getId(), recordId);
    }

    private void insertHitIfAbsent(Long termId, Long recordId) {
        if (payRiskTermHitMapper.selectByTermIdAndRecordId(termId, recordId) != null) {
            return;
        }
        PayRiskTermHitDO hit = new PayRiskTermHitDO();
        hit.setTermId(termId);
        hit.setRecordId(recordId);
        payRiskTermHitMapper.insert(hit);
    }

    private void backfillTermsFromRecentAssessRecords() {
        if (payRiskTermMapper.selectCount() > 0) {
            return;
        }
        for (PayRiskAssessRecordDO record : payRiskAssessRecordMapper.selectRecentPaymentData(50)) {
            syncChatTermsFromAssess(record.getPaymentDataJson(), record.getId());
        }
    }

    @Override
    public List<PayRiskTermDO> listTodayNewTerms(LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive) {
        return payRiskTermMapper.selectFirstSeenBetween(dayStartInclusive, dayEndExclusive);
    }

    @Override
    public PayRiskTermDO getTermByText(String term) {
        String normalized = normalizeTerm(term);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        return payRiskTermMapper.selectByTerm(normalized);
    }

    @Override
    public int countTodayHits(Long termId, LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive) {
        if (termId == null) {
            return 0;
        }
        return payRiskTermHitMapper.countByTermIdBetween(termId, dayStartInclusive, dayEndExclusive);
    }

    @Override
    public List<Long> listTodayHitRecordIds(Long termId, LocalDateTime dayStartInclusive, LocalDateTime dayEndExclusive) {
        if (termId == null) {
            return Collections.emptyList();
        }
        return payRiskTermHitMapper.selectRecordIdsByTermIdBetween(termId, dayStartInclusive, dayEndExclusive);
    }

    private PayRiskTermDO validateTermExists(Long id) {
        if (id == null) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_NOT_FOUND);
        }
        PayRiskTermDO row = payRiskTermMapper.selectById(id);
        if (row == null) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_NOT_FOUND);
        }
        return row;
    }

    private static String normalizeTerm(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static void validateTermText(String term) {
        if (StrUtil.isBlank(term)) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_TEXT_INVALID);
        }
        if (term.length() > PayRiskTermConstants.MAX_TERM_LEN) {
            throw exception(ErrorCodeConstants.PAY_RISK_TERM_TEXT_INVALID);
        }
    }

    private static String normalizeCategory(String category) {
        if (StrUtil.isBlank(category)) {
            return PayRiskTermConstants.CATEGORY_OTHER;
        }
        return category.trim().toUpperCase();
    }

    private static String guessCategory(String term) {
        String lower = term.toLowerCase();
        if (term.contains("链接") || term.contains("域名") || term.contains("URL") || lower.contains("http")) {
            return PayRiskTermConstants.CATEGORY_LINK;
        }
        if (term.contains("转账") || term.contains("打款") || term.contains("付款") || term.contains("收款")) {
            return PayRiskTermConstants.CATEGORY_PAYMENT;
        }
        if (term.contains("行为") || term.contains("设备") || term.contains("模拟器")) {
            return PayRiskTermConstants.CATEGORY_BEHAVIOR;
        }
        if (term.contains("话术") || term.contains("冒充") || term.contains("客服") || term.contains("公安")) {
            return PayRiskTermConstants.CATEGORY_FRAUD_SCRIPT;
        }
        return PayRiskTermConstants.CATEGORY_OTHER;
    }
}
