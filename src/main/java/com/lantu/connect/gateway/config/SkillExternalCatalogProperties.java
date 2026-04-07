package com.lantu.connect.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 在线技能市场：SkillsMP、多地址 JSON 镜像、静态 YAML；支持出站代理与 GitHub zip 镜像前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.skill-external-catalog")
public class SkillExternalCatalogProperties {

    /**
     * static：仅 YAML；skillsmp：聚合远程目录（受 {@link #remoteCatalogMode} 约束）；merge：YAML + 动态源合并。
     */
    private String provider = "skillsmp";

    /**
     * 远程目录生效方式：{@code MERGED} 多项合并；{@code SKILLHUB_ONLY}；{@code SKILLSMP_ONLY}；{@code MIRROR_ONLY} 仅 JSON/HTTP 镜像。
     * YAML 可用 merged / skillhub_only 等（忽略大小写与连字符）。
     */
    private String remoteCatalogMode = "MERGED";

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
     * 全量库同步是否使用 Redis 分布式锁（SET NX + token 释放）；false 时仅依赖本 JVM 内单飞。
     * Redis 连接失败时会降级为 JVM 单飞并打点 {@code skill.catalog.sync} {@code result=redis_degraded}。
     */
    private boolean syncRedisLockEnabled = true;

    /**
     * Redis 锁 TTL（分钟），应大于最坏情况下的单次全量同步耗时。
     */
    private int syncRedisLockTtlMinutes = 30;

    /**
     * 目录全量同步时是否从 GitHub raw 预取 SKILL.md 写入镜像表；详情接口优先读库，避免依赖 SkillsMP 人机校验页与展示时超时。
     */
    private boolean skillMdPrefetchOnSync = true;

    /**
     * 单次同步最多预取条数（0 表示不限制）。过大可能拉长同步耗时并触及 GitHub 限速。
     */
    private int skillMdPrefetchMaxPerSync = 0;

    /**
     * 可选：自建技能目录 JSON 的 HTTPS 地址（国内 OSS/CDN 或第三方导出接口）。SkillsMP 全失败或关闭时作为回退。
     * 格式：JSON 数组，或与业务 R 一致的对象含 data 数组；元素字段同 SkillExternalCatalogItemVO。
     * 与 {@link #mirrorCatalogUrls} 叠加（URL 去重后依次请求）。
     */
    private String mirrorCatalogUrl = "";

    /**
     * 可选：多个 JSON 目录 URL，与 mirror-catalog-url 合并配置；单条失败只跳过该源，不影响其它。
     * 便于同时拉取如国内 SkillHub / CocoLoop / skill0 等提供的导出（需填写各平台实际 HTTPS 路径）。
     */
    private List<String> mirrorCatalogUrls = new ArrayList<>();

    /**
     * 显式 HTTP 目录源（与上面字符串列表二选一或混用；同一 URL 仅保留首次出现）。
     * format：AUTO（默认，自动识别 skill0 / data / skills / 常用别名）；SKILL0 与 AUTO 等价。
     */
    private List<CatalogHttpSource> catalogHttpSources = new ArrayList<>();

    /**
     * SkillsMP、mirror-catalog-url(s) 共用出站 HTTP 代理（无认证；需 NTLM 等请在外部用专项代理网关）。
     */
    private OutboundHttpProxy outboundHttpProxy = new OutboundHttpProxy();

    /**
     * 对 GitHub archive zip 直链包一层镜像前缀，便于国内拉取；导入 zip 时服务端同样使用该 URL。
     */
    private GithubZipMirror githubZipMirror = new GithubZipMirror();

    private List<Entry> entries = new ArrayList<>();

    /**
     * 腾讯 SkillHub 等（公开 GET /api/v1/search，无需 API Key）；默认与 YAML 一并启用为主拉取源。
     */
    private SkillHub skillhub = new SkillHub();

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
         * 须为绝对地址（https:// 或 http:// 开头）。勿填相对路径（如 admin、/proxy），否则会生成非法 packUrl。
         */
        private String prefix = "";
    }

    @Data
    public static class CatalogHttpSource {
        private String url = "";
        private String format = "AUTO";
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
    public static class SkillHub {
        /**
         * 默认 true：与 skillsmp 并行参与聚合（skillsmp 默认 false 时通常仅 SkillHub + 镜像）。
         */
        private boolean enabled = true;
        /**
         * 公开搜索 API 站点根（文档示例：https://agentskillhub.dev；客户端会拼接 /api/v1/search）。
         * 注意：skillhub.tencent.com 为中文官网，多数环境下对 /api/v1/search 返回 HTML 而非 JSON，不宜作为 API base。
         */
        private String baseUrl = "https://agentskillhub.dev";
        /**
         * 主站不可用时的备用根（可填 https://skillhub.tencent.com 若贵司有 JSON 反代；官方 JSON 多为 agentskillhub.dev）。
         */
        private String fallbackBaseUrl = "";
        /** 单请求上限 10（接口限制）。 */
        private int limitPerQuery = 10;
        private int maxQueriesPerRequest = 12;
        private String githubDefaultBranch = "main";
        private List<String> discoveryQueries = SkillExternalCatalogProperties.defaultDiscoveryQueries();
    }

    @Data
    public static class SkillsMp {
        /**
         * 设为 false 则完全不请求 SkillsMP（仅用 SkillHub、镜像 JSON + YAML）。
         */
        private boolean enabled = false;

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
