ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `agent_reflection_json` longtext NULL COMMENT 'Agentic reflection JSON: assessor/skeptic/arbiter' AFTER `advanced_analysis_json`;
