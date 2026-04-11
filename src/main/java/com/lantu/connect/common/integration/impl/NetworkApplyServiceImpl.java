package com.lantu.connect.common.integration.impl;

import com.lantu.connect.common.integration.NetworkApplyRequest;
import com.lantu.connect.common.integration.NetworkApplyResult;
import com.lantu.connect.common.integration.NetworkApplyService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 网络下发服务实现
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkApplyServiceImpl implements NetworkApplyService {

    private final RestTemplate restTemplate;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Override
    @CircuitBreaker(name = "networkApply", fallbackMethod = "applyFallback")
    @Retry(name = "networkApply", fallbackMethod = "applyFallback")
    @Bulkhead(name = "networkApply", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "applyFallback")
    public NetworkApplyResult apply(NetworkApplyRequest request) {
        boolean mockMode = runtimeAppConfigService.system().isIntegrationMock();
        String networkApiUrl = runtimeAppConfigService.integration().getNetworkApiUrl();
        if (mockMode || !StringUtils.hasText(networkApiUrl)) {
            return mockApply(request);
        }
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    networkApiUrl + "/api/v1/network/apply",
                    request,
                    Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return NetworkApplyResult.builder()
                        .success(true)
                        .taskId((String) body.get("taskId"))
                        .message("网络下发成功")
                        .appliedAt(LocalDateTime.now())
                        .status("APPLIED")
                        .build();
            }
            return NetworkApplyResult.builder()
                    .success(false)
                    .message((String) body.getOrDefault("message", "网络下发失败"))
                    .status("FAILED")
                    .build();
        } catch (RestClientException e) {
            log.error("网络下发失败: {}", e.getMessage(), e);
            return NetworkApplyResult.builder()
                    .success(false)
                    .message("网络下发异常: " + e.getMessage())
                    .status("ERROR")
                    .build();
        }
    }

    public NetworkApplyResult applyFallback(NetworkApplyRequest request, Throwable e) {
        log.warn("网络下发熔断降级: {}", e.getMessage());
        return NetworkApplyResult.builder()
                .success(false)
                .message("网络服务暂时不可用，请稍后重试")
                .status("CIRCUIT_OPEN")
                .build();
    }

    @Override
    public NetworkApplyResult queryStatus(String taskId) {
        boolean mockMode = runtimeAppConfigService.system().isIntegrationMock();
        String networkApiUrl = runtimeAppConfigService.integration().getNetworkApiUrl();
        if (mockMode || !StringUtils.hasText(networkApiUrl)) {
            return NetworkApplyResult.builder()
                    .success(true)
                    .taskId(taskId)
                    .status("APPLIED")
                    .message("模拟状态查询成功")
                    .build();
        }
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    networkApiUrl + "/api/v1/network/status/" + taskId,
                    Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            return NetworkApplyResult.builder()
                    .success(true)
                    .taskId(taskId)
                    .status((String) body.getOrDefault("status", "UNKNOWN"))
                    .build();
        } catch (RestClientException e) {
            log.error("网络状态查询失败: {}", e.getMessage(), e);
            return NetworkApplyResult.builder()
                    .success(false)
                    .message("状态查询失败")
                    .status("ERROR")
                    .build();
        }
    }

    private NetworkApplyResult mockApply(NetworkApplyRequest request) {
        log.info("模拟网络下发: agent={}, type={}", request.getAgentName(), request.getNetworkType());
        return NetworkApplyResult.builder()
                .success(true)
                .taskId("NET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .message("模拟网络下发成功")
                .appliedAt(LocalDateTime.now())
                .networkId("NET-" + System.currentTimeMillis())
                .status("APPLIED")
                .build();
    }
}
