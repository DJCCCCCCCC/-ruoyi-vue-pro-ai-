ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `advanced_analysis_json` longtext NULL COMMENT 'Advanced risk analysis JSON' AFTER `llm_report_json`;
