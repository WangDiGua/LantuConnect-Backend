package com.lantu.connect.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户组织信息视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrgVO {

    private Long menuId;
    private String menuName;
    private Long menuParentId;
    private Integer menuLevel;
}
