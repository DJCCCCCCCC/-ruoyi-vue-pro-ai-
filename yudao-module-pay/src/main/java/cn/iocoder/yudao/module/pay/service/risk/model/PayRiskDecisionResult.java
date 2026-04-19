package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 风控策略引擎输出的决策结果（用于业务执行与人工复核闭环）。
 */
@Data
public class PayRiskDecisionResult {

    /**
     * 策略版本号，便于审计与回放
     */
    private String policyVersion;

    /**
     * 建议动作：ALLOW / MANUAL_REVIEW / BLOCK
     */
    private String recommendedAction;

    /**
     * 机器可读原因码
     */
    private List<String> reasonCodes = new ArrayList<>();

    /**
     * 面向运营/复核人员的中文说明
     */
    private List<String> reasonMessages = new ArrayList<>();

    /**
     * 是否需要进入人工复核队列
     */
    private boolean requiresHumanReview;

    /**
     * 复核提示（短句）
     */
    private String reviewHint;

}
