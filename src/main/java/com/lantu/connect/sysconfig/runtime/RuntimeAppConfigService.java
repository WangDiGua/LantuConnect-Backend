package com.lantu.connect.sysconfig.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.config.BackendContractProperties;
import com.lantu.connect.common.config.CorsBootstrapProperties;
import com.lantu.connect.common.config.FileBootstrapProperties;
import com.lantu.connect.common.config.GatewayInvokeProperties;
import com.lantu.connect.common.config.GeoIpProperties;
import com.lantu.connect.common.config.IntegrationProperties;
import com.lantu.connect.common.config.JwtTokenLifetimeProperties;
import com.lantu.connect.common.config.LantuConnectLoggingProperties;
import com.lantu.connect.common.config.LantuSystemProperties;
import com.lantu.connect.common.config.LegacyApiDeprecationProperties;
import com.lantu.connect.common.config.NotificationProperties;
import com.lantu.connect.common.idempotency.IdempotencyProperties;
import com.lantu.connect.gateway.config.SkillPackImportProperties;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.mapper.SystemParamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 运行时配置：库表 {@code t_system_param} 中 key={@value #PARAM_KEY} 的 JSON
 * 与 application.yml 默认值合并（仅覆盖 JSON 中出现的字段），短缓存后自动刷新。
 * <p>
 * 前端可通过既有 {@code PUT /system-config/params} 写入该 key；写入后请调用失效或等待数秒缓存过期。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuntimeAppConfigService {

    public static final String PARAM_KEY = "runtime_app_config";

    private static final long CACHE_TTL_MS = 3000L;

    private final SystemParamMapper systemParamMapper;
    private final ObjectMapper objectMapper;

    private final LegacyApiDeprecationProperties yamlApiDeprecation;
    private final IdempotencyProperties yamlIdempotency;
    private final SkillPackImportProperties yamlSkillPackImport;
    private final BackendContractProperties yamlContract;
    private final IntegrationProperties yamlIntegration;
    private final NotificationProperties yamlNotification;
    private final LantuConnectLoggingProperties yamlLogging;
    private final LantuSystemProperties yamlSystem;
    private final GatewayInvokeProperties yamlGateway;
    private final GeoIpProperties yamlGeoIp;
    private final CorsBootstrapProperties yamlCors;
    private final FileBootstrapProperties yamlFile;
    private final JwtTokenLifetimeProperties yamlJwtLifetime;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile JsonNode cachedRoot;
    private volatile long cacheUntilMs;

    public void invalidate() {
        lock.writeLock().lock();
        try {
            cachedRoot = null;
            cacheUntilMs = 0L;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private JsonNode rootOverlay() {
        long now = System.currentTimeMillis();
        lock.readLock().lock();
        try {
            if (cachedRoot != null && now < cacheUntilMs) {
                return cachedRoot;
            }
        } finally {
            lock.readLock().unlock();
        }
        lock.writeLock().lock();
        try {
            now = System.currentTimeMillis();
            if (cachedRoot != null && now < cacheUntilMs) {
                return cachedRoot;
            }
            SystemParam row = systemParamMapper.selectById(PARAM_KEY);
            if (row == null || !StringUtils.hasText(row.getValue())) {
                cachedRoot = null;
            } else {
                try {
                    cachedRoot = objectMapper.readTree(row.getValue().trim());
                } catch (JsonProcessingException e) {
                    log.warn("{} JSON 无效，忽略 DB 覆盖: {}", PARAM_KEY, e.getMessage());
                    cachedRoot = null;
                }
            }
            cacheUntilMs = now + CACHE_TTL_MS;
            return cachedRoot;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public LegacyApiDeprecationProperties apiDeprecation() {
        return mergeAt(yamlApiDeprecation, "/lantu/api-deprecation");
    }

    public IdempotencyProperties idempotency() {
        return mergeAt(yamlIdempotency, "/lantu/idempotency");
    }

    public SkillPackImportProperties skillPackImport() {
        return mergeAt(yamlSkillPackImport, "/lantu/skill-pack-import");
    }

    /** 契约元数据（info 贡献器等）；与 YAML {@link BackendContractProperties} 同结构。 */
    public BackendContractProperties contract() {
        return mergeAt(yamlContract, "/lantu/contract");
    }

    public IntegrationProperties integration() {
        return mergeAt(yamlIntegration, "/lantu/integration");
    }

    public NotificationProperties notification() {
        return mergeAt(yamlNotification, "/lantu/notification");
    }

    public LantuConnectLoggingProperties logging() {
        return mergeAt(yamlLogging, "/lantu/logging");
    }

    public LantuSystemProperties system() {
        return mergeAt(yamlSystem, "/lantu/system");
    }

    public GatewayInvokeProperties gateway() {
        return mergeAt(yamlGateway, "/lantu/gateway");
    }

    public GeoIpProperties geoIp() {
        return mergeAt(yamlGeoIp, "/geoip");
    }

    public CorsBootstrapProperties cors() {
        return mergeAt(yamlCors, "/cors");
    }

    public FileBootstrapProperties file() {
        return mergeAt(yamlFile, "/file");
    }

    public JwtTokenLifetimeProperties jwtTokenLifetime() {
        return mergeAt(yamlJwtLifetime, "/jwt");
    }

    private <T> T mergeAt(T yamlBean, String jsonPointer) {
        JsonNode root = rootOverlay();
        if (root == null || yamlBean == null) {
            return cloneYaml(yamlBean);
        }
        JsonNode sub = root.at(jsonPointer);
        if (sub == null || sub.isMissingNode() || sub.isNull()) {
            return cloneYaml(yamlBean);
        }
        try {
            T copy = cloneYaml(yamlBean);
            objectMapper.readerForUpdating(copy).readValue(sub.traverse());
            return copy;
        } catch (IOException e) {
            log.warn("合并运行时配置失败 pointer={}：{}", jsonPointer, e.getMessage());
            return cloneYaml(yamlBean);
        }
    }

    private <T> T cloneYaml(T yamlBean) {
        if (yamlBean == null) {
            return null;
        }
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(yamlBean), getClass(yamlBean));
        } catch (IOException e) {
            throw new IllegalStateException("无法克隆默认配置: " + yamlBean.getClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getClass(T yamlBean) {
        return (Class<T>) yamlBean.getClass();
    }
}
