-- 支付风险评估记录表
CREATE TABLE IF NOT EXISTS `pay_risk_assess_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `source` varchar(128) DEFAULT NULL COMMENT '来源',
  `scene` varchar(128) DEFAULT NULL COMMENT '场景',
  `ip` varchar(64) DEFAULT NULL COMMENT '评估 IP',
  `risk_score` int DEFAULT NULL COMMENT '风险评分',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `risk_factors_json` text COMMENT '风险因素 JSON',
  `deep_analysis` text COMMENT '深度分析',
  `payment_data_json` longtext COMMENT '输入数据 JSON',
  `ip_info_json` longtext COMMENT 'IP 情报 JSON',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付风险评估记录表';

