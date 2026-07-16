package com.bigbear.quarkus.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;

/**
 * 跨语言统一身份载体（完整用户信息）。
 *
 * <p>字段与 Spring 侧 {@code LoginUser}、Go 侧 {@code LoginUser} 逐字对齐，
 * 经 {@code X-User-Info}（HTTP 头 / gRPC Metadata）在边界与下游之间零解析透传。
 *
 * <p>多租户字段 {@code tenantId} 为前瞻预留，本期单租户，不参与鉴权判定。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginUser implements Serializable {

    private String userId;
    private List<String> roles;
    private String username;
    private String tenantId;
    private String deviceId;

    public LoginUser() {
    }

    public LoginUser(String userId, List<String> roles, String username, String tenantId, String deviceId) {
        this.userId = userId;
        this.roles = roles;
        this.username = username;
        this.tenantId = tenantId;
        this.deviceId = deviceId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /** 匿名：网关白名单接口，下游 userId 为空即视为匿名。 */
    public boolean isAnonymous() {
        return userId == null || userId.isBlank();
    }
}
