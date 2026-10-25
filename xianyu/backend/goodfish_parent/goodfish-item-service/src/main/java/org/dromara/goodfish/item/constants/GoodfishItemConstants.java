package org.dromara.goodfish.item.constants;

import java.time.Duration;

/**
 * goodfish 商品服务常量（商品 + 提示词 + 运行时缓存）.
 *
 * <p>遵守 AGENTS.md：禁止魔法数字/字符串，业务常量统一收敛于此.</p>
 */
public final class GoodfishItemConstants {

    private GoodfishItemConstants() {
    }

    // ==================== 账号状态（与客户端元数据服务同源，gRPC 返回值口径） ====================

    /** 账号状态：正常. */
    public static final String ACCOUNT_STATUS_NORMAL = "normal";

    /** 账号状态：停用（同步拒绝，40404）. */
    public static final String ACCOUNT_STATUS_DISABLED = "disabled";

    // ==================== 业务错误码 ====================

    /** 商品不存在. */
    public static final int CODE_ITEM_NOT_FOUND = 40401;

    /** 账号不存在（同步时按 goodfish_id 解析不到云端账号：从未上报 / 上报失败）. */
    public static final int CODE_ACCOUNT_NOT_FOUND = 40403;

    /** 账号已停用（同步被拒绝）. */
    public static final int CODE_ACCOUNT_DISABLED = 40404;

    // ==================== 错误消息 ====================

    /** 商品不存在. */
    public static final String MSG_ITEM_NOT_FOUND = "商品不存在";

    /** 账号不存在（客户端提示：账号不存在，请联系管理员）. */
    public static final String MSG_ACCOUNT_NOT_FOUND = "账号不存在，请联系管理员";

    /** 账号已停用（客户端提示：账号已停用，请联系管理员）. */
    public static final String MSG_ACCOUNT_DISABLED = "账号已停用，请联系管理员";

    /** 账号服务暂时不可用（gRPC 调用失败，区别于 40403 的账号不存在）. */
    public static final String MSG_ACCOUNT_SERVICE_UNAVAILABLE = "账号服务暂时不可用，请稍后重试";

    // ==================== 运行时配置缓存（Redis，提供方负责） ====================

    /** 运行时配置缓存 key 前缀，完整 key = 前缀 + 闲鱼账号ID + ":" + 闲鱼商品ID. */
    public static final String ITEM_RUNTIME_CACHE_KEY_PREFIX = "goodfish:item:runtime:";

    /** 缓存 TTL：60s 兜底（变更时主动删除，多实例共享 Redis 任意实例删除即全局生效）. */
    public static final Duration ITEM_RUNTIME_CACHE_TTL = Duration.ofSeconds(60);

    // ==================== 逻辑删除标记 ====================

    /** 未删除. */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 已删除（软删；同步命中时置回 0 复活）. */
    public static final String DEL_FLAG_DELETED = "1";

    // ==================== 提示词默认值 ====================

    /** 最大回复字数默认值（与建表脚本默认值一致）. */
    public static final int DEFAULT_MAX_REPLY_LENGTH = 50;

    // ==================== 入参校验上限（与表结构长度保持一致） ====================

    /** 单次同步商品数上限（防超大事务）. */
    public static final int SYNC_MAX_ITEMS = 500;

    /** 系统提示词最大长度. */
    public static final int PROMPT_SYSTEM_MAX_LENGTH = 20000;

    /** 最大回复字数下界. */
    public static final int MAX_REPLY_LENGTH_MIN = 1;

    /** 最大回复字数上界. */
    public static final int MAX_REPLY_LENGTH_MAX = 2000;

    /** 商品标题最大长度（对应 varchar(256)）. */
    public static final int TITLE_MAX_LENGTH = 256;

    /** 商品描述最大长度. */
    public static final int DESCRIPTION_MAX_LENGTH = 5000;

    /** 图片地址最大长度（对应 varchar(500)）. */
    public static final int IMAGE_URL_MAX_LENGTH = 500;

    /** 闲鱼 ID 类字段最大长度（对应 varchar(64)）. */
    public static final int GOODFISH_ID_MAX_LENGTH = 64;

    /** 价格格式（最多两位小数，字符串透传避免精度问题）. */
    public static final String PRICE_PATTERN = "^\\d+(\\.\\d{1,2})?$";

    /** 价格格式错误提示. */
    public static final String MSG_PRICE_INVALID = "价格格式不正确";

    // ==================== 日志名称 ====================

    /** 商品业务日志名. */
    public static final String LOGGER_ITEM_SERVICE = "goodfish_item/service/item-service";

    /** 商品域 gRPC 日志名. */
    public static final String LOGGER_ITEM_GRPC = "goodfish_item/grpc/item-service";

    // ==================== gRPC 分页 ====================

    /** gRPC 分页默认每页条数. */
    public static final int GRPC_DEFAULT_PAGE_SIZE = 10;

    /** gRPC 分页每页条数上限（防大页拖垮服务）. */
    public static final int GRPC_MAX_PAGE_SIZE = 100;

    // ==================== 时间格式 ====================

    /** 跨语言统一时间格式（契约 last_sync_at 为字符串）. */
    public static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

}
