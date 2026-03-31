package com.lantu.connect.gateway.service;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.config.SkillPackImportProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 受 SSRF 约束的 HTTP GET，用于拉取技能 zip 字节。
 */
@Service
public class SkillPackUrlFetcher {

    private final SkillPackImportProperties properties;
    private HttpClient httpClient;

    public SkillPackUrlFetcher(SkillPackImportProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record FetchedPack(byte[] bytes, String filenameForStorage) {}

    public FetchedPack fetch(String urlRaw) {
        ensureHostSuffixPolicy();
        if (!StringUtils.hasText(urlRaw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 不能为空");
        }
        String trimmed = urlRaw.trim();
        URI current;
        try {
            current = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 格式无效");
        }
        if (current.getRawUserInfo() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不允许在 url 中带用户信息");
        }
        int maxRedirects = Math.max(0, properties.getMaxRedirects());
        long maxBytes = Math.max(1024L, properties.getMaxBytes());
        int readSec = Math.max(5, properties.getReadTimeoutSeconds());
        int redirectsFollowed = 0;

        while (true) {
            validateRequestUrl(current);
            HttpRequest req = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(readSec))
                    .header("User-Agent", "NexusAI-Connect-SkillPackImport/1.0")
                    .header("Accept", "application/zip, application/octet-stream, */*")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp;
            try {
                resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ResultCode.PARAM_ERROR, "拉取 url 已中断");
            } catch (IOException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "拉取 url 失败: " + e.getMessage());
            }
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
                throw new BusinessException(ResultCode.PARAM_ERROR, "读取响应体失败: " + e.getMessage());
            }
        }
    }

    private void ensureHostSuffixPolicy() {
        if (!properties.isRequireAllowedHostSuffixes()) {
            return;
        }
        List<String> s = properties.getAllowedHostSuffixes();
        boolean any = s != null && s.stream().anyMatch(x -> x != null && !x.isBlank());
        if (!any) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "已启用 require-allowed-host-suffixes，但未配置有效的 lantu.skill-pack-import.allowed-host-suffixes");
        }
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

    private void validateRequestUrl(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 须包含协议");
        }
        String s = scheme.toLowerCase(Locale.ROOT);
        if (properties.isHttpsOnly() && !"https".equals(s)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅允许 https（可在配置 lantu.skill-pack-import.https-only=false 关闭）");
        }
        if (!"https".equals(s) && !"http".equals(s)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 http/https");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 缺少主机名");
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "禁止访问本地主机");
        }
        List<String> suffixes = properties.getAllowedHostSuffixes();
        if (suffixes != null && !suffixes.isEmpty()) {
            boolean ok = false;
            for (String suf : suffixes) {
                if (suf == null || suf.isBlank()) {
                    continue;
                }
                String sl = suf.trim().toLowerCase(Locale.ROOT);
                if (h.equals(sl) || h.endsWith("." + sl)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "主机不在允许列表（lantu.skill-pack-import.allowed-host-suffixes）");
            }
        }
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress a : addrs) {
                if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                        || a.isAnyLocalAddress() || a.isMulticastAddress()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "禁止访问内网或保留地址");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "无法解析主机: " + e.getMessage());
        }
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
        if (!StringUtils.hasText(last) || !last.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return "skill-pack.zip";
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
