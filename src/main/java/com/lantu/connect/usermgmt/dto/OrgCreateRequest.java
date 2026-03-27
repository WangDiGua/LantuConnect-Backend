package com.lantu.connect.usermgmt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 组织创建请求
 */
@Data
public class OrgCreateRequest {

    @NotBlank
    private String menuName;

    private Long menuParentId;

    private Integer ifXy;

    private Integer sortOrder;
}
