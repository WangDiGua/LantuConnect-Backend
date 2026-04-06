package com.lantu.connect.usersettings.dto;

import lombok.Data;

/**
 * 撤销 API Key：有本地密码时须校验密码；无密码用户须校验短信。
 */
@Data
public class ApiKeyRevokeRequest {

    private String password;

    private String smsCode;
}
