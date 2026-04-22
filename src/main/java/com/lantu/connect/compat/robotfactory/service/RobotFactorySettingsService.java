package com.lantu.connect.compat.robotfactory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsDTO;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsHealthDTO;
import com.lantu.connect.compat.robotfactory.dto.RobotFactorySettingsUpsertRequest;
import com.lantu.connect.sysconfig.entity.SystemParam;
import com.lantu.connect.sysconfig.mapper.SystemParamMapper;
import com.lantu.connect.task.support.TaskDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotFactorySettingsService {

    public static final String PARAM_KEY = "robot_factory_adapter_config";

    private static final String TASK_NAME = "RobotFactoryExternalDbHealthCheck";
    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final int DEFAULT_IDLE_MINUTES = 10;
    private static final int DEFAULT_MAX_LIFETIME_MINUTES = 30;
    private static final int DEFAULT_INVOKE_TIMEOUT_SECONDS = 120;

    private final SystemParamMapper systemParamMapper;
    private final ObjectMapper objectMapper;
    private final TaskDistributedLock taskDistributedLock;

    private volatile RobotFactorySettingsHealthDTO latestHealth = RobotFactorySettingsHealthDTO.builder()
            .configured(false)
            .databaseReachable(false)
            .externalTableReady(false)
            .status("unconfigured")
            .message("软件工厂适配尚未配置外部数据库连接")
            .checkedAt(null)
            .build();

    public RobotFactorySettingsDTO getSettings() {
        SystemParam param = systemParamMapper.selectById(PARAM_KEY);
        if (param == null || !StringUtils.hasText(param.getValue())) {
            return defaultSettings(null);
        }
        try {
            RobotFactorySettingsDTO parsed = objectMapper.readValue(param.getValue(), RobotFactorySettingsDTO.class);
            RobotFactorySettingsDTO normalized = normalize(parsed);
            normalized.setUpdateTime(param.getUpdateTime());
            return normalized;
        } catch (Exception e) {
            log.warn("robot-factory settings parse failed: {}", e.getMessage());
            return defaultSettings(param.getUpdateTime());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RobotFactorySettingsDTO saveSettings(RobotFactorySettingsUpsertRequest request) {
        RobotFactorySettingsDTO settings = normalize(toSettings(request));
        LocalDateTime now = LocalDateTime.now();
        SystemParam existing = systemParamMapper.selectById(PARAM_KEY);
        String json = toJson(settings);
        if (existing == null) {
            SystemParam entity = new SystemParam();
            entity.setKey(PARAM_KEY);
            entity.setValue(json);
            entity.setType("json");
            entity.setCategory("compat");
            entity.setEditable(true);
            entity.setDescription("软件工厂适配配置(JSON)");
            entity.setUpdateTime(now);
            systemParamMapper.insert(entity);
        } else {
            existing.setValue(json);
            existing.setType("json");
            existing.setCategory("compat");
            existing.setEditable(true);
            existing.setDescription("软件工厂适配配置(JSON)");
            existing.setUpdateTime(now);
            systemParamMapper.updateById(existing);
        }
        settings.setUpdateTime(now);
        latestHealth = probe(settings);
        return settings;
    }

    public RobotFactorySettingsHealthDTO testConnection(RobotFactorySettingsUpsertRequest request) {
        return probe(normalize(toSettings(request)));
    }

    public RobotFactorySettingsHealthDTO getHealthStatus() {
        RobotFactorySettingsHealthDTO snapshot = latestHealth;
        if (snapshot.getCheckedAt() == null) {
            snapshot = probe(getSettings());
            latestHealth = snapshot;
        }
        return snapshot;
    }

    public List<String> getAllowedIps() {
        return getSettings().getAllowedIps();
    }

    public String getPublicBaseUrl() {
        return getSettings().getPublicBaseUrl();
    }

    public int getSessionIdleMinutes() {
        return defaultIfInvalid(getSettings().getSessionIdleMinutes(), DEFAULT_IDLE_MINUTES);
    }

    public int getSessionMaxLifetimeMinutes() {
        return defaultIfInvalid(getSettings().getSessionMaxLifetimeMinutes(), DEFAULT_MAX_LIFETIME_MINUTES);
    }

    public int getInvokeTimeoutSeconds() {
        return defaultIfInvalid(getSettings().getInvokeTimeoutSeconds(), DEFAULT_INVOKE_TIMEOUT_SECONDS);
    }

    public JdbcTemplate newExternalJdbcTemplate() {
        RobotFactorySettingsDTO settings = getSettings();
        ensureDbConfigured(settings);
        return buildJdbcTemplate(settings);
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void refreshConnectionHealth() {
        if (!taskDistributedLock.tryLock(TASK_NAME)) {
            return;
        }
        try {
            latestHealth = probe(getSettings());
        } catch (Exception e) {
            log.warn("robot-factory health refresh failed: {}", e.getMessage());
        } finally {
            taskDistributedLock.unlock(TASK_NAME);
        }
    }

    private RobotFactorySettingsHealthDTO probe(RobotFactorySettingsDTO settings) {
        LocalDateTime now = LocalDateTime.now();
        if (!isDbConfigured(settings)) {
            return RobotFactorySettingsHealthDTO.builder()
                    .configured(false)
                    .databaseReachable(false)
                    .externalTableReady(false)
                    .status("unconfigured")
                    .message("请先在管理端配置软件工厂数据库连接")
                    .checkedAt(now)
                    .build();
        }
        try {
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(settings);
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            Integer tableCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name = 'genie_external_agent'
                    """, Integer.class);
            boolean tableReady = tableCount != null && tableCount > 0;
            return RobotFactorySettingsHealthDTO.builder()
                    .configured(true)
                    .databaseReachable(true)
                    .externalTableReady(tableReady)
                    .status(tableReady ? "healthy" : "warning")
                    .message(tableReady
                            ? "数据库连接正常，可访问 genie_external_agent"
                            : "数据库连接正常，但未发现 genie_external_agent 表")
                    .checkedAt(now)
                    .build();
        } catch (Exception e) {
            return RobotFactorySettingsHealthDTO.builder()
                    .configured(true)
                    .databaseReachable(false)
                    .externalTableReady(false)
                    .status("unhealthy")
                    .message(firstNonBlank(e.getMessage(), "软件工厂数据库连接异常"))
                    .checkedAt(now)
                    .build();
        }
    }

    private JdbcTemplate buildJdbcTemplate(RobotFactorySettingsDTO settings) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(settings.getDbUrl());
        dataSource.setUsername(settings.getDbUsername());
        dataSource.setPassword(settings.getDbPassword());
        dataSource.setDriverClassName(firstNonBlank(settings.getDbDriverClassName(), DEFAULT_DRIVER));
        return new JdbcTemplate(dataSource);
    }

    private void ensureDbConfigured(RobotFactorySettingsDTO settings) {
        if (!isDbConfigured(settings)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "未配置软件工厂外部数据库连接");
        }
    }

    private boolean isDbConfigured(RobotFactorySettingsDTO settings) {
        return settings != null
                && StringUtils.hasText(settings.getDbUrl())
                && StringUtils.hasText(settings.getDbUsername())
                && StringUtils.hasText(settings.getDbPassword());
    }

    private RobotFactorySettingsDTO toSettings(RobotFactorySettingsUpsertRequest request) {
        RobotFactorySettingsDTO settings = new RobotFactorySettingsDTO();
        if (request == null) {
            return settings;
        }
        settings.setDbUrl(request.getDbUrl());
        settings.setDbUsername(request.getDbUsername());
        settings.setDbPassword(request.getDbPassword());
        settings.setDbDriverClassName(request.getDbDriverClassName());
        settings.setPublicBaseUrl(request.getPublicBaseUrl());
        settings.setAllowedIps(request.getAllowedIps());
        settings.setSessionIdleMinutes(request.getSessionIdleMinutes());
        settings.setSessionMaxLifetimeMinutes(request.getSessionMaxLifetimeMinutes());
        settings.setInvokeTimeoutSeconds(request.getInvokeTimeoutSeconds());
        return settings;
    }

    private RobotFactorySettingsDTO normalize(RobotFactorySettingsDTO raw) {
        RobotFactorySettingsDTO settings = raw == null ? new RobotFactorySettingsDTO() : raw;
        settings.setDbUrl(trimToNull(settings.getDbUrl()));
        settings.setDbUsername(trimToNull(settings.getDbUsername()));
        settings.setDbPassword(trimToNull(settings.getDbPassword()));
        settings.setDbDriverClassName(firstNonBlank(trimToNull(settings.getDbDriverClassName()), DEFAULT_DRIVER));
        settings.setPublicBaseUrl(trimToNull(settings.getPublicBaseUrl()));
        settings.setAllowedIps(normalizeAllowedIps(settings.getAllowedIps()));
        settings.setSessionIdleMinutes(defaultIfInvalid(settings.getSessionIdleMinutes(), DEFAULT_IDLE_MINUTES));
        settings.setSessionMaxLifetimeMinutes(defaultIfInvalid(settings.getSessionMaxLifetimeMinutes(), DEFAULT_MAX_LIFETIME_MINUTES));
        settings.setInvokeTimeoutSeconds(defaultIfInvalid(settings.getInvokeTimeoutSeconds(), DEFAULT_INVOKE_TIMEOUT_SECONDS));
        return settings;
    }

    private RobotFactorySettingsDTO defaultSettings(LocalDateTime updateTime) {
        RobotFactorySettingsDTO settings = new RobotFactorySettingsDTO();
        settings.setDbDriverClassName(DEFAULT_DRIVER);
        settings.setAllowedIps(new ArrayList<>());
        settings.setSessionIdleMinutes(DEFAULT_IDLE_MINUTES);
        settings.setSessionMaxLifetimeMinutes(DEFAULT_MAX_LIFETIME_MINUTES);
        settings.setInvokeTimeoutSeconds(DEFAULT_INVOKE_TIMEOUT_SECONDS);
        settings.setUpdateTime(updateTime);
        return settings;
    }

    private List<String> normalizeAllowedIps(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : raw) {
            String value = trimToNull(item);
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private int defaultIfInvalid(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String toJson(RobotFactorySettingsDTO settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "软件工厂配置序列化失败");
        }
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
