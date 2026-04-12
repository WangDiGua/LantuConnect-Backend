package com.lantu.connect.integrationpackage.service;

import com.lantu.connect.integrationpackage.dto.IntegrationPackageItemDTO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageOptionVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageUpsertRequest;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageVO;

import java.util.List;

/** 集成套餐：由终端用户自建并维护；绑定到个人 API Key 后网关仅允许包内资源。 */
public interface IntegrationPackageService {

    /** 当前用户创建的全部套餐（列表/管理；Key 下拉请筛 status=active） */
    List<IntegrationPackageOptionVO> listOwnedForUser(Long userId);

    IntegrationPackageVO getOwnedByUser(String id, Long userId);

    IntegrationPackageVO createOwnedByUser(Long userId, IntegrationPackageUpsertRequest request);

    IntegrationPackageVO updateOwnedByUser(String id, Long userId, IntegrationPackageUpsertRequest request);

    void deleteOwnedByUser(String id, Long userId);

    /** 校验资源存在、已上线且未删除 */
    void assertResourcesExist(List<IntegrationPackageItemDTO> items);
}
