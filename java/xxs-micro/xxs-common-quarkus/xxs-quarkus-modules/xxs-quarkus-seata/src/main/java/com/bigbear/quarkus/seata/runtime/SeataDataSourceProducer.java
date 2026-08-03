package com.bigbear.quarkus.seata.runtime;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

/**
 * Seata XA 数据源 CDI 生产者。
 *
 * <p>提供以 {@code seataDataSource} 命名的 {@link DataSource}，其每次 {@code getConnection()} 惰性委派给
 * {@link SeataXADriver#dataSourceProxy()}（首次请求时 RM 客户端必然已初始化）。{@link DataSourceProxyXA#getConnection()}
 * 每次调用都会基于当前线程 {@code RootContext} 的 XID 新建 {@code ConnectionProxyXA}，从而正确注册该全局事务的 XA 分支；
 * 切勿经 Agroal 连接池取用（代理连接被池化后 XID 固化在创建时刻，分支永不注册）。
 *
 * @author xxs-micro
 */
@ApplicationScoped
public class SeataDataSourceProducer {

    /**
     * 产出惰性的 Seata XA 数据源代理。
     *
     * @return XA 数据源代理
     */
    @Produces
    @ApplicationScoped
    @Named("seataDataSource")
    public DataSource seataDataSource() {
        return new SeataXaDataSource();
    }

    /**
     * 惰性 XA 数据源：所有连接操作均实时委派给全局 {@code DataSourceProxyXA}。
     *
     * <p>仅实现 {@link DataSource#getConnection()} 两条核心路径，其余未用方法以语义化异常兜底，保持内聚。
     */
    static final class SeataXaDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return SeataXADriver.dataSourceProxy().getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Unwrapping to " + iface + " is not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            throw new SQLFeatureNotSupportedException("getLogWriter not supported");
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            throw new SQLFeatureNotSupportedException("setLogWriter not supported");
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            throw new SQLFeatureNotSupportedException("setLoginTimeout not supported");
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getGlobal();
        }
    }
}
