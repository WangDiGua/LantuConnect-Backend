package com.lantu.connect.common.security;

import java.io.Serializable;

/**
 * 附在仅持 API Key 通过的请求上：区分用户拥有者（可绑定 X-User-Id 语义）与组织等非用户主体。
 */
public record GatewayAuthDetails(Long ownerUserId) implements Serializable {
}
