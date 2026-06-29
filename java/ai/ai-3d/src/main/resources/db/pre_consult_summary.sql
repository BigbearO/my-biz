-- pre_consult_summary: 诊前助手病情摘要表
CREATE TABLE IF NOT EXISTS pre_consult_summary (
    id               BIGINT PRIMARY KEY,
    chat_id          VARCHAR(64) NOT NULL,
    chief_complaint  TEXT,
    present_illness  TEXT,
    past_history     TEXT,
    medication       TEXT,
    allergy          TEXT,
    visit_goal       TEXT,
    raw_summary      TEXT,
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_summary_chat_id ON pre_consult_summary(chat_id);
