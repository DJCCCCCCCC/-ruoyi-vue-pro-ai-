package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

/**
 * 结合「本人」年龄段、性格与防诈认知水平，用大白话说明对方为何像诈骗，并给出可执行的个性化防范建议。
 */
@Data
public class PayRiskLlmTailoredUserGuidance {

    /**
     * 用贴近用户理解的方式说明：对方为何可疑、与常见诈骗套路如何对应（避免术语堆砌）
     */
    private String whyLikelyScamPlainLanguage;

    /**
     * 结合 userProfile 中的年龄与个性等，给出的可操作防范建议（短句列表）
     */
    private List<String> preventionTipsForThisUser;

    /**
     * 一句安抚：减轻恐慌、自责，鼓励先暂停转账、向亲友或官方核实
     */
    private String reassuranceLine;
}
