package com.bigbear.quarkus.seata.config;

/**
 * Seata 配置常量：本扩展所有 {@code seata.*} 配置键与 gRPC XID 透传头的统一定义。
 *
 * <p>配置键均为裸前缀（非 {@code seata.*}），由 {@link SeataConfigProvider} 将 Seata 内部
 * 读取的裸 key 桥接为 {@code seata.*}，从而彻底摆脱 {@code registry.conf}/{@code file.conf}
 * 在 Quarkus ClassLoader 下不可发现的痛点。
 *
 * @author xxs-micro
 */
public final class SeataConfigConstants {

    private SeataConfigConstants() {
    }

    /** 配置前缀 */
    public static final String SEATA_PREFIX = "seata";

    /** 是否启用 Seata（TM/RM 初始化与 XA 数据源代理开关） */
    public static final String SEATA_ENABLED = SEATA_PREFIX + ".enabled";

    /** 应用标识（缺省取 quarkus.application.name） */
    public static final String SEATA_APPLICATION_ID = SEATA_PREFIX + ".application-id";

    /** 事务分组（与 Spring 侧对齐：greeter-group） */
    public static final String SEATA_TX_SERVICE_GROUP = SEATA_PREFIX + ".tx-service-group";

    /** XA 数据源真实连接参数（SeataXADriver 据此构造 PGXADataSource） */
    public static final String SEATA_DATASOURCE_URL = SEATA_PREFIX + ".datasource.url";
    public static final String SEATA_DATASOURCE_USERNAME = SEATA_PREFIX + ".datasource.username";
    public static final String SEATA_DATASOURCE_PASSWORD = SEATA_PREFIX + ".datasource.password";

    /** gRPC 全局事务 XID 透传头（与 Spring 侧 SeataXidClientInterceptor 对齐） */
    public static final String TX_XID = "TX_XID";
}
