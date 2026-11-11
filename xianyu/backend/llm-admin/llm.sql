CREATE TABLE llm_provider (
  id         BIGINT      NOT NULL PRIMARY KEY,
  name       VARCHAR(64)  NOT NULL DEFAULT '',
  base_url   VARCHAR(255) NOT NULL DEFAULT '',
  api_key    VARCHAR(255) NOT NULL DEFAULT '',
  status     SMALLINT     NOT NULL DEFAULT 1,
  created_at DATETIME    NOT NULL,
  updated_at DATETIME    NOT NULL
);

CREATE TABLE llm_model (
  id           BIGINT      NOT NULL PRIMARY KEY,
  provider_id  BIGINT      NOT NULL,
  display_name VARCHAR(64)  NOT NULL DEFAULT '',
  model_name   VARCHAR(128) NOT NULL DEFAULT '',
  temperature  FLOAT       NOT NULL DEFAULT 0.7,
  max_tokens   INT         NOT NULL DEFAULT 4096,
  status       SMALLINT    NOT NULL DEFAULT 0,
  created_at   DATETIME    NOT NULL,
  updated_at   DATETIME    NOT NULL
);
