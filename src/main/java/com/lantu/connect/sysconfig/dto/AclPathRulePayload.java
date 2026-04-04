package com.lantu.connect.sysconfig.dto;

import lombok.Data;

/**
 * 路径级 ACL 规则（Casbin/Ant 风格 path + 角色编码逗号分隔）
 */
@Data
public class AclPathRulePayload {

    private String id;
    private String path;
    private String roles;
}
