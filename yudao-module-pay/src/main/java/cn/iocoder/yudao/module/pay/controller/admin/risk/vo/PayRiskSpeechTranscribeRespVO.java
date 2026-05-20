package cn.iocoder.yudao.module.pay.controller.admin.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 语音转文字 Response VO")
@Data
public class PayRiskSpeechTranscribeRespVO {

    @Schema(description = "识别出的文本")
    private String text;

    @Schema(description = "使用的 ASR 模型")
    private String model;
}
