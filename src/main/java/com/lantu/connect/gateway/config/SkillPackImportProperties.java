package com.lantu.connect.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 URL 拉取技能 zip 的安全与超时配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.skill-pack-import")
public class SkillPackImportProperties {

    /**
     * 允许的单次下载最大字节数（含 zip）。
     */
    private long maxBytes = 26_214_400;

    /**
     * 连接超时（秒）。
     */
    private int connectTimeoutSeconds = 15;

    /**
     * 单次请求读超时（秒）。
     */
    private int readTimeoutSeconds = 90;

    /**
     * 手动跟随重定向的最大次数。
     */
    private int maxRedirects = 5;

    /**
     * 是否仅允许 https（生产建议 true；本地可 false）。
     */
    private boolean httpsOnly = true;

    /**
     * 非空时：主机名须以其中任一条为后缀（小写比较），用于内网制品站白名单。
     * 为空则仅依赖 SSRF 与内网地址拦截。
     */
    private List<String> allowedHostSuffixes = new ArrayList<>();

    /**
     * 为 true 时：必须配置至少一条 {@link #allowedHostSuffixes}，否则拒绝 URL 导入（缓解 DNS 重绑定类 SSRF，见对接文档）。
     */
    private boolean requireAllowedHostSuffixes = false;
}
