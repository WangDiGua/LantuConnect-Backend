package com.lantu.connect.common.integration;

import lombok.Data;

import java.util.List;

/**
 * ACL发布请求
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
public class AclPublishRequest {

    private String agentName;

    private String action;

    private List<String> permissions;

    private String targetResource;

    private Long requesterId;

    private String requesterName;
}
