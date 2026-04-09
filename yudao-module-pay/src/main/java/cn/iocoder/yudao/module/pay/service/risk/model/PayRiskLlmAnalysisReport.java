package cn.iocoder.yudao.module.pay.service.risk.model;

import lombok.Data;

import java.util.List;

@Data
public class PayRiskLlmAnalysisReport {

    private String mode;

    private String summary;

    private String verdict;

    private String confidence;

    private List<String> evidence;

    private List<String> suspiciousEntities;

    private List<String> recommendations;
}
