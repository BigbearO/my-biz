package com.bigbear.ai.biz.trigger.api;

import com.bigbear.spring.auth.LoginUser;
import com.bigbear.spring.auth.RequestUserUtils;
import com.bigbear.spring.auth.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/auth")
public class AuthTest {

    /**
     * 获取当前登录用户ID。
     */
    @GetMapping("/current")
    public R<?> current() {
        LoginUser user = UserContext.current();
        if (user == null) {
            user = RequestUserUtils.from();
        }
        log.info("user:{}", user);
        return R.ok("1",user == null ? "null" : user.userId());
    }
}
