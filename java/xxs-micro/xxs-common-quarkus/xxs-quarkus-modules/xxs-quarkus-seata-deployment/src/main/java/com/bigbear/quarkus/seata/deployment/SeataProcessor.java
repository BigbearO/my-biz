package com.bigbear.quarkus.seata.deployment;

import com.bigbear.quarkus.seata.runtime.SeataXADriver;

import io.quarkus.agroal.spi.JdbcDriverBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

/**
 * Seata Quarkus 扩展构建步骤。
 *
 * <p>将 {@code db-kind=seata} 映射到 {@link SeataXADriver}，使 Quarkus 数据源
 * （{@code quarkus.datasource.db-kind=seata}）经由 Seata XA 数据源代理供给连接；同时为原生镜像
 * 注册 {@code java.sql.Driver} 服务提供者。
 *
 * @author xxs-micro
 */
public class SeataProcessor {

    /** 扩展功能名 */
    private static final String FEATURE = "seata";

    /**
     * 声明扩展功能。
     *
     * @return 功能构建项
     */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * 注册 {@code db-kind=seata} 对应的 JDBC 驱动。
     *
     * @return JDBC 驱动构建项
     */
    @BuildStep
    JdbcDriverBuildItem registerJdbcDriver() {
        return new JdbcDriverBuildItem("seata", SeataXADriver.class.getName());
    }

    /**
     * 注册原生镜像的 {@code java.sql.Driver} 服务提供者。
     *
     * @return 服务提供者构建项
     */
    @BuildStep
    ServiceProviderBuildItem registerDriverService() {
        return new ServiceProviderBuildItem("java.sql.Driver", SeataXADriver.class.getName());
    }
}
