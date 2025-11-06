package com.bigbear.goodfish2.trigger.api;


import com.bigbear.aigc.biz.domain.vo.ItemVo;
import com.bigbear.aigc.biz.service.IItemService;
import com.bigbear.goodfish2.application.WebSocketService;
import jakarta.annotation.Resource;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.system.api.RemoteUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 闲鱼控制器
 *
 * @author autoagent
 */
@Slf4j
@RestController
@RequestMapping("/api/xianyu")
@RequiredArgsConstructor
public class GoodfishController {

    private final WebSocketService webSocketService;

    private final IItemService iItemService;

    @Resource
    private RemoteUserService userInfoClient;

    /**
     * 初始化连接
     */
    @PostMapping("/connect")
    public Map<String, Object> connect(@RequestBody Map<String, String> request,@RequestParam Long itemId) {
        // todo 闲鱼账号管理 这个商品里面需要账号id


        // todo 参数获取itemId, 获取对应账号的 cookies
        ItemVo itemVo = iItemService.queryById(itemId);

        // 这里先写死
        String cookiesStr = request.get("cookies");
        if (cookiesStr == null || cookiesStr.isEmpty()) {
            return Map.of("success", false, "message", "缺少cookies参数");
        }

        try {
            webSocketService.initConnection("",cookiesStr);
            return Map.of("success", true, "message", "连接成功");
        } catch (Exception e) {
            log.error("连接失败", e);
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }

    /**
     * 断开连接
     */
    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        try {
            webSocketService.closeConnection("");
            return Map.of("success", true, "message", "断开连接成功");
        } catch (Exception e) {
            log.error("断开连接失败", e);
            return Map.of("success", false, "message", "断开连接失败: " + e.getMessage());
        }
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh-token")
    public Map<String, Object> refreshToken() {
        try {
            String token = webSocketService.refreshToken("");
            if (token != null) {
                return Map.of("success", true, "message", "Token刷新成功", "token", token);
            } else {
                return Map.of("success", false, "message", "Token刷新失败");
            }
        } catch (Exception e) {
            log.error("Token刷新失败", e);
            return Map.of("success", false, "message", "Token刷新失败: " + e.getMessage());
        }
    }

    /**
     * 切换人工模式
     */
    @PostMapping("/manual-mode")
    public Map<String, Object> toggleManualMode(@RequestBody Map<String, String> request) {
        String conversationId = request.get("conversationId");
        if (conversationId == null || conversationId.isEmpty()) {
            return Map.of("success", false, "message", "缺少conversationId参数");
        }

        try {
            webSocketService.toggleManualMode(conversationId,true);
            boolean isManualMode = webSocketService.isManualMode(conversationId);
            return Map.of(
                    "success", true,
                    "message", "切换成功",
                    "manualMode", isManualMode
            );
        } catch (Exception e) {
            log.error("切换人工模式失败", e);
            return Map.of("success", false, "message", "切换人工模式失败: " + e.getMessage());
        }
    }
}
