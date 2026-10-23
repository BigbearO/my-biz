-- =============================================================================
-- llm-manage：主键从数据库自增迁移到应用侧雪花 ID
--
-- 执行前提：应用已上线「生成器 + autoIncrement:false + BeforeCreate」版本。
-- 顺序不能反：先代码后 DDL。DDL 先执行的话，旧代码插入不带 id 会直接失败。
--
-- 存量数据无需搬移：雪花 ID 约 1e18，远大于现有小整数，主键不冲突；
-- 雪花高位为时间戳，按 id 排序仍等价于创建顺序。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 步骤 1：确认当前列形态（serial 有 column_default，identity 有 is_identity）
-- ---------------------------------------------------------------------------
SELECT table_name,
       column_name,
       data_type,
       column_default,
       is_identity
FROM information_schema.columns
WHERE table_name IN ('llm_provider', 'llm_model')
  AND column_name = 'id';

-- ---------------------------------------------------------------------------
-- 步骤 2：去掉自增默认值（serial 形态）
-- ---------------------------------------------------------------------------
ALTER TABLE llm_provider ALTER COLUMN id DROP DEFAULT;
ALTER TABLE llm_model    ALTER COLUMN id DROP DEFAULT;

DROP SEQUENCE IF EXISTS llm_provider_id_seq;
DROP SEQUENCE IF EXISTS llm_model_id_seq;

-- ---------------------------------------------------------------------------
-- 步骤 3：identity 形态（与步骤 2 互斥，按步骤 1 的结果二选一执行）
-- ---------------------------------------------------------------------------
-- ALTER TABLE llm_provider ALTER COLUMN id DROP IDENTITY IF EXISTS;
-- ALTER TABLE llm_model    ALTER COLUMN id DROP IDENTITY IF EXISTS;

-- ---------------------------------------------------------------------------
-- 步骤 4：拦截 id = 0（生成器未初始化时插入 0，立即暴露而不是变成脏数据）
-- ---------------------------------------------------------------------------
ALTER TABLE llm_provider ADD CONSTRAINT chk_llm_provider_id_positive CHECK (id > 0);
ALTER TABLE llm_model    ADD CONSTRAINT chk_llm_model_id_positive CHECK (id > 0);

-- ---------------------------------------------------------------------------
-- 步骤 5：验证（新数据应 >= 16 位，且严格递增）
-- ---------------------------------------------------------------------------
SELECT id, length(id::text) AS digits, created_at
FROM llm_model
ORDER BY id DESC
LIMIT 10;

-- ---------------------------------------------------------------------------
-- 回滚（重新挂序列即可；新序列从 1 开始不会与雪花 ID 冲突）
-- ---------------------------------------------------------------------------
-- ALTER TABLE llm_model DROP CONSTRAINT IF EXISTS chk_llm_model_id_positive;
-- CREATE SEQUENCE llm_model_id_seq OWNED BY llm_model.id;
-- ALTER TABLE llm_model ALTER COLUMN id SET DEFAULT nextval('llm_model_id_seq');
-- SELECT setval('llm_model_id_seq', COALESCE((SELECT max(id) FROM llm_model), 1));
