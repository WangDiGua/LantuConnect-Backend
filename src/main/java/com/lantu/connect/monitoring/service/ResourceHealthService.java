package com.lantu.connect.monitoring.service;

import com.lantu.connect.monitoring.dto.ResourceHealthPolicyUpdateRequest;
import com.lantu.connect.monitoring.dto.ResourceHealthSnapshotVO;

import java.util.List;

/**
 * 统一资源健康治理接口。
 *
 * 负责 agent / skill / mcp 的健康策略落库、健康探测、可调用状态裁决与快照读取。
 */
public interface ResourceHealthService {

    void ensurePolicyForResource(Long resourceId);

    ResourceHealthSnapshotVO probeAndPersist(Long resourceId);

    ResourceHealthSnapshotVO manualBreak(Long resourceId, Integer openDurationSeconds);

    ResourceHealthSnapshotVO manualRecover(Long resourceId);

    ResourceHealthSnapshotVO refreshCallability(Long resourceId);

    ResourceHealthSnapshotVO getSnapshot(Long resourceId);

    ResourceHealthSnapshotVO updatePolicy(Long resourceId, ResourceHealthPolicyUpdateRequest request);

    List<ResourceHealthSnapshotVO> listSnapshots(String resourceType, String healthStatus, String callabilityState, String probeStrategy);
}
