ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `whois_info_json` longtext NULL COMMENT 'Whois info JSON' AFTER `ip_info_json`;

ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `behavior_info_json` longtext NULL COMMENT 'Behavior analysis JSON' AFTER `whois_info_json`;

ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `topology_info_json` longtext NULL COMMENT 'Relation topology JSON' AFTER `behavior_info_json`;

ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `llm_report_json` longtext NULL COMMENT 'LLM risk analysis report JSON' AFTER `topology_info_json`;

ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `advanced_analysis_json` longtext NULL COMMENT 'Advanced risk analysis JSON' AFTER `llm_report_json`;
