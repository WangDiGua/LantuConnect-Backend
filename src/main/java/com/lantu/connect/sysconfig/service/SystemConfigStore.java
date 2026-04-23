package com.lantu.connect.sysconfig.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.sysconfig.entity.SecuritySetting;
import com.lantu.connect.sysconfig.entity.SystemParam;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemConfigStore {

    private static final String SCOPE_SYSTEM = "system";
    private static final String SCOPE_SECURITY = "security";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<SystemParam> listSystemParams() {
        return jdbcTemplate.query("""
                SELECT config_key, config_value, value_type, description, category, editable, update_time
                FROM t_system_config
                WHERE scope = ?
                ORDER BY config_key
                """, (rs, rowNum) -> mapSystemParam(rs), SCOPE_SYSTEM);
    }

    public SystemParam findSystemParam(String key) {
        return jdbcTemplate.query("""
                SELECT config_key, config_value, value_type, description, category, editable, update_time
                FROM t_system_config
                WHERE scope = ? AND config_key = ?
                LIMIT 1
                """, rs -> rs.next() ? mapSystemParam(rs) : null, SCOPE_SYSTEM, key);
    }

    public void upsertSystemParam(SystemParam param) {
        if (param == null || !StringUtils.hasText(param.getKey())) {
            throw new IllegalArgumentException("system config key must not be blank");
        }
        LocalDateTime updateTime = param.getUpdateTime() != null ? param.getUpdateTime() : LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO t_system_config
                    (id, scope, config_key, config_value, value_type, label, description, category, editable, options_json, update_time)
                VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, NULL, ?)
                ON DUPLICATE KEY UPDATE
                    config_value = VALUES(config_value),
                    value_type = VALUES(value_type),
                    description = VALUES(description),
                    category = VALUES(category),
                    editable = VALUES(editable),
                    update_time = VALUES(update_time)
                """,
                id(SCOPE_SYSTEM, param.getKey()),
                SCOPE_SYSTEM,
                param.getKey(),
                param.getValue(),
                defaultText(param.getType(), "string"),
                defaultText(param.getDescription(), ""),
                defaultText(param.getCategory(), "general"),
                param.getEditable() == null ? Boolean.TRUE : param.getEditable(),
                Timestamp.valueOf(updateTime));
    }

    public List<SecuritySetting> listSecuritySettings() {
        return jdbcTemplate.query("""
                SELECT config_key, config_value, value_type, label, description, category, options_json
                FROM t_system_config
                WHERE scope = ?
                ORDER BY config_key
                """, (rs, rowNum) -> mapSecuritySetting(rs), SCOPE_SECURITY);
    }

    public SecuritySetting findSecuritySetting(String key) {
        return jdbcTemplate.query("""
                SELECT config_key, config_value, value_type, label, description, category, options_json
                FROM t_system_config
                WHERE scope = ? AND config_key = ?
                LIMIT 1
                """, rs -> rs.next() ? mapSecuritySetting(rs) : null, SCOPE_SECURITY, key);
    }

    public void upsertSecuritySetting(SecuritySetting setting) {
        if (setting == null || !StringUtils.hasText(setting.getKey())) {
            throw new IllegalArgumentException("security config key must not be blank");
        }
        jdbcTemplate.update("""
                INSERT INTO t_system_config
                    (id, scope, config_key, config_value, value_type, label, description, category, editable, options_json, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, NOW())
                ON DUPLICATE KEY UPDATE
                    config_value = VALUES(config_value),
                    value_type = VALUES(value_type),
                    label = VALUES(label),
                    description = VALUES(description),
                    category = VALUES(category),
                    options_json = VALUES(options_json),
                    update_time = NOW()
                """,
                id(SCOPE_SECURITY, setting.getKey()),
                SCOPE_SECURITY,
                setting.getKey(),
                setting.getValue(),
                defaultText(setting.getType(), "string"),
                defaultText(setting.getLabel(), setting.getKey()),
                defaultText(setting.getDescription(), ""),
                defaultText(setting.getCategory(), "security"),
                writeJsonOrNull(setting.getOptions()));
    }

    public Integer readSecurityInt(String key) {
        return jdbcTemplate.query("""
                SELECT CAST(config_value AS SIGNED) AS int_value
                FROM t_system_config
                WHERE scope = ? AND config_key = ?
                LIMIT 1
                """, rs -> rs.next() ? rs.getInt("int_value") : null, SCOPE_SECURITY, key);
    }

    private SystemParam mapSystemParam(ResultSet rs) throws SQLException {
        SystemParam param = new SystemParam();
        param.setKey(rs.getString("config_key"));
        param.setValue(rs.getString("config_value"));
        param.setType(rs.getString("value_type"));
        param.setDescription(rs.getString("description"));
        param.setCategory(rs.getString("category"));
        param.setEditable(readBoolean(rs, "editable"));
        Timestamp ts = rs.getTimestamp("update_time");
        param.setUpdateTime(ts == null ? null : ts.toLocalDateTime());
        return param;
    }

    private SecuritySetting mapSecuritySetting(ResultSet rs) throws SQLException {
        SecuritySetting setting = new SecuritySetting();
        setting.setKey(rs.getString("config_key"));
        setting.setValue(rs.getString("config_value"));
        setting.setType(rs.getString("value_type"));
        setting.setLabel(rs.getString("label"));
        setting.setDescription(rs.getString("description"));
        setting.setCategory(rs.getString("category"));
        setting.setOptions(readJsonOrNull(rs.getString("options_json")));
        return setting;
    }

    private static String id(String scope, String key) {
        return scope + ":" + key;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static Boolean readBoolean(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        return raw == null ? null : rs.getBoolean(column);
    }

    private Object readJsonOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String writeJsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid config options JSON", e);
        }
    }
}
