package com.lantu.connect.gateway.skillsmp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.catalog.SkillCatalogOutboundRestTemplateFactory;
import com.lantu.connect.gateway.config.SkillExternalCatalogProperties;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpKeywordSearchResponse;
import com.lantu.connect.gateway.skillsmp.dto.SkillsMpSkillJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SkillsMP 公开 REST API（需 API Key）：关键词搜索技能列表。
 *
 * @see <a href="https://skillsmp.com/docs/api">SkillsMP API</a>
 */
@Component
@Slf4j
public class SkillsMpCatalogClient {

    private static final int MAX_TRANSIENT_RETRIES = 2;
    private static final long INITIAL_BACKOFF_MS = 750L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SkillsMpSkillJson> searchPage(SkillExternalCatalogProperties catalogProperties,
                                             String query, int page, int limit, String sortBy) {
        SkillExternalCatalogProperties.SkillsMp cfg = catalogProperties.getSkillsmp();
        if (cfg == null || !cfg.isEnabled()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "SkillsMP 已在配置中关闭（skillsmp.enabled=false）");
        }
        String key = cfg.getApiKey();
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "未配置 SkillsMP API Key，请设置环境变量 SKILLSMP_API_KEY 或 lantu.skill-external-catalog.skillsmp.api-key");
        }
        String base = StringUtils.hasText(cfg.getBaseUrl()) ? cfg.getBaseUrl().trim() : "https://skillsmp.com/api/v1";
        RestTemplate restTemplate = SkillCatalogOutboundRestTemplateFactory.create(catalogProperties.getOutboundHttpProxy());

        String primarySort = StringUtils.hasText(sortBy) ? sortBy.trim() : "stars";
        List<String> sortAttempts = new ArrayList<>();
        sortAttempts.add(primarySort);
        if ("stars".equalsIgnoreCase(primarySort)) {
            sortAttempts.add("recent");
        }

        BusinessException lastFailure = null;
        for (String useSort : sortAttempts) {
            for (int attempt = 0; attempt < MAX_TRANSIENT_RETRIES; attempt++) {
                if (attempt > 0) {
                    sleepBackoff(INITIAL_BACKOFF_MS * attempt);
                }
                String uri = buildSearchUri(base, query, page, limit, useSort);
                try {
                    return exchangeSearch(restTemplate, uri, key.trim());
                } catch (BusinessException e) {
                    lastFailure = e;
                    break;
                } catch (HttpStatusCodeException e) {
                    BusinessException converted = handleHttpException(e);
                    if (converted != null) {
                        throw converted;
                    }
                    int code = e.getStatusCode().value();
                    if (code >= 500 && code <= 599) {
                        log.warn("SkillsMP HTTP {} for q=[{}] sortBy=[{}] (attempt {}/{}): {}",
                                code, query, useSort, attempt + 1, MAX_TRANSIENT_RETRIES,
                                abbreviateBody(e.getResponseBodyAsString()));
                        lastFailure = new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                                "SkillsMP 请求失败 HTTP " + code);
                        if (attempt + 1 < MAX_TRANSIENT_RETRIES) {
                            continue;
                        }
                        break;
                    }
                    throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                            "SkillsMP 请求失败 HTTP " + code);
                }
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR, "SkillsMP 请求失败（已重试）");
    }

    private static String buildSearchUrlBase(String base) {
        return base.endsWith("/") ? base + "skills/search" : base + "/skills/search";
    }

    private static String buildSearchUri(String base, String query, int page, int limit, String sortBy) {
        String url = buildSearchUrlBase(base);
        return UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("q", query)
                .queryParam("page", page)
                .queryParam("limit", Math.min(100, Math.max(1, limit)))
                .queryParam("sortBy", StringUtils.hasText(sortBy) ? sortBy : "stars")
                .encode(StandardCharsets.UTF_8)
                .build(true)
                .toUriString();
    }

    private List<SkillsMpSkillJson> exchangeSearch(RestTemplate restTemplate, String uri, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        headers.set(HttpHeaders.ACCEPT, "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<SkillsMpKeywordSearchResponse> resp = restTemplate.exchange(
                uri, HttpMethod.GET, entity, SkillsMpKeywordSearchResponse.class);
        SkillsMpKeywordSearchResponse body = resp.getBody();
        if (body == null) {
            return Collections.emptyList();
        }
        if (Boolean.FALSE.equals(body.getSuccess()) && body.getError() != null) {
            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                    "SkillsMP: " + body.getError().getCode() + " — " + body.getError().getMessage());
        }
        if (body.getData() == null || body.getData().getSkills() == null) {
            return Collections.emptyList();
        }
        return body.getData().getSkills();
    }

    /**
     * @return non-null BusinessException for fatal HTTP errors (401, 429, 4xx), or null if caller should retry / treat as transient 5xx
     */
    private BusinessException handleHttpException(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if (status == 401) {
            log.warn("SkillsMP HTTP 401: {}", abbreviateBody(e.getResponseBodyAsString()));
            return new BusinessException(ResultCode.PARAM_ERROR, "SkillsMP API Key 无效或未授权");
        }
        if (status == 429) {
            log.warn("SkillsMP HTTP 429: {}", abbreviateBody(e.getResponseBodyAsString()));
            return new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR, "SkillsMP 当日配额已用尽，请明日再试");
        }
        if (status >= 400 && status < 500) {
            log.warn("SkillsMP HTTP {}: {}", status, abbreviateBody(e.getResponseBodyAsString()));
            SkillsMpKeywordSearchResponse err = tryParseErrorBody(e.getResponseBodyAsString());
            if (err != null && err.getError() != null) {
                return new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                        "SkillsMP: " + err.getError().getCode() + " — " + err.getError().getMessage());
            }
            return new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR,
                    "SkillsMP 请求失败 HTTP " + status);
        }
        return null;
    }

    private SkillsMpKeywordSearchResponse tryParseErrorBody(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, SkillsMpKeywordSearchResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String abbreviateBody(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() > 280 ? t.substring(0, 277) + "..." : t;
    }

    private static void sleepBackoff(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.EXTERNAL_SERVICE_ERROR, "SkillsMP 请求被中断");
        }
    }
}
