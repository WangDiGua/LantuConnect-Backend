package com.lantu.connect.gateway.catalog;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 从可能经 {@link GitHubZipPackUrlMirror} 改写过的 packUrl 中尽量还原内层 GitHub archive zip 直链，供解析分支名等。
 */
public final class GitHubPackUrlUnwrapper {

    private GitHubPackUrlUnwrapper() {
    }

    public static String unwrapToGithubArchiveZip(String packUrl) {
        if (!StringUtils.hasText(packUrl)) {
            return packUrl;
        }
        String s = GitHubZipPackUrlMirror.repairAccidentalRelativeMirrorPrefix(packUrl.trim());
        if (looksLikeGithubArchiveZip(s)) {
            return s;
        }
        String decoded = multiDecodeWhileChanging(s);
        if (looksLikeGithubArchiveZip(decoded)) {
            return decoded;
        }
        int idx = indexOfGithubArchive(decoded);
        if (idx >= 0) {
            String tail = decoded.substring(idx);
            if (looksLikeGithubArchiveZip(tail)) {
                return tail;
            }
        }
        int idx2 = indexOfGithubArchive(s);
        if (idx2 >= 0) {
            String tail = s.substring(idx2);
            if (looksLikeGithubArchiveZip(tail)) {
                return tail;
            }
        }
        return s;
    }

    private static String multiDecodeWhileChanging(String s) {
        String cur = s;
        for (int i = 0; i < 4; i++) {
            if (!cur.contains("%")) {
                break;
            }
            try {
                String next = URLDecoder.decode(cur, StandardCharsets.UTF_8);
                if (next.equals(cur)) {
                    break;
                }
                cur = next;
                if (looksLikeGithubArchiveZip(cur)) {
                    return cur;
                }
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return cur;
    }

    private static int indexOfGithubArchive(String s) {
        if (!StringUtils.hasText(s)) {
            return -1;
        }
        String low = s.toLowerCase(Locale.ROOT);
        int g = low.indexOf("https://github.com");
        if (g < 0) {
            g = low.indexOf("http://github.com");
        }
        if (g < 0) {
            return -1;
        }
        int arch = low.indexOf("/archive/", g);
        return arch > 0 ? g : -1;
    }

    static boolean looksLikeGithubArchiveZip(String u) {
        if (!StringUtils.hasText(u)) {
            return false;
        }
        try {
            URI uri = URI.create(u.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            if (!"github.com".equals(h) && !"www.github.com".equals(h)) {
                return false;
            }
            String path = uri.getPath();
            if (path == null) {
                return false;
            }
            String pl = path.toLowerCase(Locale.ROOT);
            return pl.contains("/archive/") && pl.endsWith(".zip");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
