-- ========================================================
-- 代练管理系统数据库初始化脚本
-- 文件名: init.sql
-- 创建日期: 2025-02-09
-- 说明: 包含所有表结构创建语句
-- ========================================================

-- --------------------------------------------------------
-- 1. 游戏账号表 (account) - 新增字段
-- --------------------------------------------------------
-- 说明: 在现有account表基础上新增字段
-- 注意: 以下ALTER语句仅在表已存在时执行

-- 新增"是否启用"字段
ALTER TABLE account 
ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用';

-- 新增"分配的模拟器ID"字段
ALTER TABLE account 
ADD COLUMN IF NOT EXISTS emulator_id BIGINT COMMENT '分配的模拟器ID';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_account_enabled ON account(enabled);
CREATE INDEX IF NOT EXISTS idx_account_emulator_id ON account(emulator_id);


-- --------------------------------------------------------
-- 2. 模拟器表 (emulator) - 新建表
-- --------------------------------------------------------
-- 说明: 存储模拟器信息，包括IP、端口、游戏类型、数量上限等

DROP TABLE IF EXISTS emulator;

CREATE TABLE emulator (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(100) NOT NULL COMMENT '模拟器名称',
    ip VARCHAR(50) NOT NULL COMMENT 'IP地址',
    port INT NOT NULL COMMENT '端口',
    game_type INT NOT NULL COMMENT '游戏类型（0=原神, 1=尘白禁区）',
    max_count INT NOT NULL DEFAULT 10 COMMENT '数量上限（该模拟器最多承载账号数）',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag INT DEFAULT 0 COMMENT '删除标志（0正常 2删除）',
    
    -- 索引
    INDEX idx_game_type (game_type),
    INDEX idx_enabled (enabled)
    
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟器表';


-- --------------------------------------------------------
-- 3. 执行日志表 (execution_log) - 新建表
-- --------------------------------------------------------
-- 说明: 记录定时任务发送MQ的执行日志

DROP TABLE IF EXISTS execution_log;

CREATE TABLE execution_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    account_id BIGINT NOT NULL COMMENT '账号ID',
    emulator_id BIGINT NOT NULL COMMENT '模拟器ID',
    game_type INT NOT NULL COMMENT '游戏类型',
    command VARCHAR(100) COMMENT '执行命令',
    status INT DEFAULT 0 COMMENT '执行状态（0待执行 1执行中 2成功 3失败）',
    mq_message TEXT COMMENT '发送的MQ消息内容（JSON格式）',
    execute_time DATETIME COMMENT '执行时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 索引
    INDEX idx_account_id (account_id),
    INDEX idx_emulator_id (emulator_id),
    INDEX idx_game_type (game_type),
    INDEX idx_create_time (create_time),
    INDEX idx_account_date (account_id, create_time),
    INDEX idx_year_month (DATE_FORMAT(create_time, '%Y-%m'))
    
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志表';


-- --------------------------------------------------------
-- 4. 初始化数据
-- --------------------------------------------------------

-- 插入示例模拟器数据（可选）
-- INSERT INTO emulator (name, ip, port, game_type, max_count, enabled) VALUES
-- ('原神模拟器-01', '192.168.1.101', 5555, 0, 10, TRUE),
-- ('原神模拟器-02', '192.168.1.102', 5555, 0, 10, TRUE),
-- ('尘白禁区模拟器-01', '192.168.1.201', 5555, 1, 10, TRUE);


-- --------------------------------------------------------
-- 5. 统计视图（可选）
-- --------------------------------------------------------
-- 月度统计视图：按账号和日期分组统计有效执行次数

CREATE OR REPLACE VIEW v_monthly_statistics AS
SELECT 
    el.account_id,
    a.user_account AS account_name,
    el.game_type,
    DATE_FORMAT(el.create_time, '%Y-%m') AS year_month,
    DATE(el.create_time) AS exec_date,
    COUNT(*) AS exec_count
FROM execution_log el
LEFT JOIN account a ON el.account_id = a.id
GROUP BY el.account_id, el.game_type, DATE(el.create_time);


-- --------------------------------------------------------
-- 6. 注释说明
-- --------------------------------------------------------

/*
游戏类型枚举 (GamesEnum):
  0 = 原神
  1 = 尘白禁区

执行状态枚举:
  0 = 待执行
  1 = 执行中
  2 = 成功
  3 = 失败

统计逻辑说明:
  按月统计时，每个账号每天只算1次有效计数
  SQL示例:
  SELECT 
      account_id,
      COUNT(DISTINCT DATE(create_time)) AS valid_days
  FROM execution_log
  WHERE DATE_FORMAT(create_time, '%Y-%m') = '2025-02'
  GROUP BY account_id;
*/
