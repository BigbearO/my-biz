package org.dromara.goodfish.meta.constants;

import java.time.format.DateTimeFormatter;

/**
 * goodfish 客户端元数据服务常量（账号域 + 监听租约）.
 *
 * <p>遵守 AGENTS.md：禁止魔法数字/字符串，业务常量统一收敛于此.</p>
 */
public final class GoodfishMetaConstants {

    private GoodfishMetaConstants() {
    }

    // ==================== 账号状态 ====================

    /** 账号状态：正常. */
    public static final String ACCOUNT_STATUS_NORMAL = "normal";

    /** 账号状态：停用（★只停用、不删除）. */
    public static final String ACCOUNT_STATUS_DISABLED = "disabled";

    // ==================== 监听租约（Redis）====================

    /** 监听租约 Redis key 前缀，完整 key = 前缀 + goodfish_id. */
    public static final String LISTEN_LEASE_KEY_PREFIX = "goodfish:listen:";

    /** 租约 TTL（秒）：客户端崩溃后由此自动兜底释放. */
    public static final int LISTEN_LEASE_TTL_SECONDS = 90;

    /** 客户端续期间隔（秒），由响应下发供客户端参考. */
    public static final int LISTEN_LEASE_RENEW_INTERVAL_SECONDS = 30;

    // ==================== 业务错误码 ====================

    /** 账号不存在. */
    public static final int CODE_ACCOUNT_NOT_FOUND = 40401;

    /** 监听租约冲突：账号已被其他设备监听. */
    public static final int CODE_LEASE_CONFLICT = 40901;

    // ==================== 错误消息 ====================

    /** 账号不存在. */
    public static final String MSG_ACCOUNT_NOT_FOUND = "账号不存在";

    /** 监听租约冲突（已被其他设备占用）. */
    public static final String MSG_LEASE_CONFLICT = "该闲鱼账号已被其他设备监听";

    /** 非租约持有者执行续期/释放. */
    public static final String MSG_LEASE_NOT_HOLDER = "无权操作该闲鱼账号的监听租约";

    // ==================== 日志名称 ====================

    /** 账号业务日志名. */
    public static final String LOGGER_ACCOUNT_SERVICE = "goodfish_account/service/client-meta-service";

    /** 监听租约业务日志名. */
    public static final String LOGGER_LISTEN_LEASE_SERVICE = "goodfish_listen_lease/service/client-meta-service";

    /** 账号域 gRPC 日志名. */
    public static final String LOGGER_ACCOUNT_GRPC = "goodfish_account/grpc/client-meta-service";

    // ==================== 时间格式 ====================

    /** 跨语言统一时间格式（契约 last_active_at 为字符串）. */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== gRPC 分页 ====================

    /** gRPC 分页默认每页条数. */
    public static final int GRPC_DEFAULT_PAGE_SIZE = 10;

    /** gRPC 分页每页条数上限（防大页拖垮服务）. */
    public static final int GRPC_MAX_PAGE_SIZE = 100;

}
