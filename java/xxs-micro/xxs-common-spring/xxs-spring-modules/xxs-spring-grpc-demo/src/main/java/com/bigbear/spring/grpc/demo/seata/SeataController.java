package com.bigbear.spring.grpc.demo.seata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分布式事务演示入口。
 *
 * <p>HTTP 入站 → {@link SeataDemoService#createUser}（全局事务发起）→ gRPC 调用 seata-go 分支。
 */
@Slf4j
@RestController
@RequestMapping("/demo/seata")
@RequiredArgsConstructor
public class SeataController {

    /** 全局事务演示服务。 */
    private final SeataDemoService seataDemoService;

    /**
     * 跨端创建用户（Java + Go 双端同时落库，任一失败整体回滚）。
     *
     * @param name 用户名
     * @param age  年龄
     * @return Go 端返回的新用户 id
     */
    @PostMapping("/create-user")
    public String createUser(@RequestParam String name, @RequestParam int age) {
        log.info("seata demo create user: name={}, age={}", name, age);
        return "created user id: " + seataDemoService.createUser(name, age);
    }

    /**
     * 三端落库后主动抛错：验证 Seata XA 整单回滚（成功/回滚各三表无残留）。
     *
     * @param name 用户名
     * @param age  年龄
     * @return 失败信息（正常不会走到）
     */
    @PostMapping("/create-user-fail")
    public String createUserFail(@RequestParam String name, @RequestParam int age) {
        log.info("seata demo create user (rollback drill): name={}, age={}", name, age);
        try {
            seataDemoService.createUserThenFail(name, age);
        } catch (RuntimeException e) {
            return "rolled back: " + e.getMessage();
        }
        return "unexpected success";
    }
}
