-- ========================================================
-- 代练管理系统数据库建表脚本 (PostgreSQL)
-- 文件名: db.sql
-- 创建日期: 2025-02-10
-- 说明: 根据domain/entity实体生成的完整表结构
-- ========================================================

-- --------------------------------------------------------
-- 1. 游戏账号表 (game_account)
-- --------------------------------------------------------
DROP TABLE IF EXISTS game_account CASCADE;

CREATE TABLE game_account (
    id              BIGINT PRIMARY KEY,
    user_account    VARCHAR(100),
    credential      VARCHAR(255),
    start_time      TIMESTAMP,
    expire_time     TIMESTAMP,
    type            INTEGER,
    enabled         BOOLEAN DEFAULT FALSE,
    emulator_id     BIGINT,
    tenant_id       BIGINT,
    create_by       VARCHAR(64),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag        BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE game_account IS '游戏账号表';
COMMENT ON COLUMN game_account.id IS '订单表主键';
COMMENT ON COLUMN game_account.user_account IS '用户账号';
COMMENT ON COLUMN game_account.credential IS '用户登录凭证';
COMMENT ON COLUMN game_account.start_time IS '开始时间';
COMMENT ON COLUMN game_account.expire_time IS '过期时间';
COMMENT ON COLUMN game_account.type IS '游戏类型';
COMMENT ON COLUMN game_account.enabled IS '是否启用';
COMMENT ON COLUMN game_account.emulator_id IS '分配的模拟器ID';
COMMENT ON COLUMN game_account.tenant_id IS '多租户id';
COMMENT ON COLUMN game_account.create_by IS '创建者';
COMMENT ON COLUMN game_account.create_time IS '创建时间';
COMMENT ON COLUMN game_account.update_by IS '更新者';
COMMENT ON COLUMN game_account.update_time IS '更新时间';
COMMENT ON COLUMN game_account.del_flag IS '删除标志';

CREATE INDEX idx_game_account_user_account ON game_account(user_account);
CREATE INDEX idx_game_account_emulator_id ON game_account(emulator_id);
CREATE INDEX idx_game_account_expire_time ON game_account(expire_time);
CREATE INDEX idx_game_account_tenant_id ON game_account(tenant_id);

-- --------------------------------------------------------
-- 2. 模拟器表 (emulator)
-- --------------------------------------------------------
DROP TABLE IF EXISTS emulator CASCADE;

CREATE TABLE emulator (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    ip              VARCHAR(50),
    port            INTEGER,
    game_type       INTEGER,
    max_count       INTEGER DEFAULT 10,
    enabled         BOOLEAN DEFAULT TRUE,
    create_by       VARCHAR(64),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag        BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE emulator IS '模拟器表';
COMMENT ON COLUMN emulator.id IS '主键ID';
COMMENT ON COLUMN emulator.name IS '模拟器名称';
COMMENT ON COLUMN emulator.ip IS 'IP地址';
COMMENT ON COLUMN emulator.port IS '端口';
COMMENT ON COLUMN emulator.game_type IS '游戏类型（0=原神, 1=尘白禁区）';
COMMENT ON COLUMN emulator.max_count IS '数量上限（该模拟器最多承载账号数）';
COMMENT ON COLUMN emulator.enabled IS '是否启用';
COMMENT ON COLUMN emulator.create_by IS '创建者';
COMMENT ON COLUMN emulator.create_time IS '创建时间';
COMMENT ON COLUMN emulator.update_by IS '更新者';
COMMENT ON COLUMN emulator.update_time IS '更新时间';
COMMENT ON COLUMN emulator.del_flag IS '删除标志';

CREATE INDEX idx_emulator_game_type ON emulator(game_type);
CREATE INDEX idx_emulator_enabled ON emulator(enabled);
CREATE INDEX idx_emulator_ip_port ON emulator(ip, port);

-- --------------------------------------------------------
-- 3. 执行日志表 (execution_log)
-- --------------------------------------------------------
DROP TABLE IF EXISTS execution_log CASCADE;

CREATE TABLE execution_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      BIGINT,
    emulator_id     BIGINT,
    game_type       INTEGER,
    command         VARCHAR(100),
    status          INTEGER DEFAULT 0,
    mq_message      TEXT,
    execute_time    TIMESTAMP,
    create_by       VARCHAR(64),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag        BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE execution_log IS '执行日志表';
COMMENT ON COLUMN execution_log.id IS '主键ID';
COMMENT ON COLUMN execution_log.account_id IS '账号ID';
COMMENT ON COLUMN execution_log.emulator_id IS '模拟器ID';
COMMENT ON COLUMN execution_log.game_type IS '游戏类型';
COMMENT ON COLUMN execution_log.command IS '执行命令';
COMMENT ON COLUMN execution_log.status IS '执行状态（0待执行 1执行中 2成功 3失败）';
COMMENT ON COLUMN execution_log.mq_message IS 'MQ消息内容（JSON格式）';
COMMENT ON COLUMN execution_log.execute_time IS '执行时间';
COMMENT ON COLUMN execution_log.create_by IS '创建者';
COMMENT ON COLUMN execution_log.create_time IS '创建时间';
COMMENT ON COLUMN execution_log.update_by IS '更新者';
COMMENT ON COLUMN execution_log.update_time IS '更新时间';
COMMENT ON COLUMN execution_log.del_flag IS '删除标志';

CREATE INDEX idx_execution_log_account_id ON execution_log(account_id);
CREATE INDEX idx_execution_log_emulator_id ON execution_log(emulator_id);
CREATE INDEX idx_execution_log_game_type ON execution_log(game_type);
CREATE INDEX idx_execution_log_status ON execution_log(status);
CREATE INDEX idx_execution_log_execute_time ON execution_log(execute_time);

-- --------------------------------------------------------
-- 4. 订单表 ("order")
-- --------------------------------------------------------
DROP TABLE IF EXISTS "order" CASCADE;

CREATE TABLE "order" (
    id              BIGINT PRIMARY KEY,
    order_no        VARCHAR(64),
    user_id         VARCHAR(64),
    order_type      INTEGER,
    expire_time     TIMESTAMP,
    cancel_reason   VARCHAR(255),
    cancel_time     TIMESTAMP,
    status          INTEGER,
    game_type       INTEGER,
    pay_time        TIMESTAMP,
    pay_type        INTEGER,
    pay_amount      NUMERIC(19,2),
    remark          VARCHAR(500),
    freight_fee     NUMERIC(19,2),
    refund_status   INTEGER,
    refund_time     TIMESTAMP,
    refund_channel  VARCHAR(50),
    days            INTEGER,
    create_by       VARCHAR(64),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64),
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag        BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE "order" IS '订单表';
COMMENT ON COLUMN "order".id IS '订单表主键';
COMMENT ON COLUMN "order".order_no IS '三方订单流水号';
COMMENT ON COLUMN "order".user_id IS '用户id';
COMMENT ON COLUMN "order".order_type IS '订单类型';
COMMENT ON COLUMN "order".expire_time IS '订单过期时间';
COMMENT ON COLUMN "order".cancel_reason IS '取消理由';
COMMENT ON COLUMN "order".cancel_time IS '取消时间';
COMMENT ON COLUMN "order".status IS '订单状态';
COMMENT ON COLUMN "order".game_type IS '游戏类型';
COMMENT ON COLUMN "order".pay_time IS '支付时间';
COMMENT ON COLUMN "order".pay_type IS '支付渠道';
COMMENT ON COLUMN "order".pay_amount IS '支付金额，单位分';
COMMENT ON COLUMN "order".remark IS '订单备注';
COMMENT ON COLUMN "order".freight_fee IS '运费';
COMMENT ON COLUMN "order".refund_status IS '退款状态';
COMMENT ON COLUMN "order".refund_time IS '退款时间';
COMMENT ON COLUMN "order".refund_channel IS '退款渠道';
COMMENT ON COLUMN "order".days IS '会员天数';
COMMENT ON COLUMN "order".create_by IS '创建者';
COMMENT ON COLUMN "order".create_time IS '创建时间';
COMMENT ON COLUMN "order".update_by IS '更新者';
COMMENT ON COLUMN "order".update_time IS '更新时间';
COMMENT ON COLUMN "order".del_flag IS '删除标志';

CREATE INDEX idx_order_order_no ON "order"(order_no);
CREATE INDEX idx_order_user_id ON "order"(user_id);
CREATE INDEX idx_order_status ON "order"(status);
CREATE INDEX idx_order_game_type ON "order"(game_type);
CREATE INDEX idx_order_pay_time ON "order"(pay_time);

-- --------------------------------------------------------
-- 5. 订单项表 (order_item)
-- --------------------------------------------------------
DROP TABLE IF EXISTS order_item CASCADE;

CREATE TABLE order_item (
    id                  BIGINT PRIMARY KEY,
    order_id            BIGINT,
    logistics_id        BIGINT,
    consignee_name      VARCHAR(100),
    consignee_phone     VARCHAR(20),
    consignee_address   VARCHAR(255),
    delivery_time       TIMESTAMP,
    commodity_id        BIGINT,
    commodity_name      VARCHAR(200),
    commodity_cover_url VARCHAR(255),
    commodity_price     NUMERIC(19,2) DEFAULT 0,
    commodity_points    NUMERIC(19,2) DEFAULT 0,
    commodity_quantity  INTEGER DEFAULT 0,
    discount_amount     NUMERIC(19,2) DEFAULT 0,
    subtotal            NUMERIC(19,2) DEFAULT 0,
    extra_data          JSONB,
    create_by           VARCHAR(64),
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64),
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag            BOOLEAN DEFAULT FALSE
);

COMMENT ON TABLE order_item IS '订单项表';
COMMENT ON COLUMN order_item.id IS '订单详情表主键';
COMMENT ON COLUMN order_item.order_id IS '订单表主键';
COMMENT ON COLUMN order_item.logistics_id IS '物流表主键，若无需发货，值为0';
COMMENT ON COLUMN order_item.consignee_name IS '收货人姓名';
COMMENT ON COLUMN order_item.consignee_phone IS '收货人电话';
COMMENT ON COLUMN order_item.consignee_address IS '收货地址';
COMMENT ON COLUMN order_item.delivery_time IS '发货时间';
COMMENT ON COLUMN order_item.commodity_id IS '商品ID';
COMMENT ON COLUMN order_item.commodity_name IS '商品名';
COMMENT ON COLUMN order_item.commodity_cover_url IS '商品封面';
COMMENT ON COLUMN order_item.commodity_price IS '购买单个商品的价格';
COMMENT ON COLUMN order_item.commodity_points IS '购买商品花费的总积分';
COMMENT ON COLUMN order_item.commodity_quantity IS '购买的商品数量';
COMMENT ON COLUMN order_item.discount_amount IS '折扣金额';
COMMENT ON COLUMN order_item.subtotal IS '商品小计金额';
COMMENT ON COLUMN order_item.extra_data IS '额外信息';
COMMENT ON COLUMN order_item.create_by IS '创建者';
COMMENT ON COLUMN order_item.create_time IS '创建时间';
COMMENT ON COLUMN order_item.update_by IS '更新者';
COMMENT ON COLUMN order_item.update_time IS '更新时间';
COMMENT ON COLUMN order_item.del_flag IS '删除标志';

CREATE INDEX idx_order_item_order_id ON order_item(order_id);
CREATE INDEX idx_order_item_commodity_id ON order_item(commodity_id);
CREATE INDEX idx_order_item_logistics_id ON order_item(logistics_id);
