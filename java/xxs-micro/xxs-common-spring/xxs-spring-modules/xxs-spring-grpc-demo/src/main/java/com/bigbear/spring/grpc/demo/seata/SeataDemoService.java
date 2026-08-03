package com.bigbear.spring.grpc.demo.seata;

import com.bigbear.spring.grpc.biz.BizClient;
import com.bigbear.spring.grpc.user.UserClient;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 分布式事务演示服务（Seata XA 发起方）。
 *
 * <p>链路：全局事务内先落库 {@code seata_java.biz_tbl}，再经 gRPC 调用 seata-go 分支
 * 写 {@code seata_go.user_tbl}、Quarkus 分支写 {@code seata_quarkus.biz_tbl}；
 * 任一端失败则整单回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeataDemoService {

    /** User 充血客户端：经 gRPC 调用 Go 提供方（seata-go XA 分支）。 */
    private final UserClient userClient;

    /** Biz 充血客户端：经 gRPC 调用 Quarkus 提供方（seata-quarkus XA 分支）。 */
    private final BizClient bizClient;

    /** 多数据源模板：路由到 {@code seata_java} 数据源。 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 在全局事务内创建用户（Java + Go + Quarkus 三端落库）。
     *
     * @param name 用户名
     * @param age  年龄
     * @return Go 端返回的新用户 id
     */
    @GlobalTransactional
    @DS("seata_java")
    public String createUser(String name, int age) {
        return doCreateUser(name, age);
    }

    /**
     * 三端落库后主动抛错，验证全局事务整单回滚（三端均无残留）。
     *
     * @param name 用户名
     * @param age  年龄
     * @throws RuntimeException 业务失败，触发 Seata XA 全局回滚
     */
    @GlobalTransactional
    @DS("seata_java")
    public String createUserThenFail(String name, int age) {
        String goId = doCreateUser(name, age);
        // 回滚演练：三端均已提交分支，但业务主动失败，需整单回滚
        throw new RuntimeException("rollback drill after 3-branch write");
    }

    /**
     * 三端落库核心：Java 写 seata_java.biz_tbl → Go 写 seata_go.user_tbl → Quarkus 写 seata_quarkus.biz_tbl。
     *
     * @param name 用户名
     * @param age  年龄
     * @return Go 端返回的新用户 id
     */
    private String doCreateUser(String name, int age) {
        // 1. Java 分支：写 seata_java.biz_tbl
        jdbcTemplate.update("INSERT INTO biz_tbl(name, amount) VALUES (?, ?)", name, 0);
        log.info("seata_java.biz_tbl 已写入: name={}", name);

        // 2. Go 分支：经 gRPC 透传 XID 调用 seata-go 写 seata_go.user_tbl
        long goId = userClient.createUser(name, age).getId();

        // 3. Quarkus 分支：经 gRPC 透传 XID 调用 template-provider 写 seata_quarkus.biz_tbl
        bizClient.createBiz(name, age);
        log.info("seata_quarkus.biz_tbl 已写入: name={}", name);

        return String.valueOf(goId);
    }
}
