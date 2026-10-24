-- ============================================================================
-- good_fish 业务库建表脚本（PostgreSQL）—— 全新空库一次性初始化
--
-- 库策略：业务独立库 good_fish，不含任何 RuoYi 平台表（sys_* / gen_* 等在另一个库，
--         用 RuoYi-Cloud-Plus/script/sql/postgres/postgres_ry_cloud.sql 初始化）
--
-- 主键统一口径：应用侧雪花 ID
--   - 类型 bigint，无默认值、无序列、无 identity
--   - Java（MyBatis-Plus）：@TableId(type = IdType.ASSIGN_ID)，字段 Long
--   - Go（llm-manage）：idgen 包生成 + gorm autoIncrement:false
--   - check (id > 0)：生成器失效插入 0 时立即报错，而不是变成脏数据
--   - 例外：goodfish_item 的批量同步走原生 CTE（需「软删复活」+ 插入/更新计数），
--           id 由代码显式传入；goodfish_account / goodfish_item_prompt 的写入
--           全部走 MyBatis-Plus insert / updateById，不存在隐式 upsert
--
-- 执行库：good_fish
-- ============================================================================

drop table if exists goodfish_item_prompt cascade;
drop table if exists goodfish_item        cascade;
drop table if exists goodfish_account     cascade;
drop table if exists llm_model            cascade;
drop table if exists llm_provider         cascade;
drop table if exists client_version       cascade;

-- ----------------------------
-- 表：goodfish_account（goodfish-client-meta-service）
-- ----------------------------
create table goodfish_account (
    id                 bigint       not null,
    goodfish_id        varchar(64)  not null,
    goodfish_nickname  varchar(128) default null,
    remark             varchar(256) default null,
    status             varchar(16)  not null default 'normal',
    last_active_at     timestamp    default null,
    create_dept        bigint       default null,
    create_by          bigint       default null,
    create_time        timestamp    default null,
    update_by          bigint       default null,
    update_time        timestamp    default null,
    constraint pk_goodfish_account primary key (id),
    constraint chk_goodfish_account_id_positive check (id > 0)
);
comment on table  goodfish_account                   is '闲鱼账号表（云端不存储 Cookie，只停用不删除）';
comment on column goodfish_account.id                is '主键，应用侧雪花 ID';
comment on column goodfish_account.goodfish_id       is '闲鱼用户 ID，唯一索引';
comment on column goodfish_account.goodfish_nickname is '闲鱼昵称';
comment on column goodfish_account.remark            is '备注（运营视角的账号名）';
comment on column goodfish_account.status            is '账号状态：normal / disabled（★只停用、不删除）';
comment on column goodfish_account.last_active_at    is '最后活跃时间（客户端上报时维护）';
comment on column goodfish_account.create_dept       is '创建部门';
comment on column goodfish_account.create_by         is '创建者';
comment on column goodfish_account.create_time       is '创建时间';
comment on column goodfish_account.update_by         is '更新者';
comment on column goodfish_account.update_time       is '更新时间';
-- 唯一索引同时承担「按闲鱼 ID 判重」与「并发上报兜底」（撞键后退化为按主键更新）
create unique index uk_goodfish_account_goodfish_id on goodfish_account (goodfish_id);

-- ----------------------------
-- 表：goodfish_item（goodfish-item-service）
-- ----------------------------
create table goodfish_item (
    id                  bigint        not null,
    account_id          bigint        not null,
    goodfish_account_id varchar(64)   not null,
    goodfish_item_id    varchar(64)   not null,
    title               varchar(256)  default null,
    description         text          default null,
    price               numeric(10,2) default null,
    stock_status        varchar(32)   default null,
    image_url           varchar(500)  default null,
    sync_time           timestamp     default null,
    del_flag            char(1)       not null default '0',
    create_dept         bigint        default null,
    create_by           bigint        default null,
    create_time         timestamp     default null,
    update_by           bigint        default null,
    update_time         timestamp     default null,
    constraint pk_goodfish_item primary key (id),
    constraint chk_goodfish_item_id_positive check (id > 0)
);
comment on table  goodfish_item                     is '商品表（客户端同步上报，只增不删、软删可复活）';
comment on column goodfish_item.id                  is '主键，应用侧雪花 ID';
comment on column goodfish_item.account_id          is '关联 goodfish_account.id（同步时经 gRPC GetAccountByGoodfishId 解析）';
comment on column goodfish_item.goodfish_account_id is '冗余字段：闲鱼账号 ID——对话链路按此字段直查，不做跨服务解析';
comment on column goodfish_item.goodfish_item_id    is '闲鱼商品 ID';
comment on column goodfish_item.title               is '商品标题';
comment on column goodfish_item.description         is '商品描述';
comment on column goodfish_item.price               is '价格';
comment on column goodfish_item.stock_status        is '库存状态（只读，从同步来）';
comment on column goodfish_item.image_url           is '商品主图（本期不同步，字段保留、暂不写入）';
comment on column goodfish_item.sync_time           is '最后同步时间';
comment on column goodfish_item.del_flag            is '删除标志（0存在 1删除；软删，闲鱼侧仍在的商品再次同步会复活）';
comment on column goodfish_item.create_dept         is '创建部门';
comment on column goodfish_item.create_by           is '创建者';
comment on column goodfish_item.create_time         is '创建时间';
comment on column goodfish_item.update_by           is '更新者';
comment on column goodfish_item.update_time         is '更新时间';
-- 唯一键不能写成 WHERE del_flag='0' 的部分索引：同步靠它命中软删行并把 del_flag 置回 0（复活）
create unique index uk_gfid_item                 on goodfish_item (goodfish_account_id, goodfish_item_id);
-- 后台按账号主键聚合；按闲鱼账号 ID 查询走 uk_gfid_item 左前缀
create index        idx_goodfish_item_account_id on goodfish_item (account_id);

-- ----------------------------
-- 表：goodfish_item_prompt（goodfish-item-service）
-- ----------------------------
create table goodfish_item_prompt (
    id               bigint      not null,
    item_id          bigint      not null,
    system_prompt    text        default null,
    price_prompt     text        default null,
    tech_prompt      text        default null,
    default_prompt   text        default null,
    max_reply_length int4        not null default 50,
    create_dept      bigint      default null,
    create_by        bigint      default null,
    create_time      timestamp   default null,
    update_by        bigint      default null,
    update_time      timestamp   default null,
    constraint pk_goodfish_item_prompt primary key (id),
    constraint chk_goodfish_item_prompt_id_positive check (id > 0)
);
comment on table  goodfish_item_prompt                  is '商品提示词配置（与 goodfish_item 一对一）';
comment on column goodfish_item_prompt.id               is '主键，应用侧雪花 ID';
comment on column goodfish_item_prompt.item_id          is '关联 goodfish_item.id，唯一索引（一对一）';
comment on column goodfish_item_prompt.system_prompt    is '系统提示词（人设 + 核心原则）';
comment on column goodfish_item_prompt.price_prompt     is '议价场景提示词（本期页面不提供输入，字段保留）';
comment on column goodfish_item_prompt.tech_prompt      is '技术场景提示词（本期页面不提供输入，字段保留）';
comment on column goodfish_item_prompt.default_prompt   is '默认场景提示词（本期页面不提供输入，字段保留）';
comment on column goodfish_item_prompt.max_reply_length is '最大回复字数，默认 50';
comment on column goodfish_item_prompt.create_dept      is '创建部门';
comment on column goodfish_item_prompt.create_by        is '创建者';
comment on column goodfish_item_prompt.create_time      is '创建时间';
comment on column goodfish_item_prompt.update_by        is '更新者';
comment on column goodfish_item_prompt.update_time      is '更新时间';
-- 一对一约束，同时是并发保存提示词时的兜底（撞键后退化为按主键更新）
create unique index uk_goodfish_item_prompt_item_id on goodfish_item_prompt (item_id);

-- ----------------------------
-- 表：llm_provider（llm-manage，Go）
-- 注意：llm-manage 没有 AutoMigrate，表只能由本脚本创建
-- ----------------------------
create table llm_provider (
    id         bigint        not null,
    name       varchar(64)   not null,
    base_url   varchar(256)  not null,
    api_key    varchar(256)  not null,
    status     int4          not null default 1,
    created_at timestamptz   default null,
    updated_at timestamptz   default null,
    deleted_at timestamptz   default null,
    constraint pk_llm_provider primary key (id),
    constraint chk_llm_provider_id_positive check (id > 0)
);
comment on table  llm_provider          is '大模型供应商表';
comment on column llm_provider.id       is '主键，应用侧雪花 ID';
comment on column llm_provider.name     is '供应商名称';
comment on column llm_provider.base_url is '接口地址（OpenAI 兼容）';
comment on column llm_provider.api_key  is 'API 密钥（AES-256-GCM 加密存储）';
comment on column llm_provider.status   is '状态：1=启用，0=停用';
create index idx_llm_provider_name       on llm_provider (name);
create index idx_llm_provider_status     on llm_provider (status);
create index idx_llm_provider_deleted_at on llm_provider (deleted_at);

-- ----------------------------
-- 表：llm_model（llm-manage，Go）
-- ----------------------------
create table llm_model (
    id           bigint           not null,
    provider_id  bigint           not null,
    display_name varchar(64)      not null,
    model_name   varchar(64)      not null,
    temperature  double precision not null default 0.7,
    max_tokens   int4             not null default 2048,
    status       int4             not null default 0,
    created_at   timestamptz      default null,
    updated_at   timestamptz      default null,
    deleted_at   timestamptz      default null,
    constraint pk_llm_model primary key (id),
    constraint chk_llm_model_id_positive check (id > 0)
);
comment on table  llm_model              is '大模型表';
comment on column llm_model.id           is '主键，应用侧雪花 ID';
comment on column llm_model.provider_id  is '关联 llm_provider.id';
comment on column llm_model.display_name is '模型显示名称（如 GPT-4o）';
comment on column llm_model.model_name   is '实际模型名（如 gpt-4o）';
comment on column llm_model.temperature  is '温度参数，默认 0.7';
comment on column llm_model.max_tokens   is '最大输出 token，默认 2048';
comment on column llm_model.status       is '状态：1=启用，0=停用（全局唯一启用，应用层事务保证）';
create index idx_llm_model_provider_id on llm_model (provider_id);
create index idx_llm_model_status      on llm_model (status);
create index idx_llm_model_deleted_at  on llm_model (deleted_at);

-- ----------------------------
-- 表：client_version（预留，本期代码未实现，建表不影响）
-- ----------------------------
create table client_version (
    id            bigint       not null,
    version       varchar(32)  not null,
    release_notes text         default null,
    force_update  boolean      not null default false,
    download_url  varchar(256) default null,
    file_size     bigint       default null,
    sha512        varchar(128) default null,
    create_dept   bigint       default null,
    create_by     bigint       default null,
    create_time   timestamp    default null,
    update_by     bigint       default null,
    update_time   timestamp    default null,
    constraint pk_client_version primary key (id),
    constraint chk_client_version_id_positive check (id > 0)
);
comment on table  client_version            is '客户端版本表（预留，本期未实现）';
comment on column client_version.id         is '主键，应用侧雪花 ID';
comment on column client_version.version    is '版本号，唯一索引';
comment on column client_version.sha512     is '安装包 SHA-512 校验值';
create unique index uk_client_version_version on client_version (version);

-- ============================================================================
-- 说明：goodfish-ai-service 的三张表（ai_conversation / ai_conversation_message /
-- ai_reply_log）由该服务自己的 alembic 迁移创建，不在本脚本内。
-- ============================================================================
