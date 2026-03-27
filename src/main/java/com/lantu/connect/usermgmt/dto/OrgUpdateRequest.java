package com.lantu.connect.usermgmt.dto;

import lombok.Data;

/**
 * 组织更新请求
 */
@Data
public class OrgUpdateRequest {

    private String menuName;

    private Long menuParentId;

    private Integer ifXy;

    private Integer sortOrder;
}
