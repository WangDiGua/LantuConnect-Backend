package com.lantu.connect.usermgmt.dto;

import lombok.Data;

@Data
public class ApiKeyIntegrationPackagePatchRequest {

    /** 传 null 或空字符串表示清空绑定 */
    private String integrationPackageId;
}
