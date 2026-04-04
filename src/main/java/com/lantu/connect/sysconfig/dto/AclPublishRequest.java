package com.lantu.connect.sysconfig.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ACL 发布请求体（与前端 {@code { rules: [...] }} 对齐）
 */
@Data
public class AclPublishRequest {

    private List<AclPathRulePayload> rules = new ArrayList<>();
}
