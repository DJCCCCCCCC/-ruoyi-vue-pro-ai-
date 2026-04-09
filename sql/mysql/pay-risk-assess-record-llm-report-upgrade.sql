ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `llm_report_json` longtext NULL COMMENT 'LLM risk analysis report JSON' AFTER `topology_info_json`;
