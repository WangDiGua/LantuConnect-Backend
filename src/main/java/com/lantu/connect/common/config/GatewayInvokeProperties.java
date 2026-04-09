package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.gateway")
public class GatewayInvokeProperties {

    private boolean invokeHttpStatusReflectsUpstream = true;

    /**
     * 绑定关系驱动的 invoke 展开（向请求体写入 {@code _lantu.bindingExpansion}）。
     */
    private BindingExpansion bindingExpansion = new BindingExpansion();

    @Data
    public static class BindingExpansion {
        private boolean enabled = true;
        private boolean agent = true;
        private boolean hostedSkill = true;
    }
}
