ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `whois_info_json` longtext NULL COMMENT 'Whois 情报 JSON' AFTER `ip_info_json`,
  ADD COLUMN `behavior_info_json` longtext NULL COMMENT '行为分析 JSON' AFTER `whois_info_json`,
  ADD COLUMN `topology_info_json` longtext NULL COMMENT '关系拓扑 JSON' AFTER `behavior_info_json`;
