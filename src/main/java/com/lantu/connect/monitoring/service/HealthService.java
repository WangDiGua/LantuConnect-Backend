package com.lantu.connect.monitoring.service;

import com.lantu.connect.monitoring.dto.CircuitBreakerManualRequest;
import com.lantu.connect.monitoring.dto.CircuitBreakerUpdateRequest;
import com.lantu.connect.monitoring.dto.HealthConfigUpsertRequest;
import com.lantu.connect.monitoring.entity.CircuitBreaker;
import com.lantu.connect.monitoring.entity.HealthConfig;

import java.util.List;

/**
 * 监控Health服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface HealthService {

    List<HealthConfig> listConfigs();

    Long saveConfig(HealthConfigUpsertRequest request);

    void deleteConfig(Long id);

    List<CircuitBreaker> listCircuitBreakers();

    void updateCircuitBreaker(Long id, CircuitBreakerUpdateRequest request);

    void manualBreak(CircuitBreakerManualRequest request);

    void manualBreakById(Long id, Integer openDurationSeconds);

    void recover(String serviceKey);

    void recoverById(Long id);
}
