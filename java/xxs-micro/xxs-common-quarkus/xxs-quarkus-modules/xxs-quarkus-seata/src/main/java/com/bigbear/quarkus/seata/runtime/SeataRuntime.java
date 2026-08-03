package com.bigbear.quarkus.seata.runtime;

import java.util.Optional;

import org.apache.seata.core.rpc.netty.RmNettyRemotingClient;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.rm.RMClient;
import org.apache.seata.tm.TMClient;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.LoggerFactory;

import com.bigbear.quarkus.seata.config.SeataConfigConstants;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Seata 运行时引导：应用启动时按配置初始化 TM / RM 客户端，使其连上 Transaction Coordinator（TC），
 * 支撑全局事务参与（分支）与事务协调（发起 / 回滚）能力。
 *
 * <p>仅当 {@code seata.enabled=true} 时执行；RM 客户端必须先于 {@link SeataXADriver} 首次构建
 * {@code DataSourceProxyXA} 完成初始化，以保证资源正确注册（首次取连接发生在请求期，天然满足）。
 *
 * @author xxs-micro
 */
@ApplicationScoped
@Startup
public class SeataRuntime {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SeataRuntime.class);

    private boolean enabled;

    private String applicationId;

    private String txServiceGroup;

    private RmNettyRemotingClient rmClient;

    private TmNettyRemotingClient tmClient;

    /**
     * 读取配置并初始化 Seata 客户端。
     */
    @PostConstruct
    void init() {
        var config = ConfigProvider.getConfig();
        this.enabled = config.getOptionalValue(SeataConfigConstants.SEATA_ENABLED, Boolean.class).orElse(false);
        if (!enabled) {
            LOGGER.info("Seata is disabled, skip client initialization");
            return;
        }
        this.applicationId = config.getOptionalValue(SeataConfigConstants.SEATA_APPLICATION_ID, String.class)
                .or(() -> config.getOptionalValue("quarkus.application.name", String.class))
                .orElse("quarkus-app");
        this.txServiceGroup = config.getOptionalValue(SeataConfigConstants.SEATA_TX_SERVICE_GROUP, String.class)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required config: " + SeataConfigConstants.SEATA_TX_SERVICE_GROUP));

        // RM 必须先完成标准初始化（RMClient.init 内部会 setResourceManager + setTransactionMessageHandler，
        // 并建立 RM 通道注册到 TC），否则后续 DataSourceProxyXA 注册资源时 resourceManager 为 null 无法连 TC。
        RMClient.init(applicationId, txServiceGroup);
        this.rmClient = RmNettyRemotingClient.getInstance(applicationId, txServiceGroup);
        TMClient.init(applicationId, txServiceGroup);
        this.tmClient = TmNettyRemotingClient.getInstance(applicationId, txServiceGroup);
        LOGGER.info("Seata clients initialized, applicationId=[{}], txServiceGroup=[{}]", applicationId, txServiceGroup);
    }

    /**
     * 应用退出时释放客户端资源。
     */
    @PreDestroy
    void destroy() {
        if (tmClient != null) {
            tmClient.destroy();
        }
        if (rmClient != null) {
            rmClient.destroy();
        }
    }

    /**
     * Seata 是否启用。
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 事务分组。
     *
     * @return 事务分组
     */
    public Optional<String> getTxServiceGroup() {
        return Optional.ofNullable(txServiceGroup);
    }
}
