package com.bigbear.goofish.app;

import com.bigbear.goofish.domain.Account;
import com.bigbear.goofish.core.AccountService;
import com.bigbear.goofish.core.ConnectionProperties;
import com.bigbear.goofish.core.ConnectionPropertiesConfig;
import com.bigbear.goofish.core.socket.GoofishSocket;
import com.bigbear.goofish.core.socket.msg.MsgDispatcher;
import com.bigbear.goofish.domain.entity.AccountEntity;
import com.bigbear.goofish.infra.AccountManager;
import com.bigbear.goofish.infra.GoofishClient;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

/**
 * 用户特定的WebSocket管理器，为每个用户维护独立的连接 使用AccountService管理用户账户，实现与ConnectionProperties的解耦
 */
@Slf4j
@Component
public class UserWebSocketService {

    @Autowired
    private AutoReplyService autoReplyService;

    @Autowired
    private GoofishClient client;

    @Autowired
    private MsgDispatcher msgDispatcher;


    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountManager accountManager;



    // 存储用户ID到GoofishSocket的映射
    private final Map<String, GoofishSocket> userSockets = new ConcurrentHashMap<>();

    /**
     * 获取所有用户的Socket连接
     *
     * @return 用户ID到GoofishSocket的映射
     */
    public Map<String, GoofishSocket> getAllUserSockets() {
        return new HashMap<>(userSockets);
    }

    /**
     * 为特定用户创建WebSocket连接
     */
    public Mono<WebSocketSession> createWebSocketConnection(ConnectionProperties properties,
        Account account) {
        // 如果properties为null，则使用系统配置
        if (properties == null) {
            properties = accountService.getSystemProperties();
        }

        // 检查连接是否已存在
        String userId = account != null ? account.getUserId() : "unknown_user";
        if (userSockets.containsKey(userId)) {
            log.warn("用户 {} 的连接已经存在，断开旧连接", userId);
            disconnectUser(userId);
        }

        // 声明为final以便在lambda中使用
        final ConnectionProperties finalProperties = properties;
        final Account finalAccount = account;

        // 创建特定于用户的HttpClient
        HttpClient httpClient = HttpClient.create()
            .headers(headers -> {
                if (finalAccount != null && finalAccount.getCookieStr() != null) {
                    headers.add(HttpHeaderNames.COOKIE, finalAccount.getCookieStr());
                }
                headers.add(HttpHeaderNames.HOST, finalProperties.getSocketUrl().getHost());
                headers.add(HttpHeaderNames.CONNECTION, "Upgrade");
                headers.add(HttpHeaderNames.PRAGMA, "no-cache");
                if (finalAccount != null && finalAccount.getUserAgent() != null) {
                    headers.add(HttpHeaderNames.USER_AGENT, finalAccount.getUserAgent());
                }
                headers.add(HttpHeaderNames.ORIGIN, finalProperties.getHttpsUrl());
                headers.add(HttpHeaderNames.ACCEPT_ENCODING, "gzip, deflate, br, zstd");
                headers.add(HttpHeaderNames.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
            });

        // 创建WebSocket客户端
        ReactorNettyWebSocketClient webSocketClient = new ReactorNettyWebSocketClient(
            httpClient,
            () -> WebsocketClientSpec.builder().maxFramePayloadLength(1024 * 1024)
        );

        // 创建用户特定的GoofishSocket实例
        final GoofishSocket socket = new GoofishSocket();
        socket.setClient(client);
        socket.setMsgDispatcher(msgDispatcher);
        socket.setConnectionProperties(properties);
        socket.setReplyService(autoReplyService);

        socket.setDelegate(webSocketClient); // 设置委托
        socket.setUserId(userId); // 设置用户ID，确保socket能够正确获取用户账户信息
        socket.setAccountService(accountService);

        // 注册到用户映射
        userSockets.put(userId, socket);

        // 创建一个原子引用用于保存session
        final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();

        // 连接到WebSocket服务器
        return webSocketClient.execute(finalProperties.getSocketUrl(), session -> {
                // 保存session引用
                sessionRef.set(session);

                // WebSocketHandler需要返回Mono<Void>
                return socket.register(session)
                    .thenMany(Flux.merge(
                        socket.receive(session),
                        socket.heartbeat(session)
                    )).then()
                    .doFinally(signalType -> {
                            if (signalType != SignalType.ON_ERROR) {
                                log.info("用户 {} WebSocket会话已建立", userId);
                            }
                        }
                    );
            })
            // 使用保存的session引用作为返回值
            .then(Mono.defer(() -> {
                WebSocketSession session = sessionRef.get();
                if (session != null) {
                    return Mono.just(session);
                } else {
                    return Mono.error(new RuntimeException("WebSocket连接失败，未获取到会话"));
                }
            }))
            .doOnSubscribe(
                subscription -> {
                    log.info("开始连接用户 {} 的WebSocket", userId);
                })
            .doOnSuccess(session -> {
                log.info("用户 {} WebSocket连接成功", userId);
            })
            .doOnError(error -> {
                log.error("用户 {} WebSocket连接失败", userId, error);
            });
    }


    /**
     * 为指定用户创建并初始化Account对象
     */
    public Account createUserAccount(String cookieStr) {
        Account account = createAccount(cookieStr);
        AccountEntity account1 = accountManager.getByUserId(account.getUserId());
        if(account1==null){
            // 注册到AccountService
              accountService.registerAccount(account);
        }else {
            account.updateDeviceId(account1.getDeviceId());
        }

        return account;
    }

    public Account createAccount(String cookieStr) {
        //已经调用了初始化方法
        return new Account(cookieStr);
    }
    /**
     * 获取用户的WebSocket连接
     */
    public GoofishSocket getUserSocket(String userId) {
        return userSockets.get(userId);
    }

    /**
     * 断开用户的WebSocket连接
     */
    public void disconnectUser(String userId) {
        GoofishSocket socket = userSockets.remove(userId);
        if (socket != null) {
            log.info("断开用户 {} 的WebSocket连接", userId);
            // 同时从AccountService中移除账户
//            accountService.removeAccount(userId);
        }
    }
}
