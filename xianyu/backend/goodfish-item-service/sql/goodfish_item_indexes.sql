-- =============================================================================
-- goodfish-item-service：唯一键与查询索引（PostgreSQL）
--
-- 背景：
--   1. 商品同步使用 INSERT ... ON CONFLICT (goodfish_account_id, goodfish_item_id)，
--      该语法在缺少对应唯一索引时会直接报错 42P10
--      （there is no unique or exclusion constraint matching the ON CONFLICT
--       specification），因此必须先建 uk_gfid_item。
--   2. 提示词按 item_id 一对一 upsert（ON CONFLICT (item_id)），依赖 uk_item_id。
--   3. 主键 id 由 MyBatis-Plus 雪花算法在应用侧生成（ASSIGN_ID），
--      不是 BIGSERIAL，无需 sequence。
--
-- 注意（重要）：
--   uk_gfid_item 不能写成 WHERE del_flag = '0' 的部分索引。
--   商品删除是软删；闲鱼侧仍存在的商品再次同步时，靠 ON CONFLICT 命中软删记录
--   并把 del_flag 置回 '0'（复活）。若唯一键排除软删行，同一商品会插入重复行，
--   复活逻辑失效。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 步骤 1：建唯一索引前先确认没有重复数据（有结果必须先清理，否则建索引失败）
-- ---------------------------------------------------------------------------
SELECT goodfish_account_id,
       goodfish_item_id,
       count(*) AS duplicate_count
FROM goodfish_item
GROUP BY goodfish_account_id, goodfish_item_id
HAVING count(*) > 1;

SELECT item_id, count(*) AS duplicate_count
FROM goodfish_item_prompt
GROUP BY item_id
HAVING count(*) > 1;

-- ---------------------------------------------------------------------------
-- 步骤 2：清理重复（谨慎执行；默认注释掉，确认重复数据后按需取消注释）
--         规则：同一 (goodfish_account_id, goodfish_item_id) 保留 id 最大的一条
-- ---------------------------------------------------------------------------
-- DELETE FROM goodfish_item a
-- USING goodfish_item b
-- WHERE a.goodfish_account_id = b.goodfish_account_id
--   AND a.goodfish_item_id = b.goodfish_item_id
--   AND a.id < b.id;

-- DELETE FROM goodfish_item_prompt a
-- USING goodfish_item_prompt b
-- WHERE a.item_id = b.item_id
--   AND a.id < b.id;

-- ---------------------------------------------------------------------------
-- 步骤 3：唯一索引
-- ---------------------------------------------------------------------------
-- 商品唯一键：同步 upsert / 软删复活依赖此索引（必须覆盖软删行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_gfid_item
    ON goodfish_item (goodfish_account_id, goodfish_item_id);

-- 提示词一对一唯一键：编辑与复制提示词的 upsert 依赖此索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_item_id
    ON goodfish_item_prompt (item_id);

-- ---------------------------------------------------------------------------
-- 步骤 4：查询索引
-- ---------------------------------------------------------------------------
-- 后台按账号主键（goodfish_account.id）聚合；按闲鱼账号 ID 查询走 uk_gfid_item 左前缀
CREATE INDEX IF NOT EXISTS idx_goodfish_item_account
    ON goodfish_item (account_id);

COMMENT ON INDEX uk_gfid_item IS '商品唯一键（含软删行，供同步 ON CONFLICT 复活使用）';
COMMENT ON INDEX uk_item_id IS '商品提示词一对一唯一键（供 upsert ON CONFLICT 使用）';
