package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
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

    Map<String, Object> applyNetwork(Long operatorUserId);

    Map<String, Object> publishAcl(Long operatorUserId);
}
