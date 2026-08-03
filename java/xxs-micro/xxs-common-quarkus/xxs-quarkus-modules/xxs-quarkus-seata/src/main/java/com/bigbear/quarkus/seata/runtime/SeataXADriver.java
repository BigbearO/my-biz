package com.bigbear.quarkus.seata.runtime;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.seata.rm.datasource.xa.DataSourceProxyXA;
import org.eclipse.microprofile.config.ConfigProvider;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.LoggerFactory;

import com.bigbear.quarkus.seata.config.SeataConfigConstants;

/**
 * Seata XA JDBC 驱动：承接 Quarkus {@code db-kind=seata} 的数据源连接供给。
 *
 * <p>连接真实来源为 {@link DataSourceProxyXA}（内部包装 {@link PGSimpleDataSource}，XA 资源由 Seata 的
 * {@code XAUtils} 基于 PostgreSQL 连接包装）：当线程上下文存在全局事务（{@code RootContext} 已绑定 XID，
 * 由 {@code SeataServerInterceptor} 完成）时，返回的连接代理会自动注册 XA 分支，使经此连接执行的 SQL
 * 纳入 Seata 全局事务；全局事务外则退化为普通直连。
 *
 * <p>通过 Quarkus {@code JdbcDriverBuildItem} 注册为 {@code seata} db-kind，并在 {@code META-INF/services/java.sql.Driver}
 * 声明以便 {@link DriverManager} 与 Agroal 连接池发现。
 *
 * @author xxs-micro
 */
public class SeataXADriver implements Driver {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SeataXADriver.class);

    /** 本驱动承接的 URL 前缀（配合 quarkus.datasource.jdbc.url=jdbc:seata:jdbc:postgresql://... 使用） */
    private static final String URL_PREFIX = "jdbc:seata:";

    /** 真实 PostgreSQL 驱动前缀 */
    private static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";

    /** 懒加载的 XA 数据源代理（全局单例，线程安全） */
    private static volatile DataSourceProxyXA dataSourceProxy;

    static {
        try {
            DriverManager.registerDriver(new SeataXADriver());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to register SeataXADriver", e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        return dataSourceProxy().getConnection();
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getGlobal();
    }

    /**
     * 懒初始化并返回全局唯一的 XA 数据源代理。
     *
     * <p>注意：{@link DataSourceProxyXA} 构造时会向默认资源管理器注册自身，因此必须在 RM 客户端初始化
     * （{@code SeataRuntime} 启动钩子）之后才首次构造；首次 connect 发生在请求期，天然满足先后关系。
     *
     * <p>{@link DataSourceProxyXA#getConnection()} 每次调用都会基于当前 {@code RootContext} 的 XID 新建
     * {@code ConnectionProxyXA}，因此必须直接经它取连接（而不是让 Agroal 池化代理连接，否则 XID 在代理创建
     * 时被固化、分支永不注册）。
     *
     * @return XA 数据源代理
     */
    public static DataSourceProxyXA dataSourceProxy() {
        if (dataSourceProxy == null) {
            synchronized (SeataXADriver.class) {
                if (dataSourceProxy == null) {
                    dataSourceProxy = new DataSourceProxyXA(buildRealDataSource());
                    LOGGER.info("SeataXADriver dataSourceProxy initialized");
                }
            }
        }
        return dataSourceProxy;
    }

    /**
     * 依据 {@code seata.datasource.*} 配置构造真实 PostgreSQL 数据源。
     *
     * @return PostgreSQL 数据源
     */
    private static PGSimpleDataSource buildRealDataSource() {
        var config = ConfigProvider.getConfig();
        String url = config.getOptionalValue(SeataConfigConstants.SEATA_DATASOURCE_URL, String.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required config: " + SeataConfigConstants.SEATA_DATASOURCE_URL));
        String username = config.getOptionalValue(SeataConfigConstants.SEATA_DATASOURCE_USERNAME, String.class).orElse("");
        String password = config.getOptionalValue(SeataConfigConstants.SEATA_DATASOURCE_PASSWORD, String.class).orElse("");

        PGSimpleDataSource pgDataSource = new PGSimpleDataSource();
        pgDataSource.setUrl(url.startsWith(POSTGRES_URL_PREFIX) ? url : POSTGRES_URL_PREFIX + url);
        pgDataSource.setUser(username);
        pgDataSource.setPassword(password);
        return pgDataSource;
    }
}
