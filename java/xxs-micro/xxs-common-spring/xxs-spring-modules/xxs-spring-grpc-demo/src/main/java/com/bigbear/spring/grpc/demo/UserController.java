package com.bigbear.spring.grpc.demo;

import com.bigbear.spring.grpc.user.UserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST 入口：演示「HTTP 入站 → 充血 UserClient → gRPC 调用 bear-admin」。
 *
 * <p>通道目标 {@code nacos://bear-admin} 由 {@code xxs-spring-grpc-nacos} 解析，
 * 经由 Nacos 服务发现定位 Go 提供方（bear-admin）gRPC 实例。
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class UserController {

    /** User 充血客户端（由 user-api 自动装配）。 */
    private final UserClient userClient;

    /**
     * 调用 gRPC UserService 查询用户基本信息。
     *
     * @param id 用户标识，默认 1
     * @return gRPC 服务返回的用户信息
     */
    @GetMapping("/user")
    public String user(@RequestParam(defaultValue = "1") String id) {

        log.info("get user by id:{}", id);
        return userClient.getUser(id).toString();
    }
}
