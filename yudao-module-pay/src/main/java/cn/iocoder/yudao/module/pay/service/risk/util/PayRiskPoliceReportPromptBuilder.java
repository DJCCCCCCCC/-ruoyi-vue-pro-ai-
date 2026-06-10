package cn.iocoder.yudao.module.pay.service.risk.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PayRiskPoliceReportPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "你是一名反诈案件研判与报案材料整理专家，服务对象是已遭受电信网络诈骗、需要向公安机关报警的受害人。"
                    + "你会收到脱敏后的聊天记录、转账上下文、关系拓扑、IP/域名情报，以及可选的系统事前风控预警快照。"
                    + "你的任务是：基于现有证据整理一份面向一线民警的结构化案情摘要，帮助受害人说明经过、汇总线索、推测资金/赃物可能去向。"
                    + "要求："
                    + "① 只依据上下文中出现的信息进行归纳，不得编造具体姓名、身份证号、完整银行账号；对未出现的细节用「待核实」标注；"
                    + "② 对资金流向、账户去向等只能做「推测/可能」，并在 limitations 中说明依据与局限；"
                    + "③ 若 priorAssessSnapshot 显示系统曾发出高风险预警而用户仍转账，须在 systemWarnings 中客观记录；"
                    + "④ timeline 按聊天时间或逻辑顺序排列；suspectClues 尽量覆盖账号、手机、链接、IP、平台 ID 等；"
                    + "⑤ printableStatement 用第一人称「我」写一段 300～800 字的完整口述稿，可直接向民警陈述；"
                    + "⑥ 所有字符串与列表项使用中文；不要输出 markdown 或代码块；只返回一个 JSON 对象。";

    public static String buildUserPrompt(String contextJson) {
        return "以下是脱敏后的案情上下文（含聊天记录、转账线索、拓扑与可选的事前风控预警）：\n"
                + "<PoliceReportContext>" + contextJson + "</PoliceReportContext>\n\n"
                + "请返回如下 JSON（字段必须齐全，列表可为空数组但不可省略）：\n"
                + "{\n"
                + "  \"mode\": \"LLM\",\n"
                + "  \"reportTitle\": \"电信网络诈骗报案协助摘要\",\n"
                + "  \"caseSummary\": \"一句话概括案情\",\n"
                + "  \"fraudType\": \"诈骗类型，如冒充客服、刷单、虚假投资等\",\n"
                + "  \"fraudModusOperandi\": \"作案手法分条叙述（200字内）\",\n"
                + "  \"urgencyLevel\": \"LOW|MEDIUM|HIGH|URGENT\",\n"
                + "  \"timeline\": [{\"time\":\"\",\"phase\":\"接触|诱导|转账|事后\",\"description\":\"\",\"role\":\"victim|suspect|system\"}],\n"
                + "  \"transferSummary\": {\"amounts\":[],\"channels\":[],\"payeeAccounts\":[],\"totalLossEstimate\":\"\",\"transferTimeHint\":\"\"},\n"
                + "  \"suspectClues\": [{\"category\":\"account|phone|link|ip|identity|platform|other\",\"value\":\"\",\"source\":\"chat|ocr|topology|user_note\",\"note\":\"\"}],\n"
                + "  \"fundFlowAnalysis\": {\"summary\":\"\",\"inferredPaths\":[],\"freezeTargets\":[],\"limitations\":[]},\n"
                + "  \"evidenceInventory\": [\"受害人应保存/提交的证据 1\"],\n"
                + "  \"systemWarnings\": [\"系统曾提示的风险点（若有）\"],\n"
                + "  \"policeChecklist\": [\"建议民警核查方向 1\"],\n"
                + "  \"victimActionItems\": [\"受害人下一步 1：立即报警、冻结账户等\"],\n"
                + "  \"printableStatement\": \"第一人称完整口述稿\",\n"
                + "  \"disclaimer\": \"本报告由 AI 根据现有聊天与线索整理，仅供参考，最终以公安机关调查为准。\"\n"
                + "}";
    }
}
