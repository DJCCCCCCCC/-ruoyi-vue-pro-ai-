-- 支付风险词库
CREATE TABLE IF NOT EXISTS `pay_risk_term` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `term` varchar(256) NOT NULL COMMENT '风险词（来自聊天记录中的诈骗话术片段）',
  `category` varchar(64) DEFAULT 'OTHER' COMMENT '分类：FRAUD_SCRIPT/LINK/BEHAVIOR/PAYMENT/OTHER',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 禁用',
  `description` varchar(512) DEFAULT NULL COMMENT '说明',
  `source_type` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/AUTO_ASSESS',
  `hit_count` bigint NOT NULL DEFAULT 0 COMMENT '累计命中评估次数',
  `first_seen_time` datetime NOT NULL COMMENT '首次出现时间',
  `last_hit_time` datetime DEFAULT NULL COMMENT '最近命中时间',
  `first_record_id` bigint DEFAULT NULL COMMENT '首次命中的评估记录编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_term_tenant` (`term`, `tenant_id`, `deleted`),
  KEY `idx_first_seen_time` (`first_seen_time`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付风险词库';

-- 风险词与评估记录命中关联
CREATE TABLE IF NOT EXISTS `pay_risk_term_hit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `term_id` bigint NOT NULL COMMENT '风险词编号',
  `record_id` bigint NOT NULL COMMENT '评估记录编号',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_term_record` (`term_id`, `record_id`, `deleted`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付风险词命中记录';
