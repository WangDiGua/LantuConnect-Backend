package com.lantu.connect.usermgmt;

import java.util.List;

/**
 * 创建 API Key 时的 scope 缺省：未传或空列表视为未配置，存 {@code ["*"]}，避免前端无「选权限」步骤时得到不可用的 Key。
 */
public final class ApiKeyScopes {

    public static List<String> defaultIfUnspecified(List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        return List.of("*");
    }

    private ApiKeyScopes() {
    }
}
