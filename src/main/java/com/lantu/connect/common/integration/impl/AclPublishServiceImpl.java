package com.lantu.connect.common.integration.impl;

import com.lantu.connect.common.integration.AclPublishRequest;
import com.lantu.connect.common.integration.AclPublishResult;
import com.lantu.connect.common.integration.AclPublishService;
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
 * ACL发布服务实现
 *
 * @author 王帝
 * @date 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AclPublishServiceImpl implements AclPublishService {

    private final RestTemplate restTemplate;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Override
    @CircuitBreaker(name = "aclPublish", fallbackMethod = "publishFallback")
    @Retry(name = "aclPublish", fallbackMethod = "publishFallback")
    @Bulkhead(name = "aclPublish", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "publishFallback")
    public AclPublishResult publish(AclPublishRequest request) {
        boolean mockMode = runtimeAppConfigService.system().isIntegrationMock();
        String aclApiUrl = runtimeAppConfigService.integration().getAclApiUrl();
        if (mockMode || !StringUtils.hasText(aclApiUrl)) {
            return mockPublish(request);
        }
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aclApiUrl + "/api/v1/acl/publish",
                    request,
                    Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return AclPublishResult.builder()
                        .success(true)
                        .aclId((String) body.get("aclId"))
                        .message("ACL发布成功")
                        .publishedAt(LocalDateTime.now())
                        .status("PUBLISHED")
                        .build();
            }
            return AclPublishResult.builder()
                    .success(false)
                    .message((String) body.getOrDefault("message", "ACL发布失败"))
                    .status("FAILED")
                    .build();
        } catch (RestClientException e) {
            log.error("ACL发布失败: {}", e.getMessage(), e);
            return AclPublishResult.builder()
                    .success(false)
                    .message("ACL发布异常: " + e.getMessage())
                    .status("ERROR")
                    .build();
        }
    }

    public AclPublishResult publishFallback(AclPublishRequest request, Throwable e) {
        log.warn("ACL发布熔断降级: {}", e.getMessage());
        return AclPublishResult.builder()
                .success(false)
                .message("ACL服务暂时不可用，请稍后重试")
                .status("CIRCUIT_OPEN")
                .build();
    }

    @Override
    public AclPublishResult queryStatus(String aclId) {
        boolean mockMode = runtimeAppConfigService.system().isIntegrationMock();
        String aclApiUrl = runtimeAppConfigService.integration().getAclApiUrl();
        if (mockMode || !StringUtils.hasText(aclApiUrl)) {
            return AclPublishResult.builder()
                    .success(true)
                    .aclId(aclId)
                    .status("PUBLISHED")
                    .message("模拟状态查询成功")
                    .build();
        }
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    aclApiUrl + "/api/v1/acl/status/" + aclId,
                    Map.class);
            Map<String, Object> body = response.getBody();
            return AclPublishResult.builder()
                    .success(true)
                    .aclId(aclId)
                    .status((String) body.getOrDefault("status", "UNKNOWN"))
                    .build();
        } catch (RestClientException e) {
            log.error("ACL状态查询失败: {}", e.getMessage(), e);
            return AclPublishResult.builder()
                    .success(false)
                    .message("状态查询失败")
                    .status("ERROR")
                    .build();
        }
    }

    private AclPublishResult mockPublish(AclPublishRequest request) {
        log.info("模拟ACL发布: agent={}, action={}", request.getAgentName(), request.getAction());
        return AclPublishResult.builder()
                .success(true)
                .aclId("ACL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .message("模拟ACL发布成功")
                .publishedAt(LocalDateTime.now())
                .status("PUBLISHED")
                .build();
    }
}
