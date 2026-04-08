package com.lantu.connect.usersettings.dto;

import lombok.Data;

/**
 * 撤销或轮换 API Key：须校验登录密码。
 */
@Data
public class ApiKeyRevokeRequest {

    private String password;
}
