package com.bigbear.spring.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

/**
 * 跨语言统一身份载体（完整用户信息）。
 *
 * <p>字段与 Quarkus 侧 {@code LoginUser}、Go 侧 {@code LoginUser} 逐字对齐，
 * 经 {@code X-User-Info}（HTTP 头 / gRPC Metadata）在边界与下游之间零解析透传。
 *
 * <p>多租户字段 {@code tenantId} 为前瞻预留，本期单租户，不参与鉴权判定。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginUser(
    String userId,
    List<String> roles,
    String username,
    String tenantId,
    String deviceId
) implements Serializable {

    /** 匿名：网关白名单接口，下游 userId 为空即视为匿名。 */
    public boolean isAnonymous() {
        return userId == null || userId.isBlank();
    }
}
