package com.lantu.connect.gateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 控制台「网关集成」页可用的运行时提示（不含密钥）。
 */
@Data
@Builder
@Schema(description = "网关集成提示：绑定展开开关与 capabilities 聚合软上限（0=不限制）")
public class GatewayIntegrationHintsVO {

    @Schema(description = "lantu.gateway.binding-expansion")
    private BindingExpansionHint bindingExpansion;

    @Schema(description = "lantu.gateway.capabilities")
    private CapabilitiesHint capabilities;

    @Data
    @Builder
    public static class BindingExpansionHint {
        private boolean enabled;
        private boolean agent;
        /** {@code lantu.gateway.binding-expansion.merge-active-skill-mcps} */
        private boolean mergeActiveSkillMcps;
    }

    @Data
    @Builder
    public static class CapabilitiesHint {
        private int maxMcpsPerAggregate;
        private int maxToolsPerResponse;
    }
}
