package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.permissions")
public class PermissionProperties {

    private Map<String, List<String>> roles;

    public List<String> getPermissionsByRole(String roleCode) {
        if (roles == null || roleCode == null) {
            return List.of();
        }
        return roles.getOrDefault(roleCode, List.of());
    }
}
