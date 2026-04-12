package com.lantu.connect.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理 ApiKeyResponse 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

    private String id;

    private String name;

    private List<String> scopes;

    private String secretPlain;

    private LocalDateTime expiresAt;

    private Boolean revoked;

    /** 创建/轮换响应可能带回；列表接口见 {@link com.lantu.connect.usermgmt.entity.ApiKey#getIntegrationPackageId()} */
    private String integrationPackageId;
}
