package com.lantu.connect.gateway.model;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 历史字段：主表 {@code t_resource.access_policy}。新产品线统一为 {@link #OPEN_PLATFORM}，网关不再校验资源级 Grant。
 */
public enum ResourceAccessPolicy {

    /** 历史枚举值；兼容旧库数据，新写入不再使用。 */
    GRANT_REQUIRED("grant_required"),

    /** 历史枚举值。 */
    OPEN_ORG("open_org"),

    /** 当前唯一策略：已通过审核上架的资源，在 API Key scope 满足时对所有调用方开放。 */
    OPEN_PLATFORM("open_platform");

    private final String wireValue;

    ResourceAccessPolicy(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 写入库 / JSON 的稳定小写值。 */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 解析注册请求中的 accessPolicy；非法或空则抛出业务异常。
     */
    public static ResourceAccessPolicy parseRequestValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return GRANT_REQUIRED;
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        for (ResourceAccessPolicy p : values()) {
            if (p.wireValue.equals(n)) {
                return p;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "accessPolicy 仅支持 grant_required、open_org、open_platform");
    }

    /**
     * 读取数据库字段；未知、null 或空串时按 {@link #OPEN_PLATFORM} 处理（与迁移后库默认值一致）。
     */
    public static ResourceAccessPolicy fromStored(Object column) {
        if (column == null) {
            return OPEN_PLATFORM;
        }
        String n = String.valueOf(column).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(n)) {
            return OPEN_PLATFORM;
        }
        for (ResourceAccessPolicy p : values()) {
            if (p.wireValue.equals(n)) {
                return p;
            }
        }
        return OPEN_PLATFORM;
    }
}
