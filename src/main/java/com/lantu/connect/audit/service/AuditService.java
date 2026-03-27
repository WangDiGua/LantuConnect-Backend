package com.lantu.connect.audit.service;

import com.lantu.connect.audit.entity.AuditItem;
import com.lantu.connect.common.result.PageResult;

/**
 * 审核Audit服务接口 — 两级审核模型。
 */
public interface AuditService {

    PageResult<AuditItem> pagePendingResources(Long operatorUserId, String resourceType, String status, int page, int pageSize);

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
}
