package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户角色全量替换请求
 */
@Data
public class UserRoleReplaceRequest {

    @NotNull
    private List<Long> roleIds;
}
