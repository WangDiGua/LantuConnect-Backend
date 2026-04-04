package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.sysconfig.dto.AclPathRulePayload;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 系统配置SystemParamFacade服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class SystemParamFacadeServiceImpl implements SystemParamFacadeService {

    /** 管理端 IP 白名单：JSON 字符串数组，如 ["10.0.0.0/8"] */
    public static final String PARAM_ADMIN_NETWORK_ALLOWLIST = "admin_network_allowlist";
    /** API 路径级 ACL：JSON 数组，元素含 id / path / roles */
    public static final String PARAM_API_PATH_ACL_RULES = "api_path_acl_rules";

    private final SystemParamMapper systemParamMapper;
    private final SecuritySettingMapper securitySettingMapper;
    private final AuditLogMapper auditLogMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final SystemNotificationFacade systemNotificationFacade;
    private final RuntimeAppConfigService runtimeAppConfigService;
    private final ObjectMapper objectMapper;

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
        if (StringUtils.hasText(request.getResourceType())) {
            String rt = request.getResourceType().trim();
            q.and(w -> w.like(AuditLog::getAction, rt).or().like(AuditLog::getResource, rt));
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
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyNetwork(Long operatorUserId, List<String> rules) {
        List<String> normalized = (rules == null ? List.<String>of() : rules).stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize network allowlist failed", e);
        }
        upsertInternalParam(PARAM_ADMIN_NETWORK_ALLOWLIST, json,
                "管理端 IP 白名单 CIDR 列表（JSON 字符串数组）", "security");

        boolean integrationMock = runtimeAppConfigService.system().isIntegrationMock();
        Map<String, Object> body = new HashMap<>();
        body.put("applied", true);
        body.put("mock", integrationMock);
        body.put("rules", normalized);
        body.put("rulesCount", normalized.size());
        body.put("message", integrationMock
                ? "白名单已落库；集成模拟模式下未调用外部网关（lantu.system.integration-mock=false 时可接真实下发）"
                : "白名单已落库；网关联动请同步 infra 管道");
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SYSTEM_NETWORK_APPLIED,
                "应用网络策略",
                "network");
        return body;
    }

    @Override
    public List<String> getNetworkAllowlist() {
        SystemParam p = systemParamMapper.selectById(PARAM_ADMIN_NETWORK_ALLOWLIST);
        if (p == null || !StringUtils.hasText(p.getValue())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(p.getValue().trim(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishAcl(Long operatorUserId, List<AclPathRulePayload> rules) {
        List<AclPathRulePayload> normalized = normalizeAclRules(rules);
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("serialize acl rules failed", e);
        }
        upsertInternalParam(PARAM_API_PATH_ACL_RULES, json,
                "API 路径级 ACL 规则（JSON：[{id,path,roles}]）", "security");

        boolean integrationMock = runtimeAppConfigService.system().isIntegrationMock();
        Map<String, Object> body = new HashMap<>();
        body.put("published", true);
        body.put("mock", integrationMock);
        body.put("ruleCount", normalized.size());
        body.put("message", integrationMock
                ? "ACL 已落库；集成模拟模式下未推送 Casbin（关闭 mock 后可接同步任务）"
                : "ACL 已落库；请确认网关/网关侧 Casbin 加载任务已订阅");
        systemNotificationFacade.notifySystemSecurityOperation(
                operatorUserId,
                NotificationEventCodes.SYSTEM_ACL_PUBLISHED,
                "发布访问控制策略",
                "acl");
        return body;
    }

    @Override
    public Map<String, Object> getAclRules() {
        List<AclPathRulePayload> pathRules = readPathAclRulesFromParam();
        List<Map<String, Object>> asMaps = new ArrayList<>();
        for (AclPathRulePayload r : pathRules) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("path", r.getPath());
            row.put("roles", r.getRoles());
            asMaps.add(row);
        }
        List<PlatformRole> roles = platformRoleMapper.selectList(
                new LambdaQueryWrapper<PlatformRole>().orderByAsc(PlatformRole::getRoleCode));
        List<Map<String, Object>> roleCatalog = new ArrayList<>();
        if (roles != null) {
            for (PlatformRole r : roles) {
                Map<String, Object> row = new HashMap<>();
                row.put("roleCode", r.getRoleCode());
                row.put("roleName", r.getRoleName());
                roleCatalog.add(row);
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("rules", asMaps);
        body.put("source", PARAM_API_PATH_ACL_RULES);
        body.put("roleCatalog", roleCatalog);
        return body;
    }

    private void upsertInternalParam(String key, String value, String description, String category) {
        LocalDateTime now = LocalDateTime.now();
        SystemParam existing = systemParamMapper.selectById(key);
        if (existing == null) {
            SystemParam entity = new SystemParam();
            entity.setKey(key);
            entity.setValue(value);
            entity.setDescription(description);
            entity.setType("json");
            entity.setCategory(category);
            entity.setEditable(true);
            entity.setUpdateTime(now);
            systemParamMapper.insert(entity);
        } else {
            existing.setValue(value);
            existing.setUpdateTime(now);
            if (description != null) {
                existing.setDescription(description);
            }
            systemParamMapper.updateById(existing);
        }
    }

    private List<AclPathRulePayload> readPathAclRulesFromParam() {
        SystemParam p = systemParamMapper.selectById(PARAM_API_PATH_ACL_RULES);
        if (p == null || !StringUtils.hasText(p.getValue())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(p.getValue().trim(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<AclPathRulePayload> normalizeAclRules(List<AclPathRulePayload> raw) {
        if (raw == null) {
            return List.of();
        }
        List<AclPathRulePayload> out = new ArrayList<>();
        for (AclPathRulePayload r : raw) {
            if (r == null) {
                continue;
            }
            String path = r.getPath() == null ? "" : r.getPath().trim();
            if (!StringUtils.hasText(path)) {
                continue;
            }
            AclPathRulePayload n = new AclPathRulePayload();
            n.setPath(path);
            n.setRoles(r.getRoles() == null ? "" : r.getRoles().trim());
            String id = r.getId() == null ? "" : r.getId().trim();
            n.setId(StringUtils.hasText(id) ? id : "r-" + UUID.randomUUID());
            out.add(n);
        }
        return out;
    }

    @Override
    public List<String> listDistinctAuditActions() {
        List<String> rows = auditLogMapper.selectDistinctActions();
        return rows != null ? rows : List.of();
    }
}
