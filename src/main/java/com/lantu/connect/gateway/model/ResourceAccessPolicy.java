package com.lantu.connect.gateway.model;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 统一资源消费策略：主表 {@code t_resource.access_policy}。
 * 网关 Grant 短路由 {@link com.lantu.connect.gateway.security.ResourceInvokeGrantService} 实现（仍校验 API Key 与 scope）。
 */
public enum ResourceAccessPolicy {

    /** 与当前行为一致：非 owner 须具备有效 Grant（及 Key scope）。 */
    GRANT_REQUIRED("grant_required"),

    /** 同部门（与 owner 的 menuId 一致）内用户所持 Key 可免 Grant（仍须 scope）；具体校验在网关阶段实现。 */
    OPEN_ORG("open_org"),

    /**
     * 租户内任意已认证 API Key 在满足 scope 的前提下可免 Grant。
     * 非匿名公开；平台级「完全开放」需单独产品与风控策略。
     */
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
     * 读取数据库字段；未知或 null 时保守为 grant_required。
     */
    public static ResourceAccessPolicy fromStored(Object column) {
        if (column == null) {
            return GRANT_REQUIRED;
        }
        String n = String.valueOf(column).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(n)) {
            return GRANT_REQUIRED;
        }
        for (ResourceAccessPolicy p : values()) {
            if (p.wireValue.equals(n)) {
                return p;
            }
        }
        return GRANT_REQUIRED;
    }
}
