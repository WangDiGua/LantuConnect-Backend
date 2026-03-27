package com.lantu.connect.usermgmt.dto;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户详情视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailVO {

    private User user;
    private List<PlatformRole> roles;
    private UserOrgVO org;
}
