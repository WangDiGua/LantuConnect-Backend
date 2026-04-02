package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.catalog.GitHubZipPackUrlMirror;
import com.lantu.connect.gateway.catalog.SkillCatalogMirrorClient;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.skillhub.SkillHubCatalogClient;
import com.lantu.connect.gateway.skillhub.dto.SkillHubSearchSkillJson;
import com.lantu.connect.gateway.skillsmp.GitHubRepoRef;
import com.lantu.connect.gateway.skillsmp.SkillsMpCatalogClient;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpSkillJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能在线市场：SkillHub（默认）+ SkillsMP + 多地址 JSON 镜像 + YAML；出站代理与 GitHub zip 镜像由配置完成。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExternalCatalogService {

    private static final String LICENSE_SKILLSMP = "数据来自 SkillsMP（多关键词搜索、按星标聚合）。"
            + "ZIP 默认指向 GitHub 分支源码包；若已配置 github-zip-mirror 则展示为镜像 URL。"
            + "仅当仓库根目录含 SKILL.md 时「从 URL 导入」易一次成功。";
    private static final String LICENSE_SKILLHUB = "数据来自 SkillHub 公开搜索 API（agentskillhub.dev 等；sourceIdentifier+slug 推导 GitHub 树路径）。"
            + "ZIP 为仓库默认分支整包；子目录技能请以直链 zip 或平台导出为准。";

    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;
    private final SkillHubCatalogClient skillHubCatalogClient;
    private final SkillsMpCatalogClient skillsMpCatalogClient;
    private final SkillCatalogMirrorClient skillCatalogMirrorClient;
    private final GitHubZipPackUrlMirror gitHubZipPackUrlMirror;
    private final SkillExternalCatalogCacheCoordinator cacheCoordinator;
    private final SkillExternalCatalogPersistenceService persistenceService;

    public List<SkillExternalCatalogItemVO> listCatalog() {
        SkillExternalCatalogProperties properties = runtimeConfigService.effective();
        String mode = properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim();
        if ("static".equalsIgnoreCase(mode)) {
            return sortByStarsDesc(rewritePacks(properties, staticEntriesOnly(properties)));
        }
        if (persistenceService.usePersistence(properties, mode)) {
            persistenceService.ensureFresh(properties);
            if ("skillsmp".equalsIgnoreCase(mode)) {
                List<SkillExternalCatalogItemVO> db = persistenceService.loadAllVosOrdered();
                if (!db.isEmpty()) {
                    return db;
                }
                return cachedOrFetch(properties, this::fetchDynamicOnly);
            }
            if ("merge".equalsIgnoreCase(mode)) {
                List<SkillExternalCatalogItemVO> yaml = rewritePacks(properties, staticEntriesOnly(properties));
                List<SkillExternalCatalogItemVO> remote = persistenceService.loadAllVosOrdered();
                if (!remote.isEmpty()) {
                    return sortByStarsDesc(mergeByPackUrl(yaml, remote));
                }
                return sortByStarsDesc(mergeByPackUrl(yaml, cachedOrFetch(properties, this::fetchDynamicOnly)));
            }
        }
        if ("skillsmp".equalsIgnoreCase(mode)) {
            return cachedOrFetch(properties, this::fetchDynamicOnly);
        }
        if ("merge".equalsIgnoreCase(mode)) {
            return cachedOrFetch(properties, this::fetchMerged);
        }
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "未知 lantu.skill-external-catalog.provider: " + mode + "（支持 static | skillsmp | merge）");
    }

    /**
     * 供库镜像同步：按 {@link SkillExternalCatalogProperties#getRemoteCatalogMode()} 拉取远程目录；空或失败返回空列表，不抛错。
     */
    public List<SkillExternalCatalogItemVO> tryBuildRemoteSnapshotForDb(SkillExternalCatalogProperties properties) {
        try {
            List<SkillExternalCatalogItemVO> raw = collectRemoteUnrewritten(properties);
            if (raw.isEmpty()) {
                return List.of();
            }
            List<SkillExternalCatalogItemVO> merged = rewritePacks(properties, raw);
            return sortByStarsDesc(merged);
        } catch (BusinessException e) {
            log.warn("技能市场远程拉取失败（保留库内数据）: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("技能市场远程拉取异常（保留库内数据）: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 分页列表；开启库镜像时 skillsmp 走 SQL 分页，merge/static 仍合并后再内存分页。
     */
    public PageResult<SkillExternalCatalogItemVO> listCatalogPage(String keyword, int page, int pageSize) {
        SkillExternalCatalogProperties properties = runtimeConfigService.effective();
        String mode = properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim();
        if ("static".equalsIgnoreCase(mode)) {
            List<SkillExternalCatalogItemVO> all =
                    sortByStarsDesc(rewritePacks(properties, staticEntriesOnly(properties)));
            return pageInMemory(all, keyword, page, pageSize);
        }
        if (persistenceService.usePersistence(properties, mode)) {
            persistenceService.ensureFresh(properties);
            if ("skillsmp".equalsIgnoreCase(mode)) {
                PageResult<SkillExternalCatalogItemVO> pg = persistenceService.pageVos(keyword, page, pageSize);
                if (pg.getTotal() == 0 && persistenceService.isTableEmpty()) {
                    List<SkillExternalCatalogItemVO> all = cachedOrFetch(properties, this::fetchDynamicOnly);
                    return pageInMemory(all, keyword, page, pageSize);
                }
                return pg;
            }
            if ("merge".equalsIgnoreCase(mode)) {
                List<SkillExternalCatalogItemVO> yaml = rewritePacks(properties, staticEntriesOnly(properties));
                List<SkillExternalCatalogItemVO> remote = persistenceService.loadAllVosOrdered();
                List<SkillExternalCatalogItemVO> all;
                if (remote.isEmpty() && persistenceService.isTableEmpty()) {
                    List<SkillExternalCatalogItemVO> net = cachedOrFetch(properties, this::fetchDynamicOnly);
                    all = sortByStarsDesc(mergeByPackUrl(yaml, net));
                } else {
                    all = sortByStarsDesc(mergeByPackUrl(yaml, remote));
                }
                return pageInMemory(all, keyword, page, pageSize);
            }
        }
        return pageInMemory(listCatalog(), keyword, page, pageSize);
    }

    private PageResult<SkillExternalCatalogItemVO> pageInMemory(List<SkillExternalCatalogItemVO> all,
                                                                  String keyword,
                                                                  int page,
                                                                  int pageSize) {
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<SkillExternalCatalogItemVO> filtered = all;
        if (StringUtils.hasText(q)) {
            filtered = new ArrayList<>();
            for (SkillExternalCatalogItemVO v : all) {
                if (matchesKeyword(v, q)) {
                    filtered.add(v);
                }
            }
        }
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        long total = filtered.size();
        int from = (p - 1) * ps;
        if (from >= filtered.size()) {
            return PageResult.of(List.of(), total, p, ps);
        }
        int to = Math.min(from + ps, filtered.size());
        return PageResult.of(new ArrayList<>(filtered.subList(from, to)), total, p, ps);
    }

    private static boolean matchesKeyword(SkillExternalCatalogItemVO v, String q) {
        return containsLower(v.getName(), q)
                || containsLower(v.getSummary(), q)
                || containsLower(v.getPackUrl(), q)
                || containsLower(v.getSourceUrl(), q)
                || containsLower(v.getId(), q)
                || containsLower(v.getLicenseNote(), q);
    }

    private static boolean containsLower(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private List<SkillExternalCatalogItemVO> cachedOrFetch(
            SkillExternalCatalogProperties properties,
            java.util.function.Function<SkillExternalCatalogProperties, List<SkillExternalCatalogItemVO>> fetcher) {
        long ttlMs = Math.max(60, properties.getCacheTtlSeconds()) * 1000L;
        long now = System.currentTimeMillis();
        List<SkillExternalCatalogItemVO> cache = cacheCoordinator.getCache();
        long cacheExpiresAtMillis = cacheCoordinator.getCacheExpiresAtMillis();
        Object cacheLock = cacheCoordinator.getCacheLock();
        if (now < cacheExpiresAtMillis && !cache.isEmpty()) {
            return cache;
        }
        synchronized (cacheLock) {
            long nowLocked = System.currentTimeMillis();
            cache = cacheCoordinator.getCache();
            cacheExpiresAtMillis = cacheCoordinator.getCacheExpiresAtMillis();
            if (nowLocked < cacheExpiresAtMillis && !cache.isEmpty()) {
                return cache;
            }
            try {
                List<SkillExternalCatalogItemVO> fresh = sortByStarsDesc(fetcher.apply(properties));
                cacheCoordinator.setCache(fresh, System.currentTimeMillis() + ttlMs);
                return fresh;
            } catch (BusinessException e) {
                if (!cacheCoordinator.getCache().isEmpty()) {
                    log.warn("技能市场拉取失败，返回缓存: {}", e.getMessage());
                    return cacheCoordinator.getCache();
                }
                throw e;
            }
        }
    }

    /** 动态源：按 remote-catalog-mode 选取 SkillHub / SkillsMP / 镜像之一或合并，再重写 zip 链接 */
    private List<SkillExternalCatalogItemVO> fetchDynamicOnly(SkillExternalCatalogProperties properties) {
        List<SkillExternalCatalogItemVO> out = rewritePacks(properties, collectRemoteUnrewritten(properties));
        if (out.isEmpty()) {
            throwEmptyDynamicCatalog(properties);
        }
        return out;
    }

    /**
     * 空结果时按生效方式给出明确提示（避免「仅 SkillHub」用户误以为需要 SkillsMP Key）。
     */
    private static void throwEmptyDynamicCatalog(SkillExternalCatalogProperties properties) {
        String mode = normalizeRemoteCatalogMode(properties.getRemoteCatalogMode());
        String msg = switch (mode) {
            case "SKILLHUB_ONLY" ->
                    "未获得任何市场条目（当前为「仅 SkillHub」）。SkillHub 公开搜索接口不需要 SkillsMP API Key。"
                            + "请检查：① 市场配置中已开启 SkillHub；② baseUrl 从本机/服务器可访问（必要时配置出站代理）；"
                            + "③ Discovery 关键词每条至少 2 个字符；④ 查看日志中是否有 SkillHub HTTP 错误。";
            case "SKILLSMP_ONLY" ->
                    "未获得任何市场条目（当前为「仅 SkillsMP」）。请配置有效的 SkillsMP API Key、开启 SkillsMP，并确认 baseUrl 与网络可达。";
            case "MIRROR_ONLY" ->
                    "未获得任何市场条目（当前为「仅镜像」）。请填写可访问的 mirrorCatalogUrl / mirrorCatalogUrls 或 catalogHttpSources。";
            default ->
                    "未获得任何市场条目（多源合并）。请至少保证其一可用：SkillHub 开启且可访问、SkillsMP 开启且已配置 Key、或已配置镜像 URL；"
                            + "也可改为单一来源便于排查，或将 provider 设为 static 仅使用静态条目。";
        };
        throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR, msg);
    }

    private List<SkillExternalCatalogItemVO> fetchMerged(SkillExternalCatalogProperties properties) {
        List<SkillExternalCatalogItemVO> yaml = rewritePacks(properties, staticEntriesOnly(properties));
        List<SkillExternalCatalogItemVO> remote = rewritePacks(properties, collectRemoteUnrewritten(properties));
        if (remote.isEmpty() && yaml.isEmpty()) {
            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                    "合并结果为空：请配置 remote-catalog-mode 与对应源，或 YAML entries。");
        }
        if (remote.isEmpty()) {
            return sortByStarsDesc(yaml);
        }
        if (yaml.isEmpty()) {
            return sortByStarsDesc(remote);
        }
        return sortByStarsDesc(mergeByPackUrl(yaml, remote));
    }

    /**
     * 未 rewrite 的远程条目；按 {@link SkillExternalCatalogProperties#getRemoteCatalogMode()} 决定单源或合并。
     * 单源模式下<strong>只</strong>请求对应远端，避免「仅 SkillHub」仍误打 SkillsMP 配额与日志噪音。
     */
    private List<SkillExternalCatalogItemVO> collectRemoteUnrewritten(SkillExternalCatalogProperties properties) {
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

    private static String normalizeRemoteCatalogMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "MERGED";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (u) {
            case "SKILLHUB_ONLY", "SKILLSMP_ONLY", "MIRROR_ONLY", "MERGED" -> u;
            default -> "MERGED";
        };
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

    /**
     * mirror-catalog-url、mirror-catalog-urls 与 catalog-http-sources 合并；同一 URL 仅保留首次出现的 format。
     */
    static List<SkillExternalCatalogProperties.CatalogHttpSource> resolvedCatalogHttpSources(
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

    /**
     * SkillHub 多词搜索（/api/v1/search）；单词失败不中断；未启用时返回空列表。
     */
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
            } catch (Exception e) {
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
        Optional<GitHubRepoRef> refOpt = GitHubRepoRef.parse(treeUrl);
        if (refOpt.isEmpty()) {
            return;
        }
        GitHubRepoRef ref = refOpt.get();
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
        byDedupeKey.merge(dedupe, vo, SkillExternalCatalogService::higherStars);
    }

    /**
     * SkillsMP 多词搜索；单词失败不中断；未启用或无 Key 时返回空列表。
     */
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
        Optional<GitHubRepoRef> refOpt = GitHubRepoRef.parse(s.getGithubUrl());
        if (refOpt.isEmpty()) {
            return;
        }
        GitHubRepoRef ref = refOpt.get();
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
        byDedupeKey.merge(dedupe, vo, SkillExternalCatalogService::higherStars);
    }

    private List<SkillExternalCatalogItemVO> staticEntriesOnly(SkillExternalCatalogProperties properties) {
        List<SkillExternalCatalogProperties.Entry> entries = properties.getEntries();
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(e -> StringUtils.hasText(e.getId())
                        && StringUtils.hasText(e.getName())
                        && StringUtils.hasText(e.getPackUrl()))
                .map(e -> SkillExternalCatalogItemVO.builder()
                        .id(e.getId().trim())
                        .name(e.getName().trim())
                        .summary(blankToNull(e.getSummary()))
                        .packUrl(e.getPackUrl().trim())
                        .licenseNote(blankToNull(e.getLicenseNote()))
                        .sourceUrl(blankToNull(e.getSourceUrl()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<SkillExternalCatalogItemVO> mergeByPackUrl(List<SkillExternalCatalogItemVO> yamlFirst,
                                                            List<SkillExternalCatalogItemVO> fromRemote) {
        Map<String, SkillExternalCatalogItemVO> byPack = new LinkedHashMap<>();
        Set<String> yamlPackUrls = new LinkedHashSet<>();
        for (SkillExternalCatalogItemVO v : yamlFirst) {
            if (StringUtils.hasText(v.getPackUrl())) {
                yamlPackUrls.add(v.getPackUrl().trim().toLowerCase(Locale.ROOT));
            }
            String key = SkillExternalCatalogDedupeKeys.fromVo(v);
            byPack.putIfAbsent(key, v);
        }
        for (SkillExternalCatalogItemVO v : fromRemote) {
            String pu = v.getPackUrl() != null ? v.getPackUrl().trim().toLowerCase(Locale.ROOT) : "";
            if (StringUtils.hasText(pu) && yamlPackUrls.contains(pu)) {
                continue;
            }
            String key = SkillExternalCatalogDedupeKeys.fromVo(v);
            byPack.merge(key, v, SkillExternalCatalogService::higherStars);
        }
        return new ArrayList<>(byPack.values());
    }

    private static List<SkillExternalCatalogItemVO> mergeRemoteByPack(List<SkillExternalCatalogItemVO> a,
                                                                      List<SkillExternalCatalogItemVO> b) {
        Map<String, SkillExternalCatalogItemVO> m = new LinkedHashMap<>();
        for (SkillExternalCatalogItemVO v : a) {
            m.put(SkillExternalCatalogDedupeKeys.fromVo(v), v);
        }
        for (SkillExternalCatalogItemVO v : b) {
            m.merge(SkillExternalCatalogDedupeKeys.fromVo(v), v, SkillExternalCatalogService::higherStars);
        }
        return new ArrayList<>(m.values());
    }

    private static SkillExternalCatalogItemVO higherStars(SkillExternalCatalogItemVO a, SkillExternalCatalogItemVO b) {
        int sa = a.getStars() == null ? 0 : a.getStars();
        int sb = b.getStars() == null ? 0 : b.getStars();
        return sb >= sa ? b : a;
    }

    private List<SkillExternalCatalogItemVO> rewritePacks(SkillExternalCatalogProperties properties,
                                                         List<SkillExternalCatalogItemVO> list) {
        return list.stream().map(v -> rewritePack(properties, v)).collect(Collectors.toList());
    }

    private SkillExternalCatalogItemVO rewritePack(SkillExternalCatalogProperties properties, SkillExternalCatalogItemVO v) {
        if (v == null) {
            return null;
        }
        String next = gitHubZipPackUrlMirror.applyIfConfigured(v.getPackUrl(), properties.getGithubZipMirror());
        if (next.equals(v.getPackUrl())) {
            return v;
        }
        return SkillExternalCatalogItemVO.builder()
                .id(v.getId())
                .name(v.getName())
                .summary(v.getSummary())
                .packUrl(next)
                .licenseNote(v.getLicenseNote())
                .sourceUrl(v.getSourceUrl())
                .stars(v.getStars())
                .build();
    }

    private static List<SkillExternalCatalogItemVO> sortByStarsDesc(List<SkillExternalCatalogItemVO> list) {
        List<SkillExternalCatalogItemVO> out = new ArrayList<>(list);
        out.sort(Comparator.comparing((SkillExternalCatalogItemVO v) -> v.getStars() == null ? 0 : v.getStars()).reversed()
                .thenComparing(v -> v.getName() == null ? "" : v.getName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
