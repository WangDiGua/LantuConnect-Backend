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
     * 缁戝畾鍏崇郴椹卞姩鐨?invoke 灞曞紑锛堝悜璇锋眰浣撳啓鍏?{@code _lantu.bindingExpansion}锛夈€?
     */
    private BindingExpansion bindingExpansion = new BindingExpansion();

    /**
     * {@code GET .../capabilities/tools} 鑱氬悎鏃剁殑杞笂闄愶紱0 琛ㄧず涓嶉檺鍒躲€?
     */
    private Capabilities capabilities = new Capabilities();

    @Data
    public static class BindingExpansion {
        private boolean enabled = true;
        private boolean agent = true;
        /**
         * invoke Agent 鏃惰嫢 payload锛堟垨 {@code _lantu}锛夊惈 {@code activeSkillIds}锛屾槸鍚﹀皢鍚?Skill 涓?{@code skill_depends_mcp}
         * 缁戝畾鐨?MCP 涓?Agent 鑷韩缁戝畾 MCP 鍚堝苟鍚庡啓鍏?{@code _lantu.bindingExpansion}銆?
         */
        private boolean mergeActiveSkillMcps = true;
    }

    @Data
    public static class Capabilities {
        /** 鍗曟鑱氬悎鏈€澶氬鐞嗗灏戜釜 MCP id锛? = 涓嶉檺鍒?*/
        private int maxMcpsPerAggregate = 0;
        /** 鍚堝苟鍚庣殑 function 宸ュ叿鏉℃暟涓婇檺锛? = 涓嶉檺鍒?*/
        private int maxToolsPerResponse = 0;
        /** 资源级并发默认兜底值，0 表示使用内置默认值 100 */
        private int defaultMaxConcurrentPerResource = 100;
    }
}
