package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.gateway.catalog.SkillCatalogMirrorClient;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.skillhub.SkillHubCatalogClient;
import com.lantu.connect.gateway.skillhub.dto.SkillHubSearchSkillJson;
import com.lantu.connect.gateway.skillsmp.GitHubRepoRef;
import com.lantu.connect.gateway.skillsmp.SkillsMpCatalogClient;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpSkillJson;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 远程技能目录聚合（SkillsMP / SkillHub / 镜像 HTTP），供 {@link SkillExternalCatalogService} 与落库同步使用；
 * 对外 HTTP 经 Resilience4j 熔断与重试（Bean 外呼，避免同类自调用失效）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExternalCatalogRemoteCollectionService {

    private static final String LICENSE_SKILLSMP = "数据来自 SkillsMP（多关键词搜索、按星标聚合）。"
            + "ZIP 默认指向 GitHub 分支源码包；若已配置 github-zip-mirror 则展示为镜像 URL。"
            + "仅当仓库根目录含 SKILL.md 时「从 URL 导入」易一次成功。";
    private static final String LICENSE_SKILLHUB = "数据来自 SkillHub 公开搜索 API（agentskillhub.dev 等；sourceIdentifier+slug 推导 GitHub 树路径）。"
            + "ZIP 为仓库默认分支整包；子目录技能请以直链 zip 或平台导出为准。";

    private final SkillHubCatalogClient skillHubCatalogClient;
    private final SkillsMpCatalogClient skillsMpCatalogClient;
    private final SkillCatalogMirrorClient skillCatalogMirrorClient;

    /**
     * mirror-catalog-url、mirror-catalog-urls 与 catalog-http-sources 合并；同一 URL 仅保留首次出现的 format。
     */
    public static List<SkillExternalCatalogProperties.CatalogHttpSource> resolvedCatalogHttpSources(
            SkillExternalCatalogProperties properties) {
        LinkedHashMap<String, SkillExternalCatalogProperties.CatalogHttpSource> byUrl = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.getMirrorCatalogUrl())) {
            putSourceIfAbsent(byUrl, properties.getMirrorCatalogUrl().trim(), "AUTO");
        }
        if (properties.getMirrorCatalogUrls() != null) {
            for (String raw : properties.getMirrorCatalogUrls()) {
                if (StringUtils.hasText(raw)) {
                    putSourceIfAbsent(byUrl, raw.trim(), "AUTO");
                }
            }
        }
        if (properties.getCatalogHttpSources() != null) {
            for (SkillExternalCatalogProperties.CatalogHttpSource cs : properties.getCatalogHttpSources()) {
                if (cs == null || !StringUtils.hasText(cs.getUrl())) {
                    continue;
                }
                String fmt = StringUtils.hasText(cs.getFormat()) ? cs.getFormat().trim() : "AUTO";
                putSourceIfAbsent(byUrl, cs.getUrl().trim(), fmt);
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    private static void putSourceIfAbsent(
            LinkedHashMap<String, SkillExternalCatalogProperties.CatalogHttpSource> byUrl,
            String url,
            String format) {
        byUrl.putIfAbsent(url, catalogSource(url, format));
    }

    private static SkillExternalCatalogProperties.CatalogHttpSource catalogSource(String url, String format) {
        SkillExternalCatalogProperties.CatalogHttpSource s = new SkillExternalCatalogProperties.CatalogHttpSource();
        s.setUrl(url);
        s.setFormat(format);
        return s;
    }

    public static String normalizeRemoteCatalogMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "MERGED";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "SKILLHUB_ONLY", "SKILLSMP_ONLY", "MIRROR_ONLY", "MERGED" -> u;
            default -> "MERGED";
        };
    }

    /**
     * 未 rewrite 的远程条目；{@link CircuitBreaker} / {@link Retry} 仅对本方法生效。
     */
    @CircuitBreaker(name = "skillCatalogRemote", fallbackMethod = "collectEmptyFallback")
    @Retry(name = "skillCatalogRemote", fallbackMethod = "collectEmptyFallback")
    public List<SkillExternalCatalogItemVO> collectUnrewritten(SkillExternalCatalogProperties properties) {
        String mode = normalizeRemoteCatalogMode(properties.getRemoteCatalogMode());
        return switch (mode) {
            case "SKILLHUB_ONLY" -> fetchSkillHubDeduped(properties);
            case "SKILLSMP_ONLY" -> fetchSkillsMpDeduped(properties);
            case "MIRROR_ONLY" -> fetchMirrorSafe(properties);
            default -> {
                List<SkillExternalCatalogItemVO> hub = fetchSkillHubDeduped(properties);
                List<SkillExternalCatalogItemVO> mp = fetchSkillsMpDeduped(properties);
                List<SkillExternalCatalogItemVO> mirror = fetchMirrorSafe(properties);
                yield mergeRemoteByPack(mergeRemoteByPack(hub, mp), mirror);
            }
        };
    }

    @SuppressWarnings("unused")
    private List<SkillExternalCatalogItemVO> collectEmptyFallback(
            SkillExternalCatalogProperties properties,
            Throwable t) {
        log.warn("技能市场远程目录聚合降级（熔断或重试耗尽），返回空列表: {}", t != null ? t.getMessage() : "");
        return List.of();
    }

    private List<SkillExternalCatalogItemVO> fetchMirrorSafe(SkillExternalCatalogProperties properties) {
        List<SkillExternalCatalogProperties.CatalogHttpSource> sources = resolvedCatalogHttpSources(properties);
        if (sources.isEmpty()) {
            return List.of();
        }
        List<SkillExternalCatalogItemVO> merged = List.of();
        for (SkillExternalCatalogProperties.CatalogHttpSource src : sources) {
            try {
                List<SkillExternalCatalogItemVO> part = skillCatalogMirrorClient.fetchList(
                        src.getUrl(),
                        src.getFormat(),
                        properties.getOutboundHttpProxy());
                merged = mergeRemoteByPack(merged, part);
            } catch (BusinessException e) {
                log.warn("镜像技能目录不可用（已跳过）{} [{}]: {}", src.getUrl(), src.getFormat(), e.getMessage());
            }
        }
        return merged;
    }

    private List<SkillExternalCatalogItemVO> fetchSkillHubDeduped(SkillExternalCatalogProperties properties) {
        SkillExternalCatalogProperties.SkillHub cfg = properties.getSkillhub();
        if (cfg == null || !cfg.isEnabled()) {
            return List.of();
        }
        List<String> queries = cfg.getDiscoveryQueries();
        if (queries == null || queries.isEmpty()) {
            queries = SkillExternalCatalogProperties.defaultDiscoveryQueries();
        }
        int cap = Math.max(1, cfg.getMaxQueriesPerRequest());
        List<String> useQueries = queries.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(q -> q.length() >= 2)
                .distinct()
                .limit(cap)
                .collect(Collectors.toList());
        if (useQueries.isEmpty()) {
            return List.of();
        }
        Map<String, SkillExternalCatalogItemVO> byDedupeKey = new HashMap<>();
        int limit = Math.min(10, Math.max(1, cfg.getLimitPerQuery()));
        String branch = StringUtils.hasText(cfg.getGithubDefaultBranch()) ? cfg.getGithubDefaultBranch().trim() : "main";
        for (String q : useQueries) {
            try {
                List<SkillHubSearchSkillJson> skills = skillHubCatalogClient.search(properties, q, limit);
                for (SkillHubSearchSkillJson s : skills) {
                    mergeSkillHubIntoMap(byDedupeKey, s, branch);
                }
            } catch (RuntimeException e) {
                log.warn("SkillHub 搜索词 [{}] 跳过: {}", q, e.getMessage());
            }
        }
        return new ArrayList<>(byDedupeKey.values());
    }

    private void mergeSkillHubIntoMap(Map<String, SkillExternalCatalogItemVO> byDedupeKey, SkillHubSearchSkillJson s, String branch) {
        if (s == null || !StringUtils.hasText(s.getSourceIdentifier()) || !StringUtils.hasText(s.getSlug())) {
            return;
        }
        String sid = s.getSourceIdentifier().trim();
        if (!sid.contains("/")) {
            return;
        }
        String slug = s.getSlug().trim();
        String b = StringUtils.hasText(branch) ? branch : "main";
        String treeUrl = String.format(Locale.ROOT, "https://github.com/%s/tree/%s/%s", sid, b, slug);
        GitHubRepoRef ref = GitHubRepoRef.parse(treeUrl).orElse(null);
        if (ref == null) {
            return;
        }
        String packUrl = ref.defaultBranchArchiveZip(b);
        String name = StringUtils.hasText(s.getName()) ? s.getName().trim() : slug;
        String id = (sid.replace('/', '-') + "-" + slug).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "-");
        String summary = StringUtils.hasText(s.getDescription()) ? s.getDescription().trim() : null;
        Integer stars = s.getTotalInstalls();
        SkillExternalCatalogItemVO vo = SkillExternalCatalogItemVO.builder()
                .id(id)
                .name(name)
                .summary(summary)
                .packUrl(packUrl)
                .licenseNote(LICENSE_SKILLHUB)
                .sourceUrl(treeUrl)
                .stars(stars)
                .build();
        String dedupe = SkillExternalCatalogDedupeKeys.fromVo(vo);
        byDedupeKey.merge(dedupe, vo, SkillExternalCatalogRemoteCollectionService::higherStars);
    }

    private List<SkillExternalCatalogItemVO> fetchSkillsMpDeduped(SkillExternalCatalogProperties properties) {
        SkillExternalCatalogProperties.SkillsMp cfg = properties.getSkillsmp();
        if (cfg == null || !cfg.isEnabled()) {
            return List.of();
        }
        if (!StringUtils.hasText(cfg.getApiKey())) {
            log.info("SkillsMP 已启用但未配置 api-key，跳过远程拉取");
            return List.of();
        }
        List<String> queries = cfg.getDiscoveryQueries();
        if (queries == null || queries.isEmpty()) {
            queries = SkillExternalCatalogProperties.defaultDiscoveryQueries();
        }
        int cap = Math.max(1, cfg.getMaxQueriesPerRequest());
        List<String> useQueries = queries.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(cap)
                .collect(Collectors.toList());
        if (useQueries.isEmpty()) {
            return List.of();
        }
        Map<String, SkillExternalCatalogItemVO> byDedupeKey = new HashMap<>();
        String sortBy = StringUtils.hasText(cfg.getSortBy()) ? cfg.getSortBy().trim() : "stars";
        int limit = Math.min(100, Math.max(1, cfg.getLimitPerQuery()));
        String branch = StringUtils.hasText(cfg.getGithubDefaultBranch()) ? cfg.getGithubDefaultBranch().trim() : "main";

        for (String q : useQueries) {
            try {
                List<SkillsMpSkillJson> skills = skillsMpCatalogClient.searchPage(properties, q, 1, limit, sortBy);
                for (SkillsMpSkillJson s : skills) {
                    mergeSkillIntoMap(byDedupeKey, s, branch);
                }
            } catch (BusinessException e) {
                log.warn("SkillsMP 搜索词 [{}] 跳过: {}", q, e.getMessage());
            }
        }
        return new ArrayList<>(byDedupeKey.values());
    }

    private void mergeSkillIntoMap(Map<String, SkillExternalCatalogItemVO> byDedupeKey, SkillsMpSkillJson s, String branch) {
        if (s == null) {
            return;
        }
        GitHubRepoRef ref = GitHubRepoRef.parse(s.getGithubUrl()).orElse(null);
        if (ref == null) {
            return;
        }
        String packUrl = ref.defaultBranchArchiveZip(branch);
        String id = StringUtils.hasText(s.getId())
                ? s.getId().trim()
                : ("gh-" + ref.getOwner() + "-" + ref.getRepo()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]", "-");
        String name = StringUtils.hasText(s.getName()) ? s.getName().trim() : ref.getRepo();
        String desc = StringUtils.hasText(s.getDescription()) ? s.getDescription().trim() : "";
        String author = StringUtils.hasText(s.getAuthor()) ? s.getAuthor().trim() : "";
        String summary = desc;
        if (StringUtils.hasText(author)) {
            summary = summary.isEmpty() ? ("作者: " + author) : (summary + " — 作者: " + author);
        }
        String source = StringUtils.hasText(s.getSkillUrl()) ? s.getSkillUrl().trim() : s.getGithubUrl().trim();

        SkillExternalCatalogItemVO vo = SkillExternalCatalogItemVO.builder()
                .id(id)
                .name(name)
                .summary(StringUtils.hasText(summary) ? summary : null)
                .packUrl(packUrl)
                .licenseNote(LICENSE_SKILLSMP)
                .sourceUrl(source)
                .stars(s.getStars())
                .build();

        String dedupe = SkillExternalCatalogDedupeKeys.fromVo(vo);
        byDedupeKey.merge(dedupe, vo, SkillExternalCatalogRemoteCollectionService::higherStars);
    }

    private static List<SkillExternalCatalogItemVO> mergeRemoteByPack(List<SkillExternalCatalogItemVO> a,
                                                                      List<SkillExternalCatalogItemVO> b) {
        Map<String, SkillExternalCatalogItemVO> m = new LinkedHashMap<>();
        for (SkillExternalCatalogItemVO v : a) {
            m.put(SkillExternalCatalogDedupeKeys.fromVo(v), v);
        }
        for (SkillExternalCatalogItemVO v : b) {
            m.merge(SkillExternalCatalogDedupeKeys.fromVo(v), v, SkillExternalCatalogRemoteCollectionService::higherStars);
        }
        return new ArrayList<>(m.values());
    }

    private static SkillExternalCatalogItemVO higherStars(SkillExternalCatalogItemVO a, SkillExternalCatalogItemVO b) {
        int sa = a.getStars() == null ? 0 : a.getStars();
        int sb = b.getStars() == null ? 0 : b.getStars();
        return sb >= sa ? b : a;
    }
}
