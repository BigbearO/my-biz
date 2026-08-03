package com.bigbear.quarkus.template.provider;

import com.bigbear.quarkus.seata.runtime.SeataXidContext;
import com.bigbear.quarkus.template.grpc.BizService;
import com.bigbear.quarkus.template.grpc.CreateBizReply;
import com.bigbear.quarkus.template.grpc.CreateBizRequest;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

import jakarta.inject.Named;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * BizService 实现：Quarkus 作为 Seata XA 并列分支，在全局事务内写入本地 {@code biz_tbl}。
 *
 * <p>XID 由 {@code SeataServerInterceptor} 从入站 {@code TX_XID} 头写入 gRPC Context；本方法在真正执行
 * DB 写前用 {@link SeataXidContext#runWithXid} 在当前执行线程上重绑 {@code RootContext}，
 * 使经 {@code seataDataSource}（{@code DataSourceProxyXA}）每次 {@code getConnection} 新建的连接代理
 * 自动注册为该全局事务的 XA 分支。
 *
 * <p>无事务调用（未携带 XID）退化为普通本地写，不产生分支副作用。
 *
 * @author xxs-micro
 */
@Slf4j
@GrpcService
public class BizServiceImpl implements BizService {

    /** Seata XA 数据源（每次 getConnection 按当前 XID 新建代理连接，勿经 Agroal 池化）。 */
    private final DataSource dataSource;

    /**
     * 构造 Biz 服务实现。
     *
     * @param dataSource Seata XA 代理数据源（由 {@code SeataDataSourceProducer} 提供）
     */
    public BizServiceImpl(@Named("seataDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<CreateBizReply> createBiz(CreateBizRequest request) {
        String xid = SeataXidContext.get();
        return Uni.createFrom().item(() -> SeataXidContext.runWithXid(xid, () -> {
            insert(request);
            return CreateBizReply.newBuilder().setSuccess(true).build();
        }));
    }

    /**
     * 向本地 {@code biz_tbl} 写入业务记录。
     *
     * @param request 请求
     */
    private void insert(CreateBizRequest request) {
        String sql = "INSERT INTO biz_tbl(name, val) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.getName());
            ps.setInt(2, request.getVal());
            ps.executeUpdate();
            log.info("biz_tbl inserted, name={}, val={}", request.getName(), request.getVal());
        } catch (SQLException e) {
            // 抛异常使 gRPC 调用失败，发起方全局事务整体回滚
            throw new IllegalStateException("createBiz failed: " + request.getName(), e);
        }
    }
}
