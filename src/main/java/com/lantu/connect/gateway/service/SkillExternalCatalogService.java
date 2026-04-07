package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.catalog.GitHubZipPackUrlMirror;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.dto.SkillExternalCatalogSyncStatusResponse;
import com.lantu.connect.gateway.support.SkillExternalItemKeyCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
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

    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;
    private final SkillExternalCatalogRemoteCollectionService remoteCollectionService;
    private final GitHubZipPackUrlMirror gitHubZipPackUrlMirror;
    private final SkillExternalCatalogCacheCoordinator cacheCoordinator;
    private final SkillExternalCatalogPersistenceService persistenceService;
    private final SkillExternalEngagementService skillExternalEngagementService;

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
            List<SkillExternalCatalogItemVO> raw = remoteCollectionService.collectUnrewritten(properties);
            if (raw.isEmpty()) {
                return List.of();
            }
            List<SkillExternalCatalogItemVO> merged = rewritePacks(properties, raw);
            return sortByStarsDesc(merged);
        } catch (BusinessException e) {
            log.warn("技能市场远程拉取失败（保留库内数据）: {}", e.getMessage());
            return List.of();
        } catch (RuntimeException e) {
            log.warn("技能市场远程拉取异常（保留库内数据）: {}", e.toString());
            return List.of();
        }
    }

    /**
     * 分页列表；开启库镜像时 skillsmp 走 SQL 分页，merge/static 仍合并后再内存分页。
     * {@code userId} 可选：传入时填充 {@code favoritedByMe} 与聚合统计。
     * <p>
     * 聚合统计（收藏/下载/浏览/评论摘要）每请求对事实表做 SQL 汇总，与目录条目标题等字段的 TTL 缓存解耦，避免互动数字脏读。
     */
    public PageResult<SkillExternalCatalogItemVO> listCatalogPage(String keyword,
                                                                  int page,
                                                                  int pageSize,
                                                                  Long userId,
                                                                  Integer minStars,
                                                                  Integer maxStars,
                                                                  String source) {
        String sourceNorm = normalizeCatalogSourceFilter(source);
        Integer mn = minStars;
        Integer mx = maxStars;
        if (mn != null && mx != null && mn > mx) {
            Integer t = mn;
            mn = mx;
            mx = t;
        }
        PageResult<SkillExternalCatalogItemVO> pg =
                listCatalogPageWithoutStats(keyword, page, pageSize, mn, mx, sourceNorm);
        for (SkillExternalCatalogItemVO vo : pg.getList()) {
            if (vo.getItemKey() == null || vo.getItemKey().isBlank()) {
                vo.setItemKey(SkillExternalCatalogDedupeKeys.fromVo(vo));
            }
        }
        skillExternalEngagementService.applyAggregateStats(pg.getList(), userId);
        return pg;
    }

    private PageResult<SkillExternalCatalogItemVO> listCatalogPageWithoutStats(String keyword,
                                                                              int page,
                                                                              int pageSize,
                                                                              Integer minStars,
                                                                              Integer maxStars,
                                                                              String sourceNorm) {
        SkillExternalCatalogProperties properties = runtimeConfigService.effective();
        String mode = properties.getProvider() == null ? "skillsmp" : properties.getProvider().trim();
        if ("static".equalsIgnoreCase(mode)) {
            List<SkillExternalCatalogItemVO> all =
                    sortByStarsDesc(rewritePacks(properties, staticEntriesOnly(properties)));
            return pageInMemory(all, keyword, page, pageSize, minStars, maxStars, sourceNorm);
        }
        if (persistenceService.usePersistence(properties, mode)) {
            persistenceService.ensureFresh(properties);
            if ("skillsmp".equalsIgnoreCase(mode)) {
                PageResult<SkillExternalCatalogItemVO> pg =
                        persistenceService.pageVos(keyword, page, pageSize, minStars, maxStars, sourceNorm);
                if (pg.getTotal() == 0 && persistenceService.isTableEmpty()) {
                    List<SkillExternalCatalogItemVO> all = cachedOrFetch(properties, this::fetchDynamicOnly);
                    return pageInMemory(all, keyword, page, pageSize, minStars, maxStars, sourceNorm);
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
                return pageInMemory(all, keyword, page, pageSize, minStars, maxStars, sourceNorm);
            }
        }
        return pageInMemory(listCatalog(), keyword, page, pageSize, minStars, maxStars, sourceNorm);
    }

    /** 非法或未识别的 source 忽略（不按来源过滤）。 */
    private static String normalizeCatalogSourceFilter(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "skillhub", "skillsmp", "mirror" -> s;
            default -> null;
        };
    }

    /**
     * 按去重键解析单条（库镜像或 static/merge 内存目录）。
     */
    public Optional<SkillExternalCatalogItemVO> findCatalogItemByItemKey(String rawKey) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawKey);
        if (k.isEmpty()) {
            return Optional.empty();
        }
        SkillExternalCatalogItemVO fromDb = persistenceService.findVoByDedupeKey(k);
        if (fromDb != null) {
            SkillExternalCatalogItemVO vo = rewritePack(runtimeConfigService.effective(), fromDb);
            vo.setItemKey(SkillExternalCatalogDedupeKeys.fromVo(vo));
            return Optional.of(vo);
        }
        for (SkillExternalCatalogItemVO item : listCatalog()) {
            String itemKey = SkillExternalCatalogDedupeKeys.fromVo(item);
            if (k.equals(itemKey)) {
                return Optional.of(copyVoForExternalKey(item, itemKey));
            }
        }
        return Optional.empty();
    }

    public SkillExternalCatalogItemVO getCatalogItemWithStats(String rawKey, Long userId) {
        SkillExternalCatalogItemVO vo = findCatalogItemByItemKey(rawKey).orElseThrow(() ->
                new BusinessException(ResultCode.NOT_FOUND, "条目不存在或尚未同步"));
        skillExternalEngagementService.applyAggregateStats(List.of(vo), userId);
        return vo;
    }

    /**
     * 库镜像开启时的同步状态摘要（锁/进行中为提示性质，多实例以 Redis 锁为准）。
     */
    public SkillExternalCatalogSyncStatusResponse getExternalCatalogSyncStatusHint() {
        return persistenceService.getSyncStatusHint(runtimeConfigService.effective());
    }

    private static SkillExternalCatalogItemVO copyVoForExternalKey(SkillExternalCatalogItemVO v, String itemKey) {
        return SkillExternalCatalogItemVO.builder()
                .id(v.getId())
                .name(v.getName())
                .summary(v.getSummary())
                .packUrl(v.getPackUrl())
                .licenseNote(v.getLicenseNote())
                .sourceUrl(v.getSourceUrl())
                .stars(v.getStars())
                .itemKey(itemKey)
                .build();
    }

    private PageResult<SkillExternalCatalogItemVO> pageInMemory(List<SkillExternalCatalogItemVO> all,
                                                                  String keyword,
                                                                  int page,
                                                                  int pageSize,
                                                                  Integer minStars,
                                                                  Integer maxStars,
                                                                  String sourceNorm) {
        List<SkillExternalCatalogItemVO> filtered =
                applyCatalogListFilters(all, keyword, minStars, maxStars, sourceNorm);
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

    private List<SkillExternalCatalogItemVO> applyCatalogListFilters(List<SkillExternalCatalogItemVO> items,
                                                                     String keyword,
                                                                     Integer minStars,
                                                                     Integer maxStars,
                                                                     String sourceNorm) {
        List<SkillExternalCatalogItemVO> out = items;
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(q)) {
            out = new ArrayList<>();
            for (SkillExternalCatalogItemVO v : items) {
                if (matchesKeyword(v, q)) {
                    out.add(v);
                }
            }
        }
        if (minStars == null && maxStars == null && sourceNorm == null) {
            return out;
        }
        List<SkillExternalCatalogItemVO> filtered = new ArrayList<>();
        for (SkillExternalCatalogItemVO v : out) {
            int st = v.getStars() == null ? 0 : v.getStars();
            if (minStars != null && st < minStars) {
                continue;
            }
            if (maxStars != null && st > maxStars) {
                continue;
            }
            if (sourceNorm != null && !matchesSourceFilter(v, sourceNorm)) {
                continue;
            }
            filtered.add(v);
        }
        return filtered;
    }

    private static boolean matchesSourceFilter(SkillExternalCatalogItemVO v, String sourceNorm) {
        String lic = v.getLicenseNote() == null ? "" : v.getLicenseNote().toLowerCase(Locale.ROOT);
        return switch (sourceNorm) {
            case "skillhub" -> lic.contains("skillhub");
            case "skillsmp" -> lic.contains("skillsmp");
            case "mirror" -> !lic.contains("skillhub") && !lic.contains("skillsmp");
            default -> true;
        };
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
        List<SkillExternalCatalogItemVO> out = rewritePacks(properties, remoteCollectionService.collectUnrewritten(properties));
        if (out.isEmpty()) {
            throwEmptyDynamicCatalog(properties);
        }
        return out;
    }

    /**
     * 空结果时按生效方式给出明确提示（避免「仅 SkillHub」用户误以为需要 SkillsMP Key）。
     */
    private static void throwEmptyDynamicCatalog(SkillExternalCatalogProperties properties) {
        String mode = SkillExternalCatalogRemoteCollectionService.normalizeRemoteCatalogMode(properties.getRemoteCatalogMode());
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
        List<SkillExternalCatalogItemVO> remote = rewritePacks(properties, remoteCollectionService.collectUnrewritten(properties));
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
     * mirror-catalog-url、mirror-catalog-urls 与 catalog-http-sources 合并；同一 URL 仅保留首次出现的 format。
     */
    public static List<SkillExternalCatalogProperties.CatalogHttpSource> resolvedCatalogHttpSources(
            SkillExternalCatalogProperties properties) {
        return SkillExternalCatalogRemoteCollectionService.resolvedCatalogHttpSources(properties);
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
                .itemKey(v.getItemKey() != null && !v.getItemKey().isBlank()
                        ? v.getItemKey()
                        : SkillExternalCatalogDedupeKeys.fromVo(v))
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
