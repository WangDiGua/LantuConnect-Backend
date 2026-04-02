package com.lantu.connect.gateway.skillhub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.catalog.SkillCatalogOutboundRestTemplateFactory;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.skillhub.dto.SkillHubSearchResponse;
import com.lantu.connect.gateway.skillhub.dto.SkillHubSearchSkillJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Agent Skill Hub 兼容公开搜索 API：<code>GET {base}/api/v1/search?q=&limit=</code>（无需 Key）。
 * @see <a href="https://doc.agentskillhub.dev/guide/api.html">API Reference</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillHubCatalogClient {

    private static final int API_MAX_LIMIT = 10;
    /** 部分站点对无 Accept / 无 User-Agent 的请求会回 HTML 首页或拦截页 */
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (compatible; LantuConnect-SkillHub/1.0; +SkillHubCatalogClient)";

    private final ObjectMapper objectMapper;

    public List<SkillHubSearchSkillJson> search(SkillExternalCatalogProperties catalogProperties, String query, int limit) {
        SkillExternalCatalogProperties.SkillHub cfg = catalogProperties.getSkillhub();
        if (cfg == null || !cfg.isEnabled()) {
            return List.of();
        }
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        int lim = Math.min(API_MAX_LIMIT, Math.max(1, limit));
        String primary = normalizeApiBase(cfg.getBaseUrl());
        String fallback = StringUtils.hasText(cfg.getFallbackBaseUrl()) ? normalizeApiBase(cfg.getFallbackBaseUrl().trim()) : "";

        try {
            return searchOnce(catalogProperties, primary, q, lim);
        } catch (RestClientException e) {
            if (StringUtils.hasText(fallback) && !fallback.equalsIgnoreCase(primary)) {
                log.warn("SkillHub 主站 {} 请求失败，使用 fallback {}: {}", primary, fallback, e.getMessage());
                return searchOnce(catalogProperties, fallback, q, lim);
            }
            throw e;
        }
    }

    private List<SkillHubSearchSkillJson> searchOnce(
            SkillExternalCatalogProperties catalogProperties,
            String apiBase,
            String query,
            int limit) {
        String url = apiBase + "/search";
        String uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("q", query)
                .queryParam("limit", limit)
                .encode(StandardCharsets.UTF_8)
                .build(true)
                .toUriString();
        RestTemplate restTemplate = SkillCatalogOutboundRestTemplateFactory.create(catalogProperties.getOutboundHttpProxy());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            String raw = resp.getBody();
            MediaType ct = resp.getHeaders().getContentType();
            if (raw == null || raw.isBlank()) {
                return Collections.emptyList();
            }
            String trimmed = raw.stripLeading();
            boolean looksHtml = trimmed.startsWith("<")
                    || trimmed.toLowerCase(Locale.ROOT).startsWith("<!doctype");
            boolean declaredHtml = ct != null && ct.includes(MediaType.TEXT_HTML);
            if (looksHtml || declaredHtml) {
                log.warn(
                        "SkillHub 返回 HTML 而非 JSON（多为 baseUrl 不可达、代理/WAF 拦截页或非 API 地址）。apiBase={} url={} contentType={} bodySnippet={}",
                        apiBase,
                        uri,
                        ct,
                        abbreviate(trimmed, 240));
                return Collections.emptyList();
            }
            SkillHubSearchResponse body = objectMapper.readValue(raw, SkillHubSearchResponse.class);
            if (body.getSkills() == null) {
                return Collections.emptyList();
            }
            return body.getSkills();
        } catch (HttpStatusCodeException e) {
            log.warn("SkillHub HTTP {} {}: {}", apiBase, e.getStatusCode(), abbreviate(e.getResponseBodyAsString(), 400));
            return Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("SkillHub JSON 解析失败 apiBase={} url={}: {}", apiBase, uri, e.getOriginalMessage());
            return Collections.emptyList();
        }
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    /**
     * 将站点根 URL 规范为 …/api/v1（无尾斜杠）。
     */
    static String normalizeApiBase(String baseRaw) {
        if (!StringUtils.hasText(baseRaw)) {
            return "https://agentskillhub.dev/api/v1";
        }
        String b = baseRaw.trim().replaceAll("/+$", "");
        if (b.toLowerCase(Locale.ROOT).endsWith("/api/v1")) {
            return b;
        }
        return b + "/api/v1";
    }
}
