package com.lantu.connect.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在线技能市场：SkillsMP、自建镜像 JSON、静态 YAML；支持出站代理与 GitHub zip 镜像前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.skill-external-catalog")
public class SkillExternalCatalogProperties {

    /**
     * static：仅 YAML；skillsmp：聚合 SkillsMP（可配镜像回退）；merge：YAML + 动态源合并。
     */
    private String provider = "skillsmp";

    /**
     * 聚合结果内存缓存秒数（减轻外网请求与 SkillsMP 每日配额压力）。
     */
    private int cacheTtlSeconds = 3600;

    /**
     * 是否将 SkillsMP ∪ 镜像 JSON 的聚合结果落库（dedupe_key=packUrl 或 id:*）；坏网时列表读库内快照。
     * {@code provider=static} 时不落库。
     */
    private boolean persistenceEnabled = true;

    /**
     * 可选：自建技能目录 JSON 的 HTTPS 地址（国内 OSS/CDN）。SkillsMP 全失败或关闭时作为回退。
     * 格式：JSON 数组，或与业务 R 一致的对象含 data 数组；元素字段同 SkillExternalCatalogItemVO。
     */
    private String mirrorCatalogUrl = "";

    /**
     * SkillsMP、mirror-catalog-url 共用出站 HTTP 代理（无认证；需 NTLM 等请在外部用专项代理网关）。
     */
    private OutboundHttpProxy outboundHttpProxy = new OutboundHttpProxy();

    /**
     * 对 GitHub archive zip 直链包一层镜像前缀，便于国内拉取；导入 zip 时服务端同样使用该 URL。
     */
    private GithubZipMirror githubZipMirror = new GithubZipMirror();

    private List<Entry> entries = new ArrayList<>();

    private SkillsMp skillsmp = new SkillsMp();

    @Data
    public static class OutboundHttpProxy {
        private String host = "";
        private int port = 0;
    }

    @Data
    public static class GithubZipMirror {
        /**
         * none：不改写；prefix-encoded：{prefix}/{urlEncode(原url)}；prefix-raw：{prefix}/{原url}。
         */
        private String mode = "none";
        /**
         * 示例（按贵司可用线路自行替换）：https://ghfast.top 等；具体格式以镜像服务文档为准。
         */
        private String prefix = "";
    }

    @Data
    public static class Entry {
        private String id;
        private String name;
        private String summary;
        private String packUrl;
        private String licenseNote;
        private String sourceUrl;
    }

    @Data
    public static class SkillsMp {
        /**
         * 设为 false 则完全不请求 SkillsMP（仅用镜像 JSON + YAML），避免海外链路或配额问题。
         */
        private boolean enabled = true;

        private String baseUrl = "https://skillsmp.com/api/v1";
        /**
         * 建议使用环境变量 SKILLSMP_API_KEY；遵守官方每日约 500 次请求限制，勿高频短时暴刷以免 Key 被限制。
         */
        private String apiKey;
        private String sortBy = "stars";
        private int limitPerQuery = 100;
        private int maxQueriesPerRequest = 12;
        private String githubDefaultBranch = "main";
        private List<String> discoveryQueries = SkillExternalCatalogProperties.defaultDiscoveryQueries();
    }

    public static List<String> defaultDiscoveryQueries() {
        return List.of(
                "claude",
                "anthropic",
                "skill",
                "agent",
                "automation",
                "python",
                "typescript",
                "react",
                "devops",
                "docker",
                "kubernetes",
                "testing",
                "data",
                "database",
                "api",
                "security",
                "git",
                "workflow",
                "mcp",
                "prompt"
        );
    }
}
