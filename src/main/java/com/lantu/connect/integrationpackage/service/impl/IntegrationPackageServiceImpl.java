package com.lantu.connect.integrationpackage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageItemDTO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageOptionVO;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageUpsertRequest;
import com.lantu.connect.integrationpackage.dto.IntegrationPackageVO;
import com.lantu.connect.integrationpackage.entity.IntegrationPackage;
import com.lantu.connect.integrationpackage.mapper.IntegrationPackageMapper;
import com.lantu.connect.integrationpackage.service.IntegrationPackageMembershipService;
import com.lantu.connect.integrationpackage.service.IntegrationPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationPackageServiceImpl implements IntegrationPackageService {

    private final IntegrationPackageMapper integrationPackageMapper;
    private final JdbcTemplate jdbcTemplate;
    private final IntegrationPackageMembershipService integrationPackageMembershipService;

    @Override
    public List<IntegrationPackageOptionVO> listOwnedForUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        List<IntegrationPackage> rows = integrationPackageMapper.selectList(
                new LambdaQueryWrapper<IntegrationPackage>()
                        .eq(IntegrationPackage::getOwnerUserId, userId)
                        .orderByDesc(IntegrationPackage::getUpdateTime));
        List<IntegrationPackageOptionVO> out = new ArrayList<>();
        for (IntegrationPackage p : rows) {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_integration_package_item WHERE package_id = ?",
                    Integer.class,
                    p.getId());
            out.add(IntegrationPackageOptionVO.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .description(p.getDescription())
                    .status(p.getStatus())
                    .itemCount(cnt != null ? cnt : 0)
                    .build());
        }
        return out;
    }

    @Override
    public IntegrationPackageVO getOwnedByUser(String id, Long userId) {
        assertLoggedIn(userId);
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "id 不能为空");
        }
        IntegrationPackage p = integrationPackageMapper.selectById(id.trim());
        assertOwner(p, userId);
        return toVo(p, loadItems(p.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationPackageVO createOwnedByUser(Long userId, IntegrationPackageUpsertRequest request) {
        assertLoggedIn(userId);
        assertResourcesExist(request.getItems());
        String id = UUID.randomUUID().toString();
        IntegrationPackage p = new IntegrationPackage();
        p.setId(id);
        p.setName(request.getName().trim());
        p.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        p.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim().toLowerCase(Locale.ROOT) : "active");
        p.setOwnerUserId(userId);
        p.setCreatedBy(String.valueOf(userId));
        integrationPackageMapper.insert(p);
        replaceItems(id, request.getItems());
        integrationPackageMembershipService.evict(id);
        return getOwnedByUser(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationPackageVO updateOwnedByUser(String id, Long userId, IntegrationPackageUpsertRequest request) {
        assertLoggedIn(userId);
        IntegrationPackage p = integrationPackageMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "集成套餐不存在");
        }
        assertOwner(p, userId);
        assertResourcesExist(request.getItems());
        p.setName(request.getName().trim());
        p.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        if (StringUtils.hasText(request.getStatus())) {
            p.setStatus(request.getStatus().trim().toLowerCase(Locale.ROOT));
        }
        integrationPackageMapper.updateById(p);
        replaceItems(id, request.getItems());
        integrationPackageMembershipService.evict(id);
        return getOwnedByUser(id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnedByUser(String id, Long userId) {
        assertLoggedIn(userId);
        IntegrationPackage p = integrationPackageMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "集成套餐不存在");
        }
        assertOwner(p, userId);
        jdbcTemplate.update("UPDATE t_api_key SET integration_package_id = NULL WHERE integration_package_id = ?", id);
        jdbcTemplate.update("DELETE FROM t_integration_package_item WHERE package_id = ?", id);
        integrationPackageMapper.deleteById(id);
        integrationPackageMembershipService.evict(id);
    }

    @Override
    public void assertResourcesExist(List<IntegrationPackageItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "套餐至少包含一条资源");
        }
        for (IntegrationPackageItemDTO it : items) {
            if (it.getResourceId() == null || !StringUtils.hasText(it.getResourceType())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "resourceType、resourceId 不能为空");
            }
            String t = it.getResourceType().trim().toLowerCase(Locale.ROOT);
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_resource WHERE deleted = 0 AND resource_type = ? AND id = ? AND status = 'published'",
                    Integer.class,
                    t,
                    it.getResourceId());
            if (cnt == null || cnt == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "资源不存在或未上线: " + t + ":" + it.getResourceId());
            }
        }
    }

    private static void assertLoggedIn(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }

    private static void assertOwner(IntegrationPackage p, Long userId) {
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "集成套餐不存在");
        }
        if (p.getOwnerUserId() == null || !userId.equals(p.getOwnerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该集成套餐");
        }
    }

    private List<IntegrationPackageItemDTO> loadItems(String packageId) {
        List<IntegrationPackageItemDTO> list = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT resource_type, resource_id FROM t_integration_package_item WHERE package_id = ? ORDER BY resource_type, resource_id",
                packageId);
        for (Map<String, Object> row : rows) {
            IntegrationPackageItemDTO d = new IntegrationPackageItemDTO();
            d.setResourceType(String.valueOf(row.get("resource_type")));
            Object idObj = row.get("resource_id");
            d.setResourceId(idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj)));
            list.add(d);
        }
        return list;
    }

    private void replaceItems(String packageId, List<IntegrationPackageItemDTO> items) {
        jdbcTemplate.update("DELETE FROM t_integration_package_item WHERE package_id = ?", packageId);
        if (items == null) {
            return;
        }
        for (IntegrationPackageItemDTO it : items) {
            String t = it.getResourceType().trim().toLowerCase(Locale.ROOT);
            jdbcTemplate.update(
                    "INSERT INTO t_integration_package_item (package_id, resource_type, resource_id) VALUES (?,?,?)",
                    packageId,
                    t,
                    it.getResourceId());
        }
    }

    private static IntegrationPackageVO toVo(IntegrationPackage p, List<IntegrationPackageItemDTO> items) {
        return IntegrationPackageVO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdBy(p.getCreatedBy())
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .items(items)
                .build();
    }
}
