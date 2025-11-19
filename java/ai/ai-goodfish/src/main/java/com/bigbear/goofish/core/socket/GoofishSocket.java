package com.bigbear.goofish.core.socket;

import static org.springframework.util.StringUtils.hasLength;

import cn.hutool.json.JSONUtil;
import com.bigbear.ai.biz.domain.entity.ItemEntity;
import com.bigbear.ai.biz.manager.ItemManager;
import com.bigbear.goofish.app.AutoReplyService;
import com.bigbear.goofish.core.AccountService;
import com.bigbear.goofish.core.ConnectionProperties;
import com.bigbear.goofish.core.socket.msg.MsgDispatcher;
import com.bigbear.goofish.core.socket.msg.receive.ReceiveMsg;
import com.bigbear.goofish.core.socket.msg.send.AckDiffMsg;
import com.bigbear.goofish.core.socket.msg.send.AckMsg;
import com.bigbear.goofish.core.socket.msg.send.GetStatMsg;
import com.bigbear.goofish.core.socket.msg.send.HeartbeatMsg;
import com.bigbear.goofish.core.socket.msg.send.RegMsg;
import com.bigbear.goofish.core.socket.msg.send.ReplyTextMsg;
import com.bigbear.goofish.domain.Account;
import com.bigbear.goofish.domain.entity.ItemContext;
import com.bigbear.goofish.infra.GoofishClient;
import com.bigbear.goofish.utils.JsonUtils;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Data
@Slf4j
public class GoofishSocket {

    private AccountService accountService;

    private Account account; // 存储与当前Socket关联的用户ID

    private GoofishClient client;
    private RegMsg regMsg;
    private MsgDispatcher dispatcher;
    private ConnectionProperties properties;
    private AutoReplyService replyService;


    private ItemManager itemManager;

    private ReactorNettyWebSocketClient delegate;

    private ConcurrentHashMap<String, Boolean> manualModeChats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> heartbeatMid = new ConcurrentHashMap<>();
    @Value("${goofish.historical-data-maximum}")
    int historicalDataMaximum = 20;

    /**
     * 发送消息到WebSocket
     */
    public Mono<Void> reply(WebSocketSession session, MsgDispatcher.MsgContext ctx) {
        final String userId =  getAccount().getUserId();
        log.trace("[{}] prepare to send message for chat {}", userId, ctx.getChatId());
        Mono<ItemContext> itemMono = loadItem(ctx);

        if (itemMono==null) {
            return Mono.empty();
        }

        // 加载商品信息
        return itemMono
            .then(Mono.defer(() -> {
                // 构建消息发送逻辑
                return Mono.just(ctx)
                    .flatMap(x -> {
                        // only reply when sender in not the receiver
                        if (!Objects.equals(userId, ctx.getReceiverId())
                            && !manualModeChats.containsKey(ctx.getChatId())) {
                            log.info("买家: {}", ctx.getSendMessage());
                            return botReply(session, ctx)
                                .flatMap(message -> {
                                    log.info("商家: {}", message);
                                    log.debug(
                                        "update seller's message to database in chat history table for user {}",
                                        userId);
                                    return Mono.empty().then();
                                });
                        } else {
                            // user manually reply
                            if (Objects.equals(ctx.getSendMessage(), "[微笑]")) {
                                // return to auto mode
                                log.info("received emoji [微笑], exit manual mode for chat {}",
                                    ctx.getChatId());
                                manualModeChats.remove(ctx.getChatId());
                            } else {
                                // switch to manually mode
                                log.info(
                                    "detected user send message from other device, suspend for the chat {}",
                                    ctx.getChatId());
                                manualModeChats.put(ctx.getChatId(), true);
                            }
                            return Mono.just(ctx.getSendMessage())
                                .flatMap(message -> {
                                    log.info("商家: {}", message);
                                    log.debug(
                                        "update seller's message to database in chat history table for user {}",
                                        userId);
                                    return Mono.empty();
                                });
                        }
                    });
            }));
    }

    // 将商品信息加载到上下文中
    public Mono<ItemContext> loadItem(MsgDispatcher.MsgContext msgContext) {

        log.info("msgContext={}", JSONUtil.toJsonStr(msgContext));
        final String itemId = msgContext.getItemId();
        final String userId =
            getAccount() != null ? getAccount().getUserId() : "unknown"; // 获取当前用户ID

        // 构建缓存键，包含userId以支持多用户
        String cacheKey = userId + ":" + itemId;

        // 快速返回：从缓存中获取
        ItemContext cache = dispatcher.getItemContextRegistry().get(cacheKey);
        if (cache != null) {
            msgContext.setItemInfo(cache.getItemInfo());
            return Mono.just(cache);
        }

        ItemEntity itemById = itemManager.getItemById(itemId);
        if (itemById == null) {
            return null;
        }

        ItemContext context= new ItemContext(itemId, msgContext.getUserId(), JSONUtil.toJsonStr(itemById));


        // 从数据库查询 - 使用正确的方法名并传入userId
        return Mono.just(context)
            .flatMap(itemCtx -> {
                log.debug("found valid item info in db: {} for user {}", itemCtx.getItemInfo(),
                    userId);
                dispatcher.getItemContextRegistry().put(cacheKey, itemCtx);
                msgContext.setItemInfo(itemCtx.getItemInfo());
                return Mono.just(itemCtx);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // 如果数据库中不存在，则从API获取
                log.debug("item not found in db, try to fetch from api for itemId: {}", itemId);
                return client.getItemInfo(itemId, userId)
                    .flatMap(itemInfo -> {
                        if (itemInfo != null) {
                            ItemContext newCtx = new ItemContext();
                            newCtx.setItemId(itemId);
                            // 修复JsonNode转换为String的问题
                            String itemInfoStr = itemInfo.toString();
                            newCtx.setItemInfo(itemInfoStr);
                            dispatcher.getItemContextRegistry().put(cacheKey, newCtx);
                            msgContext.setItemInfo(itemInfoStr);
                            return Mono.just(newCtx);
                        }
                        return Mono.empty();
                    });
            }));
    }

    // 使用上下文处理回复
    private Mono<String> botReply(WebSocketSession session, MsgDispatcher.MsgContext msgContext) {
        final String userId = getAccount() != null ? getAccount().getUserId() : "unknown";
        log.info("[{}][user:{}] prepare to send bot reply for chat {}", msgContext.getIdentity(),
            userId, msgContext.getChatId());

        // 2. 构建情感提示词（流式处理）
        String emotionPrompt = replyService.emotionPrompt;

        // 3. 生成回复（链式调用）
        return Mono.just(emotionPrompt)
            .map(em -> String.format("基于以下上下文生成你的回复\n" +
                "聊天上下文: %s\n" +
                "商品信息: %s", em, msgContext.getItemInfo()))
            .flatMap(prompt -> replyService.generateReply(prompt,msgContext ))
            .flatMap(botMsg -> {
                log.info("[{}][user:{}] bot generate answer: {}", msgContext.getIdentity(),
                    userId, botMsg);
                return new ReplyTextMsg(
                    msgContext.getChatId(),
                    msgContext.getReceiverId(),
                    userId,  // 使用局部变量userId确保一致
                    botMsg
                ).send(session)
                    .thenReturn(botMsg);
            });

    }

    public Flux<ReceiveMsg> receive(WebSocketSession session) {
        return session.receive()
            // 解析消息
            .flatMap(msg -> {
                ReceiveMsg m = ReceiveMsg.parse(msg.getPayloadAsText());
                log.info("receive msg data <--- ={}", JSONUtil.toJsonStr(m));
                return Mono.just(m);
            })
            // 过滤心跳响应
            .filter(m -> !Boolean.TRUE.equals(heartbeatMid.remove(m.getMid())))
            // 处理IP摘要消息
            .flatMap(m -> m.hasIpDigest()
                ? new GetStatMsg().send(session).then(Mono.just(m))
                : Mono.just(m))
            // 处理过长标签消息
            .flatMap(m -> m.hasTooLong2Tag()
                ? new AckDiffMsg().send(session).then(Mono.just(m))
                : Mono.just(m))
            // 发送ACK确认, 放在处理推送包前 因为推送包处理时间长的话会触发server resend package
            .flatMap(m -> {
                log.trace("sending ack for mid: {}", m.getMid());
                return new AckMsg(m).send(session).thenReturn(m);
            })
            // 3. 处理同步推送包（核心逻辑）
            .flatMap(m -> {
                if (!m.hasSyncPushPackageMessage()) {
                    return Mono.just(m);
                }
                // 只处理第一个syncData对象（或根据需要修改）
                ReceiveMsg.SyncData data =
                    m.getSyncData().isEmpty() ? null : m.getSyncData().getFirst();
                Mono<ReceiveMsg> result;
                if (data != null) {
                    // 假设ObjectType是data对象的一个字段，尝试直接访问或使用默认值
                    int objectType = 0; // 默认值
                    try {
                        objectType = data.getObjectType();
                    } catch (Exception e) {
                        log.warn("无法获取SyncData的objectType: {}", e.getMessage());
                    }

                    switch (objectType) {
                        case 40000:
                            result = dispatcher.handle(m, session)
                                .flatMap(context -> {
                                    if (!hasLength(context.getChatId())) {
                                        return Mono.just(m);
                                    }
                                    // 使用properties对象的toString()代替getUserId()
                                    log.debug("[user:{}] processing message for chatId: {}",
                                        properties.toString(), context.getChatId());
                                    // send api request to get history
                                    return Mono.empty()
                                        .then(Mono.defer(() -> reply(session, context)))
                                        .thenReturn(m);
                                });
                            break;
                        case 40103:
                            log.debug("processing message with objectType 40103, mid: {}",
                                m.getMid());
                            result = Mono.just(m);
                            break;
                        // 消息已读
                        case 40102:
                            log.debug("processing message with objectType 40102, mid: {}",
                                m.getMid());
                            result = Mono.just(m);
                            break;
                        case 37000:
                            log.debug("processing message with objectType 37000, mid: {}",
                                m.getMid());
                            result = Mono.just(m);
                            break;
                        case -1:
                            log.debug("processing default objectType (-1), mid: {}", m.getMid());
                            result = Mono.just(m);
                            break;
                        default:
                            log.debug("unhandled objectType: {}, mid: {}", objectType,
                                m.getMid());
                            result = Mono.just(m);
                            break;
                    }
                } else {
                    log.warn("No sync data available, mid: {}", m.getMid());
                    result = Mono.just(m);
                }
                return result;
            })
            //原本这儿会处理历史消息，去掉放到 reply service了
            // 修改第4步：处理历史查询响应
            .flatMap(Mono::just);
    }


    /**
     * 处理WebSocket会话，为特定用户服务
     */


    public Mono<Void> register(WebSocketSession session) {
        this.regMsg = new RegMsg(getAccount().getDeviceId(), "");

        return client.getToken(getAccount()).flatMap(token -> {
            regMsg.updateToken(token);
            return regMsg.send(session);

        });
    }

    public void refreshToken() {
        client.getToken(getAccount()).subscribe(token -> {
                regMsg.updateToken(token);
            }
        );
    }

    public Flux<Void> heartbeat(WebSocketSession session) {
        return Flux.interval(Duration.ofSeconds(properties.getInterval()))
            .flatMap(tick -> {
                HeartbeatMsg heartbeatMsg = new HeartbeatMsg();
                heartbeatMid.put(heartbeatMsg.getHeaders().getMid(), true);
                return heartbeatMsg.send(session);
            });
    }

    // request history chat
//    private Mono<Void> history(ReceiveMsg recMsg, MsgDispatcher.MsgContext context, WebSocketSession session) {
//        log.debug("start fetching chat history process");
//        final String userId = getAccount() != null ? getAccount().getUserId() : "unknown"; // 获取当前用户ID
//
//        if (recMsg.getMid().equals(context.getMid())) {
//            log.trace("[{}] process history records within received message of mid: {}", recMsg.hashCode(), recMsg.getMid());
//            ListUserMessageMsg listMsg = new ListUserMessageMsg(
//                    context.getChatId(),
//                    historicalDataMaximum
//            );
//            return chatRepository.findByChatIdAndUserId(context.getChatId(), userId)
//                    .doOnSuccess(chat -> {
//                        if (chat != null) {
//                            log.trace("[{}] found chat history in db for chatId: {} and userId: {}",
//                                    recMsg.hashCode(), context.getChatId(), userId);
//                        }
//                    })
//                    .switchIfEmpty(Mono.defer(() -> {
//                        log.trace("[{}] querying historical chat records via api", recMsg.hashCode());
//                        String mid = listMsg.getHeaders().getMid();
//                        context.setMessagesQueryMid(mid);
//                        dispatcher.getHistoryMsgRegistry().put(mid, context);
//                        return listMsg.send(session)
//                                .doOnSuccess(__ -> log.trace("[{}] api request completed", recMsg.hashCode()))
//                                .then(Mono.empty());
//                    }))
//                    .flatMap(chat -> Mono.fromRunnable(() -> {
//                        String chatHistory = chat.getChatHistory();
//                        log.info("loaded chat history from database for user {}", userId);
//                        log.trace("[{}] chat history loaded: {}", recMsg.hashCode(), chatHistory);
//
//                        if (!Objects.equals(context.getReceiverId(), userId)) {
//                            log.debug("[{}]from database branch: emitting chat history to context's sink", recMsg.hashCode());
//                            context.setHistoryChat(JsonUtils.readValue(chatHistory, ObjectNode.class));
//                        }
//                    }));
//        }
//
//        // get chat history from response
//        if (recMsg.getMid().equals(context.getMessagesQueryMid())) {
//            log.trace("[{}] process history records within received response of mid : {}", recMsg.hashCode(), context.getMessagesQueryMid());
//
//            if (recMsg.getBody() == null || recMsg.getBody().getUserMessageModels() == null) {
//                if (recMsg.getCode() != 200) {
//                    log.error("encounter error with response of list user message, raw json: {}", recMsg.getRaw());
//                }
//                return Mono.empty();
//            }
//
//            ArrayNode messagesArray = OBJECT_MAPPER.createArrayNode();
//            recMsg.getBody().getUserMessageModels().forEach(model -> {
//                ObjectNode messageNode = OBJECT_MAPPER.createObjectNode();
//                messageNode.put("role",
//                        Objects.equals(model.getMessage().getExtension().getSenderUserId(),
//                                userId) ? "seller" : "buyer");
//                messageNode.put("content",
//                        model.getMessage().getExtension().getReminderContent());
//                messageNode.put("timestamp",
//                        model.getMessage().getCreateAt());
//                messagesArray.add(messageNode);
//            });
//
//            ObjectNode chatHistoryJson = OBJECT_MAPPER.createObjectNode();
//            chatHistoryJson.set("messages", messagesArray);
//            log.trace("[{}] built history chat list json success: {}", recMsg.hashCode(), chatHistoryJson);
//
//            ChatContext chatContext = new ChatContext(
//                    context.getChatId(),
//                    userId, // 添加userId
//                    chatHistoryJson.toString(),
//                    null,
//                    context.getItemId()
//            );
//
//            log.trace("[{}] insert history chat list json to database: {}", recMsg.hashCode(), chatHistoryJson);
//            return Mono.defer(() -> {
//                        log.trace("[{}] starting db insert", recMsg.hashCode());
//                        return  Mo
//                    })
//                    .then();
//        }
//        return Mono.empty();
//    }


    public Mono<Void> establish() {
        return delegate.execute(properties.getSocketUrl(),
            session -> {
                // send /reg request
                return register(session)
                    .thenMany(Flux.merge(
                        receive(session),
                        heartbeat(session)
                    )).then();
            });
    }

}
