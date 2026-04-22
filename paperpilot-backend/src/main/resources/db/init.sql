-- 创建数据库
CREATE DATABASE IF NOT EXISTS paperpilot
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE paperpilot;

-- 用户表
CREATE TABLE `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `email` VARCHAR(255) NOT NULL COMMENT '邮箱（唯一）',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'bcrypt加密密码',
    `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `quota_balance` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '剩余额度（次数）',
    `is_vip` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否VIP',
    `vip_expire_at` DATETIME DEFAULT NULL COMMENT 'VIP过期时间',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户自定义AI配置表 (BYOK)
CREATE TABLE `user_ai_configs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `provider` VARCHAR(20) NOT NULL COMMENT '提供商: openai/deepseek/claude/glm/custom',
    `api_key` VARCHAR(255) NOT NULL COMMENT 'API Key (AES加密存储)',
    `base_url` VARCHAR(500) DEFAULT NULL COMMENT '自定义Base URL',
    `model` VARCHAR(100) NOT NULL COMMENT '模型名',
    `is_active` TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    `last_tested_at` DATETIME DEFAULT NULL COMMENT '上次测试时间',
    `test_status` VARCHAR(20) DEFAULT NULL COMMENT '测试结果',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_provider` (`user_id`, `provider`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户AI配置表';

-- 支付订单表
CREATE TABLE `orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '订单金额（元）',
    `quota_amount` INT UNSIGNED NOT NULL COMMENT '购买的额度数量',
    `channel` VARCHAR(20) NOT NULL COMMENT '支付渠道: alipay/wechat',
    `channel_order_no` VARCHAR(64) DEFAULT NULL COMMENT '第三方订单号',
    `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '状态: 0-待支付 1-已支付 2-已取消',
    `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间',
    `expire_at` DATETIME NOT NULL COMMENT '订单过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付订单表';

-- 额度变动记录表
CREATE TABLE `quota_transactions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `amount` INT NOT NULL COMMENT '变动数量（正数增加，负数减少）',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: recharge/consume/refund/bonus',
    `order_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单ID',
    `task_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联任务ID',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='额度变动记录';

-- 文献任务表
CREATE TABLE `paper_tasks` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_no` VARCHAR(32) NOT NULL COMMENT '任务编号',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `source` VARCHAR(20) NOT NULL COMMENT '来源: wos/sciencedirect/semanticscholar',
    `keyword` VARCHAR(255) NOT NULL COMMENT '搜索关键词',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/running/completed/failed',
    `total_count` INT UNSIGNED DEFAULT 0 COMMENT '总文献数',
    `processed_count` INT UNSIGNED DEFAULT 0 COMMENT '已处理数',
    `quota_consumed` INT UNSIGNED DEFAULT 0 COMMENT '消耗的额度',
    `result_file_url` VARCHAR(500) DEFAULT NULL COMMENT '结果文件URL',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `filters` JSON DEFAULT NULL COMMENT '筛选条件',
    `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献任务表';

-- API Keys表
CREATE TABLE `api_keys` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `api_key` VARCHAR(64) NOT NULL COMMENT 'API Key',
    `name` VARCHAR(100) DEFAULT 'default' COMMENT '名称',
    `last_used_at` DATETIME DEFAULT NULL COMMENT '上次使用时间',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Keys表';

-- 文献详情表
CREATE TABLE `papers` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT UNSIGNED NOT NULL COMMENT '关联任务ID',
    `doi` VARCHAR(100) DEFAULT NULL COMMENT 'DOI',
    `title` VARCHAR(500) NOT NULL COMMENT '标题',
    `authors` JSON NOT NULL COMMENT '作者列表（JSON数组）',
    `abstract` TEXT DEFAULT NULL COMMENT '摘要',
    `journal` VARCHAR(255) DEFAULT NULL COMMENT '期刊名',
    `publish_year` INT DEFAULT NULL COMMENT '发表年份',
    `impact_factor` DECIMAL(5,2) DEFAULT NULL COMMENT '影响因子',
    `quartile` VARCHAR(10) DEFAULT NULL COMMENT '分区: Q1/Q2/Q3/Q4',
    `citations` INT UNSIGNED DEFAULT 0 COMMENT '被引次数',
    `pdf_url` VARCHAR(500) DEFAULT NULL COMMENT 'PDF链接',
    `source_url` VARCHAR(500) NOT NULL COMMENT '原文链接',
    `ai_summary` TEXT DEFAULT NULL COMMENT 'AI总结',
    `ai_keywords` JSON DEFAULT NULL COMMENT 'AI提取关键词',
    `methodology` TEXT DEFAULT NULL COMMENT '研究方法',
    `conclusion` TEXT DEFAULT NULL COMMENT '研究结论',
    `research_findings` TEXT DEFAULT NULL COMMENT '研究成果',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_doi` (`doi`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献详情表';

-- AI调用日志表 (V1.1新增)
CREATE TABLE `ai_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `provider` VARCHAR(20) NOT NULL COMMENT 'AI提供商',
    `model` VARCHAR(100) NOT NULL COMMENT '模型名',
    `input_tokens` INT UNSIGNED DEFAULT 0 COMMENT '输入token数',
    `output_tokens` INT UNSIGNED DEFAULT 0 COMMENT '输出token数',
    `request_hash` VARCHAR(64) DEFAULT NULL COMMENT '请求摘要MD5（用于缓存）',
    `cache_hit` TINYINT(1) DEFAULT 0 COMMENT '是否缓存命中',
    `cost_amount` DECIMAL(10,6) DEFAULT 0 COMMENT '成本金额(元)',
    `response_time_ms` INT UNSIGNED DEFAULT NULL COMMENT '响应时间(毫秒)',
    `status` VARCHAR(20) DEFAULT 'success' COMMENT '状态: success/failed',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_request_hash` (`request_hash`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';

-- 插入默认管理员账号 (密码: admin123, bcrypt加密)
INSERT INTO `users` (`email`, `password_hash`, `nickname`, `quota_balance`, `status`)
VALUES ('admin@paperpilot.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', '管理员', 9999, 1);
