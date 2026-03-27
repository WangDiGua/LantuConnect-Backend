package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户组织绑定请求
 */
@Data
public class UserOrgBindRequest {

    @NotNull
    private Long orgId;
}
