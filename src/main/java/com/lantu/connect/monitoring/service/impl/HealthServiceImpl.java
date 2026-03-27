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
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public List<HealthConfig> listConfigs() {
        return healthConfigMapper.selectList(new LambdaQueryWrapper<HealthConfig>().orderByAsc(HealthConfig::getId));
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
        if (request.getEnabled() != null) {
            existing.setHealthStatus(request.getEnabled() == 0 ? "disabled" : "healthy");
        }
        existing.setUpdateTime(now);
        healthConfigMapper.updateById(existing);
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
        return circuitBreakerMapper.selectList(new LambdaQueryWrapper<CircuitBreaker>().orderByAsc(CircuitBreaker::getId));
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
        } else {
            existing.setResourceId(resource.id());
            existing.setResourceType(resource.type());
            existing.setCurrentState(CircuitBreaker.STATE_OPEN);
            existing.setDisplayName(resource.displayName());
            existing.setLastOpenedAt(now);
            existing.setOpenDurationSec(duration);
            existing.setUpdateTime(now);
            circuitBreakerMapper.updateById(existing);
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
        existing.setCurrentState(CircuitBreaker.STATE_CLOSED);
        existing.setFailureCount(0L);
        existing.setLastOpenedAt(null);
        existing.setUpdateTime(LocalDateTime.now());
        circuitBreakerMapper.updateById(existing);
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
