package com.bigbear.goodfish.api;

import cn.hutool.json.JSONUtil;
import com.bigbear.goodfish.app.UserWebSocketService;
import com.bigbear.goodfish.core.AccountService;
import com.bigbear.goodfish.core.socket.GoofishSocket;
import com.bigbear.goodfish.core.socket.msg.MsgDispatcher;
import com.bigbear.goodfish.domain.Account;
import com.bigbear.goodfish.domain.bo.AccountBo;
import com.bigbear.goodfish.domain.entity.AccountEntity;
import com.bigbear.goodfish.infra.AccountManager;
import com.bigbear.goodfish.utils.JsonUtils;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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


    @GetMapping("/test")
    public String test() {
        String s= """
             {"identity":258362929,"needReply":false,"redReminder":"","userId":"","userUrl":"https://www.goofish.com/personal?userId=","createTime":"1763509398807","sendUserName":"x***3","receiverId":"2221205134286","sendMessage":"确认只有这几处瑕疵哈","messageId":"3827276878171.PNM","urlInfo":"fleamarket://message_chat?itemId=995273129578&peerUserId=2221205134286&sid=55554941197&messageId=e1793c7249fe4a31a682ad82c577c187&adv=no","itemId":"995273129578","chatId":"55554941197","mid":"52370007 0"}
            """;
        GoofishSocket userSocket = webSocketManager.getUserSocket("2920966133");
       try {
           userSocket.loadItem(JSONUtil.toBean(s, MsgDispatcher.MsgContext.class));
       }catch (Exception e){
           log.info(JSONUtil.toJsonStr(e.getMessage()));
       }

        return "111";
    }

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
     * todo 后续再兼容更新 ，现在全是注册
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<UserResponse>> registerUser(@RequestBody UserRegistrationRequest request) {

        // 使用UserWebSocketManager创建用户特定的Account对象
        Account account = webSocketManager.createUserAccount(request.getCookieStr());

        log.info("goodfishdata = {}", JsonUtils.toJson(account));

        // 将userId声明为final以便在lambda中使用
        final String finalUserId = account.getUserId();

        try {

            // 先建立WebSocket连接
             webSocketManager.createWebSocketConnection(account);

            UserResponse response = new UserResponse(
                finalUserId,
                true,
                "注册成功并已连接"
            );
            return Mono.just(ResponseEntity.ok(response));

//                    .then(Mono.defer(() -> {
//                        UserResponse response = new UserResponse(
//                                finalUserId,
//                                true,
//                                "注册成功并已连接"
//                        );
//                        return Mono.just(ResponseEntity.ok(response));
//                    }))
//                    .onErrorResume(error -> {
//                        log.error("注册用户失败", error);
//                        return Mono.just(ResponseEntity.badRequest().body(
//                                new UserResponse(finalUserId, false, "注册失败: " + error.getMessage())
//                        ));
//                    });


        } catch (Exception e) {
            log.error("处理用户注册请求时发生异常", e);
            return Mono.just(ResponseEntity.badRequest().body(
                    new UserResponse(finalUserId, false, "请求处理失败: " + e.getMessage())
            ));
        }
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
