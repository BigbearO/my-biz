-- ============================================
-- 模型管理模块数据库脚本
-- 包含：ai_agent（智能体）、ai_model_provider（模型供应商）、ai_model_config（模型配置）
-- ============================================

-- --------------------------------------------
-- 1. 智能体表 ai_agent
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ai_agent (
    id              VARCHAR(64) PRIMARY KEY COMMENT '智能体唯一标识',
    user_id         BIGINT COMMENT '所属用户ID',
    agent_code      VARCHAR(100) NOT NULL COMMENT '智能体编码',
    agent_name      VARCHAR(200) COMMENT '智能体名称',
    asr_model_id    VARCHAR(64) COMMENT '语音识别模型标识',
    vad_model_id    VARCHAR(64) COMMENT '语音活动检测标识',
    llm_model_id    VARCHAR(64) COMMENT '大语言模型标识',
    vllm_model_id   VARCHAR(64) COMMENT 'VLLM模型标识',
    tts_model_id    VARCHAR(64) COMMENT '语音合成模型标识',
    tts_voice_id    VARCHAR(64) COMMENT '音色标识',
    mem_model_id    VARCHAR(64) COMMENT '记忆模型标识',
    intent_model_id VARCHAR(64) COMMENT '意图模型标识',
    chat_history_conf INTEGER DEFAULT 0 COMMENT '聊天记录配置（0不记录 1仅记录文本 2记录文本和语音）',
    system_prompt   TEXT COMMENT '角色设定参数',
    summary_memory  TEXT COMMENT '总结记忆',
    lang_code       VARCHAR(20) COMMENT '语言编码',
    language        VARCHAR(50) COMMENT '交互语种',
    sort            INTEGER DEFAULT 0 COMMENT '排序',
    tenant_id       BIGINT DEFAULT 0 COMMENT '租户ID',
    del_flag        INTEGER DEFAULT 0 COMMENT '删除标志（0正常 1已删除）',
    creator         BIGINT COMMENT '创建者',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater         BIGINT COMMENT '更新者',
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '智能体配置表';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ai_agent_user_id ON ai_agent(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_agent_code ON ai_agent(agent_code);
CREATE INDEX IF NOT EXISTS idx_ai_agent_tenant_id ON ai_agent(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_del_flag ON ai_agent(del_flag);

-- --------------------------------------------
-- 2. 模型供应商表 ai_model_provider
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ai_model_provider (
    id              VARCHAR(64) PRIMARY KEY COMMENT '主键',
    model_type      VARCHAR(50) NOT NULL COMMENT '模型类型（Memory/ASR/VAD/LLM/TTS）',
    provider_code   VARCHAR(100) NOT NULL COMMENT '供应器类型，如 openai、aliyun、doubao',
    name            VARCHAR(200) COMMENT '供应器名称',
    fields          TEXT COMMENT '供应器字段列表（JSON格式）',
    sort            INTEGER DEFAULT 0 COMMENT '排序',
    tenant_id       BIGINT DEFAULT 0 COMMENT '租户ID',
    del_flag        INTEGER DEFAULT 0 COMMENT '删除标志（0正常 1已删除）',
    creator         BIGINT COMMENT '创建者',
    create_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater         BIGINT COMMENT '更新者',
    update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '模型供应商表';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ai_model_provider_model_type ON ai_model_provider(model_type);
CREATE INDEX IF NOT EXISTS idx_ai_model_provider_provider_code ON ai_model_provider(provider_code);
CREATE INDEX IF NOT EXISTS idx_ai_model_provider_tenant_id ON ai_model_provider(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_provider_del_flag ON ai_model_provider(del_flag);

-- --------------------------------------------
-- 3. 模型配置表 ai_model_config
-- --------------------------------------------
CREATE TABLE IF NOT EXISTS ai_model_config (
    id              VARCHAR(64) PRIMARY KEY COMMENT '主键',
    model_type      VARCHAR(50) NOT NULL COMMENT '模型类型（Memory/ASR/VAD/LLM/TTS）',
    model_code      VARCHAR(100) NOT NULL COMMENT '模型编码（如AliLLM、DoubaoTTS）',
    model_name      VARCHAR(200) COMMENT '模型名称',
    is_default      INTEGER DEFAULT 0 COMMENT '是否默认配置（0否 1是）',
    is_enabled      INTEGER DEFAULT 1 COMMENT '是否启用（0禁用 1启用）',
    config_json     TEXT COMMENT '模型配置（JSON格式）',
    doc_link        VARCHAR(500) COMMENT '官方文档链接',
    remark          VARCHAR(500) COMMENT '备注',
    sort            INTEGER DEFAULT 0 COMMENT '排序',
    tenant_id       BIGINT DEFAULT 0 COMMENT '租户ID',
    del_flag        INTEGER DEFAULT 0 COMMENT '删除标志（0正常 1已删除）',
    creator         BIGINT COMMENT '创建者',
    create_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updater         BIGINT COMMENT '更新者',
    update_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT '模型配置表';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_ai_model_config_model_type ON ai_model_config(model_type);
CREATE INDEX IF NOT EXISTS idx_ai_model_config_model_code ON ai_model_config(model_code);
CREATE INDEX IF NOT EXISTS idx_ai_model_config_is_default ON ai_model_config(is_default);
CREATE INDEX IF NOT EXISTS idx_ai_model_config_is_enabled ON ai_model_config(is_enabled);
CREATE INDEX IF NOT EXISTS idx_ai_model_config_tenant_id ON ai_model_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_config_del_flag ON ai_model_config(del_flag);
