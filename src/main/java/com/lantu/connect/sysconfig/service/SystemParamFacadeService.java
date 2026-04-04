package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.AclPathRulePayload;
import com.lantu.connect.sysconfig.dto.AuditLogQueryRequest;
import com.lantu.connect.sysconfig.dto.SecuritySettingUpsertRequest;
import com.lantu.connect.sysconfig.dto.SystemParamUpsertRequest;
import com.lantu.connect.sysconfig.entity.AuditLog;
import com.lantu.connect.sysconfig.entity.SecuritySetting;
import com.lantu.connect.sysconfig.entity.SystemParam;

import java.util.List;
import java.util.Map;

/**
 * 系统配置SystemParamFacade服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface SystemParamFacadeService {

    List<SystemParam> listParams();

    void putParam(Long operatorUserId, SystemParamUpsertRequest request);

    List<SecuritySetting> listSecurity();

    void putSecurity(Long operatorUserId, SecuritySettingUpsertRequest request);

    PageResult<AuditLog> pageAuditLogs(AuditLogQueryRequest request);

    Map<String, Object> applyNetwork(Long operatorUserId, List<String> rules);

    /**
     * 持久化路径级 ACL 规则；集成关闭时为落库 + 通知，真实网关联动由 infra 订阅该参数或其它管道实现。
     */
    Map<String, Object> publishAcl(Long operatorUserId, List<AclPathRulePayload> rules);

    /** 管理端网络白名单 CIDR 列表（来自 t_system_param） */
    List<String> getNetworkAllowlist();

    /**
     * ACL 规则列表（无独立存储时返回空列表；键 {@code rules} 与前端约定一致）。
     */
    Map<String, Object> getAclRules();

    /** 审计日志中出现过的 action 值，供管理端下拉 */
    List<String> listDistinctAuditActions();
}
