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
     * 单次 GET（含跟随重定向过程中的每一次请求）遇网络抖动时的额外重试次数（不含首次），如 Connection reset。
     */
    private int fetchRetries = 3;

    /**
     * 重试间隔（毫秒），避免紧连对端。
     */
    private int fetchRetryDelayMs = 1000;

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

    /**
     * 是否允许通过 HTTPS 对 Git 仓库做浅克隆（depth=1）导入技能包。
     */
    private boolean gitCloneEnabled = true;

    /**
     * {@code git clone} 及传输超时（秒）。
     */
    private int gitCloneTimeoutSeconds = 120;

    /**
     * 除 {@code *.git} 外，是否允许对「两段路径」形如 {@code /owner/repo} 的地址尝试克隆（仅限 {@link #gitBareRepoHostSuffixes} 列出的主机）。
     */
    private boolean gitCloneAllowBareRepoPaths = true;

    /**
     * 允许将 {@code /owner/repo} 解析为 Git 克隆的域名后缀（小写），例如 github.com、gitlab.com。
     */
    private List<String> gitBareRepoHostSuffixes = new ArrayList<>(List.of(
            "github.com",
            "www.github.com",
            "gitlab.com",
            "www.gitlab.com",
            "bitbucket.org",
            "www.bitbucket.org",
            "gitee.com",
            "www.gitee.com"));

    /**
     * 克隆产物（解压后打 zip 前）总字节上限，与技能包解压总上限同量级。
     */
    private long gitCloneMaxUnpackedBytes = 40 * 1024 * 1024L;
}
