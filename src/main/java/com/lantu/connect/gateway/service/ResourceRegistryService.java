package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.LifecycleTimelineVO;
import com.lantu.connect.gateway.dto.ObservabilitySummaryVO;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;

import java.util.List;
import java.util.Map;

public interface ResourceRegistryService {

    ResourceManageVO create(Long operatorUserId, ResourceUpsertRequest request);

    ResourceManageVO update(Long operatorUserId, Long resourceId, ResourceUpsertRequest request);

    void delete(Long operatorUserId, Long resourceId);

    ResourceManageVO submitForAudit(Long operatorUserId, Long resourceId);

    ResourceManageVO deprecate(Long operatorUserId, Long resourceId);

    /**
     * @param forResourceId 若提供，则仅返回该资源创建者（created_by）登记的资源，且要求操作者可管理该资源；
     *                        用于 Agent/Skill 绑定 MCP 等场景与后端绑定校验一致；为 {@code null} 时按操作者本人过滤。
     */
    PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, String status,
                                          String keyword, String sortBy, String sortOrder,
                                          Integer page, Integer pageSize, Long forResourceId);

    ResourceVersionVO createVersion(Long operatorUserId, Long resourceId, ResourceVersionCreateRequest request);

    ResourceManageVO switchVersion(Long operatorUserId, Long resourceId, String version);

    /**
     * 将某一 active 版本行的 {@code snapshot_json} 合并写回主资源与扩展表（含目录标签/关联的保留策略见实现）；
     * {@link com.lantu.connect.gateway.service.support.ResourceLifecycleStateMachine#ensureEditable} 与 update 一致。
     */
    ResourceManageVO applyVersionSnapshotToWorkingCopy(Long operatorUserId, Long resourceId, String version);

    /**
     * 审核通过「已发布资源变更」时由审核服务调用：将冻结快照写入主表/扩展表并刷新当前默认版本快照。
     */
    void applyPublishedUpdateFromAudit(Long reviewerUserId, Long resourceId, Map<String, Object> payloadSnapshot);

    List<ResourceVersionVO> listVersions(Long operatorUserId, Long resourceId);

    ResourceManageVO withdraw(Long operatorUserId, Long resourceId);

    /** 单事务批量撤回（与 {@link #withdraw} 权限与状态机一致）。 */
    void batchWithdraw(Long operatorUserId, List<Long> resourceIds);

    ResourceManageVO getById(Long operatorUserId, Long resourceId);

    /**
     * 根据 DB 中当前扩展表重建默认版本快照（用于技能包上传等与请求体无关的变更）。
     */
    void recomputeCurrentVersionSnapshot(Long operatorUserId, Long resourceId);

    LifecycleTimelineVO lifecycleTimeline(Long operatorUserId, Long resourceId);

    ObservabilitySummaryVO observabilitySummary(Long operatorUserId, String resourceType, Long resourceId);
}

