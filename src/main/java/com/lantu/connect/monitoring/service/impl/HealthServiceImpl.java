package com.lantu.connect.monitoring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.monitoring.dto.CircuitBreakerManualRequest;
import com.lantu.connect.monitoring.dto.CircuitBreakerUpdateRequest;
import com.lantu.connect.monitoring.dto.HealthConfigUpsertRequest;
import com.lantu.connect.monitoring.entity.CircuitBreaker;
import com.lantu.connect.monitoring.entity.HealthConfig;
import com.lantu.connect.monitoring.mapper.CircuitBreakerMapper;
import com.lantu.connect.monitoring.mapper.HealthConfigMapper;
import com.lantu.connect.monitoring.service.HealthService;
import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 监控Health服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private final HealthConfigMapper healthConfigMapper;
    private final CircuitBreakerMapper circuitBreakerMapper;
    private final JdbcTemplate jdbcTemplate;
    private final RealtimePushService realtimePushService;

    @Override
    public List<HealthConfig> listConfigs() {
        return healthConfigMapper.selectList(new LambdaQueryWrapper<HealthConfig>()
                .isNotNull(HealthConfig::getCheckType)
                .orderByAsc(HealthConfig::getId));
    }

    /**
     * 健康配置新增/更新：绑定资源主表并写入治理配置。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveConfig(HealthConfigUpsertRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.getId() == null) {
            ResourceRef resource = findResourceByCode(request.getTargetName());
            HealthConfig existingByResource = healthConfigMapper.selectOne(
                    new LambdaQueryWrapper<HealthConfig>().eq(HealthConfig::getResourceId, resource.id()).last("LIMIT 1"));
            if (existingByResource != null) {
                existingByResource.setAgentType(resource.type());
                existingByResource.setAgentName(request.getTargetName());
                existingByResource.setDisplayName(resource.displayName());
                existingByResource.setCheckType("http");
                existingByResource.setCheckUrl(
                        request.getTargetUrl() != null ? request.getTargetUrl() : "http://127.0.0.1");
                existingByResource.setIntervalSec(request.getCheckIntervalSec() != null ? request.getCheckIntervalSec() : 60);
                existingByResource.setHealthyThreshold(3);
                existingByResource.setTimeoutSec(10);
                existingByResource.setHealthStatus(request.getEnabled() != null && request.getEnabled() == 0
                        ? "disabled"
                        : "healthy");
                existingByResource.setUpdateTime(now);
                healthConfigMapper.updateById(existingByResource);
                realtimePushService.pushHealthConfigChanged(
                        existingByResource.getResourceId(),
                        existingByResource.getAgentType(),
                        existingByResource.getAgentName(),
                        existingByResource.getDisplayName(),
                        existingByResource.getCheckType(),
                        existingByResource.getHealthStatus(),
                        now,
                        null);
                return existingByResource.getId();
            }
            HealthConfig entity = new HealthConfig();
            entity.setResourceId(resource.id());
            entity.setAgentType(resource.type());
            entity.setAgentName(request.getTargetName());
            entity.setDisplayName(resource.displayName());
            entity.setCheckType("http");
            entity.setCheckUrl(request.getTargetUrl() != null ? request.getTargetUrl() : "http://127.0.0.1");
            entity.setIntervalSec(request.getCheckIntervalSec() != null ? request.getCheckIntervalSec() : 60);
            entity.setHealthyThreshold(3);
            entity.setTimeoutSec(10);
            entity.setHealthStatus(request.getEnabled() != null && request.getEnabled() == 0 ? "disabled" : "healthy");
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            healthConfigMapper.insert(entity);
            realtimePushService.pushHealthConfigChanged(
                    entity.getResourceId(),
                    entity.getAgentType(),
                    entity.getAgentName(),
                    entity.getDisplayName(),
                    entity.getCheckType(),
                    entity.getHealthStatus(),
                    now,
                    null);
            return entity.getId();
        }
        HealthConfig existing = healthConfigMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getTargetName() != null) {
            ResourceRef resource = findResourceByCode(request.getTargetName());
            existing.setResourceId(resource.id());
            existing.setAgentType(resource.type());
            existing.setAgentName(request.getTargetName());
            existing.setDisplayName(resource.displayName());
        }
        if (request.getTargetUrl() != null) {
            existing.setCheckUrl(request.getTargetUrl());
        }
        if (request.getCheckIntervalSec() != null) {
            existing.setIntervalSec(request.getCheckIntervalSec());
        }
        String prevHealth = existing.getHealthStatus();
        if (request.getEnabled() != null) {
            existing.setHealthStatus(request.getEnabled() == 0 ? "disabled" : "healthy");
        }
        existing.setUpdateTime(now);
        healthConfigMapper.updateById(existing);
        if (!Objects.equals(
                prevHealth == null ? null : prevHealth.trim().toLowerCase(),
                existing.getHealthStatus() == null ? null : existing.getHealthStatus().trim().toLowerCase())) {
            realtimePushService.pushHealthConfigChanged(
                    existing.getResourceId(),
                    existing.getAgentType(),
                    existing.getAgentName(),
                    existing.getDisplayName(),
                    existing.getCheckType(),
                    existing.getHealthStatus(),
                    now,
                    prevHealth);
        }
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        if (healthConfigMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        healthConfigMapper.deleteById(id);
    }

    @Override
    public List<CircuitBreaker> listCircuitBreakers() {
        return circuitBreakerMapper.selectList(new LambdaQueryWrapper<CircuitBreaker>()
                .isNotNull(CircuitBreaker::getCurrentState)
                .orderByAsc(CircuitBreaker::getId));
    }

    /**
     * 熔断器配置更新：服务键切换时重新绑定到资源主表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCircuitBreaker(Long id, CircuitBreakerUpdateRequest request) {
        CircuitBreaker existing = circuitBreakerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getServiceKey() != null) {
            ResourceRef resource = findResourceByCode(request.getServiceKey());
            existing.setResourceId(resource.id());
            existing.setResourceType(resource.type());
            existing.setAgentName(request.getServiceKey());
            existing.setDisplayName(resource.displayName());
        }
        if (request.getOpenDurationSeconds() != null) {
            existing.setOpenDurationSec(request.getOpenDurationSeconds());
        }
        existing.setUpdateTime(LocalDateTime.now());
        circuitBreakerMapper.updateById(existing);
    }

    /**
     * 手工开断：不存在则创建，存在则覆盖为 OPEN 并刷新开断窗口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualBreak(CircuitBreakerManualRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ResourceRef resource = findResourceByCode(request.getServiceKey());
        CircuitBreaker existing = circuitBreakerMapper.selectOne(
                new LambdaQueryWrapper<CircuitBreaker>().eq(CircuitBreaker::getAgentName, request.getServiceKey()));
        String prevState = existing == null ? null : existing.getCurrentState();
        int duration = request.getOpenDurationSeconds() != null ? request.getOpenDurationSeconds() : 60;
        if (existing == null) {
            CircuitBreaker cb = new CircuitBreaker();
            cb.setResourceId(resource.id());
            cb.setResourceType(resource.type());
            cb.setAgentName(request.getServiceKey());
            cb.setDisplayName(resource.displayName());
            cb.setCurrentState(CircuitBreaker.STATE_OPEN);
            cb.setFailureCount(0L);
            cb.setLastOpenedAt(now);
            cb.setOpenDurationSec(duration);
            cb.setCreateTime(now);
            cb.setUpdateTime(now);
            circuitBreakerMapper.insert(cb);
            realtimePushService.pushCircuitStateChanged(
                    resource.id(),
                    resource.type(),
                    request.getServiceKey(),
                    resource.displayName(),
                    CircuitBreaker.STATE_OPEN,
                    prevState);
        } else {
            existing.setResourceId(resource.id());
            existing.setResourceType(resource.type());
            existing.setCurrentState(CircuitBreaker.STATE_OPEN);
            existing.setDisplayName(resource.displayName());
            existing.setLastOpenedAt(now);
            existing.setOpenDurationSec(duration);
            existing.setUpdateTime(now);
            circuitBreakerMapper.updateById(existing);
            if (!CircuitBreaker.STATE_OPEN.equals(prevState)) {
                realtimePushService.pushCircuitStateChanged(
                        resource.id(),
                        resource.type(),
                        request.getServiceKey(),
                        resource.displayName(),
                        CircuitBreaker.STATE_OPEN,
                        prevState);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualBreakById(Long id, Integer openDurationSeconds) {
        CircuitBreaker cb = circuitBreakerMapper.selectById(id);
        if (cb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        CircuitBreakerManualRequest req = new CircuitBreakerManualRequest();
        req.setServiceKey(cb.getAgentName());
        req.setOpenDurationSeconds(openDurationSeconds);
        manualBreak(req);
    }

    /**
     * 手工恢复：重置为 CLOSED 并清空失败计数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recover(String serviceKey) {
        CircuitBreaker existing = circuitBreakerMapper.selectOne(
                new LambdaQueryWrapper<CircuitBreaker>().eq(CircuitBreaker::getAgentName, serviceKey));
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        String prevState = existing.getCurrentState();
        existing.setCurrentState(CircuitBreaker.STATE_CLOSED);
        existing.setFailureCount(0L);
        existing.setLastOpenedAt(null);
        existing.setUpdateTime(LocalDateTime.now());
        circuitBreakerMapper.updateById(existing);
        if (existing.getResourceId() != null && !CircuitBreaker.STATE_CLOSED.equals(prevState)) {
            realtimePushService.pushCircuitStateChanged(
                    existing.getResourceId(),
                    existing.getResourceType(),
                    existing.getAgentName(),
                    existing.getDisplayName(),
                    CircuitBreaker.STATE_CLOSED,
                    prevState);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverById(Long id) {
        CircuitBreaker cb = circuitBreakerMapper.selectById(id);
        if (cb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        recover(cb.getAgentName());
    }

    private ResourceRef findResourceByCode(String resourceCode) {
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, resource_type, display_name FROM t_resource WHERE deleted = 0 AND resource_code = ? LIMIT 1",
                resourceCode);
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "目标资源不存在: " + resourceCode);
        }
        java.util.Map<String, Object> row = rows.get(0);
        Long id = ((Number) row.get("id")).longValue();
        String type = String.valueOf(row.get("resource_type"));
        String displayName = String.valueOf(row.get("display_name"));
        return new ResourceRef(id, type, displayName);
    }

    private record ResourceRef(Long id, String type, String displayName) {}
}
