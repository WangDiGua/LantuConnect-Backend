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

    /**
     * {@code GET .../capabilities/tools} 聚合时的软上限；0 表示不限制。
     */
    private Capabilities capabilities = new Capabilities();

    @Data
    public static class BindingExpansion {
        private boolean enabled = true;
        private boolean agent = true;
        /**
         * invoke Agent 时若 payload（或 {@code _lantu}）含 {@code activeSkillIds}，是否将各 Skill 上 {@code skill_depends_mcp}
         * 绑定的 MCP 与 Agent 自身绑定 MCP 合并后写入 {@code _lantu.bindingExpansion}。
         */
        private boolean mergeActiveSkillMcps = true;
    }

    @Data
    public static class Capabilities {
        /** 单次聚合最多处理多少个 MCP id；0 = 不限制 */
        private int maxMcpsPerAggregate = 0;
        /** 合并后的 function 工具条数上限；0 = 不限制 */
        private int maxToolsPerResponse = 0;
    }
}
