package com.lantu.connect.common.integration;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ACL发布结果
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Data
@Builder
public class AclPublishResult {

    private boolean success;

    private String aclId;

    private String message;

    private LocalDateTime publishedAt;

    private String status;
}
