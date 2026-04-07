package com.lantu.connect.gateway.catalog;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 当 {@link GitHubBrowserRepoPathParser} 无法从来源 URL 得到 tree 子路径时，按常见 monorepo 布局与
 * SkillsMP id 惯例追加尝试路径（例如 {@code skills/coding-agent/SKILL.md}）。
 */
public final class GitHubSkillMdPathHeuristics {

    private static final Pattern SKILLS_MP_TAIL_ID = Pattern.compile("(?i)-skills-(.+)-skill-md$");
    /**
     * openclaw 等：{@code ...-extensions-acpx-skills-acp-router-skill-md} → {@code extensions/acpx/skills/acp-router/}。
     */
    private static final Pattern EXTENSIONS_SKILLS_TAIL_ID =
            Pattern.compile("(?i)-extensions-([a-z0-9._-]+)-skills-(.+)-skill-md$");

    private GitHubSkillMdPathHeuristics() {
    }

    /**
     * 在解析候选之后追加；路径需再经 {@link GitHubBrowserRepoPathParser#safeRepoRelativePath} 校验。
     */
    public static List<String> monorepoCandidates(String displayName, String catalogId) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String slug = slugifyPathSegment(displayName);
        if (StringUtils.hasText(slug)) {
            set.add("skills/" + slug + "/SKILL.md");
            set.add("skills/" + slug + "/skill.md");
            set.add("skill/" + slug + "/SKILL.md");
        }
        String fromId = skillSlugFromSkillsMpStyleId(catalogId);
        if (StringUtils.hasText(fromId)) {
            if (!fromId.equalsIgnoreCase(slug)) {
                set.add("skills/" + fromId + "/SKILL.md");
                set.add("skills/" + fromId + "/skill.md");
            }
        }
        addExtensionsLayoutPaths(catalogId, set);
        List<String> out = new ArrayList<>();
        for (String p : set) {
            if (GitHubBrowserRepoPathParser.safeRepoRelativePath(p)) {
                out.add(p);
            }
        }
        return out;
    }

    static String slugifyPathSegment(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        s = s.replaceAll("[^a-z0-9._-]", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s;
    }

    /**
     * SkillsMP 常见 slug：{@code owner-repo-skills-<segment>-skill-md}，取 segment 作为子目录名。
     */
    static String skillSlugFromSkillsMpStyleId(String id) {
        if (!StringUtils.hasText(id)) {
            return "";
        }
        Matcher m = SKILLS_MP_TAIL_ID.matcher(id.trim());
        if (!m.find()) {
            return "";
        }
        String inner = m.group(1);
        if (!StringUtils.hasText(inner)) {
            return "";
        }
        return slugifyPathSegment(inner.replace('/', '-'));
    }

    static void addExtensionsLayoutPaths(String catalogId, LinkedHashSet<String> set) {
        if (!StringUtils.hasText(catalogId)) {
            return;
        }
        Matcher m = EXTENSIONS_SKILLS_TAIL_ID.matcher(catalogId.trim());
        if (!m.find()) {
            return;
        }
        String extSeg = slugifyPathSegment(m.group(1));
        String skillSeg = slugifyPathSegment(m.group(2).replace('/', '-'));
        if (!StringUtils.hasText(extSeg) || !StringUtils.hasText(skillSeg)) {
            return;
        }
        set.add("extensions/" + extSeg + "/skills/" + skillSeg + "/SKILL.md");
        set.add("extensions/" + extSeg + "/skills/" + skillSeg + "/skill.md");
    }
}
