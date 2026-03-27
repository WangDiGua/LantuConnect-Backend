package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
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
import com.lantu.connect.notification.service.NotificationEventCodes;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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

    @Value("${lantu.system.integration-mock:true}")
    private boolean integrationMock;

    private final SystemParamMapper systemParamMapper;
    private final SecuritySettingMapper securitySettingMapper;
    private final AuditLogMapper auditLogMapper;
    private final SystemNotificationFacade systemNotificationFacade;

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
        q.orderByDesc(AuditLog::getCreateTime);
        Page<AuditLog> result = auditLogMapper.selectPage(page, q);
        return PageResults.from(result);
    }

    @Override
    public Map<String, Object> applyNetwork(Long operatorUserId) {
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
}
