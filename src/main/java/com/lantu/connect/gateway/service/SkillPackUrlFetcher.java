package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.SkillPackImportProperties;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * 受 SSRF 约束的 HTTP GET，用于拉取技能 zip 字节；支持将 Git HTTPS 仓库浅克隆为 zip。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillPackUrlFetcher {

    private static final String UA_APP = "NexusAI-Connect-SkillPackImport/1.0";
    private static final String UA_BROWSERISH =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RuntimeAppConfigService runtimeAppConfigService;
    private final SkillPackRemoteUriValidator remoteUriValidator;
    private final GitSkillPackCloner gitSkillPackCloner;

    private SkillPackImportProperties p() {
        return runtimeAppConfigService.skillPackImport();
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, p().getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record FetchedPack(byte[] bytes, String filenameForStorage) {}

    public FetchedPack fetch(String urlRaw) {
        remoteUriValidator.assertHostSuffixPolicyConfigured();
        if (!StringUtils.hasText(urlRaw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 不能为空");
        }
        String trimmed = urlRaw.trim();
        URI initial;
        try {
            initial = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 格式无效");
        }
        if (initial.getRawUserInfo() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许在 url 中带用户信息");
        }
        if (gitSkillPackCloner.shouldGitClone(initial)) {
            remoteUriValidator.assertUriSafeForRemote(initial);
            return gitSkillPackCloner.cloneShallowToFetchedPack(trimmed);
        }

        URI current = initial;
        int maxRedirects = Math.max(0, p().getMaxRedirects());
        long maxBytes = Math.max(1024L, p().getMaxBytes());
        int readSec = Math.max(5, p().getReadTimeoutSeconds());
        int redirectsFollowed = 0;

        while (true) {
            remoteUriValidator.assertUriSafeForRemote(current);
            HttpResponse<InputStream> resp = sendGetWithRetries(current, readSec);
            int code = resp.statusCode();
            if (code >= 300 && code < 400) {
                if (redirectsFollowed >= maxRedirects) {
                    drainAndClose(resp.body());
                    throw new BusinessException(ResultCode.PARAM_ERROR,
                            "重定向次数超过上限（maxRedirects=" + maxRedirects + "，不含最终 200 响应）");
                }
                redirectsFollowed++;
                String loc = resp.headers().firstValue("location").orElse(null);
                drainAndClose(resp.body());
                if (!StringUtils.hasText(loc)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "重定向响应缺少 Location");
                }
                current = resolveRedirect(current, loc.trim());
                continue;
            }
            if (code != 200) {
                drainAndClose(resp.body());
                throw new BusinessException(ResultCode.PARAM_ERROR, "下载失败 HTTP " + code);
            }
            long declared = contentLength(resp.headers());
            if (declared > maxBytes) {
                drainAndClose(resp.body());
                throw new BusinessException(ResultCode.PARAM_ERROR, "响应 Content-Length 超过上限");
            }
            String fname = filenameFromUri(current);
            try (InputStream in = resp.body()) {
                byte[] data = readLimited(in, maxBytes);
                return new FetchedPack(data, fname);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, packFetchIoMessage(e).replaceFirst(
                        "^拉取 url 失败: ", "读取响应体失败: "));
            }
        }
    }

    private HttpResponse<InputStream> sendGetWithRetries(URI uri, int readTimeoutSec) {
        int extra = Math.max(0, p().getFetchRetries());
        int attempts = 1 + extra;
        long delayMs = Math.max(0, p().getFetchRetryDelayMs());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String userAgent = attempt == 1 ? UA_APP : UA_BROWSERISH;
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(readTimeoutSec))
                    .header("User-Agent", userAgent)
                    .header("Accept",
                            "application/zip, application/x-7z-compressed, application/x-rar-compressed, "
                                    + "application/gzip, application/x-gzip, application/x-tar, "
                                    + "text/markdown, application/octet-stream, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .GET()
                    .build();
            try {
                return httpClient().send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.PARAM_ERROR, "拉取 url 已中断");
            } catch (IOException e) {
                if (attempt < attempts && isRetryableNetworkError(e)) {
                    log.warn("技能包 GET {} 第 {}/{} 次 IO 失败，{}ms 后重试: {}",
                            uri.getHost(), attempt, attempts, delayMs, e.getMessage());
                    if (delayMs > 0) {
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new BusinessException(ResultCode.PARAM_ERROR, "拉取 url 已中断");
                        }
                    }
                    continue;
                }
                throw new BusinessException(ResultCode.PARAM_ERROR, packFetchIoMessage(e));
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "拉取 url 失败: 已达重试上限");
    }

    private static boolean isRetryableNetworkError(IOException e) {
        String m = e.getMessage();
        if (m == null) {
            return true;
        }
        String l = m.toLowerCase(Locale.ROOT);
        return l.contains("connection reset")
                || l.contains("connection refused")
                || l.contains("broken pipe")
                || l.contains("connection timed out")
                || l.contains("timed out")
                || l.contains("forcibly closed")
                || l.contains("failed to respond")
                || l.contains("unexpected end of stream");
    }

    private static String packFetchIoMessage(IOException e) {
        String core = "拉取 url 失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        String lm = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (lm.contains("connection reset") || lm.contains("broken pipe") || lm.contains("forcibly closed")) {
            return core + "。直连 GitHub 等源在国内易受 RST 影响：可在「技能在线市场」配置 github-zip-mirror 使用镜像前缀，"
                    + "或换用国内可访问的 zip 直链；亦可调大 lantu.skill-pack-import.fetch-retries / connect-timeout-seconds。";
        }
        return core;
    }

    private static void drainAndClose(InputStream in) {
        if (in == null) {
            return;
        }
        try (InputStream stream = in) {
            stream.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ignored) {
        }
    }

    private static long contentLength(HttpHeaders headers) {
        return headers.firstValueAsLong("content-length").orElse(-1L);
    }

    private static URI resolveRedirect(URI base, String location) {
        try {
            URI next = base.resolve(location);
            if (next.getRawUserInfo() != null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "重定向目标不允许带用户信息");
            }
            return next;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无效的重定向地址");
        }
    }

    private static String filenameFromUri(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return "skill-pack.zip";
        }
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        String ll = last.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(last)
                || !(ll.endsWith(".zip") || ll.endsWith(".7z") || ll.endsWith(".rar")
                || ll.endsWith(".tgz") || ll.endsWith(".tar.gz") || ll.endsWith(".tar")
                || ll.endsWith(".md") || ll.endsWith(".gz"))) {
            return "skill-pack.bin";
        }
        return last;
    }

    private static byte[] readLimited(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "下载内容超过大小上限");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
