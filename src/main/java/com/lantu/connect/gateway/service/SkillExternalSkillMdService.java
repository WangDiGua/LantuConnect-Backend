package com.lantu.connect.gateway.service;

import com.lantu.connect.gateway.catalog.GitHubBrowserRepoPathParser;
import com.lantu.connect.gateway.catalog.GitHubBrowserRepoPathParser.BrowserLayout;
import com.lantu.connect.gateway.catalog.GitHubPackUrlUnwrapper;
import com.lantu.connect.gateway.catalog.GitHubSkillMdPathHeuristics;
import com.lantu.connect.gateway.catalog.SkillCatalogOutboundRestTemplateFactory;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.dto.SkillExternalSkillMdResponse;
import com.lantu.connect.gateway.entity.SkillExternalCatalogItem;
import com.lantu.connect.gateway.mapper.SkillExternalCatalogItemMapper;
import com.lantu.connect.gateway.skillsmp.GitHubRepoRef;
import com.lantu.connect.gateway.support.SkillExternalItemKeyCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 针对 GitHub 来源的外部技能条目，通过 raw.githubusercontent.com 拉取 SKILL.md；带短 TTL 缓存与响应体上限。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillExternalSkillMdService {

    private static final int MAX_BODY_BYTES = 512 * 1024;
    private static final long CACHE_TTL_MS = 10 * 60_000L;
    private static final int CACHE_MAX_ENTRIES = 400;

    private final SkillExternalCatalogService skillExternalCatalogService;
    private final SkillExternalCatalogRuntimeConfigService runtimeConfigService;
    private final SkillExternalCatalogItemMapper skillExternalCatalogItemMapper;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 目录同步预取：仅打 GitHub raw，不读库、不写进程内缓存。
     */
    public record GithubSkillMdPrefetch(String markdown, String resolvedRawUrl, boolean truncated) {
    }

    public Optional<GithubSkillMdPrefetch> prefetchGithubSkillMd(
            SkillExternalCatalogItemVO vo, SkillExternalCatalogProperties cfg) {
        SkillExternalSkillMdResponse r = buildFromVo(vo, cfg);
        if (!StringUtils.hasText(r.getMarkdown())) {
            return Optional.empty();
        }
        return Optional.of(new GithubSkillMdPrefetch(r.getMarkdown(), r.getResolvedRawUrl(), r.isTruncated()));
    }

    public SkillExternalSkillMdResponse fetchForItemKey(String rawKey) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawKey);
        if (k.isEmpty()) {
            return SkillExternalSkillMdResponse.builder()
                    .hint("无效的 key")
                    .build();
        }
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(k);
        if (hit != null && hit.expiresAtMs > now) {
            return SkillExternalSkillMdResponse.builder()
                    .markdown(hit.markdown)
                    .resolvedRawUrl(hit.resolvedRawUrl)
                    .truncated(hit.truncated)
                    .fromCache(true)
                    .fromDbMirror(hit.fromDbMirror)
                    .build();
        }
        SkillExternalCatalogItem stored = skillExternalCatalogItemMapper.selectByDedupeKey(k);
        if (stored != null && StringUtils.hasText(stored.getSkillMd())) {
            SkillExternalSkillMdResponse db = SkillExternalSkillMdResponse.builder()
                    .markdown(stored.getSkillMd())
                    .resolvedRawUrl(stored.getSkillMdResolvedUrl())
                    .truncated(Boolean.TRUE.equals(stored.getSkillMdTruncated()))
                    .fromCache(false)
                    .fromDbMirror(true)
                    .build();
            cache.put(k, new CacheEntry(
                    db.getMarkdown(),
                    db.getResolvedRawUrl(),
                    db.isTruncated(),
                    true,
                    now + CACHE_TTL_MS));
            capCacheSize();
            return db;
        }
        Optional<SkillExternalCatalogItemVO> voOpt = skillExternalCatalogService.findCatalogItemByItemKey(k);
        if (voOpt.isEmpty()) {
            return SkillExternalSkillMdResponse.builder()
                    .hint("条目不存在或尚未同步")
                    .build();
        }
        SkillExternalSkillMdResponse built = buildFromVo(voOpt.get(), runtimeConfigService.effective());
        if (StringUtils.hasText(built.getMarkdown())) {
            cache.put(k, new CacheEntry(
                    built.getMarkdown(),
                    built.getResolvedRawUrl(),
                    built.isTruncated(),
                    false,
                    now + CACHE_TTL_MS));
            capCacheSize();
        }
        return SkillExternalSkillMdResponse.builder()
                .markdown(built.getMarkdown())
                .resolvedRawUrl(built.getResolvedRawUrl())
                .hint(built.getHint())
                .truncated(built.isTruncated())
                .fromCache(false)
                .fromDbMirror(false)
                .build();
    }

    private SkillExternalSkillMdResponse buildFromVo(SkillExternalCatalogItemVO vo, SkillExternalCatalogProperties cfg) {
        String defaultBranch = defaultGithubBranch(cfg);
        String pack = vo.getPackUrl();
        String source = vo.getSourceUrl();
        String unwrappedZip = GitHubPackUrlUnwrapper.unwrapToGithubArchiveZip(pack);
        Optional<String> archiveRef = GitHubBrowserRepoPathParser.extractRefFromArchiveZipUrl(unwrappedZip);

        Optional<GitHubRepoRef> repoRef = GitHubRepoRef.parse(source);
        if (repoRef.isEmpty()) {
            repoRef = GitHubRepoRef.parse(unwrappedZip);
        }
        if (repoRef.isEmpty()) {
            return SkillExternalSkillMdResponse.builder()
                    .hint("当前条目非 GitHub 仓库或缺少可解析的 archive 链接，无法在站内拉取 SKILL.md。")
                    .build();
        }
        Optional<BrowserLayout> layoutOpt = GitHubBrowserRepoPathParser.parseBrowserUrl(source);
        GitHubRepoRef gh = repoRef.get();
        String owner = gh.getOwner();
        String repo = gh.getRepo();
        if (layoutOpt.isPresent()) {
            BrowserLayout lay = layoutOpt.get();
            if (!owner.equalsIgnoreCase(lay.owner()) || !repo.equalsIgnoreCase(lay.repo())) {
                // source 与 pack 指向不同仓库时，以 source 为准（通常更贴近「项目页」）
                owner = lay.owner();
                repo = lay.repo();
            }
        }
        String ref = layoutOpt
                .map(l -> GitHubBrowserRepoPathParser.resolveRef(l, archiveRef.orElse(""), defaultBranch))
                .orElseGet(() -> archiveRef.orElse(defaultBranch));

        List<String> candidates = mergeCandidatePaths(vo, layoutOpt);

        RestTemplate rt = SkillCatalogOutboundRestTemplateFactory.createSkillMarkdownFetch(cfg.getOutboundHttpProxy());
        boolean networkFault = false;
        for (String path : candidates) {
            if (!GitHubBrowserRepoPathParser.safeRepoRelativePath(path)) {
                continue;
            }
            FetchAttempt att = attemptFetch(rt, owner, repo, ref, path);
            if (att.networkFault()) {
                networkFault = true;
            }
            if (att.result().isPresent()) {
                FetchResult r = att.result().get();
                return SkillExternalSkillMdResponse.builder()
                        .markdown(r.text())
                        .resolvedRawUrl(r.url())
                        .truncated(r.truncated())
                        .build();
            }
        }
        return SkillExternalSkillMdResponse.builder()
                .hint(networkFault
                        ? "连接 raw.githubusercontent.com 超时或网络失败（已重试候选路径）。请稍后在良好网络下刷新，或直接在来源页查看。"
                        : "未在仓库中找到 SKILL.md（已尝试来源路径、仓库根、以及常见 monorepo 路径如 skills/<名称>/）。")
                .build();
    }

    private static List<String> mergeCandidatePaths(SkillExternalCatalogItemVO vo, Optional<BrowserLayout> layoutOpt) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        List<String> fromLayout = layoutOpt
                .map(GitHubBrowserRepoPathParser::skillMarkdownCandidates)
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> List.of("SKILL.md"));
        for (String p : fromLayout) {
            if (GitHubBrowserRepoPathParser.safeRepoRelativePath(p)) {
                set.add(p);
            }
        }
        for (String p : GitHubSkillMdPathHeuristics.monorepoCandidates(vo.getName(), vo.getId())) {
            set.add(p);
        }
        return new ArrayList<>(set);
    }

    private static String defaultGithubBranch(SkillExternalCatalogProperties cfg) {
        String b = cfg.getSkillhub() != null ? cfg.getSkillhub().getGithubDefaultBranch() : "";
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        b = cfg.getSkillsmp() != null ? cfg.getSkillsmp().getGithubDefaultBranch() : "";
        return StringUtils.hasText(b) ? b.trim() : "main";
    }

    private record FetchResult(String text, String url, boolean truncated) {
    }

    private record FetchAttempt(Optional<FetchResult> result, boolean networkFault) {
        static FetchAttempt ok(FetchResult r) {
            return new FetchAttempt(Optional.of(r), false);
        }

        static FetchAttempt miss() {
            return new FetchAttempt(Optional.empty(), false);
        }

        static FetchAttempt network() {
            return new FetchAttempt(Optional.empty(), true);
        }
    }

    private FetchAttempt attemptFetch(RestTemplate rt, String owner, String repo, String ref, String path) {
        String encoded = encodeRawPath(path);
        String url = String.format(Locale.ROOT, "https://raw.githubusercontent.com/%s/%s/%s/%s",
                owner, repo, ref, encoded);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "text/plain,*/*");
            headers.set(HttpHeaders.USER_AGENT, "LantuConnect-SkillExternal/1.0");
            ResponseEntity<byte[]> res = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = res.getBody();
            if (body == null || body.length == 0) {
                return FetchAttempt.miss();
            }
            boolean truncated = false;
            if (body.length > MAX_BODY_BYTES) {
                truncated = true;
                byte[] cut = new byte[MAX_BODY_BYTES];
                System.arraycopy(body, 0, cut, 0, MAX_BODY_BYTES);
                body = cut;
            }
            String text = new String(body, StandardCharsets.UTF_8);
            return FetchAttempt.ok(new FetchResult(text, url, truncated));
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() != 404 && ex.getStatusCode().value() != 403) {
                log.debug("skill-md raw fetch {} -> {}", url, ex.getStatusCode());
            }
            return FetchAttempt.miss();
        } catch (ResourceAccessException ex) {
            log.info("skill-md raw fetch timeout or network error: {}", url);
            return FetchAttempt.network();
        } catch (Exception ex) {
            log.debug("skill-md raw fetch failed: {}", url, ex);
            return FetchAttempt.miss();
        }
    }

    private static String encodeRawPath(String relativePath) {
        String[] segments = relativePath.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String s = segments[i];
            if (s.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(UriUtils.encodePathSegment(s, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private void capCacheSize() {
        if (cache.size() <= CACHE_MAX_ENTRIES) {
            return;
        }
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiresAtMs <= now);
        int guard = 0;
        while (cache.size() > CACHE_MAX_ENTRIES && guard++ < CACHE_MAX_ENTRIES) {
            Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
            if (!it.hasNext()) {
                break;
            }
            it.next();
            it.remove();
        }
    }

    private record CacheEntry(
            String markdown, String resolvedRawUrl, boolean truncated, boolean fromDbMirror, long expiresAtMs) {
    }
}
