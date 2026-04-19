-- 决策闭环：策略输出与人工复核字段
ALTER TABLE `pay_risk_assess_record`
  ADD COLUMN `decision_action` varchar(32) NULL COMMENT '策略建议动作 ALLOW/MANUAL_REVIEW/BLOCK' AFTER `advanced_analysis_json`,
  ADD COLUMN `decision_json` text NULL COMMENT '策略决策 JSON' AFTER `decision_action`,
  ADD COLUMN `review_status` varchar(32) NULL COMMENT '人工复核状态' AFTER `decision_json`,
  ADD COLUMN `review_remark` varchar(512) NULL COMMENT '复核备注' AFTER `review_status`,
  ADD COLUMN `reviewer` varchar(64) NULL COMMENT '复核人' AFTER `review_remark`,
  ADD COLUMN `review_time` datetime NULL COMMENT '复核时间' AFTER `reviewer`;
