package com.lantu.connect.usersettings.service;

import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usersettings.dto.UserStatsVO;
import com.lantu.connect.usersettings.dto.WorkspaceSettingsVO;
import com.lantu.connect.usersettings.dto.WorkspaceUpdateRequest;

import java.util.List;

/**
 * 用户设置UserSettings服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface UserSettingsService {

    WorkspaceSettingsVO getWorkspace(Long userId);

    void updateWorkspace(Long userId, WorkspaceUpdateRequest request);

    List<ApiKey> listApiKeys(Long userId);

    ApiKeyResponse createApiKey(Long userId, ApiKeyCreateRequest request);

    void deleteApiKey(Long userId, String apiKeyId);

    UserStatsVO getStats(Long userId);
}
