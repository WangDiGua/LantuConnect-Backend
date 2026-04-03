package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.sysconfig.dto.AuditLogQueryRequest;
import com.lantu.connect.sysconfig.dto.SecuritySettingUpsertRequest;
import com.lantu.connect.sysconfig.dto.SystemParamUpsertRequest;
import com.lantu.connect.sysconfig.entity.AuditLog;
import com.lantu.connect.sysconfig.entity.SecuritySetting;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.mapper.AuditLogMapper;
import com.lantu.connect.sysconfig.mapper.SecuritySettingMapper;
import com.lantu.connect.sysconfig.mapper.SystemParamMapper;
import com.lantu.connect.sysconfig.service.SystemParamFacadeService;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置SystemParamFacade服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class SystemParamFacadeServiceImpl implements SystemParamFacadeService {

    private final SystemParamMapper systemParamMapper;
    private final SecuritySettingMapper securitySettingMapper;
    private final AuditLogMapper auditLogMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final SystemNotificationFacade systemNotificationFacade;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Override
    public List<SystemParam> listParams() {
        return systemParamMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putParam(Long operatorUserId, SystemParamUpsertRequest request) {
        SystemParam existing = systemParamMapper.selectById(request.getParamKey());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            SystemParam entity = new SystemParam();
            entity.setKey(request.getParamKey());
            entity.setValue(request.getParamValue());
            entity.setDescription(request.getDescription());
            entity.setType("string");
            entity.setCategory("general");
            entity.setEditable(true);
            entity.setUpdateTime(now);
            systemParamMapper.insert(entity);
        } else {
            if (request.getParamValue() != null) {
                existing.setValue(request.getParamValue());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            existing.setUpdateTime(now);
            systemParamMapper.updateById(existing);
        }
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SYSTEM_PARAM_CHANGED,
                "修改系统参数",
                request.getParamKey());
        if (RuntimeAppConfigService.PARAM_KEY.equals(request.getParamKey())) {
            runtimeAppConfigService.invalidate();
        }
    }

    @Override
    public List<SecuritySetting> listSecurity() {
        return securitySettingMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void putSecurity(Long operatorUserId, SecuritySettingUpsertRequest request) {
        SecuritySetting existing = securitySettingMapper.selectById(request.getSettingKey());
        if (existing == null) {
            SecuritySetting entity = new SecuritySetting();
            entity.setKey(request.getSettingKey());
            entity.setValue(request.getSettingValue());
            entity.setLabel(request.getSettingKey());
            entity.setType("string");
            entity.setCategory("security");
            securitySettingMapper.insert(entity);
        } else {
            if (request.getSettingValue() != null) {
                existing.setValue(request.getSettingValue());
            }
            securitySettingMapper.updateById(existing);
        }
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SECURITY_SETTING_CHANGED,
                "修改安全配置",
                request.getSettingKey());
    }

    @Override
    public PageResult<AuditLog> pageAuditLogs(AuditLogQueryRequest request) {
        Page<AuditLog> page = new Page<>(request.getPage(), request.getPageSize());
        LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getUserId())) {
            q.eq(AuditLog::getUserId, request.getUserId());
        }
        if (StringUtils.hasText(request.getAction())) {
            q.eq(AuditLog::getAction, request.getAction());
        }
        if (StringUtils.hasText(request.getResult())) {
            String r = request.getResult().trim().toLowerCase();
            if ("success".equals(r) || "failure".equals(r)) {
                q.eq(AuditLog::getResult, r);
            }
        } else if (Boolean.TRUE.equals(request.getOnlyFailure())) {
            q.eq(AuditLog::getResult, "failure");
        }
        LocalDateTime from = request.getTimeFrom();
        LocalDateTime to = request.getTimeTo();
        if (from != null && to != null) {
            q.between(AuditLog::getCreateTime, from, to);
        } else if (from != null) {
            q.ge(AuditLog::getCreateTime, from);
        } else if (to != null) {
            q.le(AuditLog::getCreateTime, to);
        }
        String kw = ListQueryKeyword.normalize(request.getKeyword());
        if (kw != null) {
            q.and(w -> w.like(AuditLog::getUsername, kw)
                    .or()
                    .like(AuditLog::getAction, kw)
                    .or()
                    .like(AuditLog::getResource, kw)
                    .or()
                    .like(AuditLog::getResourceId, kw)
                    .or()
                    .like(AuditLog::getDetails, kw)
                    .or()
                    .like(AuditLog::getIp, kw)
                    .or()
                    .like(AuditLog::getUserId, kw));
        }
        q.orderByDesc(AuditLog::getCreateTime);
        Page<AuditLog> result = auditLogMapper.selectPage(page, q);
        return PageResults.from(result);
    }

    @Override
    public Map<String, Object> applyNetwork(Long operatorUserId) {
        boolean integrationMock = runtimeAppConfigService.system().isIntegrationMock();
        Map<String, Object> body = new HashMap<>();
        body.put("applied", true);
        body.put("mock", integrationMock);
        body.put("message", integrationMock
                ? "network apply simulated (set lantu.system.integration-mock=false to wire real infra)"
                : "network apply acknowledged (implement infra hook here)");
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SYSTEM_NETWORK_APPLIED,
                "应用网络策略",
                "network");
        return body;
    }

    @Override
    public Map<String, Object> publishAcl(Long operatorUserId) {
        boolean integrationMock = runtimeAppConfigService.system().isIntegrationMock();
        Map<String, Object> body = new HashMap<>();
        body.put("published", true);
        body.put("mock", integrationMock);
        body.put("message", integrationMock
                ? "acl publish simulated (set lantu.system.integration-mock=false to wire real infra)"
                : "acl publish acknowledged (implement infra hook here)");
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SYSTEM_ACL_PUBLISHED,
                "发布访问控制策略",
                "acl");
        return body;
    }

    @Override
    public Map<String, Object> getAclRules() {
        List<PlatformRole> roles = platformRoleMapper.selectList(
                new LambdaQueryWrapper<PlatformRole>().orderByAsc(PlatformRole::getRoleCode));
        List<Map<String, Object>> rules = new ArrayList<>();
        if (roles != null) {
            for (PlatformRole r : roles) {
                Map<String, Object> row = new HashMap<>();
                row.put("roleCode", r.getRoleCode());
                row.put("roleName", r.getRoleName());
                row.put("description", r.getDescription());
                row.put("permissions", r.getPermissions() != null ? r.getPermissions() : List.of());
                row.put("isSystem", r.getIsSystem());
                rules.add(row);
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("rules", rules);
        body.put("source", "t_platform_role");
        return body;
    }

    @Override
    public List<String> listDistinctAuditActions() {
        List<String> rows = auditLogMapper.selectDistinctActions();
        return rows != null ? rows : List.of();
    }
}
