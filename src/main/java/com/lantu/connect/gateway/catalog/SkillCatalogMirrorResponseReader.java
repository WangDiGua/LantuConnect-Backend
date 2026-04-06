package com.lantu.connect.gateway.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.skillsmp.GitHubRepoRef;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 解析技能目录 HTTPS JSON：根数组或常见对象键；兼容 skill0（skills[]+pagination）、各类 {data|results|list|items}；
 * 元素支持 VO 直映射、直链 zip（downloadUrl 等）或 githubUrl 推导 zip。
 */
public final class SkillCatalogMirrorResponseReader {

    private static final String LICENSE_MIRROR = "数据来自配置的 JSON/HTTPS 目录接口（含第三方市场）；ZIP 来自直链或 GitHub 分支打包推导。";

    private SkillCatalogMirrorResponseReader() {
    }

    public record ParsedPage(List<SkillExternalCatalogItemVO> items, Optional<Integer> nextPageNumber) {
    }

    /**
     * @param templateUrl 用户配置的目录 URL（用于翻页时保留 path 与其它 query）
     */
    public static ParsedPage parseFirstPage(String json, ObjectMapper objectMapper, String templateUrl)
            throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode array = extractItemsArray(root);
        List<SkillExternalCatalogItemVO> items = mapItemsFromArray(array, objectMapper);
        Optional<Integer> next = readNextPageNumber(root).flatMap(n -> sanitizeNext(templateUrl, n));
        return new ParsedPage(items, next);
    }

    static Optional<Integer> readNextPageNumber(JsonNode root) {
        JsonNode p = paginationNode(root);
        if (p == null || !p.isObject()) {
            return Optional.empty();
        }
        int page = intAt(p, "page", intAt(p, "pageNum", 1));
        int totalPages = intAt(p, "totalPages", intAt(p, "totalPage", 0));
        boolean explicitHasNext = p.path("hasNext").asBoolean(false)
                || p.path("has_more").asBoolean(false)
                || p.path("hasMore").asBoolean(false);
        if (explicitHasNext && totalPages > 0 && page < totalPages) {
            return Optional.of(page + 1);
        }
        if (explicitHasNext && totalPages <= 0) {
            return Optional.of(page + 1);
        }
        if (totalPages > 0 && page < totalPages) {
            return Optional.of(page + 1);
        }
        return Optional.empty();
    }

    private static JsonNode paginationNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode nested = root.get("pagination");
        if (nested != null && nested.isObject()) {
            return nested;
        }
        if (root.has("page") && (root.has("totalPages") || root.has("totalPage"))) {
            return root;
        }
        return null;
    }

    private static int intAt(JsonNode n, String field, int defaultVal) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return defaultVal;
        }
        return n.get(field).asInt(defaultVal);
    }

    private static Optional<Integer> sanitizeNext(String templateUrl, int nextPage) {
        if (!StringUtils.hasText(templateUrl) || nextPage <= 1) {
            return Optional.empty();
        }
        try {
            UriComponentsBuilder.fromHttpUrl(templateUrl.trim()).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
        return Optional.of(nextPage);
    }

    public static String urlWithPageParam(String templateUrl, int page) {
        return UriComponentsBuilder.fromHttpUrl(templateUrl.trim())
                .replaceQueryParam("page", page)
                .build(true)
                .toUriString();
    }

    static JsonNode extractItemsArray(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        String[] keys = { "data", "skills", "results", "list", "items", "records", "rows" };
        for (String k : keys) {
            if (root.has(k) && root.get(k).isArray()) {
                return root.get(k);
            }
        }
        return null;
    }

    static List<SkillExternalCatalogItemVO> mapItemsFromArray(JsonNode array, ObjectMapper objectMapper) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<SkillExternalCatalogItemVO> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (n == null || !n.isObject()) {
                continue;
            }
            SkillExternalCatalogItemVO vo = mapOneItem(n, objectMapper);
            if (vo != null) {
                out.add(vo);
            }
        }
        return out;
    }

    private static SkillExternalCatalogItemVO mapOneItem(JsonNode n, ObjectMapper objectMapper) {
        SkillExternalCatalogItemVO direct = tryDirectVo(n, objectMapper);
        if (direct != null) {
            return direct;
        }
        SkillExternalCatalogItemVO loosePack = tryLoosePackOrTitle(n);
        if (loosePack != null) {
            return loosePack;
        }
        return tryFromGithubStyle(n);
    }

    private static SkillExternalCatalogItemVO tryDirectVo(JsonNode n, ObjectMapper objectMapper) {
        try {
            SkillExternalCatalogItemVO vo = objectMapper.convertValue(n, SkillExternalCatalogItemVO.class);
            if (vo != null
                    && StringUtils.hasText(vo.getId())
                    && StringUtils.hasText(vo.getName())
                    && StringUtils.hasText(vo.getPackUrl())) {
                return vo;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return null;
    }

    /** id/name/packUrl 从 slug、title、downloadUrl 等补齐。 */
    private static SkillExternalCatalogItemVO tryLoosePackOrTitle(JsonNode n) {
        String id = firstNonBlank(
                text(n, "id"),
                text(n, "slug"),
                text(n, "skillId"),
                text(n, "uuid"));
        String name = firstNonBlank(
                text(n, "name"),
                text(n, "title"),
                text(n, "displayName"));
        String packUrl = firstNonBlank(
                text(n, "packUrl"),
                text(n, "downloadUrl"),
                text(n, "zipUrl"),
                text(n, "zip"),
                text(n, "artifactUrl"),
                text(n, "packageUrl"));
        if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !StringUtils.hasText(packUrl)) {
            return null;
        }
        if (!packUrl.startsWith("http://") && !packUrl.startsWith("https://")) {
            return null;
        }
        String summary = firstNonBlank(text(n, "summary"), text(n, "description"), text(n, "intro"));
        Integer stars = n.has("stars") && n.get("stars").isNumber() ? n.get("stars").asInt() : null;
        return SkillExternalCatalogItemVO.builder()
                .id(id.trim())
                .name(name.trim())
                .summary(StringUtils.hasText(summary) ? summary.trim() : null)
                .packUrl(packUrl.trim())
                .licenseNote(LICENSE_MIRROR)
                .sourceUrl(firstNonBlank(text(n, "sourceUrl"), text(n, "url"), text(n, "link")))
                .stars(stars)
                .build();
    }

    private static SkillExternalCatalogItemVO tryFromGithubStyle(JsonNode n) {
        String id = firstNonBlank(text(n, "id"), text(n, "slug"));
        String name = firstNonBlank(text(n, "name"), text(n, "title"));
        String gh = githubUrlFrom(n);
        if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !StringUtils.hasText(gh)) {
            return null;
        }
        if (!gh.startsWith("http://") && !gh.startsWith("https://")) {
            return null;
        }
        GitHubRepoRef ref = GitHubRepoRef.parse(gh).orElse(null);
        if (ref == null) {
            return null;
        }
        String branch = firstNonBlank(text(n, "branch"), text(n, "defaultBranch"));
        if (!StringUtils.hasText(branch)) {
            branch = "main";
        }
        String packUrl = ref.defaultBranchArchiveZip(branch);
        String desc = firstNonBlank(text(n, "description"), text(n, "summary"));
        String author = text(n, "author");
        String summary = desc;
        if (StringUtils.hasText(author)) {
            summary = !StringUtils.hasText(summary) ? ("作者: " + author) : (summary + " — 作者: " + author);
        }
        Integer stars = n.has("stars") && n.get("stars").isNumber() ? n.get("stars").asInt() : null;

        return SkillExternalCatalogItemVO.builder()
                .id(id.trim())
                .name(name.trim())
                .summary(StringUtils.hasText(summary) ? summary.trim() : null)
                .packUrl(packUrl)
                .licenseNote(LICENSE_MIRROR)
                .sourceUrl(gh.trim())
                .stars(stars)
                .build();
    }

    private static String githubUrlFrom(JsonNode n) {
        String direct = firstNonBlank(
                text(n, "githubUrl"),
                text(n, "github_url"),
                text(n, "repoUrl"),
                text(n, "gitUrl"),
                text(n, "htmlUrl"));
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        JsonNode repo = n.get("repository");
        if (repo != null && repo.isObject()) {
            return firstNonBlank(
                    text(repo, "html_url"),
                    text(repo, "htmlUrl"),
                    text(repo, "url"));
        }
        return null;
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        String s = n.get(field).asText();
        return StringUtils.hasText(s) ? s : null;
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String s : parts) {
            if (StringUtils.hasText(s)) {
                return s.trim();
            }
        }
        return null;
    }
}
