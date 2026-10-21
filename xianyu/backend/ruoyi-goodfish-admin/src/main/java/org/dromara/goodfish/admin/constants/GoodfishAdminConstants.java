package org.dromara.goodfish.admin.constants;

import java.time.format.DateTimeFormatter;

/**
 * goodfish 管理后台 BFF 常量.
 *
 * <p>遵守 AGENTS.md：禁止魔法数字/字符串，业务常量统一收敛于此.</p>
 */
public final class GoodfishAdminConstants {

    private GoodfishAdminConstants() {
    }

    // ==================== 账号状态（与客户端元数据服务同源） ====================

    /** 账号状态：正常. */
    public static final String ACCOUNT_STATUS_NORMAL = "normal";

    /** 账号状态：停用. */
    public static final String ACCOUNT_STATUS_DISABLED = "disabled";

    // ==================== 账号全量拉取（聚合账号名用） ====================

    /** 账号全量拉取每页条数（账号总量小，循环拉全用于内存拼装）. */
    public static final int ACCOUNT_FETCH_PAGE_SIZE = 100;

    /** 账号全量拉取最大页数兜底（防异常分页导致死循环，100 页 = 1 万个账号）. */
    public static final int ACCOUNT_FETCH_MAX_PAGES = 100;

    // ==================== gRPC 分页 ====================

    /** gRPC 分页默认页码. */
    public static final int GRPC_DEFAULT_PAGE_NUM = 1;

    /** gRPC 分页默认每页条数. */
    public static final int GRPC_DEFAULT_PAGE_SIZE = 10;

    /** gRPC 分页每页条数上限（防大页拖垮下游服务）. */
    public static final int GRPC_MAX_PAGE_SIZE = 100;

    // ==================== 业务错误码（与基础服务保持一致） ====================

    /** 数据不存在（账号 / 商品）. */
    public static final int CODE_NOT_FOUND = 40401;

    // ==================== 错误消息 ====================

    /** 账号不存在. */
    public static final String MSG_ACCOUNT_NOT_FOUND = "账号不存在";

    /** 商品不存在. */
    public static final String MSG_ITEM_NOT_FOUND = "商品不存在";

    /** 账号服务描述（拼接「暂时不可用」提示）. */
    public static final String DESC_ACCOUNT_SERVICE = "账号服务";

    /** 商品服务描述（拼接「暂时不可用」提示）. */
    public static final String DESC_ITEM_SERVICE = "商品服务";

    /** 账号状态非法提示. */
    public static final String MSG_INVALID_ACCOUNT_STATUS = "账号状态仅支持 normal / disabled";

    // ==================== 日志名称 ====================

    /** 商品列表聚合日志名. */
    public static final String LOGGER_ADMIN_ITEM_SERVICE = "goodfish_admin_item/service/admin-bff";

    /** 账号停用转调日志名. */
    public static final String LOGGER_ADMIN_ACCOUNT_SERVICE = "goodfish_admin_account/service/admin-bff";

    /** 授权校验日志名. */
    public static final String LOGGER_AUTH_SERVICE = "goodfish_auth/service/admin-bff";

    // ==================== 时间格式 ====================

    /** 跨语言统一时间格式（契约时间字段为字符串，原样透传）.
     */
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

}
