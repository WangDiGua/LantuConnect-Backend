package com.lantu.connect.common.integration;

/**
 * ACL发布服务接口
 *
 * @author 王帝
 * @date 2026-03-23
 */
public interface AclPublishService {

    AclPublishResult publish(AclPublishRequest request);

    AclPublishResult queryStatus(String aclId);
}
