package com.lantu.connect.usermgmt.service;

import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.usermgmt.dto.ApiKeyCreateRequest;
import com.lantu.connect.usermgmt.dto.ApiKeyDetailResponse;
import com.lantu.connect.usermgmt.dto.ApiKeyResponse;
import com.lantu.connect.usermgmt.dto.CreateUserRequest;
import com.lantu.connect.usermgmt.dto.OrgCreateRequest;
import com.lantu.connect.usermgmt.dto.OrgUpdateRequest;
import com.lantu.connect.usermgmt.dto.OrgNodeVO;
import com.lantu.connect.usermgmt.dto.RoleCreateRequest;
import com.lantu.connect.usermgmt.dto.RoleUpdateRequest;
import com.lantu.connect.usermgmt.dto.UserDetailVO;
import com.lantu.connect.usermgmt.dto.UserOrgBindRequest;
import com.lantu.connect.usermgmt.dto.UserOrgVO;
import com.lantu.connect.usermgmt.dto.UserRoleBindRequest;
import com.lantu.connect.usermgmt.dto.UserRoleReplaceRequest;
import com.lantu.connect.usermgmt.dto.UpdateUserRequest;
import com.lantu.connect.usermgmt.dto.UserBatchUpdateRequest;
import com.lantu.connect.usermgmt.dto.UserQueryRequest;
import com.lantu.connect.usermgmt.entity.ApiKey;

import java.util.List;

/**
 * 用户管理UserMgmt服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface UserMgmtService {

    PageResult<User> listUsers(UserQueryRequest request, Long operatorUserId);

    User createUser(CreateUserRequest request);

    void updateUser(Long id, UpdateUserRequest request);

    void batchUpdateUsers(UserBatchUpdateRequest body);

    UserDetailVO getUserDetail(Long id);

    void deleteUser(Long id);

    List<PlatformRole> listRoles();

    PlatformRole createRole(RoleCreateRequest request);

    void updateRole(Long id, RoleUpdateRequest request);

    void deleteRole(Long id);

    List<ApiKey> listApiKeys();

    ApiKeyDetailResponse getApiKeyDetail(String id);

    ApiKeyResponse createApiKey(ApiKeyCreateRequest request);

    void revokeApiKey(String id);

    void batchRevokeApiKeys(List<String> ids);

    List<OrgNodeVO> getOrgTree();

    OrgNodeVO getOrgById(Long id);

    OrgNodeVO createOrg(OrgCreateRequest request);

    void updateOrg(Long id, OrgUpdateRequest request);

    void deleteOrg(Long id);

    UserOrgVO getUserOrg(Long userId);

    void bindUserOrg(Long userId, UserOrgBindRequest request);

    void unbindUserOrg(Long userId);

    List<PlatformRole> getUserRoles(Long userId);

    void bindUserRoles(Long userId, UserRoleBindRequest request);

    void replaceUserRoles(Long userId, UserRoleReplaceRequest request);

    void unbindUserRole(Long userId, Long roleId);
}
