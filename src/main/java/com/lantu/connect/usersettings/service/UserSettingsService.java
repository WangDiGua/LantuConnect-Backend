package com.lantu.connect.usersettings.service;

import com.lantu.connect.gateway.dto.ResourceGrantVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageOptionVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageUpsertRequest;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageVO;
import com.lantu.connect.usersettings.dto.InvokeEligibilityRequest;
import com.lantu.connect.usersettings.dto.InvokeEligibilityResponse;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyIntegrationPackagePatchRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.entity.ApiKey;
import com.lantu.connect.usersettings.dto.ApiKeyRevokeRequest;
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

    List<IntegrationPackageOptionVO> listActiveIntegrationPackages(Long userId);

    IntegrationPackageVO getOwnedIntegrationPackage(Long userId, String packageId);

    IntegrationPackageVO createOwnedIntegrationPackage(Long userId, IntegrationPackageUpsertRequest request);

    IntegrationPackageVO updateOwnedIntegrationPackage(Long userId, String packageId, IntegrationPackageUpsertRequest request);

    void deleteOwnedIntegrationPackage(Long userId, String packageId);

    ApiKeyResponse createApiKey(Long userId, ApiKeyCreateRequest request);

    void patchApiKeyIntegrationPackage(Long userId, String apiKeyId, ApiKeyIntegrationPackagePatchRequest request);

    void deleteApiKey(Long userId, String apiKeyId);

    List<ResourceGrantVO> listResourceGrantsForApiKey(Long userId, String apiKeyId, String resourceType);

    InvokeEligibilityResponse invokeEligibilityForApiKey(Long userId, String apiKeyId, InvokeEligibilityRequest request);

    void revokeApiKey(Long userId, String apiKeyId, ApiKeyRevokeRequest request, String clientIp);

    /**
     * 轮换 API Key 明文：库内仅存摘要，无法找回旧明文；验证身份后为该记录生成新 secretPlain，旧值立即失效。
     */
    ApiKeyResponse rotateApiKey(Long userId, String apiKeyId, ApiKeyRevokeRequest request, String clientIp);

    UserStatsVO getStats(Long userId);
}
