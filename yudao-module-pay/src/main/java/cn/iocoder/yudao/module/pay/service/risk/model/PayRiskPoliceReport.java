package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

/**
 * 事后报警协助：面向民警的结构化案情摘要与线索报告。
 */
@Data
public class PayRiskPoliceReport {

    private String mode;

    private String reportTitle;

    /** 案件一句话摘要 */
    private String caseSummary;

    /** 诈骗类型/族系 */
    private String fraudType;

    /** 作案手法描述 */
    private String fraudModusOperandi;

    /** 紧急程度：LOW / MEDIUM / HIGH / URGENT */
    private String urgencyLevel;

    /** 事件时间线 */
    private List<TimelineItem> timeline;

    /** 转账/损失汇总 */
    private TransferSummary transferSummary;

    /** 嫌疑人/对端线索 */
    private List<SuspectClue> suspectClues;

    /** 资金流向与赃物去向推测 */
    private FundFlowAnalysis fundFlowAnalysis;

    /** 证据清单（便于受害人准备材料） */
    private List<String> evidenceInventory;

    /** 系统曾发出的预警摘要（若用户忽略后继续转账） */
    private List<String> systemWarnings;

    /** 建议警方核查事项 */
    private List<String> policeChecklist;

    /** 受害人下一步动作（报警前后） */
    private List<String> victimActionItems;

    /** 可打印/口述的完整案情陈述（给派出所） */
    private String printableStatement;

    /** 分析局限与免责声明 */
    private String disclaimer;

    @Data
    public static class TimelineItem {
        /** 时间描述（可从聊天 timestamp 推断） */
        private String time;
        /** 阶段，如：接触、诱导、转账、事后 */
        private String phase;
        private String description;
        /** victim / suspect / system */
        private String role;
    }

    @Data
    public static class TransferSummary {
        private List<String> amounts;
        private List<String> channels;
        private List<String> payeeAccounts;
        private String totalLossEstimate;
        private String transferTimeHint;
    }

    @Data
    public static class SuspectClue {
        /** account / phone / link / ip / identity / platform / other */
        private String category;
        private String value;
        /** 线索来源：chat / ocr / topology / user_note */
        private String source;
        private String note;
    }

    @Data
    public static class FundFlowAnalysis {
        private String summary;
        /** 推测资金流向路径 */
        private List<String> inferredPaths;
        /** 建议冻结/追踪的账户或平台 */
        private List<String> freezeTargets;
        /** 分析局限说明 */
        private List<String> limitations;
    }
}
