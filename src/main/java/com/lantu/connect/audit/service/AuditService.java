package com.lantu.connect.audit.service;

import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.common.result.PageResult;

import java.util.List;

/**
 * 审核Audit服务接口 — 两级审核模型。
 */
public interface AuditService {

    PageResult<AuditItem> pagePendingResources(Long operatorUserId, String resourceType, String status, String keyword, int page, int pageSize);

    PageResult<AuditItem> pagePendingAgents(Long operatorUserId, int page, int pageSize);

    PageResult<AuditItem> pagePendingSkills(Long operatorUserId, int page, int pageSize);

    void approveAgent(Long id, Long reviewerId);

    void approveSkill(Long id, Long reviewerId);

    void rejectAgent(Long id, String reason, Long reviewerId);

    void rejectSkill(Long id, String reason, Long reviewerId);

    void publishAgent(Long id, Long reviewerId);

    void publishSkill(Long id, Long reviewerId);

    void approveResource(Long id, Long reviewerId);

    void rejectResource(Long id, String reason, Long reviewerId);

    void publishResource(Long id, Long reviewerId);

    /** 单事务：任一条失败则全部回滚。 */
    void batchApproveResources(List<Long> ids, Long reviewerId);

    void batchRejectResources(List<Long> ids, String reason, Long reviewerId);

    void batchPublishResources(List<Long> ids, Long reviewerId);

    /**
     * 平台强制下架：仅应由 {@code platform_admin} 调用（控制器层校验）。资源进入 {@code deprecated}，并可选更新审核队列表记。
     */
    void platformForceDeprecateResource(Long resourceId, Long operatorUserId, String reason);
}
