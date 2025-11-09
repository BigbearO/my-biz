package com.bigbear.goofish.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.goofish.app.UserWebSocketService;
import com.bigbear.goofish.domain.bo.AccountBo;
import com.bigbear.goofish.domain.entity.AccountEntity;
import com.bigbear.goofish.domain.vo.AccountVo;
import com.bigbear.goofish.infra.AccountManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bigbear.goofish.core.Account;
import com.bigbear.goofish.core.AccountService;
import com.bigbear.goofish.core.ConnectionProperties;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bigbear.goofish.core.socket.GoofishSocket;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 账户管理API控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/account")

public class AccountController {

    private final AccountService accountService;

    private final AccountManager accountManager;

    private final UserWebSocketService webSocketManager;

    /**
     * 查询账号列表
     */
//    @SaCheckPermission("ai:account:list")
    @GetMapping("/list")
    public TableDataInfo<AccountEntity> list(AccountBo bo, PageQuery pageQuery) {
        return accountService.queryPageList(bo, pageQuery);
    }

    /**
     * 注册新用户
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<UserResponse>> registerUser(@RequestBody UserRegistrationRequest request) {

        String userId = request.getUserId();

        // 将userId声明为final以便在lambda中使用
        final String finalUserId = userId;

        try {
            // 使用UserWebSocketManager创建用户特定的Account对象
            Account account = webSocketManager.createUserAccount(request.getCookieStr());

            // 如果请求中提供了userId，使用请求中的userId，否则使用从cookie解析的userId
            if (userId == null || userId.trim().isEmpty()) {
                userId = generateUserId();
            }
            // 设置用户ID到Account中
            if (account != null) {
                account.setUserId(userId);

                // 设置其他属性（如果提供）
                if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
                    account.setDeviceId(request.getDeviceId());
                }
                if (request.getCsrfToken() != null) {
                    account.setCsrfToken(request.getCsrfToken());
                }
                if (request.getCookie2() != null) {
                    account.setCookie2(request.getCookie2());
                }
                if (request.getCna() != null) {
                    account.setCna(request.getCna());
                }

                // 注册账户到AccountService
                accountService.registerAccount(account);
            }

            // 检查用户是否已存在
            if (accountService.getAccount(finalUserId) != null) {
                log.warn("用户 {} 已经注册", finalUserId);
                return Mono.just(ResponseEntity.badRequest().body(
                        new UserResponse(finalUserId, false, "用户已存在")
                ));
            }

            // 获取系统配置
            ConnectionProperties properties = accountService.getSystemProperties();

            // 先建立WebSocket连接
            return webSocketManager.createWebSocketConnection(properties, account)
                    .then(Mono.defer(() -> {
                        UserResponse response = new UserResponse(
                                finalUserId,
                                true,
                                "注册成功并已连接"
                        );
                        return Mono.just(ResponseEntity.ok(response));
                    }))
                    .onErrorResume(error -> {
                        log.error("注册用户失败", error);
                        return Mono.just(ResponseEntity.badRequest().body(
                                new UserResponse(finalUserId, false, "注册失败: " + error.getMessage())
                        ));
                    });
        } catch (Exception e) {
            log.error("处理用户注册请求时发生异常", e);
            return Mono.just(ResponseEntity.badRequest().body(
                    new UserResponse(finalUserId, false, "请求处理失败: " + e.getMessage())
            ));
        }
    }

    /**
     * 生成用户ID
     */
    private String generateUserId() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取所有用户状态
     */
    @GetMapping
    public Mono<ResponseEntity<Map<String, UserStatus>>> getAllUsers() {
        // 从UserWebSocketManager获取所有已连接的用户
        Map<String, UserStatus> statusMap = new HashMap<>();

        // 获取UserWebSocketManager中的所有用户连接信息
        Map<String, GoofishSocket> sockets = webSocketManager.getAllUserSockets();
        for (Map.Entry<String, GoofishSocket> entry : sockets.entrySet()) {
            String userId = entry.getKey();
            GoofishSocket socket = entry.getValue();

            UserStatus status = new UserStatus(true, "connection-" + userId);
            status.setUserId(userId);
            statusMap.put(userId, status);
        }

        return Mono.just(ResponseEntity.ok(statusMap));
    }

    /**
     * 获取用户状态
     */
    @GetMapping("/status/{userId}")
    public Mono<ResponseEntity<UserStatus>> getUserStatus(@PathVariable String userId) {
        // 从WebSocketManager获取用户连接状态
         GoofishSocket socket = webSocketManager.getUserSocket(userId);
         if (socket != null) {
            UserStatus status = new UserStatus(true, "connection-" + userId);
            status.setUserId(userId);
            return Mono.just(ResponseEntity.ok(status));
        } else {
            return Mono.just(ResponseEntity.notFound().build());
        }
    }

    /**
     * 断开用户连接
     */
    @PostMapping("/disconnect/{userId}")
    public Mono<ResponseEntity<Map<String, Object>>> disconnectUser(@PathVariable String userId) {
        try {
            // 通过WebSocketManager断开用户连接
            webSocketManager.disconnectUser(userId);

            // 从AccountService中移除用户账户
            accountService.removeAccount(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("disconnected", true);
            return Mono.just(ResponseEntity.ok(response));
        } catch (Exception error) {
            log.error("断开用户连接失败: {}", error.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("userId", userId);
            errorResponse.put("disconnected", false);
            errorResponse.put("error", error.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
        }
    }

    /**
     * 获取所有用户状态（备用端点）
     */
    @GetMapping("/users")
    public Mono<ResponseEntity<Map<String, UserStatus>>> getAllUsersStatus() {
        // 复用getAllUsers方法的逻辑
        return getAllUsers();
    }

    // 请求和响应模型
    @Data
    static class UserRegistrationRequest {
        private String userId;
        private String cookieStr;
        private String csrfToken;
        private String cookie2;
        private String cna;
        private String deviceId;
    }

    @Data
    static class UserResponse {
        private String userId;
        private boolean connected;
        private String message;

        // 添加带参数的构造器
        public UserResponse(String userId, boolean connected, String message) {
            this.userId = userId;
            this.connected = connected;
            this.message = message;
        }
    }

    @Data
    static class UserStatus {
        private boolean connected;
        private String connectionId;
        private long lastActiveTime;
        private String userId; // 添加userId字段

        public UserStatus(boolean connected, String connectionId) {
            this.connected = connected;
            this.connectionId = connectionId;
            this.lastActiveTime = System.currentTimeMillis();
        }


    }
}
