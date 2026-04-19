package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

/**
 * LLM 对沟通对端（如聊天中的「对方」）的人物画像与话术侧研判。
 */
@Data
public class PayRiskLlmPersonaProfile {

    /**
     * 一句话人物画像（综合自称、话术、节奏与上下文）
     */
    private String summary;

    /**
     * 对话中呈现或暗示的身份 / 角色（如「平台客服」「财务同事」「陌生卖家」）
     */
    private String claimedOrImpliedRole;

    /**
     * 推断的沟通者原型（如催促型收款方、过度热情客服口吻、权威施压型等）
     */
    private String inferredArchetype;

    /**
     * 话术与互动特征（短句列表）
     */
    private List<String> communicationTraits;

    /**
     * 施压、诱导、紧迫感或情感操控类信号（短句列表）
     */
    private List<String> pressureAndControlSignals;
}
